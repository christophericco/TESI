package com.example.android.nsdchat.connections.host;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by chris on 12/04/17.
 */

class Worker implements Runnable {
	
	interface ICallbackWorker {
		void onConnectionStarted(String channelId);
		void onConnectionLost(String channelId);
		void onFailure(Throwable exception);
		void onMessageArrived(Message message);
	}
	
	private static final String CLASS_NAME = Worker.class.getSimpleName();
	private static final Logger log = Logger.getLogger(CLASS_NAME);

    private Queue<SocketChannel> inboundSocketQueue = new LinkedList<>();
    private Queue<Message> inboundMessageQueue = new LinkedList<>();
    private Queue<String> removeSocketQueue = new LinkedList<>();

    private MessageReaderFactory mMessageReaderFactory = null;
    private MessageWriterFactory mMessageWriterFactory = null;

    private Map<String, ISocketSession> sessionMap = new HashMap<>();
    private List<ISocketSession> emptyToNonEmptySockets = new ArrayList<>();
    private List<ISocketSession> nonEmptyToEmptySockets = new ArrayList<>();
    

    private ByteBuffer writeBuffer = ByteBuffer.allocate(1024 * 1024);
    private ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
    
    private Thread sendThread = null;
    private String threadName;
    private Future<?> senderFuture;
    private volatile boolean running = false;
    private Object lifecycle = new Object();
    private Object workAvailable = new Object();
    private Semaphore opAvailable = new Semaphore(1);
    private AtomicInteger numReader = new AtomicInteger();
    private AtomicInteger numWriter = new AtomicInteger();

    private Selector mReadSelector = null;
    private Selector mWriteSelector = null;
    
    private ICallbackWorker callbackWorker;

    public Worker(MessageReaderFactory messageReaderFactory,
                           MessageWriterFactory messageWriterFactory,
                           ICallbackWorker callbackWorker) {

        this.mMessageReaderFactory = messageReaderFactory;
        this.mMessageWriterFactory = messageWriterFactory;

        this.callbackWorker = callbackWorker;
    }
    
    public void start(String threadName, ExecutorService executorService) {
    	final String methodName = "[" + threadName + "] start";
    	log.log(Level.INFO, methodName);
    	
    	this.threadName = threadName;
		
		synchronized (lifecycle) {
			if (!running) {
				numReader.set(0);
				numWriter.set(0);
				inboundMessageQueue.clear();
				inboundSocketQueue.clear();
				sessionMap.clear();
				running = true;
				senderFuture = executorService.submit(this);
			}
		}
	}
	
	public void stop() {
		final String methodName = "[" + this.threadName + "] stop";
		log.log(Level.INFO, methodName);
		
		synchronized (lifecycle) {
			if (running && senderFuture != null) {
				running = false;
				
				if(numReader.get() == 0){
					synchronized (workAvailable) {
						workAvailable.notifyAll();
					}
				}
				
				senderFuture.cancel(true);
			}
			
			sendThread = null;
		}
	}
	
	public void enqueue(Message message){
		final String methodName = "[" + this.threadName + "] new message enqueue";
		
		try {
			opAvailable.acquire();
			if(numReader.get() > 0){
				boolean emptyMessage = inboundMessageQueue.isEmpty();
				inboundMessageQueue.offer(message);
			
				if(emptyMessage && inboundSocketQueue.isEmpty()) {
					log.log(Level.INFO, methodName + " : readSelector.wakeup");
					
					mReadSelector.wakeup();
				}	
			}
			
		} catch (InterruptedException e) {
			log.log(Level.INFO, methodName + " : " + e);
		}finally {
			opAvailable.release();
		}	
	}
	
	public void newSocket(SocketChannel socket) {
		final String methodName = "[" + this.threadName + "] new socket";
		
		try {
			opAvailable.acquire();
			boolean empty = inboundSocketQueue.isEmpty();
			inboundSocketQueue.offer(socket);
			
			if(numReader.get() == 0 && empty && inboundMessageQueue.isEmpty()) {
				synchronized (workAvailable) {
					log.log(Level.INFO, methodName + " : workAvailable.notifyAll");
					
					workAvailable.notifyAll();
				}
			} else {
				if(empty && inboundMessageQueue.isEmpty()) {
					log.log(Level.INFO, methodName + " : readSelector.wakeup");
					
					mReadSelector.wakeup();
				}
			}	
		} catch (InterruptedException e) {
			log.log(Level.INFO, methodName + " : " + e);
		}finally {
			opAvailable.release();
		}
	}
	
	public void removeSocket(String sessionId) {
		final String methodName = "[" + this.threadName + "] remove socket";
		
		try {
			opAvailable.acquire();
			boolean empty = removeSocketQueue.isEmpty();
				
			if(numReader.get() > 0){
				removeSocketQueue.offer(sessionId);
				
				if(empty && inboundMessageQueue.isEmpty()) {
					log.log(Level.INFO, methodName + " : readSelector.wakeup");
					
					mReadSelector.wakeup();
				}
			}
		} catch (InterruptedException e) {
			log.log(Level.INFO, methodName + " : " + e);
		}finally {
			opAvailable.release();
		}
	}
	

    @Override
    public void run() {
    	final String methodName = "[" + threadName + "] run";
    	sendThread = Thread.currentThread();
		sendThread.setName(threadName);
		
		log.log(Level.INFO, methodName);
    	
    	try {
			mReadSelector = Selector.open();
			mWriteSelector = Selector.open();
		} catch (IOException e) {
			running = false;
			log.log(Level.SEVERE, methodName + " : " + e.getMessage());
		}
        
        while(running) {
            try {
                executeCycle();
            } catch (IOException e) {
            	log.log(Level.SEVERE, methodName, e);
                running = false;
            }
        }
        
        closeSockets();
    }

    private void closeSockets() {
    	final String methodName = "[" + this.threadName + "] closeSockets";
		log.log(Level.INFO, methodName);
		
        try {
            this.mReadSelector.close();
            this.mWriteSelector.close();
        } catch (IOException e) {
        	log.log(Level.SEVERE, "ReadSelector or WriteSelector close: ", e);
        }

        for(ISocketSession s : sessionMap.values()){
            try {
                s.getChannel().close();
            } catch (IOException e) {
            	log.log(Level.SEVERE, "SocketChannel close: ", e);
            }
        }
    }

    private void executeCycle() throws IOException{
    	final String methodName = "[" + threadName + "] executeCycle";
    	int readReady = 0;
        int writeReady = 0;
        
        try{
        	opAvailable.acquire();
            takeNewSockets();
            removeSockets();
            takeNewOutboundMessages();
        }catch(InterruptedException e){
        	log.log(Level.INFO, methodName, e);
        }finally {
			opAvailable.release();
		}
        
        if(numReader.get() == 0){
        	synchronized (workAvailable) {
				try {
					log.log(Level.INFO, methodName + " : workAvailable.wait");
					workAvailable.wait();
				} catch (InterruptedException e) {
					running = false;
					log.log(Level.INFO, methodName + " : interruped waitAvailable");
				}
			}
        } else {
        	if(numWriter.get() == 0){
        		log.log(Level.INFO, methodName + " : readSelector.select");
        		readReady = this.mReadSelector.select();
            	if(readReady > 0){
            		readFromSockets();
            	}
        	} else {
        		log.log(Level.INFO, methodName + " : writeSelector.select(100)");
        		writeReady = this.mWriteSelector.select(100);
        		if(writeReady > 0) {
    	        	writeToSockets();
    	        }
        		
        		log.log(Level.INFO, methodName + " : readSelector.selectNow");
        		readReady = this.mReadSelector.selectNow();
            	if(readReady > 0){
            		readFromSockets();
            	}
        	}
        }
    }
    

    private void takeNewSockets() {
    	final String methodName = "[" + threadName + "] takeNewSockets";
    	
    	String socketId;
    	
    	SocketChannel socketChannel = this.inboundSocketQueue.poll();
    	
        while(socketChannel != null){

        	log.log(Level.INFO, methodName + " : new socket");
        	
            IMessageReader messageReader = this.mMessageReaderFactory.createMessageReader();
            IMessageWriter messageWriter = this.mMessageWriterFactory.createMessageWriter();

            SocketSession socketSession = new SocketSession(socketChannel, messageReader, messageWriter);
            socketId = socketSession.getSocketId();
            this.sessionMap.put(socketId, socketSession);
            
            try {
				socketChannel.configureBlocking(false);
				socketChannel.register(this.mReadSelector, SelectionKey.OP_READ, socketSession);
				socketChannel.register(this.mWriteSelector, SelectionKey.OP_WRITE, socketSession);
				numReader.incrementAndGet();
				if(callbackWorker != null) {
					callbackWorker.onConnectionStarted(socketId);
				}
			} catch (IOException e) {
				log.log(Level.SEVERE, methodName + " : " + e.getMessage());
				
				if(callbackWorker != null) {
					callbackWorker.onFailure(e);
				}
			}
            
            
        	socketChannel = this.inboundSocketQueue.poll();
        }
    }
    
    private void removeSockets() {
		final String methodName = "[" + this.threadName + "] removeSockets";
		
		ISocketSession socketSession = null;
		String sessionId = removeSocketQueue.poll();

		while(sessionId != null){
			log.log(Level.INFO, methodName + " : remove socket");
			
            socketSession = sessionMap.remove(sessionId);
            
            if(socketSession != null) {
            	log.log(Level.INFO, methodName + " : socket exist");
            	socketSession.getChannel().keyFor(mReadSelector).cancel();
                socketSession.getChannel().keyFor(mWriteSelector).cancel();
                
                numReader.decrementAndGet();
                
                try {
    				socketSession.getChannel().close();
    				
    				if(callbackWorker != null) {
                		callbackWorker.onConnectionLost(socketSession.getSocketId());
                	}
    			} catch (IOException e) {
    				log.log(Level.INFO, methodName, e);
    	        	
    	        	if(callbackWorker != null) {
    	        		callbackWorker.onFailure(e);
    	            }
    			}    
            }else{
            	log.log(Level.INFO, methodName + " : socket don't exist");
            }
	        
	        sessionId = removeSocketQueue.poll();
		}
	}

    private void readFromSockets() {
    	
        Set<SelectionKey> selectedKeys = this.mReadSelector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while(keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();

            readFromSocket(key);

            keyIterator.remove();
        }
        selectedKeys.clear(); 
    }

    private void readFromSocket(SelectionKey key) {
    	final String methodName = "[" + threadName + "] readFromSocket";
    	
        ISocketSession socketSession = (ISocketSession) key.attachment();
        
        try {
        	log.log(Level.INFO, methodName + " : socketSession.read start");
			socketSession.read(this.readBuffer);
			log.log(Level.INFO, methodName + " : socketSession.read finish");
		} catch (IOException e) {
			log.log(Level.INFO, methodName + " : " + e.getMessage());
		}

        Message newMessage = socketSession.dequeue();
        while(newMessage != null){
            
        	if(callbackWorker != null) {
        		callbackWorker.onMessageArrived(newMessage);
        	}

            newMessage = socketSession.dequeue();
        }


        if(socketSession.isEndOfStreamReached()){
        	log.log(Level.INFO, methodName + " : close socket " + socketSession.getSocketId());
            this.sessionMap.remove(socketSession.getSocketId());
            key.cancel();
            
            numReader.decrementAndGet();
            
            try {
				socketSession.getChannel().close();
			} catch (IOException e) {
				log.log(Level.INFO, methodName, e);
			}
            
            if(callbackWorker != null) {
        		callbackWorker.onConnectionLost(socketSession.getSocketId());
        	}
        }
    }
    
    private void writeToSockets() {
    	final String methodName = "[" + threadName + "] writeToSockets";
    	
        Set<SelectionKey> selectionKeys = this.mWriteSelector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectionKeys.iterator();

        while(keyIterator.hasNext()){
            SelectionKey key = keyIterator.next();

            ISocketSession socketSession = (ISocketSession) key.attachment();
            if(!socketSession.isEmpty()){
	            try {
	            	log.log(Level.INFO, methodName + " : socketSession.write start " + socketSession.getSocketId());
					socketSession.write(this.writeBuffer);
					log.log(Level.INFO, methodName + " : socketSession.write finish");
				} catch (IOException e) {
					log.log(Level.INFO, methodName, e);
				}
	            if(socketSession.isEmpty()){
	                this.nonEmptyToEmptySockets.add(socketSession);
	            }
            }
            

            keyIterator.remove();
        }

        selectionKeys.clear();

        
    }

    

    private void takeNewOutboundMessages() {
    	final String methodName = "[" + threadName + "] takeNewOutboundMessages";
    	
    	Message inMessage = this.inboundMessageQueue.poll();

        while(inMessage != null){
        	ISocketSession session = this.sessionMap.get(inMessage.socketID);
 			if(session != null){
 				log.log(Level.INFO, methodName + " new message for sessionId: " + session.getSocketId());
 				
	            if(session.isEmpty()){
	                if(!nonEmptyToEmptySockets.remove(session)){
	                	emptyToNonEmptySockets.add(session);
	                }
	                
	                log.log(Level.INFO, methodName + " : active socket writer");
	            }
	            session.enqueue(inMessage);
    		}
        	
 			inMessage = this.inboundMessageQueue.poll();
        }
        
        cancelEmptySockets();
        
        registerNonEmptySockets();
    }
    
    private void registerNonEmptySockets(){
        for(ISocketSession socketSession : emptyToNonEmptySockets){
            //try {
            	log.log(Level.INFO, "register socket: " + socketSession.getSocketId());
            	//socketSession.getChannel().register(this.mWriteSelector, SelectionKey.OP_WRITE, socketSession);
            	//log.log(Level.INFO, "register finish");
            	
            	log.log(Level.INFO, "numWriter: " + numWriter.incrementAndGet());
            //} catch (ClosedChannelException e) {
            //	log.log(Level.INFO, "SocketChannel register: ", e);
            //}
        }
        emptyToNonEmptySockets.clear();
    }

    private void cancelEmptySockets() {
        for(ISocketSession socketSession : nonEmptyToEmptySockets){
        	log.log(Level.INFO, "cancel socket:" + socketSession.getSocketId());
            //socketSession.getChannel().keyFor(this.mWriteSelector).cancel();
        	//log.log(Level.INFO, "cancel finish");
        	
            log.log(Level.INFO, "numWriter: " + numWriter.decrementAndGet());
        }
        nonEmptyToEmptySockets.clear();
    }
}

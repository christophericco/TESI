package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Acceptor implements Runnable {
	
	interface ICallbackAccept {
		void onServerStarted(int port);
		void onServerStopped(int port);
		void onServerFailure(Throwable exception);
		void onIncomingSocket(SocketChannel socket);
	}
	
	private static final String CLASS_NAME = Acceptor.class.getName();
	private static final Logger log = Logger.getLogger(CLASS_NAME);

	private volatile boolean running = false;
	private Object lifecycle = new Object();
	private Object workAvailable = new Object();
	private AtomicInteger numServerSocket = new AtomicInteger();
	private AtomicInteger addRequests = new AtomicInteger();
	private Queue<Integer> removeRequests = new LinkedList<>();
	
	private Thread sendThread = null;
	private Map<Integer, ServerSocketChannel> mapPortToSocket = new HashMap<>();

	private String threadName;
	private final Semaphore opAvailable = new Semaphore(1);
	private Future<?> senderFuture;
	
	private Selector mAcceptSelector = null;
	private ICallbackAccept mCallbackAccept = null;
	
	public Acceptor(ICallbackAccept callbackAccept) {
		this.mCallbackAccept = callbackAccept;
	}
	
	public void start(String threadName, ExecutorService executorService) {
		this.threadName = threadName;
		final String methodName = "[" + this.threadName + "] start";
		
		synchronized (lifecycle) {
			if (!running) {
				addRequests.set(0);
				mapPortToSocket.clear();
				removeRequests.clear();
				running = true;
				senderFuture = executorService.submit(this);
			}
		}
		
		log.log(Level.INFO, methodName);
	}
	
	public void stop() {
		final String methodName = "[" + this.threadName + "] stop";

		synchronized (lifecycle) {
			if (running && senderFuture != null) {
				running = false;
				
				if(numServerSocket.get() == 0){
					synchronized (workAvailable) {
						workAvailable.notifyAll();
					}
				}
				
				senderFuture.cancel(true);
			}
			
			sendThread = null;
		}
		
		log.log(Level.INFO, methodName);
	}

	@Override
	public void run() {
		final String methodName = "[" + threadName + "] run";
		sendThread = Thread.currentThread();
		sendThread.setName(threadName);
		
		log.log(Level.INFO, methodName);
		
		try {
			mAcceptSelector = Selector.open();
		} catch (IOException e) {
			running = false;
			log.log(Level.INFO, methodName + " : " + e.getMessage());
		}
		
		while(running) {
			try{
				opAvailable.acquire();
				addServerSockets();
				removeServerSockets();
			}catch(InterruptedException e){
				running = false;
				log.log(Level.INFO, methodName, e);
			}finally {
				opAvailable.release();
			}
			
			
			if(numServerSocket.get() == 0) {
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
				try {
					log.log(Level.INFO, methodName + " : select");
					int readReady = mAcceptSelector.select();
			        if(readReady > 0){
			        	takeNewSockets();
			        }
				} catch(IOException e) {
					running = false;
					log.log(Level.INFO, methodName + " : " + e.getMessage());
				}
			}
		}
		
		closeSockets();
	}
	
	private void closeSockets() {
		final String methodName = "[" + this.threadName + "] closeSockets";
		log.log(Level.INFO, methodName);
		
        try {
            this.mAcceptSelector.close();
        } catch (IOException e) {
        	log.log(Level.SEVERE, "AcceptSelector close: ", e);
        }

        for(ServerSocketChannel sc : mapPortToSocket.values()){
            try {
                sc.close();
            } catch (IOException e) {
            	log.log(Level.SEVERE, "ServerSocketChannel close: ", e);
            }
        }
    }
	
	private void addServerSockets() {
		final String methodName = "[" + this.threadName + "] addNewServerSockets";
		
		ServerSocketChannel serverSocket;
		int port = 0;
		int nReq = addRequests.get();
		while(nReq > 0){
	        try{
	            serverSocket = ServerSocketChannel.open();
	            serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
	            serverSocket.bind(null, 10);
	            serverSocket.configureBlocking(false);
	            serverSocket.register(mAcceptSelector, SelectionKey.OP_ACCEPT, serverSocket);
	            port = serverSocket.socket().getLocalPort();
	            
	            mapPortToSocket.put(port, serverSocket);
	            numServerSocket.incrementAndGet();
	            
	            log.log(Level.INFO, methodName + " : new server socket");
	            
	            if(mCallbackAccept != null) {
	            	mCallbackAccept.onServerStarted(port);
	            }
	        } catch(IOException e){
	        	log.log(Level.SEVERE, methodName, e);
	        	
	        	if(mCallbackAccept != null) {
	            	mCallbackAccept.onServerFailure(e);
	            }
	        }
	        
	        nReq = addRequests.decrementAndGet();
		}
	}
	
	private void removeServerSockets() {
		final String methodName = "[" + this.threadName + "] removeNewServerSockets";
		
		ServerSocketChannel serverSocket = null;
		Integer port = removeRequests.poll();

		while(port != null){
	        try{
	            serverSocket = mapPortToSocket.remove(port);
	            
	            if(serverSocket != null) {
 		            numServerSocket.decrementAndGet();
 		            serverSocket.keyFor(mAcceptSelector).cancel();
 		            serverSocket.socket().close();
 		            
 		            log.log(Level.INFO, methodName + " : remove server socket " + port);
 		            
 		            if(mCallbackAccept != null) {
 		            	mCallbackAccept.onServerStopped(port);
 		            }
 	            }
	            
	        } catch(IOException e){
	        	log.log(Level.SEVERE, methodName, e);
	        	
	        	if(mCallbackAccept != null) {
	            	mCallbackAccept.onServerFailure(e);
	            }
	        }
	        
	        port = removeRequests.poll();
		}
	}
	
	private void takeNewSockets() throws IOException {
    	Set<SelectionKey> selectedKeys = mAcceptSelector.selectedKeys();
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while(keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();

            acceptFromSocket(key);

            keyIterator.remove();
        }
        selectedKeys.clear();  
    }
    
    private void acceptFromSocket(SelectionKey key) throws IOException{
    	final String methodName = "[" + this.threadName + "] acceptFromSocket";
    	
    	ServerSocketChannel serverSocket = (ServerSocketChannel) key.attachment();
    	SocketChannel newSocketChannel = serverSocket.accept();
        while(newSocketChannel != null){
        	log.log(Level.INFO, methodName + " : new socket");
        	
        	if(mCallbackAccept != null) {
        		mCallbackAccept.onIncomingSocket(newSocketChannel);
        	}
        	
            newSocketChannel = serverSocket.accept();
        }
    }
	
	public void addServerSocket(){
		final String methodName = "[" + this.threadName + "] add";
		
		try{
			opAvailable.acquire();
			int numTemp = addRequests.getAndIncrement();
			
			if(numServerSocket.get() == 0 && numTemp == 0) {
				log.log(Level.INFO, methodName + " : workAvailable.notifyAll");
				synchronized (workAvailable) {
					workAvailable.notifyAll();
				}
			} else {
				if(numTemp == 0){
					log.log(Level.INFO, methodName + " : mAcceptSelector.wakeup");
					
					mAcceptSelector.wakeup();
				}
			}
		}catch(InterruptedException e){
			log.log(Level.INFO, methodName, e);
		}finally {
			opAvailable.release();
		}
	}
	
	public void removeServerSocket(int port){
		final String methodName = "[" + this.threadName + "] remove";
		
		try{
			opAvailable.acquire();
			boolean empty = removeRequests.isEmpty();
			
			if(numServerSocket.get() > 0) {
				removeRequests.offer(port);
				
				if(empty){
					log.log(Level.INFO, methodName + " : mAcceptSelector.wakeup");
					
					mAcceptSelector.wakeup();	
				}
			}
		}catch(InterruptedException e){
			log.log(Level.INFO, methodName, e);
		}finally {
			opAvailable.release();
		}
		
	}

}

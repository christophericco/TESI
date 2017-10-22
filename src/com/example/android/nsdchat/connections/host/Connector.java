package com.example.android.nsdchat.connections.host;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Connector implements Runnable {

	interface ICallbackConnect {
		void onConnectionFailure(Throwable exception);
		void onOutcomingSocket(SocketChannel socket);
	}
	
	private static final String CLASS_NAME = Connector.class.getName();
	private static final Logger log = Logger.getLogger(CLASS_NAME);

	private volatile boolean running = false;
	private Object lifecycle = new Object();
	private Object workAvailable = new Object();
	private Semaphore opAvailable = new Semaphore(1);
	private AtomicInteger numConnectionPending = new AtomicInteger();
	private List<SocketChannel> connections = new ArrayList<>();
	
	private Thread sendThread = null;

	private String threadName;
	private Future<?> senderFuture;
	
	
	private Queue<InetSocketAddress> socketList = new LinkedList<>();
	
	private Selector mConnectSelector = null;
	private ICallbackConnect mCallbackConnect = null;
	
	public Connector(ICallbackConnect callbackConnect) {
		this.mCallbackConnect = callbackConnect;
	}
	
	public void start(String threadName, ExecutorService executorService) {
		this.threadName = threadName;
		final String methodName = "[" + this.threadName + "] start";
		
		synchronized (lifecycle) {
			if (!running) {
				numConnectionPending.set(0);
				socketList.clear();
				connections.clear();
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
				
				if(numConnectionPending.get() == 0){
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
			mConnectSelector = Selector.open();
		} catch (IOException e) {
			running = false;
			log.severe(methodName + " : " + e.getMessage());
		}
		
		while(running) {
			
			try{
				opAvailable.acquire();
				takeNewSockets();
			}catch(InterruptedException e){
				running = false;
				log.log(Level.INFO, methodName + " : interruped opAvailable");
			}finally{
				opAvailable.release();
			}
			
			if(numConnectionPending.get() == 0) {
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
					log.log(Level.INFO, methodName + " : select(200)");
					int readReady = mConnectSelector.select();
			        if(readReady > 0){
			        	completeNewConnections();
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
            this.mConnectSelector.close();
        } catch (IOException e) {
        	log.log(Level.SEVERE, "ConnectSelector close: ", e);
        }

        for(SocketChannel sc : connections){
            try {
                sc.close();
            } catch (IOException e) {
            	log.log(Level.SEVERE, "SocketChannel close: ", e);
            }
        }
        
        connections.clear();
    }
	
	private void takeNewSockets() {
		final String methodName = "[" + this.threadName + "] takeNewSockets";
		
		SocketChannel socket;
		InetSocketAddress address = socketList.poll();
		
		while(address != null) {
	        try{
	        	socket = SocketChannel.open();
	            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
	            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
	            socket.configureBlocking(false);
	            socket.register(mConnectSelector, SelectionKey.OP_CONNECT, socket);
	            socket.connect(address);
	            
	            connections.add(socket);
				numConnectionPending.incrementAndGet();
				
	            log.log(Level.INFO, methodName + " : socket.connect(address)");
	            
	        } catch(IOException e){
	        	log.log(Level.INFO, methodName + " : " + e.getMessage());
	        	
	        	if(mCallbackConnect != null) {
	        		mCallbackConnect.onConnectionFailure(e);
	            }
	        }
	        
	        address = socketList.poll();
		}
	}
	
	private void completeNewConnections() throws IOException{
		final String methodName = "[" + this.threadName + "] completeNewConnections";
        
    	Set<SelectionKey> selectedKeys = this.mConnectSelector.selectedKeys();
    	Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while(keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
    		SocketChannel socket = (SocketChannel) key.attachment();
    		if(socket.finishConnect()){
    			socket.keyFor(mConnectSelector).cancel();
    			connections.remove(socket);
				numConnectionPending.decrementAndGet();
				
				
    			log.log(Level.INFO, methodName + " : new socket");
    			if(mCallbackConnect != null) {
    				mCallbackConnect.onOutcomingSocket(socket);
    			}
    		}
    		
    		keyIterator.remove();   
        }
        selectedKeys.clear();
        
    }
    
	
	public void addSocket(InetSocketAddress address){
		final String methodName = "[" + this.threadName + "] add";
		
		try{
			opAvailable.acquire();
			boolean empty = socketList.isEmpty();
			socketList.offer(address);
			if(numConnectionPending.get() == 0 && empty) {
				log.log(Level.INFO, methodName + " : workAvailable.notifyAll");
				
				synchronized (workAvailable) {
					workAvailable.notifyAll();
				}
			} else {
				if(empty) {
					log.log(Level.INFO, methodName + " : mConnectorSelector.wakeup");
					
					mConnectSelector.wakeup();
				}
			}
		}catch(InterruptedException e){
			log.log(Level.INFO, methodName, e);
		} finally {
			opAvailable.release();
		}
	}

}

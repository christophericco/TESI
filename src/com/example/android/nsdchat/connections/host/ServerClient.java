package com.example.android.nsdchat.connections.host;


import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.android.nsdchat.connections.host.Acceptor.ICallbackAccept;
import com.example.android.nsdchat.connections.host.Connector.ICallbackConnect;
import com.example.android.nsdchat.connections.host.Worker.ICallbackWorker;

import it.cricco.events.ActiveConnectionEvent;
import it.cricco.events.ErrorEvent;
import it.cricco.events.EventDispatcher.IEventListener;
import it.cricco.events.IDispatcher;
import it.cricco.events.IncomingConnectionEvent;
import it.cricco.events.IncomingMessageEvent;
import it.cricco.events.LostConnectionEvent;
import it.cricco.events.OutcomingConnectionEvent;
import it.cricco.events.StartedServerEvent;
import it.cricco.events.StoppedServerEvent;

public class ServerClient implements ICallbackAccept, ICallbackConnect, ICallbackWorker{

	private static final String CLASS_NAME = ServerClient.class.getSimpleName();
	private static final Logger log = Logger.getLogger(CLASS_NAME);
	
	private IEventListener listener = new IEventListener() {
		
		@Override
		public void startConnection(String hostname, int port) {
			createConnection("localhost", port);
		}
		
		@Override
		public void sendMessage(Message message) {
			ServerClient.this.sendMessage(message);
		}

		@Override
		public void stopConnection(String sessionId) {
			removeConnection(sessionId);
		}
	};
	
	public IEventListener getListener(){
		return listener;
	}

    private ExecutorService executor;

    private MessageReaderFactory mMessageReaderFactory = null;
    private MessageWriterFactory mMessageWriterFactory = null;
    
    private Acceptor acceptor = null;
    private Connector connector = null;
    private Worker workerProcessor = null;
    
    private IDispatcher dispatcher = null;
    
    private Map<Integer, Integer> mapServer = new HashMap<>();
    private Queue<Integer> nameServices = new LinkedList<>();

    public ServerClient(MessageReaderFactory messageReaderFactory,
                  MessageWriterFactory messageWriterFactory) {
        this.mMessageReaderFactory = messageReaderFactory;
        this.mMessageWriterFactory = messageWriterFactory;
    }
    
    public void setDispatcher(IDispatcher dispatcher){
    	this.dispatcher = dispatcher;
    }

    public void start() {
    	final String methodName = "[" + Thread.currentThread().getName() + "] start";
    	log.log(Level.INFO, methodName);
    	
        executor = Executors.newFixedThreadPool(3);

        this.workerProcessor = 
        		new Worker(mMessageReaderFactory, 
        				mMessageWriterFactory, 
        				this);
        this.acceptor = new Acceptor(this);
        this.connector = new Connector(this);
        
        
        acceptor.start("AcceptorThread", executor);
        connector.start("ConnectorThread", executor);
        workerProcessor.start("WorkerThread", executor);
    
        log.log(Level.INFO, methodName + " : processors create");
    }

    public void stop() throws InterruptedException{
        acceptor.stop();
        connector.stop();
        workerProcessor.stop();
        
        executor.shutdown();
        //executor.awaitTermination(200, TimeUnit.MILLISECONDS);
    }

    public void sendMessage(Message message){
        workerProcessor.enqueue(message);
    }
    
    public void createServer(int port){
    	this.acceptor.addServerSocket();
    	synchronized (nameServices) {
    		nameServices.offer(port);
		}
    }
    public void removeServer(int port){
    	this.acceptor.removeServerSocket(port);
    }
    public void createConnection(String hostname, int port){
    	this.connector.addSocket(new InetSocketAddress(hostname, port));
    }
    public void removeConnection(String sessionId){
    	this.workerProcessor.removeSocket(sessionId);
    }
    
    @Override
	public void onOutcomingSocket(SocketChannel socket) {
    	if(dispatcher != null) {
			String sessionId = socket.socket().getPort() + "." + socket.socket().getLocalPort();
			dispatcher.disruptorPublish(new OutcomingConnectionEvent(sessionId, socket.socket().getPort()));
		}
		this.workerProcessor.newSocket(socket);
	}

	@Override
	public void onIncomingSocket(SocketChannel socket) {
		if(dispatcher != null) {
			String sessionId = socket.socket().getPort() + "." + socket.socket().getLocalPort();
			dispatcher.disruptorPublish(new IncomingConnectionEvent(sessionId, socket.socket().getLocalPort()));
		}
		this.workerProcessor.newSocket(socket);
	}

	@Override
	public void onConnectionStarted(String channelId) {
		log.log(Level.INFO, "New connection started: " + channelId);
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new ActiveConnectionEvent(channelId));
		}
	}

	@Override
	public void onConnectionLost(String channelId) {
		log.log(Level.INFO, "Connection lost: " + channelId);
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new LostConnectionEvent(channelId));
		}
	}

	@Override
	public void onFailure(Throwable exception) {
		log.log(Level.INFO, "Worker error: " + exception);
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new ErrorEvent(exception));
		}
	}

	@Override
	public void onMessageArrived(Message message) {
		log.log(Level.INFO, "New message arrived: " + message.getPayload().toString());
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new IncomingMessageEvent(message.socketID, message));
		}
	}

	@Override
	public void onConnectionFailure(Throwable exception) {
		log.log(Level.INFO, "New connecting failed: " + exception);
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new ErrorEvent(exception));
		}
	}

	@Override
	public void onServerStarted(int newPort) {
		log.log(Level.INFO, "New server started: " + newPort);
		synchronized (nameServices) {
			Integer port = nameServices.poll();
			if(port != null){
				log.log(Level.INFO, "Server proxy for server: " + port);
				mapServer.put(newPort, port);
				if(dispatcher != null) {
					dispatcher.disruptorPublish(new StartedServerEvent(port, newPort));
				}
			}
		}
		
	}

	@Override
	public void onServerFailure(Throwable exception) {
		log.log(Level.INFO, "New server failed: " + exception);
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new ErrorEvent(exception));
		}
	}

	@Override
	public void onServerStopped(int newPort) {
		log.log(Level.INFO, "Remove server: " + newPort);
		int port = mapServer.remove(newPort);
		
		if(dispatcher != null) {
			dispatcher.disruptorPublish(new StoppedServerEvent(port, newPort));
		}
	}

	
}

package it.cricco.events;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.example.android.nsdchat.connections.host.IMessageRouter;
import com.example.android.nsdchat.connections.host.Message;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.MultiThreadedClaimStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

public class EventDispatcher implements IDispatcher, Observer, EventHandler<EventValue>{
	
	public interface IEventListener {
		void sendMessage(Message message);
		void startConnection(String hostname, int port);
		void stopConnection(String sessionId);
	}
	
	private static final String CLASS_NAME = EventDispatcher.class.getName();
	private static final Logger log = Logger.getLogger(CLASS_NAME);
	
	private Disruptor<EventValue> mDisruptor = null;
    private ExecutorService mExecutorService = null;
    private RingBuffer<EventValue> mRingBuffer = null;
    
    private IMessageRouter messageRouter;
    private IEventListener eventListener;
    
    private List<Message> messageTempQueue = new LinkedList<>();
    private Map<String, ConnectionState> connections = new HashMap<>();
	//private Timer timer = new Timer("MessageRouter Timer");
    
    public EventDispatcher(IMessageRouter messageRouter, IEventListener eventListener){
    	this.messageRouter = messageRouter;
    	this.eventListener = eventListener;
    }
    
	@SuppressWarnings("unchecked")
	public void start(){
		mExecutorService = Executors.newSingleThreadExecutor();
        mDisruptor = new Disruptor<EventValue>(EventValue.FACTORY, 
        		mExecutorService, 
        		new MultiThreadedClaimStrategy(1024) , 
        		new BlockingWaitStrategy());
        mDisruptor.handleEventsWith(this);
        
        mRingBuffer = mDisruptor.start();
        
        log.log(Level.INFO, "EventDispatcher started.");
	}
	
	public void stop() {
        if(mDisruptor == null || mExecutorService == null){
            throw new RuntimeException("Invoked stop on MessagingService that wasn't initialized");
        }

        //timer.cancel();
        mDisruptor.shutdown();
        mExecutorService.shutdown();
        log.log(Level.INFO, "EventDispatcher stopped.");
    }
	
	@Override
	public void disruptorPublish(AbstractEvent msgEvent) {
        log.log(Level.INFO, "Publish event: " + msgEvent);
        long sequence = mRingBuffer.next();
        EventValue event = mRingBuffer.get(sequence);

        event.setMessagingEvent(msgEvent);

        mRingBuffer.publish(sequence);
    }

	@Override
	public void onEvent(EventValue event, long sequence, boolean endOfBatch) throws Exception {
		AbstractEvent msgEvent = event.getMessagingEvent();
		if(msgEvent instanceof IncomingConnectionEvent){
			log.log(Level.INFO, "IncomingConnectionEvent");
			String sessionId = ((IncomingConnectionEvent) msgEvent).getSessionId();
			int port = ((IncomingConnectionEvent) msgEvent).getServerPort();
			ConnectionState connectionState = new ConnectionState(sessionId, port, State.IN);
			connectionState.addObserver(this);
			connections.put(sessionId,connectionState);
			
			port = messageRouter.addIncomingClient(sessionId, port);
			eventListener.startConnection("host", port);
            //callback
        }
		if(msgEvent instanceof OutcomingConnectionEvent){
			log.log(Level.INFO, "OutcomingConnectionEvent");
			String sessionId = ((OutcomingConnectionEvent) msgEvent).getSessionId();
			int port = ((OutcomingConnectionEvent) msgEvent).getServerPort();
			ConnectionState connectionState = new ConnectionState(sessionId, port, State.OUT);
			connectionState.addObserver(this);
			connections.put(sessionId,connectionState);
			
			messageRouter.addOutcomingClient(sessionId, port);
            //callback
        }
		if(msgEvent instanceof ActiveConnectionEvent){
			log.log(Level.INFO, "ActiveConnectionEvent");
            String sessionId = ((ActiveConnectionEvent) msgEvent).getSessionId();
            ConnectionState connectionState = connections.get(sessionId);
            if(connectionState != null){
            	connectionState.setState(State.ACTIVE);
            }
            //callback
        }
        if(msgEvent instanceof LostConnectionEvent){
        	log.log(Level.INFO, "LostConnectionEvent");
        	final String sessionId = ((LostConnectionEvent) msgEvent).getSessionId();
        	ConnectionState connectionState = connections.remove(sessionId);
        	if(connectionState != null){
        		connectionState.deleteObserver(this);
        	}
        	messageTempQueue.stream().filter(m -> m.socketID == sessionId).collect(Collectors.toList())
        	.forEach(m -> messageTempQueue.remove(m));
            final String sessionId2 = messageRouter.removeClient(sessionId);
            if(sessionId2 != null){
            	eventListener.stopConnection(sessionId2);
            }
            
            messageTempQueue.stream().filter(m -> m.socketID == sessionId2).collect(Collectors.toList())
            .forEach(m -> messageTempQueue.remove(m));
            //callback
        }
        if(msgEvent instanceof StartedServerEvent){
        	log.log(Level.INFO, "StartedServerEvent");
        	int newPort = ((StartedServerEvent) msgEvent).getNewPort();
        	int port = ((StartedServerEvent) msgEvent).getPort();
        	messageRouter.addService(port, newPort);
        	//callback
        }
        if(msgEvent instanceof StoppedServerEvent){
        	log.log(Level.INFO, "StoppedServerEvent");
        	int newPort = ((StoppedServerEvent) msgEvent).getNewPort();
        	messageRouter.removeService(newPort);
        	//callback
        }
        if(msgEvent instanceof IncomingMessageEvent){
        	log.log(Level.INFO, "IncomingMessageEvent");
        	String sessionId = ((IncomingMessageEvent) msgEvent).getSessionId();
        	Message message = (Message) ((IncomingMessageEvent) msgEvent).getMessage();
        	
        	if(connections.containsKey(sessionId)){
        		ConnectionState con = connections.get(sessionId);
        		if(con.state.equals(State.ACTIVE)){
        			String routeId = messageRouter.routing(sessionId);
        			if(routeId != null){
        				message.socketID = routeId;
        				ConnectionState con2 = connections.get(routeId);
                		if(con2.state.equals(State.ACTIVE)){
                			eventListener.sendMessage(message);
                		}else{
                			con2.queue.offer(message);
                		}
                		
        			}else{
        				log.log(Level.INFO, "Routing unknown for socket: " + sessionId);
        				messageTempQueue.add(message);
        			}
        		}else{
        			log.log(Level.INFO, "message enqueue for socket: " + sessionId);
        			con.queue.offer(message);
        		}
        	}else{
        		log.log(Level.INFO, "Message discarded. Socket don't exist: " + sessionId);
        		//messageTempQueue.add(message);
        	}
        }
		
	}
	
	class ConnectionState extends Observable {
		private State state;
		private String sessionId;
		private int port;
		private Queue<Message> queue = new LinkedList<>(); 
		
		ConnectionState(String sessionId, int port, State state) {
			this.sessionId = sessionId;
			this.port = port;
			this.state = state;
		}
		
		public void setState(State state){
			this.state = state;
			setChanged();
			notifyObservers(sessionId);
		}
	}
	
	enum State {
		IN,
		OUT,
		ACTIVE;
	}

	@Override
	public void update(Observable o, Object arg) {
		log.log(Level.INFO, "Observer update, arg: " + arg.toString());
		ConnectionState con = (ConnectionState) o;
		if(con.state.equals(State.ACTIVE)){
			List<Message> msgs = messageTempQueue.stream()
				.filter(m -> {
							String routeId = messageRouter.routing(m.socketID);
							if(routeId != null && routeId.equals(con.sessionId)){
								m.socketID = routeId;
								return true;
							}else{
								return false;
							}
						
					})
				.sorted((m1, m2) -> {
					if(m1.timestamp == m2.timestamp)
						return 0;
					else
						if(m1.timestamp < m2.timestamp)
							return -1;
						else
							return 1;
					}).collect(Collectors.toList());
				
			msgs.forEach(m -> {
					log.log(Level.INFO, "send msg socket: " + m.socketID + ", timestamp: " + m.timestamp);
					eventListener.sendMessage(m);
					messageTempQueue.remove(m);
				});
			
			
			
			Message message = con.queue.poll();
			while(message != null){
				String sessionId = con.sessionId;
				String routeId = messageRouter.routing(sessionId);
				if(routeId != null){
					message.socketID = routeId;
					eventListener.sendMessage(message);
				}else{
					log.log(Level.INFO, "Routing unknown for socket: " + sessionId);
					messageTempQueue.add(message);
				}
				
				message = con.queue.poll();
			}
		}
	}

}

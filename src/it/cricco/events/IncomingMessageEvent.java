package it.cricco.events;

public class IncomingMessageEvent extends AbstractEvent {
	
	private String sessionId;
	private Object message;
	
	public IncomingMessageEvent(String sessionId, Object message){
		this.sessionId = sessionId;
		this.message = message;
	}
	
	public String getSessionId(){
		return sessionId;
	}
	
	public Object getMessage(){
		return message;
	}

}

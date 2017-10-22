package it.cricco.events;

public class OutcomingConnectionEvent extends AbstractEvent {
	
	private String sessionId;
	private int serverPort;

    public OutcomingConnectionEvent(String sessionId, int serverPort) {
        super();
        this.sessionId = sessionId;
        this.serverPort = serverPort;
    }

    public String getSessionId() {
        return sessionId;
    }
    
    public int getServerPort() {
    	return serverPort;
    }
}

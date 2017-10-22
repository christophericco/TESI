package it.cricco.events;

public class IncomingConnectionEvent extends AbstractEvent {
	
	private String sessionId;
	private int serverPort;

    public IncomingConnectionEvent(String sessionId, int serverPort) {
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

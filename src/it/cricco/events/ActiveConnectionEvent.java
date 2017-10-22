package it.cricco.events;

public class ActiveConnectionEvent extends AbstractEvent {
	
	private String sessionId;

    public ActiveConnectionEvent(String sessionId) {
        super();
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}

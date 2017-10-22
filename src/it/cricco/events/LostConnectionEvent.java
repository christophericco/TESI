package it.cricco.events;

public class LostConnectionEvent extends AbstractEvent {
	
	private String sessionId;

    public LostConnectionEvent(String sessionId) {
        super();
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}

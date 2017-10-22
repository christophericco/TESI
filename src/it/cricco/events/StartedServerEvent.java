package it.cricco.events;

public class StartedServerEvent extends AbstractEvent {

	private int port;
	private int newPort;
	
	public StartedServerEvent(int port, int newPort){
		this.port = port;
		this.newPort = newPort;
	}
	
	public int getPort(){
		return port;
	}

	public int getNewPort() {
    	return newPort;
    }
}

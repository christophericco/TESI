package it.cricco.events;

public class ErrorEvent extends AbstractEvent {

	private Throwable exception;
	
	public ErrorEvent(Throwable exception){
		this.exception = exception;
	}
	
	public Throwable getException(){
		return this.exception;
	}
}

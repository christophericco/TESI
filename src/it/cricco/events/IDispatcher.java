package it.cricco.events;

public interface IDispatcher {
	void disruptorPublish(AbstractEvent msg);
}

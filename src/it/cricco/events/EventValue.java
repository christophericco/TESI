package it.cricco.events;

import com.lmax.disruptor.EventFactory;

public class EventValue {
	
	private AbstractEvent event = null;

    public AbstractEvent getMessagingEvent(){
        return event;
    }

    public void setMessagingEvent(AbstractEvent event){
        this.event = event;
    }

    public static final EventFactory<EventValue> FACTORY = new EventFactory<EventValue>() {
        @Override
        public EventValue newInstance() {
            return new EventValue();
        }
    };
}

package ibm.labs.kc.order.query.model.events;

import ibm.labs.kc.order.query.model.Container;

public class ContainerOrderReleasedEvent extends ContainerEvent {
	
    private Container payload;
	
	public ContainerOrderReleasedEvent(long timestampMillis, String version, Container payload) {
        super(timestampMillis, ContainerEvent.TYPE_CONTAINER_ORDER_RELEASED, version);
        this.payload = payload;
    }

    public ContainerOrderReleasedEvent() {
    }

	public Container getPayload() {
		return payload;
	}

	public void setPayload(Container payload) {
		this.payload = payload;
	}

}

package ch.lin.youtube.hub.backend.api.app.service.event;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when the Hub configuration has been updated.
 */
public class ConfigUpdatedEvent extends ApplicationEvent {

    public ConfigUpdatedEvent(Object source) {
        super(source);
    }
}

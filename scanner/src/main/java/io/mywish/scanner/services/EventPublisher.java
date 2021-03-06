package io.mywish.scanner.services;

import io.mywish.scanner.model.BaseEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void publish(BaseEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}

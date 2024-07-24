package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import lombok.Value;
import org.comroid.api.func.util.Event;
import org.comroid.api.net.Rabbit;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;

@Value
public class RabbitMessagingService extends MessagingServiceBase<EntityService> {
    @Event.Subscriber(type = NotifyEvent.class)
    Queue<NotifyEvent> incomingQueue = new LinkedList<>();
    Rabbit.Exchange.Route<NotifyEvent> route;

    public RabbitMessagingService(String uri, EntityService service, Duration interval) {
        super(service, interval);

        var rabbit   = Rabbit.of(uri).orElseThrow();
        var exchange = rabbit.exchange("banmod");
        (this.route = exchange.route("", NotifyEvent.class)).register(this);
    }

    @Override
    protected void push(NotifyEvent event) {
        route.send(event);
    }

    @Override
    protected NotifyEvent[] pollNotifier() {
        NotifyEvent[] queue;
        synchronized (incomingQueue) {
            queue = incomingQueue.toArray(NotifyEvent[]::new);
            incomingQueue.clear();
        }
        return queue;
    }
}
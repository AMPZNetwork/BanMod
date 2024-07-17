package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.EntityType;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.tree.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
@NonFinal
public abstract class MessagingServiceBase<Service extends EntityService> extends Component.Base implements MessagingService {
    protected Service service;
    protected @NonFinal BigInteger ident;

    public MessagingServiceBase(Service service, Duration interval) {
        this.service = service;

        service.getScheduler().scheduleWithFixedDelay(() -> dispatch(pollNotifier()), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    protected abstract void push(NotifyEvent event);
    protected abstract NotifyEvent[] pollNotifier();

    @Override
    public final AlmostComplete<NotifyEvent.Builder> push() {
        return new AlmostComplete<>(NotifyEvent::builder, builder -> {
            var event       = builder.ident(ident).build();
            var relatedType = event.getRelatedType();
            var eventType   = event.getType();
            if (!eventType.test(relatedType))
                throw new IllegalArgumentException("%s event does not allow %s payloads".formatted(eventType, relatedType));
            push(event);
        });
    }

    private void dispatch(NotifyEvent... events) {
        if (events.length == 0) return;
        if (events.length > 1)
            for (var event : events)
                dispatch(event);
        var event = events[0];

        // nothing to do for HELLO
        var eventType = event.getType();
        if (eventType == NotifyEvent.Type.HELLO)
            return;

        // validate event
        var relatedType = event.getRelatedType();
        if (event.getRelatedId() == null || relatedType == null) {
            service.getBanMod().log().error("Invalid event received; data was null\n" + event);
            return;
        }
        if (!eventType.test(relatedType)) {
            service.getBanMod().log().error("Invalid packet received; %s event type does not allow %s payloads; ignoring it"
                    .formatted(eventType, relatedType));
            return;
        }

        // handle SYNC
        service.refresh(event.getRelatedType(), event.getRelatedId());
        if (event.getRelatedType() == EntityType.Infraction)
            service.getInfraction(event.getRelatedId()).ifPresent(service.getBanMod()::realize);
    }
}

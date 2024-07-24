package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.EntityType;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.func.util.Debug;
import org.comroid.api.tree.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
@NonFinal
public abstract class MessagingServiceBase<Entities extends EntityService> extends Component.Base implements MessagingService {
    protected           Entities   entities;
    protected @NonFinal BigInteger ident;

    public MessagingServiceBase(Entities entities, Duration interval) {
        this.entities = entities;

        entities.getScheduler()
                .scheduleWithFixedDelay(() -> {
                            try {
                                dispatch(pollNotifier());
                            } catch (Throwable t) {
                                Debug.log(entities.getBanMod().log(), "An error occurred during event dispatch", t);
                            }
                        },
                        interval.toMillis(),
                        interval.toMillis(),
                        TimeUnit.MILLISECONDS);
    }

    protected abstract void push(NotifyEvent event);

    protected abstract NotifyEvent[] pollNotifier();

    @Override
    public final AlmostComplete<NotifyEvent.Builder> push() {
        return new AlmostComplete<>(NotifyEvent::builder, builder -> {
            var event       = builder.ident(ident).build();
            var relatedType = event.getRelatedType();
            var eventType   = event.getType();
            if (relatedType != null && !eventType.test(relatedType))
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
            entities.getBanMod().log().error("Invalid event received; data was null\n" + event);
            return;
        }
        if (!eventType.test(relatedType)) {
            entities.getBanMod().log().error("Invalid packet received; %s event type does not allow %s payloads; ignoring it"
                    .formatted(eventType, relatedType));
            return;
        }

        // handle SYNC
        entities.refresh(event.getRelatedType(), event.getRelatedId());
        if (event.getRelatedType() == EntityType.Infraction)
            entities.getInfraction(event.getRelatedId()).ifPresent(entities.getBanMod()::realize);
    }
}

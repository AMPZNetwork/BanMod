package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.EntityType;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.tree.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Value
@NonFinal
public abstract class MessagingServiceBase<Entities extends EntityService> extends Component.Base implements MessagingService {
    static {
        // register service types
        new Type<PollingDatabase.Config, PollingMessagingService>("polling-db") {
            @Override
            public PollingMessagingService createService(BanMod mod, EntityService entities, PollingDatabase.Config config) {
                var configDbInfo = config.dbInfo();
                if (!mod.getDatabaseInfo().equals(configDbInfo))
                    entities = new HibernateEntityService(mod, configDbInfo);
                if (entities instanceof HibernateEntityService hibernate)
                    return new PollingMessagingService(hibernate, config.interval());
                return null;
            }
        };
        new Type<RabbitMQ.Config, RabbitMessagingService>("rabbit-mq") {
            @Override
            public RabbitMessagingService createService(BanMod mod, EntityService entities, RabbitMQ.Config config) {
                return new RabbitMessagingService(config.uri(), entities);
            }
        };
    }
    protected @NonFinal BigInteger ident;

    protected Entities entities;

    public MessagingServiceBase(Entities entities, Duration interval) {
        this.entities = entities;

        entities.getScheduler().scheduleWithFixedDelay(() -> dispatch(pollNotifier()), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
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

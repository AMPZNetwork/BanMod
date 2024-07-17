package com.ampznetwork.banmod.core.database;

import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.EntityType;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import lombok.Value;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.func.util.Debug;
import org.comroid.api.func.util.Stopwatch;
import org.comroid.api.tree.Component;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigInteger;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Value
public class PollingMessagingService extends Component.Base implements MessagingService {
    HibernateEntityService service;
    EntityManager          manager;
    Session    session;
    BigInteger ident;

    public PollingMessagingService(HibernateEntityService service, EntityManager manager, Duration interval) {
        this.service = service;
        this.manager = manager;
        this.session = manager.unwrap(Session.class);

        service.getScheduler()
                .scheduleWithFixedDelay(this::pollNotifier, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);

        // find recently used idents
        //noinspection unchecked
        var occupied = ((Stream<BigInteger>) service.wrapQuery(Query::getResultList, session.createSQLQuery("""
                select BIT_OR(ne.ident) as x
                from banmod_notify ne
                group by ne.ident, ne.timestamp
                order by ne.timestamp desc
                limit 50
                """)).stream())
                .map(BigInteger.class::cast)
                .filter(x -> x.intValue() != 0)
                .findAny()
                .orElse(BigInteger.valueOf(0xFFFF_FFFFL));

        // randomly try to get a new ident
        BigInteger x;
        var        c   = 0;
        var        rng = new Random();
        do {
            c += 1;
            x = BigInteger.ONE.shiftLeft(rng.nextInt(64));
        } while (c < 64 && (x.and(occupied.not()).intValue() == 0 || x.equals(occupied) || x.intValue() == 0));

        this.ident = x;

        // send HELLO
        push().complete(bld -> bld.type(NotifyEvent.Type.HELLO));
    }

    @Override
    public AlmostComplete<NotifyEvent.Builder> push() {
        return new AlmostComplete<>(NotifyEvent::builder, builder -> {
            var event       = builder.ident(ident).build();
            var relatedType = event.getRelatedType();
            var eventType   = event.getType();
            if (!eventType.test(relatedType))
                throw new IllegalArgumentException("%s event does not allow %s payloads".formatted(eventType, relatedType));
            service.save(event);
        });
    }

    private void pollNotifier() {
        var stopwatch = Stopwatch.start(this);
        var events = service.wrapTransaction(Connection.TRANSACTION_REPEATABLE_READ, () -> {
            var handle = Polyfill.<List<NotifyEvent>>uncheckedCast(manager.createNativeQuery("""
                            select ne.*
                            from banmod_notify ne
                            where ne.ident != :me and (ne.acknowledge & :me) = 0
                            order by ne.timestamp
                            """, NotifyEvent.class)
                    .setParameter("me", ident)
                    .getResultList());
            for (var event : handle.toArray(new NotifyEvent[0])) {
                // acknowledge
                var ack = manager.createNativeQuery("""
                                update banmod_notify ne
                                set ne.acknowledge = (ne.acknowledge | :me)
                                where ne.ident = :ident and ne.timestamp = :timestamp
                                """)
                        .setParameter("me", ident)
                        .setParameter("ident", event.getIdent())
                        .setParameter("timestamp", event.getTimestamp())
                        .executeUpdate();
                if (ack != 1) {
                    service.getBanMod()
                            .log()
                            .warn("Failed to acknowledge notification {}; ignoring it", event);
                    handle.remove(event);
                    continue;
                }

                // validate
                var relatedType = event.getRelatedType();
                var eventType   = event.getType();
                if (!eventType.test(relatedType)) {
                    service.getBanMod().log().error("Invalid packet received; %s event type does not allow %s payloads; ignoring it"
                            .formatted(eventType, relatedType));
                    handle.remove(event);
                }
            }
            return handle.toArray(new NotifyEvent[0]);
        });

        var duration = stopwatch.stop();
        if (events.length == 0)
            return;
        var msg = "Accepting %d events took %sms".formatted(events.length, duration.toMillis());
        if (Debug.isDebug()) // best log level handling EVER
            service.getBanMod()
                    .log()
                    .info(msg);
        else service.getBanMod()
                .log()
                .debug(msg);

        service.getScheduler().execute(() -> dispatch(events));
    }

    private void dispatch(NotifyEvent... events) {
        if (events.length == 0) return;
        if (events.length > 1)
            for (var event : events)
                dispatch(event);
        var event = events[0];
        if (event.getType() == NotifyEvent.Type.HELLO) return; // nothing to do
        // handle SYNC
        if (event.getRelatedId() == null || event.getRelatedType() == null) {
            service.getBanMod().log().error("Invalid SYNC event received; data was null");
            return;
        }
        service.refresh(event.getRelatedType(), event.getRelatedId());
        if (event.getRelatedType() == EntityType.Infraction)
            service.getInfraction(event.getRelatedId()).ifPresent(service.getBanMod()::realize);
    }
}

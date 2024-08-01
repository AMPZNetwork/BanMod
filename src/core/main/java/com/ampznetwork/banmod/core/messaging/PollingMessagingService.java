package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import com.ampznetwork.libmod.api.messaging.MessagingService;
import com.ampznetwork.libmod.api.messaging.NotifyEvent;
import lombok.Value;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.Debug;
import org.comroid.api.func.util.Stopwatch;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.math.BigInteger;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Value
public class PollingMessagingService extends MessagingServiceBase<HibernateEntityService> implements MessagingService.PollingDatabase {
    public static final Duration EventExpireTime = Duration.ofHours(1);
    EntityManager manager;
    Session       session;

    public PollingMessagingService(HibernateEntityService service, Duration interval) {
        super(service, interval);

        this.manager = service.getManager();
        this.session = manager.unwrap(Session.class);

        cleanup();

        // find recently used idents
        //noinspection unchecked
        var occupied = ((Stream<BigInteger>) service.wrapQuery(Connection.TRANSACTION_SERIALIZABLE, Query::getResultList, session.createSQLQuery("""
                select BIT_OR(ne.ident) as x
                from banmod_notify ne
                group by ne.ident, ne.timestamp
                order by ne.timestamp desc
                limit 50;
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
        } while (c < 62 && (x.and(occupied.not()).intValue() == 0 || x.equals(occupied) || x.intValue() == 0));

        this.ident = x;

        // ack all old events
        service.wrapQuery(Query::executeUpdate, manager.createNativeQuery("""
                update banmod_notify ne
                set ne.acknowledge = (ne.acknowledge | :me)
                """).setParameter("me", ident));

        // send HELLO
        push().complete(bld -> bld.type(NotifyEvent.Type.HELLO));
    }

    public void cleanup() {
        entities.wrapQuery(Query::executeUpdate, session.createNativeQuery("""
                delete from banmod_notify
                where timestamp < :expire or (timestamp & ident) > 0;
                """).setParameter("expire", Instant.now().minus(EventExpireTime)));
    }

    @Override
    protected void push(NotifyEvent event) {
        entities.save(event);
    }

    @Override
    protected NotifyEvent[] pollNotifier() {
        var stopwatch = Stopwatch.start(this);
        var events = entities.wrapTransaction(Connection.TRANSACTION_REPEATABLE_READ, () -> {
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
                    entities.getBanMod()
                            .log()
                            .warn("Failed to acknowledge notification {}; ignoring it", event);
                    handle.remove(event);
                }
            }
            return handle.toArray(new NotifyEvent[0]);
        });

        var duration = stopwatch.stop();
        if (!Debug.isDebug() && events.length == 0)
            return events;
        Debug.log(entities.getBanMod().log(), "Accepting %d events took %sms".formatted(events.length, duration.toMillis()));

        return events;
    }
}

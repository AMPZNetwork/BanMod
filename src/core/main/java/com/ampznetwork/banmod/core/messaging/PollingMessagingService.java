package com.ampznetwork.banmod.core.messaging;

import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
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
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@Value
public class PollingMessagingService extends MessagingServiceBase<HibernateEntityService> {
    EntityManager manager;
    Session       session;

    public PollingMessagingService(HibernateEntityService service, EntityManager manager, Duration interval) {
        super(service, interval);

        this.manager = manager;
        this.session = manager.unwrap(Session.class);

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
    protected void push(NotifyEvent event) {
        service.save(event);
    }

    @Override
    protected NotifyEvent[] pollNotifier() {
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
                }
            }
            return handle.toArray(new NotifyEvent[0]);
        });

        var duration = stopwatch.stop();
        if (events.length == 0)
            return events;
        var msg = "Accepting %d events took %sms".formatted(events.length, duration.toMillis());
        if (Debug.isDebug()) // best log level handling EVER
            service.getBanMod()
                    .log()
                    .info(msg);
        else service.getBanMod()
                .log()
                .debug(msg);

        return events;
    }
}

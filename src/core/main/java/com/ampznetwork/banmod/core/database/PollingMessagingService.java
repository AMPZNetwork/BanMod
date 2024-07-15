package com.ampznetwork.banmod.core.database;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import lombok.Value;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.func.util.Debug;
import org.comroid.api.func.util.Stopwatch;
import org.comroid.api.tree.Component;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Value
public class PollingMessagingService extends Component.Base implements MessagingService {
    HibernateEntityService service;
    EntityManager          manager;
    long                   me;

    public PollingMessagingService(HibernateEntityService service, EntityManager manager, Duration interval) {
        this.service = service;
        this.manager = manager;

        service.getScheduler().scheduleWithFixedDelay(this::pollNotifier, 5, interval.toMillis(), TimeUnit.MILLISECONDS);

        // find recently used idents
        //noinspection unchecked
        var occupied = service.wrapQuery(Query::getResultStream, manager.createNativeQuery("""
                select BIT_OR(ne.ident)
                from banmod_notify ne
                group by ne.ident, ne.timestamp
                order by ne.timestamp desc
                limit 50
                """, Long.class)).mapToLong(x -> (long) x).findAny().orElse(0);

        // randomly try to get a new ident
        long x;
        var  rng = new Random();
        do {
            x = 1L << rng.nextInt(64);
        } while ((x & ~occupied) != 0);
        this.me = x;

        // send HELLO
        push().complete(bld -> bld.type(NotifyEvent.Type.HELLO));
    }

    public void pollNotifier() {
        var stopwatch = Stopwatch.start(this);
        var events = service.wrapTransaction(Connection.TRANSACTION_REPEATABLE_READ, () -> {
            var handle = Polyfill.<List<NotifyEvent>>uncheckedCast(manager.createNativeQuery("""
                    select ne.*
                    from banmod_notify ne
                    where ne.ident != :me and (ne.acknowledge & :me) = 0
                    order by ne.timestamp
                    """, NotifyEvent.class).getResultList());
            for (var event : handle.toArray(new NotifyEvent[0])) {
                var ack = manager.createNativeQuery("""
                        update banmod_notify ne
                        set ne.acknowledge = (ne.acknowledge | :me)
                        where ne.ident = :ident and ne.timestamp = :timestamp
                        """).setParameter("me", me).setParameter("ident", event.getIdent()).setParameter("timestamp", event.getTimestamp()).executeUpdate();
                if (ack != 1) {
                    service.getBanMod().log().warn("Failed to acknowledge notification {}; ignoring it", event);
                    handle.remove(event);
                }
            }
            return handle.toArray(new NotifyEvent[0]);
        });

        var duration = stopwatch.stop();
        var msg      = "Accepting %d events took %s".formatted(events.length, BanMod.Displays.formatDuration(duration));
        if (Debug.isDebug()) // best log level handling EVER
            service.getBanMod()
                    .log()
                    .info(msg);
        else service.getBanMod()
                .log()
                .debug(msg);

        service.getScheduler().execute(() -> dispatch(events));
    }

    @Override
    public AlmostComplete<NotifyEvent.Builder> push() {
        return new AlmostComplete<>(NotifyEvent::builder, builder -> service.save(builder.ident(me).build()));
    }

    private void dispatch(NotifyEvent... events) {
        if (events.length == 0) return;
        if (events.length > 1) for (var event : events)
            dispatch(event);
        var event = events[0];
        if (event.getType() == NotifyEvent.Type.HELLO) return; // nothing to do
        // handle SYNC
        service.sync(event.getData());
    }
}

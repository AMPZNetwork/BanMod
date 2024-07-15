package com.ampznetwork.banmod.core.database;

import com.ampznetwork.banmod.api.database.MessagingService;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.core.database.hibernate.HibernateEntityService;
import lombok.Value;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.tree.Component;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

@Value
public class PollingMessagingService extends Component.Base implements MessagingService {
    HibernateEntityService entityService;
    EntityManager          manager;

    public PollingMessagingService(HibernateEntityService entityService, EntityManager manager) {
        this.entityService = entityService;
        this.manager = manager;

        entityService.getScheduler().scheduleAtFixedRate(this::pollNotifier, 5, 2, TimeUnit.SECONDS);

        // find free ident bit & send hello
        var ident =
                push().complete(bld -> bld.type(NotifyEvent.Type.HELLO));
    }

    public void pollNotifier() {}

    @Override
    public AlmostComplete<NotifyEvent.Builder> push() {
        return new AlmostComplete<>(NotifyEvent::builder, this::push);
    }

    private void push(NotifyEvent.Builder push) {
        entityService.wrapTransaction(Connection.TRANSACTION_SERIALIZABLE, () -> {
            // get next free incr
            long incr = manager.createQuery("""
                    select ne.incr from NotifyEvent ne order by ne.timestamp desc
                    """, Long.class).getResultStream().findAny().orElse(0L);

            // set incr to this event
            var event = push.incr(incr + 1).build();

            do {
                try {
                    manager.persist(event);
                    return event;
                } catch (EntityExistsException ignored) {
                    event.setIncr(incr += 1);
                }
            } while (true);
        });
    }
}

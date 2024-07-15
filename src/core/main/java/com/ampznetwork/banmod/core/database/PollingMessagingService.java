package com.ampznetwork.banmod.core.database;

import com.ampznetwork.banmod.api.database.EntityService;
import com.ampznetwork.banmod.api.database.MessagingService;
import lombok.Value;
import org.comroid.api.tree.Component;

import java.util.concurrent.TimeUnit;

@Value
public class PollingMessagingService extends Component.Base implements MessagingService {
    EntityService entityService;

    public PollingMessagingService(EntityService entityService) {
        this.entityService = entityService;

        entityService.getScheduler().scheduleAtFixedRate(this::pollNotifier, 5, 2, TimeUnit.SECONDS);
    }

    public void pollNotifier() {}

    @Override public void push() {}
}

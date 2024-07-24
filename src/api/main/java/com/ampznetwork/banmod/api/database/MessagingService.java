package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.api.attr.Named;
import org.comroid.api.func.util.AlmostComplete;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

public interface MessagingService {
    AlmostComplete<NotifyEvent.Builder> push();

    interface Config {}

    interface PollingDatabase extends MessagingService {
        record Config(DatabaseInfo dbInfo, Duration interval) implements MessagingService.Config {}
    }

    interface RabbitMQ extends MessagingService {
        record Config(String uri) implements MessagingService.Config {}
    }

    @Value
    @NonFinal
    abstract class Type<Config extends MessagingService.Config, Implementation extends MessagingService> implements Named {
        public static final Set<Type<?, ?>> REGISTRY = new HashSet<>();
        String name;

        {REGISTRY.add(this);}

        public abstract @Nullable Implementation createService(BanMod mod, EntityService entities, Config config);
    }
}

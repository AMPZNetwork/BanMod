package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.BanMod;
import com.ampznetwork.banmod.api.entity.NotifyEvent;
import com.ampznetwork.banmod.api.model.info.DatabaseInfo;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.api.attr.Named;
import org.comroid.api.func.util.AlmostComplete;
import org.comroid.api.text.Capitalization;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public interface MessagingService {
    AlmostComplete<NotifyEvent.Builder> push();

    interface Config {
        boolean inheritDatasource();
    }

    interface PollingDatabase extends MessagingService {
        record Config(@Nullable DatabaseInfo dbInfo, Duration interval) implements MessagingService.Config {
            @Override
            public boolean inheritDatasource() {
                return dbInfo == null;
            }
        }
    }

    interface RabbitMQ extends MessagingService {
        record Config(String uri) implements MessagingService.Config {
            @Override
            public boolean inheritDatasource() {
                return false;
            }
        }
    }

    @Value
    @NonFinal
    abstract class Type<Config extends MessagingService.Config, Implementation extends MessagingService> implements Named {
        public static final Set<Type<?, ?>> REGISTRY = new HashSet<>();
        String name;

        {REGISTRY.add(this);}

        public abstract @Nullable Implementation createService(BanMod mod, EntityService entities, Config config);

        public interface Provider {
            String getMessagingServiceTypeName();

            MessagingService.Config getMessagingServiceConfig();

            default Optional<Type<?, ?>> getMessagingServiceType() {
                var messagingServiceTypeName = getMessagingServiceTypeName();
                return Type.REGISTRY.stream()
                        .filter(type -> Capitalization.equalsIgnoreCase(type.name, messagingServiceTypeName))
                        .findAny();
            }
        }
    }
}

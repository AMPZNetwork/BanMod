package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.entity.NotifyEvent;
import org.comroid.api.func.util.AlmostComplete;

public interface MessagingService {
    AlmostComplete<NotifyEvent.Builder> push();
}

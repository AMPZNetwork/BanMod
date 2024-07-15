package com.ampznetwork.banmod.api.database;

import com.ampznetwork.banmod.api.BanMod;
import lombok.Value;
import org.comroid.api.tree.Component;
import org.comroid.api.tree.Reloadable;

@Value
public class MessagingService extends Component.Base implements Reloadable {
    BanMod mod;

    public MessagingService(BanMod mod) {
        this.mod = mod;
    }
}

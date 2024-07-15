package com.ampznetwork.banmod.api.model.adp;

import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

public interface IPropagationAdapter {
    void cancel();

    void force();

    @Value
    @NoArgsConstructor
    class Stateful implements com.ampznetwork.libmod.api.model.adp.IPropagationAdapter {
        @NonFinal
        boolean cancelled;
        @NonFinal
        boolean forced;

        @Override
        public void cancel() {
            cancelled = true;
            forced = false;
        }

        @Override
        public void force() {
            cancelled = false;
            forced = true;
        }
    }
}

package com.ampznetwork.banmod.api.model.adp;

import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

@Deprecated(forRemoval = true)
public interface IPropagationAdapter {
    void cancel();

    void force();

    @Value
    @NoArgsConstructor
    @Deprecated(forRemoval = true)
    class Stateful implements IPropagationAdapter {
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

package com.ampznetwork.banmod.core.importer;

import org.comroid.api.tree.UncheckedCloseable;

public interface Importer extends UncheckedCloseable {
    ImportResult run();
}

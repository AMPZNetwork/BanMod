package com.ampznetwork.banmod.api.model.adp;

import net.kyori.adventure.text.Component;

import java.util.List;

public interface BookAdapter {
    String TITLE = "Interactive BanMod Menu";
    String AUTHOR = "kaleidox@ampznetwork";

    List<Component[]> getPages();
}

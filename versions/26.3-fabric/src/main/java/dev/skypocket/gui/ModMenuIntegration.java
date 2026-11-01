package dev.skypocket.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entrypoint (declared in fabric.mod.json). Only loaded when ModMenu is
 * installed, so it is safe to compile against ModMenu without requiring it.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new OneClickScreen();
    }
}

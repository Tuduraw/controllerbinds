package com.tuduraw.controllerbinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.tuduraw.controllerbinds.config.ControllerBindsConfig;
import com.tuduraw.controllerbinds.input.ControllerManager;
import com.tuduraw.controllerbinds.screen.ControllerBindsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ControllerBindsClient implements ClientModInitializer {
    public static final String MOD_ID = "controllerbinds";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static ControllerBindsConfig config = new ControllerBindsConfig();
    private static final ControllerManager CONTROLLER = new ControllerManager();
    private static KeyMapping openMenuKey;

    public static ControllerBindsConfig config() {
        return config;
    }

    public static ControllerManager controller() {
        return CONTROLLER;
    }

    @Override
    public void onInitializeClient() {
        config = ControllerBindsConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.controllerbinds.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ControllerBindsScreen(null));
                }
            }
        });
    }
}

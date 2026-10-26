package com.vibe.client.input

import com.vibe.client.feature.FreeLook
import com.vibe.client.feature.WorldTweaks
import com.vibe.client.feature.Zoom
import com.vibe.client.gui.ClickGui
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

/**
 * Opens the client menu.
 *
 * Keys: Right Shift (Lunar-style), M, N — all three work.
 * They can be rebound from vanilla Controls because they are real keybindings.
 */
object KeyInputHandler {

    private lateinit var openMenu: KeyBinding
    private lateinit var zoomKey: KeyBinding
    private lateinit var freeLookKey: KeyBinding

    fun register() {
        openMenu = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.vibeclient.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.vibeclient"
            )
        )

        // Hold-to-zoom, the Lunar default binding. Rebindable in vanilla
        // Controls like any other key.
        zoomKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.vibeclient.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                "category.vibeclient"
            )
        )

        // Hold-to-look-around, the Lunar default binding.
        freeLookKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.vibeclient.freelook",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "category.vibeclient"
            )
        )

        var prevM = false
        var prevN = false

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Primary: registered keybinding (Right Shift by default, rebindable).
            var open = false
            while (openMenu.wasPressed()) open = true

            // Extra hardcoded shortcuts: M and N (edge-triggered).
            val m = InputUtil.isKeyPressed(client.window.handle, GLFW.GLFW_KEY_M)
            val n = InputUtil.isKeyPressed(client.window.handle, GLFW.GLFW_KEY_N)
            if (m && !prevM) open = true
            if (n && !prevN) open = true
            prevM = m
            prevN = n

            if (open && client.currentScreen == null) {
                client.setScreen(ClickGui(null))
            }

            // Zoom is a "while held" state, so it is read every tick rather
            // than edge-triggered. Never active with a screen open: the user is
            // typing or clicking, not looking around.
            WorldTweaks.zooming = zoomKey.isPressed && client.currentScreen == null

            // FreeLook is also a "while held" state. It is dropped whenever a
            // screen opens, otherwise the camera would stay detached from the
            // player after closing the menu.
            FreeLook.setHeld(client, freeLookKey.isPressed && client.currentScreen == null)
        }
    }
}

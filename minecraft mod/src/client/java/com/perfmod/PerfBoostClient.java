package com.perfmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public class PerfBoostClient implements ClientModInitializer {

    private static KeyMapping openPerfHudKey;
    private static KeyMapping togglePerfModeKey;
    private static boolean hudVisible = false;

    /** When true, mod applies performance tweaks (e.g. particle limit). */
    public static boolean performanceMode = false;

    /** Cached GuiGraphics.drawString(Font, Component, int, int, int, boolean) for 1.21.1 (returns int) vs 1.21.10 (returns void). */
    private static Method drawStringMethod;

    private static void drawHudString(GuiGraphics drawContext, Font font, Component text, int x, int y, int color, boolean shadow) {
        if (drawStringMethod == null) {
            for (Method m : GuiGraphics.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 6 && p[0] == Font.class && p[1] == Component.class
                    && p[2] == int.class && p[3] == int.class && p[4] == int.class && p[5] == boolean.class) {
                    drawStringMethod = m;
                    drawStringMethod.setAccessible(true);
                    break;
                }
            }
        }
        if (drawStringMethod != null) {
            try {
                drawStringMethod.invoke(drawContext, font, text, x, y, color, shadow);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Create KeyMapping compatible with both 1.21.1 (String category) and 1.21.10+ (KeyMapping.Category). */
    private static KeyMapping createKeyMapping(String id, InputConstants.Type type, int keyCode, String categoryName) {
        for (Constructor<?> ctor : KeyMapping.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 4) continue;
            if (params[0] != String.class || params[2] != int.class) continue;
            Class<?> categoryClass = params[3];
            if (categoryClass == String.class) continue;
            // Prefer custom category so keybinds show under "Perf Boost" and are rebindable in Controls
            Object categoryInstance = createCustomCategory(categoryClass);
            if (categoryInstance == null) categoryInstance = getStaticCategoryInstance(categoryClass);
            if (categoryInstance == null) continue;
            try {
                ctor.setAccessible(true);
                return (KeyMapping) ctor.newInstance(id, type, keyCode, categoryInstance);
            } catch (Throwable ignored) {
                continue;
            }
        }
        return new KeyMapping(id, type, keyCode, categoryName);
    }

    /** Create KeyMapping.Category(Identifier) so keybinds appear in Controls under "Perf Boost" and are rebindable. */
    private static Object createCustomCategory(Class<?> categoryClass) {
        try {
            for (Constructor<?> catCtor : categoryClass.getDeclaredConstructors()) {
                if (catCtor.getParameterCount() != 1) continue;
                Class<?> idClass = catCtor.getParameterTypes()[0];
                Object identifier = createIdentifier(idClass, "perfboost", "perfboost");
                if (identifier == null) continue;
                catCtor.setAccessible(true);
                return catCtor.newInstance(identifier);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object createIdentifier(Class<?> idClass, String namespace, String path) {
        try {
            for (java.lang.reflect.Method m : idClass.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && p[1] == String.class && m.getReturnType() == idClass) {
                    m.setAccessible(true);
                    return m.invoke(null, namespace, path);
                }
            }
            Constructor<?> c = idClass.getConstructor(String.class, String.class);
            c.setAccessible(true);
            return c.newInstance(namespace, path);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Get any static instance of the Category type (fallback when custom category not available). */
    private static Object getStaticCategoryInstance(Class<?> categoryClass) {
        if (categoryClass.isEnum()) {
            Object[] constants = categoryClass.getEnumConstants();
            return constants != null && constants.length > 0 ? constants[0] : null;
        }
        for (Field f : categoryClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == categoryClass) {
                try {
                    f.setAccessible(true);
                    return f.get(null);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public void onInitializeClient() {
        openPerfHudKey = KeyBindingHelper.registerKeyBinding(createKeyMapping(
                "key.perfboost.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.perfboost"
        ));
        togglePerfModeKey = KeyBindingHelper.registerKeyBinding(createKeyMapping(
                "key.perfboost.toggle_perf_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.perfboost"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPerfHudKey.consumeClick()) hudVisible = !hudVisible;
            while (togglePerfModeKey.consumeClick()) {
                performanceMode = !performanceMode;
                PerfOptionsHelper.applyPerformanceMode(performanceMode);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!hudVisible || drawContext == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui) return;
            renderPerfHud(drawContext, mc);
        });

        SystemInfoHelper.startBackgroundUpdater();
    }

    private void renderPerfHud(GuiGraphics drawContext, Minecraft mc) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int pad = 6;
        int boxWidth = 150;
        int lineHeight = 9;
        int lines = 11;
        int boxHeight = 4 + (lineHeight + 1) * lines + 4;
        int x = screenWidth - boxWidth - pad;
        int y = pad;

        // Background & border
        drawContext.fill(x, y, x + boxWidth, y + boxHeight, 0xCC1a1a1a);
        drawContext.fill(x, y, x + boxWidth, y + 1, 0xFF2d7d46);
        drawContext.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF2d7d46);
        drawContext.fill(x, y, x + 1, y + boxHeight, 0xFF2d7d46);
        drawContext.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, 0xFF2d7d46);

        int textY = y + 4;
        int textX = x + 5;

        drawHudString(drawContext, mc.font, Component.literal("Perf Boost"), textX, textY, 0xFF2d7d46, false);
        textY += lineHeight + 1;

        // FPS & frame time (use ROOT locale so ms is always "8.5" not "8,5")
        int fps = mc.getFps();
        int fpsColor = fps >= 60 ? 0xFF55FF55 : (fps >= 30 ? 0xFFFFFF55 : 0xFFFF5555);
        double frameMs = fps > 0 ? 1000.0 / fps : 0;
        drawHudString(drawContext, mc.font, Component.literal("FPS: " + fps + " (" + String.format(Locale.ROOT, "%.1f", frameMs) + " ms)"), textX, textY, fpsColor, false);
        textY += lineHeight;

        // RAM
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        drawHudString(drawContext, mc.font, Component.literal("RAM: " + usedMB + " / " + maxMB + " MB"), textX, textY, 0xFFAAAAAA, false);
        textY += lineHeight;

        // Entities & Chunks (in world)
        ClientLevel level = mc.level;
        if (level != null) {
            drawHudString(drawContext, mc.font, Component.literal("Entities: " + level.getEntityCount()), textX, textY, 0xFFAAAAAA, false);
            textY += lineHeight;
            drawHudString(drawContext, mc.font, Component.literal("Chunks: " + level.getChunkSource().getLoadedChunksCount()), textX, textY, 0xFFAAAAAA, false);
        } else {
            drawHudString(drawContext, mc.font, Component.literal("Entities: —"), textX, textY, 0xFF666666, false);
            textY += lineHeight;
            drawHudString(drawContext, mc.font, Component.literal("Chunks: —"), textX, textY, 0xFF666666, false);
        }
        textY += lineHeight;

        // Ping (multiplayer)
        ClientPacketListener conn = mc.getConnection();
        if (conn != null && mc.player != null) {
            var playerInfo = conn.getPlayerInfo(mc.player.getUUID());
            if (playerInfo != null) {
                int ping = playerInfo.getLatency();
                int pingColor = ping < 100 ? 0xFF55FF55 : (ping < 200 ? 0xFFFFFF55 : 0xFFFF5555);
                drawHudString(drawContext, mc.font, Component.literal("Ping: " + ping + " ms"), textX, textY, pingColor, false);
            } else {
                drawHudString(drawContext, mc.font, Component.literal("Ping: —"), textX, textY, 0xFF666666, false);
            }
        } else {
            drawHudString(drawContext, mc.font, Component.literal("Ping: —"), textX, textY, 0xFF666666, false);
        }
        textY += lineHeight;

        // CPU (cached from background thread; show — when OSHI unavailable or returns 0)
        float cpuLoad = SystemInfoHelper.getCpuLoad();
        if (cpuLoad > 0f) {
            int cpuPct = (int) (cpuLoad * 100);
            int cpuColor = cpuPct < 70 ? 0xFF55FF55 : (cpuPct < 90 ? 0xFFFFFF55 : 0xFFFF5555);
            drawHudString(drawContext, mc.font, Component.literal("CPU: " + cpuPct + "%"), textX, textY, cpuColor, false);
        } else {
            drawHudString(drawContext, mc.font, Component.literal("CPU: —"), textX, textY, 0xFF666666, false);
        }
        textY += lineHeight;

        // Temp (cached from background thread only)
        float temp = SystemInfoHelper.getCpuTempCelsius();
        if (!Float.isNaN(temp) && temp > 0) {
            int tempColor = temp < 70 ? 0xFF55FF55 : (temp < 85 ? 0xFFFFFF55 : 0xFFFF5555);
            drawHudString(drawContext, mc.font, Component.literal("Temp: " + (int) temp + " °C"), textX, textY, tempColor, false);
        } else {
            drawHudString(drawContext, mc.font, Component.literal("Temp: —"), textX, textY, 0xFF666666, false);
        }
        textY += lineHeight;

        if (performanceMode) {
            drawHudString(drawContext, mc.font, Component.literal("Perf: ON"), textX, textY, 0xFF55FF55, false);
        }
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }
}

package com.perfmod;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;

/**
 * When performance mode is ON, temporarily forces low graphics options.
 * When turned OFF, restores the user's previous values.
 */
public final class PerfOptionsHelper {

    private static final int PERF_RENDER_DISTANCE = 8;
    private static final int PERF_SIMULATION_DISTANCE = 6;
    private static final double PERF_ENTITY_DISTANCE = 0.5;

    private static boolean saved;
    private static GraphicsStatus savedGraphics;
    private static ParticleStatus savedParticles;
    private static int savedRenderDistance;
    private static int savedSimulationDistance;
    private static double savedEntityDistance;
    private static boolean savedAo;
    private static boolean savedEntityShadows;
    private static int savedBiomeBlend;

    public static void applyPerformanceMode(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;

        if (on) {
            if (!saved) {
                savedGraphics = mc.options.graphicsMode().get();
                savedParticles = mc.options.particles().get();
                savedRenderDistance = mc.options.renderDistance().get();
                savedSimulationDistance = mc.options.simulationDistance().get();
                savedEntityDistance = mc.options.entityDistanceScaling().get();
                savedAo = mc.options.ambientOcclusion().get();
                savedEntityShadows = mc.options.entityShadows().get();
                savedBiomeBlend = mc.options.biomeBlendRadius().get();
                saved = true;
            }
            mc.options.graphicsMode().set(GraphicsStatus.FAST);
            mc.options.particles().set(ParticleStatus.MINIMAL);
            mc.options.renderDistance().set(PERF_RENDER_DISTANCE);
            mc.options.simulationDistance().set(PERF_SIMULATION_DISTANCE);
            mc.options.entityDistanceScaling().set(PERF_ENTITY_DISTANCE);
            mc.options.ambientOcclusion().set(false);
            mc.options.entityShadows().set(false);
            mc.options.biomeBlendRadius().set(0);
        } else {
            if (saved) {
                mc.options.graphicsMode().set(savedGraphics);
                mc.options.particles().set(savedParticles);
                mc.options.renderDistance().set(savedRenderDistance);
                mc.options.simulationDistance().set(savedSimulationDistance);
                mc.options.entityDistanceScaling().set(savedEntityDistance);
                mc.options.ambientOcclusion().set(savedAo);
                mc.options.entityShadows().set(savedEntityShadows);
                mc.options.biomeBlendRadius().set(savedBiomeBlend);
                saved = false;
            }
        }
    }
}

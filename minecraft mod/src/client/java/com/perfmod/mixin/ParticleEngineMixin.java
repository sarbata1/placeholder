package com.perfmod.mixin;

import com.perfmod.PerfBoostClient;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Map;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    private static final int MAX_PARTICLES_PERF_MODE = 2000;

    @Shadow
    @Final
    private Map<?, ?> particles;

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void perfboost$limitParticles(
            ParticleOptions options, double x, double y, double z,
            double vx, double vy, double vz,
            CallbackInfoReturnable<?> cir
    ) {
        if (!PerfBoostClient.performanceMode) return;
        int total = 0;
        for (Object value : particles.values()) {
            if (value instanceof Collection<?> c) {
                total += c.size();
            }
        }
        if (total >= MAX_PARTICLES_PERF_MODE) {
            cir.setReturnValue(null);
            cir.cancel();
        }
    }
}

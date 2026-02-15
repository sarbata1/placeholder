package com.perfmod;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;

/**
 * CPU load and temperature via OSHI. All OSHI work (init + updates) runs on a
 * single background thread; the render thread only reads cached values.
 * Disables itself after repeated failures (e.g. broken WMI/PDH on Windows).
 */
public final class SystemInfoHelper {
    private static final long UPDATE_INTERVAL_MS = 3000;
    private static final int MAX_CONSECUTIVE_FAILURES = 2;

    private static volatile float lastCpuLoad = -1f;
    private static volatile float lastCpuTemp = Float.NaN;
    private static volatile boolean oshiDisabled;
    private static volatile boolean started;

    private static int consecutiveFailures;

    /** Start background updater. Call once from client init. OSHI runs only in that thread. */
    public static void startBackgroundUpdater() {
        if (started) return;
        started = true;
        Thread t = new Thread(SystemInfoHelper::backgroundUpdateLoop, "PerfBoost-OSHI");
        t.setDaemon(true);
        t.start();
    }

    /** Only read cached values; never calls OSHI. Safe on render thread. */
    public static float getCpuLoad() {
        return lastCpuLoad;
    }

    /** Only read cached value; never calls OSHI. Safe on render thread. */
    public static float getCpuTempCelsius() {
        return lastCpuTemp;
    }

    public static boolean isAvailable() {
        return !oshiDisabled;
    }

    private static void backgroundUpdateLoop() {
        SystemInfo systemInfo = null;
        HardwareAbstractionLayer hal = null;
        try {
            systemInfo = new SystemInfo();
            hal = systemInfo.getHardware();
        } catch (Throwable t) {
            oshiDisabled = true;
            return;
        }
        final HardwareAbstractionLayer halRef = hal;
        while (!oshiDisabled) {
            try {
                Thread.sleep(UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (oshiDisabled) break;
            try {
                CentralProcessor cpu = halRef.getProcessor();
                double load = cpu.getSystemCpuLoad(0);
                lastCpuLoad = load >= 0 ? (float) load : -1f;
                Sensors sensors = halRef.getSensors();
                if (sensors != null && sensors.getCpuTemperature() > 0) {
                    lastCpuTemp = (float) sensors.getCpuTemperature();
                } else {
                    lastCpuTemp = Float.NaN;
                }
                consecutiveFailures = 0;
            } catch (Throwable ignored) {
                lastCpuLoad = -1f;
                lastCpuTemp = Float.NaN;
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    oshiDisabled = true;
                }
            }
        }
    }
}

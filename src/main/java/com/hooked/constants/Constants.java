package com.hooked.constants;

public final class Constants {

    private Constants() {}

    public static final String ENTITY_TAG = "hooked_debris";
    public static final long DRIFT_TICK_INTERVAL = 4L;
    public static final double DRIFT_TICK_INTERVAL_SECONDS = DRIFT_TICK_INTERVAL / 20.0;
    public static final long SMOOTH_DRIFT_TICK_INTERVAL = 1L;
    public static final double SMOOTH_DRIFT_TICK_INTERVAL_SECONDS = SMOOTH_DRIFT_TICK_INTERVAL / 20.0;
    public static final int TELEPORT_SYNC_INTERVAL = 20;
    public static final long CLEANUP_TICK_INTERVAL = 100L;
    public static final long HOOK_TIMEOUT_MS = 5000L;
    public static final double DRIFT_DIRECTION_CHANGE_INTERVAL_SECONDS = 5.0;
    public static final double DIRECTION_PERTURB_DEGREES = 5.0;
    public static final double OBSTACLE_TURN_MIN_DEGREES = 120.0;
    public static final double OBSTACLE_TURN_MAX_DEGREES = 180.0;
    public static final double GRAB_FINISH_DISTANCE = 0.5;
    public static final int MAX_SPAWN_ATTEMPTS = 10;
    public static final int OBSTACLE_CHECK_RADIUS = 3;
    public static final String CONFIG_DEBUG = "debug";
    public static final String PLUGIN_NAME = "Hooked";
}

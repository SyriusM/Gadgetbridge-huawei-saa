package nodomain.freeyourgadget.gadgetbridge.devices;


public enum SleepAsAndroidFeature {
    HEART_RATE,
    RR_INTERVALS,
    ALARMS,
    NOTIFICATIONS,
    ACCELEROMETER,
    OXIMETRY,
    SPO2,
    // Device only gets SPO2 via periodic fetching of recorded samples, not live readings.
    // Should be reported alongside SPO2.
    SPO2_AUTOFETCH,
    // Device only gets R-R intervals from a measurement that keeps its screen on, so they are
    // opt-in through a separate preference. Should be reported alongside RR_INTERVALS.
    RR_INTERVALS_SCREEN_ON
}

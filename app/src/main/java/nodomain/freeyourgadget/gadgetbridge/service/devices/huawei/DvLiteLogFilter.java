package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei;

import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.DvLiteSensor;

/**
 * The accelerometer stream sends ~100 packets/s for the whole night, and the generic per-packet
 * DEBUG lines turned that into ~465 MB of log per night. While the stream runs, drop those lines:
 * the raw (encrypted) notification dump, the encrypted outer TLV, the inner ACC TLV and the
 * DvLite data "asynchronous response" line. Decrypted TLVs of all other packets stay logged.
 */
public final class DvLiteLogFilter extends TurboFilter {
    private static final DvLiteLogFilter INSTANCE = new DvLiteLogFilter();
    private static volatile boolean accStreaming = false;

    private static final String RAW_PACKET = "onCharacteristicChanged: {} <@ {}";
    private static final String PARSED_TLV = "Parsed TLV: {}";
    private static final String ASYNC_RESPONSE = "Service: {}, command: {}, asynchronous response.";
    private static final int TAG_ENCRYPTED_DATA = 0x7c;
    private static final int MIN_ACC_VALUE_LENGTH = 32;

    public static void setAccStreaming(boolean streaming) {
        if (streaming) {
            install();
        }
        accStreaming = streaming;
    }

    // Logging may reset the logger context (e.g. when file logging is toggled), which drops
    // turbo filters, so check again every time the stream starts.
    private static void install() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            return;
        }
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (!context.getTurboFilterList().contains(INSTANCE)) {
            INSTANCE.start();
            context.addTurboFilter(INSTANCE);
        }
    }

    @Override
    public FilterReply decide(List<Marker> markers, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (!accStreaming || level != Level.DEBUG || format == null) {
            return FilterReply.NEUTRAL;
        }
        switch (format) {
            case RAW_PACKET:
                return FilterReply.DENY;
            case PARSED_TLV:
                return isEncryptedOrAcc(params) ? FilterReply.DENY : FilterReply.NEUTRAL;
            case ASYNC_RESPONSE:
                return params != null && params.length == 2 && "30".equals(params[0]) && "1".equals(params[1])
                        ? FilterReply.DENY : FilterReply.NEUTRAL;
            default:
                return FilterReply.NEUTRAL;
        }
    }

    private static boolean isEncryptedOrAcc(Object[] params) {
        if (params == null || params.length != 1 || !(params[0] instanceof HuaweiTLV)) {
            return false;
        }
        final List<HuaweiTLV.TLV> tlvs = ((HuaweiTLV) params[0]).get();
        if (tlvs.isEmpty()) {
            return false;
        }
        final HuaweiTLV.TLV first = tlvs.get(0);
        if ((first.getTag() & 0xFF) == TAG_ENCRYPTED_DATA) {
            return true;
        }
        final byte[] value = first.getValue();
        return (first.getTag() & 0xFF) == 0x01 && value != null && value.length >= MIN_ACC_VALUE_LENGTH
                && (value[0] & 0xFF) == DvLiteSensor.SENSOR_ID_ACC;
    }
}

package nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;

/**
 * Wear Engine sensor virtualization (DvLite/DMSDP) channel, as used by Huawei Health.
 */
public class DvLiteSensor {
    public static final byte id = 0x30;

    public static final int SENSOR_ID_HR = 32;
    public static final int SENSOR_ID_ACC = 33;
    public static final float ACC_RESOLUTION = 0.0023928226f; // m/s^2 per LSB, reported by Band 9

    // Huawei Health knows HIGH(1) and MID(2). Band 9 streams ACC at ~100 Hz on MID and refuses HR
    // on MID (OperationResult -1) while accepting it on HIGH (~1 Hz).
    public static final int RATE_HIGH = 1;
    public static final int RATE_MID = 2;

    /** HR payload: sensorId (1) + device uptime ms (8, BE) + bpm (int32 LE); values above 255 are error codes. */
    public static Integer parseHeartRate(byte[] value) {
        if (value == null || value.length < 13 || (value[0] & 0xFF) != SENSOR_ID_HR) {
            return null;
        }
        final int bpm = ByteBuffer.wrap(value, 9, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return bpm > 255 ? null : bpm;
    }

    public static final String METHOD_ENABLE = "ENABLE";
    public static final String METHOD_DISABLE = "DISABLE";

    public static class SensorControl {
        public static final byte id = 0x02;

        public static class Request extends HuaweiPacket {
            public Request(ParamsProvider paramsProvider, int sensorId, int seq, int rate, String method) {
                super(paramsProvider);
                this.serviceId = DvLiteSensor.id;
                this.commandId = id;

                final String body = "msdp_trigger_method: " + method;
                final String text = "SET_PARAMETER * DMSDP/1.0\r\nServiceType: 2048\r\n"
                        + "DataSessionID: " + sensorId + "\r\nCSeq: " + seq + "\r\nDataVersion: 2\r\n"
                        + "Rate: " + rate + "\r\nContent-Type: text/parameters\r\n"
                        + "Content-Length: " + body.length() + "\r\n\r\n" + body + "\0";
                final byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
                final byte[] inner = new byte[textBytes.length + 2];
                inner[0] = 0x00;
                inner[1] = 0x02;
                System.arraycopy(textBytes, 0, inner, 2, textBytes.length);

                this.tlv = new HuaweiTLV().put(0x01, inner);
                this.complete = true;
            }
        }
    }

    public static class SensorData {
        public static final byte id = 0x01;

        public static final int HEADER_LENGTH = 17; // sensorId (1) + counter (8) + epoch ms (8)

        public final int sensorId;
        public final long timestampMs;
        public final short[] xyz;

        private SensorData(int sensorId, long timestampMs, short[] xyz) {
            this.sensorId = sensorId;
            this.timestampMs = timestampMs;
            this.xyz = xyz;
        }

        /** Returns null when the payload is not a 3-axis int16 sample block. */
        public static SensorData parse(byte[] value) {
            if (value == null || value.length < HEADER_LENGTH + 6) {
                return null;
            }
            final ByteBuffer header = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
            final int sensorId = value[0] & 0xFF;
            final long timestampMs = header.getLong(9);
            final int sampleBytes = (value.length - HEADER_LENGTH) / 6 * 6;
            final ByteBuffer samples = ByteBuffer.wrap(value, HEADER_LENGTH, sampleBytes).order(ByteOrder.LITTLE_ENDIAN);
            final short[] xyz = new short[sampleBytes / 2];
            for (int i = 0; i < xyz.length; i++) {
                xyz[i] = samples.getShort();
            }
            return new SensorData(sensorId, timestampMs, xyz);
        }
    }
}

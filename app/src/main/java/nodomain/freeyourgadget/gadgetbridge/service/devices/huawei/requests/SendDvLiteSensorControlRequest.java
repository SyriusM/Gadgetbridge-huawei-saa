package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.requests;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.DvLiteSensor;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiSupportProvider;

public class SendDvLiteSensorControlRequest extends Request {
    private final int sensorId;
    private final int seq;
    private final int rate;
    private final String method;

    public SendDvLiteSensorControlRequest(HuaweiSupportProvider support, int sensorId, int seq, int rate, String method) {
        super(support);
        this.serviceId = DvLiteSensor.id;
        this.commandId = DvLiteSensor.SensorControl.id;
        this.addToResponse = false;
        this.sensorId = sensorId;
        this.seq = seq;
        this.rate = rate;
        this.method = method;
    }

    @Override
    protected List<byte[]> createRequest() throws RequestCreationException {
        try {
            return new DvLiteSensor.SensorControl.Request(paramsProvider, sensorId, seq, rate, method).serialize();
        } catch (HuaweiPacket.CryptoException e) {
            throw new RequestCreationException(e);
        }
    }
}

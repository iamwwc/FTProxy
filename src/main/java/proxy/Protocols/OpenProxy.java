package proxy.Protocols;

import io.vertx.core.buffer.Buffer;

public class OpenProxy implements IProtocol<Buffer,Buffer>{
    private boolean isTCPRequestSend = false;

    @Override
    public Buffer encode(Buffer data) {
        return null;

    }

    @Override
    public Buffer decode(Buffer data) {
        return null;
    }

    @Override
    public Buffer encodeUDP(Buffer data) {
        return null;
    }

    @Override
    public Buffer decodeUDP(Buffer data) {
        return null;
    }
}

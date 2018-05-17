package proxy.Crypto;

import io.vertx.core.buffer.Buffer;

public interface ICrypto <T,M> {
    public T encrypt(M data);
    public T decrypt(M data);
}

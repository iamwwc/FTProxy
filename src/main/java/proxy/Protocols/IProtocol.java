package proxy.Protocols;

public interface IProtocol<T,M> {

    T encode(M data);
    T decode(M data);
    T encodeUDP(M data);
    T decodeUDP(M data);
}

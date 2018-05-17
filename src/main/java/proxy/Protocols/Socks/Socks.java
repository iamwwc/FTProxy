package proxy.Protocols.Socks;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proxy.Common.Util;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class Socks{
    private Logger log = LoggerFactory.getLogger(Socks.class.getName());
    private static final int VERSION = 0x05;

    private static final int ATYP_IPV4 = 0x01;
    private static final int ATYP_DOMAIN_NAME = 0x03;
    private static final int ATYP_IPV6 = 0x04;

    private static final int CMD_CONNECT = 0x01;
    private static final int CMD_BIND = 0x02;
    private static final int CMD_UDP_ASSOCIATE = 0x03;

    private static final int STREAM_INIT = 0;
    private static final int STREAM_ADDR_CONNECTING = 1;
    private static final int STREAM_RUNNING = 2;

    private int streamStatus = STREAM_INIT;

    private InetSocketAddress dstSocketAddr = null;

    private NetSocket socket;
    public Socks(NetSocket socket){
        this.socket = socket;
    }

    public void handleSocksAuth(Buffer data)
            throws BadSocksHandShakeException {

        if(data.getByte(0) != VERSION){
           throw new BadSocksHandShakeException("socks version not equal 5");
        }
        socket.write(Buffer.buffer(new byte[]{0x05,0x00}));
    }

    public void handleSocksAddrConnecting(Buffer data)
            throws BadSocksHandShakeException
                , UnknownHostException {

        assert data.getByte(0) == VERSION :"socks version not equal 5";

        int cmd = data.getByte(1);
        data = data.slice(3,data.length());
        switch (cmd){
            case CMD_CONNECT: handleCmdConnect(data);break;
            case CMD_BIND: handleCmdBind(data);break;
            case CMD_UDP_ASSOCIATE:handleCmdUdpAssociate(data);break;
            default: throw new BadSocksHandShakeException("bad socks addr, cannot find CMD");
        }
    }

    private void handleCmdConnect(Buffer data)
            throws UnknownHostException
                , BadSocksHandShakeException {

        String host;
        int port;
        int aType = data.getByte(0);
        switch (aType){
            case ATYP_IPV4:
                host = Util.getIpAsStringFromStream(1,4,data);//ipv4 length is 4
                port = data.getUnsignedShort(5);
                break;
            case ATYP_DOMAIN_NAME:
                int length = data.getUnsignedByte(1);
                byte[] domainName = data.getBytes(2,2 + length);
                host = new String(domainName, Charset.forName("UTF-8"));
                port = data.getUnsignedShort(2 + length);
                break;
            case ATYP_IPV6:
                host = Util.getIpAsStringFromStream(1,16,data);//ipv6 length is 16
                port = data.getUnsignedShort(17);
                break;
            default:
                String err = Arrays.toString(data.getBytes());
                throw new BadSocksHandShakeException("cannot find CMD ATYPE required" + err);
        }
        dstSocketAddr = new InetSocketAddress(host,port);
    }

    private void handleCmdBind(Buffer data){

    }

    private void handleCmdUdpAssociate(Buffer data){

    }


}

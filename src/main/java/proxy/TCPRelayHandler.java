package proxy;


//|-----Only Authorized--------|--------------------Authorized and Encrypted--------------------|
//+--------------+-------------+----------+--------+----------+-------+-------------------------+
//|Header Length | Data Length |    IV    |  ATYP  | Variable |  Port |   Encrypted TCP Data    |
//+--------------+-------------+----------+--------+----------+-------+-------------------------+
//|       2      |      2      |    12    |   1    | Variable |   2   |        Variable         |
//+--------------+-------------+----------+--------+----------+-------+-------------------------+
//|<------------------------------------Header----------------------->|
//切割数据包，数据包头会包含两个字节的headerLength



//+-----------+-----------------------+
//|Data Length| Encrypted TCP Data    |
//+-----------+-----------------------+
//|     2     |     Variable          |
//+-----------+-----------------------+
//


import proxy.Common.Util;
import proxy.Exception.NotSupportOperation;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import static proxy.Common.Util.*;

public class TCPRelayHandler implements Handler<Buffer>{
	private Logger log = LoggerFactory.getLogger(TCPRelayHandler.class);
	
	private static final int CMD_CONNECT = 0x01;	
	private static final int CMD_BIND = 0x02;
	private static final int CMD_UDP_ASSOCIATE = 0x03;

	private static final int ATYP_IPV4 = 0x01;
	private static final int ATYP_DOMAIN_NAME = 0x03;
	private static final int ATYP_IPV6 = 0x04;

	/*ms*/
	private static final int CONNECT_TIMEOUT = 30000;

	private boolean isConnected = false;

	public String tcpRelayHandlerId ;
	
	private NetSocket localNetSocket;
	private NetSocket remoteNetSocket;

	private NetClient netClient;

	private ProtocolParser parser;

	private Vertx vertx;
	private TCPRelay tcpRelay;
	private Encryptor encryptor = null;
	
	private HashMap<String,Object> config;
	private boolean isLocal;
	private String dstHost = null;
	private int dstPort = 0;
	
	private String serverHost;
	private int serverPort;
	
	public long lastActiveTime = 0;
    public boolean isDestroyed = false;

    private Queue<Buffer> dataWriteToLocal = new LinkedList<>();
    private Queue<Buffer> dataWriteToRemote = new LinkedList<>();

    private static final int StreamNoOperation = 0x00;
    private static final int StreamNeedWritting = 0x01;
    private int remoteStatus = StreamNoOperation;

	/**/
	private static final int StreamInit = 0x10;
	private static final int StreamRunning = 0x20;
	private static final int StreamAddrConnecting = 0x30;
    private int streamStatus;

    int hashCode = this.hashCode();

    //from httpServer
	public TCPRelayHandler(Vertx vertx
			,TCPRelay tcpRelay
			,boolean isLocal
			,HashMap<String,Object> config) {
        this.vertx = vertx;
		this.isLocal = isLocal;
		if(isLocal){
		    this.streamStatus = StreamInit;
        }else{
		    this.streamStatus = StreamAddrConnecting;
        }

		this.config = config;

		this.tcpRelay = tcpRelay;

		this.serverHost = (String)this.config.get("server_address");
		this.serverPort = ((Integer)this.config.get("server_port")).intValue();
		String method = (String)this.config.get("method");
		String password = (String)this.config.get("password");
        this.encryptor = new Encryptor(password,method);
        this.parser = new ProtocolParser(this,isLocal);

        this.tcpRelayHandlerId = "isLocal:" +isLocal + " HashCode: " + this.hashCode;

	}


	//from netServer
	public TCPRelayHandler(Vertx vertx
			,TCPRelay tcpRelay
			,NetSocket localNetSocket
			,boolean isLocal
			,HashMap<String,Object> config){

		this(vertx, tcpRelay,isLocal,config);

		this.localNetSocket = localNetSocket;
		this.localNetSocket
				.endHandler(this::handleLocalEnd)
				.closeHandler(this::handleLocalClose)
				.exceptionHandler(this::handleLocalException)
				.handler(this::handleLocalRead);
        lastActiveTime = Util.getCurrentTime();
	}

	public TCPRelayHandler(){}


	private void connectToRemote() {
        SocketAddress addr = null;
	    if(isLocal){
	        addr = SocketAddress.inetSocketAddress(this.serverPort,this.serverHost);
        }else{
	        addr = SocketAddress.inetSocketAddress(this.dstPort,this.dstHost);
        }
        this.netClient = vertx.createNetClient(getNetClientOptions());
	    this.netClient.connect(addr, result ->{
	        if(result.succeeded()){
                this.remoteNetSocket = result.result();
                this.isConnected = true;
                log.info("Connected to [{}:{}]",this.dstHost,this.dstPort);
                this.remoteNetSocket.handler(this::handleRemoteRead)
                        .endHandler(this::handleRemoteEnd)
                        .closeHandler(this::handleRemoteClose)
                        .exceptionHandler(this::handleRemoteException);
                writtingToSocket(remoteNetSocket);
            }
        });
	}
	
	private NetClientOptions getNetClientOptions() {
		NetClientOptions options = new NetClientOptions();
		options.setLocalAddress("0.0.0.0");
		options.setTcpKeepAlive(true);
		options.setTcpNoDelay(true);
		options.setConnectTimeout(CONNECT_TIMEOUT);
		options.setReusePort(true);
		options.setReuseAddress(true);
		return options;
	}

	//Client:
    //applicaiton ->     <-localNetSocket       <->        remoteNetSocket(HttpClient)->
    //                                                              ^
    //                                                              |
    //                                                              V
    //
    //  dst     <->    remoteNetSocket(NetClient)    <->   localNetSocket(request.NetSocket())
	//Server:
    public void destroyHandler() {

	    /*

	    log.debug("destroyHandler stack:-----------------");
	    new Exception().printStackTrace(System.out);
	    log.debug("stack end-----------------------------");

	    */
	    if(isDestroyed)
	        return;
	    if(this.localNetSocket != null){
	        this.localNetSocket.close();
	        this.localNetSocket = null;
        }
        if(this.remoteNetSocket != null){
	        this.remoteNetSocket.close();
            this.remoteNetSocket = null;
        }

        log.info("handler destroyed, HashCode: [{}]",this.hashCode());
	    this.isDestroyed = true;
		this.tcpRelay.destroyHandler(this, isDestroyed);
	}
	
	private void updataActivity() {
		this.tcpRelay.updateActivity(this);
	}


	

	private void handleLocalRead(Buffer data) {
	    if(isLocal){
	        this.handleClientLocalRead(data);
        }else{
	        log.debug("recv data from local, store into parser, data length: [{}]",data.length());
	        this.parser.put(data);

        }
        this.updataActivity();
	}

	private void handleClientLocalRead(Buffer data){
	    log.debug("Thread: [{}]",Thread.currentThread().getName());
        if(this.streamStatus == StreamRunning){
            log.debug("streamStatus: StreamRunning");
            data = this.encryptor.encryptor(data);
            if(data == null){
                destroyHandler();
                return;
            }
            this.writeToSocket(this.remoteNetSocket,data);
        }else if(this.streamStatus == StreamInit){
            log.debug("streamStatus: StreamInit");
            handleStageInit(data);
            streamStatus = StreamAddrConnecting;
        }else if(this.streamStatus == StreamAddrConnecting){
            log.debug("streamStatus: StreamAddrConnecting");
            data = handleStageAddrConnecting(data);
            Buffer response = Buffer.buffer(new byte[]{5,0,0,1,0,0,0,0,10,10});
            writeToSocket(localNetSocket,response);
            try {
                handleAddr(data);
                data = encryptor.encryptor(data);
                writeToSocket(remoteNetSocket,data);
                this.connectToRemote();

            } catch (UnknownHostException e) {
                log.error("UnknowHost when parse socks connecting header, destroyed");
                this.destroyHandler();
            } catch(NotSupportOperation e ){
                log.error("[{}], handler destroyed",e.getMessage());
                destroyHandler();
            }
            streamStatus = StreamRunning;
        }
    }

    private void writeToSocket(NetSocket socket, Buffer data){
        if(socket == remoteNetSocket){
            this.dataWriteToRemote.offer(data);
        }else{
            this.dataWriteToLocal.offer(data);
        }
        writtingToSocket(socket);
    }

    private void writtingToSocket(NetSocket socket){
	    if(socket == remoteNetSocket){
	        if(!isConnected)
	            return;
	        Buffer data = null;
	        while((data = this.dataWriteToRemote.poll()) != null){
	            if(!socket.writeQueueFull()){
	                socket.write(data);
	            } else{
                    socket.drainHandler(this::handleRemoteDrain);
                }

            }
        }else{
	        if(socket == null)
	            return;
	        Buffer data = null;
            while((data = this.dataWriteToLocal.poll())!= null){
                if(!socket.writeQueueFull()){
                    socket.write(data);
                } else
                    socket.drainHandler(this::handleLocalDrain);
            }
        }
    }





//发向server的第一个包，data length 等于 0
//+--------------+--------+----------+-------+-----------+
//|Header Length |  ATYP  | Variable |  Port |Data Length|
//+--------------+--------+----------+-------+-----------+
//|       1      |   1    | Variable |   2   |     2     |
//+--------------+--------+----------+-------+-----------+
//|<-----------------------Header----------------------->|


    @Override
    public void handle(Buffer data ){
	    try {
	        data = encryptor.decryptor(data);
	    } catch(Exception e ) {
	        log.debug("Exception occurred, Message:[{}], destroyed",e.getMessage());
	        destroyHandler();
	        return;
        }
	    if(streamStatus == StreamRunning){
            if(isLocal) {
                writeToSocket(localNetSocket,data);
            }else {
                writeToSocket(remoteNetSocket,data);
            }

        }else if(streamStatus == StreamAddrConnecting){
            //由于client的StreamInit状态在handleLocalRead已经处理了，所以这里就一定是server的Init状态
	        //在解密数据包的时候ATYP字段之前的数据已经被remove掉了，这里直接就和client的handleAddr一样
            //区别在如果port之后有数据那么就直接转发

            try {
                handleAddr(data);
                this.connectToRemote();
                streamStatus = StreamRunning;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (NotSupportOperation e ){
                log.error("[{}], handler destroyed",e.getMessage());
                destroyHandler();
                return ;
            }
        }
    }





    //由于socks握手还未结束，所以不再认为data之后存在要转发的数据，handleConnecting结束之后data直接丢弃
	private Buffer handleStageAddrConnecting(Buffer data){
	    int cmd = byteToUnsignInt(data.getByte(1));
	    if(cmd == CMD_CONNECT ){
	        //slice(startPosition,end) end == length;
            data = data.slice(3,data.length() );//start, length
        }else{
	        log.error("only support CMD_CONNECT");
	        this.destroyHandler();
        }
        return data;
    }

    private  void handleAddr(Buffer data) throws UnknownHostException , NotSupportOperation{
	    int type = byteToUnsignInt(data.getByte(0));
        if(type == ATYP_DOMAIN_NAME){
            int length = data.getByte( 1) & 0xff;
            byte[] hostBytes = data.getBytes( 2,2+ length);
            byte[] portBytes = data.getBytes( 2 + length,  2 + length + 2);
            this.dstHost = new String(hostBytes, Charset.forName("UTF-8"));
            this.dstPort = ((portBytes[0] & 0xff ) << 8) + (portBytes[1] & 0xff);

        }else if(type == ATYP_IPV4){
            byte[] ipv4 = data.getBytes(1, 1+4);
            this.dstHost = InetAddress.getByAddress(ipv4).getHostAddress();
            this.dstPort = ((data.getByte(5) & 0xff) << 8) + (data.getByte( 6) & 0xff);
        }else if(type == ATYP_IPV6){
            byte[] ipv6 = data.getBytes(1,1 + 16);
            this.dstHost = InetAddress.getByAddress(ipv6).getHostAddress();
            this.dstPort = ((data.getByte(16) & 0xff) << 8) + (data.getByte(17) & 0xff);
        }
        if(this.dstHost == null){
            log.error("dstHost is null, request's ten bytes: [{}]",Arrays.toString(data.getBytes(0,10)));
            throw new NotSupportOperation("bad request!, handler destroyed");
        }

    }



	private void handleStageInit(Buffer data){
		int version = byteToUnsignInt(data.getByte(0));
		if(version != 5){
		    log.debug("Only support Socks version 5, not [{}]",version);
			this.localNetSocket.write(Buffer.buffer(new byte[]{(byte)5,(byte)0xff}));
			this.destroyHandler();
			return;
		}else{
			int method = byteToUnsignInt(data.getByte(2));
			if(method == 0){
				this.localNetSocket.write(Buffer.buffer(new byte[]{(byte)5 , (byte)0}));
				return;
			}else{
				log.debug("Only support No auth, not [{}]",method);
			}
		}
	}

	
	private void handleLocalException(Throwable e) {
		log.debug("local exception [{}]",e.getMessage());
	}


    private void handleLocalEnd(Void v) {
        log.debug("{},local socks has been end, handler destroyed",this.tcpRelayHandlerId);
        this.destroyHandler();
    }

    private void handleLocalDrain(Void v) {
        log.debug("local socket writable, handleLocalDrain called");
        localNetSocket.drainHandler(null);
        this.writtingToSocket(localNetSocket);
    }

    private void handleLocalClose(Void v) {
        log.debug("local NetSocket has been closed, handler destroyed");
        destroyHandler();
    }

    private void handleRemoteException(Throwable e ){
	    this.destroyHandler();
	    log.debug("remote socket raise exception: [{}], handler destroyed",e.getMessage());
    }

    private void handleRemoteRead(Buffer data){
	    log.debug("recv data from remote, data length: [{}]",data.length());
	    if(isLocal){
	        this.parser.put(data);
        }else{
	        data = encryptor.encryptor(data);
            writeToSocket(localNetSocket,data);
	    }
    }

    private void handleRemoteDrain(Void v){
        log.debug("remote socket writable, handleRemoteDrain called");
        remoteNetSocket.drainHandler(null);
        this.writtingToSocket(remoteNetSocket);
    }

    private void handleRemoteClose(Void v){
	    this.destroyHandler();
        log.debug("remote socket closed, handler destroyed");
    }

    private void handleRemoteEnd(Void v){
	    log.debug("remote socks has been end, handler destroyed");
	    this.destroyHandler();
    }


}

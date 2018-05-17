package proxy;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proxy.Common.Util;

import java.util.*;

public class TCPRelay extends AbstractVerticle{
	private static final int MAX_HANDLERS_NUMBERS = 512;
	
	private Logger log = LoggerFactory.getLogger(TCPRelay.class);
	private HashMap<String,Object> config;
	private boolean isLocal ;

	
	private String localAddress = null;
	private String serverAddress = null;
	private int serverPort =  0;
	private String password = null;
	private String method = null;
	
	private int localPort = 0;
	private int timeout = 0;


	private int lastTimeoutIndex = 0;
	
	private List<TCPRelayHandler> tcpHandlers;
	private Map<Integer, Integer> handlersHashIndexMap;

    private Handler<Long> handleTimeoutHandlers;

	private NetServer netServer;

	private LengthFieldBasedFrameDecoder protocolParser ;
	
	public TCPRelay(HashMap<String,Object> config) {
		this.config  = Util.config;
		//this.config = config;
		this.tcpHandlers = new ArrayList<>();
		this.handlersHashIndexMap = new LinkedHashMap<>();
		this.isLocal = (Boolean) this.config.get("isLocal");


		this.method = (String)this.config.get("method");
		this.password = (String)this.config.get("password");
		this.timeout = (Integer) this.config.get("timeout");

		timeout = timeout < 20 ? 20: timeout;

		this.localAddress = (String)this.config.get("local_address");
		this.localPort = (Integer) this.config.get("local_port");
        this.serverPort = (Integer)this.config.get("server_port");
        this.serverAddress = (String)this.config.get("server_address");


		this.handleTimeoutHandlers = id ->{
		  this.handleTimeoutHandlers();
		  this.vertx.setTimer(timeout * 1000,this.handleTimeoutHandlers);
        };
				
	}

	public TCPRelay(){
	    this(null);
    }
	
	@Override
	public void start(Future<Void> startFuture) {
		if(isLocal){
		    this.netServer = createNetServer("0.0.0.0",this.localPort);

        }else{
		    this.netServer = createNetServer("0.0.0.0",this.serverPort);

        }
		startFuture.complete();
	}



	private NetServer createNetServer(String host, int port){
		NetServerOptions options = this.getNetServerOptions(host, port);
		this.netServer = this.vertx.createNetServer(options);
		netServer.connectHandler(netSocket->{
            if(!isLocal){
                SocketAddress addr = netSocket.localAddress();
                log.info("Accepted connection: [{}:{}]",addr.host(),addr.port());
            }
			TCPRelayHandler handler = new TCPRelayHandler (this.vertx,this,netSocket, this.isLocal,this.config);

			this.tcpHandlers.add(handler);
			this.handlersHashIndexMap.put(handler.hashCode(), this.tcpHandlers.size() - 1);

			assert this.tcpHandlers.get(this.handlersHashIndexMap.get(handler.hashCode())) == handler;

		}).exceptionHandler(result ->{
		    log.debug("some exception occurred");
		}).listen(result ->{
			if(result.succeeded()) {
				log.info("listen on [{}]",result.result().actualPort());
				if(isLocal){
				    log.info("server host: [{}:{}]",this.serverAddress,this.serverPort);
                }
			}else {
				log.error("listen failed, cause: [{}]",result.cause());
			}
		});
		return this.netServer;
	}
	
	private NetServerOptions getNetServerOptions(String host, int port) {
		NetServerOptions options = new NetServerOptions();
		options.setReuseAddress(true);
		options.setTcpNoDelay(true);
		options.setReusePort(true);
		options.setHost(host);
		options.setPort(port);
		options.setTcpKeepAlive(true);
		return options;
	}
	
	public void destroyHandler(TCPRelayHandler handler, boolean isDestroyed) {
		int hashCode = handler.hashCode();
		assert this.handlersHashIndexMap.containsKey(hashCode)
                :"Bad design, HashCode: " + hashCode + " cannot find in handlersHashIndexMap";

		int index = this.handlersHashIndexMap.get(hashCode);

		assert handler == this.tcpHandlers.get(index) : "Bad design, handler not equal handler in tcpHandlers";

		this.tcpHandlers.set(index, null);
		this.handlersHashIndexMap.remove(hashCode);

	}
	
	public void updateActivity(TCPRelayHandler handler) {
	    long now = Util.getCurrentTime();
	    if(now - handler.lastActiveTime < timeout)
	        return;

	    handler.lastActiveTime = now;
		int hashCode = handler.hashCode();
		log.debug("updateActivity, hashCode: [{}]",hashCode);

		assert this.handlersHashIndexMap.containsKey(hashCode): "Bad design, Map not contain HashCode " + hashCode;

		int index = this.handlersHashIndexMap.get(hashCode);

		assert tcpHandlers.get(index) == handler;

		this.tcpHandlers.set(index, null);
		this.tcpHandlers.add(handler);
		int lastIndex =	this.tcpHandlers.size() - 1;
		this.handlersHashIndexMap.put(hashCode, lastIndex);
	}
	
	private void handleTimeoutHandlers() {
	    int count = 0;
		int position = this.lastTimeoutIndex;
		int handlersLength = this.tcpHandlers.size();
		log.debug("Before clean timeout handlers, TCPHandlers size: [{}]",handlersLength);
		for(; position < handlersLength ; ++ position){
		    TCPRelayHandler handler = this.tcpHandlers.get(position);
		    if(handler != null ){
		        long time = handler.lastActiveTime;
		        long currentTime = Util.getCurrentTime();
		        if(currentTime - time < timeout){
		            break;
                }
                handler.destroyHandler();
		        count ++;

            }
        }

		if(position > MAX_HANDLERS_NUMBERS
                && position > this.tcpHandlers.size() >> 1){
		    log.debug("handlers list need be flush, start position: [{}]",position);
		    this.tcpHandlers = new ArrayList<>(this.tcpHandlers.subList(position,this.tcpHandlers.size()));
		    for(Map.Entry<Integer,Integer> key : this.handlersHashIndexMap.entrySet()){
		        key.setValue(key.getValue() - position);

		        assert key.getKey() == this.tcpHandlers.get(key.getValue()).hashCode()
                        :"key's value not equal handler hashcode";
            }
            position = 0;
        }
        this.lastTimeoutIndex = position;
		log.debug("clean timeout completed,[{}] handlers removed, TCPHandlers size: [{}], last position: [{}]",count,position,this.lastTimeoutIndex);
	}



}

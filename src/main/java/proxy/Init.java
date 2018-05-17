package proxy;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proxy.Common.Util;

import java.util.HashMap;

/**
 * Hello world!
 *
 */
public class Init 
{
    public static void main( String[] args )
    {
    	/*如果参数中指定了isLocal, 那么以参数的为准*/
    	Logger log = LoggerFactory.getLogger(Init.class);
        PropertyConfigurator.configure("resources/log4j.properties");
    	HashMap<String, Object> config = new HashMap<>();
    	boolean isLocal = true; 
    	boolean notIsLocal = true;
        for(int i = 0 ; i < args.length ; ++i) {
        	if(args[i].equals("--config") ) {
        		config = Util.getConfig(args[i + 1]);
        		if(config == null) {
        			log.error("config is null, JSONObject is failed");
        			System.exit(-1);
        		}        			
        	}else if(args[i].equals("--isLocal") ) {
        		notIsLocal = false;
        		if(args[i + 1].equals("true")) {
        			isLocal = true;
        		}else if(args[i + 1].equals("false")) {
        			isLocal = false;
        		}else {
        			log.error("isLocal is invalid");
        			System.exit(-1);
        		}
        	}        		
        }
        config.put("isLocal", new Boolean(isLocal));
        DeploymentOptions options = new DeploymentOptions().setInstances(4);
        Vertx.vertx().deployVerticle(TCPRelay.class.getName(),options);
       // Vertx.vertx().deployVerticle(new TCPRelay(config));
    }
}

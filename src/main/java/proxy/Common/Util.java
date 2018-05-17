package proxy.Common;

import io.vertx.core.buffer.Buffer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;

public class Util {
    public static HashMap<String,Object> config;
	public static Logger log = LoggerFactory.getLogger(Util.class);
	private static MessageDigest messageDigest;

    static {
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String,Object> getConfig(String path) {
		try {
			byte[] config = Files.readAllBytes(Paths.get(path));
			JSONObject jsonObject = new JSONObject(new String(config,Charset.forName("UTF-8")));
			return (Util.config = (HashMap<String, Object>)jsonObject.toMap());
		} catch (IOException e) {
			log.error("cannot find config path,{}",e.getMessage());
		}
		return null;
	}
	
	public static long getCurrentTime() {
		return Instant.now().toEpochMilli() / 1000;
	}

	public static int byteToUnsignInt(byte b){
		return b & 0xff;
	}

	public static byte[] shortToByteArray(short s){return new byte[]{(byte)(s >> 8),(byte)s}; }

	public static byte[] shortToByteArray(int i){
	    final short s = (short)i;
	    return new byte[]{(byte)(s >> 8),(byte)s};
	};

	public static byte[] getMD5(String s, int length){
        return Arrays.copyOf(
                messageDigest.digest(s.getBytes(Charset.forName("UTF-8")))
                ,length);
    }

    public static <T> String getIpAsStringFromStream(int start, int length, T data)
            throws UnknownHostException {

	    if(data instanceof Buffer){
	        if(length == 4){
	            return Inet4Address.getByAddress(((Buffer) data).getBytes(start,start + length)).getHostAddress();
            }else{//ipv6
                return Inet6Address.getByAddress(((Buffer) data).getBytes(start,start + length)).getHostAddress();
            }
        }
        return null;
    }
}

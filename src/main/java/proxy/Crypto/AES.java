package proxy.Crypto;

import io.vertx.core.buffer.Buffer;
import proxy.Common.Util;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;

public class AES implements ICrypto<Buffer,Buffer>{
    private Cipher dCipher;
    private Cipher eCipher;
    private SecretKey secretKey;
    //private byte[] iv = new;

    public AES(String passwd, int keyLength, String mode){
        secretKey = new SecretKeySpec(Util.getMD5(passwd,keyLength/8),"AES");
        if(mode.toUpperCase().equals("GCM")){
            String algorithm  = new StringBuilder().append("AES/").append(Integer.toString(keyLength)).append("/GCM").toString();
            try {
                dCipher = Cipher.getInstance(algorithm);
                eCipher = Cipher.getInstance(algorithm);
                //dCipher.init();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Buffer encrypt(Buffer data) {
        return null;
    }

    @Override
    public Buffer decrypt(Buffer data) {
        return null;
    }

}

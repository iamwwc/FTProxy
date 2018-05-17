package proxy;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.util.Random;


public class Encryptor_Test {
    private String passwd = "hello";
    private Encryptor encryptor = new Encryptor(passwd,"");
    private Encryptor decryptor = new Encryptor(passwd,"");

    private Handler<Buffer> remoteHandler = data ->{
        try {
            data = encryptor.decryptor(data);
        } catch(Exception e ) {

            return;
        }
    };

    Handler<Buffer> localHandler = data ->{
        try {
            data = encryptor.decryptor(data);
        } catch(Exception e ) {

            return;
        }
    };

    @Test
    public void encryptorAndProtocolParser_Test(){

        ProtocolParser localParser = new ProtocolParser(localHandler,true);
        ProtocolParser remoteParser = new ProtocolParser(remoteHandler,false);

        int count = 100;


        while(count != 0){
            count --;
            Buffer data = getRandomData();
            Buffer needEncrypt = data.copy();
            needEncrypt = encryptor.encryptor(needEncrypt);
            remoteParser.put(needEncrypt);
            Buffer needDecrypt = getRandomData();
            needDecrypt = decryptor.encryptor(needDecrypt);
            localParser.put(needDecrypt);


        }

    }

    public Buffer getRandomData(){
        int MIN_LENGTH = 10000;
        Random random = new Random();
        int randomLength = random.nextInt(MIN_LENGTH + 1) + MIN_LENGTH;
        byte[] randomBytes = new byte [randomLength];
        random.nextBytes(randomBytes);
        return Buffer.buffer(randomBytes);
    }
}

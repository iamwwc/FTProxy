package proxy;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ProtocolParserTest {
    private static final int MIN_LENGTH = 10000;


    @Test
    public void protocolParserPut_Test(){
        Handler<Buffer> handler = data ->{
            int length = data.getUnsignedShort(0);
            assertEquals(data.length(),length);
        };

        ProtocolParser parser = new ProtocolParser(handler,false);
        Random random = new Random();
        int count = 1000;
        boolean isFirst = true;
        while(count != 0){
            count --;
            int randomLength = random.nextInt(MIN_LENGTH + 1) + MIN_LENGTH;
            byte[] randomBytes = new byte [randomLength];
            random.nextBytes(randomBytes);
            Buffer buf = null;
            if(isFirst){
                buf = Buffer.buffer(randomBytes.length + 4);
                buf.appendUnsignedShort(randomBytes.length + 4);
                buf.appendUnsignedShort(0);
                isFirst = false;
            }else{
                buf = Buffer.buffer(randomBytes.length + 2);
                buf.appendUnsignedShort(randomBytes.length + 2);
            }
            buf.appendBytes(randomBytes);
            parser.put(buf);

        }

    }




}

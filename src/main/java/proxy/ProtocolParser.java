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


import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class ProtocolParser  {
    private static final Logger log = LoggerFactory.getLogger(ProtocolParser.class);

    private static final int MAX_BUFFER_LENGTH = 32*1024;
    private static final int StatusStream_Init = 1;
    private static final int Status_StreamRunning = 2;
    private static final int Status_StreamRunning_NewDataCome = 3;

    int status ;
    int length = 0;//packet length
    int currentLength = 0;//当前buffer里面存放的数据长度
    int lastPosition = 0;//上一次处理完之后的新包的第一个字节位置

    private boolean isLocal;

    private int defaultMaxBufferLength = MAX_BUFFER_LENGTH;

    private Handler<Buffer> handler;


    private ByteBuffer buffer = null;

    public ProtocolParser(Handler handler,boolean isLocal){
        this.handler = handler;
        this.isLocal = isLocal;
        buffer = ByteBuffer.allocate(defaultMaxBufferLength);
        if(!isLocal){
            status = StatusStream_Init;
        }else{
            status = Status_StreamRunning_NewDataCome;
        }
    }

    public void put(ByteBuffer data){
        if(status == StatusStream_Init){
            data.mark();
            int headerLength = data.getShort() & 0xffff;
            int dataLength = data.getShort() & 0xffff;
            length = headerLength + dataLength;
            data.reset();
            status = Status_StreamRunning;
        }else if(status == Status_StreamRunning_NewDataCome){
            data.mark();
            int dataLength = data.getShort() & 0xffff;
            length = dataLength;
            data.reset();
            status = Status_StreamRunning;
        }

        //most bytes we can receive
        int maxLength = buffer.remaining();
        if(maxLength < data.remaining()){
            flushBuffer();
        }
        currentLength += data.remaining();
        try {
            buffer.put(data);
        }catch(BufferOverflowException e ){
            log.error("BufferOverFlowException, length need: [{}], last position: [{}], current length: [{}]",
                    length,lastPosition,currentLength);
            ByteBuffer buf = ByteBuffer.allocate(MAX_BUFFER_LENGTH * 2);
            int newLimit = buffer.position();
            buffer.position(lastPosition);
            buffer.limit(newLimit);
            buf.put(buffer);
            buffer = buf;
            lastPosition = 0;
            buffer.put(data);
        }

        while(checkHavePacket()){}

    }

    private boolean checkHavePacket(){
        if(currentLength == 0)
            return false;
        if(currentLength >= length){
            Buffer buf = getCimpletedData(buffer);
            setCompletedDataLength(buffer);
            handler.handle(buf);
            return true;
        }
        return false;
    }


    public void put(Buffer data){
        byte[] buf = data.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        put(buffer);
    }

    private Buffer getCimpletedData(ByteBuffer buffer){
        int currentPosition = buffer.position();
        buffer.position(lastPosition);
        buffer.limit(lastPosition + length);
        lastPosition = buffer.limit();
        currentLength -= length;

        int remaining = buffer.remaining();
        byte[] completedData = new byte[remaining];
        buffer.get(completedData,0,remaining);
        Buffer data = Buffer.buffer(completedData);

        //this data is last packet, no more data behind it
        if(buffer.position() == currentPosition){
            buffer.clear();
            status = Status_StreamRunning_NewDataCome;
            lastPosition = 0;
            length = 0;
            currentLength = 0;
            return data;
        }
        buffer.limit(buffer.capacity());
        buffer.position(currentPosition);

        return data;
    }

    private void setCompletedDataLength(ByteBuffer data){
        int dataLength = data.getShort(lastPosition) & 0xffff;
        //length =  dataLength + 2;// dataLength + data Length variable
        length = dataLength;
    }

    private void flushBuffer(){
        int newLimit = buffer.position();
        buffer.position(lastPosition);
        buffer.limit(newLimit);
        buffer.compact();
        lastPosition = 0;
    }



}
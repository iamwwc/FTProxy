package proxy;

import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.security.*;
import java.util.Arrays;


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


public class Encryptor {
    private Logger log = LoggerFactory.getLogger(Encryptor.class);
    private String password ;
    private String method;
    private static final int AES_KEY_LENGTH = 128;
    private static final int AES_AUTHORIZATION_TAG_LENGTH = 128;
    private static final int AES_AUTHORIZATION_TAG_LENGTH_INT = AES_AUTHORIZATION_TAG_LENGTH / 8;
    private static final int AES_GCM_IV_LENGTH = 12;
    private static final int HeaderLength_LENGTH = 2;
    private static final int DataLength_LENGTH = 2;
    private static final int Authorized_Header_Length = HeaderLength_LENGTH + DataLength_LENGTH + AES_GCM_IV_LENGTH;


    SecureRandom random = new SecureRandom();

    private Cipher encryptor = null;
    private Cipher decryptor = null;

    private SecretKey secretKey = null;
    private MessageDigest messageDigest = null;

    private byte[] iv = new byte[AES_GCM_IV_LENGTH];
    public Encryptor(String password, String method){
        this.password = password;
        this.method = method;

        try {
            this.messageDigest = MessageDigest.getInstance("SHA-1");
            byte[] aesKey = getMD5(this.password.getBytes(Charset.forName("UTF-8")),AES_KEY_LENGTH / 8);
            this.secretKey = new SecretKeySpec(aesKey,"AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Encryptor(){}

    /*Iv length is 12 bytes*/

    public Buffer decryptor(Buffer data)
            throws IllegalBlockSizeException
            , BadPaddingException
            , InvalidAlgorithmParameterException
            , InvalidKeyException {

        if(this.decryptor == null){
            try {
                this.decryptor = Cipher.getInstance("AES/GCM/NoPadding");
                this.encryptor = Cipher.getInstance("AES/GCM/NoPadding");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }
            return decryptorInit(data);
        }
        return decryptorRunning(data);
    }


    private Buffer decryptorRunning(Buffer data)
            throws InvalidAlgorithmParameterException
            , InvalidKeyException, BadPaddingException
            , IllegalBlockSizeException {

        iv = data.getBytes(2,2 + AES_GCM_IV_LENGTH);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(AES_AUTHORIZATION_TAG_LENGTH,iv);

        this.decryptor.init(Cipher.DECRYPT_MODE,this.secretKey,gcmSpec);
        int aadLength = DataLength_LENGTH + AES_GCM_IV_LENGTH;
        this.decryptor.updateAAD(data.getBytes(0,aadLength));
        byte[] needDecrypted = data.getBytes(aadLength,data.length());
        byte[] decrypted = this.decryptor.doFinal(needDecrypted);
        return Buffer.buffer(decrypted);
    }

    private Buffer decryptorInit(Buffer data)
            throws InvalidAlgorithmParameterException
            , InvalidKeyException
            , BadPaddingException
            , IllegalBlockSizeException {

        int aadLength = HeaderLength_LENGTH + DataLength_LENGTH + AES_GCM_IV_LENGTH;
        iv = data.getBytes(HeaderLength_LENGTH + DataLength_LENGTH,HeaderLength_LENGTH + DataLength_LENGTH + AES_GCM_IV_LENGTH);

        decryptor.init(Cipher.DECRYPT_MODE,this.secretKey,new GCMParameterSpec(AES_AUTHORIZATION_TAG_LENGTH,iv));
        decryptor.updateAAD(data.getBytes(0,aadLength));
        data = data.slice(aadLength,data.length());
        return Buffer.buffer(decryptor.doFinal(data.getBytes()));

    }




    public Buffer encryptor(Buffer data){
        if(this.encryptor == null){
            try {
                this.encryptor = Cipher.getInstance("AES/GCM/NoPadding");
                this.decryptor = Cipher.getInstance("AES/GCM/NoPadding");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            }
            return this.encryptInit(data);
        }else{
            return this.encryptorRunning(data);
        }
    }


    private byte[] getMD5(byte[] data){
        return this.messageDigest.digest(data);
    }

    private Buffer encryptorRunning(Buffer data){
        int totalPacketLength = DataLength_LENGTH + AES_GCM_IV_LENGTH + data.length() + AES_AUTHORIZATION_TAG_LENGTH_INT;
        Buffer encryptedData = Buffer.buffer(totalPacketLength);
        encryptedData.appendUnsignedShort(totalPacketLength);
        random.nextBytes(this.iv);
        encryptedData.appendBytes(iv);

        try {
            GCMParameterSpec gcmSpec = new GCMParameterSpec(this.AES_AUTHORIZATION_TAG_LENGTH,this.iv);
            encryptor.init(Cipher.ENCRYPT_MODE,this.secretKey,gcmSpec);
            int aadLength = AES_GCM_IV_LENGTH + DataLength_LENGTH;
            encryptor.updateAAD(encryptedData.getBytes(0,aadLength));
            byte[] encrypted = encryptor.doFinal(data.getBytes());
            encryptedData.appendBytes(encrypted);
            return encryptedData;
        } catch (IllegalBlockSizeException
                | BadPaddingException
                | InvalidAlgorithmParameterException
                | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getMD5(byte[] data ,int length){
        byte[] md5 = getMD5(data);
        return Arrays.copyOf(md5,length);
    }

    private Buffer encryptInit(Buffer data){
        int totalLength = HeaderLength_LENGTH + DataLength_LENGTH + AES_GCM_IV_LENGTH + data.length() + AES_AUTHORIZATION_TAG_LENGTH_INT;

        byte[] iv = new byte[AES_GCM_IV_LENGTH];

        random.nextBytes(iv);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(AES_AUTHORIZATION_TAG_LENGTH,iv);
        try {
            encryptor.init(Cipher.ENCRYPT_MODE,this.secretKey,gcmSpec);
            Buffer encryptedData = Buffer.buffer(totalLength);
            encryptedData.appendShort((short)totalLength);
            encryptedData.appendShort((short)0);
            encryptedData.appendBytes(iv);
            byte[] aad = encryptedData.getBytes(0,Authorized_Header_Length);
            encryptor.updateAAD(aad);
            byte[] ae = data.getBytes();
            encryptedData.appendBytes(encryptor.doFinal(ae));
            return encryptedData;
        } catch (InvalidKeyException
                | InvalidAlgorithmParameterException
                | BadPaddingException
                | IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return null;
    }


    //remove 2 bytes data length from head
    private Buffer getStreamRunningPacket(Buffer data){
        data = data.slice(2,data.length());
        return data;
    }

}

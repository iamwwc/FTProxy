package proxy.Protocols.Socks;

public class BadSocksHandShakeException extends Exception {
    private String message;

    public BadSocksHandShakeException(){
        super();
    }

    public BadSocksHandShakeException(String message){
        super(message);

    }


    public BadSocksHandShakeException(String message, Throwable cause){
        super(message,cause);
    }

}

package proxy.Exception;

public class NotSupportOperation extends  Exception{
    private String msg;


    public NotSupportOperation(String message){
     super(message);
    }
}

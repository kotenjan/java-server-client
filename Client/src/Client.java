import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {

    public static void main(String[] args) {
        for (int i = 0; i < 1; i++) {
            new Thread() {

                public void run() {
                    try {
                        Scanner scanner = new Scanner(System.in);
                        Socket s = new Socket(InetAddress.getByName("localhost"), 3999);
                        PrintWriter w = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                        Thread t1 = new Thread(new Helper(s));
                        t1.start();

                        while (true) {
                            System.out.print("Enter Message: ");
                            String command = scanner.nextLine();
                            w.printf(command + "\u0007" + "\u0008");
                            System.out.println("--------------------");
                        }
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }
            }.start();
        }

    }
}

class sender extends Thread{
    sender(Socket s){

    }

    public void run(){
    }
}

class Helper extends Thread{
    Socket s = null;

    Helper(Socket s){
        this.s = s;
    }
    public void run(){
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
            while(true){
                if(r.ready()){
                    System.out.println("Data from server: ");
                    System.out.println(r.readLine());
                    System.out.flush();
                }
            }
        } catch (IOException ex) {
            System.out.println("Socket Problem");
            System.out.flush();
        }
    }
}

import javafx.util.Pair;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class ServerRunner extends Thread {

    static final int TIMEOUT = 1000;
    static final int RECHARGING = 5000;
    static final int BELL = 7;
    static final int BACKSPACE = 8;
    static final int TEXT_LENGTH = 12;
    static final int MESSAGE_LENGTH = 100;

    Pair<Integer, Integer> position;
    int direction;
    Socket socket;
    BufferedReader reader;
    PrintWriter writer;

    ServerRunner(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
        this.writer = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream()), true);
    }

    @Override
    public void run() {
        try {
            System.out.println("From " + this.socket.getRemoteSocketAddress() + " Device connected!");

            int key_out = HashKey(Initialize());
            List<Integer> message = SendMessage(Integer.toString((key_out + 54621) % 65536), true, true);

            if (!CheckKey(message == null ? Get(TEXT_LENGTH, false) : message, key_out)) {
                SendMessage("300 LOGIN FAILED", false, false);
                throw new InvalidParameterException("ID not matching");
            }
            message = SendMessage("200 OK", true, true);

            if (!Navigate_to_perimeter(message))
                SearchSquare();

            SendMessage("106 LOGOUT", false, false);


        } catch (InterruptedIOException ioe) {
            System.err.println("Remote host timed out during read operation");
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        try {
            System.out.println("Closing connection");
            socket.close();
            reader.close();
            writer.close();
        } catch (Exception e) {
            System.err.println("Can't close all resources: " + e);
        }
    }

    private List<Integer> SendMessage(String text, Boolean recursive, Boolean begin) throws Exception {
        List<Integer> message = null;
        if (recursive) {
            TimeUnit.MILLISECONDS.sleep(5);
            while (this.reader.ready()) {
                message = begin ? Read() : Read(TEXT_LENGTH);
                if (!Compare("RECHARGING", message)) {
                    break;
                }
                this.socket.setSoTimeout(RECHARGING);
                if (!Compare("FULL POWER", Read(TEXT_LENGTH))) {
                    SendMessage("302 LOGIC ERROR", false, false);
                    throw new InvalidParameterException("FULL POWER not received!");
                }
            }
            if (Compare("RECHARGING", message))
                message = null;
        }

        this.writer.printf(text + "\u0007" + "\u0008");
        return message;
    }

    private List<Integer> Get(int max_len, Boolean recharging) throws Exception {
        this.socket.setSoTimeout(recharging ? RECHARGING : TIMEOUT);

        List<Integer> message = Read(max_len);

        if (!recharging) {
            if (Compare("RECHARGING", message)) {
                if (Compare("FULL POWER", Get(TEXT_LENGTH, true)))
                    return Get(TEXT_LENGTH, false);
                else {
                    SendMessage("302 LOGIC ERROR", false, false);
                    throw new InvalidParameterException("FULL POWER not received!");
                }
            }
        }

        return message;
    }

    private List<Integer> Read(int max_len) throws Exception {
        List<Integer> message = new ArrayList<>();
        int a = this.reader.read(), b = this.reader.read(), count = 2;

        while (a == -1)
            a = this.reader.read();

        while (b == -1)
            b = this.reader.read();

        while (!(a == BELL && b == BACKSPACE) && count++ < max_len) {
            message.add(a);
            a = b;
            do {
                b = this.reader.read();
            } while (b == -1);
        }

        if (a != BELL || b != BACKSPACE) {
            SendMessage("301 SYNTAX ERROR", false, false);
            throw new InvalidParameterException("Message too long!");
        }

        return message;
    }

    private List<Integer> Read() throws Exception {
        List<Integer> message = new ArrayList<>();
        int a, b, count = 2;
        if (this.reader.ready())
            a = this.reader.read();
        else
            return message;
        if (this.reader.ready())
            b = this.reader.read();
        else {
            message.add(a);
            return message;
        }

        while (!(a == BELL && b == BACKSPACE) && count++ < TEXT_LENGTH && a != -1 && b != -1) {
            message.add(a);
            a = b;
            b = this.reader.read();
        }


        if (count > TEXT_LENGTH) {
            SendMessage("301 SYNTAX ERROR", false, false);
            throw new InvalidParameterException("Message too long!");
        }

        return message;
    }

    private void SearchSquare() throws Exception {
        PositionRobot(-1);
        if (CheckMessage())
            return;

        for (int i = 0; i < 4; ++i) {
            MoveForward();
            if (CheckMessage())
                return;
        }
        TurnRight();

        for (int k = 4; k > 0; --k) {
            for (int j = 0; j < 2; ++j) {
                for (int i = 0; i < k; ++i) {
                    MoveForward();
                    if (CheckMessage())
                        return;
                }
                TurnRight();
            }
        }
    }

    private Boolean Navigate_to_perimeter(List<Integer> message1) throws Exception {
        List<Integer> message2 = SendMessage("102 MOVE", true, true);
        Pair<Integer, Integer> position_a = GetPosition(message1 == null ? Get(TEXT_LENGTH, false) : message1);
        this.position = position_a;

        if (CheckMessage())
            return true;


        SendMessage("102 MOVE", true, false);
        Pair<Integer, Integer> position_b = GetPosition(message2 == null ? Get(TEXT_LENGTH, false) : message2);
        while (position_b.equals(position_a)) {
            SendMessage("102 MOVE", true, false);
            position_b = GetPosition(Get(TEXT_LENGTH, false));
        }
        this.position = position_b;

        if (CheckMessage())
            return true;

        this.direction = 2 * (position_b.getKey() - position_a.getKey())
                + (position_b.getValue() - position_a.getValue());

        int x = 2 - this.position.getKey();
        int y = 2 - this.position.getValue();

        if (x < 0)
            PositionRobot(-2);
        else if (x > 0)
            PositionRobot(2);

        while (this.position.getKey() != 2) {
            MoveForward();
            if (CheckMessage())
                return true;
        }

        if (y < 0)
            PositionRobot(-1);
        else if (y > 0)
            PositionRobot(1);

        while (this.position.getValue() != 2) {
            MoveForward();
            if (CheckMessage())
                return true;
        }

        return false;
    }

    private Boolean CheckMessage() throws Exception {
        if (this.position.getValue() >= -2 && this.position.getValue() <= 2
                && this.position.getKey() >= -2 && this.position.getKey() <= 2) {
            SendMessage("105 GET MESSAGE", true, false);
            List<Integer> message = Get(MESSAGE_LENGTH, false);
            return !message.isEmpty();
        }
        return false;
    }

    private void MoveForward() throws Exception {
        SendMessage("102 MOVE", true, false);
        Pair<Integer, Integer> position_b = GetPosition(Get(TEXT_LENGTH, false));

        while (position_b.equals(this.position)) {
            SendMessage("102 MOVE", true, false);
            position_b = GetPosition(Get(TEXT_LENGTH, false));
        }

        this.position = position_b;
    }

    private void TurnRight() throws Exception {
        SendMessage("104 TURN RIGHT", true, false);
        GetPosition(Get(TEXT_LENGTH, false));
    }

    private void TurnLeft() throws Exception {
        SendMessage("103 TURN LEFT", true, false);
        GetPosition(Get(TEXT_LENGTH, false));
    }

    private Pair<Integer, Integer> GetPosition(List<Integer> message) throws Exception {

        int count = 0;
        for (int a : message) {
            if ((char) a == ' ')
                ++count;
        }

        if (count != 2) {
            SendMessage("301 SYNTAX ERROR", false, false);
            throw new InvalidParameterException("Message too long!");
        }

        StringBuilder builder = new StringBuilder();
        for (int a : message)
            builder.append((char) a);

        String[] split = builder.toString().split("\\s+");

        if (split.length != 3 || !split[0].equals("OK")) {
            SendMessage("301 SYNTAX ERROR", false, false);
            throw new InvalidParameterException("Position unclear!");
        }
        try {
            return new Pair<>(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
        } catch (NumberFormatException e) {
            SendMessage("301 SYNTAX ERROR", false, false);
            System.err.println(e.getMessage());
        }
        throw new InvalidParameterException("Invalid position");
    }

    private Boolean Compare(String reference, List<Integer> text) {
        if (text == null)
            return false;
        if (reference.length() != text.size())
            return false;

        int i = 0;
        for (int a : text) {
            if (a != reference.charAt(i++))
                return false;
        }
        return true;
    }

    private Boolean CheckKey(List<Integer> key, int to_check) throws Exception {
        if (key.size() > 5) {
            SendMessage("301 SYNTAX ERROR", false, false);
            throw new InvalidParameterException("Position unclear!");
        }
        int number = 0;

        if (key.isEmpty())
            return false;

        for (int a : key) {
            number *= 10;
            if ((a - '0') % 10 != (a - '0')) {
                SendMessage("301 SYNTAX ERROR", false, false);
                throw new InvalidParameterException("Position unclear!");
            }
            number += (a - '0');
        }

        return number == (to_check + 45328) % 65536;
    }

    private int HashKey(List<Integer> ID) {
        int number = 0;
        for (int i : ID)
            number += i;
        return (number * 1000) % 65536;
    }

    private List<Integer> Initialize() throws Exception {
        this.socket.setSoTimeout(TIMEOUT);
        return Read(TEXT_LENGTH);
    }

    private void PositionRobot(int direction) throws Exception {
        switch (direction) {
            case -2:
                switch (this.direction) {
                    case 1:
                        TurnLeft();
                        break;
                    case -1:
                        TurnRight();
                        break;
                    case 2:
                        TurnRight();
                        TurnRight();
                }
                break;
            case -1:
                switch (this.direction) {
                    case -2:
                        TurnLeft();
                        break;
                    case 2:
                        TurnRight();
                        break;
                    case 1:
                        TurnRight();
                        TurnRight();
                }
                break;
            case 1:
                switch (this.direction) {
                    case 2:
                        TurnLeft();
                        break;
                    case -2:
                        TurnRight();
                        break;
                    case -1:
                        TurnRight();
                        TurnRight();
                }
                break;
            case 2:
                switch (this.direction) {
                    case -1:
                        TurnLeft();
                        break;
                    case 1:
                        TurnRight();
                        break;
                    case -2:
                        TurnRight();
                        TurnRight();
                }
                break;
        }
        this.direction = direction;
    }
}

public class Server {

    private final ServerSocket server;

    public Server() throws Exception {
        this.server = new ServerSocket(3999, 1, InetAddress.getByName("localhost"));
    }

    private void listen() {

        try {
            while (true) {
                Thread Server = new Thread(new ServerRunner(this.server.accept()));
                Server.start();
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        try {
            server.close();
        } catch (Exception ignored) {

        }
    }

    public InetAddress getSocketAddress() {
        return this.server.getInetAddress();
    }

    public int getPort() {
        return this.server.getLocalPort();
    }

    public static void main(String[] args) throws Exception {
        Server app = new Server();
        System.out.println("\r\nRunning Server: " +
                "Host=" + app.getSocketAddress().getHostAddress() +
                " Port=" + app.getPort());

        app.listen();
    }
}
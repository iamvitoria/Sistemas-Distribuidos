package causalmulticast;

public class Participant {

    private String ip;
    private int port;
    private int id;

    public Participant(int id, String ip, int port) {
        this.id = id;
        this.ip = ip;
        this.port = port;
    }

    public int getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
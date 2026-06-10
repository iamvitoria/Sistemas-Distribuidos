package causalmulticast;

import java.io.Serializable;

public class Message implements Serializable {

    private String content;
    private int senderId;
    private int[] vectorClock;

    public Message(String content,
                   int senderId,
                   int[] vectorClock) {

        this.content = content;
        this.senderId = senderId;
        this.vectorClock = vectorClock;
    }

    public String getContent() {
        return content;
    }

    public int getSenderId() {
        return senderId;
    }

    public int[] getVectorClock() {
        return vectorClock;
    }
}
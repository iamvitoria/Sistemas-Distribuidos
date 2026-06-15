package CausalMulticast;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {

    private String content;
    private int senderId;
    private Map<Integer, Integer> vectorClock;
    private Map<Integer, Integer> matrixRow;

    public Message(
            String content,
            int senderId,
            Map<Integer, Integer> vectorClock,
            Map<Integer, Integer> matrixRow) {

        this.content = content;
        this.senderId = senderId;
        this.vectorClock = vectorClock != null ? new HashMap<>(vectorClock) : new HashMap<>();
        this.matrixRow = matrixRow != null ? new HashMap<>(matrixRow) : new HashMap<>();
    }

    public String getContent() {
        return content;
    }

    public int getSenderId() {
        return senderId;
    }

    public Map<Integer, Integer> getVectorClock() {
        return vectorClock;
    }

    public Map<Integer, Integer> getMatrixRow() {
        return matrixRow;
    }

    @Override
    public String toString() {
        return "Mensagem: " + content +
                "\nRemetente: " + senderId +
                "\nVC: " + vectorClock;
    }
}
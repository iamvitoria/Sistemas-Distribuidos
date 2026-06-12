package causalmulticast;

import java.io.Serializable;
import java.util.Arrays;

public class Message implements Serializable {

    private String content;
    private int senderId;
    private int[] vectorClock;
    private int[] matrixRow;

    public Message(
            String content,
            int senderId,
            int[] vectorClock,
            int[] matrixRow) {

        this.content = content;
        this.senderId = senderId;
        this.vectorClock = vectorClock;
        this.matrixRow = matrixRow;
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

    public int[] getMatrixRow() {
        return matrixRow;
    }

    @Override
    public String toString() {
        return "Mensagem: " + content +
                "\nRemetente: " + senderId +
                "\nVC: " + Arrays.toString(vectorClock);
    }
}
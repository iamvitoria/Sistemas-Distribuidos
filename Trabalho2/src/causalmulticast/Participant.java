package CausalMulticast;

/**
 * Representa um participante do grupo multicast.
 *
 * Armazena informações necessárias para envio de
 * mensagens UDP entre os processos.
 *
 */
public class Participant {

    private String ip;
    private int port;
    private int id;

    /**
     * Cria um novo participante.
     *
     * @param id identificador interno do participante
     * @param ip endereço IP
     * @param port porta de comunicação
     */
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

    @Override
    public String toString() {
        return "P" + id + " (" + ip + ":" + port + ")";
    }
}
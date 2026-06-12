package causalmulticast;

import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

/**
 * Classe principal do Middleware CausalMulticast.
 * Responsável por gerenciar a descoberta de membros, envio de mensagens UDP
 * simulando multicast, ordenação causal via relógios vetoriais e
 * descarte de mensagens da memória via algoritmo de estabilização (matriz de relógios).
 */

public class CausalMulticast {

    private DatagramSocket socket;
    private ICausalMulticast client;
    private final List<Participant> participants = new ArrayList<>();
    private final String MULTICAST_IP = "230.0.0.1";
    private final int MULTICAST_PORT = 4446;
    private int myPort;
    private int[] vectorClock = new int[34];
    private int[][] matrixClock = new int[34][34];
    private Queue<Message> messageBuffer = new LinkedList<>();
    private List<DelayedMessage> delayedMessages = new ArrayList<>();
    // Buffer para mensagens entregues que aguardam estabilização
    private List<Message> historyBuffer = new ArrayList<>();

    /**
     * Construtor do Middleware.
     * Inicializa os sockets UDP para comunicação e inicia as threads
     * do Serviço de Descoberta (IP Multicast).
     * * @param ip O endereço IP local do usuário (ex: "localhost").
     * @param port A porta em que este usuário receberá as mensagens unicast.
     * @param client Referência para a aplicação do usuário (para callback via deliver).
     */

    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        /**
         * Realiza o envio multicast de uma mensagem para todos os participantes descobertos.
         * O método anexa o relógio vetorial atual (piggyback) para garantir a ordem causal.
         * Permite atrasar mensagens manualmente para fins de demonstração.
         * * @param msg O conteúdo da mensagem a ser enviada.
         * @param client Referência do cliente solicitante do envio.
         */

        this.client = client;
        this.myPort = port;

        try {
            socket = new DatagramSocket(port);
            iniciarRecepcaoUDP();
            iniciarDescoberta();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getMyIndex() {
        return myPort - 5001;
    }

    private void iniciarRecepcaoUDP() {
        Thread receiver = new Thread(() -> {
            while (true) {
                try {
                    byte[] buffer = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] receivedData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, receivedData, 0, packet.getLength());

                    Message message = deserialize(receivedData);

                    System.out.println("\nRelógio recebido:");
                    System.out.println(Arrays.toString(message.getVectorClock()));

                    if (canDeliver(message)) {
                        deliverMessage(message);
                        processBuffer();
                    } else {
                        messageBuffer.add(message);
                        System.out.println("\nMensagem armazenada no buffer.");
                        mostrarBuffer();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private boolean canDeliver(Message message) {

        int senderIndex = message.getSenderId() - 5001;
        int[] msgVC = message.getVectorClock();

        if (msgVC[senderIndex] != vectorClock[senderIndex] + 1) {
            return false;
        }

        for (int i = 0; i < msgVC.length; i++) {
            if (i != senderIndex && msgVC[i] > vectorClock[i]) {
                return false;
            }
        }

        return true;
    }

    private void deliverMessage(Message message) {
        int senderIndex = message.getSenderId() - 5001;

        vectorClock[senderIndex]++;
        matrixClock[senderIndex] = message.getVectorClock().clone();

        if (senderIndex != getMyIndex()) {
            matrixClock[getMyIndex()][senderIndex]++;
        }

        System.out.println("\n[CAUSAL] Mensagem entregue: " + message.getContent());

        historyBuffer.add(message);
        client.deliver(message.toString());
        verificarEstabilizacao();
    }

    private void verificarEstabilizacao() {
        List<Message> estabilizadas = new ArrayList<>();

        List<Integer> ativos = new ArrayList<>();
        ativos.add(getMyIndex());
        for (Participant p : participants) {
            ativos.add(p.getPort() - 5001);
        }

        for (Message msg : historyBuffer) {
            int senderIndex = msg.getSenderId() - 5001;
            int timestampRemetente = msg.getVectorClock()[senderIndex];

            int min = Integer.MAX_VALUE;
            for (int x : ativos) {
                if (matrixClock[x][senderIndex] < min) {
                    min = matrixClock[x][senderIndex];
                }
            }

            if (timestampRemetente <= min) {
                estabilizadas.add(msg);
            }
        }

        if (!estabilizadas.isEmpty()) {
            historyBuffer.removeAll(estabilizadas);
            System.out.println("\n[ESTABILIZAÇÃO] " + estabilizadas.size() + " mensagens foram estabilizadas e descartadas!");
            for(Message m : estabilizadas){
                System.out.println("  -> Descartada: " + m.getContent());
            }
        }
    }

    private void mostrarMatriz() {
        System.out.println("\nMatriz:");

        for (int i = 0; i < participants.size() + 1; i++) {
            System.out.println(Arrays.toString(matrixClock[i]));
        }
    }

    private void processBuffer() {
        boolean delivered;

        do {
            delivered = false;
            List<Message> remover = new ArrayList<>();

            for (Message msg : messageBuffer) {
                if (canDeliver(msg)) {
                    deliverMessage(msg);
                    remover.add(msg);
                    delivered = true;
                }
            }

            messageBuffer.removeAll(remover);

        } while (delivered);
    }

    private void mostrarBuffer() {

        System.out.println("\nBuffer:");

        if (messageBuffer.isEmpty()) {
            System.out.println("Vazio");
            return;
        }

        for (Message msg : messageBuffer) {
            System.out.println(msg.getContent());
        }
    }

    private static class DelayedMessage {

        private String ip;
        private int port;
        private Message message;

        public DelayedMessage(String ip, int port, Message message) {
            this.ip = ip;
            this.port = port;
            this.message = message;
        }
    }

    private void iniciarDescoberta() {
        Thread listener = new Thread(() -> {

            try {
                MulticastSocket multicastSocket = new MulticastSocket(MULTICAST_PORT);
                InetAddress group = InetAddress.getByName(MULTICAST_IP);
                multicastSocket.joinGroup(group);

                while (true) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());

                    if (msg.startsWith("DISCOVER:")) {
                        int port = Integer.parseInt(msg.split(":")[1]);

                        if (port != myPort && !jaExiste(port)) {

                            Participant p = new Participant(
                                    participants.size(),
                                    packet.getAddress().getHostAddress(),
                                    port);

                            participants.add(p);

                            System.out.println("\n[INFO] Novo participante conectado: " + port);
                            listarParticipantes();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        listener.setDaemon(true);
        listener.start();

        Thread announcer = new Thread(() -> {
            while (true) {
                try {
                    anunciarPresenca();
                    Thread.sleep(5000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        announcer.setDaemon(true);
        announcer.start();
    }

    private void anunciarPresenca() {
        try {
            MulticastSocket multicastSocket = new MulticastSocket();
            InetAddress group = InetAddress.getByName(MULTICAST_IP);

            String msg = "DISCOVER:" + myPort;

            DatagramPacket packet = new DatagramPacket(
                    msg.getBytes(),
                    msg.length(),
                    group,
                    MULTICAST_PORT);

            multicastSocket.send(packet);
            multicastSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean jaExiste(int port) {
        for (Participant p : participants) {
            if (p.getPort() == port) {
                return true;
            }
        }
        return false;
    }

    private byte[] serialize(Message message) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(message);
        out.flush();
        return bos.toByteArray();
    }

    private Message deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        return (Message) in.readObject();
    }

    public void listarParticipantes() {

        if (participants.isEmpty()) {
            System.out.println("Nenhum participante conectado.");
            return;
        }

        for (Participant p : participants) {
            System.out.println("- " + p.getPort());
        }
    }

    public void sendUDP(String ip, int port, Message message) {
        try {
            byte[] data = serialize(message);

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(ip),
                    port);

            socket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void mcsend(String msg, ICausalMulticast client) {

        vectorClock[getMyIndex()]++;
        int[] timestamp = vectorClock.clone();
        Message message = new Message(msg, myPort, timestamp, matrixClock[getMyIndex()].clone());
        Scanner scanner = new Scanner(System.in);

        for (Participant p : participants) {

            System.out.println("\nEnviar para " + p.getPort() + "? (s/n)");
            String resposta = scanner.nextLine();

            if (resposta.equalsIgnoreCase("s")) {
                sendUDP(p.getIp(), p.getPort(), message);
            } else {
                delayedMessages.add(new DelayedMessage(p.getIp(), p.getPort(), message));
                System.out.println("Mensagem atrasada para " + p.getPort());
            }
        }

        System.out.println("\nRelógio local:");
        System.out.println(Arrays.toString(vectorClock));

        mostrarMatriz();
        historyBuffer.add(message);
        verificarEstabilizacao();
    }

    public void enviarMensagensAtrasadas() {

        if (delayedMessages.isEmpty()) {
            System.out.println("\nNão existem mensagens atrasadas.");
            return;
        }

        List<DelayedMessage> enviadas = new ArrayList<>();

        for (DelayedMessage dm : delayedMessages) {
            sendUDP(dm.ip, dm.port, dm.message);
            System.out.println("Mensagem enviada para " + dm.port);
            enviadas.add(dm);
        }
        delayedMessages.removeAll(enviadas);
    }

    public List<Participant> getParticipants() {
        return participants;
    }
}
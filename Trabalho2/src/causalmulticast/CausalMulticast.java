package CausalMulticast;

import java.net.*;
import java.util.*;
import java.io.*;

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
    private Map<Integer, Integer> vectorClock = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> matrixClock = new HashMap<>();
    private Map<Integer, Long> lastHeartbeat = new HashMap<>();
    private static final long HEARTBEAT_TIMEOUT = 15000;
    private Queue<Message> messageBuffer = new LinkedList<>();
    private List<DelayedMessage> delayedMessages = new ArrayList<>();
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
        this.client = client;
        this.myPort = port;

        vectorClock.put(myPort, 0);
        Map<Integer, Integer> selfRow = new HashMap<>();
        selfRow.put(myPort, 0);
        matrixClock.put(myPort, selfRow);

        try {
            socket = new DatagramSocket(port);
            iniciarRecepcaoUDP();
            iniciarDescoberta();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureKnown(int port) {
        if (!vectorClock.containsKey(port)) {
            vectorClock.put(port, 0);
        }
        if (!matrixClock.containsKey(port)) {
            Map<Integer, Integer> newRow = new HashMap<>();
            for (int p : vectorClock.keySet()) {
                newRow.put(p, 0);
            }
            matrixClock.put(port, newRow);
        }
        for (Map<Integer, Integer> row : matrixClock.values()) {
            row.putIfAbsent(port, 0);
        }
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

                    if (canDeliver(message)) {
                        deliverMessage(message);
                        processBuffer();
                        mostrarEstadoCompleto();
                    } else {
                        messageBuffer.add(message);
                        System.out.println("\nMensagem armazenada no buffer.");
                        mostrarEstadoCompleto();
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
        int senderPort = message.getSenderId();
        Map<Integer, Integer> msgVC = message.getVectorClock();

        // Mensagem sem VC (UDP direto) — entrega imediata sem ordenação causal
        if (msgVC.isEmpty()) return true;

        ensureKnown(senderPort);
        for (int p : msgVC.keySet()) {
            ensureKnown(p);
        }

        if (msgVC.getOrDefault(senderPort, 0) != vectorClock.getOrDefault(senderPort, 0) + 1) {
            return false;
        }

        for (Map.Entry<Integer, Integer> entry : msgVC.entrySet()) {
            int p = entry.getKey();
            if (p == senderPort) continue;
            if (entry.getValue() > vectorClock.getOrDefault(p, 0)) {
                return false;
            }
        }

        return true;
    }

    private void deliverMessage(Message message) {
        int senderPort = message.getSenderId();
        Map<Integer, Integer> msgVC = message.getVectorClock();

        if (!msgVC.isEmpty()) {
            vectorClock.merge(senderPort, 1, Integer::sum);
            matrixClock.put(senderPort, new HashMap<>(msgVC));

            if (senderPort != myPort) {
                matrixClock.get(myPort).merge(senderPort, 1, Integer::sum);
            }
        } else {
            // Mensagem UDP direta (sem VC) — registra apenas recebimento
            ensureKnown(senderPort);
        }

        System.out.println("\n[CAUSAL] Mensagem entregue: " + message.getContent());

        historyBuffer.add(message);
        client.deliver(message.toString());
        verificarEstabilizacao();
    }

    private void verificarEstabilizacao() {
        List<Message> estabilizadas = new ArrayList<>();

        List<Integer> ativos = new ArrayList<>();
        ativos.add(myPort);
        for (Participant p : participants) {
            ativos.add(p.getPort());
        }

        for (Message msg : historyBuffer) {
            int senderPort = msg.getSenderId();
            Map<Integer, Integer> msgVC = msg.getVectorClock();
            int timestamp = msgVC.getOrDefault(senderPort, 0);

            int min = Integer.MAX_VALUE;
            for (int observerPort : ativos) {
                Map<Integer, Integer> row = matrixClock.get(observerPort);
                int val = (row != null) ? row.getOrDefault(senderPort, 0) : 0;
                if (val < min) min = val;
            }

            if (timestamp <= min) {
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

    private void mostrarEstadoCompleto() {
        System.out.println("\n========== ESTADO DO SISTEMA ==========");

        System.out.println("VC local: " + vectorClock);

        System.out.println("\nMatriz de relógios:");
        if (matrixClock.isEmpty()) {
            System.out.println("  (vazia)");
        } else {
            List<Integer> portas = new ArrayList<>(matrixClock.keySet());
            Collections.sort(portas);
            for (int p : portas) {
                System.out.println("  Porta " + p + ": " + matrixClock.get(p));
            }
        }

        System.out.println("\nMensagens recebidas (buffer de ordenação causal):");
        if (messageBuffer.isEmpty()) {
            System.out.println("  (vazio)");
        } else {
            for (Message msg : messageBuffer) {
                System.out.println("  - \"" + msg.getContent() + "\" (remetente " + msg.getSenderId() + ")");
            }
        }

        System.out.println("\nMensagens atrasadas (enviar depois):");
        if (delayedMessages.isEmpty()) {
            System.out.println("  (vazio)");
        } else {
            for (int i = 0; i < delayedMessages.size(); i++) {
                DelayedMessage dm = delayedMessages.get(i);
                System.out.println("  " + i + " -> porta " + dm.port + ": \"" + dm.message.getContent() + "\"");
            }
        }

        System.out.println("========================================");
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

                        if (port != myPort) {
                            lastHeartbeat.put(port, System.currentTimeMillis());

                            if (!jaExiste(port)) {
                                ensureKnown(port);

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

        Thread cleaner = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    long now = System.currentTimeMillis();
                    List<Participant> removidos = new ArrayList<>();

                    for (Participant p : participants) {
                        Long last = lastHeartbeat.get(p.getPort());
                        if (last != null && (now - last) > HEARTBEAT_TIMEOUT) {
                            removidos.add(p);
                        }
                    }

                    for (Participant p : removidos) {
                        participants.remove(p);
                        lastHeartbeat.remove(p.getPort());
                        System.out.println("\n[INFO] Participante timeout - removido: " + p.getPort());
                        listarParticipantes();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        cleaner.setDaemon(true);
        cleaner.start();
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

    public void mcsend(String msg) {
        vectorClock.merge(myPort, 1, Integer::sum);
        Map<Integer, Integer> timestamp = new HashMap<>(vectorClock);
        Message message = new Message(msg, myPort, timestamp, new HashMap<>(matrixClock.get(myPort)));
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

        matrixClock.put(myPort, new HashMap<>(vectorClock));
        System.out.println("\n[CAUSAL] Mensagem entregue: " + message.getContent());
        historyBuffer.add(message);
        this.client.deliver(message.toString());
        verificarEstabilizacao();
        mostrarEstadoCompleto();
    }

    public void enviarMensagensAtrasadas() {

        if (delayedMessages.isEmpty()) {
            System.out.println("\nNao existem mensagens atrasadas.");
            return;
        }

        System.out.println("\n--- Mensagens atrasadas ---");
        for (int i = 0; i < delayedMessages.size(); i++) {
            DelayedMessage dm = delayedMessages.get(i);
            System.out.println("  " + i + " -> porta " + dm.port + ": \"" + dm.message.getContent() + "\"");
        }

        System.out.print("\nDigite o(s) indice(s) para enviar (separados por espaco), ou Enter para sair: ");
        Scanner scanner = new Scanner(System.in);
        String linha = scanner.nextLine().trim();

        if (linha.isEmpty()) return;

        String[] partes = linha.split("\\s+");
        List<Integer> indices = new ArrayList<>();

        for (String p : partes) {
            try {
                int idx = Integer.parseInt(p);
                if (idx >= 0 && idx < delayedMessages.size()) {
                    indices.add(idx);
                } else {
                    System.out.println("Indice invalido: " + idx);
                }
            } catch (NumberFormatException e) {
                System.out.println("Entrada invalida: " + p);
            }
        }

        Collections.sort(indices);
        Collections.reverse(indices);
        for (int idx : indices) {
            DelayedMessage dm = delayedMessages.get(idx);
            sendUDP(dm.ip, dm.port, dm.message);
            System.out.println("Mensagem \"" + dm.message.getContent() + "\" enviada para porta " + dm.port);
            delayedMessages.remove(idx);
        }
    }

    public List<Participant> getParticipants() {
        return participants;
    }
}
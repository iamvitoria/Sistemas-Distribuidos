package CausalMulticast;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

/**
 * Middleware responsável por fornecer comunicação multicast
 * com ordenação causal e estabilização de mensagens.
 *
 * O envio multicast é implementado através de múltiplos envios
 * UDP unicast. O sistema utiliza relógios vetoriais para
 * garantir a ordem causal e matriz de relógios para detectar
 * mensagens estabilizadas.
 *
 */
public class CausalMulticast {

    private DatagramSocket socket;
    private ICausalMulticast client;
    private final Scanner scanner = new Scanner(System.in);
    private final List<Participant> participants = new CopyOnWriteArrayList<>();
    private final String MULTICAST_IP = "230.0.0.1";
    private final int MULTICAST_PORT = 4446;
    private int myPort;
    private Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>();
    private Map<Integer, Map<Integer, Integer>> matrixClock = new ConcurrentHashMap<>();
    private Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_TIMEOUT = 15000;
    private Queue<Message> messageBuffer = new ConcurrentLinkedQueue<>();
    private List<DelayedMessage> delayedMessages = new CopyOnWriteArrayList<>();
    private List<Message> historyBuffer = new CopyOnWriteArrayList<>();

    /**
     * Cria uma instância do middleware CausalMulticast.
     *
     * Inicializa os sockets de comunicação, o serviço de descoberta
     * de participantes e as threads responsáveis pela recepção
     * de mensagens.
     *
     * @param ip endereço IP local do processo
     * @param port porta utilizada pelo processo
     * @param client referência para a aplicação cliente que receberá
     *               callbacks através do método deliver()
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

            Map<Integer, Integer> senderMatrixRow = message.getMatrixRow();
            if (senderMatrixRow != null && !senderMatrixRow.isEmpty()) {
                for (Map.Entry<Integer, Integer> entry : senderMatrixRow.entrySet()) {
                    ensureKnown(entry.getKey());
                    matrixClock.get(senderPort).merge(entry.getKey(), entry.getValue(), Integer::max);
                }
            }

            if (senderPort != myPort) {
                matrixClock.get(myPort).merge(senderPort, 1, Integer::sum);
            }
        } else {
            // Mensagem UDP direta (sem VC) — registra apenas recebimento
            ensureKnown(senderPort);
        }

        System.out.println("\n[CAUSAL] Mensagem entregue: " + message.getContent());

        historyBuffer.add(message);
        client.deliver(message.getContent());
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

            remover.forEach(messageBuffer::remove);

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
            try {
                MulticastSocket multicastSocket = new MulticastSocket();
                InetAddress group = InetAddress.getByName(MULTICAST_IP);
                while (true) {
                    String msg = "DISCOVER:" + myPort;
                    DatagramPacket packet = new DatagramPacket(
                        msg.getBytes(), msg.length(), group, MULTICAST_PORT);
                    multicastSocket.send(packet);
                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                e.printStackTrace();
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

    public void sendDirect(int port, String content) {
        for (Participant p : participants) {
            if (p.getPort() == port) {
                Message message = new Message(content, myPort, new HashMap<>(), new HashMap<>());
                sendUDP(p.getIp(), port, message);
                return;
            }
        }
        System.out.println("Participante " + port + " nao encontrado.");
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

    /**
     * Envia uma mensagem UDP para um participante específico.
     *
     * Este método é utilizado internamente para implementar
     * o multicast através de múltiplos envios unicast.
     *
     * @param ip endereço IP do destinatário
     * @param port porta do destinatário
     * @param message mensagem a ser enviada
     */
    void sendUDP(String ip, int port, Message message) {
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

    /**
     * Realiza o envio multicast de uma mensagem obedecendo
     * a ordenação causal.
     *
     * Antes do envio, o relógio vetorial local é incrementado
     * e anexado à mensagem utilizando a técnica de piggyback.
     *
     * O usuário pode optar por atrasar manualmente o envio
     * para determinados participantes.
     *
     * @param msg conteúdo da mensagem multicast
     */
    public void mcsend(String msg, ICausalMulticast client) {
        vectorClock.merge(myPort, 1, Integer::sum);
        Map<Integer, Integer> timestamp = new HashMap<>(vectorClock);
        Message message = new Message(msg, myPort, timestamp, new HashMap<>(matrixClock.get(myPort)));
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

        matrixClock.get(myPort).merge(myPort, 1, Integer::sum);
        System.out.println("\n[CAUSAL] Mensagem entregue: " + message.getContent());
        historyBuffer.add(message);
        this.client.deliver(message.getContent());
        verificarEstabilizacao();
        mostrarEstadoCompleto();
    }

    /**
     * Permite enviar mensagens que foram previamente
     * atrasadas durante um multicast.
     *
     * O usuário escolhe quais mensagens pendentes deseja
     * liberar para os respectivos destinatários.
     */
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

    /**
     * Retorna a lista atual de participantes conhecidos
     * pelo serviço de descoberta.
     *
     * @return lista de participantes ativos
     */
    public List<Participant> getParticipants() {
        return participants;
    }
}
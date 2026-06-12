package causalmulticast;

import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.*;

public class CausalMulticast {

    private DatagramSocket socket;
    private ICausalMulticast client;
    private final List<Participant> participants = new ArrayList<>();
    private final String MULTICAST_IP = "230.0.0.1";
    private final int MULTICAST_PORT = 4446;
    private int myPort;
    private int[] vectorClock = new int[34];

    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
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

                    client.deliver(message.toString());

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        receiver.setDaemon(true);
        receiver.start();
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

        Message message = new Message(
                msg,
                myPort,
                timestamp);

        for (Participant p : participants) {

            sendUDP(
                    p.getIp(),
                    p.getPort(),
                    message);
        }

        System.out.println("\nRelógio local:");
        System.out.println(Arrays.toString(vectorClock));
    }

    public List<Participant> getParticipants() {
        return participants;
    }
}
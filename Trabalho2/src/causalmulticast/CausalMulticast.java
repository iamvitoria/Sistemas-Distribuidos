package causalmulticast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class CausalMulticast {

    private DatagramSocket socket;
    private ICausalMulticast client;

    public CausalMulticast(
            String ip,
            Integer port,
            ICausalMulticast client) {

        this.client = client;

        try {

            socket = new DatagramSocket(port);

            System.out.println(
                    "Escutando na porta " + port);

            iniciarRecepcao();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarRecepcao() {

        Thread receiver = new Thread(() -> {

            while (true) {

                try {

                    byte[] buffer =
                            new byte[4096];

                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer,
                                    buffer.length);

                    socket.receive(packet);

                    String msg =
                            new String(
                                    packet.getData(),
                                    0,
                                    packet.getLength());

                    client.deliver(msg);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });

        receiver.start();
    }

    public void sendUDP(
            String ip,
            int port,
            String msg) {

        try {

            byte[] dados =
                    msg.getBytes();

            DatagramPacket packet =
                    new DatagramPacket(
                            dados,
                            dados.length,
                            InetAddress.getByName(ip),
                            port);

            socket.send(packet);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void mcsend(
            String msg,
            ICausalMulticast client) {

        // vamos implementar depois
    }
}
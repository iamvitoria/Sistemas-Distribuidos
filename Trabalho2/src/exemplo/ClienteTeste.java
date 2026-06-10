package exemplo;

import causalmulticast.*;

public class ClienteTeste
        implements ICausalMulticast {

    private CausalMulticast cm;

    public ClienteTeste(
            String ip,
            int port) {

        cm = new CausalMulticast(
                ip,
                port,
                this);
    }

    @Override
    public void deliver(String msg) {
        System.out.println(
                "Recebido: " + msg);
    }

    public static void main(String[] args) {

        ClienteTeste c =
                new ClienteTeste(
                        "localhost",
                        5001);
    }
}
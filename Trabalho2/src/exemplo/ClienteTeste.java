package exemplo;

import causalmulticast.*;

import java.util.Scanner;

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
                "\nRecebido: " + msg);
    }

    public void enviar(
            String ip,
            int port,
            String msg) {

        cm.sendUDP(
                ip,
                port,
                msg);
    }

    public static void main(String[] args) {

        Scanner scanner =
                new Scanner(System.in);

        System.out.print(
                "Minha porta: ");

        int minhaPorta =
                Integer.parseInt(
                        scanner.nextLine());

        ClienteTeste cliente =
                new ClienteTeste(
                        "localhost",
                        minhaPorta);

        while (true) {

            System.out.print(
                    "\nDestino: ");

            int destino =
                    Integer.parseInt(
                            scanner.nextLine());

            System.out.print(
                    "Mensagem: ");

            String msg =
                    scanner.nextLine();

            cliente.enviar(
                    "localhost",
                    destino,
                    msg);
        }
    }
}
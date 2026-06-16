package exemplo;

import CausalMulticast.*;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Aplicação de exemplo utilizada para demonstrar
 * o funcionamento do middleware CausalMulticast.
 *
 * Permite testar descoberta de participantes,
 * multicast causal, atraso manual de mensagens
 * e estabilização.
 *
 */
public class ClienteTeste implements ICausalMulticast {

    public CausalMulticast cm;

    public ClienteTeste(String ip, int port) {

        cm = new CausalMulticast(ip, port, this);
    }

    @Override
    public void deliver(String msg) {
        System.out.println("\n[MSG RECEBIDA] " + msg);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Olá! Informe sua porta:");
        int porta = Integer.parseInt(scanner.nextLine());
        ClienteTeste cliente = new ClienteTeste("localhost", porta);

        System.out.println("\nBuscando participantes...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (cliente.cm.getParticipants().isEmpty()) {

            System.out.println("\nAinda não há participantes conectados.");
            System.out.println("Aguarde alguém entrar para enviar mensagens.");

            while (cliente.cm.getParticipants().isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.out.println("\nParticipantes encontrados:");
        cliente.cm.listarParticipantes();

        System.out.println(
                "\nSistema pronto para envio de mensagens.");

        while (true) {

            System.out.println("\nTipo de envio:");
            System.out.println("1 - UDP");
            System.out.println("2 - Multicast");
            System.out.println("3 - Enviar mensagens atrasadas");
            System.out.print("Escolha: ");
            String opcao = scanner.nextLine();

            if (opcao.equals("1")) {
                if (cliente.cm.getParticipants().isEmpty()) {
                    System.out.println("\nNenhum participante conectado.");
                    continue;
                }

                System.out.println("\nParticipantes disponíveis:");

                for (Participant p : cliente.cm.getParticipants()) {
                    System.out.println("- " + p.getPort());
                }

                System.out.print("\nPorta destino: ");
                int portaDestino;

                try {
                    portaDestino = Integer.parseInt(scanner.nextLine());
                } catch (Exception e) {
                    System.out.println("Porta inválida.");
                    continue;
                }

                Participant destino = null;
                for (Participant p : cliente.cm.getParticipants()) {
                    if (p.getPort() == portaDestino) {
                        destino = p;
                        break;
                    }
                }

                if (destino == null) {
                    System.out.println("Participante não encontrado.");
                    continue;
                }

                System.out.print("Mensagem: ");
                String msg = scanner.nextLine();
                Message message = new Message(msg, porta, new HashMap<>(), new HashMap<>());
                cliente.cm.sendUDP(destino.getIp(), destino.getPort(), message);

            } else if (opcao.equals("2")) {

                if (cliente.cm.getParticipants().isEmpty()) {
                    System.out.println("\nNenhum participante conectado.");
                    continue;
                }

                System.out.print("Mensagem: ");
                String msg = scanner.nextLine();
                cliente.cm.mcsend(msg);
            } else if (opcao.equals("3")) {
                cliente.cm.enviarMensagensAtrasadas();
            } else {
                System.out.println("Opção inválida.");
            }
        }
    }
}
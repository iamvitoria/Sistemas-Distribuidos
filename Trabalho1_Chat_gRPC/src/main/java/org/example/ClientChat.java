package org.example;

import elc1018.grpc.chat.protos.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class ClientChat {
    public static void main(String[] args) {
        // 1. configurando a conexão com o servidor
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 51018)
                .usePlaintext()
                .build();

        // 2. criando os stubs (blocking-> chamadas simples, async-> para o stream)
        ChatServiceGrpc.ChatServiceBlockingStub blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        ChatServiceGrpc.ChatServiceStub asyncStub = ChatServiceGrpc.newStub(channel);

        Scanner scanner = new Scanner(System.in);
        System.out.println("=== CLIENTE CHAT gRPC ===");

        boolean logado = false;
        String meuNome = "";
        User usuario = null;

        // 3. RFA01 - Registo em loop para tratar nomes repetidos
        while (!logado) {
            System.out.print("Escolha o seu apelido: ");
            meuNome = scanner.nextLine();

            usuario = User.newBuilder().setUsername(meuNome).build();

            try {
                RegisterResponse res = blockingStub.register(usuario);

                if (res.getSuccess()) {
                    System.out.println("Sistema: Login realizado como [" + res.getUsername() + "]");
                    logado = true; // sai do loop e prossegue para o chat
                } else {
                    System.out.println("Sistema: ERRO - O nome '" + meuNome + "' já está em uso. Tente outro.");
                }
            } catch (Exception e) {
                System.err.println("Sistema: Não foi possível contactar o servidor. Verifique se o ServerChat está rodando.");
                channel.shutdown();
                return; // encerra o programa se o servidor estiver off
            }
        }

        // cria uma cópia fixa do nome para o Java não dar erro dentro da classe anônima
        final String finalMeuNome = meuNome;

        // 4. RFA05 - Abre o stream para receber mensagens
        asyncStub.receiveMessages(usuario, new StreamObserver<ChatMessage>() {
            @Override
            public void onNext(ChatMessage msg) {
                // RFA06 - A ordem é mantida pelo stream
                // Filtramos para não ver as nossas próprias mensagens enviadas
                if (!msg.getFrom().equals(finalMeuNome)) {

                    // RFA04 - Formatação do timestamp para algo legível
                    String horaFormatada = "";
                    if (msg.hasTimestamp()) {
                        horaFormatada = Instant.ofEpochSecond(msg.getTimestamp().getSeconds())
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    }

                    System.out.println("\n" + horaFormatada + " [" + msg.getFrom() + "]: " + msg.getContent());
                    System.out.print("> "); // mantém linha limpa pra digitar próxima mensagem
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("\nSistema: Conexão com o servidor foi perdida.");
                System.exit(1);
            }

            @Override
            public void onCompleted() {
                System.out.println("\nSistema: Conexão encerrada pelo servidor.");
                System.exit(0);
            }
        });

        // delay para garantir que o stream de recepção está pronto
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 5. RFA03 - Loop para envio de mensagens
        System.out.println("Sistema: Pode começar a conversar! (Pressione Enter para enviar)");
        while (true) {
            System.out.print("> ");
            String texto = scanner.nextLine();

            if (!texto.trim().isEmpty()) {
                try {
                    blockingStub.sendMessage(ChatMessage.newBuilder()
                            .setFrom(meuNome) // aqui usamos o meuNome normal sem problemas
                            .setContent(texto)
                            .build());
                } catch (Exception e) {
                    System.err.println("Erro ao enviar mensagem. O servidor pode ter caído.");
                    break; // sai do loop e finaliza o cliente se não conseguir enviar
                }
            }
        }

        channel.shutdown();
    }
}
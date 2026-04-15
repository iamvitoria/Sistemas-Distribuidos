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
        // 1. Configura a conexão com o servidor
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();

        // 2. Cria os Stubs (Blocking para chamadas simples, Async para o Stream)
        ChatServiceGrpc.ChatServiceBlockingStub blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        ChatServiceGrpc.ChatServiceStub asyncStub = ChatServiceGrpc.newStub(channel);

        Scanner scanner = new Scanner(System.in);
        System.out.println("=== CLIENTE CHAT gRPC ===");
        System.out.print("Escolha o seu apelido: ");
        String meuNome = scanner.nextLine();

        // 3. RFA01: Tenta realizar o registo
        User usuario = User.newBuilder().setUsername(meuNome).build();
        try {
            RegisterResponse res = blockingStub.register(usuario);

            if (res.getSuccess()) {
                System.out.println("Sistema: Login realizado como [" + res.getUsername() + "]");

                // 4. RFA05: Abre o canal (Stream) para receber mensagens
                asyncStub.receiveMessages(usuario, new StreamObserver<ChatMessage>() {
                    @Override
                    public void onNext(ChatMessage msg) {
                        // RFA06: A ordem é mantida pelo stream.
                        // Filtramos para não ver as nossas próprias mensagens enviadas
                        if (!msg.getFrom().equals(meuNome)) {

                            // Formata o Timestamp (RFA04) para algo legível
                            String horaFormatada = "";
                            if (msg.hasTimestamp()) {
                                horaFormatada = Instant.ofEpochSecond(msg.getTimestamp().getSeconds())
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            }

                            System.out.println("\n" + horaFormatada + " [" + msg.getFrom() + "]: " + msg.getContent());
                            System.out.print("> "); // Mantém o cursor de digitação limpo
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.err.println("Sistema: Erro na ligação ao servidor.");
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println("Sistema: Conexão encerrada pelo servidor.");
                    }
                });

                // Pequeno delay para garantir que o stream de recepção está pronto
                Thread.sleep(500);

                // 5. RFA03: Loop para envio de mensagens
                System.out.println("Sistema: Pode começar a conversar! (Pressione Enter para enviar)");
                while (true) {
                    System.out.print("> ");
                    String texto = scanner.nextLine();

                    if (!texto.trim().isEmpty()) {
                        blockingStub.sendMessage(ChatMessage.newBuilder()
                                .setFrom(meuNome)
                                .setContent(texto)
                                // O timestamp é gerado no servidor conforme a lógica gRPC comum,
                                // mas se quiseres podes preencher aqui também.
                                .build());
                    }
                }

            } else {
                System.out.println("Sistema: ERRO - O nome '" + meuNome + "' já está em uso.");
                channel.shutdown();
            }

        } catch (Exception e) {
            System.err.println("Sistema: Não foi possível contactar o servidor. Verifique se o ServerChat está a correr.");
        }
    }
}
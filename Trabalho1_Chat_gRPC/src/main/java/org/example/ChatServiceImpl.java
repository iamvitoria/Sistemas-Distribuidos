package org.example;

import com.google.protobuf.Timestamp;
import elc1018.grpc.chat.protos.*;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {

    // Hash map seguro para threads que guarda o nome do usuário e o seu "tubo" de recebimento de mensagens
    private static final ConcurrentHashMap<String, StreamObserver<ChatMessage>> clients = new ConcurrentHashMap<>();

    @Override
    public void register(User request, StreamObserver<RegisterResponse> responseObserver) {
        String nome = request.getUsername();

        // Verifica se o nome já está na lista
        if (clients.containsKey(nome)) {
            // Rejeita o registro
            responseObserver.onNext(RegisterResponse.newBuilder().setSuccess(false).build());
            responseObserver.onCompleted();
        } else {
            // Aceita o registro (a adição na lista acontece no receiveMessages)
            responseObserver.onNext(RegisterResponse.newBuilder()
                    .setSuccess(true)
                    .setUsername(nome)
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void receiveMessages(User request, StreamObserver<ChatMessage> responseObserver) {
        String nome = request.getUsername();

        // Adiciona o usuário à lista ativa de transmissões
        clients.put(nome, responseObserver);

        // Notifica todo mundo que ele entrou
        broadcastMessage("Sistema", "O usuário [" + nome + "] entrou na sala.");

        // Monitora o contexto da conexão. Se o cliente fechar o terminal, isso dispara automaticamente.
        Context.current().addListener(context -> {
            if (context.isCancelled()) {
                clients.remove(nome);
                broadcastMessage("Sistema", "O usuário [" + nome + "] saiu da sala.");
            }
        }, Runnable::run);
    }

    @Override
    public void sendMessage(ChatMessage request, StreamObserver<Ack> responseObserver) {
        // 1. Pega a mensagem recebida e envia para todos
        broadcastMessage(request.getFrom(), request.getContent());

        // 2. Cria a mensagem de confirmação (Ack) dizendo que deu tudo certo
        Ack resposta = Ack.newBuilder()
                .setSuccess(true)
                .build();

        // 3. Retorna a resposta para o cliente que enviou
        responseObserver.onNext(resposta);
        responseObserver.onCompleted();
    }

    // Método auxiliar para construir e enviar a mensagem a todos
    private void broadcastMessage(String remetente, String conteudo) {
        Instant now = Instant.now();
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        ChatMessage msg = ChatMessage.newBuilder()
                .setFrom(remetente)
                .setContent(conteudo)
                .setTimestamp(ts)
                .build();

        // Itera sobre todos os clientes logados e manda a mensagem
        for (StreamObserver<ChatMessage> client : clients.values()) {
            try {
                client.onNext(msg);
            } catch (Exception e) {
                // Ignora exceções se tentar enviar para um cliente que caiu bruscamente
            }
        }
    }
}
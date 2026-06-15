package CausalMulticast;
/**
 * Interface obrigatória para os clientes do middleware.
 * Define o método de callback utilizado para entregar mensagens
 * que já satisfizeram a ordem causal.
 */
public interface ICausalMulticast {

    /**
     * Entrega a mensagem processada e ordenada para a aplicação do usuário.
     * * @param msg O conteúdo da mensagem estabilizada e pronta para leitura.
     */
    void deliver(String msg);
}
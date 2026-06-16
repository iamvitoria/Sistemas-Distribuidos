package CausalMulticast;
/**
 * Interface utilizada pela aplicação cliente para
 * receber mensagens entregues pelo middleware.
 */
public interface ICausalMulticast {
    /**
     * Callback executado quando uma mensagem pode ser
     * entregue à aplicação respeitando a ordem causal.
     *
     * @param msg mensagem entregue pelo middleware
     */
    void deliver(String msg);
}
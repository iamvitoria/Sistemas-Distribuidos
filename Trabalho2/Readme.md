1. Arquitetura do Middleware
   O middleware CausalMulticast atua como uma camada intermediária entre a aplicação do usuário e a rede. A arquitetura foi desenvolvida utilizando comunicação assíncrona orientada a eventos. O sistema utiliza sockets UDP não confiáveis (DatagramSocket) para simular o envio multicast através de múltiplos envios unicast iterativos.

Para a detecção de membros no grupo de comunicação, foi implementado um Serviço de Descoberta dinâmico. Ele utiliza um MulticastSocket operando no IP 230.0.0.1 (porta 4446). Ao instanciar o middleware, duas Daemon Threads são iniciadas:

Announcer: Envia periodicamente (a cada 5 segundos) a porta do processo local para o grupo multicast avisando que está vivo.

Listener: Escuta o canal multicast aguardando mensagens de "DISCOVER". Ao detectar uma nova porta, adiciona o novo participante à lista de membros ativos, permitindo a entrada dinâmica de usuários na topologia.

2. Algoritmo de Ordenação Causal
   A garantia de que as mensagens não serão entregues à aplicação fora de ordem baseia-se na implementação de Relógios Vetoriais (Vector Clocks).
   Sempre que um processo deseja enviar uma mensagem (método mcsend), ele incrementa o seu próprio índice no relógio vetorial e anexa esse vetor à mensagem (técnica de piggyback).

Quando um processo recebe uma mensagem, ele avalia as dependências causais comparando o relógio da mensagem com o seu relógio local:

Verifica se a mensagem é exatamente a próxima esperada do remetente (msg.VC[remetente] == local[remetente] + 1).

Verifica se o remetente não viu nenhuma outra mensagem que o processo atual ainda não viu (msg.VC[k] <= local[k] para todos os outros índices).

Se a condição for satisfeita, a mensagem é entregue via deliver(). Caso contrário, a mensagem é retida no messageBuffer (buffer de mensagens pendentes) até que as mensagens que a antecedem causalmente sejam recebidas. Toda vez que uma mensagem é entregue, o sistema reprocessa o buffer para verificar se alguma mensagem retida foi liberada.

3. Algoritmo de Estabilização de Mensagens
   Para evitar o consumo infinito de memória pelas mensagens entregues, implementou-se o algoritmo de estabilização através de uma Matriz de Relógios (Matrix Clocks). A matriz atua como uma "visão das visões", onde a linha i representa o relógio vetorial conhecido do processo i.

O funcionamento ocorre da seguinte forma:

Após a mensagem ser causalmente ordenada e entregue, ela é movida para um buffer de histórico (historyBuffer).

A matriz local é atualizada com a visão do remetente (substituindo a linha correspondente pelo vetor recebido no piggyback).

Para verificar se uma mensagem já estabilizou (ou seja, se todos os participantes já a receberam), o sistema busca o valor mínimo na coluna correspondente ao remetente original da mensagem.

Se o timestamp (relógio) original da mensagem for menor ou igual a esse valor mínimo da coluna, significa que todos os membros já têm conhecimento dessa mensagem. Neste momento, a mensagem é classificada como estabilizada e é descartada (eliminada do buffer).

4. Instruções de Execução (Como testar)
   Pré-requisitos: Java Development Kit (JDK) instalado.

Abra um terminal e compile todos os arquivos .java do projeto.

Abra 3 terminais distintos para simular a execução de 3 processos diferentes na mesma máquina.

Em cada terminal, execute a classe ClienteTeste.

Quando solicitado no console, insira portas diferentes para cada processo (ex: Terminal 1 digita 5001, Terminal 2 digita 5002 e Terminal 3 digita 5003).

Aguarde 5 segundos para que o Serviço de Descoberta sincronize todos os terminais.

Utilize o menu iterativo para testar o envio de mensagens (Opção 2 - Multicast). Para fins de demonstração, o sistema perguntará individualmente se deseja enviar ou atrasar a mensagem para cada participante, validando a retenção no buffer e posterior entrega ordenada/estabilização.
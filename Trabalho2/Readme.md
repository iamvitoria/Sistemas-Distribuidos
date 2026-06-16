# CausalMulticast

## Arquitetura do Middleware

O middleware CausalMulticast atua como uma camada intermediária entre a aplicação do usuário e a rede. A arquitetura foi desenvolvida utilizando comunicação assíncrona orientada a eventos.

O sistema utiliza sockets UDP não confiáveis (`DatagramSocket`) para simular multicast através de múltiplos envios unicast.

### Componentes principais

#### Serviço de Descoberta

Utiliza um `MulticastSocket` no grupo `230.0.0.1:4446`.

Três threads são executadas:

* **Announcer:** envia periodicamente mensagens `DISCOVER` a cada 5s (reusa o mesmo socket).
* **Listener:** recebe mensagens `DISCOVER` e registra/atualiza participantes.
* **Cleaner:** remove participantes que não anunciaram presença por 15s (timeout).

#### Comunicação

O envio multicast é realizado por múltiplos envios UDP individuais para todos os participantes conhecidos.

#### Aplicação Cliente

A aplicação implementa a interface `ICausalMulticast`, recebendo o conteúdo da mensagem através do callback:

```java
public void deliver(String msg)
```

---

## Algoritmo de Ordenação Causal

A ordenação causal é baseada em Relógios Vetoriais.

### Envio

Ao executar:

```java
mcsend(String msg, ICausalMulticast client)
```

o processo:

1. Incrementa seu relógio vetorial local (`VC[i]++`).
2. Copia o vetor e a linha da matriz (`MC[i]`).
3. Anexa ambos à mensagem (piggyback).
4. Envia a mensagem aos participantes selecionados (ou atrasa).
5. Entrega a mensagem para si mesmo via `deliver()`, atualizando `MC[i][i]++`.

### Recepção

Ao receber uma mensagem, são verificadas duas condições:

### Condição 1

A mensagem deve ser a próxima esperada do remetente.

[
VC_m[sender] = VC_{local}[sender] + 1
]

### Condição 2

Nenhuma dependência causal pode estar ausente.

[
VC_m[k] \le VC_{local}[k]
]

para todo (k \neq sender).

### Buffer de Mensagens Pendentes

Caso alguma condição não seja satisfeita:

* a mensagem é armazenada em `messageBuffer`;
* ela não é entregue à aplicação;
* o buffer é reprocessado após cada nova entrega.

### Entrega

Quando as dependências são satisfeitas:

* a mensagem é entregue através de `deliver(message.getContent())`;
* o relógio vetorial local é atualizado (`VC[sender]++`);
* a matriz local é atualizada com o VC recebido e a `matrixRow` (propagação de conhecimento);
* o buffer de mensagens pendentes é reavaliado;
* o estado completo (VC, matriz, buffers) é exibido automaticamente no terminal.

---

## Algoritmo de Estabilização

O descarte de mensagens utiliza Matrix Clocks.

### Estrutura

A matriz de relógios (`Map<Integer, Map<Integer, Integer>>`) mantém o conhecimento que cada processo possui sobre os demais, indexada dinamicamente por porta (sem índices fixos).

Cada linha representa um processo:

```
M[porta_observador][porta_alvo]
```

Cada coluna representa o conhecimento sobre outro processo.

### Funcionamento

Após a entrega causal:

1. A mensagem é armazenada em `historyBuffer`.
2. A matriz local é atualizada.
3. O sistema procura o menor valor da coluna referente ao remetente.

### Critério de Estabilização

Uma mensagem é considerada estabilizada quando:

[
timestamp \le min(coluna)
]

Isso significa que todos os participantes já possuem conhecimento daquela mensagem.

### Descarte

Quando estabilizada:

* a mensagem é removida do `historyBuffer`;
* o evento é exibido no terminal.

Exemplo:

```text
[ESTABILIZAÇÃO] 1 mensagens foram estabilizadas e descartadas
 -> Descartada: A
```

---

## Funcionalidades Implementadas

### Middleware

* Descoberta automática de participantes com heartbeat
* Comunicação multicast via UDP unicast
* Entrada e saída dinâmica de participantes (timeout de 15s)
* Estruturas thread-safe (`ConcurrentHashMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedQueue`)
* Entrega causal para si mesmo (self-delivery)
* Atraso manual de mensagens por destinatário
* Reenvio seletivo de mensagens atrasadas (escolha por índice) 

### Ordenação Causal

* Relógio vetorial dinâmico (porta → valor, sem índice fixo)
* Piggyback do relógio e da linha da matriz
* Buffer de mensagens pendentes com reprocessamento automático
* Verificação das dependências causais (variante BSS)
* Entrega causal com exibição automática do estado completo

### Estabilização

* Matrix Clock dinâmico (porta → mapa)
* Propagação da `matrixRow` entre processos
* Histórico de mensagens entregues
* Detecção de mensagens estabilizadas por consenso da matriz
* Descarte automático com notificação no terminal

---

## Instruções de Execução

### Compilação

```bash
javac -d out -sourcepath src src/exemplo/ClienteTeste.java
```

### Execução

Abrir três terminais:

```bash
java exemplo.ClienteTeste
```

Portas sugeridas:

| Processo | Porta |
| -------- | ----- |
| P1       | 5001  |
| P2       | 5002  |
| P3       | 5003  |

Aguardar aproximadamente 5 segundos para descoberta dos participantes.

### Teste da Ordenação Causal

1. P1 envia mensagem "A" (opção 2 - Multicast).
2. Atrasar envio para P3 (responder `n` para P3).
3. P2 recebe "A".
4. P2 envia mensagem "B" (opção 2 - Multicast).
5. Enviar "B" para P3 (responder `s` para P3).
6. P3 receberá "B" antes de "A".
7. A mensagem "B" ficará armazenada no buffer de ordenação causal.
8. Utilizar a opção 3 - Enviar mensagens atrasadas.
9. Escolher o índice da mensagem "A" para P3 (ex: `0`).
10. O middleware entregará primeiro "A" e depois liberará "B" automaticamente.

### Teste da Estabilização

1. Enviar mensagens normalmente entre os participantes.
2. Observar a atualização da matriz de relógios.
3. Verificar o surgimento da mensagem:

```text
[ESTABILIZAÇÃO] X mensagens foram estabilizadas e descartadas
```

4. Confirmar que as mensagens foram removidas do histórico.

---

## Nota sobre o algoritmo de ordenação causal

Foi implementada a variante **BSS (Birman-Schiper-Stephenson)**, onde a condição de entrega verifica separadamente `msg.VC[sender] == VC[sender] + 1` e `msg.VC[k] <= VC[k]` para `k ≠ sender`. Isso difere sutilmente do pseudocódigo da Figura 1, mas garante equivalentemente a causalidade.

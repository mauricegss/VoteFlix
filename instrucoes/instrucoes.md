# Como Executar o VoteFlix no IntelliJ IDEA

Este guia explica como configurar e executar os módulos do servidor e do cliente do VoteFlix diretamente de dentro do IntelliJ IDEA, utilizando as configurações de execução do Maven.

## Pré-requisitos

* O projeto Maven `voteflix-parent` já foi aberto e importado corretamente no IntelliJ IDEA.
* Você tem um JDK (Java Development Kit) versão 21 ou superior configurado na sua IDE.

## 1. Configurando e Executando o Servidor (`voteflix-server`)

Você precisa criar uma configuração de execução específica para o módulo do servidor.

1.  No canto superior direito do IntelliJ IDEA, clique no menu de configurações de execução (geralmente exibe "Current File" ou o nome de uma configuração anterior) e selecione **"Edit Run/Debug Configurations..."**.
2.  Na janela que abrir, clique no ícone de **+** (Add New Configuration) no canto superior esquerdo e selecione **"Maven"**.
3.  Configure os seguintes campos:
    * **Name**: Dê um nome claro, como `VoteFlix Server`.
    * **Working directory**: Clique no ícone de pasta e selecione o diretório do módulo do servidor: `.../VoteFlix-ae85ae49cf734ef4b92ee3b44b4870c012f520b8/voteflix-server`.
    * **Run**: No campo de texto, digite o comando do Maven: `javafx:run`.
4.  Clique em **Apply** e depois em **OK**.
5.  Agora, com a configuração `VoteFlix Server` selecionada no menu superior, clique no botão de **Run** (ícone de play verde) para iniciar o servidor. O painel do servidor deverá aparecer.

## 2. Configurando e Executando o Cliente (`voteflix-client`)

Com o servidor rodando, crie uma segunda configuração para o cliente.

1.  Volte para **"Edit Run/Debug Configurations..."**.
2.  Clique no **+** novamente e adicione outra configuração **"Maven"**.
3.  Configure os campos para o cliente:
    * **Name**: Dê um nome claro, como `VoteFlix Client`.
    * **Working directory**: Selecione o diretório do módulo do cliente: `.../VoteFlix-ae85ae49cf734ef4b92ee3b44b4870c012f520b8/voteflix-client`.
    * **Run**: Digite o mesmo comando: `javafx:run`.
4.  Clique em **Apply** e depois em **OK**.
5.  Selecione a nova configuração `VoteFlix Client` no menu e clique no botão de **Run**. A janela de conexão do cliente será iniciada.

Agora você pode executar o servidor e múltiplos clientes, cada um a partir de sua própria configuração dentro da IDE.
package TCP_Bloqueante;
import java.io.*;
import java.net.*;

public class TCP_Server {

    public static void main(String args[]) throws IOException {
        //Rotina para entrada de dados via teclado
        //DataInputStream teclado = new DataInputStream(System.in);

        //System.out.println("Servidor carregado na porta especificada");
        ServerSocket echoServer = null;     // cria o socket do servidor
        String line;                        // string para conter informações transferidas
        String verificacao;                 // string psrs encerramento do servidor
        DataInputStream is;                 // cria um duto de entrada
        PrintStream os;                     // cria um duto de saída
        Socket clientSocket = null;         // cria o socket do cliente

        System.out.println("Qual porta o servidor deve usar? ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int porta = Integer.parseInt(br.readLine());
        
        System.out.println("Servidor carregado na porta " + porta);

        try {
            echoServer = new ServerSocket(porta);  // *** socket() + bind()  // instancia o socket do servidor na porta especificada. 
        } catch (IOException e) {
            System.out.println(e);
        }
        while (true) {
            try {
                System.out.println("Aguardando conexao");
                clientSocket = echoServer.accept();        // *** listen() + accept() // aguarda conexão do cliente
                is = new DataInputStream(clientSocket.getInputStream());    // aponta o duto de entrada para o socket do cliente
                os = new PrintStream(clientSocket.getOutputStream());       // aponta o duto de saída para o socket do cliente
                os.println("Servidor responde: Conexao efetuada com o servidor");
                while (true) {
                    line = is.readLine(); // *** recv()  // recebe dados do cliente
                    System.out.println("Cliente enviou: " + line);
                    os.println(line.toUpperCase());  //*** send()   // envia dados para o cliente
                    System.out.println("Foi enviado para cliente: " + line.toUpperCase());
                    if (line.toUpperCase().equals("BYE")) // recebendo 'BYE' possibilita o encerramento do servidor
                        break;
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    } // main
} // classe

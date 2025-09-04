package TCP_Bloqueante;
import java.io.*;
import java.net.*;

public class TCP_Client {

    static DataInputStream in;                  // cria um duto de entrada
    static PrintStream out;                     // cria um duto de saída

    public static void main(String[] args) throws IOException {

        //Rotina para entrada de dados via teclado
        DataInputStream teclado = new DataInputStream(System.in);

        //Geração do socket
        Socket ClientSocket = null;

        try {
            /* cria o socket do cliente para conexao com o servidor
           que esta na maquina 127.0.0.1 operando na porta determinada */
            System.out.println("Qual o IP do servidor? ");
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String serverIP = br.readLine();

            System.out.println("Qual a Porta do servidor? ");
            br = new BufferedReader(new InputStreamReader(System.in));
            int serverPort = Integer.parseInt(br.readLine());

            System.out.println("Tentando conectar com host " + serverIP + " na porta " + serverPort);

            ClientSocket = new Socket(serverIP, serverPort);
            /* associa um buffer de entrada e outro de saida ao socket */
            in = new DataInputStream(ClientSocket.getInputStream());    // aponta o duto de entrada para o socket do cliente
            out = new PrintStream(ClientSocket.getOutputStream());       // aponta o duto de saída para o socket do cliente

            //aguarda uma digitação pelo teclado para enviar ao servidor
            System.out.println(in.readLine());
            System.out.println("Conectado. Digite (\"bye\" para sair)");
            while (true) {
                System.out.print("Digite: ");
                String enviar = teclado.readLine();
                out.println(enviar);
                if (enviar.toUpperCase().equals("BYE")) {
                    ClientSocket.close();
                    break;
                }
                String receber = in.readLine();
                System.out.println("Servidor retornou: " + receber);
            }
        } catch (UnknownHostException e) {
            System.err.println("Host desconhecido: ");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IP ou Porta não existe ");
            System.exit(1);
        } catch (Exception e) {
            System.out.println("Falha na conexão com o servidor");
        }

    }
}

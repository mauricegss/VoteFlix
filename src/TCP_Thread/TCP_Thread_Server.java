package TCP_Thread;

import java.net.*;
import java.io.*;

/**
 *
 * @author richard
 */
public class TCP_Thread_Server extends Thread {

   protected Socket clientSocket;

   public static void main(String[] args) throws IOException {
      ServerSocket serverSocket = null;

      System.out.println("Qual porta o servidor deve usar? ");
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      int porta = Integer.parseInt(br.readLine());

      System.out.println("Servidor carregado na porta " + porta);
      System.out.println("Aguardando conexao....\n ");

      try {
         serverSocket = new ServerSocket(porta);  // instancia o socket do servidor na porta especificada
         System.out.println("Criado Socket de Conexao.\n");
         try {
            while (true) {
               new TCP_Thread_Server(serverSocket.accept());
               System.out.println("Accept ativado. Esperando por uma conexao...\n");
            }
         } catch (IOException e) {
            System.err.println("Accept falhou!");
            System.exit(1);
         }
      } catch (IOException e) {
         System.err.println("Nao foi possivel ouvir a porta " + porta);
         System.exit(1);
      } finally {
         try {
            serverSocket.close();
         } catch (IOException e) {
            System.err.println("Nao foi possivel fechar a porta " + porta);
            System.exit(1);
         }
      }
   }

   // Constructor
   private TCP_Thread_Server(Socket clientSoc) {
      clientSocket = clientSoc;
      start();
   }

   /**
    * Override java.language.Thread
    */
   @Override
   public void run() {
      System.out.println("Nova thread de comunicacao iniciada.\n");

      try {
         PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

         String inputLine;

         while ((inputLine = in.readLine()) != null) {
            System.out.println("Servidor recebeu: " + inputLine);
            System.out.println("Servidor enviou: " + inputLine.toUpperCase());
            out.println(inputLine.toUpperCase());

            if (inputLine.toUpperCase().equals("BYE"))
               break;
         }

         out.close();
         in.close();
         clientSocket.close();
      } catch (IOException e) {
         System.err.println("Problema com Servidor de Communicacao!");
         System.exit(1);
      }
   }
}

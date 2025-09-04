package UDP;

/**
 *
 * @author richard
 */
import java.io.*;
import java.net.*;

class UDPServer {

   public static void main(String args[]) throws Exception {
      try {
         System.out.println("Qual porta o servidor deve usar? ");
         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         int porta = Integer.parseInt(br.readLine());

         System.out.println("Servidor carregado na porta " + porta);
         System.out.println("Aguardando comunicação....");

         DatagramSocket serverSocket = new DatagramSocket(porta);

         byte[] receiveData = new byte[1024];
         byte[] sendData = new byte[1024];

         while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            System.out.println("Esperando por pacote datagrama");

            serverSocket.receive(receivePacket);

            String sentence = new String(receivePacket.getData());

            InetAddress IPAddress = receivePacket.getAddress();

            int port = receivePacket.getPort();

            System.out.println("Recebido de " + IPAddress + ":" + port + " a mensagem: " + sentence);
            System.out.println("Enviado de volta: " + sentence.toUpperCase());

            String capitalizedSentence = sentence.toUpperCase();

            sendData = capitalizedSentence.getBytes();

            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);

            serverSocket.send(sendPacket);
         }

      } catch (IOException e) {
         System.out.println("Erro de execeção: " + e.getMessage());
         System.exit(1);
      }

   }
}

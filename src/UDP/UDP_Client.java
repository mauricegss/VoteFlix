package UDP;

/**
 *
 * @author richard
 */
import java.io.*;
import java.net.*;

class UDPClient {

   public static void main(String args[]) throws Exception {
      try {
         System.out.println("Qual o IP do servidor? ");
         BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
         String serverIP = inFromUser.readLine();
         
         InetAddress IPAddress = InetAddress.getByName(serverIP);

         System.out.println("Qual a Porta do servidor? ");
         inFromUser = new BufferedReader(new InputStreamReader(System.in));
         int serverPort = Integer.parseInt(inFromUser.readLine());

         System.out.println("Tentando conectar com host " + IPAddress + " via UDP na porta " + serverPort);

         DatagramSocket clientSocket = new DatagramSocket();

         byte[] sendData = new byte[1024];
         byte[] receiveData = new byte[1024];

         while (true) {
            System.out.print("Digite a mensagem: ");
            String sentence = inFromUser.readLine();

            if (sentence.equals("fim")) {
               clientSocket.close();
               break;
            }

            sendData = sentence.getBytes();

            System.out.println("Mandando " + sendData.length + " bytes de dados para o servidor.");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);

            clientSocket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            System.out.println("Esperando por pacote de retorno");
            clientSocket.setSoTimeout(10000);

            try {

               clientSocket.receive(receivePacket);
               String modifiedSentence = new String(receivePacket.getData());

               InetAddress returnIPAddress = receivePacket.getAddress();

               int port = receivePacket.getPort();

               //System.out.println(InetAddress.getLocalHost().getHostName());
               System.out.println("Recebido do servidor " + returnIPAddress + ":" + port + " a mensagem: " + modifiedSentence);
               
            } catch (SocketTimeoutException ste) {
               System.out.println("Timeout ocorreu: pacote considerado como perdido");
            }
         }
      } catch (UnknownHostException ex) {
         System.err.println(ex);
      } catch (IOException ex) {
         System.err.println(ex);
      }

   }
}

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

public class Client_messages extends Thread{
    SocketChannel sc_client;
    ClientUI user_interface;

    public Client_messages(SocketChannel socket_channel_param, ClientUI user_interface_param) {         // Ce thread prend en paramètre le socketChannel entre le client et le serveur
        this.sc_client = socket_channel_param;                                                          // et l'interface du client pour gérer l'affichage des msg en arrière plan
        this.user_interface = user_interface_param;
    }
    
    public void run() {
        try {                                           // Petit délai pour que le client se connecte bien au serveur
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        try {
            while (user_interface.isRunning()) {                // Tant que l'interface client tourne
                byteBuffer.clear();
                int bytesRead = sc_client.read(byteBuffer);

                if (bytesRead == -1) {                          // La connexion a été fermée
                    System.out.println("break");
                    break;
                }
                byteBuffer.flip();

                byte[] donnees = new byte[byteBuffer.remaining()];
                byteBuffer.get(donnees);
                String message_client = new String(donnees, StandardCharsets.UTF_8);

                if (!message_client.isEmpty()) {                        // N'affiche le msg que lorsque le socketChannel contient bien du contenu
                    user_interface.appendMessage(message_client);       // Affiche sur l'interface du client
                }

                byteBuffer.clear();                             // 'Reset' le ByteBuffer pour la prochaine utilisation
            }
        
        } catch (NotYetConnectedException | ConnectException nyce) {
            nyce.printStackTrace();
            System.out.println("Le serveur n'est pas lancé !");
            user_interface.appendMessage("Le serveur n'est pas lancé !\n");
    
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.out.println("Client deconnecté");
            user_interface.appendMessage("Une erreur est survenue...\n");

        } 
    }
}

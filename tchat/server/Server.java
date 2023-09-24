import javafx.application.Platform;

import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.net.BindException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Processus serveur qui ecoute les connexion entrantes,
 * les messages entrant et les rediffuse au clients connectes
 *
 * @author mathieu.fabre
 */
public class Server extends Thread implements ITchat {
	ServerSocketChannel serveur = null;
    Selector selecteur;

    ServerUI serverUI;
    
    int port = 1234;

    DateTimeFormatter format_date = DateTimeFormatter.ofPattern("dd/MM/yy");
    DateTimeFormatter format_heure = DateTimeFormatter.ofPattern("HH:mm");

    public Server (ServerUI user_interface_param) {                 // Server prend en paramètre l'interface serveur pour afficher les logs
        this.serverUI = user_interface_param;
    }

    public void run() {
        FileOutputStream fos = null;
       
        try {
            File log = new File("server/log.txt");          // Fichier log.txt présent dans le répertoire contenant les msg du serveur
            if (! log.exists()) {
                log.createNewFile();
            }
            fos = new FileOutputStream(log, true);                      // FileOutputStream pour écrire dans log.txt

            serveur = ServerSocketChannel.open();                               // Initialisation du ServerSocketChannel
            serveur.configureBlocking(false);
            serveur.bind(new InetSocketAddress(InetAddress.getLocalHost(), port));

            selecteur = Selector.open();                                        // Initialisation du selecteur
            serveur.register(selecteur, SelectionKey.OP_ACCEPT);                // Enregistrement du ServerSocketChannel sur le selecteur

            String msg_lancement_serv = "Server started !\n";
            sendLogToUI(msg_lancement_serv);                                    // Affichage d'un message sur l'interface serveur

            fos.write((string_date_heure_actuelle() + msg_lancement_serv).getBytes());      // Ecriture dans le log.txt

            while (serverUI.isRunning()) {                                                                  // Tant que l'interface tourne
                selecteur.select();
                Set<SelectionKey> cles_presentes = selecteur.selectedKeys();                                // Récupération des clés
                Iterator<SelectionKey> parcours_cle = cles_presentes.iterator();                            // Création d'un Iterator pour parcourir le Set de clés
                
                while (parcours_cle.hasNext()) {                                                            // Parcours des clés du selecteur
                    SelectionKey cle = parcours_cle.next();
                    
                    if (cle.isAcceptable() && cle.isValid()) {                                              // On peut accepter une nouvelle connexion
                        SocketChannel sc_client = serveur.accept();
                        sc_client.configureBlocking(false);
                        sc_client.register(selecteur, SelectionKey.OP_READ | SelectionKey.OP_WRITE);        // Enregistrement du SocketChannel sur le selecteur

                    } else if (cle.isReadable() && cle.isValid()) {                                         // On peut lire les données du channel
                        try {
                            SocketChannel socket_channel = (SocketChannel) cle.channel();                   // On récupère le socketChannel à travers la clé
                            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                            int bytes_lus = socket_channel.read(byteBuffer);

                            if (bytes_lus == -1) {                                                          // La connexion a été fermée par le client
                                socket_channel.close();                                                     // Fermeture du socketChannel
                                cle.cancel();                                                               // Suppression de la clé du selecteur

                            } else if (bytes_lus > 0) {                                                     // Traitement des données lues
                                byteBuffer.flip();                                  

                                byte[] donnees = new byte[byteBuffer.remaining()];
                                byteBuffer.get(donnees);
                                String message_client = new String(donnees, StandardCharsets.UTF_8);

                                sendLogToUI(string_date_heure_actuelle() + message_client);            // On envoie le message à l'interface serveur
                                
                                fos.write((string_date_heure_actuelle()).getBytes());
                                fos.write(donnees);                                                                 // Ecriture du msg dans log.txt

                                byteBuffer.rewind();                                    // Remettre le buffer à la position 0 pour une réutilisation

                                // Partie broadcast
                                selecteur.select();
                                Set<SelectionKey> cles_presentes_broadcast = selecteur.selectedKeys();
                                Iterator<SelectionKey> parcours_cle2 = cles_presentes_broadcast.iterator();

                                while (parcours_cle2.hasNext()) {                                                   // Parcours des clés du selecteur
                                    SelectionKey cle_broadcast = parcours_cle2.next();

                                    if (cle_broadcast.isValid()) {                                                  // Pour toutes les clés valides, va envoyer le msg au socketChannel associé                                     
                                        SocketChannel sc_client = (SocketChannel) cle_broadcast.channel();
                                
                                        try {
                                            ByteBuffer byteBuffer_date_heure = ByteBuffer.wrap(string_date_heure_actuelle().getBytes());

                                            ByteBuffer byteBuffer_complet = ByteBuffer.allocate(byteBuffer.remaining() + byteBuffer_date_heure.remaining());        
                                            byteBuffer_complet.put(byteBuffer_date_heure);                                  // Concatenation du ByteBuffer contenant la date & heure                                       
                                            byteBuffer_complet.put(byteBuffer);                                             // et celui contenant le msg                                       
                                            byteBuffer_complet.flip();                                                      // en un ByteBuffer qui sera envoyé au socketChannel
                                            
                                            sc_client.write(byteBuffer_complet);

                                        } catch (IOException ioe) {                  
                                            ioe.printStackTrace();
                                            System.out.println("La connexion est perdue avec un client.");
                                        }
                                
                                        byteBuffer.rewind();
                                    }
                                }
                                byteBuffer.clear();
                            }

                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                            System.out.println("Un client est partie comme un sauvage...");
                            
                            fos.write((string_date_heure_actuelle() + "Un client est partie comme un sauvage...").getBytes());

                            cle.cancel();
                        }

                    }
                }    
                
                cles_presentes.clear();         // Vide le Set pour le réutiliser au prochain selecteur.selectedKeys(); 

            }
            
        } catch (BindException be) {
            be.printStackTrace();
            String msg = "IP address already in use";
            sendLogToUI(msg);
            System.out.println(msg);
        
        } catch (IOException ioe) {
            ioe.printStackTrace();
            sendLogToUI("Une erreur est survenue...\n");

        } finally {                                                         // S'éxecute lorsque le serveur est arrêté 
            if (serveur != null) {                      
                try {
                    serveur.close();                                        // Fermeture du ServerSocketChannel
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    System.out.println("Erreur lors de la fermeture du serveur.");
                }
            }
            if (fos != null) {                                              
                try {
                    fos.close();                                            // Fermeture du FileOutputStream
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    System.out.println("Erreur lors de la fermeture du FileOutputStream.");
                }
            }
        } 
    }

    public String string_date_heure_actuelle() {        // Fonction renvoyant un String contenant la date et l'heure sous format => "[dd/MM/yy - HH:mm]"
        LocalDate date = LocalDate.now();
        LocalTime heure = LocalTime.now();    
        return ("[" + date.format(format_date) + " - " + heure.format(format_heure) + "] ");
    }
	
    /**
     * Envoi un message de log a l'IHM
     */
    public void sendLogToUI(String message) {
        Platform.runLater(() -> serverUI.log(message));
    }

}

import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.nio.ByteBuffer;

import java.sql.*;

/**
 * Client de tchat
 */
public class Client extends Thread implements ITchat {
    ClientUI user_interface;
    InetAddress ip;
    int port = 1234;
    String nickname;
    String password;
    String input = "";

    Selector selecteur;
    SocketChannel sc_client;

    public Client(ClientUI interface_param, String ip_param, int port_param, String nickname_param, String password_param) {
        this.user_interface = interface_param;
        try {
        this.ip = InetAddress.getByName(ip_param);              // Client prend en paramètre les champs de texte entrés dans ClientUI par le client
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }
        this.port = port_param;
        this.nickname = nickname_param;
        this.password = password_param;
    }

	public void run() {
        boolean test_connexion = false;
        String url = "jdbc:sqlite:BDD_tchat.db";                // Les nickname et password sont stockés dans une BDD
        Connection verif_joueur_conn = null;

        try {                                                                               // Vérification du nickname et password entré
            verif_joueur_conn = DriverManager.getConnection(url);
            String requete = "SELECT mdp FROM utilisateurs WHERE nom=?";
            PreparedStatement preparedStatement = verif_joueur_conn.prepareStatement(requete);
            preparedStatement.setString(1, nickname);

            ResultSet utilisateur = preparedStatement.executeQuery();
            
            if (utilisateur.next()) {
                if (utilisateur.getString("mdp").hashCode() == password.hashCode()) {       // nickname et password correct
                    user_interface.appendMessage("Connexion...\n");
                    test_connexion =  true;

                } else {                                                                                // password incorrect pour le nickname entré
                    user_interface.appendMessage("Mot de passe incorrect !\n");
                    user_interface.disconnectFromServer();
                }

            } else {                                                                                    // Le nickname n'existe pas donc création du compte
                String requete_insertion = "INSERT INTO utilisateurs (nom, mdp) VALUES (?, ?)";
                PreparedStatement preparedStatement2 = verif_joueur_conn.prepareStatement(requete_insertion);
                preparedStatement2.setString(1, nickname);
                preparedStatement2.setString(2, password);

                preparedStatement2.executeUpdate();

                user_interface.appendMessage("Création du compte...\n");
                test_connexion =  true;
            }
            user_interface.emptyPassword();

        } 
        catch (SQLException e) {
            e.printStackTrace();

        } finally {
            try {
                verif_joueur_conn.close();          // Fermeture de la connexion à la BDD

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (test_connexion) {                               // Le client s'est correctement connecté
            try {
                selecteur = Selector.open();                // Initialisation du selecteur
                sc_client = SocketChannel.open();           // Initialisation du socketChannel

                sc_client.configureBlocking(false);
                sc_client.connect(new InetSocketAddress(InetAddress.getLocalHost(), port));         // Le serveur tourne sur le localhost
                sc_client.register(selecteur, SelectionKey.OP_CONNECT);                 // Enregistrement du SocketChannel sur le selecteur

                new Client_messages(sc_client, user_interface).start();                 // Démarrage du thread qui va gérer l'affichage des msg en arrière plan
                
                while (user_interface.isRunning()) {                                    // Tant que l'interface tourne
                    selecteur.select();                                             
                    Set<SelectionKey> cle_presentes = selecteur.selectedKeys();         // Récupération des clés
                    Iterator<SelectionKey> parcours_cle = cle_presentes.iterator();     // Création d'un Iterator pour parcourir le Set de clés

                    while (parcours_cle.hasNext()) {                                        // Parcours des clés du selecteur
                        SelectionKey cle = parcours_cle.next(); 
        

                        if (cle.isConnectable()) {                                          // La clé indique que on peut finaliser la connexion
                            SocketChannel client_sc = (SocketChannel) cle.channel();        // On récupère le socketChannel à travers la clé

                            if (client_sc.isConnectionPending()) {
                                client_sc.finishConnect();                                  // Finalisation de l'ouverture de connexion
                            }

                            client_sc.register(selecteur, SelectionKey.OP_WRITE);           // Enregistrement du socketChannel au selecteur     

                            String msg_connexion = nickname + " s'est connecté !\n";
                            ByteBuffer byteBuffer = ByteBuffer.wrap(msg_connexion.getBytes(StandardCharsets.UTF_8));
                            client_sc.write(byteBuffer);                                    // Ecriture dans le socketChannel

                            byteBuffer.clear();                                             // 'Reset' le ByteBuffer pour la prochaine utilisation
                                        
    
                        } else if (cle.isWritable()) {                                      // La clé indique que on peut écrire des données dans le channel associé
                            SocketChannel client_sc = (SocketChannel) cle.channel();

                            if (!input.isEmpty()) {                                         // N'écrit que lorsque le client a tapé Entrée et que le champ de texte contient quelque chose
                                String msg_en_forme = nickname + " : " + input + "\n";
                                ByteBuffer byteBuffer = ByteBuffer.wrap(msg_en_forme.getBytes(StandardCharsets.UTF_8));
                                client_sc.write(byteBuffer);                                // Ecriture dans le socketChannel
                                                                                
                                byteBuffer.clear();
                                input = "";                                                 // On vide le champ de texte après que le client ait envoyé son message
                            }
                        }

                        parcours_cle.remove();                                              // On retire la clé pour ne pas la reparcourir ensuite
                    }
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();

            } finally {                                                                     // S'éxecute lorsque le client a appuyé sur le bouton de deconnexion
                String msg_deconnexion = nickname + " s'est deconnecté !\n";
                ByteBuffer byteBuffer = ByteBuffer.wrap(msg_deconnexion.getBytes(StandardCharsets.UTF_8));

                try {
                    sc_client.write(byteBuffer);                                            // Ecriture dans le socketChannel

                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }

                byteBuffer.clear();

                if (sc_client != null) {                                                    // Fermeture du socketChannel
                    try {
                        sc_client.close();
                        user_interface.appendMessage("Deconnexion...\n");           // Affichage d'un message dans l'interface client
                    } catch (IOException ioe) {
                        //
                    }
                }

                user_interface.setDisconnectedState();                      // Remet l'interface utilisateur dans l'état déconnecté
            }
        }
    }
}

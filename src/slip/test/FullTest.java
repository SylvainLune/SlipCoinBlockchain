package slip.test;

import java.util.ArrayList;

import slip.blockchain.network.NodeThread;
import slip.blockchain.pos.SCBlockData_transaction;
import slip.blockchain.pos.SCCoinWallet;
import slip.blockchain.pos.SCNode;
import slip.network.buffers.NetBuffer;
import slip.network.tcp.TCPClient;
import slip.network.tcp.TCPServer;
import slip.security.common.RSA;

/**
 * Démonstration de la manière dont fonctionne le TCPServer et TCPClient
 *
 */



///!!\ Ici, je lance un nouveau thread pour simuler une application serveur, rien de plus
class ApplicationServeur implements Runnable {
	
	/** Ecrire un message sur la console (+ rapide à écrire !)
	 * @param infoMessage message à écrire
	 */
	public static void log(String infoMessage) {
		synchronized(WriteOnConsoleClass.LOCK) { System.out.println("ApplicationServeur : " + infoMessage); } //System.out.flush();
		//System.out.println("ApplicationServeur : " + infoMessage);
	}

	public static int nextClientID = 1;
	
	class CustomServerClient {
		public TCPClient tcpSock;
		private final int ID;
		private boolean estAuthentifie = false;
		private String nomDeCompte, motDePasse;
		
		public boolean estActuellementAuthentifie() {
			return estAuthentifie;
		}
		public void vientDEtreAuthentifie(String arg_compte, String arg_pass) {
			nomDeCompte = arg_compte;
			motDePasse = arg_pass;
			estAuthentifie = true;
		}
		
		public CustomServerClient(TCPClient arg_tcpSock) {
			tcpSock = arg_tcpSock;
			ID = ApplicationServeur.nextClientID;
			ApplicationServeur.nextClientID++;
		}
	}
	
	public ArrayList<CustomServerClient> serverClientList = new ArrayList<CustomServerClient>();
	
	public boolean checkUserCredentials(String userName, String userPass) {
		return true;
	}
	
	public static void sleep(long millisec) {
		try { Thread.sleep(millisec); } catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	TCPServer server;
	
	@Override
	// Equivalent de la méthode :
	// public static void main(String[] args)
	// sur une réelle appication serveur.
	public void run() {
		
		
		int tcpPort = 12345;
		server = new TCPServer(tcpPort);
		if (server.isListening()) {
			log("Le serveur écoute sur le port " + tcpPort);
		} else {
			server.stop();
			return;
		}
		
		// Boucle du serveur
		while (server.isListening()) {
			
			// Accepter de nouveaux clients (asynchrone)
			TCPClient newTCPClient = server.accept(); // non bloquant
			if (newTCPClient != null) {
				// Nouveau client accepté !
				// Je crée le client du serveur
				CustomServerClient servClient = new CustomServerClient(newTCPClient);
				serverClientList.add(servClient);

				log("Nouveau client ! IP client = " + newTCPClient.getRemoteIP());
				/*
				System.out.println("Serveur : nouveau client - Liste des clients :");
				for (int i = 0; i < serverClientList.size(); i++) {
					System.out.println(serverClientList.get(i).ID);
				}*/
			}
			
			// Suppression des clients qui ne sont plus connectés
			int clientIndex = 0;
			while (clientIndex < serverClientList.size()) {
				CustomServerClient servClient = serverClientList.get(clientIndex);
				if ( ! servClient.tcpSock.isConnected() )  {
					boolean criticalErrorOccured = servClient.tcpSock.criticalErrorOccured();
					if (criticalErrorOccured) {
						log("Erreur critique sur un client, déconnexion : " + servClient.tcpSock.getCriticalErrorMessage());
					}
					servClient.tcpSock.stop(); // facultatif
					serverClientList.remove(clientIndex);
					System.out.println("Serveur : Déconnexion du client : " + servClient.ID);
					
				} else
					clientIndex++;
			}
			
			// Ecouter ce que les clients demandent
			for (clientIndex = 0; clientIndex < serverClientList.size(); clientIndex++) {
				CustomServerClient servClient = serverClientList.get(clientIndex);
				NetBuffer newMessage = servClient.tcpSock.getNewMessage();
				if (newMessage != null) {
					log("Nouveau message reçu de " + servClient.ID);
					if (! newMessage.currentData_isInt()) {
						log("ERREUR : message mal formatté.");
						// Je ne réponds rien
						//servClient.tcpSock.sendMessage(replyMessage);
					} else {
						int messageType = newMessage.readInteger();
						
						// Authentification
						if (messageType == 1) {
							String nomCompte = newMessage.readString();
							String motDePasse = newMessage.readString();
							if (checkUserCredentials(nomCompte, motDePasse)) {
								servClient.vientDEtreAuthentifie(nomCompte, motDePasse);
								// Réuss
								NetBuffer reply = new NetBuffer();
								reply.writeInt(1);
								reply.writeBool(true);
								reply.writeString("Bienvenue " + nomCompte);
								servClient.tcpSock.sendMessage(reply);
							} else {
								NetBuffer reply = new NetBuffer();
								reply.writeInt(1);
								reply.writeBool(false);
								reply.writeString("Echec de la connexion : mot de passe ou nom de compte invalide.");
								servClient.tcpSock.sendMessage(reply);
							}
						}

						// Demander son ID
						if (messageType == 2) {
							NetBuffer reply = new NetBuffer();
							reply.writeInt(2);
							reply.writeBool(servClient.estActuellementAuthentifie());
							if (servClient.estActuellementAuthentifie()) {
								reply.writeInt(servClient.ID);
							}
							servClient.tcpSock.sendMessage(reply);
						}

						// Demander son nom (amnésie power...)
						if (messageType == 3) {
							NetBuffer reply = new NetBuffer();
							reply.writeInt(3);
							reply.writeBool(servClient.estActuellementAuthentifie());
							if (servClient.estActuellementAuthentifie()) {
								reply.writeString(servClient.nomDeCompte);
							}
							servClient.tcpSock.sendMessage(reply);
						}
						
						
					}
				}
			}
			sleep(1); // 1ms entre chaque itération, minimum
		}
		
	}
	
	public void forceStop() {
		if (server == null) return;
		server.stop();
	}
	
}

///!!\ Ici, je lance un nouveau thread pour simuler une application serveur, rien de plus
class ApplicationClient implements Runnable {
	
	
	public static void sleep(long millisec) {
		try { Thread.sleep(millisec); } catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	TCPClient client;
	
	/** Ecrire un message sur la console (+ rapide à écrire !)
	 * @param infoMessage message à écrire
	 */
	public static void log(String infoMessage) {
		synchronized(WriteOnConsoleClass.LOCK) { System.out.println("ApplicationClient : " + infoMessage); } //System.out.flush();
		//System.out.println("ApplicationClient : " + infoMessage);
	}
	
	public boolean rcvMessageLoop() {
		boolean receivedSomething = false;
		boolean continueLoop = true;
		while (continueLoop) {
			if (rcvMessageLoop_iteration()) {
				receivedSomething = true;
			} else continueLoop = false; // plus de message à recevoir
		}
		return receivedSomething;
	}
	
	private boolean rcvMessageLoop_iteration() {
		
		if (! client.isConnected()) {
			return false;
		}
		
		NetBuffer rcvMessage = client.getNewMessage();
		if (rcvMessage == null)  return false;
		
		int typeMessage = rcvMessage.readInt();
		log("Reçu message de type = " + typeMessage);
		
		if (typeMessage == 1) {
			boolean ressiteAuthentification = rcvMessage.readBool();
			String strMessage = rcvMessage.readStr();
			log("Réussite authentification : " + ressiteAuthentification + " message = " + strMessage);
		}

		if (typeMessage == 2) {
			boolean estAuthentifie = rcvMessage.readBool();
			log("Est authentifie = " + estAuthentifie);
			if (estAuthentifie) {
				int monID = rcvMessage.readInt();
				log("Mon ID reçu : " + monID);
			}
		}

		if (typeMessage == 3) {
			boolean estAuthentifie = rcvMessage.readBool();
			log("Est authentifie = " + estAuthentifie);
			if (estAuthentifie) {
				String nomDeCompte = rcvMessage.readString();
				log("Mon nom de compte : " + nomDeCompte);
			}
		}
		
		return true;
	}
	
	@Override
	// Equivalent de la méthode :
	// public static void main(String[] args)
	// sur une réelle appication client.
	public void run() {
		
		int tcpPort = 12345;
		client = new TCPClient("localhost", tcpPort);
		
		for (int iWait = 0; iWait < 1000; iWait++) {
			if (client.isConnected()) {
				break;
			}
			if (iWait >= 990) {
				log("ERREUR : impossible de se connecter, temps d'attente dépassé");
				client.stop();
				break;
			}
			if (client.criticalErrorOccured()) {
				log("ERREUR critique dans le client : " + client.getCriticalErrorMessage());
				break;
			}
			sleep(1);
		}
		if (! client.isConnected()) return;
		
		
		NetBuffer demandeConnexionMessage = new NetBuffer();
		demandeConnexionMessage.writeInt(1);
		demandeConnexionMessage.writeString("Sylvie");
		demandeConnexionMessage.writeString("**Lalouette**");
		client.sendMessage(demandeConnexionMessage);
		
		for (int iWait = 0; iWait < 1000; iWait++) {
			
			/*NetBuffer rcvMessage = client.getNewMessage();
			if (rcvMessage == null)  {
				sleep(1);
				continue;
			}
			int typeMessage = rcvMessage.readInt();
			if (typeMessage == 1) {
				boolean ressiteAuthentification = rcvMessage.readBool();
				String strMessage = rcvMessage.readStr();
				log("Réussite authentification : " + ressiteAuthentification + " message = " + strMessage);
			}*/
			if (rcvMessageLoop()) break; // message reçu !
			sleep(1);
		}
		

		NetBuffer demandeIDMessage = new NetBuffer();
		demandeIDMessage.writeInt(2);
		client.sendMessage(demandeIDMessage);
		
		NetBuffer demandeNomMessage = new NetBuffer();
		demandeNomMessage.writeInt(3);
		client.sendMessage(demandeNomMessage);
		
		// Je reçois ce que je peux pendant 200ms
		for (int iWait = 0; iWait < 200; iWait++) {
			
			rcvMessageLoop();
			sleep(1);
			
		}
		
		client.stop();
		
		
	}
	
}

public class FullTest {
	
	public static void sleep(long millisec) {
		try { Thread.sleep(millisec); } catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}
	
	/** Ecrire un message sur la console (+ rapide à écrire !)
	 * @param infoMessage message à écrire
	 */
	public static void log(String infoMessage) {
		System.out.println(infoMessage);
	}
	
	
	public static void showWallets(ArrayList<SCCoinWallet> walletList, SCNode fromNode) {
		for (SCCoinWallet wallet : walletList) {
			wallet.updateWallet(fromNode);
			System.out.println(wallet);
		}
	}
	
	public static void main_ENLEVER_POUR_TESTER(String[] args) throws InterruptedException {
		
		
		SCCoinWallet walletNode1 = SCCoinWallet.createNewWallet("Node 1 owner");
		SCCoinWallet walletNode2 = SCCoinWallet.createNewWallet("Node 2 owner");
		SCCoinWallet walletNode3 = SCCoinWallet.createNewWallet("Node 3 owner");
		
		SCNode node = new SCNode(walletNode1.getPublicKey(), walletNode1.getPrivateKey());//RSA.STR_PUBLIC_KEY, RSA.STR_PRIVATE_KEY);
		
		SCNode node2 = new SCNode(walletNode2.getPublicKey(), walletNode2.getPrivateKey());//RSA.STR_PUBLIC_KEY, RSA.STR_PRIVATE_KEY);
		SCNode node3 = new SCNode(walletNode3.getPublicKey(), walletNode3.getPrivateKey());//RSA.STR_PUBLIC_KEY, RSA.STR_PRIVATE_KEY);
		
		
		
		
		
		//SCBlockData_transaction(int arg_amount, String arg_senderKey, String arg_receiverKey, boolean hasToSignTransaction, String senderPrivateKey, String arg_senderSignature);
		SCBlockData_transaction transaction
		  = new SCBlockData_transaction(78, RSA.STR_PUBLIC_KEY, "123456789", System.currentTimeMillis(), true, RSA.STR_PRIVATE_KEY, null);
		//System.out.println(transaction.toString());
		System.out.println("Transaction valide : " + transaction.checkSignatureValidity());
		transaction.receiverPublicKey = "123456779"; // petite erreur
		System.out.println("Transaction valide : " + transaction.checkSignatureValidity());
		transaction.receiverPublicKey = "méchant_voleur"; // vilain, vilain
		System.out.println("Transaction valide : " + transaction.checkSignatureValidity());
		transaction.receiverPublicKey = "123456789"; // retour à la normale
		System.out.println("Transaction valide : " + transaction.checkSignatureValidity());

		SCCoinWallet walletEtienne = SCCoinWallet.createNewWallet("Etienne");
		SCCoinWallet walletSylvain = SCCoinWallet.createNewWallet("Sylvain");
		SCCoinWallet walletLong = SCCoinWallet.createNewWallet("Long");
		SCCoinWallet walletAntonin = SCCoinWallet.createNewWallet("Antonin");
		SCCoinWallet walletProfDeJava = SCCoinWallet.createNewWallet("LaProfDeJava!");
		

		/*Pour le test de IdenticalTimeSnap
		 * node.newTransaction(3.421, walletEtienne.getPrivateKey(), walletEtienne.getPublicKey(), walletSylvain.getPublicKey());
		node2.newTransaction(3.421, walletEtienne.getPrivateKey(), walletEtienne.getPublicKey(), walletSylvain.getPublicKey());
		node3.newTransaction(3.421, walletEtienne.getPrivateKey(), walletEtienne.getPublicKey(), walletSylvain.getPublicKey());
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		node2.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		node3.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());*/
		
		
		ArrayList<SCCoinWallet> a1Wallet = new ArrayList<SCCoinWallet>();
		a1Wallet.add(walletEtienne);
		a1Wallet.add(walletSylvain);
		a1Wallet.add(walletLong);
		a1Wallet.add(walletAntonin);
		a1Wallet.add(walletProfDeJava);
		
		showWallets(a1Wallet, node);
		boolean success;
		success = node.newTransaction(3.421, walletEtienne.getPrivateKey(), walletEtienne.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction : Etienne -> Sylvain : réussite = " + success);
		//showWallets(a1Wallet, node);

		// OK pour le TimeStamp
		success = node.newTransaction(1.7852, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction :1 Long -> Sylvain : réussite = " + success);
		Thread.sleep(1);
		success = node.newTransaction(1.7852, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction :2 Long -> Sylvain : réussite = " + success);
		Thread.sleep(1);
		success = node.newTransaction(0.2, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction :3 Long -> Sylvain : réussite = " + success);
		Thread.sleep(1);
		success = node.newTransaction(0.2, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction :4 Long -> Sylvain : réussite = " + success);
		//showWallets(a1Wallet, node);
		
		success = node.newTransaction(78.421, walletAntonin.getPrivateKey(), walletAntonin.getPublicKey(), walletSylvain.getPublicKey());
		System.out.println("Transaction : Antonin -> Sylvain : réussite = " + success);
		//showWallets(a1Wallet, node);
		
		success = node.newTransaction(8.11, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletEtienne.getPublicKey());
		System.out.println("Transaction : Long -> Sylvain : réussite = " + success);
		//showWallets(a1Wallet, node);
		
		success = node.newTransaction(12.8765, walletSylvain.getPrivateKey(), walletSylvain.getPublicKey(), walletProfDeJava.getPublicKey());
		System.out.println("Transaction : Sylvain -> Prof de Java : réussite = " + success);
		showWallets(a1Wallet, node);

		System.out.println("Node : node.get_bufferedDataList_size() = " + node.get_bufferedDataList_size()); // transactions en attente dans le buffer
		System.out.println("Node : node.get_blockChain_size() = " + node.get_blockChain_size()); // nombre de blocs dans le blockchain
		System.out.println(" --- Sylvain mine des blocs --- "); // Ecriture des transactions dans les blocs
		
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());

		System.out.println("Node : node.get_bufferedDataList_size() = " + node.get_bufferedDataList_size());
		System.out.println("Node : node.get_blockChain_size() = " + node.get_blockChain_size());
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletSylvain.getPublicKey(), walletSylvain.getPrivateKey());
		showWallets(a1Wallet, node);

		System.out.println(" --- Antonin mine des blocs --- ");
		node.assembleNewBlockWithBufferedData(walletAntonin.getPublicKey(), walletAntonin.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletAntonin.getPublicKey(), walletAntonin.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletAntonin.getPublicKey(), walletAntonin.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletAntonin.getPublicKey(), walletAntonin.getPrivateKey());
		node.assembleNewBlockWithBufferedData(walletAntonin.getPublicKey(), walletAntonin.getPrivateKey());
		showWallets(a1Wallet, node);
		
		// receiveNewBlockChainPart
		
		node2.newTransaction(1.421, walletAntonin.getPrivateKey(), walletAntonin.getPublicKey(), walletSylvain.getPublicKey());
		node2.newTransaction(0.42, walletLong.getPrivateKey(), walletLong.getPublicKey(), walletSylvain.getPublicKey());
		node2.assembleNewBlockWithBufferedData(walletLong.getPublicKey(), walletLong.getPrivateKey());
		node2.receiveNewBlockChainPart(node.debugGetBlockchain());
		
		System.out.println(" --- Synchronisation des blockchains --- ");
		showWallets(a1Wallet, node2);
		
		
		
		/*NodeThread node2Thread = new NodeThread(node2, 3334);
		NodeThread node3Thread = new NodeThread(node3, 3337);
		new Thread(node2Thread).start();
		new Thread(node3Thread).start();*/
		
		
		if (true) return;
		System.out.println("Solde = " + node.getWalletAmount(RSA.STR_PUBLIC_KEY));
		System.out.println("Chaine intègre = " + node.checkMyBlockChain());
		
		
		
		/*
		
		ApplicationServeur applicationServ = new ApplicationServeur();
		ApplicationClient applicationClient = new ApplicationClient();
		
		new Thread(applicationServ).start();
		new Thread(applicationClient).start();
		
		// Attendre 200ms, histoire que tout le monde ait bien fini de tout faire ce qu'il avait à faire (toussa toussa)
		sleep(200);
		// Maintenant arrêt en bourrin
		applicationServ.forceStop();
		*/
		//applicationClient.forceStop();
		
		
		
		/* ça marche bien, même si on en lance 5000 en 5 secondes ! (1 par ms)
		for (int i = 1; i <= 5000; i++) {
			new Thread(new ApplicationClient()).start();
			sleep(1);
		}*/
		
	}
}

//Juste pour avoir un println thread-safe
class WriteOnConsoleClass {
	static public final Object LOCK = new Object();
}

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Sistema Completo per Acquisti di Prodotti
 * 
 * Questo file contiene tutto il codice necessario per:
 * - Server che genera prezzi casuali
 * - Client che effettuano acquisti
 * - Launcher per avviare il sistema completo
 */
public class SistemaAcquistiCompleto {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            // Avvia il sistema completo
            avviaSystemaCompleto();
        } else if (args[0].equals("server")) {
            // Avvia solo il server
            ProductServer server = new ProductServer();
            server.start();
        } else if (args[0].equals("client")) {
            // Avvia solo un client
            String clientId = args.length > 1 ? args[1] : "Client-" + System.currentTimeMillis();
            ProductClient client = new ProductClient(clientId);
            client.start();
        } else {
            System.out.println("Uso: java SistemaAcquistiCompleto [server|client [nome_client]]");
            System.out.println("Senza parametri avvia il sistema completo");
        }
    }
    
    /**
     * Avvia il sistema completo con server e 3 client
     */
    private static void avviaSystemaCompleto() {
        System.out.println("=== SISTEMA ACQUISTI PRODOTTI ===");
        System.out.println("Avvio server e 3 client...");
        
        // Avvia server in thread separato
        Thread serverThread = new Thread(() -> {
            ProductServer server = new ProductServer();
            server.start();
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Aspetta che il server si avvii
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Avvia 3 client
        for (int i = 1; i <= 3; i++) {
            final String clientId = "Client-" + i;
            Thread clientThread = new Thread(() -> {
                ProductClient client = new ProductClient(clientId);
                client.start();
            });
            clientThread.start();
            
            try {
                Thread.sleep(1000); // Ritardo tra avvii client
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        System.out.println("Sistema avviato con successo!");
        
        // Mantieni il main thread attivo
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Sistema terminato");
        }
    }
}

/**
 * Classe Server per la gestione dei prodotti
 */
class ProductServer {
    private static final int PORT = 8080;
    private static final int MIN_PRICE = 10;
    private static final int MAX_PRICE = 100;
    private static final int PRICE_INTERVAL = 3000; // 3 secondi
    
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private Random random;
    private boolean isRunning;
    private int currentPrice;
    private int clientsFinished;
    
    public ProductServer() {
        clients = Collections.synchronizedList(new ArrayList<>());
        random = new Random();
        isRunning = true;
        clientsFinished = 0;
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("SERVER AVVIATO sulla porta " + PORT);
            System.out.println("In attesa di connessioni client...");
            
            // Thread per generazione prezzi
            Thread priceThread = new Thread(this::generatePrices);
            priceThread.setDaemon(true);
            priceThread.start();
            
            // Accetta connessioni client
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    clients.add(handler);
                    Thread clientThread = new Thread(handler);
                    clientThread.start();
                    
                    System.out.println("Nuovo client connesso. Totale: " + clients.size());
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Errore connessione client: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("Errore server: " + e.getMessage());
        }
    }
    
    private void generatePrices() {
        while (isRunning) {
            try {
                currentPrice = MIN_PRICE + random.nextInt(MAX_PRICE - MIN_PRICE + 1);
                System.out.println("PREZZO GENERATO: EUR " + currentPrice);
                
                // Invia prezzo a tutti i client
                synchronized (clients) {
                    Iterator<ClientHandler> it = clients.iterator();
                    while (it.hasNext()) {
                        ClientHandler client = it.next();
                        if (!client.sendPrice(currentPrice)) {
                            it.remove();
                        }
                    }
                }
                
                Thread.sleep(PRICE_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public synchronized boolean processPurchase(String clientId, int maxPrice) {
        if (maxPrice >= currentPrice) {
            System.out.println("ACQUISTO APPROVATO per " + clientId + 
                             " (Max: EUR " + maxPrice + ", Vendita: EUR " + currentPrice + ")");
            return true;
        } else {
            System.out.println("ACQUISTO RIFIUTATO per " + clientId + 
                             " (Max: EUR " + maxPrice + ", Vendita: EUR " + currentPrice + ")");
            return false;
        }
    }
    
    public synchronized void clientFinished(String clientId) {
        clientsFinished++;
        System.out.println(clientId + " ha terminato gli acquisti. " + 
                          "Completati: " + clientsFinished + "/" + clients.size());
        
        if (clientsFinished >= clients.size() && clients.size() > 0) {
            System.out.println("TUTTI I CLIENT HANNO TERMINATO. Chiusura server...");
            shutdown();
        }
    }
    
    public void shutdown() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Errore chiusura server: " + e.getMessage());
        }
    }
}

/**
 * Gestore per ogni client connesso
 */
class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ProductServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String clientId;
    private boolean isConnected;
    
    public ClientHandler(Socket socket, ProductServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.clientId = "Client-" + socket.getPort();
        this.isConnected = true;
        
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Errore setup client handler: " + e.getMessage());
            isConnected = false;
        }
    }
    
    @Override
    public void run() {
        try {
            String message;
            while (isConnected && (message = in.readLine()) != null) {
                if (message.startsWith("PURCHASE:")) {
                    int maxPrice = Integer.parseInt(message.split(":")[1]);
                    boolean approved = server.processPurchase(clientId, maxPrice);
                    out.println(approved ? "APPROVED" : "DENIED");
                } else if (message.equals("FINISHED")) {
                    server.clientFinished(clientId);
                    break;
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Errore client handler: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    
    public boolean sendPrice(int price) {
        if (isConnected && out != null) {
            out.println("PRICE:" + price);
            return !out.checkError();
        }
        return false;
    }
    
    private void closeConnection() {
        isConnected = false;
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Errore chiusura connessione: " + e.getMessage());
        }
    }
}

/**
 * Classe Client per l'acquisto di prodotti
 */
class ProductClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    private static final int MIN_MAX_PRICE = 10;
    private static final int MAX_MAX_PRICE = 75;
    private static final int TARGET_PURCHASES = 10;
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Random random;
    private String clientId;
    private int purchaseCount;
    private boolean isRunning;
    
    public ProductClient(String clientId) {
        this.clientId = clientId;
        this.random = new Random();
        this.purchaseCount = 0;
        this.isRunning = true;
    }
    
    public void start() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println(clientId + " connesso al server");
            
            String message;
            while (isRunning && (message = in.readLine()) != null) {
                if (message.startsWith("PRICE:")) {
                    int sellingPrice = Integer.parseInt(message.split(":")[1]);
                    handlePrice(sellingPrice);
                } else if (message.equals("APPROVED")) {
                    purchaseCount++;
                    System.out.println(clientId + " - Acquisto numero " + purchaseCount + " APPROVATO");
                    
                    if (purchaseCount >= TARGET_PURCHASES) {
                        finishPurchasing();
                    }
                } else if (message.equals("DENIED")) {
                    System.out.println(clientId + " - Acquisto RIFIUTATO");
                }
            }
            
        } catch (IOException e) {
            System.err.println(clientId + " - Errore connessione: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    
    private void handlePrice(int sellingPrice) {
        int maxPrice = MIN_MAX_PRICE + random.nextInt(MAX_MAX_PRICE - MIN_MAX_PRICE + 1);
        
        System.out.println(clientId + " - Prezzo vendita: EUR " + sellingPrice + 
                          ", Budget massimo: EUR " + maxPrice);
        
        if (sellingPrice <= maxPrice) {
            System.out.println(clientId + " - Invio richiesta acquisto");
            out.println("PURCHASE:" + maxPrice);
        } else {
            System.out.println(clientId + " - Prezzo troppo alto, non acquisto");
        }
    }
    
    private void finishPurchasing() {
        System.out.println(clientId + " - COMPLETATI " + TARGET_PURCHASES + " ACQUISTI");
        out.println("FINISHED");
        isRunning = false;
    }
    
    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println(clientId + " - Errore chiusura: " + e.getMessage());
        }
    }
}
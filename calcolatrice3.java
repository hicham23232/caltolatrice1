import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Online Product Sales System
 * 
 * Simple application managing online product sales with:
 * - JavaFX graphical interface
 * - Client-server architecture using sockets
 * - User authentication system
 * - Product management (buy, sell, return)
 */
public class SistemaVenditaOnline extends Application {
    
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("server")) {
            ProductServer server = new ProductServer();
            server.start();
        } else {
            launch(args);
        }
    }
    
    @Override
    public void start(Stage primaryStage) {
        Thread serverThread = new Thread(() -> {
            ProductServer server = new ProductServer();
            server.start();
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ClientInterface client = new ClientInterface();
        client.start(primaryStage);
    }
}

/**
 * Product class representing items in the system
 */
class Product {
    private String name;
    private double price;
    private String identifier;
    
    public Product(String name, double price, String identifier) {
        this.name = name;
        this.price = price;
        this.identifier = identifier;
    }
    
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getIdentifier() { return identifier; }
    
    @Override
    public String toString() {
        return String.format("%s - EUR %.2f (ID: %s)", name, price, identifier);
    }
    
    public String toProtocolString() {
        return name + ";" + price + ";" + identifier;
    }
    
    public static Product fromProtocolString(String str) {
        String[] parts = str.split(";");
        return new Product(parts[0], Double.parseDouble(parts[1]), parts[2]);
    }
}

/**
 * User class for authentication
 */
class User {
    private String username;
    private String password;
    private List<Product> purchasedProducts;
    
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.purchasedProducts = new ArrayList<>();
    }
    
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<Product> getPurchasedProducts() { return purchasedProducts; }
    
    public void addPurchasedProduct(Product product) {
        purchasedProducts.add(product);
    }
    
    public boolean removePurchasedProduct(String identifier) {
        return purchasedProducts.removeIf(p -> p.getIdentifier().equals(identifier));
    }
}

/**
 * Server class managing products and user connections
 */
class ProductServer {
    private static final int PORT = 8080;
    private ServerSocket serverSocket;
    private Map<String, User> users;
    private List<Product> availableProducts;
    private boolean isRunning;
    
    public ProductServer() {
        users = new ConcurrentHashMap<>();
        availableProducts = Collections.synchronizedList(new ArrayList<>());
        isRunning = true;
        initializeData();
    }
    
    /**
     * Initialize server with default users and products
     */
    private void initializeData() {
        users.put("admin", new User("admin", "admin123"));
        users.put("user1", new User("user1", "pass1"));
        users.put("user2", new User("user2", "pass2"));
        
        availableProducts.add(new Product("Laptop", 999.99, "PROD001"));
        availableProducts.add(new Product("Mouse", 29.99, "PROD002"));
        availableProducts.add(new Product("Keyboard", 79.99, "PROD003"));
        availableProducts.add(new Product("Monitor", 299.99, "PROD004"));
        availableProducts.add(new Product("Headphones", 149.99, "PROD005"));
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Product Server started on port " + PORT);
            
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Thread clientHandler = new Thread(new ServerClientHandler(clientSocket, this));
                    clientHandler.start();
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    public boolean authenticateUser(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }
    
    public List<Product> getAvailableProducts() {
        return new ArrayList<>(availableProducts);
    }
    
    public synchronized boolean purchaseProduct(String username, String identifier) {
        Product product = availableProducts.stream()
            .filter(p -> p.getIdentifier().equals(identifier))
            .findFirst()
            .orElse(null);
            
        if (product != null) {
            availableProducts.remove(product);
            User user = users.get(username);
            if (user != null) {
                user.addPurchasedProduct(product);
            }
            return true;
        }
        return false;
    }
    
    public synchronized boolean returnProduct(String username, String identifier) {
        User user = users.get(username);
        if (user != null) {
            Product product = user.getPurchasedProducts().stream()
                .filter(p -> p.getIdentifier().equals(identifier))
                .findFirst()
                .orElse(null);
                
            if (product != null) {
                user.removePurchasedProduct(identifier);
                availableProducts.add(product);
                return true;
            }
        }
        return false;
    }
    
    public synchronized void addNewProduct(Product product) {
        availableProducts.add(product);
    }
    
    public void shutdown() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
}

/**
 * Server-side client handler
 */
class ServerClientHandler implements Runnable {
    private Socket clientSocket;
    private ProductServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String authenticatedUser;
    
    public ServerClientHandler(Socket socket, ProductServer server) {
        this.clientSocket = socket;
        this.server = server;
    }
    
    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            
            String message;
            while ((message = in.readLine()) != null) {
                processMessage(message);
            }
            
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }
    
    private void processMessage(String message) {
        String[] parts = message.split(":");
        String command = parts[0];
        
        switch (command) {
            case "LOGIN":
                handleLogin(parts[1], parts[2]);
                break;
            case "LIST_PRODUCTS":
                handleListProducts();
                break;
            case "PURCHASE":
                handlePurchase(parts[1]);
                break;
            case "RETURN":
                handleReturn(parts[1]);
                break;
            case "ADD_PRODUCT":
                handleAddProduct(parts[1], parts[2], parts[3]);
                break;
            case "CLOSE":
                handleClose();
                break;
        }
    }
    
    private void handleLogin(String username, String password) {
        if (server.authenticateUser(username, password)) {
            authenticatedUser = username;
            out.println("ACCESS_GRANTED");
        } else {
            out.println("ACCESS_DENIED");
        }
    }
    
    private void handleListProducts() {
        if (authenticatedUser == null) {
            out.println("NOT_AUTHENTICATED");
            return;
        }
        
        List<Product> products = server.getAvailableProducts();
        out.println("PRODUCT_LIST:" + products.size());
        for (Product product : products) {
            out.println("PRODUCT:" + product.toProtocolString());
        }
    }
    
    private void handlePurchase(String identifier) {
        if (authenticatedUser == null) {
            out.println("NOT_AUTHENTICATED");
            return;
        }
        
        if (server.purchaseProduct(authenticatedUser, identifier)) {
            out.println("PRODUCT_PURCHASED");
        } else {
            out.println("PURCHASE_FAILED");
        }
    }
    
    private void handleReturn(String identifier) {
        if (authenticatedUser == null) {
            out.println("NOT_AUTHENTICATED");
            return;
        }
        
        if (server.returnProduct(authenticatedUser, identifier)) {
            out.println("RETURN_ACCEPTED");
        } else {
            out.println("RETURN_FAILED");
        }
    }
    
    private void handleAddProduct(String name, String price, String identifier) {
        if (authenticatedUser == null) {
            out.println("NOT_AUTHENTICATED");
            return;
        }
        
        try {
            Product product = new Product(name, Double.parseDouble(price), identifier);
            server.addNewProduct(product);
            out.println("PRODUCT_ADDED");
        } catch (NumberFormatException e) {
            out.println("INVALID_PRICE");
        }
    }
    
    private void handleClose() {
        out.println("CLOSING");
        authenticatedUser = null;
    }
    
    private void closeConnection() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}

/**
 * JavaFX Client Interface
 */
class ClientInterface {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Stage primaryStage;
    private String currentUser;
    
    private TextField usernameField;
    private PasswordField passwordField;
    private ListView<String> productListView;
    private TextField newProductName;
    private TextField newProductPrice;
    private TextField newProductId;
    private TextField returnProductId;
    
    public void start(Stage stage) {
        this.primaryStage = stage;
        connectToServer();
        createLoginInterface();
    }
    
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 8080);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            showAlert("Connection Error", "Cannot connect to server");
        }
    }
    
    private void createLoginInterface() {
        VBox loginBox = new VBox(10);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(20));
        
        Label titleLabel = new Label("Online Product Sales System");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(200);
        
        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(200);
        
        Button loginButton = new Button("Login");
        loginButton.setOnAction(e -> handleLogin());
        
        loginBox.getChildren().addAll(titleLabel, usernameField, passwordField, loginButton);
        
        Scene scene = new Scene(loginBox, 400, 300);
        primaryStage.setTitle("Product Sales - Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        out.println("LOGIN:" + username + ":" + password);
        
        try {
            String response = in.readLine();
            if ("ACCESS_GRANTED".equals(response)) {
                currentUser = username;
                createMainInterface();
            } else {
                showAlert("Login Failed", "Invalid credentials");
            }
        } catch (IOException e) {
            showAlert("Error", "Communication error");
        }
    }
    
    private void createMainInterface() {
        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        
        Label welcomeLabel = new Label("Welcome " + currentUser);
        welcomeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Product list section
        Label productLabel = new Label("Available Products:");
        productListView = new ListView<>();
        productListView.setPrefHeight(150);
        Button viewProductsButton = new Button("View Products");
        viewProductsButton.setOnAction(e -> loadProducts());
        
        // Purchase section
        Label purchaseLabel = new Label("Purchase Product (Enter Product ID):");
        TextField purchaseField = new TextField();
        purchaseField.setPromptText("Product ID");
        Button purchaseButton = new Button("Purchase");
        purchaseButton.setOnAction(e -> purchaseProduct(purchaseField.getText()));
        
        // Return section
        Label returnLabel = new Label("Return Product (Enter Product ID):");
        returnProductId = new TextField();
        returnProductId.setPromptText("Product ID");
        Button returnButton = new Button("Return");
        returnButton.setOnAction(e -> returnProduct(returnProductId.getText()));
        
        // Add product section
        Label addLabel = new Label("Add New Product:");
        newProductName = new TextField();
        newProductName.setPromptText("Product Name");
        newProductPrice = new TextField();
        newProductPrice.setPromptText("Price");
        newProductId = new TextField();
        newProductId.setPromptText("Product ID");
        Button addButton = new Button("Add Product");
        addButton.setOnAction(e -> addNewProduct());
        
        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> handleClose());
        
        mainBox.getChildren().addAll(
            welcomeLabel,
            productLabel, productListView, viewProductsButton,
            purchaseLabel, purchaseField, purchaseButton,
            returnLabel, returnProductId, returnButton,
            addLabel, newProductName, newProductPrice, newProductId, addButton,
            closeButton
        );
        
        Scene scene = new Scene(mainBox, 500, 700);
        primaryStage.setTitle("Product Sales - " + currentUser);
        primaryStage.setScene(scene);
        
        loadProducts();
    }
    
    private void loadProducts() {
        out.println("LIST_PRODUCTS");
        try {
            String response = in.readLine();
            if (response.startsWith("PRODUCT_LIST:")) {
                int count = Integer.parseInt(response.split(":")[1]);
                productListView.getItems().clear();
                for (int i = 0; i < count; i++) {
                    String productLine = in.readLine();
                    if (productLine.startsWith("PRODUCT:")) {
                        String productData = productLine.substring(8);
                        Product product = Product.fromProtocolString(productData);
                        productListView.getItems().add(product.toString());
                    }
                }
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to load products");
        }
    }
    
    private void purchaseProduct(String identifier) {
        out.println("PURCHASE:" + identifier);
        try {
            String response = in.readLine();
            if ("PRODUCT_PURCHASED".equals(response)) {
                showAlert("Success", "Product purchased successfully");
                loadProducts();
            } else {
                showAlert("Purchase Failed", "Could not purchase product");
            }
        } catch (IOException e) {
            showAlert("Error", "Communication error");
        }
    }
    
    private void returnProduct(String identifier) {
        out.println("RETURN:" + identifier);
        try {
            String response = in.readLine();
            if ("RETURN_ACCEPTED".equals(response)) {
                showAlert("Success", "Product return accepted");
                loadProducts();
            } else {
                showAlert("Return Failed", "Could not return product");
            }
        } catch (IOException e) {
            showAlert("Error", "Communication error");
        }
    }
    
    private void addNewProduct() {
        String name = newProductName.getText();
        String price = newProductPrice.getText();
        String id = newProductId.getText();
        
        out.println("ADD_PRODUCT:" + name + ":" + price + ":" + id);
        
        try {
            String response = in.readLine();
            if ("PRODUCT_ADDED".equals(response)) {
                showAlert("Success", "Product added successfully");
                newProductName.clear();
                newProductPrice.clear();
                newProductId.clear();
                loadProducts();
            } else {
                showAlert("Error", "Could not add product");
            }
        } catch (IOException e) {
            showAlert("Error", "Communication error");
        }
    }
    
    private void handleClose() {
        out.println("CLOSE");
        try {
            String response = in.readLine();
            if ("CLOSING".equals(response)) {
                Platform.exit();
            }
        } catch (IOException e) {
            Platform.exit();
        }
    }
    
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
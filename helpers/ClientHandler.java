package helpers;

import Cafe3Test.Barista;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Barista barista;

    public ClientHandler(Socket socket, Barista barista) {
        this.socket = socket;
        this.barista = barista;
    }

    @Override
    public void run() {
        try (
                Scanner scanner = new Scanner(socket.getInputStream());
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            //read the customers name sent by the client
            String customerName = scanner.nextLine().trim();
            System.out.println("New connection from: " + customerName);
            JsonLogger.log("INFO"," New Connection:  " + customerName);

            if (customerName.length() < 2 || customerName.length() > 20 || !customerName.matches("^[a-zA-Z\\s]+$")) {
                writer.println("bad customer name");
                JsonLogger.log("ERROR"," Bad Customer Name: " + customerName);
                return;
            }
            barista.addClient(customerName);  //new client connection

            writer.println("success");

            while (true) {
                try {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue; // skip empty commands

                    System.out.println("Received command from client: " + line);
                    JsonLogger.log("INFO" , " Received Command " + line);

                    //parts[0] will be the command e.g. 'order', parts[1] will be the order details - if present
                    String[] parts = line.split(" ", 2);

                    String command = parts[0].toLowerCase();
                    //notifying if order is ready is consistently running in the background
                    notifyOrderReady(writer, customerName);
                    switch (command) {
                        case "order":
                            if (parts.length > 1 && parts[1].equalsIgnoreCase("status")) {
                                String status = barista.getOrderStatus(customerName);
                                writer.println("Order status: " + status);
                            } else if (parts.length > 1) {
                                String orderDetails = parts[1];
                                boolean success = barista.placeOrder(customerName, orderDetails);

                                if (success) {
                                    writer.println("your order has been placed.");
                                    JsonLogger.log("INFO", " Order has been successfully placed by: " +customerName);

                                } else {
                                    writer.println("Failed to place order. Please check your syntax and try again.");
                                }
                            } else {
                                writer.println("Invalid order command. Please specify the order details or type 'order status'.");
                            }
                            break;

                        case "collect":
                            String collectResponse = barista.collectOrder(customerName);
                            writer.println(collectResponse);
                            break;

                        case "exit":
                            System.out.println(customerName + " disconnected.");
                            JsonLogger.log("INFO", customerName + " Disconnected on EXIT command");
                            barista.removeClient(customerName); //remove client from the barista system
                            barista.logState();

                            writer.println("goodbye :( ");

                            return;

                        default:
                            writer.println("Invalid command. Please try again.");
                            JsonLogger.log("ERROR", " Invalid command from: " +customerName);

                            break;
                    }
                } catch (Exception e) {
                    System.out.println("Error processing client request: " + e.getMessage());
                    JsonLogger.log("ERROR", "Error processing client request" + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Error in client handler: " + e.getMessage());
            JsonLogger.log("ERROR", "Error in the client handler");
        }
    }

    //notify the customer their order is ready and send through writer to customer the message, if finished is true
    //server to keep serving other requests while continuously checking if the order is ready - otherwise server would block until order is ready
    private void notifyOrderReady(PrintWriter writer, String customerName) {
        new Thread(() -> {
            while (true) {
                synchronized (barista.finished) {
                    if (barista.finisheder) {
                        writer.println("your order is ready for collection, " + customerName + "!");
                        JsonLogger.log("INFO", " Order ready to be collected message for: " +customerName);

                        writer.flush();
                        break;
                    }
                }
                try {
                    Thread.sleep(600); //polls every 600 ms to see if the order is complete or not
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}

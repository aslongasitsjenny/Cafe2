import helpers.JsonLogger;
import helpers.ClientHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Customer {
    public static void main(String[] args) {
        System.out.println("Enter your name, please:");
        try (Scanner in = new Scanner(System.in)) { //input scanner for user
            String customerName = in.nextLine().trim(); // read and sanitise the name

            //validation of customer name
            if (customerName.length() < 2 || !customerName.matches("^[a-zA-Z\\s]*$") || customerName.length() > 20) {
                System.out.println("Invalid name. Must be 2-20 characters and contain only letters.");
                return;
            }

            try (
                    Socket socket = new Socket("localhost", 12345); //connect to the server through port 12345
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true); //send to server
                    Scanner serverScanner = new Scanner(socket.getInputStream()) //reading from the server
            ) {
                //send customer name to the server
                writer.println(customerName);

                //wait for server acknowledgment
                if (serverScanner.hasNextLine()) {
                    String response = serverScanner.nextLine().trim();
                    System.out.println("Received response from server: " + response);

                    if (response.equalsIgnoreCase("success")) {
                        System.out.println("Welcome to the cafe, " + customerName + "!");
                    } else {
                        System.out.println("Server rejected connection: " + response);
                        return;
                    }
                } else {
                    System.out.println("Server did not send a response.");
                    return;
                }

                //start a thread to listen for server notifications, as before it was waiting for the servers response in a synchronous manner
                //serverScanner.nextLine() was blocking the program until it receives a line from the server - and this is why the order is ready message would
                //only come through once a command was entered by the customer
                Thread serverListener = new Thread(() -> {
                    try {
                        while (serverScanner.hasNextLine()) {
                            String serverMessage = serverScanner.nextLine();
                            System.out.println("Server: " + serverMessage);
                        }
                    } catch (Exception e) {
                        System.out.println("Disconnected from server.");
                    }
                });
                serverListener.setDaemon(true); //when a user exits, the main thread will terminate - freeing up resources + doesnt block from exiting
                serverListener.start();

                while (true) {
                    System.out.println("Enter a command (e.g., 'order 1 tea', 'order status', 'collect', 'exit'):");

                    //handle graceful exit on ctrl+c
                    try {
                        String command = in.nextLine().trim();
                        if (command.equalsIgnoreCase("exit")) {
                            System.out.println("Exiting the cafe.");
                            writer.println("exit");
                            break;
                        }

                        writer.println(command); //send command to server:D
                    } catch (Exception e) {
                        //handle case when user interrupts or exits with ctrl+c or other connection issues
                        System.out.println("Client interrupted. Exiting.");

                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection closed unexpectedly: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Error reading customer name: " + e.getMessage());
        }
    }
}

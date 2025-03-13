import helpers.JsonLogger;
import helpers.ClientHandler;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

public class Barista {
    private final HashMap<String, String> waitingarea = new HashMap<>();
    private final HashMap<String, String> brewingarea = new HashMap<>();
    private final HashMap<String, String> trayarea = new HashMap<>(); // i love hashmaps ☻
    private static final int PORT = 12345;

    private final Object waitingAreaLock = new Object();
    private final Object brewingAreaLock = new Object();
    private final Object trayAreaLock = new Object();
    private final Set<String> clients = new HashSet<>(); //hashset guarantees that each client is unique + fast
    private int brewingTeas = 0;
    private int brewingCoffees = 0;

    private final Object teaCounterLock = new Object();
    private final Object coffeeCounterLock = new Object();

    public volatile boolean finisheder = false;
    public final Object finished = new Object();

    private final AtomicInteger clientCount = new AtomicInteger(0); //thread safe counters without synchronisation

    public static void main(String[] args) {
        Barista barista = new Barista();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Barista is running and listening on port " + PORT);
            JsonLogger.log("INFO", "Barista has started listening on: " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New customer connected: " + clientSocket.getInetAddress());
                //each customer gets their own thread to handle orders asynchronously
                ClientHandler clientHandler = new ClientHandler(clientSocket, barista);
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error occurred while setting up the server.");
            JsonLogger.log("ERROR", "Server error in setting up");
        }
    }

    //addClient once per client thus then synchronisation is not needed - when they join only first time
    public void addClient(String customerName) {
        //add the client to the set
        clients.add(customerName);

        //increment client count - atomic
        clientCount.incrementAndGet();

        System.out.println("Added client: " + customerName);
        logState();
    }

    public void removeClient(String customerName) {
        synchronized (waitingAreaLock) {
            waitingarea.remove(customerName);
        }
        synchronized (brewingAreaLock) {
            brewingarea.remove(customerName);
        }
        synchronized (trayAreaLock) {
            trayarea.remove(customerName);
        }
        clientCount.decrementAndGet();
        logState();
    }

    public void logState() {
        System.out.println("::: Barista log :::");
        System.out.println("Number of clients idle in the cafe: " + clientCount.get());
        System.out.println("Number of clients waiting for orders: " + waitingarea.size());
        System.out.println("Waiting Area: " + waitingarea);
        System.out.println("Brewing Area: " + brewingarea);
        System.out.println("Tray Area: " + trayarea);
    }

    //this is to place the order, handling the order details to identify the quantities of tea/coffee- add it to the waiting area and start brewing thread - in a separate one to not block main thread
    public boolean placeOrder(String customerName, String orderDetails) {
        synchronized (finished) {
            finisheder = false; //reset flag when a new order is placed - as the order wouldnt be finished, as they placed a new order.
        }
        String[] items = orderDetails.split("and");
        int teaCount = 0;
        int coffeeCount = 0;

        for (String item : items) {
            item = item.trim();
            if (item.contains("tea")) {
                teaCount += parseItemQuantity(item, "tea");
            } else if (item.contains("coffee")) {
                coffeeCount += parseItemQuantity(item, "coffee");
            }
        }

        String orderSummary = "Tea: " + teaCount + ", Coffee: " + coffeeCount;

        //since waitingarea is a shared resource, synchronise it to stop race conditions
        synchronized (waitingAreaLock) {
            waitingarea.put(customerName, orderSummary);
            System.out.println("Order added to waiting area: " + customerName);
            JsonLogger.log("INFO", " Order is added to waiting area for: " +customerName);

        }

        System.out.println("Starting brewing thread for customer: " + customerName);
        JsonLogger.log("INFO", " Started brewing thread for " + customerName);
        int finalTeaCount = teaCount;
        int finalCoffeeCount = coffeeCount;
        new Thread(() -> brewOrder(customerName, finalTeaCount, finalCoffeeCount)).start();

        return true;
    }

   //this is used for extracting item quantities from an order status string that may contain multiple items e.g. waitingAreaStatus = 'Tea: 1, Coffee: 2'
    private int countItems(String status, String item) {
        Pattern pattern = Pattern.compile(item + ":\\s*(\\d+)");
        Matcher matcher = pattern.matcher(status);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    //parseItemQuantity is used for parsing simple commands where a single item and its quantity are provided (input etc)
    //pattern is dynamic so it can be used for coffee, tea.
    //matcher object is created by applying the pattern to the lowercase version of the item string - e.g if item = '2 tea', the pattern '(\\d+)\\s*tea' will match
    //quantity is extracted if the item string matches the pattern & get the int value
    private int parseItemQuantity(String item, String itemType) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*" + itemType);
        Matcher matcher = pattern.matcher(item.toLowerCase());

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private void brewOrder(String customerName, int teaCount, int coffeeCount) {
        synchronized (brewingAreaLock) {
            synchronized (waitingAreaLock) {
                brewingarea.put(customerName, waitingarea.remove(customerName));
                System.out.println("Order moved to brewing area: " + customerName);
                JsonLogger.log("INFO", " Order moved from waiting area to brewing area for: " + customerName);
            }
        }

        //having the tea brewing thread & coffee brewing thread separately allows for concurrency, dont have to wait for tea to be done before coffee starts - unlike before
        Thread teaBrewingThread = new Thread(() -> {
            for (int i = 0; i < teaCount; i++) {
                boolean teaBrewing = false;
                while (!teaBrewing) {
                    synchronized (teaCounterLock) {
                        if (brewingTeas < 2) {
                            brewingTeas++;
                            teaBrewing = true;
                        }
                    }
                    if (!teaBrewing) {
                        try {
                            Thread.sleep(100); //wait before retrying to brew - to stop potential overloading of resources
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.out.println("Thread interrupted while waiting to brew tea for " + customerName);
                            JsonLogger.log("ERROR", " Thread interrupted for brewing tea for: " +customerName);
                            return;
                        }
                    }
                }

                try {
                    System.out.println("Brewing tea " + (i + 1) + " for " + customerName);
                    Thread.sleep(30000);
                    synchronized (trayAreaLock) { //once brewing is done add the tea to the tray area for the customer - default value incase key doesnt exist
                        trayarea.put(customerName, trayarea.getOrDefault(customerName, "") + "Tea, ");
                        System.out.println("Tea " + (i + 1) + " added to tray area for " + customerName);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread interrupted during tea brewing for " + customerName);
                    JsonLogger.log("ERROR", " Thread interrupted for brewing tea for: " +customerName);

                } finally {
                    synchronized (teaCounterLock) {
                        brewingTeas--; //this is incase the brewing catches an exception, if anything it will always fallback to this - ensuring even if it fails - the amount of teas brewing currently is always going to be reduced (keeping consistency)
                    }
                }
            }
        });

        Thread coffeeBrewingThread = new Thread(() -> {
            for (int i = 0; i < coffeeCount; i++) {
                boolean coffeeBrewing = false;
                while (!coffeeBrewing) {
                    synchronized (coffeeCounterLock) {
                        if (brewingCoffees < 2) {
                            brewingCoffees++;
                            coffeeBrewing = true;
                        }
                    }
                    if (!coffeeBrewing) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.out.println("Thread interrupted while waiting to brew coffee for " + customerName);
                            JsonLogger.log("ERROR", " Thread interrupted for brewing coffee for: " +customerName);

                            return;
                        }
                    }
                }

                try {
                    System.out.println("Brewing coffee " + (i + 1) + " for " + customerName);
                    Thread.sleep(30000);
                    synchronized (trayAreaLock) {
                        trayarea.put(customerName, trayarea.getOrDefault(customerName, "") + "Coffee, ");
                        System.out.println("Coffee " + (i + 1) + " added to tray area for " + customerName);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread interrupted during coffee brewing for " + customerName);
                    JsonLogger.log("ERROR", " Thread interrupted for brewing coffee for: " +customerName);

                } finally {
                    synchronized (coffeeCounterLock) {
                        brewingCoffees--;
                    }
                }
            }
        });

        teaBrewingThread.start();
        coffeeBrewingThread.start();

        //wait for both tea and coffee brewing threads to finish before updating the tray area - ensures completion
        try {
            teaBrewingThread.join();
            coffeeBrewingThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted while waiting for brewing to complete for " + customerName);
            JsonLogger.log("ERROR", " Thread interrupted while waiting for brewing to complete for: " +customerName);

        }


        // i decided to use objects representing the areas, to ensure thread safety when multiple threads might try to access and modify the shared resources (the hashmaps)
        //only one thread can modify the trayarea and brewingarea at once
        synchronized (brewingAreaLock) {
            synchronized (trayAreaLock) {
                String currentTray = trayarea.getOrDefault(customerName, "Tea: 0, Coffee: 0");
                String brewingStatus = brewingarea.getOrDefault(customerName, "");

                //calculate the final counts of tea and coffee by combining the current tray status and the brewing status - in case they have previous orders not collected
                //i decided to make a countItems as i was having a weird time with the map where it would just be nullTea, tea but countItems safely extracts the correct number of items
                int teaCountFinal = countItems(currentTray, "Tea") + countItems(brewingStatus, "Tea");
                int coffeeCountFinal = countItems(currentTray, "Coffee") + countItems(brewingStatus, "Coffee");

                String trayStatus = "Tea: " + teaCountFinal + ", Coffee: " + coffeeCountFinal;
                trayarea.put(customerName, trayStatus);
                brewingarea.remove(customerName);

                System.out.println("Order moved to tray area: " + trayStatus);
                JsonLogger.log("INFO", " Order moved from brewing to tray area for: " +customerName);

            }
            synchronized (finished) { //set finisheder to true to allow for notifyOrderReady to run - to let the customer know their order is ready - only way i thought to do this idk
                //even though this wasnt the problem as to why it wasnt sending but it works
                finisheder = true;
                System.out.println("Order brewing finished for " + customerName);
            }
        }

    }
// collect order for the customer method, i have finished to show the whole order is finished and this has to be set to false once the order is collected
    // otherwise the your order is ready to collect message will continue showing which wouldnt be true. (ofc synchronised to stop access from other threads)
    public String collectOrder(String customerName) {
        synchronized (trayAreaLock) {
            if (trayarea.containsKey(customerName)) {
                trayarea.remove(customerName);
                synchronized (finished) {
                    finisheder = false;
                }
                JsonLogger.log("INFO", " Order collected for: " +customerName);

                System.out.println("Order collected for customer: " + customerName);
                return "You collected your order.";
            }
        }
        return "No order ready for collection.";
    }

    //shows all the areas, if they dont have anything in the maps, default to showing 0.
    public String getOrderStatus(String customerName) {
        StringBuilder status = new StringBuilder("Order status for " + customerName + ": ");
        synchronized (waitingAreaLock) {
            status.append("Waiting: ").append(waitingarea.getOrDefault(customerName, "0")).append("; ");
        }
        synchronized (brewingAreaLock) {
            status.append("Brewing: ").append(brewingarea.getOrDefault(customerName, "0")).append("; ");
        }
        synchronized (trayAreaLock) {
            status.append("Tray: ").append(trayarea.getOrDefault(customerName, "0")).append(".");
        }
        return status.toString();
    }
}

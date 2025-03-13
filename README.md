# Virtual Cafe - CE303 Assignment - Jennifer Warwick
 
Meant to replicate a real cafe, a Barista who acts as the server listening for connections, orders, and commands from connected clients e.g. 'order 1 coffee and 1 tea' - like in a normal cafe. Preparing the orders and 
brewing them. The customer provides their name to the server before proceeding with any commands, the customer then can order coffee and tea; and collect these orders. The customer can also leave at any time. 
## Key Features

### Clients:
- Order - tea or coffee. Any amount but it will only brew 2 tea/coffee maximum at a time. (Tracked by brewingTeas/Coffees)
- Order status - This will print out the customer's order status.
> Server: Order status: Order status for *customerName*: Waiting: 0; Brewing: 0; Tray: Tea: 1, Coffee: 0.
- Collect - This allows the customer to collect the order after being prepared. If it isn't finished then it will send 
>  No order ready for collection.
- Exit - The customer can exit the cafe (program).
- Java Sockets for network communication, on port 12345.
- InputStream/OutputStream: These are used to read and write data over the socket connection.

### Server: 
- The server has a ClientHandler class to handle the customer's inputs and calls the methods appropriate for the inputs.
- The server also can handle concurrency, with multiple customers as I have used threads and synchronisation to allow for concurrent order processing and brewing, ensuring that multiple clients can interact with the server simultaneously without affecting each other's orders or the overall application performance.
- Brewing of the teas and coffees with individual threads to allow for simultaneous brewing as before I had the coffee only brewing after tea was finished. 
- Clients can receive real-time updates on their order status, including when it's being brewed and when it's ready for pickup.
-  The server includes robust error handling and logging mechanisms to track issues and improve system reliability. It also logs the logs in a JSON file, using GSON.
- Java Sockets for network communication, on port 12345.
- InputStream/OutputStream: These are used to read and write data over the socket connection.
- Barista logs showing the number of clients in the cafe, the number of clients waiting for orders, number, and type of items in the waiting area, brewing area, and tray area.
- If a client leaves the cafe before their order has been completed, it discards the relevant objects from the respective areas.




## Instructions

1. First download the files Cafe2.zip.
2. Locate where the files are, usually I like to right-click and open a GitBash directly from the source folder. Or:
 ```
CD [Path]
```
3. Compile by - IF IT'S NOT ALREADY COMPILED:
```  
javac -cp ".;gson-2.10.1.jar" helpers/*.java *.java
```
4. Once everything is compiled, run this in the bash that is located in the appropriate folder aka *Cafe3Test* folder:
```
java -cp ".;..;gson-2.10.1.jar" Cafe3Test.Barista
```
Everything should run perfectly fine - it did on my PC during testing with this. BUT not *java -cp ".:gson.jar" Barista*

5. The customers should be able to just connect by typing in the directory of the Cafe3Test:
```
java Customer.java
```

## Limitations

- A limitation I found was when compiling that if I didn't specifically do this below then it wouldn't run.
```
java -cp ".;..;gson-2.10.1.jar" Cafe3Test.Barista
```
- Sometimes the *Server: your order is ready for collection, customerName!* appears more than once after completing an order.
- If the customer inputs something like *order 2 peas* it will take it as a valid order - say it's ready, move it into waiting, brewing, and tray but it doesn't get added to the number of teas/coffees in those areas as it isn't tea or coffee. - probably needed some more input sanitisation.

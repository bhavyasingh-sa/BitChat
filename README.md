# BitChat

A Java chat application for a local network. Several laptops connect to one
server and chat in real time.

- **Java (Swing + sockets + threads):** `Chatting Application/`
- **ESP32:** `esp32_bitchat_screen/`, a touchscreen board that creates the BitChat Wi-Fi network the laptops join, so no router or internet is needed. The chat itself is all Java.

## How it works

| Class | Job |
|---|---|
| `Server` | Console program. Listens on port 6001, accepts clients, keeps the list of connected clients. |
| `ClientHandler` | `implements Runnable`. One thread per client. Reads its messages and passes them to the others. |
| `Client` | The Swing chat window. Asks for a username and the server IP, then sends and receives messages. |

Messages are sent with `writeUTF` / `readUTF`.

## Run it

Needs Java (JDK 11 or newer). Join all laptops to the same network: the **BitChat** Wi-Fi made by the ESP32 (see below), or any router or phone hotspot.

```bash
cd "Chatting Application"
javac -d ~/bitchat_build src/Server/*.java
```

Start the server on one laptop. It prints the addresses it can be reached on:

```bash
java -cp ~/bitchat_build Server.Server
```

Start a client on every laptop, then type a username and the server's IP address:

```bash
java -cp ~/bitchat_build Server.Client
```

(Or open the folder in Eclipse and run `Server.java` / `Client.java`.)

## ESP32 network

The ESP32 acts as the network for the chat. We used it to learn how a Wi-Fi access
point and a touchscreen work on a microcontroller, and built a small app for it.

`esp32_bitchat_screen/esp32_bitchat_screen.ino` is for a 3.5" ESP32 board with an
ST7796 touchscreen. Tap START to create the **BitChat** Wi-Fi network; the screen
shows the name, password, IP address and how many devices are connected. The BOOT
button on the board also starts and stops the network.
Needs the ESP32 board package and the `LovyanGFX` library in the Arduino IDE
(board: ESP32 Dev Module).

Hardware notes for this board:

- The touch chip is resistive (XPT2046). It shares the display's SPI pins (14, 13, 12) and uses pin 33 as its chip-select. Its IRQ pin is not wired, so touch is read from pressure.
- The touch X axis is mirrored compared to the screen, so `TOUCH_OFFSET_ROT` is set to 2.
- Laptops join the **BitChat** Wi-Fi, then the Java server prints an address like `192.168.4.x` for the clients to use.

package net.augustus.irc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight embedded TCP + LAN-discovery hub allowing local multi-instance and LAN/VPN peers
 * to share Augustus IRC presence and chat without needing external server infrastructure.
 */
final class IrcLocalHubServer {
   static final String DISCOVERY_QUERY = "AUGUSTUS_IRC_DISCOVER";
   static final String DISCOVERY_REPLY_PREFIX = "AUGUSTUS_IRC_HERE:";

   private final int port;
   private final CopyOnWriteArrayList<ClientConn> clients = new CopyOnWriteArrayList<ClientConn>();
   private final Map<String, String> presenceByNode = new ConcurrentHashMap<String, String>();
   private volatile boolean running;
   private ServerSocket serverSocket;
   private DatagramSocket udpSocket;
   private Thread acceptThread;
   private Thread discoveryThread;

   IrcLocalHubServer(int port) {
      this.port = port;
   }

   synchronized boolean start() {
      if (this.running) {
         return true;
      }
      try {
         this.serverSocket = new ServerSocket(this.port);
         this.running = true;
      } catch (IOException e) {
         return false;
      }

      this.acceptThread = new Thread(() -> this.acceptLoop(), "Augustus-IRC-Hub-Accept");
      this.acceptThread.setDaemon(true);
      this.acceptThread.start();

      this.discoveryThread = new Thread(() -> this.discoveryLoop(), "Augustus-IRC-Hub-Discovery");
      this.discoveryThread.setDaemon(true);
      this.discoveryThread.start();
      return true;
   }

   synchronized void stop() {
      this.running = false;
      try {
         if (this.serverSocket != null) {
            this.serverSocket.close();
         }
      } catch (IOException ignored) {
      }
      if (this.udpSocket != null) {
         this.udpSocket.close();
      }
      for (ClientConn conn : this.clients) {
         conn.close();
      }
      this.clients.clear();
      this.presenceByNode.clear();
   }

   boolean isRunning() {
      return this.running;
   }

   private void acceptLoop() {
      while (this.running) {
         try {
            Socket socket = this.serverSocket.accept();
            socket.setTcpNoDelay(true);
            ClientConn conn = new ClientConn(socket);
            this.clients.add(conn);
            Thread reader = new Thread(() -> this.readClientLoop(conn), "Augustus-IRC-Hub-Client");
            reader.setDaemon(true);
            reader.start();
         } catch (IOException e) {
            if (!this.running) {
               break;
            }
         }
      }
   }

   private void discoveryLoop() {
      try {
         this.udpSocket = new DatagramSocket(this.port + 1);
         this.udpSocket.setBroadcast(true);
         byte[] buf = new byte[256];
         while (this.running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            this.udpSocket.receive(packet);
            String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
            if (DISCOVERY_QUERY.equals(msg)) {
               byte[] reply = (DISCOVERY_REPLY_PREFIX + this.port).getBytes(StandardCharsets.UTF_8);
               DatagramPacket response = new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort());
               this.udpSocket.send(response);
            }
         }
      } catch (Exception ignored) {
      }
   }

   private void readClientLoop(ClientConn conn) {
      try {
         // Replay known presence lines immediately so the newly joined peer sees everyone online.
         for (String line : this.presenceByNode.values()) {
            conn.send(line);
         }

         String line;
         while (this.running && (line = conn.reader.readLine()) != null) {
            if (line.isEmpty()) {
               continue;
            }
            if (line.startsWith("AUG1|PRESENCE|")) {
               String nodeId = extractField(line, 2);
               if (!nodeId.isEmpty()) {
                  conn.nodeId = nodeId;
                  this.presenceByNode.put(nodeId, line);
               }
               this.broadcast(line);
            } else if (line.startsWith("AUG1|MSG|")) {
               this.broadcast(line);
            } else if (line.startsWith("AUG1|BYE|")) {
               String nodeId = extractField(line, 2);
               if (!nodeId.isEmpty()) {
                  this.presenceByNode.remove(nodeId);
               }
               this.broadcast(line);
            } else if ("AUG1|PING".equals(line)) {
               conn.send("AUG1|PONG");
            }
         }
      } catch (IOException ignored) {
      } finally {
         this.clients.remove(conn);
         conn.close();
         if (conn.nodeId != null && !conn.nodeId.isEmpty()) {
            this.presenceByNode.remove(conn.nodeId);
            this.broadcast("AUG1|BYE|" + conn.nodeId);
         }
      }
   }

   private void broadcast(String line) {
      for (ClientConn client : this.clients) {
         if (!client.send(line)) {
            this.clients.remove(client);
            client.close();
         }
      }
   }

   private static String extractField(String line, int index) {
      String[] parts = line.split("\\|", -1);
      return index >= 0 && index < parts.length ? parts[index] : "";
   }

   static String discoverLanHub(int hubPort, int timeoutMs) {
      try (DatagramSocket sock = new DatagramSocket()) {
         sock.setBroadcast(true);
         sock.setSoTimeout(timeoutMs);
         byte[] query = DISCOVERY_QUERY.getBytes(StandardCharsets.UTF_8);
         DatagramPacket pkt = new DatagramPacket(query, query.length, InetAddress.getByName("255.255.255.255"), hubPort + 1);
         sock.send(pkt);
         byte[] respBuf = new byte[128];
         DatagramPacket resp = new DatagramPacket(respBuf, respBuf.length);
         sock.receive(resp);
         String text = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8).trim();
         if (text.startsWith(DISCOVERY_REPLY_PREFIX)) {
            return resp.getAddress().getHostAddress();
         }
      } catch (Exception ignored) {
      }
      return null;
   }

   private static final class ClientConn {
      private final Socket socket;
      private final BufferedReader reader;
      private final BufferedWriter writer;
      private volatile String nodeId = "";

      ClientConn(Socket socket) throws IOException {
         this.socket = socket;
         this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
      }

      synchronized boolean send(String line) {
         try {
            this.writer.write(line);
            this.writer.newLine();
            this.writer.flush();
            return true;
         } catch (IOException e) {
            return false;
         }
      }

      void close() {
         try {
            this.socket.close();
         } catch (IOException ignored) {
         }
      }
   }
}

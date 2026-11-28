package net.augustus.irc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Dual-mode IRC transport supporting standard RFC-1459 TCP/TLS IRC servers and
 * zero-config Augustus Hub TCP connections (local / LAN / port-forwarded peers).
 */
final class IrcNetworkClient {
   enum State {
      DISCONNECTED("Desconectado"),
      CONNECTING("Conectando..."),
      CONNECTED_EXTERNAL("Conectado (IRC)"),
      CONNECTED_HUB_HOST("Activo (Hub Host)"),
      CONNECTED_HUB_PEER("Conectado (Hub)");

      private final String label;

      State(String label) {
         this.label = label;
      }

      String getLabel() {
         return this.label;
      }
   }

   private final IrcManager manager;
   private final Map<String, String> ircNickToNodeId = new ConcurrentHashMap<String, String>();
   private volatile State state = State.DISCONNECTED;
   private volatile boolean desiredRunning = false;
   private volatile boolean externalMode = false;
   private volatile String activeServerLabel = "";
   private volatile Socket socket;
   private volatile BufferedWriter writer;
   private volatile IrcLocalHubServer localHub;
   private Thread workerThread;
   private long lastPresencePushMs = 0L;
   private long lastReconnectTryMs = 0L;

   IrcNetworkClient(IrcManager manager) {
      this.manager = manager;
   }

   State getState() {
      return this.state;
   }

   String getActiveServerLabel() {
      return this.activeServerLabel;
   }

   synchronized void start() {
      if (this.desiredRunning) {
         return;
      }
      this.desiredRunning = true;
      this.workerThread = new Thread(() -> this.runSupervisorLoop(), "Augustus-IRC-Client");
      this.workerThread.setDaemon(true);
      this.workerThread.start();
   }

   synchronized void restart() {
      this.stop();
      this.start();
   }

   synchronized void stop() {
      this.desiredRunning = false;
      this.sendQuitQuietly();
      this.closeConnectionOnly();
      if (this.localHub != null) {
         this.localHub.stop();
         this.localHub = null;
      }
      this.state = State.DISCONNECTED;
      this.activeServerLabel = "";
      if (this.workerThread != null) {
         this.workerThread.interrupt();
         this.workerThread = null;
      }
   }

   void tickHeartbeat(IrcUserRecord local) {
      if (!this.desiredRunning || local == null) {
         return;
      }
      long now = System.currentTimeMillis();
      long interval = this.externalMode ? 20_000L : 4_000L;
      if (this.writer != null && now - this.lastPresencePushMs >= interval) {
         this.lastPresencePushMs = now;
         this.sendPresence(local);
      }
   }

   boolean sendChat(IrcUserRecord local, String message) {
      if (message == null || message.trim().isEmpty()) {
         return false;
      }
      String clean = message.replace('\r', ' ').replace('\n', ' ').trim();
      if (clean.length() > 360) {
         clean = clean.substring(0, 360);
      }

      if (this.writer == null) {
         return false;
      }

      if (this.externalMode) {
         String channel = this.manager.getConfig().getIrcChannel();
         String payload = encodeExternalTag(local) + clean;
         return this.writeRawLine("PRIVMSG " + channel + " :" + payload);
      }

      String b64Msg = Base64.getEncoder().encodeToString(clean.getBytes(StandardCharsets.UTF_8));
      String origB64 = Base64.getEncoder().encodeToString(local.getDisplayOriginalNick().getBytes(StandardCharsets.UTF_8));
      String line = "AUG1|MSG|"
         + local.getNodeId()
         + "|"
         + origB64
         + "|"
         + local.getLevel()
         + "|"
         + (local.isPremiumLinked() ? "1" : "0")
         + "|"
         + b64Msg;
      return this.writeRawLine(line);
   }

   void sendPresenceNow(IrcUserRecord local) {
      if (local != null && this.writer != null) {
         this.lastPresencePushMs = System.currentTimeMillis();
         this.sendPresence(local);
      }
   }

   private void runSupervisorLoop() {
      while (this.desiredRunning) {
         long now = System.currentTimeMillis();
         if (now - this.lastReconnectTryMs < 2_500L) {
            sleepQuietly(400L);
            continue;
         }
         this.lastReconnectTryMs = now;

         IrcConfigFile cfg = this.manager.getConfig();
         if (!cfg.isIrcEnabled()) {
            this.state = State.DISCONNECTED;
            sleepQuietly(1_000L);
            continue;
         }

         String host = cfg.getIrcHost();
         String mode = cfg.getTransportMode();
         boolean wantExternal = "External IRC".equalsIgnoreCase(mode)
            || ("Auto".equalsIgnoreCase(mode) && host != null && !host.trim().isEmpty());

         try {
            if (wantExternal && host != null && !host.trim().isEmpty()) {
               this.runExternalIrcSession(host.trim(), cfg.getIrcPort(), cfg.isIrcUseTls(), cfg.getIrcChannel(), cfg.getIrcPassword());
            } else {
               this.runHubSession(cfg.getLocalHubPort());
            }
         } catch (Exception ignored) {
         } finally {
            this.closeConnectionOnly();
            this.ircNickToNodeId.clear();
            this.manager.clearRemoteUsers();
            if (this.desiredRunning) {
               this.state = State.DISCONNECTED;
            }
         }
      }
   }

   private void runHubSession(int hubPort) throws IOException {
      this.externalMode = false;
      this.state = State.CONNECTING;

      String targetHost = "127.0.0.1";
      boolean hostedLocally = false;

      if (!this.tryConnectTcp(targetHost, hubPort, 700)) {
         String lanHost = IrcLocalHubServer.discoverLanHub(hubPort, 350);
         if (lanHost != null && this.tryConnectTcp(lanHost, hubPort, 1200)) {
            targetHost = lanHost;
         } else {
            if (this.localHub == null || !this.localHub.isRunning()) {
               this.localHub = new IrcLocalHubServer(hubPort);
               hostedLocally = this.localHub.start();
            } else {
               hostedLocally = true;
            }
            if (!this.tryConnectTcp("127.0.0.1", hubPort, 1500)) {
               return;
            }
            targetHost = "127.0.0.1";
         }
      }

      this.state = hostedLocally ? State.CONNECTED_HUB_HOST : State.CONNECTED_HUB_PEER;
      this.activeServerLabel = targetHost + ":" + hubPort;
      this.sendPresenceNow(this.manager.getLocalUser());

      BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
      String line;
      while (this.desiredRunning && (line = reader.readLine()) != null) {
         this.handleHubLine(line);
      }
   }

   private void runExternalIrcSession(String host, int port, boolean useTls, String channel, String password) throws IOException {
      this.externalMode = true;
      this.state = State.CONNECTING;
      this.activeServerLabel = host + ":" + port + " (" + channel + ")";

      Socket sock;
      if (useTls) {
         SSLSocket ssl = (SSLSocket)SSLSocketFactory.getDefault().createSocket();
         ssl.connect(new InetSocketAddress(host, port), 8000);
         ssl.startHandshake();
         sock = ssl;
      } else {
         sock = new Socket();
         sock.connect(new InetSocketAddress(host, port), 8000);
      }
      sock.setSoTimeout(90_000);
      sock.setTcpNoDelay(true);
      this.socket = sock;
      this.writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));

      IrcUserRecord local = this.manager.getLocalUser();
      String baseNick = sanitizeIrcNick(local.getDisplayOriginalNick());
      if (password != null && !password.isEmpty()) {
         this.writeRawLine("PASS " + password);
      }
      this.writeRawLine("NICK " + baseNick);
      this.writeRawLine("USER aug 0 * :Augustus Redesing User");

      BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
      int nickAttempt = 0;
      String line;
      while (this.desiredRunning && (line = reader.readLine()) != null) {
         if (line.startsWith("PING ")) {
            this.writeRawLine("PONG " + line.substring(5));
            continue;
         }

         String[] tokens = line.split(" ", 4);
         String cmd = tokens.length >= 2 ? tokens[1] : "";
         if ("433".equals(cmd) && nickAttempt < 5) {
            nickAttempt++;
            this.writeRawLine("NICK " + baseNick + "_" + ((int)(System.currentTimeMillis() % 900L) + 100));
            continue;
         }
         if ("001".equals(cmd)) {
            this.writeRawLine("JOIN " + channel);
            this.state = State.CONNECTED_EXTERNAL;
            this.sendPresenceNow(this.manager.getLocalUser());
            continue;
         }
         this.handleExternalLine(line, channel);
      }
   }

   private boolean tryConnectTcp(String host, int port, int timeoutMs) {
      try {
         Socket sock = new Socket();
         sock.connect(new InetSocketAddress(host, port), timeoutMs);
         sock.setTcpNoDelay(true);
         this.socket = sock;
         this.writer = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));
         return true;
      } catch (IOException e) {
         return false;
      }
   }

   private void handleHubLine(String line) {
      if (line == null || line.isEmpty() || "AUG1|PONG".equals(line)) {
         return;
      }
      String[] p = line.split("\\|", -1);
      if (p.length < 3 || !"AUG1".equals(p[0])) {
         return;
      }
      String kind = p[1];
      String nodeId = p[2];
      IrcUserRecord local = this.manager.getLocalUser();
      if (local != null && nodeId.equals(local.getNodeId())) {
         return;
      }

      if ("BYE".equals(kind)) {
         this.manager.removeRemoteUser(nodeId);
         return;
      }

      if ("PRESENCE".equals(kind) && p.length >= 9) {
         String orig = decodeB64(p[3]);
         String cur = decodeB64(p[4]);
         String uuid = p[5];
         boolean premium = "1".equals(p[6]);
         int level = parseIntSafe(p[7], 1);
         long playMs = parseLongSafe(p[8], 0L);
         String srv = p.length >= 10 ? decodeB64(p[9]) : "Online";
         this.manager.upsertRemoteUser(nodeId, orig, cur, uuid, premium, level, playMs, srv);
      } else if ("MSG".equals(kind) && p.length >= 7) {
         String orig = decodeB64(p[3]);
         int level = parseIntSafe(p[4], 1);
         boolean premium = "1".equals(p[5]);
         String content = decodeB64(p[6]);
         this.manager.onIncomingRemoteMessage(new IrcChatMessage(System.currentTimeMillis(), orig, level, premium, content, false));
      }
   }

   private void handleExternalLine(String raw, String channel) {
      if (!raw.startsWith(":")) {
         return;
      }
      int excl = raw.indexOf('!');
      int space = raw.indexOf(' ');
      if (space <= 1) {
         return;
      }
      String senderIrcNick = (excl > 1 && excl < space) ? raw.substring(1, excl) : raw.substring(1, space);
      String rest = raw.substring(space + 1);
      String[] parts = rest.split(" ", 3);
      if (parts.length < 2) {
         return;
      }
      String cmd = parts[0];
      String target = parts[1];
      String trailing = parts.length >= 3 && parts[2].startsWith(":") ? parts[2].substring(1) : (parts.length >= 3 ? parts[2] : "");

      if ("PART".equalsIgnoreCase(cmd) || "QUIT".equalsIgnoreCase(cmd)) {
         String mappedId = this.ircNickToNodeId.remove(senderIrcNick);
         if (mappedId != null) {
            this.manager.removeRemoteUser(mappedId);
         }
         return;
      }

      if (!target.equalsIgnoreCase(channel)) {
         return;
      }

      if ("NOTICE".equalsIgnoreCase(cmd) && trailing.startsWith("\u0001AUG ")) {
         String b64 = trailing.substring(5).replace("\u0001", "").trim();
         this.applyPresenceJson(senderIrcNick, decodeB64(b64));
         return;
      }

      if ("PRIVMSG".equalsIgnoreCase(cmd)) {
         String displayNick = senderIrcNick;
         int level = 1;
         boolean premium = false;
         String messageText = trailing;

         if (trailing.startsWith("[AUG:") && trailing.contains("] ")) {
            int end = trailing.indexOf("] ");
            String meta = decodeB64(trailing.substring(5, end));
            messageText = trailing.substring(end + 2);
            try {
               JsonObject obj = JsonParser.parseString(meta).getAsJsonObject();
               if (obj.has("o")) {
                  displayNick = obj.get("o").getAsString();
               }
               if (obj.has("l")) {
                  level = obj.get("l").getAsInt();
               }
               if (obj.has("p")) {
                  premium = obj.get("p").getAsBoolean();
               }
               this.applyPresenceJson(senderIrcNick, meta);
            } catch (Exception ignored) {
            }
         } else {
            String mapped = this.ircNickToNodeId.get(senderIrcNick);
            IrcUserRecord known = mapped != null ? this.manager.getRemoteUser(mapped) : null;
            if (known != null) {
               displayNick = known.getDisplayOriginalNick();
               level = known.getLevel();
               premium = known.isPremiumLinked();
            }
         }

         this.manager.onIncomingRemoteMessage(new IrcChatMessage(System.currentTimeMillis(), displayNick, level, premium, messageText, false));
      }
   }

   private void applyPresenceJson(String senderIrcNick, String jsonStr) {
      if (jsonStr == null || jsonStr.isEmpty()) {
         return;
      }
      try {
         JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
         String nodeId = obj.has("id") ? obj.get("id").getAsString() : senderIrcNick;
         IrcUserRecord local = this.manager.getLocalUser();
         if (local != null && nodeId.equals(local.getNodeId())) {
            return;
         }
         String orig = obj.has("o") ? obj.get("o").getAsString() : senderIrcNick;
         String cur = obj.has("c") ? obj.get("c").getAsString() : orig;
         String uuid = obj.has("u") ? obj.get("u").getAsString() : "";
         boolean prem = obj.has("p") && obj.get("p").getAsBoolean();
         int level = obj.has("l") ? obj.get("l").getAsInt() : 1;
         long ms = obj.has("t") ? obj.get("t").getAsLong() : 0L;
         String srv = obj.has("s") ? obj.get("s").getAsString() : "Online";
         this.ircNickToNodeId.put(senderIrcNick, nodeId);
         this.manager.upsertRemoteUser(nodeId, orig, cur, uuid, prem, level, ms, srv);
      } catch (Exception ignored) {
      }
   }

   private void sendPresence(IrcUserRecord local) {
      if (this.externalMode) {
         String channel = this.manager.getConfig().getIrcChannel();
         String b64 = encodePresenceJsonB64(local);
         this.writeRawLine("NOTICE " + channel + " :\u0001AUG " + b64 + "\u0001");
         return;
      }

      String origB64 = Base64.getEncoder().encodeToString(local.getDisplayOriginalNick().getBytes(StandardCharsets.UTF_8));
      String curB64 = Base64.getEncoder().encodeToString(local.getCurrentNick().getBytes(StandardCharsets.UTF_8));
      String srvB64 = Base64.getEncoder().encodeToString(local.getServerContext().getBytes(StandardCharsets.UTF_8));
      String line = "AUG1|PRESENCE|"
         + local.getNodeId()
         + "|"
         + origB64
         + "|"
         + curB64
         + "|"
         + local.getUuid()
         + "|"
         + (local.isPremiumLinked() ? "1" : "0")
         + "|"
         + local.getLevel()
         + "|"
         + local.getPlayTimeMillis()
         + "|"
         + srvB64;
      this.writeRawLine(line);
   }

   private static String encodeExternalTag(IrcUserRecord local) {
      return "[AUG:" + encodePresenceJsonB64(local) + "] ";
   }

   private static String encodePresenceJsonB64(IrcUserRecord local) {
      JsonObject obj = new JsonObject();
      obj.addProperty("id", local.getNodeId());
      obj.addProperty("o", local.getDisplayOriginalNick());
      obj.addProperty("c", local.getCurrentNick());
      obj.addProperty("u", local.getUuid());
      obj.addProperty("p", local.isPremiumLinked());
      obj.addProperty("l", local.getLevel());
      obj.addProperty("t", local.getPlayTimeMillis());
      obj.addProperty("s", local.getServerContext());
      return Base64.getEncoder().encodeToString(obj.toString().getBytes(StandardCharsets.UTF_8));
   }

   private void sendQuitQuietly() {
      if (this.writer == null) {
         return;
      }
      if (this.externalMode) {
         this.writeRawLine("QUIT :Augustus Redesing");
      } else {
         IrcUserRecord local = this.manager.getLocalUser();
         if (local != null) {
            this.writeRawLine("AUG1|BYE|" + local.getNodeId());
         }
      }
   }

   private synchronized boolean writeRawLine(String line) {
      if (this.writer == null) {
         return false;
      }
      try {
         this.writer.write(line);
         this.writer.newLine();
         this.writer.flush();
         return true;
      } catch (IOException e) {
         return false;
      }
   }

   private void closeConnectionOnly() {
      this.writer = null;
      if (this.socket != null) {
         try {
            this.socket.close();
         } catch (IOException ignored) {
         }
         this.socket = null;
      }
   }

   private static String sanitizeIrcNick(String nick) {
      StringBuilder sb = new StringBuilder();
      if (nick != null) {
         for (int i = 0; i < nick.length() && sb.length() < 14; i++) {
            char c = nick.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
               sb.append(c);
            }
         }
      }
      if (sb.length() == 0 || Character.isDigit(sb.charAt(0))) {
         sb.insert(0, "Aug");
      }
      return sb.toString();
   }

   private static String decodeB64(String raw) {
      if (raw == null || raw.isEmpty()) {
         return "";
      }
      try {
         return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
      } catch (Exception e) {
         return "";
      }
   }

   private static int parseIntSafe(String s, int fallback) {
      try {
         return Integer.parseInt(s);
      } catch (Exception e) {
         return fallback;
      }
   }

   private static long parseLongSafe(String s, long fallback) {
      try {
         return Long.parseLong(s);
      } catch (Exception e) {
         return fallback;
      }
   }

   private static void sleepQuietly(long ms) {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException ignored) {
      }
   }
}

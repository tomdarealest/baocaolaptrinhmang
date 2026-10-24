package Btapbaocao;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

/**
 * FTP Server co ban voi 2 kenh:
 *  - Kenh dieu khien (Control): cong 2121, nhan lenh USER, PASS, LIST, RETR, STOR, PASV, PORT, QUIT
 *  - Kenh du lieu (Data): mo rieng cho moi lan LIST/RETR/STOR (Active hoac Passive)
 */
public class FTPServer {

    public static final int CONTROL_PORT = 2121;
    // Dai cong cho kenh du lieu Passive (mo san trong Firewall)
    public static final int PASV_MIN = 50000;
    public static final int PASV_MAX = 50100;
    public static File ROOT = new File("ftp_root");   // thu muc chia se
    public static final Map<String, String> USERS = new HashMap<>();

    public static volatile boolean running = false;
    public static ServerSocket currentServer = null;
    public static java.util.function.Consumer<String> logger = null;
    public static Runnable clientListener = null;
    public static final java.util.List<ClientHandler> activeClients = new java.util.concurrent.CopyOnWriteArrayList<>();

    static {
        USERS.put("admin", "123456");
        USERS.put("user", "user");
    }

    public static void log(String msg) {
        System.out.println(msg);
        if (logger != null) {
            try {
                logger.accept(msg);
            } catch (Exception ignored) {
            }
        }
    }

    public static void startServer(int port, File rootFolder) throws IOException {
        if (running) {
            return;
        }
        ROOT = rootFolder != null ? rootFolder : new File("ftp_root");
        if (!ROOT.exists()) {
            ROOT.mkdirs();
        }
        currentServer = new ServerSocket(port);
        running = true;
        log("FTP Server dang chay tai cong " + port);
        log("Thu muc chia se: " + ROOT.getAbsolutePath());

        new Thread(() -> {
            try {
                while (running && currentServer != null && !currentServer.isClosed()) {
                    Socket client = currentServer.accept();
                    ClientHandler handler = new ClientHandler(client);
                    activeClients.add(handler);
                    if (clientListener != null) {
                        clientListener.run();
                    }
                    log("Client ket noi: " + client.getRemoteSocketAddress());
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                if (running) {
                    log("Loi Server: " + e.getMessage());
                }
            } finally {
                stopServer();
            }
        }, "FTPServer-Acceptor").start();
    }

    public static void stopServer() {
        running = false;
        if (currentServer != null && !currentServer.isClosed()) {
            try {
                currentServer.close();
            } catch (IOException ignored) {
            }
        }
        currentServer = null;
        for (ClientHandler ch : activeClients) {
            ch.disconnect();
        }
        activeClients.clear();
        if (clientListener != null) {
            clientListener.run();
        }
        log("FTP Server da dung.");
    }

    public static void main(String[] args) throws IOException {
        startServer(CONTROL_PORT, ROOT);
        printIPs();
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /** In ra cac dia chi IP cua may nay (IP Radmin thuong bat dau bang 26.) */
    static void printIPs() {
        try {
            System.out.println("Dia chi IP cua may nay (dung IP Radmin 26.x.x.x cho client o may khac):");
            for (java.util.Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (java.util.Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
                    InetAddress ia = a.nextElement();
                    if (ia instanceof Inet4Address) {
                        String tag = ia.getHostAddress().startsWith("26.") ? "   <== Radmin" : "";
                        System.out.println("  " + ia.getHostAddress() + "  (" + ni.getDisplayName() + ")" + tag);
                    }
                }
            }
        } catch (SocketException ignored) {
        }
    }

    /** Mo ServerSocket tren cong ban dau tien con trong trong dai [min, max]. */
    static ServerSocket openPort(int min, int max) throws IOException {
        for (int p = min; p <= max; p++) {
            try {
                return new ServerSocket(p);
            } catch (IOException ignored) {
            }
        }
        throw new IOException("Khong con cong trong");
    }
}

class ClientHandler implements Runnable {

    private final Socket ctrl;
    private final java.util.Date connectedTime = new java.util.Date();
    private BufferedReader in;
    private PrintWriter out;

    private String pendingUser = null;
    private boolean loggedIn = false;

    // Passive mode: server mo cong du lieu cho client ket noi vao
    private ServerSocket pasvServer = null;
    // Active mode: client cho san cong, server ket noi toi
    private String activeHost = null;
    private int activePort = -1;

    ClientHandler(Socket ctrl) {
        this.ctrl = ctrl;
    }

    public String getUsername() {
        return loggedIn ? pendingUser : "(Chưa đăng nhập)";
    }

    public Socket getSocket() {
        return ctrl;
    }

    public java.util.Date getConnectedTime() {
        return connectedTime;
    }

    public void disconnect() {
        closePasv();
        try {
            if (ctrl != null && !ctrl.isClosed()) {
                ctrl.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void reply(String msg) {
        out.println(msg);
        FTPServer.log("  -> " + msg);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(ctrl.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(ctrl.getOutputStream()), true);
            reply("220 Welcome to simple FTP server");

            String line;
            while ((line = in.readLine()) != null) {
                FTPServer.log("<- " + line);
                String[] p = line.trim().split("\\s+", 2);
                String cmd = p[0].toUpperCase();
                String arg = p.length > 1 ? p[1] : "";

                if (cmd.equals("QUIT")) {
                    reply("221 Goodbye");
                    break;
                }
                handle(cmd, arg);
            }
        } catch (IOException e) {
            FTPServer.log("Loi client: " + e.getMessage());
        } finally {
            closePasv();
            disconnect();
            FTPServer.activeClients.remove(this);
            if (FTPServer.clientListener != null) {
                FTPServer.clientListener.run();
            }
            FTPServer.log("Client ngat ket noi: " + ctrl.getRemoteSocketAddress());
        }
    }

    private void handle(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "USER":
                pendingUser = arg;
                loggedIn = false;
                reply("331 Username ok, need password");
                return;
            case "PASS":
                if (pendingUser != null && arg.equals(FTPServer.USERS.get(pendingUser))) {
                    loggedIn = true;
                    if (FTPServer.clientListener != null) {
                        FTPServer.clientListener.run();
                    }
                    reply("230 Login successful");
                } else {
                    reply("530 Login incorrect");
                }
                return;
        }

        if (!loggedIn) {
            reply("530 Please login with USER and PASS");
            return;
        }

        switch (cmd) {
            case "PASV":
                closePasv();
                activeHost = null;
                try {
                    pasvServer = FTPServer.openPort(FTPServer.PASV_MIN, FTPServer.PASV_MAX);
                } catch (IOException e) {
                    reply("425 Cannot open passive port");
                    break;
                }
                int port = pasvServer.getLocalPort();
                String ip = ctrl.getLocalAddress().getHostAddress().replace('.', ',');
                reply("227 Entering Passive Mode (" + ip + "," + (port / 256) + "," + (port % 256) + ")");
                break;
            case "PORT":
                try {
                    String[] n = arg.split(",");
                    activeHost = n[0] + "." + n[1] + "." + n[2] + "." + n[3];
                    activePort = Integer.parseInt(n[4].trim()) * 256 + Integer.parseInt(n[5].trim());
                    closePasv();
                    reply("200 PORT command successful");
                } catch (Exception e) {
                    reply("501 Syntax error in PORT");
                }
                break;
            case "LIST":
                doList();
                break;
            case "RETR":
                doRetr(arg);
                break;
            case "STOR":
                doStor(arg);
                break;
            case "DELE":
                doDele(arg);
                break;
            case "SIZE":
                doSize(arg);
                break;
            case "NOOP":
                reply("200 NOOP ok");
                break;
            default:
                reply("502 Command not implemented");
        }
    }

    private void doDele(String name) {
        if (name == null || name.trim().isEmpty()) {
            reply("501 Missing filename");
            return;
        }
        File f = new File(FTPServer.ROOT, new File(name).getName());
        if (!f.exists() || !f.isFile()) {
            reply("550 File not found");
            return;
        }
        if (f.delete()) {
            reply("250 File deleted successfully");
        } else {
            reply("450 Cannot delete file");
        }
    }

    private void doSize(String name) {
        if (name == null || name.trim().isEmpty()) {
            reply("501 Missing filename");
            return;
        }
        File f = new File(FTPServer.ROOT, new File(name).getName());
        if (!f.exists() || !f.isFile()) {
            reply("550 File not found");
            return;
        }
        reply("213 " + f.length());
    }

    /** Mo kenh du lieu theo che do Passive hoac Active. */
    private Socket openDataSocket() throws IOException {
        if (pasvServer != null) {
            pasvServer.setSoTimeout(15000);
            Socket s = pasvServer.accept();
            closePasv();
            return s;
        }
        if (activeHost != null) {
            Socket s = new Socket(activeHost, activePort);
            activeHost = null;
            return s;
        }
        return null;
    }

    private void closePasv() {
        if (pasvServer != null) {
            try {
                pasvServer.close();
            } catch (IOException ignored) {
            }
            pasvServer = null;
        }
    }

    private boolean hasDataMode() {
        return pasvServer != null || activeHost != null;
    }

    private void doList() throws IOException {
        if (!hasDataMode()) {
            reply("425 Use PASV or PORT first");
            return;
        }
        reply("150 Opening data connection for directory listing");
        try (Socket data = openDataSocket();
                PrintWriter dw = new PrintWriter(new OutputStreamWriter(data.getOutputStream()), true)) {
            File[] files = FTPServer.ROOT.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        dw.println(f.getName() + "\t" + f.length() + " bytes");
                    }
                }
            }
        } catch (IOException e) {
            reply("425 Cannot open data connection");
            return;
        }
        reply("226 Transfer complete");
    }

    private void doRetr(String name) throws IOException {
        File f = new File(FTPServer.ROOT, new File(name).getName());   // chong ../
        if (!f.isFile()) {
            reply("550 File not found");
            return;
        }
        if (!hasDataMode()) {
            reply("425 Use PASV or PORT first");
            return;
        }
        reply("150 Opening data connection for " + f.getName());
        try (Socket data = openDataSocket();
                InputStream fin = new FileInputStream(f);
                OutputStream dout = data.getOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fin.read(buf)) != -1) {
                dout.write(buf, 0, n);
            }
            dout.flush();
        } catch (IOException e) {
            reply("426 Transfer aborted");
            return;
        }
        reply("226 Transfer complete");
    }

    private void doStor(String name) throws IOException {
        if (name.isEmpty()) {
            reply("501 Missing filename");
            return;
        }
        if (!hasDataMode()) {
            reply("425 Use PASV or PORT first");
            return;
        }
        File f = new File(FTPServer.ROOT, new File(name).getName());
        reply("150 Ready to receive " + f.getName());
        try (Socket data = openDataSocket();
                InputStream din = data.getInputStream();
                OutputStream fout = new FileOutputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = din.read(buf)) != -1) {
                fout.write(buf, 0, n);
            }
        } catch (IOException e) {
            reply("426 Transfer aborted");
            return;
        }
        reply("226 Transfer complete");
    }
}
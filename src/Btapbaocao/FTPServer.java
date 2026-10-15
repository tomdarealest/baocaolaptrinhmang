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
    public static final File ROOT = new File("ftp_root");   // thu muc chia se
    public static final Map<String, String> USERS = new HashMap<>();

    public static void main(String[] args) throws IOException {
        USERS.put("admin", "123456");
        USERS.put("user", "user");
        if (!ROOT.exists()) {
            ROOT.mkdirs();
        }

        try (ServerSocket server = new ServerSocket(CONTROL_PORT)) {
            System.out.println("FTP Server dang chay tai cong " + CONTROL_PORT);
            System.out.println("Thu muc chia se: " + ROOT.getAbsolutePath());
            printIPs();
            while (true) {
                Socket client = server.accept();
                System.out.println("Client ket noi: " + client.getRemoteSocketAddress());
                new Thread(new ClientHandler(client)).start();
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

    private void reply(String msg) {
        out.println(msg);
        System.out.println("  -> " + msg);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(ctrl.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(ctrl.getOutputStream()), true);
            reply("220 Welcome to simple FTP server");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("<- " + line);
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
            System.out.println("Loi client: " + e.getMessage());
        } finally {
            closePasv();
            try {
                ctrl.close();
            } catch (IOException ignored) {
            }
            System.out.println("Client ngat ket noi");
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
            default:
                reply("502 Command not implemented");
        }
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
package Btapbaocao;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * FTP Client dong lenh (console) voi 2 kenh:
 *  - Kenh dieu khien: gui USER, PASS, LIST, RETR, STOR, PASV, PORT
 *  - Kenh du lieu: mo rieng cho moi lan truyen (Passive hoac Active)
 */
public class FTPClient {

    private Socket ctrl;
    private BufferedReader in;
    private PrintWriter out;
    private boolean passive = true;

    private static final int DEFAULT_CONTROL_PORT = 2121;
    // Dai cong cho kenh du lieu Active (mo san trong Firewall cua may client)
    private static final int ACTIVE_MIN = 50200;
    private static final int ACTIVE_MAX = 50300;

    private interface DataHandler {
        void handle(Socket data) throws IOException;
    }

    public static void main(String[] args) {
        FTPClient c = new FTPClient();
        try {
            c.run();
        } catch (IOException e) {
            System.out.println("Loi: " + e.getMessage());
        }
    }

    private String readReply() throws IOException {
        String r = in.readLine();
        if (r == null) {
            throw new IOException("Server dong ket noi");
        }
        System.out.println("S: " + r);
        return r;
    }

    private String send(String cmd) throws IOException {
        System.out.println("C: " + cmd);
        out.println(cmd);
        return readReply();
    }

    private void run() throws IOException {
        Scanner sc = new Scanner(System.in);
        System.out.print("IP Server (Radmin VPN) [26.69.89.236]: ");
        String host = sc.nextLine().trim();
        if (host.isEmpty()) {
            host = "26.69.89.236";
        }
        System.out.print("Cong dieu khien [" + DEFAULT_CONTROL_PORT + "]: ");
        String ps = sc.nextLine().trim();
        int cport = ps.isEmpty() ? DEFAULT_CONTROL_PORT : Integer.parseInt(ps);
        ctrl = new Socket();
        ctrl.connect(new InetSocketAddress(host, cport), 5000);   // timeout 5s
        in = new BufferedReader(new InputStreamReader(ctrl.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(ctrl.getOutputStream()), true);
        readReply();

        printHelp();
        while (true) {
            System.out.print("ftp> ");
            if (!sc.hasNextLine()) {
                break;
            }
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\\s+", 2);
            String cmd = p[0].toLowerCase();
            String arg = p.length > 1 ? p[1] : "";

            try {
                switch (cmd) {
                    case "user":
                        send("USER " + arg);
                        break;
                    case "pass":
                        send("PASS " + arg);
                        break;
                    case "mode":
                        passive = !arg.equalsIgnoreCase("active");
                        System.out.println("Che do: " + (passive ? "PASSIVE" : "ACTIVE"));
                        break;
                    case "ls":
                    case "list":
                        doList();
                        break;
                    case "get":
                        doGet(arg);
                        break;
                    case "put":
                        doPut(arg);
                        break;
                    case "del":
                    case "delete":
                        send("DELE " + arg);
                        break;
                    case "quit":
                        send("QUIT");
                        ctrl.close();
                        return;
                    default:
                        printHelp();
                }
            } catch (IOException e) {
                System.out.println("Loi: " + e.getMessage());
            }
        }
    }

    private void printHelp() {
        System.out.println("Lenh: user <ten> | pass <mk> | mode active|passive | ls | get <file> | put <duong_dan_file> | del <ten_file> | quit");
        System.out.println("Tai khoan mau: admin/123456 hoac user/user");
    }

    /**
     * Thiet lap kenh du lieu (Passive/Active), gui lenh, chay handler, doc ma 226.
     */
    private void transfer(String cmd, DataHandler handler) throws IOException {
        Socket data = null;
        ServerSocket activeServer = null;
        try {
            if (passive) {
                String r = send("PASV");
                if (!r.startsWith("227")) {
                    return;
                }
                String[] n = r.substring(r.indexOf('(') + 1, r.indexOf(')')).split(",");
                // Dung chinh IP ma ta da ket noi kenh dieu khien (on dinh hon khi qua VPN)
                String h = ctrl.getInetAddress().getHostAddress();
                int port = Integer.parseInt(n[4].trim()) * 256 + Integer.parseInt(n[5].trim());
                data = new Socket();
                data.connect(new InetSocketAddress(h, port), 5000);   // client ket noi toi server
                String r2 = send(cmd);
                if (!r2.startsWith("150")) {
                    return;
                }
            } else {
                activeServer = openPort(ACTIVE_MIN, ACTIVE_MAX);   // client mo cong cho server
                int port = activeServer.getLocalPort();
                String ip = ctrl.getLocalAddress().getHostAddress().replace('.', ',');
                String r = send("PORT " + ip + "," + (port / 256) + "," + (port % 256));
                if (!r.startsWith("200")) {
                    return;
                }
                String r2 = send(cmd);
                if (!r2.startsWith("150")) {
                    return;
                }
                activeServer.setSoTimeout(15000);
                data = activeServer.accept();               // server ket noi toi client
            }

            handler.handle(data);
            data.close();
            data = null;
            readReply();                                    // 226 Transfer complete
        } finally {
            if (data != null) {
                data.close();
            }
            if (activeServer != null) {
                activeServer.close();
            }
        }
    }

    private static ServerSocket openPort(int min, int max) throws IOException {
        for (int p = min; p <= max; p++) {
            try {
                return new ServerSocket(p);
            } catch (IOException ignored) {
            }
        }
        throw new IOException("Khong con cong trong");
    }

    private void doList() throws IOException {
        transfer("LIST", data -> {
            BufferedReader r = new BufferedReader(new InputStreamReader(data.getInputStream()));
            String l;
            System.out.println("--- Danh sach file ---");
            while ((l = r.readLine()) != null) {
                System.out.println(l);
            }
            System.out.println("----------------------");
        });
    }

    private void doGet(String name) throws IOException {
        if (name.isEmpty()) {
            System.out.println("Cu phap: get <ten_file>");
            return;
        }
        File dir = new File("downloads");
        dir.mkdirs();
        File target = new File(dir, new File(name).getName());
        transfer("RETR " + name, data -> {
            try (InputStream din = data.getInputStream();
                    OutputStream fout = new FileOutputStream(target)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = din.read(buf)) != -1) {
                    fout.write(buf, 0, n);
                }
            }
            System.out.println("Da luu: " + target.getAbsolutePath());
        });
    }

    private void doPut(String path) throws IOException {
        File f = new File(path);
        if (!f.isFile()) {
            System.out.println("Khong tim thay file: " + path);
            return;
        }
        transfer("STOR " + f.getName(), data -> {
            try (InputStream fin = new FileInputStream(f);
                    OutputStream dout = data.getOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = fin.read(buf)) != -1) {
                    dout.write(buf, 0, n);
                }
                dout.flush();
            }
        });
    }
}

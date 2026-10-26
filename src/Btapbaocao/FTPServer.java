package Btapbaocao;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Máy chủ FTP của đồ án. Lớp này quản lý tài nguyên dùng chung và nhận kết nối mới;
 * ClientHandler ở bên dưới mới xử lý lệnh của từng client.
 *
 * FTP có hai loại kết nối TCP trong ứng dụng này:
 * - Control: giữ suốt phiên, mặc định cổng 2121; truyền lệnh và mã phản hồi.
 * - Data: mở riêng cho mỗi LIST/RETR/STOR; truyền danh sách hoặc byte của file.
 *
 * Đây là tập lệnh FTP phục vụ đồ án, không phải triển khai đầy đủ mọi tính năng RFC 959.
 */
public class FTPServer {

    /** Cổng tiếp nhận các kết nối điều khiển từ client. */
    public static final int CONTROL_PORT = 2121;
    /** Khoảng cổng server thử mở khi client chọn Passive Mode. */
    public static final int PASV_MIN = 50000;
    public static final int PASV_MAX = 50100;
    /** Thư mục gốc chia sẻ; các lệnh file phía server thao tác trong thư mục này. */
    public static File ROOT = new File("ftp_root");
    /** Tài khoản demo: khóa là username, giá trị là password. */
    public static final Map<String, String> USERS = new HashMap<>();

    /** Cờ trạng thái được đọc từ cả luồng giao diện và luồng nhận kết nối. */
    public static volatile boolean running = false;
    /** ServerSocket đang chờ client; đóng socket này sẽ làm accept() dừng. */
    public static ServerSocket currentServer = null;
    /** Callback tùy chọn để Server GUI nhận log mà vẫn giữ log ở console. */
    public static java.util.function.Consumer<String> logger = null;
    /** Callback tùy chọn để GUI làm mới bảng client khi danh sách thay đổi. */
    public static Runnable clientListener = null;
    /** Các phiên client hiện hoạt động; CopyOnWriteArrayList an toàn khi nhiều thread duyệt/sửa. */
    public static final java.util.List<ClientHandler> activeClients = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Tạo hai tài khoản mẫu khi lớp FTPServer được nạp. */
    static {
        USERS.put("admin", "123456");
        USERS.put("user", "user");
    }

    /** Ghi sự kiện ra console và chuyển tiếp đến GUI nếu GUI đã đăng ký callback. */
    public static void log(String msg) {
        System.out.println(msg);
        if (logger != null) {
            try {
                logger.accept(msg);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Chọn thư mục chia sẻ, mở cổng điều khiển và khởi chạy luồng accept.
     * Hàm trả về sau khi luồng nhận kết nối được tạo; server tiếp tục chạy nền.
     */
    public static void startServer(int port, File rootFolder) throws IOException {
        if (running) {
            return;
        }
        ROOT = rootFolder != null ? rootFolder : new File("ftp_root");
        if (!ROOT.exists()) {
            ROOT.mkdirs();
        }
        // ServerSocket lắng nghe trên các địa chỉ mạng của máy ở cổng được chọn.
        currentServer = new ServerSocket(port);
        running = true;
        log("FTP Server dang chay tai cong " + port);
        log("Thu muc chia se: " + ROOT.getAbsolutePath());

        // Tách accept() khỏi Swing EDT để nút Start/Stop và cửa sổ không bị chặn.
        new Thread(() -> {
            try {
                // Mỗi accept() thành công tạo một Socket riêng cho đúng một client.
                while (running && currentServer != null && !currentServer.isClosed()) {
                    Socket client = currentServer.accept();
                    ClientHandler handler = new ClientHandler(client);
                    activeClients.add(handler);
                    if (clientListener != null) {
                        clientListener.run();
                    }
                    log("Client ket noi: " + client.getRemoteSocketAddress());
                    // Mỗi phiên có thread riêng; thread accept quay lại chờ client kế tiếp.
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

    /** Dừng nhận kết nối mới rồi đóng các phiên đang hoạt động. */
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

    /** Điểm chạy cho bản console; GUI gọi startServer() trực tiếp. */
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

    /**
     * In IPv4 của các card mạng đang hoạt động để người dùng biết địa chỉ client cần nhập.
     * Nếu hai máy cùng Radmin VPN thì dùng địa chỉ Radmin 26.x.x.x được in ở đây.
     */
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

    /** Thử mở lần lượt các cổng trong khoảng đã cho; ném IOException nếu tất cả đều lỗi. */
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

/** Xử lý một phiên FTP độc lập trên thread do FTPServer tạo. */
class ClientHandler implements Runnable {

    /** Socket điều khiển: nhận lệnh văn bản và gửi mã phản hồi. */
    private final Socket ctrl;
    /** Thời điểm nhận kết nối, dùng để hiển thị thông tin phiên trong GUI. */
    private final java.util.Date connectedTime = new java.util.Date();
    /** Reader/writer biến luồng byte của socket điều khiển thành các dòng văn bản FTP. */
    private BufferedReader in;
    private PrintWriter out;

    /** Trạng thái đăng nhập thuộc riêng phiên client này. */
    private String pendingUser = null;
    private boolean loggedIn = false;

    // Passive: server mở cổng và đợi client kết nối đến.
    private ServerSocket pasvServer = null;
    // Active: client thông báo địa chỉ/cổng; server chủ động kết nối ngược lại.
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

    /** Đóng các socket của phiên; GUI cũng dùng hàm này để kick client. */
    public void disconnect() {
        closePasv();
        try {
            if (ctrl != null && !ctrl.isClosed()) {
                ctrl.close();
            }
        } catch (IOException ignored) {
        }
    }

    /** Gửi một dòng phản hồi qua kênh điều khiển và ghi cùng nội dung vào log server. */
    private void reply(String msg) {
        out.println(msg);
        FTPServer.log("  -> " + msg);
    }

    @Override
    public void run() {
        try {
            // Socket mang byte; Reader/Writer cung cấp cách đọc và gửi lệnh dạng dòng.
            in = new BufferedReader(new InputStreamReader(ctrl.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(ctrl.getOutputStream()), true);
            // Mã 220 báo rằng kênh điều khiển đã sẵn sàng nhận lệnh.
            reply("220 Welcome to simple FTP server");

            String line;
            // readLine() chờ một lệnh đến; null nghĩa là client đã đóng luồng gửi.
            while ((line = in.readLine()) != null) {
                FTPServer.log("<- " + line);
                // Chia tối đa hai phần để phần tên file/tham số còn lại không bị tách tiếp.
                String[] p = line.trim().split("\\s+", 2);
                String cmd = p[0].toUpperCase();
                String arg = p.length > 1 ? p[1] : "";

                if (cmd.equals("QUIT")) {
                    // 221 kết thúc phiên; finally sẽ đóng socket và xóa client khỏi danh sách.
                    reply("221 Goodbye");
                    break;
                }
                handle(cmd, arg);
            }
        } catch (IOException e) {
            FTPServer.log("Loi client: " + e.getMessage());
        } finally {
            // Dọn dẹp kể cả khi client ngắt bất ngờ hoặc một thao tác mạng gặp lỗi.
            closePasv();
            disconnect();
            FTPServer.activeClients.remove(this);
            if (FTPServer.clientListener != null) {
                FTPServer.clientListener.run();
            }
            FTPServer.log("Client ngat ket noi: " + ctrl.getRemoteSocketAddress());
        }
    }

    /** Phân loại lệnh, kiểm tra đăng nhập, rồi chuyển việc cho hàm xử lý tương ứng. */
    private void handle(String cmd, String arg) throws IOException {
        switch (cmd) {
            case "USER":
                // USER chỉ lưu username; mật khẩu được đối chiếu ở lệnh PASS kế tiếp.
                pendingUser = arg;
                loggedIn = false;
                reply("331 Username ok, need password");
                return;
            case "PASS":
                // USERS.get() tìm mật khẩu theo username đã nhận trước đó.
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

        // Không thực hiện thao tác dữ liệu trước khi xác thực thành công.
        if (!loggedIn) {
            reply("530 Please login with USER and PASS");
            return;
        }

        switch (cmd) {
            case "PASV":
                // Hủy trạng thái data cũ trước khi chuẩn bị chế độ Passive mới.
                closePasv();
                activeHost = null;
                try {
                    pasvServer = FTPServer.openPort(FTPServer.PASV_MIN, FTPServer.PASV_MAX);
                } catch (IOException e) {
                    reply("425 Cannot open passive port");
                    break;
                }
                // FTP mã hóa cổng thành hai số: port = số_thứ_năm * 256 + số_thứ_sáu.
                int port = pasvServer.getLocalPort();
                String ip = ctrl.getLocalAddress().getHostAddress().replace('.', ',');
                reply("227 Entering Passive Mode (" + ip + "," + (port / 256) + "," + (port % 256) + ")");
                break;
            case "PORT":
                try {
                    // PORT truyền 4 số IP và 2 số tạo thành port; đây là địa chỉ client.
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

    /** Xóa một file trong thư mục chia sẻ; không nhận đường dẫn thư mục tùy ý từ client. */
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

    /** Trả kích thước file theo byte; 213 là phản hồi SIZE thành công. */
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

    /**
     * Mở socket dữ liệu theo trạng thái đã chọn trước đó.
     * Passive gọi accept() vì client sẽ gọi connect(); Active gọi connect() tới client.
     */
    private Socket openDataSocket() throws IOException {
        if (pasvServer != null) {
            // Tránh chờ vô hạn nếu client không kết nối vào cổng Passive đã công bố.
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

    /** Đóng socket đang lắng nghe Passive; an toàn khi gọi nhiều lần. */
    private void closePasv() {
        if (pasvServer != null) {
            try {
                pasvServer.close();
            } catch (IOException ignored) {
            }
            pasvServer = null;
        }
    }

    /** Kiểm tra client đã chọn PASV hoặc PORT trước lệnh cần truyền dữ liệu chưa. */
    private boolean hasDataMode() {
        return pasvServer != null || activeHost != null;
    }

    /** Gửi tên và kích thước các file trực tiếp trong ROOT qua socket dữ liệu. */
    private void doList() throws IOException {
        if (!hasDataMode()) {
            reply("425 Use PASV or PORT first");
            return;
        }
        // 150 báo bắt đầu mở data channel; 226 ở cuối mới báo truyền xong.
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

    /** RETR: đọc file tại server và gửi các byte sang client trên data channel. */
    private void doRetr(String name) throws IOException {
        // Chỉ dùng thành phần tên cuối; không ghép nguyên chuỗi ../ vào thư mục chia sẻ.
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
            // Đọc từng khối để RAM không cần chứa toàn bộ file; n là số byte vừa đọc.
            byte[] buf = new byte[4096];
            int n;
            while ((n = fin.read(buf)) != -1) {
                // Chỉ gửi n byte hợp lệ, vì lần đọc cuối có thể ngắn hơn buffer.
                dout.write(buf, 0, n);
            }
            dout.flush();
        } catch (IOException e) {
            reply("426 Transfer aborted");
            return;
        }
        reply("226 Transfer complete");
    }

    /** STOR: đọc byte client gửi và ghi dần thành file trong thư mục ROOT. */
    private void doStor(String name) throws IOException {
        if (name.isEmpty()) {
            reply("501 Missing filename");
            return;
        }
        if (!hasDataMode()) {
            reply("425 Use PASV or PORT first");
            return;
        }
        // FileOutputStream sẽ ghi đè nếu tên file đã tồn tại.
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

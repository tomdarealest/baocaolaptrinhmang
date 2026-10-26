package Btapbaocao;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Máy chủ HTTP cho giao diện web của đồ án. Không cần framework bên ngoài:
 * HttpServer nhận và phân tích HTTP; các hàm bên dưới xử lý route, xác thực,
 * đọc/ghi file và tạo phản hồi bằng API mạng và I/O có sẵn trong JDK.
 *
 * Luồng một yêu cầu: trình duyệt gửi method/path/header/body → handle() chọn route
 * → API cần dữ liệu kiểm tra Authorization → xử lý file → send() hoặc stream trả kết quả.
 * HTTP dùng request/response riêng cho mỗi thao tác; không dùng PASV/PORT như FTP.
 */
public class WebFileServer {
    /** Cổng HTTP mà trình duyệt và Cloudflare Tunnel kết nối tới. */
    public static final int PORT = 8080;
    /** Giới hạn dung lượng tối đa của một file upload (20 MiB, tính theo byte). */
    private static final long MAX_UPLOAD = 20L * 1024 * 1024;
    /** Thư mục dùng chung với FTP; normalize() tạo đường dẫn tuyệt đối đã chuẩn hóa. */
    private static final Path ROOT = Paths.get("ftp_root").toAbsolutePath().normalize();
    /** Thư mục chứa HTML, CSS và JavaScript được gửi đến trình duyệt. */
    private static final Path WEB = Paths.get("web").toAbsolutePath().normalize();
    /** Header Authorization đúng được tạo một lần lúc khởi động, không lưu lại mật khẩu thô. */
    private static byte[] expectedAuth;

    /**
     * Chuẩn bị thư mục/tài khoản, tạo HTTP listener và nhóm thread xử lý request.
     * Listener chỉ bind loopback để Cloudflare Tunnel cùng máy có thể nối vào origin.
     */
    public static void main(String[] args) throws IOException {
        Files.createDirectories(ROOT);
        if (!Files.isRegularFile(WEB.resolve("index.html"))) {
            throw new IOException("Thieu web/index.html. Hay chay tu thu muc du an.");
        }
        // Có thể cấu hình tài khoản qua environment; nếu thiếu thì dùng username demo.
        String user = System.getenv("WEB_USER");
        if (user == null || user.isEmpty()) user = "admin";
        String password = System.getenv("WEB_PASSWORD");
        if (password == null || password.isEmpty()) {
            // Mật khẩu ngẫu nhiên theo lần chạy, tránh để một mật khẩu cố định trong source.
            password = UUID.randomUUID().toString().replace("-", "");
            System.out.println("Mat khau tam cho lan chay nay: " + password);
        }
        // HTTP Basic gửi dạng Base64 (không phải mã hóa); trên mạng công khai cần HTTPS.
        expectedAuth = ("Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8);

        // Chi lang nghe loopback: cloudflared tren cung may ket noi vao day.
        // 32 là backlog kết nối chờ nhận. Bind loopback nghĩa là không mở trực tiếp ra LAN.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 32);
        // Giới hạn đồng thời: 4 thread thường trực, tối đa 8 worker và hàng đợi 64 request.
        ExecutorService workers = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(64), new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(workers);
        // Một context gốc nhận các path; handle() kiểm tra path cụ thể phía dưới.
        server.createContext("/", WebFileServer::handle);
        // Khi JVM dừng, đóng listener và worker đang chạy.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            workers.shutdownNow();
        }));
        server.start();
        System.out.println("Web File Server: http://127.0.0.1:" + PORT);
        System.out.println("Tai khoan: " + user + " | Thu muc: " + ROOT);
        System.out.println("Cloudflare Tunnel origin: http://127.0.0.1:8080");
    }

    /** Điều phối một request HTTP và bảo đảm exchange được đóng khi xử lý xong. */
    private static void handle(HttpExchange exchange) throws IOException {
        try {
            // Header bảo vệ trình duyệt và ngăn cache trang/file nhạy cảm.
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            // URI cho biết endpoint; method xác định thao tác GET/POST cần cho endpoint đó.
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            // Ba tài nguyên giao diện công khai; API phía dưới mới yêu cầu đăng nhập.
            if (path.equals("/") || path.equals("/app.js") || path.equals("/style.css")) {
                requireMethod(method, "GET");
                String file = path.equals("/") ? "index.html" : path.substring(1);
                String type = file.endsWith(".html") ? "text/html" :
                        file.endsWith(".js") ? "text/javascript" : "text/css";
                send(exchange, 200, type + "; charset=utf-8", Files.readAllBytes(WEB.resolve(file)));
                return;
            }
            // Từ chối path không tồn tại trước khi xác thực.
            if (!path.startsWith("/api/")) throw new HttpError(404, "Khong tim thay trang.");
            if (!authorize(exchange)) throw new HttpError(401, "Sai tai khoan hoac mat khau.");
            // Mỗi endpoint chấp nhận method riêng để không lẫn đọc file với ghi file.
            switch (path) {
                case "/api/files":
                    requireMethod(method, "GET");
                    listFiles(exchange);
                    break;
                case "/api/download":
                    requireMethod(method, "GET");
                    download(exchange);
                    break;
                case "/api/upload":
                    requireMethod(method, "POST");
                    upload(exchange);
                    break;
                default:
                    throw new HttpError(404, "Khong tim thay chuc nang.");
            }
        } catch (HttpError ex) {
            // Lỗi dự kiến (401, 404, sai tên file...) được gửi về với mã HTTP tương ứng.
            send(exchange, ex.status, "text/plain; charset=utf-8", ex.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            // Log chỉ ghi loại exception; không tiết lộ đường dẫn nội bộ hay stack trace cho browser.
            System.err.println("Request error: " + ex.getClass().getSimpleName());
            // Khong gui duong dan noi bo, mat khau hay stack trace ve trinh duyet.
            if (exchange.getResponseCode() == -1) {
                send(exchange, 500, "text/plain; charset=utf-8",
                        "Server khong the xu ly yeu cau.".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            exchange.close();
        }
    }

    /** So khớp Authorization với thông tin đã chuẩn bị khi khởi động. */
    private static boolean authorize(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Authorization");
        // So sánh constant-time giảm rò rỉ thời gian; Basic chỉ an toàn khi bên ngoài dùng HTTPS.
        return value != null && MessageDigest.isEqual(expectedAuth, value.getBytes(StandardCharsets.UTF_8));
    }

    /** Phản hồi 405 nếu endpoint được gọi bằng method ngoài method đã định nghĩa. */
    private static void requireMethod(String actual, String allowed) throws HttpError {
        if (!actual.equals(allowed)) throw new HttpError(405, "Phuong thuc HTTP khong duoc ho tro.");
    }

    /** GET /api/files: liệt kê file trong ROOT và trả JSON để JavaScript dựng danh sách. */
    private static void listFiles(HttpExchange exchange) throws IOException {
        List<Path> files = new ArrayList<>();
        // DirectoryStream chỉ duyệt cấp đầu; NOFOLLOW_LINKS bỏ qua symlink trỏ ra ngoài ROOT.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ROOT)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) files.add(file);
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        // Tự tạo JSON; jsonString() escape tên file để chuỗi không phá cấu trúc JSON.
        StringBuilder json = new StringBuilder("[");
        for (Path file : files) {
            if (json.length() > 1) json.append(',');
            json.append("{\"name\":").append(jsonString(file.getFileName().toString()))
                    .append(",\"size\":").append(Files.size(file))
                    .append(",\"modified\":").append(Files.getLastModifiedTime(file).toMillis())
                    .append('}');
        }
        json.append(']');
        send(exchange, 200, "application/json; charset=utf-8", json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** GET /api/download?name=...: stream file xuống response thay vì đọc cả file vào RAM. */
    private static void download(HttpExchange exchange) throws IOException {
        Path file = requestedFile(exchange);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new HttpError(404, "File khong ton tai.");
        String encoded = URLEncoder.encode(file.getFileName().toString(), "UTF-8").replace("+", "%20");
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        // Header attachment hướng trình duyệt lưu response thành file có tên gốc.
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            long length = Files.size(file);
            if (length == 0) exchange.getResponseHeaders().set("Content-Length", "0");
            exchange.sendResponseHeaders(200, length == 0 ? -1 : length);
            try (OutputStream output = exchange.getResponseBody()) {
                // Sao chép từng khối 8 KiB; input đọc từ đĩa, output gửi qua HTTP socket.
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
    }

    /** POST /api/upload?name=...: nhận byte từ HTTP request và lưu thành file trong ROOT. */
    private static void upload(HttpExchange exchange) throws IOException {
        // Chi chap nhan byte file, khong dung multipart/form-data hay framework.
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!"application/octet-stream".equalsIgnoreCase(type)) {
            throw new HttpError(415, "Can Content-Type: application/octet-stream.");
        }
        // Kiểm tra tên trước khi tạo file; từ chối tên đã dùng để tránh ghi đè dữ liệu cũ.
        Path destination = requestedFile(exchange);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new HttpError(409, "Ten file da ton tai. Hay doi ten file.");
        // Kiểm tra Content-Length sớm nếu có; vẫn đếm byte thật khi đọc để giới hạn request chunked.
        String declared = exchange.getRequestHeaders().getFirst("Content-Length");
        if (declared != null) {
            try {
                long size = Long.parseLong(declared);
                if (size < 0) throw new NumberFormatException();
                if (size > MAX_UPLOAD) throw new HttpError(413, "File vuot qua 20 MiB.");
            } catch (NumberFormatException ex) {
                throw new HttpError(400, "Content-Length khong hop le.");
            }
        }
        // Thu muc rieng de file dang nhan khong xuat hien trong danh sach.
        // Nhận vào file tạm trước: nếu client rớt giữa chừng, file dở chưa xuất hiện trong ROOT.
        Path staging = ROOT.resolve(".incoming");
        if (Files.isSymbolicLink(staging)) throw new HttpError(500, "Thu muc tam khong hop le.");
        Files.createDirectories(staging);
        Path temporary = Files.createTempFile(staging, "upload-", ".tmp");
        try {
            try (InputStream input = exchange.getRequestBody(); OutputStream output = Files.newOutputStream(temporary)) {
                // Giữ bộ nhớ ổn định bằng buffer; dừng ngay khi tổng byte vượt 20 MiB.
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_UPLOAD) throw new HttpError(413, "File vuot qua 20 MiB.");
                    output.write(buffer, 0, count);
                }
            }
            // Dong bo viec cong bo file giua cac request upload cua web server.
            // Đồng bộ bước công bố tên file để hai upload trùng tên không ghi đè nhau.
            synchronized (WebFileServer.class) {
                try {
                    Files.move(temporary, destination);
                } catch (FileAlreadyExistsException ex) {
                    throw new HttpError(409, "Ten file da ton tai. Hay doi ten file.");
                }
            }
            send(exchange, 201, "text/plain; charset=utf-8", "Tai len thanh cong.".getBytes(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Đọc query name, giải mã URL và chỉ chấp nhận một tên file cấp gốc.
     * Trả Path đã chuẩn hóa; ném 400 nếu tên có dấu phân cách hoặc thoát khỏi ROOT.
     */
    private static Path requestedFile(HttpExchange exchange) throws HttpError {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.startsWith("name=") || query.contains("&")) throw new HttpError(400, "Thieu ten file.");
        String name;
        try { name = URLDecoder.decode(query.substring(5), "UTF-8"); }
        catch (Exception ex) { throw new HttpError(400, "Ten file khong hop le."); }
        // Chi mot ten file, khong cho phep duong dan, NTFS alternate streams hoac ten thiet bi.
        // Chặn đường dẫn, ký tự điều khiển, tên ẩn/tên thiết bị Windows và tên quá dài.
        if (name.isEmpty() || name.length() > 180 || name.startsWith(".") || name.endsWith(".") || name.endsWith(" ")
                || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new HttpError(400, "Ten file khong hop le.");
        }
        try {
            // Kiểm tra parent lần cuối sau normalize(); không tin dữ liệu tên từ request.
            Path file = ROOT.resolve(name).normalize();
            if (!ROOT.equals(file.getParent()) || Files.isSymbolicLink(file)) throw new HttpError(400, "Duong dan khong hop le.");
            return file;
        } catch (InvalidPathException ex) { throw new HttpError(400, "Ten file khong hop le."); }
    }

    /** Escape dấu ngoặc, backslash và ký tự điều khiển trước khi nhúng tên vào JSON. */
    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            if (ch == '\\' || ch == '"') result.append('\\').append(ch);
            else if (ch < 32) result.append(String.format("\\u%04x", (int) ch));
            else result.append(ch);
        }
        return result.append('"').toString();
    }

    /** Gửi status, Content-Type và body cho phản hồi ngắn như JSON, lỗi hoặc xác nhận upload. */
    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    /** Ngoại lệ điều khiển luồng cho lỗi HTTP dự kiến, mang theo status cần trả về. */
    private static class HttpError extends IOException {
        final int status;
        HttpError(int status, String message) { super(message); this.status = status; }
    }
}

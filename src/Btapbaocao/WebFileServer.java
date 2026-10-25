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
 * Ban web cua do an: chi dung HTTP server co san trong JDK va Java I/O.
 * Kien: main, handle, authorize, listFiles, download, upload va send.
 * HTTP dung mot request/response, khong dung kenh FTP PASV/PORT.
 */
public class WebFileServer {
    public static final int PORT = 8080;
    private static final long MAX_UPLOAD = 20L * 1024 * 1024;
    private static final Path ROOT = Paths.get("ftp_root").toAbsolutePath().normalize();
    private static final Path WEB = Paths.get("web").toAbsolutePath().normalize();
    private static byte[] expectedAuth;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(ROOT);
        if (!Files.isRegularFile(WEB.resolve("index.html"))) {
            throw new IOException("Thieu web/index.html. Hay chay tu thu muc du an.");
        }
        String user = System.getenv("WEB_USER");
        if (user == null || user.isEmpty()) user = "admin";
        String password = System.getenv("WEB_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = UUID.randomUUID().toString().replace("-", "");
            System.out.println("Mat khau tam cho lan chay nay: " + password);
        }
        expectedAuth = ("Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8);

        // Chi lang nghe loopback: cloudflared tren cung may ket noi vao day.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 32);
        ExecutorService workers = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(64), new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(workers);
        server.createContext("/", WebFileServer::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            workers.shutdownNow();
        }));
        server.start();
        System.out.println("Web File Server: http://127.0.0.1:" + PORT);
        System.out.println("Tai khoan: " + user + " | Thu muc: " + ROOT);
        System.out.println("Cloudflare Tunnel origin: http://127.0.0.1:8080");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; "
                    + "connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/") || path.equals("/app.js") || path.equals("/style.css")) {
                requireMethod(method, "GET");
                String file = path.equals("/") ? "index.html" : path.substring(1);
                String type = file.endsWith(".html") ? "text/html" :
                        file.endsWith(".js") ? "text/javascript" : "text/css";
                send(exchange, 200, type + "; charset=utf-8", Files.readAllBytes(WEB.resolve(file)));
                return;
            }
            if (!path.startsWith("/api/")) throw new HttpError(404, "Khong tim thay trang.");
            if (!authorize(exchange)) throw new HttpError(401, "Sai tai khoan hoac mat khau.");
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
            send(exchange, ex.status, "text/plain; charset=utf-8", ex.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
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

    private static boolean authorize(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("Authorization");
        return value != null && MessageDigest.isEqual(expectedAuth, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireMethod(String actual, String allowed) throws HttpError {
        if (!actual.equals(allowed)) throw new HttpError(405, "Phuong thuc HTTP khong duoc ho tro.");
    }

    private static void listFiles(HttpExchange exchange) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ROOT)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) files.add(file);
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
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

    private static void download(HttpExchange exchange) throws IOException {
        Path file = requestedFile(exchange);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new HttpError(404, "File khong ton tai.");
        String encoded = URLEncoder.encode(file.getFileName().toString(), "UTF-8").replace("+", "%20");
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            long length = Files.size(file);
            if (length == 0) exchange.getResponseHeaders().set("Content-Length", "0");
            exchange.sendResponseHeaders(200, length == 0 ? -1 : length);
            try (OutputStream output = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
    }

    private static void upload(HttpExchange exchange) throws IOException {
        // Chi chap nhan byte file, khong dung multipart/form-data hay framework.
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!"application/octet-stream".equalsIgnoreCase(type)) {
            throw new HttpError(415, "Can Content-Type: application/octet-stream.");
        }
        Path destination = requestedFile(exchange);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new HttpError(409, "Ten file da ton tai. Hay doi ten file.");
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
        Path staging = ROOT.resolve(".incoming");
        if (Files.isSymbolicLink(staging)) throw new HttpError(500, "Thu muc tam khong hop le.");
        Files.createDirectories(staging);
        Path temporary = Files.createTempFile(staging, "upload-", ".tmp");
        try {
            try (InputStream input = exchange.getRequestBody(); OutputStream output = Files.newOutputStream(temporary)) {
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

    private static Path requestedFile(HttpExchange exchange) throws HttpError {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || !query.startsWith("name=") || query.contains("&")) throw new HttpError(400, "Thieu ten file.");
        String name;
        try { name = URLDecoder.decode(query.substring(5), "UTF-8"); }
        catch (Exception ex) { throw new HttpError(400, "Ten file khong hop le."); }
        // Chi mot ten file, khong cho phep duong dan, NTFS alternate streams hoac ten thiet bi.
        if (name.isEmpty() || name.length() > 180 || name.startsWith(".") || name.endsWith(".") || name.endsWith(" ")
                || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new HttpError(400, "Ten file khong hop le.");
        }
        try {
            Path file = ROOT.resolve(name).normalize();
            if (!ROOT.equals(file.getParent()) || Files.isSymbolicLink(file)) throw new HttpError(400, "Duong dan khong hop le.");
            return file;
        } catch (InvalidPathException ex) { throw new HttpError(400, "Ten file khong hop le."); }
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            if (ch == '\\' || ch == '"') result.append('\\').append(ch);
            else if (ch < 32) result.append(String.format("\\u%04x", (int) ch));
            else result.append(ch);
        }
        return result.append('"').toString();
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
    }

    private static class HttpError extends IOException {
        final int status;
        HttpError(int status, String message) { super(message); this.status = status; }
    }
}

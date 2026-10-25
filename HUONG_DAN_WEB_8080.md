# Bản web truyền file – Java và HTML, cổng 8080

## Chạy chương trình

1. Cài JDK 22 trở lên.
2. Chạy `run_web_server.bat`. Script biên dịch lại source mỗi lần chạy.
3. Mở http://127.0.0.1:8080 trên trình duyệt.
4. Tài khoản mặc định: `admin`. Mật khẩu ngẫu nhiên được in trên console server mỗi lần khởi động. Không đưa mật khẩu lên Git hoặc báo cáo công khai.
5. Đăng nhập, xem danh sách, chọn file và tải lên, hoặc tải file về. Mỗi upload tối đa 20 MiB, không ghi đè file đã có. File lưu trong `ftp_root`.

Có thể tự đặt `WEB_USER` và `WEB_PASSWORD` trong môi trường của tiến trình trước khi chạy. Trang web giữ thông tin xác thực trong bộ nhớ của tab; tải lại trang thì đăng nhập lại. Đăng xuất xóa thông tin xác thực ở tab, không thu hồi một token trên server (bản này dùng HTTP Basic, không có session).

## Cloudflare Tunnel cho www.localexplorerai.site

Máy chạy cloudflared cần cùng máy với Java server (server chỉ bind 127.0.0.1).

Trong cấu hình tunnel đang sở hữu, đặt published application route/public hostname:

- Hostname: `www.localexplorerai.site`.
- Service type: `HTTP`.
- Origin/service URL: `http://127.0.0.1:8080` (nếu giao diện đã chọn HTTP riêng, ô URL nhập `127.0.0.1:8080`).
- Path: để trống để chuyển cả `/`, `/app.js`, `/style.css` và `/api/*` tới server.

Sau khi Java server và tunnel hoạt động, truy cập https://www.localexplorerai.site/ (không thêm :8080 vào URL công khai). HTTPS do Cloudflare xử lý; kết nối local từ cloudflared tới Java là HTTP. Chỉ dùng HTTP Basic qua HTTPS ở bên ngoài và loopback khi thử trên máy.

Nếu tunnel quản lý bằng file YAML, ghép rule sau vào ingress của cấu hình hiện có, trước rule catch-all. Giữ nguyên tunnel ID, credentials và các hostname khác:

```yaml
ingress:
  - hostname: www.localexplorerai.site
    service: http://127.0.0.1:8080
  - service: http_status:404
```

Đây chỉ là mẫu ingress, không phải toàn bộ file cấu hình. Không tạo tunnel mới hoặc thay DNS nếu hostname đã được gắn với tunnel hiện có. Chưa xác nhận/chỉnh sửa cấu hình tài khoản Cloudflare trong lần triển khai local này.

## Kiến thức Java lập trình mạng được dùng

- `HttpServer` và `HttpExchange`: API HTTP có sẵn trong JDK (`jdk.httpserver`), không dùng Spring, Tomcat, Maven hay thư viện ngoài. API này xử lý cú pháp HTTP, header và framing; sinh viên viết handler xử lý request/response.
- `InetSocketAddress`: lắng nghe TCP tại 127.0.0.1:8080.
- `ExecutorService`/`ThreadPoolExecutor`: xử lý nhiều request bằng nhóm thread có giới hạn.
- HTTP method/status: GET, POST; 200, 201, 400, 401, 404, 405, 409, 413, 415, 500.
- InputStream/OutputStream và buffer 8192 byte: đọc/ghi nội dung file theo từng khối.
- URL encoding: gửi tên file có dấu/khoảng trắng; kiểm tra tên để không cho truy cập đường dẫn ngoài thư mục gốc.
- HTTP Basic: kiểm tra Authorization; Base64 là mã hóa biểu diễn, KHÔNG phải mã hóa bảo mật.
- try-with-resources, exception, thư mục tạm và move file: đóng tài nguyên, không công bố file upload nhận dở.
- HTML/CSS là giao diện; JavaScript thuần dùng fetch/XMLHttpRequest gửi request, hiển thị danh sách và tiến độ upload. Không có framework frontend.

Nếu môn học bắt buộc tự phân tích HTTP bằng `ServerSocket` thay vì được dùng HTTP server có sẵn trong JDK, cần đổi lớp tiếp nhận HTTP; bản hiện tại dùng HTTP API của JDK, không tự viết bộ phân tích HTTP.

## Phân công mới của Kiên, Huy, Quốc

- **Kiên – server:** `WebFileServer.java`: main (khởi động), handle (định tuyến), authorize (đăng nhập), listFiles, download, upload, requestedFile (kiểm tra tên), send (phản hồi). Giải thích thread pool, status/header, đọc ghi stream và file tạm.
- **Huy – giao tiếp client web:** `web/app.js`: gửi Authorization, GET danh sách/tải xuống, POST upload, URL encoding, xử lý HTTP lỗi và trạng thái kết nối.
- **Quốc – giao diện:** `web/index.html`, `web/style.css`, phối hợp phần cập nhật DOM/progress trong `app.js`; form, danh sách, responsive, thông báo và khóa nút khi truyền.

Luồng upload: chọn file → POST /api/upload?name=... → xác thực → kiểm tra tên/kích thước → đọc request body vào file tạm → chuyển sang ftp_root → trả 201 → làm mới danh sách.

Luồng download: GET /api/download?name=... → xác thực → kiểm tra file → header attachment → stream file trong HTTP response → trình duyệt lưu file.

## So với bản FTP cũ

Bản web chuyển giao thức sang HTTP, không chỉ đổi số cổng FTP. HTTP không sử dụng USER/PASS/PASV/PORT/RETR/STOR của bản cũ. Không gọi tên bản web là FTP chuẩn.

`FTPServer.java`, `FTPClient.java`, `FTPClientGUI.java` vẫn giữ để tham khảo. Chạy `run_ftp_server.bat` ở cổng 2121 nếu muốn dùng client Swing/console cũ. Không đổi các client FTP sang cổng HTTP 8080. Hai bản dùng chung ftp_root; khi demo web chỉ nên chạy server web để tránh bản FTP cũ ghi đè file mà web đang phục vụ.

## Phạm vi của bản đồ án

Chỉ chia sẻ file cấp trên cùng, không quản lý thư mục hoặc xóa file. Không hỗ trợ resume, tài khoản/database nhiều người, hay chia sẻ link không đăng nhập. Download trên trình duyệt gom thành Blob trước khi lưu, phù hợp file demo vừa phải. Giới hạn 20 MiB áp dụng upload web, không áp dụng file có sẵn được đưa vào ftp_root bằng cách khác. Dịch vụ demo chưa có hạn mức dung lượng tổng, rate limit đăng nhập và đầy đủ cơ chế vận hành production. HTTPS công khai phụ thuộc tunnel; dừng máy hoặc dừng Java thì website không phục vụ file được.

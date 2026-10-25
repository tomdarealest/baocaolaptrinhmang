# Báo Cáo Môn Lập Trình Mạng: Ứng Dụng FTP Server & FTP Client

Dự án xây dựng hệ thống **FTP Server** và **FTP Client** đa luồng (Multi-threaded), hỗ trợ cả giao diện đồ họa **GUI Swing** và **Console CLI**, tuân thủ mô hình 2 kênh điều khiển và truyền dữ liệu theo chuẩn RFC 959 (hỗ trợ Active Mode và Passive Mode).



## 🚀 Tính Năng Chính

### 1. FTP Server (`FTPServer.java` & `FTPServerGUI.java`)
- **Kênh điều khiển (Control Port):** Mặc định cổng `2121`.
- **Kênh dữ liệu (Data Port):**
  - **Passive Mode (`PASV`):** Mở động trong dải cổng `50000 - 50100`.
  - **Active Mode (`PORT`):** Kết nối chủ động tới cổng của Client.
- **Bảng điều khiển GUI:**
  - Bật/Tắt Server linh hoạt, tùy chọn thư mục gốc chia sẻ (`ftp_root`).
  - Quét và hiển thị danh sách IP máy tính (nổi bật IP Radmin VPN `26.x.x.x`).
  - **Nhật ký hoạt động (Logs):** Hiển thị thời gian thực mọi câu lệnh FTP.
  - **Client đang kết nối:** Quản lý danh sách Client online, hỗ trợ ngắt kết nối (Kick client).
  - **Quản lý tài khoản:** Thêm/xóa user và cập nhật mật khẩu trực tiếp.

### 2. FTP Client (`FTPClient.java` & `FTPClientGUI.java`)
- **Giao diện 2 bảng khám phá (Dual-pane Explorer):**
  - Bảng trái: Duyệt thư mục máy cục bộ (Local).
  - Bảng phải: Danh sách tập tin trên máy chủ (Remote).
- **Thao tác nhanh:** Upload, Download, Xóa file, Làm mới, Double-click để truyền file.
- **Thanh tiến trình trực quan (`JProgressBar`):** Hiển thị % truyền tải và tốc độ truyền file theo thời gian thực (KB/s).
- **Hỗ trợ đầy đủ lệnh:** `USER`, `PASS`, `LIST`, `RETR`, `STOR`, `DELE`, `SIZE`, `QUIT`.

---

## 🛠 Hướng Dẫn Cài Đặt & Sử Dụng

### Khởi động nhanh bằng file `.bat` (Windows):
1. **Khởi động Server:** Chạy file `Chay_Server_GUI.bat` -> Bấm nút **▶ Khởi Động Server**.
2. **Khởi động Client:** Chạy file `Chay_Client_GUI.bat` -> Bấm **Kết Nối** (Mặc định `127.0.0.1:2121`, `admin` / `123456`).

### Tài khoản mặc định:
- `admin` / `123456`
- `user` / `user`

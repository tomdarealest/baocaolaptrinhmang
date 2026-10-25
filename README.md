# Báo Cáo Môn Lập Trình Mạng: Ứng Dụng FTP Server & FTP Client

Dự án xây dựng hệ thống **FTP Server** và **FTP Client** đa luồng (Multi-threaded), hỗ trợ cả giao diện đồ họa **GUI Swing** và **Console CLI**, tuân thủ mô hình 2 kênh điều khiển và truyền dữ liệu theo chuẩn RFC 959 (hỗ trợ Active Mode và Passive Mode).

---

## 👥 Thông Tin Sinh Viên
* **Tài khoản GitHub:** [HoyMe1011](https://github.com/HoyMe1011)
* **Email:** huy10200511@gmail.com
* **Repository:** [baocaolaptrinhmang](https://github.com/tomdarealest/baocaolaptrinhmang)

---

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
2. **Khởi động Client:** Chạy file `Chay_Client_GUI.bat` -> Bấm **Kết Nối** (mặc định IP Radmin `26.69.89.236:2121`, `admin` / `123456`). Nếu chạy cả Server và Client trên máy này, đổi IP thành `127.0.0.1`; IP Radmin của máy hiện tại là `26.54.197.44`.

### Kết nối qua Radmin VPN
- Cài và kết nối Radmin VPN trên cả máy chạy Server và máy chạy Client.
- IP Radmin của máy chạy Server phải là `26.69.89.236`; mở Server tại cổng `2121`.
- Trên Client, nhập `26.69.89.236` và cổng `2121`. Passive mode dùng thêm dải cổng dữ liệu `50000–50100` của Server; Active mode cần kết nối ngược tới dải `50200–50300` của Client.
- Nếu `26.69.89.236` không phải IP của máy Server, hãy dùng IP Radmin hiển thị trên máy Server. Máy này hiện có IP Radmin `26.54.197.44`.

### Tài khoản mặc định:
- `admin` / `123456`
- `user` / `user`

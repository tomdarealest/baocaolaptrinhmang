# BẢNG PHÂN CHIA NHIỆM VỤ VÀ BÁO CÁO ĐỒ ÁN LẬP TRÌNH MẠNG
## ĐỀ TÀI: XÂY DỰNG HỆ THỐNG TRUYỀN FILE FTP (CLIENT - SERVER) BẰNG JAVA SOCKET

---

### I. BẢNG PHÂN CHIA CÔNG VIỆC NHÓM (3 THÀNH VIÊN)

Để đảm bảo tính cân đối khối lượng công việc và tính khoa học trong đồ án, nhóm chia thành 3 phân hệ độc lập tương ứng với 3 thành viên:

| STT | Thành viên | Vai trò / Phân hệ | Nhiệm vụ chi tiết | File đảm nhiệm chính |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Thành viên 1** *(Người đã làm phần lõi)* | **Core Server & Protocol Engine** | - Thiết lập kết nối Socket TCP (Control Channel cổng `2121`).<br>- Xây dựng cơ chế truyền dữ liệu 2 kênh: Passive Mode (`PASV`) và Active Mode (`PORT`).<br>- Xử lý đa luồng với `ClientHandler` cho phép nhiều client kết nối đồng thời.<br>- Cài đặt bộ khung giao thức FTP chuẩn (RFC 959). | `FTPServer.java` |
| **2** | **Thành viên 2** *(Đồng đội thứ hai)* | **Hệ thống Quản lý Tệp & Lệnh Mở rộng trên Server** | - Xây dựng các lệnh mở rộng quản lý thư mục: `PWD`, `CWD`, `MKD`, `RMD`.<br>- Cài đặt lệnh thao tác file nâng cao: `DELE` (xóa file), `RNFR`/`RNTO` (đổi tên), `SIZE` (lấy kích thước).<br>- Cài đặt cơ chế bảo vệ máy chủ: chống tấn công Path Traversal (`../`), kiểm soát phân quyền thư mục. | `FTPServer.java` *(Bổ sung module File Manager)* |
| **3** | **Thành viên 3** *(BẠN)* | **Phân hệ Giao diện Đồ họa FTP Client (GUI & UX)** | - Thiết kế toàn bộ giao diện trực quan chuẩn FileZilla (Dual-Pane: bảng file máy cục bộ và bảng file máy chủ).<br>- Lập trình đa luồng (`Background Thread` / `SwingWorker`) chống đơ giao diện khi truyền file lớn.<br>- Xây dựng thanh tiến trình `JProgressBar` và tính toán tốc độ truyền tải thời gian thực (KB/s, %).<br>- Xây dựng bảng Log Console hiển thị chi tiết các câu lệnh giao thức FTP có timestamp. | `FTPClientGUI.java` |

---

### II. CHI TIẾT ĐÓNG GÓP CỦA THÀNH VIÊN 3 (BẠN)

Bạn đã xây dựng hoàn chỉnh file [FTPClientGUI.java](file:///d:/Downloads/T2_Demo/src/Btapbaocao/FTPClientGUI.java) với các đặc điểm kỹ thuật nổi bật:

1. **Giao diện Dual-Pane chuyên nghiệp**:
   - Khung bên trái: Máy cục bộ (Local Site) với chức năng chọn thư mục, hiển thị tên tệp, dung lượng và ngày sửa đổi.
   - Khung bên phải: Máy chủ (Remote Server) lấy danh sách tệp trực tiếp từ `ftp_root`.
   - Nút điều hướng ở giữa: `Upload >>` và `<< Download`.

2. **Thanh kết nối nhanh (Quick Connect Bar)**:
   - Cho phép nhập IP Host, Port, Username, Password.
   - Hỗ trợ chuyển đổi linh hoạt giữa chế độ **Passive (PASV)** và **Active (PORT)** qua ComboBox.
   - Đổi trạng thái nút bấm và màu sắc đèn báo ("Đã kết nối" / "Chưa kết nối").

3. **Thanh tiến độ thời gian thực (Transfer Progress)**:
   - Cập nhật số byte đã chuyển theo từng khối buffer (8KB).
   - Hiển thị phần trăm hoàn thành trên thanh ProgressBar.
   - Tính toán và hiển thị tốc độ truyền tải thực tế (`KB/s`).

4. **Bảng nhật ký giao thức (Protocol Log Console)**:
   - Ghi nhận chi tiết từng dòng lệnh gửi từ Client (`C: USER admin`, `C: PASV`, `C: RETR...`) và mã phản hồi từ Server (`S: 220`, `S: 230`, `S: 150`, `S: 226`).
   - Tự động che mật khẩu (`PASS ******`) để đảm bảo an toàn thông tin.

5. **Trải nghiệm người dùng (UX)**:
   - Click đúp vào file ở bảng Local để Upload nhanh.
   - Click đúp vào file ở bảng Remote để Download nhanh.
   - Xử lý bất đồng bộ, không bị treo ứng dụng khi thao tác mạng.

---

### III. HƯỚNG DẪN CHẠY DEMO ĐỒ ÁN

Nhóm có thể sử dụng các file `.bat` tiện ích đã tạo sẵn trong thư mục dự án:

1. **Bước 1: Khởi động Server**
   - Chạy file [run_server.bat](file:///d:/Downloads/T2_Demo/run_server.bat) (hoặc chạy lớp `Btapbaocao.FTPServer`).
   - Server sẽ mở cổng `2121` và hiển thị danh sách IP.

2. **Bước 2: Khởi động giao diện GUI (Phần của Bạn)**
   - Chạy file [run_client_gui.bat](file:///d:/Downloads/T2_Demo/run_client_gui.bat) (hoặc chạy lớp `Btapbaocao.FTPClientGUI`).
   - Nhấn nút **Kết nối** (mặc định đã điền sẵn `127.0.0.1:2121`, tài khoản `admin / 123456`).
   - Chọn file từ bảng Local và bấm **Upload >>** để đẩy lên server.
   - Chọn file từ bảng Remote và bấm **<< Download** để tải về thư mục máy mình.
   - Quan sát thanh tiến trình và bảng Log bên dưới.

3. *(Tùy chọn) Chạy bản Console cũ*:
   - Chạy file [run_client_console.bat](file:///d:/Downloads/T2_Demo/run_client_console.bat) để trình bày sự khác biệt giữa bản Console sơ khai và bản GUI nâng cấp của Bạn.

---

### IV. GỢI Ý CÂU HỎI VẤN ĐÁP KHI BẢO VỆ ĐỒ ÁN (DÀNH CHO BẠN)

- **Câu hỏi 1: Tại sao em lại dùng đa luồng (Multi-thread) trong giao diện Swing?**
  - *Trả lời*: Trong Java Swing, nếu thực hiện các tác vụ mạng (Network I/O) hoặc đọc ghi file trên luồng chính (Event Dispatch Thread - EDT), giao diện sẽ bị "đơ" (freeze) và hệ điều hành sẽ báo "Not Responding". Em tạo luồng riêng (`new Thread(...)`) để xử lý việc truyền file, sau đó dùng `SwingUtilities.invokeLater` để cập nhật thanh tiến trình ProgressBar và bảng Log một cách an toàn.

- **Câu hỏi 2: Giao thức FTP của nhóm em hoạt động theo mấy kênh?**
  - *Trả lời*: FTP chuẩn dùng 2 kênh riêng biệt. Kênh điều khiển (Control Channel) qua Socket cổng 2121 để trao đổi lệnh và mã trạng thái. Kênh dữ liệu (Data Channel) được mở riêng cho từng lần truyền file hoặc lấy danh sách thư mục (LIST, RETR, STOR) thông qua cơ chế Passive hoặc Active.

- **Câu hỏi 3: Phân hệ GUI của em kết nối và giao tiếp với Server như thế nào?**
  - *Trả lời*: Em lập trình lớp `FTPClientGUI` tuân thủ 100% các mã phản hồi và lệnh chuẩn của Server viết ở tầng Socket, đồng thời bổ sung bộ đệm buffer 8KB để tính toán dung lượng đã truyền theo thời gian thực nhằm hiển thị thanh tiến độ.

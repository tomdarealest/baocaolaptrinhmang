package Btapbaocao;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * =========================================================================
 * PHÂN HỆ GIAO DIỆN ĐỒ HỌA FTP CLIENT (Java Swing GUI)
 * Người thực hiện: Thành viên 3 (Phụ trách Giao diện & Trải nghiệm Client)
 * 
 * Tính năng chính:
 *  - Giao diện Dual-Pane chuyên nghiệp chuẩn FileZilla (Local File vs Remote FTP Server).
 *  - Thanh kết nối nhanh (Host, Port, User, Pass, Mode Passive/Active).
 *  - Thanh tiến trình truyền file (JProgressBar) theo thời gian thực (Bytes, %, Tốc độ KB/s).
 *  - Bảng nhật ký giao thức chi tiết (FTP Console Log) có timestamp.
 *  - Đa luồng xử lý (Background Thread) đảm bảo giao diện không bao giờ bị đơ (No UI Freeze).
 *  - Thao tác chuột trực quan: Click đúp tải/mở file, chuột phải (Context Menu) Upload/Download.
 * =========================================================================
 */
public class FTPClientGUI extends JFrame {

    // --- Cấu hình mạng FTP ---
    private Socket ctrlSocket;
    private BufferedReader ctrlIn;
    private PrintWriter ctrlOut;
    private boolean isConnected = false;
    private boolean isPassiveMode = true;

    private static final int DEFAULT_PORT = 2121;
    private static final int ACTIVE_MIN = 50200;
    private static final int ACTIVE_MAX = 50300;

    // --- Thư mục cục bộ (Local) ---
    private File currentLocalDir = new File("downloads");

    // --- Thành phần giao diện (UI Components) ---
    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JComboBox<String> cbMode;
    private JButton btnConnect;
    private JLabel lblConnStatus;

    // Local Pane
    private JLabel lblLocalPath;
    private JTable tblLocal;
    private DefaultTableModel localModel;
    private JButton btnLocalBrowse;
    private JButton btnLocalRefresh;

    // Remote Pane
    private JLabel lblRemotePath;
    private JTable tblRemote;
    private DefaultTableModel remoteModel;
    private JButton btnRemoteRefresh;

    // Action buttons ở giữa
    private JButton btnUpload;
    private JButton btnDownload;

    // Progress & Status
    private JProgressBar progressBar;
    private JLabel lblTransferStatus;
    private JLabel lblSpeed;
    private JLabel lblFooterStatus;

    // Log Console
    private JTextArea txtLog;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public FTPClientGUI() {
        super("FTP Client GUI - Hệ Thống Truyền Tệp Trực Quan (Đồ án Lập trình mạng)");
        initUI();
        initEvents();
        setupInitialState();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        // Thiết lập layout tổng thể
        JPanel rootPanel = new JPanel(new BorderLayout(5, 5));
        rootPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(rootPanel);

        // 1. Thanh kết nối nhanh ở đỉnh (Top Quick Connect Bar)
        rootPanel.add(buildTopBar(), BorderLayout.NORTH);

        // 2. Khu vực trung tâm: Dual-Pane File Explorer + Action Buttons
        rootPanel.add(buildCenterPanel(), BorderLayout.CENTER);

        // 3. Khu vực đáy: Progress Bar, Status Bar & Console Log
        rootPanel.add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    /**
     * Xây dựng thanh công cụ kết nối nhanh trên cùng
     */
    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Thanh kết nối nhanh (Quick Connect)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        JPanel inputsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));

        txtHost = new JTextField("127.0.0.1", 11);
        txtPort = new JTextField(String.valueOf(DEFAULT_PORT), 4);
        txtUser = new JTextField("admin", 8);
        txtPass = new JPasswordField("123456", 8);

        cbMode = new JComboBox<>(new String[]{"Passive (PASV)", "Active (PORT)"});
        btnConnect = new JButton("Kết nối");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(40, 140, 220));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);

        lblConnStatus = new JLabel("Chưa kết nối");
        lblConnStatus.setForeground(Color.RED);
        lblConnStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));

        inputsPanel.add(new JLabel("Máy chủ:"));
        inputsPanel.add(txtHost);
        inputsPanel.add(new JLabel("Cổng:"));
        inputsPanel.add(txtPort);
        inputsPanel.add(new JLabel("Tài khoản:"));
        inputsPanel.add(txtUser);
        inputsPanel.add(new JLabel("Mật khẩu:"));
        inputsPanel.add(txtPass);
        inputsPanel.add(new JLabel("Chế độ:"));
        inputsPanel.add(cbMode);
        inputsPanel.add(btnConnect);
        inputsPanel.add(lblConnStatus);

        panel.add(inputsPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Xây dựng vùng trung tâm gồm 2 khung hiển thị File Local và Remote
     */
    private JPanel buildCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));

        // Khung Local (Bên trái)
        JPanel localPanel = new JPanel(new BorderLayout(4, 4));
        localPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Máy cục bộ (Local Site)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        JPanel localTop = new JPanel(new BorderLayout(5, 5));
        lblLocalPath = new JLabel("Thư mục: " + currentLocalDir.getAbsolutePath());
        lblLocalPath.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        
        JPanel localBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnLocalBrowse = new JButton("Chọn thư mục...");
        btnLocalRefresh = new JButton("Làm mới");
        localBtns.add(btnLocalBrowse);
        localBtns.add(btnLocalRefresh);

        localTop.add(lblLocalPath, BorderLayout.CENTER);
        localTop.add(localBtns, BorderLayout.EAST);
        localPanel.add(localTop, BorderLayout.NORTH);

        String[] localCols = {"Tên tệp tin", "Kích thước", "Ngày sửa đổi"};
        localModel = new DefaultTableModel(localCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblLocal = new JTable(localModel);
        setupTableStyle(tblLocal);
        localPanel.add(new JScrollPane(tblLocal), BorderLayout.CENTER);

        // Khung Remote (Bên phải)
        JPanel remotePanel = new JPanel(new BorderLayout(4, 4));
        remotePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Máy chủ FTP (Remote Server)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        JPanel remoteTop = new JPanel(new BorderLayout(5, 5));
        lblRemotePath = new JLabel("Thư mục từ xa: / (ftp_root)");
        lblRemotePath.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRemoteRefresh = new JButton("Làm mới danh sách");
        remoteTop.add(lblRemotePath, BorderLayout.CENTER);
        remoteTop.add(btnRemoteRefresh, BorderLayout.EAST);
        remotePanel.add(remoteTop, BorderLayout.NORTH);

        String[] remoteCols = {"Tên tệp tin trên máy chủ", "Kích thước", "Trạng thái"};
        remoteModel = new DefaultTableModel(remoteCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblRemote = new JTable(remoteModel);
        setupTableStyle(tblRemote);
        remotePanel.add(new JScrollPane(tblRemote), BorderLayout.CENTER);

        // Nút truyền file ở giữa
        JPanel actionBtnsPanel = new JPanel();
        actionBtnsPanel.setLayout(new BoxLayout(actionBtnsPanel, BoxLayout.Y_AXIS));
        actionBtnsPanel.setBorder(new EmptyBorder(40, 4, 40, 4));

        btnUpload = new JButton("Upload  >>");
        btnUpload.setToolTipText("Tải tệp đang chọn từ Local lên Server");
        btnUpload.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnUpload.setMaximumSize(new Dimension(110, 36));

        btnDownload = new JButton("<<  Download");
        btnDownload.setToolTipText("Tải tệp đang chọn từ Server về máy");
        btnDownload.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnDownload.setMaximumSize(new Dimension(110, 36));

        actionBtnsPanel.add(Box.createVerticalGlue());
        actionBtnsPanel.add(btnUpload);
        actionBtnsPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        actionBtnsPanel.add(btnDownload);
        actionBtnsPanel.add(Box.createVerticalGlue());

        // Ghép SplitPane trái phải
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setContinuousLayout(true);

        centerPanel.add(splitPane, BorderLayout.CENTER);
        centerPanel.add(actionBtnsPanel, BorderLayout.EAST);

        return centerPanel;
    }

    /**
     * Xây dựng khu vực đáy: Tiến trình truyền file và Bảng Log điều khiển
     */
    private JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setPreferredSize(new Dimension(800, 240));

        // 1. Panel hiển thị Tiến trình truyền tải (Transfer Progress)
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Tiến độ truyền tải (Transfer Progress)",
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11)),
                new EmptyBorder(2, 6, 4, 6)));

        JPanel progressInfo = new JPanel(new GridLayout(1, 2));
        lblTransferStatus = new JLabel("Trạng thái: Sẵn sàng");
        lblTransferStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSpeed = new JLabel("Tốc độ: 0 KB/s", SwingConstants.RIGHT);
        lblSpeed.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressInfo.add(lblTransferStatus);
        progressInfo.add(lblSpeed);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setPreferredSize(new Dimension(600, 20));

        progressPanel.add(progressInfo, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        // 2. Panel hiển thị Nhật ký giao thức (Log Console)
        JPanel logPanel = new JPanel(new BorderLayout(3, 3));
        logPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Nhật ký giao thức FTP (Protocol Log)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 11)));

        txtLog = new JTextArea();
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setEditable(false);
        txtLog.setBackground(new Color(248, 249, 250));
        JScrollPane scrollLog = new JScrollPane(txtLog);

        JButton btnClearLog = new JButton("Xóa log");
        btnClearLog.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnClearLog.addActionListener(e -> txtLog.setText(""));

        JPanel logHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        logHeader.add(btnClearLog);

        logPanel.add(scrollLog, BorderLayout.CENTER);
        logPanel.add(logHeader, BorderLayout.NORTH);

        // Ghép progressPanel và logPanel
        JPanel bottomContainer = new JPanel(new BorderLayout(3, 3));
        bottomContainer.add(progressPanel, BorderLayout.NORTH);
        bottomContainer.add(logPanel, BorderLayout.CENTER);

        // Status bar dưới cùng
        lblFooterStatus = new JLabel(" Sẵn sàng kết nối.");
        lblFooterStatus.setBorder(new BevelBorder(BevelBorder.LOWERED));
        lblFooterStatus.setPreferredSize(new Dimension(800, 22));
        lblFooterStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        bottomPanel.add(bottomContainer, BorderLayout.CENTER);
        bottomPanel.add(lblFooterStatus, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private void setupTableStyle(JTable table) {
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setShowGrid(true);
        table.setGridColor(new Color(230, 230, 230));
    }

    private void setupInitialState() {
        if (!currentLocalDir.exists()) {
            currentLocalDir.mkdirs();
        }
        refreshLocalFiles();
        updateUIState(false);
        log("Khởi động ứng dụng FTP Client GUI thành công.");
        log("Sẵn sàng kết nối tới FTP Server (Mặc định: 127.0.0.1:2121).");
    }

    /**
     * Đăng ký các sự kiện tương tác
     */
    private void initEvents() {
        // Nút kết nối / ngắt kết nối
        btnConnect.addActionListener(e -> {
            if (isConnected) {
                disconnectServer();
            } else {
                connectServer();
            }
        });

        // Đổi chế độ Passive / Active
        cbMode.addActionListener(e -> {
            isPassiveMode = cbMode.getSelectedIndex() == 0;
            log("Đã chọn chế độ: " + (isPassiveMode ? "PASSIVE (PASV)" : "ACTIVE (PORT)"));
        });

        // Nút chọn thư mục local
        btnLocalBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentLocalDir);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                currentLocalDir = chooser.getSelectedFile();
                lblLocalPath.setText("Thư mục: " + currentLocalDir.getAbsolutePath());
                refreshLocalFiles();
            }
        });

        // Làm mới local
        btnLocalRefresh.addActionListener(e -> refreshLocalFiles());

        // Làm mới remote
        btnRemoteRefresh.addActionListener(e -> {
            if (isConnected) {
                requestRemoteFiles();
            }
        });

        // Nút Upload
        btnUpload.addActionListener(e -> performUpload());

        // Nút Download
        btnDownload.addActionListener(e -> performDownload());

        // Nhấp đúp chuột vào bảng Local để upload nhanh
        tblLocal.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblLocal.getSelectedRow() != -1) {
                    performUpload();
                }
            }
        });

        // Nhấp đúp chuột vào bảng Remote để download nhanh
        tblRemote.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblRemote.getSelectedRow() != -1) {
                    performDownload();
                }
            }
        });

        // Xử lý sự kiện đóng cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isConnected) {
                    disconnectServer();
                }
                dispose();
                System.exit(0);
            }
        });
    }

    // =========================================================================
    // XỬ LÝ MẠNG VÀ GIAO THỨC FTP (NETWORK PROTOCOL ENGINE)
    // =========================================================================

    private void connectServer() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());

        if (host.isEmpty() || portStr.isEmpty() || user.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Host, Port và Username!",
                    "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnConnect.setEnabled(false);
        lblFooterStatus.setText(" Đang kết nối tới " + host + ":" + port + "...");

        // Chạy trên Background Thread để không làm đơ giao diện
        new Thread(() -> {
            try {
                ctrlSocket = new Socket();
                ctrlSocket.connect(new InetSocketAddress(host, port), 5000);
                ctrlIn = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
                ctrlOut = new PrintWriter(new OutputStreamWriter(ctrlSocket.getOutputStream()), true);

                String welcome = readReply(); // Đọc 220
                if (!welcome.startsWith("220")) {
                    throw new IOException("Máy chủ từ chối kết nối: " + welcome);
                }

                // Gửi USER
                String userRep = sendCmd("USER " + user);
                if (!userRep.startsWith("331")) {
                    throw new IOException("Đăng nhập thất bại: " + userRep);
                }

                // Gửi PASS
                String passRep = sendCmd("PASS " + pass);
                if (!passRep.startsWith("230")) {
                    throw new IOException("Mật khẩu không chính xác: " + passRep);
                }

                SwingUtilities.invokeLater(() -> {
                    isConnected = true;
                    updateUIState(true);
                    lblFooterStatus.setText(" Đã kết nối: " + user + "@" + host + ":" + port);
                    log("ĐĂNG NHẬP THÀNH CÔNG VỚI TÀI KHOẢN: " + user);
                    requestRemoteFiles();
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    cleanSocket();
                    updateUIState(false);
                    lblFooterStatus.setText(" Kết nối thất bại.");
                    log("LỖI KẾT NỐI: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Không thể kết nối đến máy chủ:\n" + ex.getMessage(),
                            "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void disconnectServer() {
        new Thread(() -> {
            try {
                if (ctrlSocket != null && !ctrlSocket.isClosed()) {
                    sendCmd("QUIT");
                }
            } catch (Exception ignored) {
            } finally {
                cleanSocket();
                SwingUtilities.invokeLater(() -> {
                    isConnected = false;
                    updateUIState(false);
                    remoteModel.setRowCount(0);
                    lblFooterStatus.setText(" Đã ngắt kết nối.");
                    log("ĐÃ NGẮT KẾT NỐI VỚI MÁY CHỦ.");
                });
            }
        }).start();
    }

    private void cleanSocket() {
        try {
            if (ctrlSocket != null) ctrlSocket.close();
        } catch (Exception ignored) {}
        ctrlSocket = null;
        ctrlIn = null;
        ctrlOut = null;
    }

    private String readReply() throws IOException {
        String reply = ctrlIn.readLine();
        if (reply == null) {
            throw new IOException("Máy chủ đã đóng kết nối.");
        }
        log("S: " + reply);
        return reply;
    }

    private String sendCmd(String cmd) throws IOException {
        if (cmd.startsWith("PASS")) {
            log("C: PASS ******");
        } else {
            log("C: " + cmd);
        }
        ctrlOut.println(cmd);
        return readReply();
    }

    private interface DataHandler {
        void handle(Socket dataSocket) throws IOException;
    }

    /**
     * Mở kênh dữ liệu (Passive hoặc Active), gửi lệnh, thực thi luồng truyền tệp, đọc mã 226
     */
    private void transfer(String ftpCmd, DataHandler handler) throws IOException {
        Socket dataSocket = null;
        ServerSocket activeServer = null;

        try {
            if (isPassiveMode) {
                // PASSIVE MODE
                String pasvRep = sendCmd("PASV");
                if (!pasvRep.startsWith("227")) {
                    throw new IOException("Lỗi chuyển Passive Mode: " + pasvRep);
                }
                String[] parts = pasvRep.substring(pasvRep.indexOf('(') + 1, pasvRep.indexOf(')')).split(",");
                String targetHost = ctrlSocket.getInetAddress().getHostAddress();
                int targetPort = Integer.parseInt(parts[4].trim()) * 256 + Integer.parseInt(parts[5].trim());

                dataSocket = new Socket();
                dataSocket.connect(new InetSocketAddress(targetHost, targetPort), 5000);

                String startRep = sendCmd(ftpCmd);
                if (!startRep.startsWith("150")) {
                    throw new IOException("Máy chủ không mở kênh truyền tải: " + startRep);
                }
            } else {
                // ACTIVE MODE
                activeServer = openLocalPort(ACTIVE_MIN, ACTIVE_MAX);
                int localPort = activeServer.getLocalPort();
                String localIp = ctrlSocket.getLocalAddress().getHostAddress().replace('.', ',');
                
                String portRep = sendCmd("PORT " + localIp + "," + (localPort / 256) + "," + (localPort % 256));
                if (!portRep.startsWith("200")) {
                    throw new IOException("Lỗi thiết lập Active Mode (PORT): " + portRep);
                }

                String startRep = sendCmd(ftpCmd);
                if (!startRep.startsWith("150")) {
                    throw new IOException("Máy chủ không mở kênh truyền tải: " + startRep);
                }

                activeServer.setSoTimeout(15000);
                dataSocket = activeServer.accept();
            }

            // Thực thi đọc/ghi dữ liệu
            handler.handle(dataSocket);

            // Đóng kênh dữ liệu
            dataSocket.close();
            dataSocket = null;

            // Đọc phản hồi hoàn tất 226 Transfer complete
            readReply();

        } finally {
            if (dataSocket != null) {
                try { dataSocket.close(); } catch (Exception ignored) {}
            }
            if (activeServer != null) {
                try { activeServer.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static ServerSocket openLocalPort(int min, int max) throws IOException {
        for (int p = min; p <= max; p++) {
            try {
                return new ServerSocket(p);
            } catch (IOException ignored) {}
        }
        throw new IOException("Không tìm thấy cổng trống trong dải Active [" + min + " - " + max + "]");
    }

    // =========================================================================
    // CÁC THAO TÁC TRUYỀN DỮ LIỆU (LIST, UPLOAD, DOWNLOAD)
    // =========================================================================

    private void requestRemoteFiles() {
        if (!isConnected) return;
        lblFooterStatus.setText(" Đang tải danh sách tệp từ máy chủ...");

        new Thread(() -> {
            java.util.List<String[]> fileList = new ArrayList<>();
            try {
                transfer("LIST", dataSocket -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] tokens = line.split("\t");
                            String name = tokens[0];
                            String size = tokens.length > 1 ? tokens[1] : "-";
                            fileList.add(new String[]{name, size, "Sẵn sàng"});
                        }
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    remoteModel.setRowCount(0);
                    for (String[] row : fileList) {
                        remoteModel.addRow(row);
                    }
                    lblFooterStatus.setText(" Đã cập nhật danh sách máy chủ: " + fileList.size() + " tệp tin.");
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    log("LỖI TẢI DANH SÁCH FILE: " + ex.getMessage());
                    lblFooterStatus.setText(" Lỗi lấy danh sách tệp.");
                });
            }
        }).start();
    }

    private void performUpload() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Bạn chưa kết nối tới máy chủ FTP!",
                    "Chưa kết nối", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedRow = tblLocal.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp từ bảng máy cục bộ (Local) để tải lên!",
                    "Chưa chọn tệp", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String fileName = (String) localModel.getValueAt(selectedRow, 0);
        File localFile = new File(currentLocalDir, fileName);
        if (!localFile.isFile()) {
            JOptionPane.showMessageDialog(this, "Tệp được chọn không tồn tại hoặc là thư mục!",
                    "Lỗi tệp", JOptionPane.ERROR_MESSAGE);
            return;
        }

        long totalBytes = localFile.length();
        setTransferUI(true, "Đang tải lên: " + fileName, 0);

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                transfer("STOR " + localFile.getName(), dataSocket -> {
                    try (InputStream fin = new FileInputStream(localFile);
                         OutputStream dout = dataSocket.getOutputStream()) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long transferred = 0;

                        while ((bytesRead = fin.read(buffer)) != -1) {
                            dout.write(buffer, 0, bytesRead);
                            transferred += bytesRead;

                            int progress = totalBytes > 0 ? (int) ((transferred * 100) / totalBytes) : 100;
                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speedKB = (transferred / 1024.0) / (elapsed / 1000.0);

                            final long curTransferred = transferred;
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(progress);
                                lblTransferStatus.setText(String.format("Tải lên: %s (%s / %s - %d%%)",
                                        fileName, formatBytes(curTransferred), formatBytes(totalBytes), progress));
                                lblSpeed.setText(String.format("Tốc độ: %.1f KB/s", speedKB));
                            });
                        }
                        dout.flush();
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    setTransferUI(false, "Tải lên hoàn tất: " + fileName, 100);
                    log("TẢI LÊN THÀNH CÔNG: " + fileName + " (" + formatBytes(totalBytes) + ")");
                    requestRemoteFiles(); // Cập nhật lại danh sách trên server
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setTransferUI(false, "Lỗi khi tải lên: " + ex.getMessage(), 0);
                    log("LỖI UPLOAD: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lỗi khi tải tệp lên:\n" + ex.getMessage(),
                            "Lỗi Upload", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void performDownload() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Bạn chưa kết nối tới máy chủ FTP!",
                    "Chưa kết nối", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedRow = tblRemote.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp từ bảng máy chủ (Remote) để tải về!",
                    "Chưa chọn tệp", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String fileName = (String) remoteModel.getValueAt(selectedRow, 0);
        String sizeStr = (String) remoteModel.getValueAt(selectedRow, 1);
        long estimatedSize = parseSizeBytes(sizeStr);

        File targetFile = new File(currentLocalDir, fileName);

        setTransferUI(true, "Đang tải về: " + fileName, 0);

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                transfer("RETR " + fileName, dataSocket -> {
                    try (InputStream din = dataSocket.getInputStream();
                         OutputStream fout = new FileOutputStream(targetFile)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long transferred = 0;

                        while ((bytesRead = din.read(buffer)) != -1) {
                            fout.write(buffer, 0, bytesRead);
                            transferred += bytesRead;

                            int progress = estimatedSize > 0 ? (int) Math.min(100, (transferred * 100) / estimatedSize) : 50;
                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speedKB = (transferred / 1024.0) / (elapsed / 1000.0);

                            final long curTransferred = transferred;
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(progress);
                                lblTransferStatus.setText(String.format("Tải về: %s (%s / %s - %d%%)",
                                        fileName, formatBytes(curTransferred), formatBytes(estimatedSize), progress));
                                lblSpeed.setText(String.format("Tốc độ: %.1f KB/s", speedKB));
                            });
                        }
                        fout.flush();
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    setTransferUI(false, "Tải về hoàn tất: " + fileName, 100);
                    log("TẢI VỀ THÀNH CÔNG: " + fileName + " -> " + targetFile.getAbsolutePath());
                    refreshLocalFiles(); // Cập nhật lại danh sách local
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setTransferUI(false, "Lỗi khi tải về: " + ex.getMessage(), 0);
                    log("LỖI DOWNLOAD: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lỗi khi tải tệp về:\n" + ex.getMessage(),
                            "Lỗi Download", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // =========================================================================
    // HỖ TRỢ GIAO DIỆN & TIỆN ÍCH (HELPERS)
    // =========================================================================

    private void refreshLocalFiles() {
        localModel.setRowCount(0);
        if (!currentLocalDir.exists() || !currentLocalDir.isDirectory()) {
            return;
        }

        File[] files = currentLocalDir.listFiles();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) {
                if (f.isFile()) {
                    localModel.addRow(new Object[]{
                            f.getName(),
                            formatBytes(f.length()),
                            df.format(new Date(f.lastModified()))
                    });
                }
            }
        }
    }

    private void updateUIState(boolean connected) {
        btnConnect.setText(connected ? "Ngắt kết nối" : "Kết nối");
        btnConnect.setBackground(connected ? new Color(220, 53, 69) : new Color(40, 140, 220));
        btnConnect.setEnabled(true);

        lblConnStatus.setText(connected ? "Đã kết nối" : "Chưa kết nối");
        lblConnStatus.setForeground(connected ? new Color(40, 167, 69) : Color.RED);

        txtHost.setEnabled(!connected);
        txtPort.setEnabled(!connected);
        txtUser.setEnabled(!connected);
        txtPass.setEnabled(!connected);

        btnUpload.setEnabled(connected);
        btnDownload.setEnabled(connected);
        btnRemoteRefresh.setEnabled(connected);
    }

    private void setTransferUI(boolean transferring, String status, int progress) {
        lblTransferStatus.setText("Trạng thái: " + status);
        progressBar.setValue(progress);
        if (!transferring) {
            lblSpeed.setText("Tốc độ: 0 KB/s");
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = timeFormat.format(new Date());
            txtLog.append("[" + time + "] " + message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        digitGroups = Math.min(digitGroups, units.length - 1);
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private static long parseSizeBytes(String sizeStr) {
        try {
            if (sizeStr != null && sizeStr.contains("bytes")) {
                String num = sizeStr.replace("bytes", "").trim();
                return Long.parseLong(num);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // =========================================================================
    // HÀM MAIN KHỞI CHẠY ỨNG DỤNG GUI
    // =========================================================================
    public static void main(String[] args) {
        // Thiết lập Look & Feel hệ điều hành
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            FTPClientGUI clientGUI = new FTPClientGUI();
            clientGUI.setVisible(true);
        });
    }
}

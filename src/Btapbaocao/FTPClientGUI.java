package Btapbaocao;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Giao dien do hoa (Swing GUI) cho FTP Client.
 * Tich hop giao dien 2 bang (Local Files va Remote Files giong FileZilla),
 * ho tro ca Passive va Active Mode, co thanh tien trinh truyen file (ProgressBar)
 * va nhat ky trao doi lenh FTP theo thoi gian thuc.
 */
public class FTPClientGUI extends JFrame {

    // --- Cac thanh phan giao dien ket noi ---
    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUser;
    private JPasswordField txtPass;
    private JComboBox<String> cbMode;
    private JButton btnConnect;
    private JLabel lblStatusBadge;

    // --- Bang file cuc bo (Local) ---
    private JTextField txtLocalPath;
    private JTable tblLocal;
    private DefaultTableModel modelLocal;
    private File currentLocalDir;
    private JButton btnUpload;
    private JButton btnRefreshLocal;
    private JButton btnDeleteLocal;

    // --- Bang file server (Remote) ---
    private JLabel lblRemotePath;
    private JTable tblRemote;
    private DefaultTableModel modelRemote;
    private JButton btnDownload;
    private JButton btnRefreshRemote;
    private JButton btnDeleteRemote;

    // --- Tien trinh & Nhat ky ---
    private JProgressBar progressBar;
    private JLabel lblProgressStatus;
    private JTextArea txtLog;
    private JButton btnClearLog;

    // --- Bien dieu khien Socket & Ket noi ---
    private Socket ctrlSocket = null;
    private BufferedReader in = null;
    private PrintWriter out = null;
    private boolean connected = false;
    private boolean isPassive = true;

    private static final int DEFAULT_PORT = 2121;
    private static final int ACTIVE_MIN = 50200;
    private static final int ACTIVE_MAX = 50300;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public FTPClientGUI() {
        super("FTP Client - Báo Cáo Lập Trình Mạng");
        initUI();
        currentLocalDir = new File("downloads");
        if (!currentLocalDir.exists()) {
            currentLocalDir.mkdirs();
        }
        updateLocalFileList(currentLocalDir);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);

        // Ap dung Look and Feel cua he dieu hanh
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // 1. THANH KET NOI (NORTH)
        mainPanel.add(buildTopBar(), BorderLayout.NORTH);

        // 2. KHUNG CHINH: SplitPane (Dual File Explorer o tren, Log & Progress o duoi)
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.68);
        verticalSplit.setContinuousLayout(true);

        // Dual File Pane (Trai: May cuc bo, Phai: May chu FTP)
        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSplit.setResizeWeight(0.5);
        horizontalSplit.setContinuousLayout(true);
        horizontalSplit.setLeftComponent(buildLocalPanel());
        horizontalSplit.setRightComponent(buildRemotePanel());
        verticalSplit.setTopComponent(horizontalSplit);

        // Bottom Pane (Tien trinh + Nhat ky)
        verticalSplit.setBottomComponent(buildBottomPanel());

        mainPanel.add(verticalSplit, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Xu ly ngat ket noi khi dong cua so
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectServer();
            }
        });
    }

    // --- Giao dien thanh ket noi tren cung ---
    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Thông Tin Kết Nối FTP Server"));

        panel.add(new JLabel("IP Server:"));
        txtHost = new JTextField("127.0.0.1", 10);
        panel.add(txtHost);

        panel.add(new JLabel("Port:"));
        txtPort = new JTextField(String.valueOf(DEFAULT_PORT), 4);
        panel.add(txtPort);

        panel.add(new JLabel("Tài khoản:"));
        txtUser = new JTextField("admin", 8);
        panel.add(txtUser);

        panel.add(new JLabel("Mật khẩu:"));
        txtPass = new JPasswordField("123456", 8);
        panel.add(txtPass);

        panel.add(new JLabel("Chế độ:"));
        cbMode = new JComboBox<>(new String[]{"Passive (PASV)", "Active (PORT)"});
        panel.add(cbMode);

        btnConnect = new JButton("Kết Nối");
        btnConnect.setBackground(new Color(46, 139, 87));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> toggleConnect());
        panel.add(btnConnect);

        lblStatusBadge = new JLabel("● Chưa kết nối");
        lblStatusBadge.setForeground(Color.RED);
        lblStatusBadge.setFont(lblStatusBadge.getFont().deriveFont(Font.BOLD));
        panel.add(lblStatusBadge);

        return panel;
    }

    // --- Giao dien danh sach file cuc bo (Local Explorer) ---
    private JPanel buildLocalPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("💻 Máy Cục Bộ (Local Files)"));

        // Duong dan thu muc cuc bo
        JPanel topLocal = new JPanel(new BorderLayout(4, 4));
        txtLocalPath = new JTextField();
        txtLocalPath.setEditable(false);
        JButton btnBrowse = new JButton("📂 Đổi thư mục...");
        btnBrowse.addActionListener(e -> chooseLocalFolder());
        JButton btnUp = new JButton("⬆ Lên");
        btnUp.addActionListener(e -> goUpLocalFolder());

        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        pathButtons.add(btnUp);
        pathButtons.add(btnBrowse);

        topLocal.add(new JLabel("Thư mục: "), BorderLayout.WEST);
        topLocal.add(txtLocalPath, BorderLayout.CENTER);
        topLocal.add(pathButtons, BorderLayout.EAST);
        panel.add(topLocal, BorderLayout.NORTH);

        // Bang file
        modelLocal = new DefaultTableModel(new String[]{"Tên tập tin / Thư mục", "Kích thước", "Ngày sửa đổi"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblLocal = new JTable(modelLocal);
        tblLocal.setRowHeight(22);
        tblLocal.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblLocal.getColumnModel().getColumn(0).setPreferredWidth(200);
        tblLocal.getColumnModel().getColumn(1).setPreferredWidth(80);
        tblLocal.getColumnModel().getColumn(2).setPreferredWidth(120);

        // Can phai cho cot Kich thuoc
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        tblLocal.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

        tblLocal.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleLocalDoubleClick();
                }
            }
        });

        // Menu chuot phai cho bang Local
        JPopupMenu localPopup = new JPopupMenu();
        JMenuItem miUpload = new JMenuItem("⬆ Tải lên Server (Upload)");
        miUpload.addActionListener(e -> uploadSelectedFile());
        JMenuItem miDelLocal = new JMenuItem("🗑 Xóa tập tin cục bộ");
        miDelLocal.addActionListener(e -> deleteSelectedLocalFile());
        JMenuItem miRefLocal = new JMenuItem("🔄 Làm mới danh sách");
        miRefLocal.addActionListener(e -> updateLocalFileList(currentLocalDir));
        localPopup.add(miUpload);
        localPopup.addSeparator();
        localPopup.add(miDelLocal);
        localPopup.add(miRefLocal);
        tblLocal.setComponentPopupMenu(localPopup);

        panel.add(new JScrollPane(tblLocal), BorderLayout.CENTER);

        // Nut hanh dong
        JPanel bottomLocal = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnUpload = new JButton("⬆ Tải Lên Server");
        btnUpload.setEnabled(false);
        btnUpload.addActionListener(e -> uploadSelectedFile());

        btnRefreshLocal = new JButton("🔄 Làm Mới");
        btnRefreshLocal.addActionListener(e -> updateLocalFileList(currentLocalDir));

        btnDeleteLocal = new JButton("🗑 Xóa File");
        btnDeleteLocal.addActionListener(e -> deleteSelectedLocalFile());

        bottomLocal.add(btnUpload);
        bottomLocal.add(btnRefreshLocal);
        bottomLocal.add(btnDeleteLocal);
        panel.add(bottomLocal, BorderLayout.SOUTH);

        return panel;
    }

    // --- Giao dien danh sach file tren FTP Server (Remote Explorer) ---
    private JPanel buildRemotePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("🌐 Máy Chủ FTP (Remote Server)"));

        // Thanh tieu de thu muc Remote
        JPanel topRemote = new JPanel(new BorderLayout(4, 4));
        lblRemotePath = new JLabel("Thư mục chia sẻ: / (ftp_root)");
        topRemote.add(lblRemotePath, BorderLayout.CENTER);
        panel.add(topRemote, BorderLayout.NORTH);

        // Bang file remote
        modelRemote = new DefaultTableModel(new String[]{"Tên tập tin trên Server", "Kích thước"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblRemote = new JTable(modelRemote);
        tblRemote.setRowHeight(22);
        tblRemote.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblRemote.getColumnModel().getColumn(0).setPreferredWidth(260);
        tblRemote.getColumnModel().getColumn(1).setPreferredWidth(100);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        tblRemote.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

        tblRemote.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    downloadSelectedFile();
                }
            }
        });

        // Menu chuot phai cho bang Remote
        JPopupMenu remotePopup = new JPopupMenu();
        JMenuItem miDownload = new JMenuItem("⬇ Tải về máy (Download)");
        miDownload.addActionListener(e -> downloadSelectedFile());
        JMenuItem miDelRemote = new JMenuItem("🗑 Xóa trên Server");
        miDelRemote.addActionListener(e -> deleteSelectedRemoteFile());
        JMenuItem miRefRemote = new JMenuItem("🔄 Làm mới danh sách");
        miRefRemote.addActionListener(e -> refreshRemoteFiles());
        remotePopup.add(miDownload);
        remotePopup.addSeparator();
        remotePopup.add(miDelRemote);
        remotePopup.add(miRefRemote);
        tblRemote.setComponentPopupMenu(remotePopup);

        panel.add(new JScrollPane(tblRemote), BorderLayout.CENTER);

        // Nut hanh dong
        JPanel bottomRemote = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnDownload = new JButton("⬇ Tải Về Máy");
        btnDownload.setEnabled(false);
        btnDownload.addActionListener(e -> downloadSelectedFile());

        btnRefreshRemote = new JButton("🔄 Làm Mới");
        btnRefreshRemote.setEnabled(false);
        btnRefreshRemote.addActionListener(e -> refreshRemoteFiles());

        btnDeleteRemote = new JButton("🗑 Xóa Trên Server");
        btnDeleteRemote.setEnabled(false);
        btnDeleteRemote.addActionListener(e -> deleteSelectedRemoteFile());

        bottomRemote.add(btnDownload);
        bottomRemote.add(btnRefreshRemote);
        bottomRemote.add(btnDeleteRemote);
        panel.add(bottomRemote, BorderLayout.SOUTH);

        return panel;
    }

    // --- Giao dien tien trinh va nhat ky lenh (Bottom) ---
    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("📊 Tiến Trình Truyền & Nhật Ký Giao Tiếp"));

        // Thanh tien trinh
        JPanel progressPanel = new JPanel(new BorderLayout(6, 4));
        lblProgressStatus = new JLabel("Trạng thái: Chưa kết nối");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setPreferredSize(new Dimension(150, 20));

        progressPanel.add(lblProgressStatus, BorderLayout.CENTER);
        progressPanel.add(progressBar, BorderLayout.EAST);
        panel.add(progressPanel, BorderLayout.NORTH);

        // Khung Text Log
        txtLog = new JTextArea(8, 50);
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(248, 249, 250));
        JScrollPane logScroll = new JScrollPane(txtLog);
        panel.add(logScroll, BorderLayout.CENTER);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        btnClearLog = new JButton("Xóa Nhật Ký");
        btnClearLog.addActionListener(e -> txtLog.setText(""));
        logActions.add(btnClearLog);
        panel.add(logActions, BorderLayout.SOUTH);

        return panel;
    }

    // --- Ghi nhat ky ---
    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // =========================================================================
    // XU LY KET NOI / DANG NHAP / DANG XUAT
    // =========================================================================

    private void toggleConnect() {
        if (!connected) {
            connectServer();
        } else {
            disconnectServer();
        }
    }

    private void connectServer() {
        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());
        isPassive = cbMode.getSelectedIndex() == 0;

        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP máy chủ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng điều khiển không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnConnect.setEnabled(false);
        lblProgressStatus.setText("Đang kết nối tới " + host + ":" + port + "...");

        new Thread(() -> {
            try {
                log("Đang kết nối kênh điều khiển tới " + host + ":" + port + "...");
                ctrlSocket = new Socket();
                ctrlSocket.connect(new InetSocketAddress(host, port), 5000);
                in = new BufferedReader(new InputStreamReader(ctrlSocket.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(ctrlSocket.getOutputStream()), true);

                String welcome = readReply();
                if (!welcome.startsWith("220")) {
                    throw new IOException("Server không gửi mã chào 220: " + welcome);
                }

                // Gửi lệnh USER
                String userRep = sendCommand("USER " + user);
                if (!userRep.startsWith("331")) {
                    throw new IOException("Lỗi USER: " + userRep);
                }

                // Gửi lệnh PASS
                String passRep = sendCommand("PASS " + pass);
                if (!passRep.startsWith("230")) {
                    throw new IOException("Đăng nhập thất bại: " + passRep);
                }

                connected = true;
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setText("Ngắt Kết Nối");
                    btnConnect.setBackground(new Color(220, 53, 69));
                    btnConnect.setEnabled(true);
                    lblStatusBadge.setText("● Đã kết nối (" + user + ")");
                    lblStatusBadge.setForeground(new Color(40, 167, 69));
                    lblProgressStatus.setText("Đăng nhập thành công với tài khoản " + user);
                    setTransferButtonsEnabled(true);
                    txtHost.setEnabled(false);
                    txtPort.setEnabled(false);
                    txtUser.setEnabled(false);
                    txtPass.setEnabled(false);
                    cbMode.setEnabled(false);
                });

                log("Đăng nhập thành công! Chế độ truyền: " + (isPassive ? "PASSIVE" : "ACTIVE"));
                refreshRemoteFiles();

            } catch (Exception ex) {
                log("Lỗi kết nối: " + ex.getMessage());
                disconnectServer();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Không thể kết nối tới FTP Server:\n" + ex.getMessage(),
                            "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "FTP-Connect-Thread").start();
    }

    private void disconnectServer() {
        if (connected) {
            new Thread(() -> {
                try {
                    if (out != null) {
                        sendCommand("QUIT");
                    }
                } catch (Exception ignored) {
                } finally {
                    closeControlSocket();
                }
            }).start();
        } else {
            closeControlSocket();
        }

        connected = false;
        SwingUtilities.invokeLater(() -> {
            btnConnect.setText("Kết Nối");
            btnConnect.setBackground(new Color(46, 139, 87));
            btnConnect.setEnabled(true);
            lblStatusBadge.setText("● Chưa kết nối");
            lblStatusBadge.setForeground(Color.RED);
            lblProgressStatus.setText("Trạng thái: Đã ngắt kết nối");
            progressBar.setValue(0);
            progressBar.setString("0%");
            setTransferButtonsEnabled(false);
            modelRemote.setRowCount(0);

            txtHost.setEnabled(true);
            txtPort.setEnabled(true);
            txtUser.setEnabled(true);
            txtPass.setEnabled(true);
            cbMode.setEnabled(true);
        });
    }

    private void closeControlSocket() {
        try {
            if (ctrlSocket != null && !ctrlSocket.isClosed()) {
                ctrlSocket.close();
            }
        } catch (IOException ignored) {
        }
        ctrlSocket = null;
        in = null;
        out = null;
    }

    private void setTransferButtonsEnabled(boolean enabled) {
        btnUpload.setEnabled(enabled);
        btnDownload.setEnabled(enabled);
        btnRefreshRemote.setEnabled(enabled);
        btnDeleteRemote.setEnabled(enabled);
    }

    private String readReply() throws IOException {
        String line = in.readLine();
        if (line == null) {
            throw new IOException("Server đã đóng kết nối.");
        }
        log("S: " + line);
        return line;
    }

    private String sendCommand(String cmd) throws IOException {
        if (cmd.startsWith("PASS")) {
            log("C: PASS ******");
        } else {
            log("C: " + cmd);
        }
        out.println(cmd);
        return readReply();
    }

    // =========================================================================
    // KENH DU LIEU: MO SOCKET CHO LIST, RETR, STOR (Active / Passive)
    // =========================================================================

    private interface DataHandler {
        void handle(Socket dataSocket) throws Exception;
    }

    private synchronized void executeDataCommand(String ftpCmd, DataHandler handler) throws Exception {
        Socket data = null;
        ServerSocket activeServer = null;
        isPassive = cbMode.getSelectedIndex() == 0;

        try {
            if (isPassive) {
                String rep = sendCommand("PASV");
                if (!rep.startsWith("227")) {
                    throw new IOException("PASV thất bại: " + rep);
                }
                int start = rep.indexOf('(');
                int end = rep.indexOf(')');
                if (start == -1 || end == -1) {
                    throw new IOException("Phản hồi PASV không đúng định dạng: " + rep);
                }
                String[] parts = rep.substring(start + 1, end).split(",");
                String serverHost = ctrlSocket.getInetAddress().getHostAddress();
                int serverPort = Integer.parseInt(parts[4].trim()) * 256 + Integer.parseInt(parts[5].trim());

                data = new Socket();
                data.connect(new InetSocketAddress(serverHost, serverPort), 8000);

                String cmdRep = sendCommand(ftpCmd);
                if (!cmdRep.startsWith("150")) {
                    throw new IOException("Lệnh " + ftpCmd + " thất bại: " + cmdRep);
                }
            } else {
                activeServer = openAvailablePort(ACTIVE_MIN, ACTIVE_MAX);
                int localPort = activeServer.getLocalPort();
                String localIp = ctrlSocket.getLocalAddress().getHostAddress().replace('.', ',');
                String portRep = sendCommand("PORT " + localIp + "," + (localPort / 256) + "," + (localPort % 256));
                if (!portRep.startsWith("200")) {
                    throw new IOException("PORT thất bại: " + portRep);
                }

                String cmdRep = sendCommand(ftpCmd);
                if (!cmdRep.startsWith("150")) {
                    throw new IOException("Lệnh " + ftpCmd + " thất bại: " + cmdRep);
                }

                activeServer.setSoTimeout(15000);
                data = activeServer.accept();
            }

            // Thuc thi truyen du lieu
            handler.handle(data);

            data.close();
            data = null;

            // Doc phan hoi 226 Transfer complete tu kenh dieu khien
            String finRep = readReply();
            if (!finRep.startsWith("226")) {
                log("Cảnh báo phản hồi sau truyền: " + finRep);
            }

        } finally {
            if (data != null) {
                try {
                    data.close();
                } catch (IOException ignored) {
                }
            }
            if (activeServer != null) {
                try {
                    activeServer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static ServerSocket openAvailablePort(int min, int max) throws IOException {
        for (int p = min; p <= max; p++) {
            try {
                return new ServerSocket(p);
            } catch (IOException ignored) {
            }
        }
        throw new IOException("Không còn cổng khả dụng trong dải [" + min + ", " + max + "]");
    }

    // =========================================================================
    // CAC THAO TAC TREN SERVER: LIST, DOWNLOAD, UPLOAD, DELETE
    // =========================================================================

    private void refreshRemoteFiles() {
        if (!connected) {
            return;
        }

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                btnRefreshRemote.setEnabled(false);
                lblProgressStatus.setText("Đang tải danh sách tập tin từ Server...");
            });

            List<String[]> fileList = new ArrayList<>();
            try {
                executeDataCommand("LIST", dataSocket -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] p = line.split("\t");
                            String name = p[0].trim();
                            String sizeStr = p.length > 1 ? p[1].trim() : "0 bytes";
                            fileList.add(new String[]{name, sizeStr});
                        }
                    }
                });

                SwingUtilities.invokeLater(() -> {
                    modelRemote.setRowCount(0);
                    for (String[] row : fileList) {
                        modelRemote.addRow(row);
                    }
                    lblProgressStatus.setText("Đã cập nhật " + fileList.size() + " tập tin từ Server.");
                });

            } catch (Exception e) {
                log("Lỗi lấy danh sách file: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    lblProgressStatus.setText("Lỗi lấy danh sách file: " + e.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> btnRefreshRemote.setEnabled(true));
            }
        }, "FTP-List-Thread").start();
    }

    private void downloadSelectedFile() {
        int row = tblRemote.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin trên Server để tải về!",
                    "Chưa Chọn File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String remoteFileName = (String) modelRemote.getValueAt(row, 0);
        String sizeInfo = (String) modelRemote.getValueAt(row, 1);
        long expectedSize = parseSizeFromInfo(sizeInfo);

        File targetFile = new File(currentLocalDir, new File(remoteFileName).getName());
        if (targetFile.exists()) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Tập tin '" + targetFile.getName() + "' đã tồn tại ở máy cục bộ. Bạn có muốn ghi đè?",
                    "Xác Nhận Ghi Đè", JOptionPane.YES_NO_OPTION);
            if (opt != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setTransferButtonsEnabled(false);
        new Thread(() -> {
            try {
                log("Bắt đầu tải về: " + remoteFileName + " -> " + targetFile.getAbsolutePath());
                long startTime = System.currentTimeMillis();

                executeDataCommand("RETR " + remoteFileName, dataSocket -> {
                    try (InputStream din = dataSocket.getInputStream();
                         OutputStream fout = new FileOutputStream(targetFile)) {

                        byte[] buffer = new byte[8192];
                        long totalRead = 0;
                        int n;

                        while ((n = din.read(buffer)) != -1) {
                            fout.write(buffer, 0, n);
                            totalRead += n;

                            final long currentRead = totalRead;
                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speed = (currentRead / 1024.0) / (elapsed / 1000.0); // KB/s

                            int percent = expectedSize > 0 ? (int) Math.min(100, (currentRead * 100) / expectedSize) : 0;
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(percent);
                                progressBar.setString(percent > 0 ? percent + "% (" + formatSize(currentRead) + ")" : formatSize(currentRead));
                                lblProgressStatus.setText(String.format("Đang tải về: %s (%.1f KB/s)", remoteFileName, speed));
                            });
                        }
                        fout.flush();
                    }
                });

                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                log("Tải về hoàn tất: " + targetFile.getName() + " (" + formatSize(targetFile.length()) + " trong " + duration + " ms)");

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("100% Hoàn thành");
                    lblProgressStatus.setText("Đã tải xong: " + targetFile.getName());
                    updateLocalFileList(currentLocalDir);
                });

            } catch (Exception ex) {
                log("Lỗi tải file: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi khi tải tập tin: " + ex.getMessage(),
                            "Lỗi Tải File", JOptionPane.ERROR_MESSAGE);
                    lblProgressStatus.setText("Tải file thất bại!");
                });
            } finally {
                SwingUtilities.invokeLater(() -> setTransferButtonsEnabled(true));
            }
        }, "FTP-Download-Thread").start();
    }

    private void uploadSelectedFile() {
        int row = tblLocal.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin ở bảng máy cục bộ để tải lên!",
                    "Chưa Chọn File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String localFileName = (String) modelLocal.getValueAt(row, 0);
        File localFile = new File(currentLocalDir, localFileName);
        if (!localFile.isFile()) {
            JOptionPane.showMessageDialog(this, "Chỉ có thể tải lên tập tin (không phải thư mục)!",
                    "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setTransferButtonsEnabled(false);
        new Thread(() -> {
            try {
                long totalSize = localFile.length();
                log("Bắt đầu tải lên: " + localFile.getName() + " (" + formatSize(totalSize) + ")");
                long startTime = System.currentTimeMillis();

                executeDataCommand("STOR " + localFile.getName(), dataSocket -> {
                    try (InputStream fin = new FileInputStream(localFile);
                         OutputStream dout = dataSocket.getOutputStream()) {

                        byte[] buffer = new byte[8192];
                        long totalWritten = 0;
                        int n;

                        while ((n = fin.read(buffer)) != -1) {
                            dout.write(buffer, 0, n);
                            totalWritten += n;

                            final long currentWritten = totalWritten;
                            long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                            double speed = (currentWritten / 1024.0) / (elapsed / 1000.0); // KB/s

                            int percent = totalSize > 0 ? (int) Math.min(100, (currentWritten * 100) / totalSize) : 0;
                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(percent);
                                progressBar.setString(percent + "% (" + formatSize(currentWritten) + ")");
                                lblProgressStatus.setText(String.format("Đang tải lên: %s (%.1f KB/s)", localFile.getName(), speed));
                            });
                        }
                        dout.flush();
                    }
                });

                long duration = Math.max(1, System.currentTimeMillis() - startTime);
                log("Tải lên thành công: " + localFile.getName() + " trong " + duration + " ms");

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setString("100% Hoàn tất");
                    lblProgressStatus.setText("Tải lên thành công: " + localFile.getName());
                });

                refreshRemoteFiles();

            } catch (Exception ex) {
                log("Lỗi tải lên: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Lỗi khi tải lên tập tin: " + ex.getMessage(),
                            "Lỗi Upload", JOptionPane.ERROR_MESSAGE);
                    lblProgressStatus.setText("Tải lên thất bại!");
                });
            } finally {
                SwingUtilities.invokeLater(() -> setTransferButtonsEnabled(true));
            }
        }, "FTP-Upload-Thread").start();
    }

    private void deleteSelectedRemoteFile() {
        int row = tblRemote.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tập tin trên Server cần xóa!",
                    "Chưa Chọn File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String remoteName = (String) modelRemote.getValueAt(row, 0);
        int opt = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa tập tin '" + remoteName + "' trên Server không?",
                "Xác Nhận Xóa File", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) {
            return;
        }

        new Thread(() -> {
            try {
                String rep = sendCommand("DELE " + remoteName);
                if (rep.startsWith("250")) {
                    log("Đã xóa file trên Server: " + remoteName);
                    SwingUtilities.invokeLater(() -> {
                        lblProgressStatus.setText("Đã xóa: " + remoteName);
                    });
                    refreshRemoteFiles();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Không thể xóa file: " + rep, "Lỗi Xóa", JOptionPane.ERROR_MESSAGE);
                    });
                }
            } catch (Exception ex) {
                log("Lỗi khi xóa file Server: " + ex.getMessage());
            }
        }).start();
    }

    // =========================================================================
    // CAC THAO TAC TREN MAY CUC BO (LOCAL)
    // =========================================================================

    private void updateLocalFileList(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        currentLocalDir = dir;
        txtLocalPath.setText(dir.getAbsolutePath());
        modelLocal.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            // Hien thu muc truoc, file sau
            for (File f : files) {
                if (f.isDirectory()) {
                    modelLocal.addRow(new Object[]{
                            f.getName(),
                            "<DIR>",
                            DATE_FORMAT.format(new Date(f.lastModified()))
                    });
                }
            }
            for (File f : files) {
                if (f.isFile()) {
                    modelLocal.addRow(new Object[]{
                            f.getName(),
                            formatSize(f.length()),
                            DATE_FORMAT.format(new Date(f.lastModified()))
                    });
                }
            }
        }
    }

    private void chooseLocalFolder() {
        JFileChooser chooser = new JFileChooser(currentLocalDir);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            updateLocalFileList(chooser.getSelectedFile());
        }
    }

    private void goUpLocalFolder() {
        File parent = currentLocalDir.getParentFile();
        if (parent != null && parent.exists()) {
            updateLocalFileList(parent);
        }
    }

    private void handleLocalDoubleClick() {
        int row = tblLocal.getSelectedRow();
        if (row == -1) {
            return;
        }
        String name = (String) modelLocal.getValueAt(row, 0);
        File target = new File(currentLocalDir, name);
        if (target.isDirectory()) {
            updateLocalFileList(target);
        } else if (connected) {
            uploadSelectedFile();
        }
    }

    private void deleteSelectedLocalFile() {
        int row = tblLocal.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tập tin cục bộ cần xóa!",
                    "Chưa Chọn File", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = (String) modelLocal.getValueAt(row, 0);
        File target = new File(currentLocalDir, name);

        int opt = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa tập tin cục bộ '" + target.getName() + "' không?",
                "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            if (target.delete()) {
                log("Đã xóa file cục bộ: " + target.getName());
                updateLocalFileList(currentLocalDir);
            } else {
                JOptionPane.showMessageDialog(this, "Không thể xóa tập tin này!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // =========================================================================
    // HAM TIEN ICH (HELPERS)
    // =========================================================================

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private static long parseSizeFromInfo(String info) {
        if (info == null) return -1;
        String clean = info.replace("bytes", "").trim();
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FTPClientGUI gui = new FTPClientGUI();
            gui.setVisible(true);
        });
    }
}

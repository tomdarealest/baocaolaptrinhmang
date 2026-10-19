package Btapbaocao;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Giao dien do hoa (Swing GUI) cho FTP Server.
 * Cung cap Bang dieu khien bat/tat Server, quan ly danh sach Client dang ket noi,
 * quan ly tai khoan dang nhap, quan ly tap tin chia se va Nhat ky hoat dong thoi gian thuc.
 */
public class FTPServerGUI extends JFrame {

    // --- Cac thanh phan dieu khien Server ---
    private JTextField txtPort;
    private JTextField txtRootDir;
    private JButton btnBrowseRoot;
    private JButton btnToggleServer;
    private JLabel lblServerStatus;
    private JTextArea txtIPs;

    // --- Tab 1: Nhat ky ---
    private JTextArea txtLog;
    private JButton btnClearLog;

    // --- Tab 2: Danh sach Client ---
    private JTable tblClients;
    private DefaultTableModel modelClients;
    private JButton btnKickClient;
    private JButton btnRefreshClients;

    // --- Tab 3: Quan ly tai khoan ---
    private JTable tblUsers;
    private DefaultTableModel modelUsers;
    private JButton btnAddUser;
    private JButton btnRemoveUser;

    // --- Tab 4: File tren Server ---
    private JTable tblServerFiles;
    private DefaultTableModel modelServerFiles;
    private JButton btnRefreshFiles;
    private JButton btnOpenRootFolder;
    private JButton btnDeleteServerFile;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public FTPServerGUI() {
        super("FTP Server Control Panel - Báo Cáo Lập Trình Mạng");
        initUI();
        loadLocalIPs();
        loadUsersTable();
        refreshFilesTable();

        // Ket noi Log callback tu FTPServer vao GUI
        FTPServer.logger = this::appendLog;

        // Ket noi su kien Client ket noi / ngat ket noi
        FTPServer.clientListener = () -> SwingUtilities.invokeLater(this::refreshClientsTable);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 700);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // 1. THANH DIEU KHIEN VA TRANG THAI SERVER (NORTH)
        mainPanel.add(buildControlPanel(), BorderLayout.NORTH);

        // 2. KHUNG CHINH: TABBED PANE (CENTER)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 12f));

        tabbedPane.addTab("📜 Nhật Ký Hoạt Động (Logs)", buildLogPanel());
        tabbedPane.addTab("👥 Client Đang Kết Nối", buildClientsPanel());
        tabbedPane.addTab("🔑 Quản Lý Tài Khoản", buildUsersPanel());
        tabbedPane.addTab("📁 Tập Tin Chia Sẻ (ftp_root)", buildFilesPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Lang nghe su kien dong cua so de dung Server an toan
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (FTPServer.running) {
                    FTPServer.stopServer();
                }
            }
        });
    }

    // --- Thanh dieu khien Server ---
    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Cấu Hình & Trạng Thái Máy Chủ"));

        // Dong 1: Port, Root Dir, Start/Stop button
        JPanel configRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        configRow.add(new JLabel("Cổng lắng nghe (Port):"));
        txtPort = new JTextField(String.valueOf(FTPServer.CONTROL_PORT), 5);
        configRow.add(txtPort);

        configRow.add(new JLabel("Thư mục chia sẻ:"));
        txtRootDir = new JTextField(FTPServer.ROOT.getAbsolutePath(), 25);
        txtRootDir.setEditable(false);
        configRow.add(txtRootDir);

        btnBrowseRoot = new JButton("📂 Đổi Thư Mục...");
        btnBrowseRoot.addActionListener(e -> chooseRootDir());
        configRow.add(btnBrowseRoot);

        btnToggleServer = new JButton("▶ Khởi Động Server");
        btnToggleServer.setBackground(new Color(40, 167, 69));
        btnToggleServer.setForeground(Color.WHITE);
        btnToggleServer.setFont(btnToggleServer.getFont().deriveFont(Font.BOLD));
        btnToggleServer.setFocusPainted(false);
        btnToggleServer.addActionListener(e -> toggleServer());
        configRow.add(btnToggleServer);

        lblServerStatus = new JLabel("● ĐÃ DỪNG");
        lblServerStatus.setForeground(Color.RED);
        lblServerStatus.setFont(lblServerStatus.getFont().deriveFont(Font.BOLD, 13f));
        configRow.add(lblServerStatus);

        panel.add(configRow, BorderLayout.NORTH);

        // Dong 2: Hien thi danh sach IP de chia se cho ban be / client
        JPanel ipRow = new JPanel(new BorderLayout(4, 4));
        ipRow.setBorder(new EmptyBorder(0, 8, 4, 8));
        JLabel lblIpInfo = new JLabel("Địa chỉ IP máy chủ (Dùng IP Radmin 26.x.x.x cho máy khác kết nối):");
        lblIpInfo.setForeground(new Color(0, 102, 204));
        ipRow.add(lblIpInfo, BorderLayout.NORTH);

        txtIPs = new JTextArea(2, 50);
        txtIPs.setEditable(false);
        txtIPs.setBackground(new Color(245, 245, 245));
        txtIPs.setFont(new Font("Consolas", Font.PLAIN, 12));
        ipRow.add(new JScrollPane(txtIPs), BorderLayout.CENTER);

        panel.add(ipRow, BorderLayout.SOUTH);
        return panel;
    }

    // --- Tab 1: Log Panel ---
    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        txtLog = new JTextArea();
        txtLog.setEditable(false);
        txtLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLog.setBackground(new Color(248, 249, 250));
        panel.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        btnClearLog = new JButton("Xóa Nhật Ký");
        btnClearLog.addActionListener(e -> txtLog.setText(""));
        bottom.add(btnClearLog);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 2: Connected Clients ---
    private JPanel buildClientsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        modelClients = new DefaultTableModel(new String[]{"IP & Cổng Client", "Tài Khoản Đăng Nhập", "Thời Điểm Kết Nối"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblClients = new JTable(modelClients);
        tblClients.setRowHeight(22);
        tblClients.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(tblClients), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnKickClient = new JButton("⛔ Ngắt Kết Nối Client Được Chọn");
        btnKickClient.addActionListener(e -> kickSelectedClient());

        btnRefreshClients = new JButton("🔄 Làm Mới");
        btnRefreshClients.addActionListener(e -> refreshClientsTable());

        bottom.add(btnKickClient);
        bottom.add(btnRefreshClients);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 3: Users Management ---
    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        modelUsers = new DefaultTableModel(new String[]{"Tên Đăng Nhập (Username)", "Mật Khẩu (Password)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblUsers = new JTable(modelUsers);
        tblUsers.setRowHeight(22);
        tblUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(tblUsers), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnAddUser = new JButton("➕ Thêm Tài Khoản");
        btnAddUser.addActionListener(e -> addNewUser());

        btnRemoveUser = new JButton("🗑 Xóa Tài Khoản");
        btnRemoveUser.addActionListener(e -> removeSelectedUser());

        bottom.add(btnAddUser);
        bottom.add(btnRemoveUser);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // --- Tab 4: Server Files Explorer ---
    private JPanel buildFilesPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        modelServerFiles = new DefaultTableModel(new String[]{"Tên Tập Tin", "Kích Thước", "Ngày Sửa Đổi Cuối"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tblServerFiles = new JTable(modelServerFiles);
        tblServerFiles.setRowHeight(22);
        tblServerFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        tblServerFiles.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

        panel.add(new JScrollPane(tblServerFiles), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        btnRefreshFiles = new JButton("🔄 Làm Mới");
        btnRefreshFiles.addActionListener(e -> refreshFilesTable());

        btnOpenRootFolder = new JButton("📂 Mở Thư Mục Bằng Explorer");
        btnOpenRootFolder.addActionListener(e -> openRootInDesktop());

        btnDeleteServerFile = new JButton("🗑 Xóa File Trên Server");
        btnDeleteServerFile.addActionListener(e -> deleteSelectedServerFile());

        bottom.add(btnRefreshFiles);
        bottom.add(btnOpenRootFolder);
        bottom.add(btnDeleteServerFile);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    // =========================================================================
    // XU LY BAT / DUNG SERVER
    // =========================================================================

    private void toggleServer() {
        if (!FTPServer.running) {
            startServer();
        } else {
            stopServer();
        }
    }

    private void startServer() {
        String pStr = txtPort.getText().trim();
        int port;
        try {
            port = Integer.parseInt(pStr);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            FTPServer.startServer(port, FTPServer.ROOT);
            btnToggleServer.setText("⏹ Dừng Server");
            btnToggleServer.setBackground(new Color(220, 53, 69));
            lblServerStatus.setText("● ĐANG HOẠT ĐỘNG (Port " + port + ")");
            lblServerStatus.setForeground(new Color(40, 167, 69));
            txtPort.setEnabled(false);
            btnBrowseRoot.setEnabled(false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể khởi động Server:\n" + ex.getMessage(),
                    "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopServer() {
        FTPServer.stopServer();
        btnToggleServer.setText("▶ Khởi Động Server");
        btnToggleServer.setBackground(new Color(40, 167, 69));
        lblServerStatus.setText("● ĐÃ DỪNG");
        lblServerStatus.setForeground(Color.RED);
        txtPort.setEnabled(true);
        btnBrowseRoot.setEnabled(true);
        refreshClientsTable();
    }

    private void chooseRootDir() {
        JFileChooser chooser = new JFileChooser(FTPServer.ROOT);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            FTPServer.ROOT = selected;
            txtRootDir.setText(selected.getAbsolutePath());
            refreshFilesTable();
            appendLog("[HỆ THỐNG] Đã đổi thư mục chia sẻ sang: " + selected.getAbsolutePath());
        }
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = TIME_FORMAT.format(new Date());
            txtLog.append("[" + time + "] " + msg + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // =========================================================================
    // XU LY CLIENTS, USERS, FILES
    // =========================================================================

    private void refreshClientsTable() {
        modelClients.setRowCount(0);
        for (ClientHandler ch : FTPServer.activeClients) {
            String address = ch.getSocket() != null ? String.valueOf(ch.getSocket().getRemoteSocketAddress()) : "(Không xác định)";
            String user = ch.getUsername();
            String time = DATE_FORMAT.format(ch.getConnectedTime());
            modelClients.addRow(new Object[]{address, user, time});
        }
    }

    private void kickSelectedClient() {
        int row = tblClients.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 Client cần ngắt kết nối!",
                    "Chưa Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (row < FTPServer.activeClients.size()) {
            ClientHandler ch = FTPServer.activeClients.get(row);
            ch.disconnect();
            appendLog("[HỆ THỐNG] Quản trị viên đã ngắt kết nối: " + ch.getSocket().getRemoteSocketAddress());
            refreshClientsTable();
        }
    }

    private void loadUsersTable() {
        modelUsers.setRowCount(0);
        for (Map.Entry<String, String> e : FTPServer.USERS.entrySet()) {
            modelUsers.addRow(new Object[]{e.getKey(), e.getValue()});
        }
    }

    private void addNewUser() {
        JTextField uField = new JTextField();
        JPasswordField pField = new JPasswordField();
        Object[] message = {
                "Tên đăng nhập:", uField,
                "Mật khẩu:", pField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Thêm Tài Khoản Mới", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String u = uField.getText().trim();
            String p = new String(pField.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Tên đăng nhập và mật khẩu không được để trống!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            FTPServer.USERS.put(u, p);
            loadUsersTable();
            appendLog("[HỆ THỐNG] Đã thêm tài khoản mới: " + u);
        }
    }

    private void removeSelectedUser() {
        int row = tblUsers.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tài khoản cần xóa!",
                    "Chưa Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String u = (String) modelUsers.getValueAt(row, 0);
        if (u.equalsIgnoreCase("admin")) {
            JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản mặc định 'admin'!", "Cảnh Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int opt = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa tài khoản '" + u + "'?", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            FTPServer.USERS.remove(u);
            loadUsersTable();
            appendLog("[HỆ THỐNG] Đã xóa tài khoản: " + u);
        }
    }

    private void refreshFilesTable() {
        modelServerFiles.setRowCount(0);
        File[] files = FTPServer.ROOT.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    modelServerFiles.addRow(new Object[]{
                            f.getName(),
                            FTPClientGUI.formatSize(f.length()),
                            DATE_FORMAT.format(new Date(f.lastModified()))
                    });
                }
            }
        }
    }

    private void openRootInDesktop() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(FTPServer.ROOT);
            } else {
                JOptionPane.showMessageDialog(this, "Hệ thống không hỗ trợ mở tự động thư mục!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedServerFile() {
        int row = tblServerFiles.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin trên Server cần xóa!",
                    "Chưa Chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = (String) modelServerFiles.getValueAt(row, 0);
        File target = new File(FTPServer.ROOT, name);
        int opt = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa file '" + name + "'?",
                "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            if (target.delete()) {
                appendLog("[HỆ THỐNG] Đã xóa file trên server: " + name);
                refreshFilesTable();
            } else {
                JOptionPane.showMessageDialog(this, "Không thể xóa file này!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadLocalIPs() {
        StringBuilder sb = new StringBuilder();
        try {
            for (java.util.Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (java.util.Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ia = a.nextElement();
                    if (ia instanceof Inet4Address) {
                        String ip = ia.getHostAddress();
                        String tag = ip.startsWith("26.") ? "  <=== [IP Radmin VPN - Cho máy khác kết nối]" : "";
                        sb.append("• ").append(ip).append(" (").append(ni.getDisplayName()).append(")").append(tag).append("\n");
                    }
                }
            }
            txtIPs.setText(sb.toString().trim());
        } catch (Exception ignored) {
            txtIPs.setText("127.0.0.1 (Localhost)");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FTPServerGUI gui = new FTPServerGUI();
            gui.setVisible(true);
        });
    }
}

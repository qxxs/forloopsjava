import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

/**
 * Roblox Rare Username Finder
 * Checks username availability via the Roblox auth API.
 * Run: javac RobloxUsernameFinder.java && java RobloxUsernameFinder
 */
public class RobloxUsernameFinder {

    // ── Colours ──────────────────────────────────────────────────────────────
    static final Color BG        = new Color(15,  17,  26);
    static final Color PANEL     = new Color(22,  27,  42);
    static final Color CARD      = new Color(30,  37,  56);
    static final Color ACCENT    = new Color(99, 102, 241);   // indigo
    static final Color ACCENT2   = new Color(139, 92, 246);   // purple
    static final Color SUCCESS   = new Color(52, 211, 153);
    static final Color DANGER    = new Color(248, 113, 113);
    static final Color WARN      = new Color(251, 191, 36);
    static final Color TEXT      = new Color(226, 232, 240);
    static final Color SUBTEXT   = new Color(148, 163, 184);
    static final Color BORDER    = new Color(51,  65,  92);
    static final Font  FONT_H1   = new Font("Segoe UI", Font.BOLD,  22);
    static final Font  FONT_H2   = new Font("Segoe UI", Font.BOLD,  15);
    static final Font  FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font  FONT_MONO = new Font("Consolas",  Font.PLAIN, 13);

    // ── Roblox API ────────────────────────────────────────────────────────────
    static final String VALIDATE_URL =
        "https://auth.roblox.com/v1/usernames/validate";
    // Delay between API calls in ms (be respectful to Roblox servers)
    static final int DELAY_MS = 350;

    // ── State ─────────────────────────────────────────────────────────────────
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JLabel statsLabel;
    private final AtomicInteger available = new AtomicInteger(0);
    private final AtomicInteger checked   = new AtomicInteger(0);
    private final AtomicInteger total     = new AtomicInteger(0);
    private volatile ExecutorService executor;
    private volatile boolean running = false;
    private final HttpClient http;

    // ── Constructor ───────────────────────────────────────────────────────────
    public RobloxUsernameFinder() {
        http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        tableModel = buildTableModel();
        statusLabel = styledLabel("Ready", SUBTEXT, FONT_BODY);
        progressBar = buildProgressBar();
        statsLabel  = styledLabel("Available: 0  |  Checked: 0 / 0", SUBTEXT, FONT_BODY);

        JFrame frame = new JFrame("✦ Roblox Rare Username Finder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(960, 680));
        frame.setPreferredSize(new Dimension(1100, 740));
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildCenter(), BorderLayout.CENTER);
        frame.add(buildFooter(), BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ── UI Builders ───────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(18, 28, 18, 28)
        ));

        JLabel title = styledLabel("✦ Roblox Rare Username Finder", TEXT, FONT_H1);
        JLabel sub   = styledLabel("Find available Roblox usernames before anyone else", SUBTEXT, FONT_BODY);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(sub);
        p.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton stopBtn = accentButton("⏹ Stop", DANGER);
        stopBtn.addActionListener(e -> stopChecking());

        JButton exportBtn = accentButton("💾 Export Available", SUCCESS);
        exportBtn.addActionListener(e -> exportResults());

        right.add(stopBtn);
        right.add(exportBtn);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JSplitPane buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(), buildResultPanel());
        split.setDividerLocation(380);
        split.setDividerSize(4);
        split.setBackground(BG);
        split.setBorder(null);
        return split;
    }

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new MatteBorder(0, 0, 0, 1, BORDER));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PANEL);
        tabs.setForeground(TEXT);
        tabs.setFont(FONT_BODY);
        UIManager.put("TabbedPane.selected",    CARD);
        UIManager.put("TabbedPane.background",  PANEL);
        UIManager.put("TabbedPane.foreground",  TEXT);

        tabs.addTab("⚡ Generate",  buildGenerateTab());
        tabs.addTab("📄 From File", buildFileTab());

        p.add(tabs);
        return p;
    }

    // ── Generate Tab ──────────────────────────────────────────────────────────

    private JPanel buildGenerateTab() {
        JPanel root = new JPanel();
        root.setBackground(PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- Length ---
        root.add(sectionLabel("Username Length"));
        root.add(Box.createVerticalStrut(8));
        JPanel lengthRow = hPanel();
        JSpinner minLen = spinner(3, 3, 20);
        JSpinner maxLen = spinner(5, 3, 20);
        lengthRow.add(styledLabel("Min:", SUBTEXT, FONT_BODY));
        lengthRow.add(Box.createHorizontalStrut(6));
        lengthRow.add(minLen);
        lengthRow.add(Box.createHorizontalStrut(14));
        lengthRow.add(styledLabel("Max:", SUBTEXT, FONT_BODY));
        lengthRow.add(Box.createHorizontalStrut(6));
        lengthRow.add(maxLen);
        root.add(lengthRow);
        root.add(Box.createVerticalStrut(16));

        // --- Prefix / Suffix ---
        root.add(sectionLabel("Prefix  (optional)"));
        root.add(Box.createVerticalStrut(6));
        JTextField prefixField = styledTextField("e.g. xX  or  cool");
        root.add(prefixField);
        root.add(Box.createVerticalStrut(12));

        root.add(sectionLabel("Suffix  (optional)"));
        root.add(Box.createVerticalStrut(6));
        JTextField suffixField = styledTextField("e.g. 123  or  _YT");
        root.add(suffixField);
        root.add(Box.createVerticalStrut(16));

        // --- Character Sets ---
        root.add(sectionLabel("Character Sets"));
        root.add(Box.createVerticalStrut(8));
        JCheckBox cbLower = checkBox("Lowercase (a-z)", true);
        JCheckBox cbUpper = checkBox("Uppercase (A-Z)", false);
        JCheckBox cbDigit = checkBox("Digits (0-9)", false);
        JCheckBox cbUnder = checkBox("Underscores (_)", false);
        root.add(cbLower); root.add(cbUpper);
        root.add(cbDigit); root.add(cbUnder);
        root.add(Box.createVerticalStrut(16));

        // --- Max to check ---
        root.add(sectionLabel("Max usernames to check"));
        root.add(Box.createVerticalStrut(6));
        JSpinner maxCheck = spinner(500, 10, 100000);
        ((JSpinner.DefaultEditor) maxCheck.getEditor()).getTextField().setColumns(8);
        JPanel maxRow = hPanel();
        maxRow.add(maxCheck);
        root.add(maxRow);
        root.add(Box.createVerticalStrut(24));

        // --- Start button ---
        JButton startBtn = bigButton("▶  Start Checking");
        startBtn.addActionListener(e -> {
            String prefix = prefixField.getText().trim();
            String suffix = suffixField.getText().trim();
            int lo = (int) minLen.getValue();
            int hi = (int) maxLen.getValue();
            if (lo > hi) { showError("Min length must be ≤ Max length."); return; }

            StringBuilder chars = new StringBuilder();
            if (cbLower.isSelected()) chars.append("abcdefghijklmnopqrstuvwxyz");
            if (cbUpper.isSelected()) chars.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            if (cbDigit.isSelected()) chars.append("0123456789");
            if (cbUnder.isSelected()) chars.append("_");
            if (chars.length() == 0) { showError("Select at least one character set."); return; }

            int max = (int) maxCheck.getValue();
            List<String> names = generateUsernames(prefix, suffix, lo, hi,
                                                   chars.toString(), max);
            startChecking(names);
        });
        root.add(startBtn);
        root.add(Box.createVerticalGlue());
        return root;
    }

    // ── File Tab ──────────────────────────────────────────────────────────────

    private JPanel buildFileTab() {
        JPanel root = new JPanel();
        root.setBackground(PANEL);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));

        root.add(sectionLabel("Load usernames from .txt file"));
        root.add(Box.createVerticalStrut(8));
        JLabel hint = styledLabel("One username per line.", SUBTEXT, FONT_BODY);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(hint);
        root.add(Box.createVerticalStrut(14));

        JTextField pathField = styledTextField("No file selected…");
        pathField.setEditable(false);
        root.add(pathField);
        root.add(Box.createVerticalStrut(10));

        JButton browseBtn = accentButton("📂 Browse…", ACCENT);
        browseBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel fileInfo = styledLabel("", SUBTEXT, FONT_BODY);
        fileInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        final List<String> fileNames = new ArrayList<>();

        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
            fc.setDialogTitle("Select username list");
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                pathField.setText(f.getAbsolutePath());
                fileNames.clear();
                try {
                    List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                    for (String l : lines) {
                        String s = l.trim();
                        if (!s.isEmpty()) fileNames.add(s);
                    }
                    fileInfo.setText(fileNames.size() + " usernames loaded.");
                } catch (IOException ex) {
                    showError("Could not read file: " + ex.getMessage());
                }
            }
        });
        root.add(browseBtn);
        root.add(Box.createVerticalStrut(8));
        root.add(fileInfo);
        root.add(Box.createVerticalStrut(16));

        // Manual entry area
        root.add(sectionLabel("— or paste / type usernames —"));
        root.add(Box.createVerticalStrut(8));
        JTextArea area = new JTextArea(8, 20);
        area.setBackground(CARD);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setFont(FONT_MONO);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new LineBorder(BORDER));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        root.add(sp);
        root.add(Box.createVerticalStrut(20));

        JButton startBtn = bigButton("▶  Check These Usernames");
        startBtn.addActionListener(e -> {
            List<String> names = new ArrayList<>(fileNames);
            // Also add manually typed ones
            for (String l : area.getText().split("\\r?\\n")) {
                String s = l.trim();
                if (!s.isEmpty() && !names.contains(s)) names.add(s);
            }
            if (names.isEmpty()) { showError("No usernames to check."); return; }
            startChecking(names);
        });
        root.add(startBtn);
        root.add(Box.createVerticalGlue());
        return root;
    }

    // ── Results Panel ─────────────────────────────────────────────────────────

    private JPanel buildResultPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        // Header bar above table
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(PANEL);
        topBar.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(10, 16, 10, 16)
        ));
        topBar.add(styledLabel("Results", TEXT, FONT_H2), BorderLayout.WEST);

        JButton clearBtn = accentButton("🗑 Clear", SUBTEXT);
        clearBtn.addActionListener(e -> clearResults());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);

        // Filter dropdown
        JComboBox<String> filterBox = new JComboBox<>(
            new String[]{"Show All", "Available Only", "Taken Only", "Invalid Only"});
        filterBox.setBackground(CARD);
        filterBox.setForeground(TEXT);
        filterBox.setFont(FONT_BODY);
        filterBox.addActionListener(e -> applyFilter((String) filterBox.getSelectedItem()));

        btns.add(filterBox);
        btns.add(clearBtn);
        topBar.add(btns, BorderLayout.EAST);
        p.add(topBar, BorderLayout.NORTH);

        // Table
        _table = new JTable(tableModel) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c2 = super.prepareRenderer(r, row, col);
                c2.setBackground(row % 2 == 0 ? CARD : BG);
                c2.setForeground(TEXT);
                // colour status column
                if (col == 1) {
                    String v = (String) getValueAt(row, col);
                    if ("AVAILABLE".equals(v)) c2.setForeground(SUCCESS);
                    else if ("TAKEN".equals(v)) c2.setForeground(DANGER);
                    else if ("INVALID".equals(v)) c2.setForeground(WARN);
                }
                return c2;
            }
        };
        _table.setBackground(CARD);
        _table.setForeground(TEXT);
        _table.setFont(FONT_MONO);
        _table.setRowHeight(26);
        _table.setGridColor(BORDER);
        _table.setShowGrid(true);
        _table.getTableHeader().setBackground(PANEL);
        _table.getTableHeader().setForeground(SUBTEXT);
        _table.getTableHeader().setFont(FONT_BODY);
        _table.getTableHeader().setBorder(new MatteBorder(0, 0, 1, 0, BORDER));
        _table.setSelectionBackground(ACCENT);
        _table.setSelectionForeground(Color.WHITE);

        // Column widths
        _table.getColumnModel().getColumn(0).setPreferredWidth(200);
        _table.getColumnModel().getColumn(1).setPreferredWidth(100);
        _table.getColumnModel().getColumn(2).setPreferredWidth(220);

        JScrollPane scroll = new JScrollPane(_table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(null);
        p.add(scroll, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(10, 20, 10, 20)
        ));
        p.add(statsLabel,  BorderLayout.WEST);
        p.add(progressBar, BorderLayout.CENTER);
        p.add(statusLabel, BorderLayout.EAST);
        return p;
    }

    // ── Checking Logic ────────────────────────────────────────────────────────

    private void startChecking(List<String> names) {
        if (running) { showError("Already running. Press Stop first."); return; }
        // Shut down any previous executor cleanly before starting a new one
        if (executor != null && !executor.isTerminated()) executor.shutdownNow();
        running = true;
        available.set(0);
        checked.set(0);
        total.set(names.size());
        progressBar.setMaximum(names.size());
        progressBar.setValue(0);
        updateStats();
        setStatus("Checking " + names.size() + " usernames…", ACCENT);

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            for (String name : names) {
                if (!running) break;
                CheckResult result = checkUsername(name);
                SwingUtilities.invokeLater(() -> {
                    tableModel.addRow(new Object[]{name, result.status, result.message});
                    if ("AVAILABLE".equals(result.status)) available.incrementAndGet();
                    checked.incrementAndGet();
                    progressBar.setValue(checked.get());
                    updateStats();
                });
                try { Thread.sleep(DELAY_MS); } catch (InterruptedException ie) { break; }
            }
            SwingUtilities.invokeLater(() -> {
                running = false;
                setStatus("Done. Found " + available.get() + " available names.", SUCCESS);
            });
        });
    }

    private void stopChecking() {
        running = false;
        if (executor != null) executor.shutdownNow();
        setStatus("Stopped.", WARN);
    }

    /** Calls the Roblox username validation endpoint. */
    private CheckResult checkUsername(String username) {
        try {
            String body = String.format("{\"username\":\"%s\",\"context\":\"Username\"}",
                username.replace("\"", "\\\""));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(VALIDATE_URL))
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .header("User-Agent",   "RobloxUsernameFinder/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String json = resp.body();
            int code = parseCode(json);
            return switch (code) {
                case 0  -> new CheckResult("AVAILABLE", "Username is valid and available");
                case 1  -> new CheckResult("TAKEN",     "Username is already taken");
                case 2  -> new CheckResult("INVALID",   "Username has inappropriate content");
                case 3  -> new CheckResult("INVALID",   "Username not appropriate for Roblox");
                case 4  -> new CheckResult("INVALID",   "Username starts/ends with _");
                case 5  -> new CheckResult("INVALID",   "Username is too short or too long");
                case 6  -> new CheckResult("INVALID",   "Username has spaces");
                case 10 -> new CheckResult("TAKEN",     "Username is held by a deleted account");
                default -> new CheckResult("INVALID",   "Code " + code + ": " + json);
            };
        } catch (Exception ex) {
            return new CheckResult("ERROR", ex.getMessage());
        }
    }

    private int parseCode(String json) {
        // Simple extraction: "code":N
        int idx = json.indexOf("\"code\":");
        if (idx < 0) return -1;
        int start = idx + 7;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || (end == start && json.charAt(end) == '-')))
            end++;
        try { return Integer.parseInt(json.substring(start, end).trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    // ── Username Generator ────────────────────────────────────────────────────

    private List<String> generateUsernames(String prefix, String suffix,
                                           int minLen, int maxLen,
                                           String charset, int maxCount) {
        List<String> result = new ArrayList<>();
        Random rng = new Random();
        Set<String> seen = new LinkedHashSet<>();

        // Determine how many chars the generated middle needs
        int maxMiddle = maxLen - prefix.length() - suffix.length();
        int minMiddle = minLen - prefix.length() - suffix.length();
        if (maxMiddle < 0) maxMiddle = 0;
        if (minMiddle < 0) minMiddle = 0;

        char[] chars = charset.toCharArray();

        // Try to enumerate short combos first, then fall back to random
        if (maxMiddle <= 4 && chars.length <= 36 &&
                Math.pow(chars.length, maxMiddle) <= 500_000) {
            // full enumeration
            for (int len = minMiddle; len <= maxMiddle && result.size() < maxCount; len++) {
                enumerate(prefix, suffix, chars, len, new StringBuilder(), seen, result, maxCount);
            }
        }
        // Fill remainder randomly (only when maxMiddle >= minMiddle)
        if (maxMiddle >= minMiddle) {
            int attempts = 0;
            int range = maxMiddle - minMiddle + 1;
            while (result.size() < maxCount && attempts < maxCount * 10) {
                int midLen = minMiddle + rng.nextInt(range);
                StringBuilder sb = new StringBuilder(prefix);
                for (int i = 0; i < midLen; i++) sb.append(chars[rng.nextInt(chars.length)]);
                sb.append(suffix);
                String name = sb.toString();
                if (!seen.contains(name)) { seen.add(name); result.add(name); }
                attempts++;
            }
        }
        return result;
    }

    private void enumerate(String prefix, String suffix, char[] chars,
                           int remaining, StringBuilder current,
                           Set<String> seen, List<String> result, int maxCount) {
        if (result.size() >= maxCount) return;
        if (remaining == 0) {
            String name = prefix + current.toString() + suffix;
            if (!seen.contains(name)) { seen.add(name); result.add(name); }
            return;
        }
        for (char c : chars) {
            current.append(c);
            enumerate(prefix, suffix, chars, remaining - 1, current, seen, result, maxCount);
            current.deleteCharAt(current.length() - 1);
        }
    }

    // ── Filter / Export / Clear ───────────────────────────────────────────────

    private void applyFilter(String filter) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        RowFilter<DefaultTableModel, Object> rf = switch (filter) {
            case "Available Only" -> RowFilter.regexFilter("^AVAILABLE$", 1);
            case "Taken Only"     -> RowFilter.regexFilter("^TAKEN$", 1);
            case "Invalid Only"   -> RowFilter.regexFilter("^INVALID$", 1);
            default               -> null;
        };
        sorter.setRowFilter(rf);
        // Apply sorter to the actual table - get it from the scroll pane in result panel
        // We'll look for the table in the hierarchy; simplest: keep a reference.
        // (handled via the table being created inline – we store sorter in field)
        _lastSorter = sorter;
        if (_table != null) _table.setRowSorter(sorter);
    }

    // Keep references for filter and table
    private TableRowSorter<DefaultTableModel> _lastSorter;
    private JTable _table;

    private void exportResults() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export available usernames");
        fc.setSelectedFile(new File("available_usernames.txt"));
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter pw = new PrintWriter(fc.getSelectedFile(), StandardCharsets.UTF_8)) {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                if ("AVAILABLE".equals(tableModel.getValueAt(r, 1))) {
                    pw.println(tableModel.getValueAt(r, 0));
                }
            }
            JOptionPane.showMessageDialog(null, "Exported successfully!", "Done",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void clearResults() {
        tableModel.setRowCount(0);
        available.set(0);
        checked.set(0);
        total.set(0);
        progressBar.setValue(0);
        updateStats();
        setStatus("Ready", SUBTEXT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DefaultTableModel buildTableModel() {
        return new DefaultTableModel(
            new String[]{"Username", "Status", "Details"}, 0) {
            @Override public Class<?> getColumnClass(int c) { return String.class; }
        };
    }

    private void updateStats() {
        statsLabel.setText(String.format("Available: %d  |  Checked: %d / %d",
            available.get(), checked.get(), total.get()));
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ── Widget Factories ──────────────────────────────────────────────────────

    private JLabel styledLabel(String text, Color fg, Font font) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setFont(font);
        return l;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = styledLabel(text, SUBTEXT, FONT_BODY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JProgressBar buildProgressBar() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setStringPainted(false);
        pb.setBackground(CARD);
        pb.setForeground(ACCENT);
        pb.setBorderPainted(false);
        pb.setPreferredSize(new Dimension(300, 8));
        return pb;
    }

    private JSpinner spinner(int val, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        s.setBackground(CARD);
        s.setForeground(TEXT);
        s.setFont(FONT_BODY);
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setBackground(CARD);
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setForeground(TEXT);
        s.setMaximumSize(new Dimension(80, 30));
        return s;
    }

    private JTextField styledTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setBackground(CARD);
        tf.setForeground(TEXT);
        tf.setCaretColor(TEXT);
        tf.setFont(FONT_MONO);
        tf.setBorder(new CompoundBorder(
            new LineBorder(BORDER), new EmptyBorder(6, 10, 6, 10)));
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        // placeholder
        tf.setText(placeholder);
        tf.setForeground(SUBTEXT);
        tf.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (tf.getText().equals(placeholder)) { tf.setText(""); tf.setForeground(TEXT); }
            }
            @Override public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) { tf.setText(placeholder); tf.setForeground(SUBTEXT); }
            }
        });
        return tf;
    }

    private JCheckBox checkBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setBackground(PANEL);
        cb.setForeground(TEXT);
        cb.setFont(FONT_BODY);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        return cb;
    }

    private JButton accentButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(CARD);
        b.setForeground(fg);
        b.setFont(FONT_BODY);
        b.setBorder(new CompoundBorder(
            new LineBorder(BORDER), new EmptyBorder(6, 14, 6, 14)));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton bigButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setBorder(new EmptyBorder(12, 24, 12, 24));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(ACCENT2); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(ACCENT); }
        });
        return b;
    }

    private JPanel hPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    // ── Result record ─────────────────────────────────────────────────────────
    static class CheckResult {
        final String status, message;
        CheckResult(String s, String m) { status = s; message = m; }
    }

    // ── Entry Point ───────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            // Enable hardware acceleration hints
            System.setProperty("sun.java2d.opengl", "true");
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Non-fatal; proceed with default L&F
        }

        // Override key UI defaults for dark theme
        UIManager.put("Panel.background",         BG);
        UIManager.put("ScrollPane.background",    BG);
        UIManager.put("Viewport.background",      BG);
        UIManager.put("OptionPane.background",    PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ComboBox.background",      CARD);
        UIManager.put("ComboBox.foreground",      TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("List.background",          CARD);
        UIManager.put("List.foreground",          TEXT);

        SwingUtilities.invokeLater(RobloxUsernameFinder::new);
    }
}

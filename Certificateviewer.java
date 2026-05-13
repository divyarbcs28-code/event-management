package thread;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.net.URISyntaxException;
import javax.imageio.ImageIO;

public class CertificateViewer extends JDialog {

    // ── certificate data ──────────────────────────────────────────
    public static class CertData {
        public int    certId;
        public String studentName;
        public String eventName;
        public String certType;   // "Participant" | "Winner" | "Runner-up"
        public String issueDate;
        public String deptName;
    }

    // ── colours ───────────────────────────────────────────────────
    static final Color BLUE_DARK  = new Color(26,  58, 107);
    static final Color BLUE_MID   = new Color(58, 114, 181);
    static final Color BLUE_LIGHT = new Color(74,  90, 128);
    static final Color BLUE_MUTED = new Color(122, 138, 170);
    static final Color GOLD       = new Color(160, 110,   0);
    static final Color WHITE      = Color.WHITE;
    static final Color PAGE_BG    = new Color(235, 238, 245);

    // ── ACTUAL certificate render size (internal drawing canvas) ──
    static final int CERT_W = 900;
    static final int CERT_H = 636;

    // ── DISPLAY size — fits on screen with room for top/bottom bars
    // The cert is scaled DOWN to fit, but rendered at full res for export
    static final int DISP_W = 760;
    static final int DISP_H = 537;   // maintains 900:636 ratio

    // ── Fixed window layout ───────────────────────────────────────
    static final int TOP_H = 50;     // top bar height
    static final int BOT_H = 58;     // bottom bar height
    static final int PAD   = 12;     // padding above/below cert
    static final int WIN_W = DISP_W + 40;
    static final int WIN_H = 740;

    

    
 // ── TEXT POSITIONS ─────────────────────────────────────

 // Student name (center blank line)
 static final int NAME_Y = 315;

 // Event name
 static final int EVENT_X = 190;
 static final int EVENT_Y = 520;

 // Date
 static final int DATE_X = 730;
 static final int DATE_Y = 520;

 // Certificate ID
 static final int CERT_ID_X = 830;
 static final int CERT_ID_Y = 145;

 // Awarded date (top right)
 static final int AWARD_DATE_X = 830;
 static final int AWARD_DATE_Y = 165;

    private final CertData      data;
    private       BufferedImage templateImage;
    private       BufferedImage certImage;

    // ── static factory ────────────────────────────────────────────
    public static void show(Frame owner, CertData data) {
        new CertificateViewer(owner, data).setVisible(true);
    }

    // ── constructor ───────────────────────────────────────────────
    public CertificateViewer(Frame owner, CertData data) {
        super(owner, "Certificate \u2014 " + data.certType, true);
        this.data = data;
        templateImage = loadTemplate();
        certImage=renderAtScale(2);
        buildUI();
    }

    // =============================================================
    //  UI  —  null layout, every pixel controlled
    // =============================================================
    private void buildUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(WIN_W, WIN_H);
        setLocationRelativeTo(getOwner());

        JPanel content = new JPanel(null);
        content.setBackground(PAGE_BG);
        setContentPane(content);

        // ── top bar ───────────────────────────────────────────────
        JPanel topBar = new JPanel(null);
        topBar.setBackground(WHITE);
        topBar.setBounds(0, 0, WIN_W, TOP_H);
        topBar.setBorder(BorderFactory.createMatteBorder(
                         0, 0, 1, 0, new Color(200, 210, 225)));
        content.add(topBar);

        JLabel titleLbl = new JLabel("Certificate of " + data.certType);
        titleLbl.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 16));
        titleLbl.setForeground(BLUE_DARK);
        titleLbl.setBounds(16, 12, 500, 26);
        topBar.add(titleLbl);

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        closeBtn.setForeground(BLUE_DARK);
        closeBtn.setBackground(PAGE_BG);
        closeBtn.setFocusPainted(false);
        closeBtn.setBounds(WIN_W - 86, 11, 68, 28);
        closeBtn.addActionListener(e -> dispose());
        topBar.add(closeBtn);

        // ── certificate display — centred between top and bottom bars
        int certX = (WIN_W - DISP_W) / 2;
        int certY = TOP_H + PAD;

        JPanel certPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(certImage, 0, 0, getWidth(), getHeight(), null);
            }
            };
        certPanel.setOpaque(true);
        certPanel.setBounds(certX, certY, DISP_W, DISP_H);
        content.add(certPanel);

        // ── bottom bar — always at WIN_H - BOT_H ─────────────────
        int botY = WIN_H - BOT_H - 35;

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        bottomBar.setBackground(PAGE_BG);
        bottomBar.setBounds(0, botY, WIN_W, BOT_H);
        bottomBar.setBorder(BorderFactory.createMatteBorder(
                            1, 0, 0, 0, new Color(210, 218, 228)));
        content.add(bottomBar);

        JButton btnPng = buildBtn("Save as PNG", true);
        JButton btnJpg = buildBtn("Save as JPG", false);
        JButton btnPdf = buildBtn("Print / PDF", false);
        btnPng.addActionListener(e -> saveImage("PNG"));
        btnJpg.addActionListener(e -> saveImage("JPG"));
        btnPdf.addActionListener(e -> printCert());
        bottomBar.add(btnPng);
        bottomBar.add(btnJpg);
        bottomBar.add(btnPdf);
    }

    // =============================================================
    //  RENDER  —  draws onto a CERT_W x CERT_H canvas
    //  scale=1 → 900×636 for screen, scale=2 → 1800×1272 for export
    // =============================================================
    private BufferedImage renderAtScale(int scale) {
        int w = CERT_W * scale;
        int h = CERT_H * scale;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // always white fill first
        g.setColor(WHITE);
        g.fillRect(0, 0, w, h);

        g.scale(scale, scale);

        if (templateImage != null)
            g.drawImage(templateImage, 0, 0, CERT_W, CERT_H, null);
        else
            drawFallbackBackground(g);

        drawTextOverlay(g);

        g.dispose();
        return img;
    }

    // =============================================================
    //  TEXT OVERLAY
    //  Only writes into blank spaces the template leaves empty.
    //  The template already has: title, "This is to certify that",
    //  "For special achievements...", two signature lines.
    //  We write: student name, event name, date, cert ID.
    // =============================================================
    private void drawTextOverlay(Graphics2D g) {

    final int cx = CERT_W / 2;

    // ── Student Name ─────────────────────────────
    g.setFont(new Font("Georgia", Font.BOLD, 30));
    g.setColor(BLUE_DARK);

    String name = nvl(data.studentName, "________________");

    drawCentred(g, name, cx, NAME_Y);

    // ── Event Name ───────────────────────────────
    g.setFont(new Font("Georgia", Font.BOLD | Font.ITALIC, 20));
    g.setColor(BLUE_DARK);

    String ev = nvl(data.eventName, "________________");

    g.drawString(ev, EVENT_X, EVENT_Y);

    // ── Department ───────────────────────────────
    if (data.deptName != null && !data.deptName.isEmpty()) {

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(BLUE_MUTED);

        drawCentred(g,
                "Department of " + data.deptName,
                cx,
                EVENT_Y + 30);
    }

    // ── Bottom Date ──────────────────────────────
    if (data.issueDate != null) {

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(BLUE_DARK);

        g.drawString(data.issueDate, DATE_X, DATE_Y);
    }

    // ── Certificate ID ───────────────────────────
    g.setFont(new Font("SansSerif", Font.PLAIN, 12));
    g.setColor(new Color(80, 90, 110));

    String idStr =
            "CES-" + String.format("%04d", data.certId);

    FontMetrics fm = g.getFontMetrics();

    g.drawString(
            idStr,
            CERT_ID_X - fm.stringWidth(idStr),
            CERT_ID_Y
    );

    // ── Award Date Top Right ─────────────────────
    if (data.issueDate != null) {

        FontMetrics fm2 = g.getFontMetrics();

        g.drawString(
                data.issueDate,
                AWARD_DATE_X - fm2.stringWidth(data.issueDate),
                AWARD_DATE_Y
        );
    }
}

    // =============================================================
    //  FALLBACK BACKGROUND (when no template is found)
    // =============================================================
    private void drawFallbackBackground(Graphics2D g) {
        final int W = CERT_W, H = CERT_H, B = 22;

        g.setColor(WHITE);
        g.fillRect(0, 0, W, H);

        g.setColor(BLUE_MID);
        g.fillRect(0,     0,     W, B);
        g.fillRect(0,     H - B, W, B);
        g.fillRect(0,     0,     B, H);
        g.fillRect(W - B, 0,     B, H);

        g.setColor(new Color(100, 150, 210, 150));
        g.fillPolygon(new int[]{0,    B*5, 0},   new int[]{0, 0, B*5},   3);
        g.fillPolygon(new int[]{W,    W-B*5, W}, new int[]{0, 0, B*5},   3);
        g.setColor(new Color(80, 120, 180, 100));
        g.fillPolygon(new int[]{W,    W-B*3, W}, new int[]{H, H, H-B*3}, 3);
        g.fillPolygon(new int[]{0,    B*3,   0}, new int[]{H, H, H-B*3}, 3);

        g.setFont(new Font("Georgia", Font.BOLD, 160));
        g.setColor(new Color(58, 114, 181, 12));
        FontMetrics wm = g.getFontMetrics();
        g.drawString("CES", W/2 - wm.stringWidth("CES")/2, H/2 + wm.getAscent()/3);

        int hx = B + 14, hy = B + 12;
        g.setColor(BLUE_MID);
        g.fillRoundRect(hx, hy, 28, 28, 5, 5);
        g.setColor(WHITE);
        g.setFont(new Font("Georgia", Font.BOLD, 10));
        FontMetrics lf = g.getFontMetrics();
        g.drawString("CES", hx + 14 - lf.stringWidth("CES")/2, hy + 19);
        g.setColor(BLUE_DARK);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("Campus Event System", hx + 34, hy + 12);
        g.setColor(BLUE_MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString("Certification Authority", hx + 34, hy + 26);

        String title = titleFor(data.certType);
        g.setFont(new Font("Georgia", Font.BOLD, 34));
        g.setColor(BLUE_DARK);
        FontMetrics tf = g.getFontMetrics();
        g.drawString(title, W/2 - tf.stringWidth(title)/2, B + 110);

        g.setFont(new Font("Georgia", Font.ITALIC, 14));
        g.setColor(BLUE_LIGHT);
        String certify = "This is to certify that";
        FontMetrics cf = g.getFontMetrics();
        g.drawString(certify, W/2 - cf.stringWidth(certify)/2, B + 152);

        String body = bodyFor(data.certType);
        g.setFont(new Font("Georgia", Font.PLAIN, 14));
        g.setColor(BLUE_LIGHT);
        FontMetrics bf = g.getFontMetrics();
        g.drawString(body, W/2 - bf.stringWidth(body)/2, EVENT_Y + 36);

        g.setColor(new Color(58, 114, 181, 60));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(W/2 - 200, EVENT_Y + 55, W/2 + 200, EVENT_Y + 55);

        int sigY = H - B - 62;
        sigLine(g, W/4,   sigY, "Organiser");
        sigLine(g, 3*W/4, sigY, "Principal");

        g.setFont(new Font("SansSerif", Font.ITALIC, 10));
        g.setColor(new Color(160, 170, 190));
        String tag = "Campus Event System  \u00B7  Excellence in Participation";
        FontMetrics tgf = g.getFontMetrics();
        g.drawString(tag, W/2 - tgf.stringWidth(tag)/2, H - B/2 - 4);
    }

    private static void sigLine(Graphics2D g, int cx, int y, String role) {
        g.setColor(new Color(58, 114, 181, 130));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx - 60, y, cx + 60, y);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(BLUE_MUTED);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(role, cx - fm.stringWidth(role)/2, y + 16);
    }

    // =============================================================
    //  TEMPLATE LOADER
    // =============================================================
    private static BufferedImage loadTemplate() {
        String[] bases = {
            "certificate_template", "Certificate_Template",
            "certificate", "Certificate", "cert_template", "template"
        };
        String[] exts = { ".jpeg", ".jpg", ".png", ".JPEG", ".JPG", ".PNG" };

        java.util.List<File> dirs = new java.util.ArrayList<>();
        dirs.add(new File("."));
        dirs.add(new File(System.getProperty("user.dir")));
        try {
            File jarDir = new File(CertificateViewer.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParentFile();
            if (jarDir != null) dirs.add(jarDir);
        } catch (URISyntaxException | NullPointerException ignored) {}

        for (File dir : dirs)
            for (String base : bases)
                for (String ext : exts) {
                    File f = new File(dir, base + ext);
                    if (f.exists()) {
                        try {
                            BufferedImage img = ImageIO.read(f);
                            if (img != null) {
                                System.out.println("[Cert] Loaded: " + f.getAbsolutePath());
                                return img;
                            }
                        } catch (IOException ignored) {}
                    }
                }

        for (String base : bases)
            for (String ext : exts)
                try (InputStream is = CertificateViewer.class
                        .getResourceAsStream("/" + base + ext)) {
                    if (is != null) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) return img;
                    }
                } catch (IOException ignored) {}

        System.err.println("[Cert] Template not found. Using fallback design.");
        System.err.println("[Cert] Put certificate_template.jpeg in: "
            + new File(".").getAbsolutePath());
        return null;
    }

    // =============================================================
    //  SAVE
    // =============================================================
    private void saveImage(String fmt) {
        String ext = fmt.equalsIgnoreCase("JPG") ? "jpg" : "png";
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Certificate_" + safeName(data.studentName) + "." + ext));
        fc.setDialogTitle("Save Certificate as " + fmt.toUpperCase());
        fc.setFileFilter(new FileNameExtensionFilter(fmt + " image", ext));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + ext))
            file = new File(file.getAbsolutePath() + "." + ext);

        try {
            BufferedImage hi = renderAtScale(2);
            boolean ok = ImageIO.write(hi,
                fmt.equalsIgnoreCase("JPG") ? "JPEG" : "PNG", file);
            if (!ok) throw new IOException("No writer for: " + fmt);
            JOptionPane.showMessageDialog(this,
                "Saved:\n" + file.getAbsolutePath(),
                "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Error: " + ex.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    // =============================================================
    //  PRINT / PDF
    // =============================================================
    private void printCert() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf  = job.defaultPage();
        Paper paper    = pf.getPaper();
        double pw = 842, ph = 595;
        paper.setSize(pw, ph);
        paper.setImageableArea(18, 18, pw - 36, ph - 36);
        pf.setOrientation(PageFormat.LANDSCAPE);
        pf.setPaper(paper);

        job.setPrintable((gfx, fmt, idx) -> {
            if (idx > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) gfx;
            g2.setColor(WHITE);
            g2.fillRect(0, 0, (int) fmt.getWidth(), (int) fmt.getHeight());
            g2.translate(fmt.getImageableX(), fmt.getImageableY());
            double iw = fmt.getImageableWidth(), ih = fmt.getImageableHeight();
            double s  = Math.min(iw / CERT_W, ih / CERT_H);
            g2.translate((iw - CERT_W * s) / 2, (ih - CERT_H * s) / 2);
            g2.scale(s, s);
            if (templateImage != null)
                g2.drawImage(templateImage, 0, 0, CERT_W, CERT_H, null);
            else
                drawFallbackBackground(g2);
            drawTextOverlay(g2);
            return Printable.PAGE_EXISTS;
        }, pf);

        if (job.printDialog())
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Print error: " + ex.getMessage());
            }
    }

    // ── helpers ───────────────────────────────────────────────────
    private static void drawCentred(Graphics2D g, String text, int cx, int y) {
        g.drawString(text, cx - g.getFontMetrics().stringWidth(text) / 2, y);
    }

    private static String titleFor(String t) {
        if ("Winner".equalsIgnoreCase(t))    return "CERTIFICATE OF EXCELLENCE";
        if ("Runner-up".equalsIgnoreCase(t)) return "CERTIFICATE OF ACHIEVEMENT";
        return "CERTIFICATE OF PARTICIPATION";
    }

    private static String bodyFor(String t) {
        if ("Winner".equalsIgnoreCase(t))    return "has successfully WON the event";
        if ("Runner-up".equalsIgnoreCase(t)) return "has achieved Runner-up position in the event";
        return "has successfully participated in the event";
    }

    private static String nvl(String s, String fb) {
        return (s == null || s.isEmpty()) ? fb : s;
    }

    private static String safeName(String s) {
        return (s == null ? "student" : s).replaceAll("[^a-zA-Z0-9]", "_");
    }

    // ── button helpers ────────────────────────────────────────────
    private static JButton buildBtn(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(primary ? BLUE_MID : new Color(235, 238, 245));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                if (!primary) {
                    g2.setColor(new Color(180, 200, 220));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 10, 10);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setForeground(primary ? WHITE : BLUE_DARK);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(160, 36));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}

package thread;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/**
 * CertificateViewer — renders a styled certificate and allows
 * PDF-print or PNG download.
 *
 * Usage:
 *   CertificateViewer.show(parentFrame, certData);
 *
 * certData fields: studentName, eventName, certType, issueDate, deptName
 */
public class CertificateViewer extends JDialog {

    // ── certificate data ──────────────────────────────────────────
    public static class CertData {
        public int    certId;
        public String studentName;
        public String eventName;
        public String certType;      // Participant | Winner | Runner-up
        public String issueDate;
        public String deptName;
    }

    // ── colours matching the dashboard palette ────────────────────
    private static final Color BG_PAGE     = new Color(8, 8, 18);
    private static final Color ACCENT      = new Color(130, 60, 255);
    private static final Color ACCENT_L    = new Color(180,120,255);
    private static final Color GOLD        = new Color(255,200, 60);
    private static final Color TEXT_PRI    = new Color(240,235,255);
    private static final Color TEXT_MUT    = new Color(160,150,200);
    private static final Color CARD_BG     = new Color(22, 12, 50);
    private static final Color CARD_BORDER = new Color(130,60,255,160);

    // ── cert canvas size (A4 landscape proportions) ───────────────
    private static final int CERT_W = 900;
    private static final int CERT_H = 636;

    private final CertData data;
    private CertificatePanel certPanel;

    // ─────────────────────────────────────────────────────────────
    //  Static factory
    // ─────────────────────────────────────────────────────────────
    public static void show(Frame owner, CertData data) {
        CertificateViewer dlg = new CertificateViewer(owner, data);
        dlg.setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────
    public CertificateViewer(Frame owner, CertData data) {
        super(owner, "Certificate — " + data.certType, true);
        this.data = data;

        setSize(980, 780);
        setLocationRelativeTo(owner);
        setResizable(false);
        setUndecorated(false);

        JPanel root = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new GradientPaint(0,0,BG_PAGE,getWidth(),getHeight(),new Color(18,8,40)));
                g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
        root.setOpaque(true);

        // ── top title bar ─────────────────────────────────────────
        JPanel topBar = new JPanel(null);
        topBar.setOpaque(false);
        topBar.setPreferredSize(new Dimension(980, 56));

        JLabel title = makeLabel("🎓  Certificate of " + data.certType,
            new Font("Georgia", Font.BOLD|Font.ITALIC, 18), TEXT_PRI);
        title.setBounds(24, 14, 600, 28); topBar.add(title);

        JButton closeBtn = buildSmallBtn("✕ Close");
        closeBtn.setBounds(880, 12, 80, 32);
        closeBtn.addActionListener(e -> dispose());
        topBar.add(closeBtn);

        // ── certificate canvas ────────────────────────────────────
        certPanel = new CertificatePanel(data);
        certPanel.setPreferredSize(new Dimension(CERT_W, CERT_H));

        JPanel canvasHolder = new JPanel(new GridBagLayout());
        canvasHolder.setOpaque(false);
        canvasHolder.setBorder(new EmptyBorder(10, 30, 10, 30));
        canvasHolder.add(certPanel);

        // ── bottom action bar ─────────────────────────────────────
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 14));
        bottomBar.setOpaque(false);

        JButton dlPng = buildActionBtn("⬇  Download PNG", true);
        JButton dlPrint = buildActionBtn("🖨  Print / Save PDF", false);

        dlPng.addActionListener(e -> downloadPNG());
        dlPrint.addActionListener(e -> printCertificate());

        bottomBar.add(dlPng);
        bottomBar.add(dlPrint);

        root.add(topBar,     BorderLayout.NORTH);
        root.add(canvasHolder, BorderLayout.CENTER);
        root.add(bottomBar,  BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ─────────────────────────────────────────────────────────────
    //  Export: PNG
    // ─────────────────────────────────────────────────────────────
    private void downloadPNG() {
        JFileChooser fc = new JFileChooser();
        String safeName = data.studentName.replaceAll("[^a-zA-Z0-9]","_");
        fc.setSelectedFile(new File("Certificate_" + safeName + ".png"));
        fc.setDialogTitle("Save Certificate as PNG");
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png"))
            file = new File(file.getAbsolutePath() + ".png");

        try {
            BufferedImage img = renderToImage(2); // 2× for retina quality
            ImageIO.write(img, "PNG", file);
            JOptionPane.showMessageDialog(this,
                "✅  Certificate saved:\n" + file.getAbsolutePath(),
                "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Error saving: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Export: Print (also works as "Save as PDF" via OS dialog)
    // ─────────────────────────────────────────────────────────────
    private void printCertificate() {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pf = job.defaultPage();
        Paper paper = pf.getPaper();

        // A4 landscape in points (1 pt = 1/72 inch)
        double w = 842, h = 595;
        paper.setSize(w, h);
        paper.setImageableArea(18, 18, w-36, h-36);
        pf.setOrientation(PageFormat.LANDSCAPE);
        pf.setPaper(paper);

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) graphics;
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            double scaleX = pageFormat.getImageableWidth()  / CERT_W;
            double scaleY = pageFormat.getImageableHeight() / CERT_H;
            double scale  = Math.min(scaleX, scaleY);
            g2.scale(scale, scale);
            certPanel.paint(g2);
            return Printable.PAGE_EXISTS;
        }, pf);

        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Print error: " + ex.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Render certificate panel to a BufferedImage
    // ─────────────────────────────────────────────────────────────
    private BufferedImage renderToImage(int scale) {
        BufferedImage img = new BufferedImage(
            CERT_W * scale, CERT_H * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.scale(scale, scale);
        certPanel.paint(g2);
        g2.dispose();
        return img;
    }

    // ─────────────────────────────────────────────────────────────
    //  The certificate canvas itself
    // ─────────────────────────────────────────────────────────────
    static class CertificatePanel extends JPanel {

        private final CertData d;

        CertificatePanel(CertData d) {
            this.d = d;
            setOpaque(false);
            setPreferredSize(new Dimension(CERT_W, CERT_H));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int W = CERT_W, H = CERT_H;

            // ── 1. Background ──────────────────────────────────────
            g.setPaint(new GradientPaint(0,0,new Color(12,6,30), W,H,new Color(22,8,48)));
            g.fillRect(0,0,W,H);

            // subtle radial glow
            g.setPaint(new RadialGradientPaint(W/2f, H*0.35f, W*0.55f,
                new float[]{0f,1f},
                new Color[]{new Color(100,40,200,55), new Color(0,0,0,0)}));
            g.fillRect(0,0,W,H);

            // ── 2. Outer border frame ──────────────────────────────
            int bm = 18; // border margin
            // outer gold double-line
            drawBorderFrame(g, bm, W, H);

            // ── 3. Corner ornaments ────────────────────────────────
            drawCornerOrnaments(g, bm+8, W, H);

            // ── 4. Top watermark / seal ────────────────────────────
            drawSeal(g, W/2, 80, 56);

            // ── 5. Institution header ──────────────────────────────
            drawCentred(g, "CAMPUS EVENT SYSTEM",
                new Font("Georgia", Font.BOLD, 13),
                new Color(180,140,255), W/2, 148);

            // ── 6. Main title ──────────────────────────────────────
            String titleText = getTitleText();
            drawCentred(g, titleText,
                new Font("Georgia", Font.BOLD|Font.ITALIC, 38),
                GOLD, W/2, 210);

            // gold underline under title
            int tlW = 420;
            g.setPaint(new LinearGradientPaint(W/2f-tlW/2f,0,W/2f+tlW/2f,0,
                new float[]{0f,0.5f,1f},
                new Color[]{new Color(255,200,60,0),GOLD,new Color(255,200,60,0)}));
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(W/2-tlW/2, 220, W/2+tlW/2, 220);

            // ── 7. "This is to certify that" ──────────────────────
            drawCentred(g, "This is to certify that",
                new Font("Georgia", Font.ITALIC, 15),
                new Color(160,150,200), W/2, 254);

            // ── 8. Student name ────────────────────────────────────
            drawCentred(g, d.studentName,
                new Font("Georgia", Font.BOLD, 32),
                TEXT_PRI, W/2, 300);

            // name underline
            FontMetrics nmFm = g.getFontMetrics(new Font("Georgia", Font.BOLD, 32));
            int nmW = nmFm.stringWidth(d.studentName) + 60;
            g.setPaint(new LinearGradientPaint(W/2f-nmW/2f,0,W/2f+nmW/2f,0,
                new float[]{0f,0.5f,1f},
                new Color[]{new Color(130,60,255,0), ACCENT, new Color(130,60,255,0)}));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(W/2-nmW/2, 310, W/2+nmW/2, 310);

            // ── 9. Body text ───────────────────────────────────────
            String bodyLine = getBodyLine();
            drawCentred(g, bodyLine,
                new Font("Georgia", Font.PLAIN, 14),
                new Color(200,190,230), W/2, 340);

            // event name (highlighted)
            drawCentred(g, "\"" + d.eventName + "\"",
                new Font("Georgia", Font.BOLD|Font.ITALIC, 17),
                ACCENT_L, W/2, 368);

            // ── 10. Department ─────────────────────────────────────
            if (d.deptName != null && !d.deptName.isEmpty()) {
                drawCentred(g, "Department of " + d.deptName,
                    new Font("SansSerif", Font.PLAIN, 12),
                    new Color(140,130,180), W/2, 395);
            }

            // ── 11. Divider ────────────────────────────────────────
            g.setPaint(new LinearGradientPaint(W/2f-200,0,W/2f+200,0,
                new float[]{0f,0.5f,1f},
                new Color[]{new Color(130,60,255,0), new Color(130,60,255,100), new Color(130,60,255,0)}));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(W/2-200, 412, W/2+200, 412);

            // ── 12. Certificate ID + date ──────────────────────────
            drawCentred(g, "Certificate No: CES-" + String.format("%04d", d.certId)
                + "       Date of Issue: " + d.issueDate,
                new Font("SansSerif", Font.PLAIN, 11),
                new Color(120,110,160), W/2, 430);

            // ── 13. Signature zone ─────────────────────────────────
            int sigY = 490;
            // Left sig
            drawSignatureLine(g, W/4, sigY, "Organiser");
            // Right sig
            drawSignatureLine(g, 3*W/4, sigY, "Principal");

            // ── 14. Winner ribbon (if not Participant) ─────────────
            if (!"Participant".equalsIgnoreCase(d.certType)) {
                drawRibbon(g, W, d.certType);
            }

            // ── 15. Bottom tagline ─────────────────────────────────
            drawCentred(g, "Campus Event System  ·  Excellence in Participation",
                new Font("SansSerif", Font.ITALIC, 10),
                new Color(80,70,110), W/2, H-28);

            g.dispose();
        }

        // ── helpers ───────────────────────────────────────────────

        private String getTitleText() {
            return switch (d.certType) {
                case "Winner"    -> "Certificate of Excellence";
                case "Runner-up" -> "Certificate of Achievement";
                default          -> "Certificate of Participation";
            };
        }

        private String getBodyLine() {
            return switch (d.certType) {
                case "Winner"    -> "has successfully WON the event";
                case "Runner-up" -> "has achieved Runner-up position in the event";
                default          -> "has successfully participated in the event";
            };
        }

        private void drawCentred(Graphics2D g, String text, Font font, Color color, int cx, int y) {
            g.setFont(font);
            g.setColor(color);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, cx - fm.stringWidth(text)/2, y);
        }

        private void drawBorderFrame(Graphics2D g, int m, int W, int H) {
            // outer gold frame
            g.setColor(new Color(200,160,40,180));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(m, m, W-2*m, H-2*m);

            // inner purple frame
            g.setColor(new Color(130,60,255,120));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(m+6, m+6, W-2*(m+6), H-2*(m+6));

            // thin gold inner-inner
            g.setColor(new Color(200,160,40,60));
            g.setStroke(new BasicStroke(0.7f));
            g.drawRect(m+10, m+10, W-2*(m+10), H-2*(m+10));
        }

        private void drawCornerOrnaments(Graphics2D g, int m, int W, int H) {
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.5f));
            int s = 22; // ornament size
            int[][] corners = {{m,m},{W-m-s,m},{m,H-m-s},{W-m-s,H-m-s}};
            for (int[] c : corners) {
                int x=c[0], y=c[1];
                // L-bracket ornament
                g.drawLine(x,y, x+s,y);
                g.drawLine(x,y, x,y+s);
                // small diamond at corner
                int[] dx={x+4,x+8,x+4,x},dy={y,y+4,y+8,y+4};
                g.fillPolygon(dx,dy,4);
            }
        }

        private void drawSeal(Graphics2D g, int cx, int cy, int r) {
            // outer glow
            g.setPaint(new RadialGradientPaint(cx,cy,r,
                new float[]{0f,0.6f,1f},
                new Color[]{new Color(130,60,255,100),new Color(130,60,255,40),new Color(0,0,0,0)}));
            g.fillOval(cx-r,cy-r,r*2,r*2);

            // circle border
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx-r+4,cy-r+4,(r-4)*2,(r-4)*2);
            g.setColor(new Color(130,60,255,200));
            g.setStroke(new BasicStroke(1f));
            g.drawOval(cx-r+9,cy-r+9,(r-9)*2,(r-9)*2);

            // star inside
            drawStar(g, cx, cy, r-14, 6, GOLD);

            // "CES" text
            g.setFont(new Font("Georgia",Font.BOLD,11));
            g.setColor(GOLD);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("CES", cx - fm.stringWidth("CES")/2, cy + fm.getAscent()/2 - 2);
        }

        private void drawStar(Graphics2D g, int cx, int cy, int r, int points, Color c) {
            int inner = r/2;
            int[] xp = new int[points*2], yp = new int[points*2];
            for (int i=0;i<points*2;i++) {
                double angle = Math.PI/points * i - Math.PI/2;
                int rad = (i%2==0) ? r : inner;
                xp[i] = cx + (int)(rad * Math.cos(angle));
                yp[i] = cy + (int)(rad * Math.sin(angle));
            }
            g.setColor(c);
            g.fillPolygon(xp,yp,points*2);
        }

        private void drawSignatureLine(Graphics2D g, int cx, int y, String role) {
            g.setColor(new Color(130,60,255,120));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(cx-60, y, cx+60, y);

            g.setFont(new Font("SansSerif",Font.PLAIN,11));
            g.setColor(new Color(140,130,180));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(role, cx - fm.stringWidth(role)/2, y+16);
        }

        private void drawRibbon(Graphics2D g, int W, String type) {
            // top-right corner ribbon
            Color c1 = "Winner".equals(type) ? new Color(220,170,20) : new Color(150,150,160);
            Color c2 = "Winner".equals(type) ? new Color(255,210,60)  : new Color(190,190,200);

            int rx = W - 30, ry = 30, size = 90;
            int[] xp = {rx-size, rx, rx};
            int[] yp = {ry,      ry, ry+size};
            g.setPaint(new GradientPaint(rx-size,ry, c1, rx,ry+size, c2));
            g.fillPolygon(xp,yp,3);

            // text on ribbon
            g.setColor(new Color(20,10,40));
            g.setFont(new Font("Georgia",Font.BOLD,10));
            Graphics2D gr = (Graphics2D) g.create();
            gr.translate(rx-18, ry+18);
            gr.rotate(Math.PI/4);
            gr.drawString(type.toUpperCase(), -gr.getFontMetrics().stringWidth(type.toUpperCase())/2, 0);
            gr.dispose();
        }
    }

    // ── small UI helpers ──────────────────────────────────────────
    private static JButton buildSmallBtn(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(40,20,80));
        btn.setForeground(new Color(200,190,230));
        btn.setFont(new Font("SansSerif",Font.PLAIN,12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(130,60,255,80),1));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JButton buildActionBtn(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (primary) {
                    g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),getWidth(),getHeight(),new Color(90,20,200)));
                } else {
                    g2.setColor(new Color(255,255,255,18));
                }
                g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                if (!primary) {
                    g2.setColor(new Color(130,60,255,80));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia",Font.BOLD,13));
        btn.setPreferredSize(new Dimension(230,44));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static JLabel makeLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font); l.setForeground(color); l.setOpaque(false);
        return l;
    }
}

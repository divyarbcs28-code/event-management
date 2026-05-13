package thread;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.text.SimpleDateFormat;

public class WelcomePage extends JFrame {

    // ── Oracle DB ──────────────────────────────────────────────────
    private static final String DB_URL  = "jdbc:oracle:thin:@localhost:1521:orcl";
    private static final String DB_USER = "C##miniproject";
    private static final String DB_PASS = "mini";

    // ── Brand palette ──────────────────────────────────────────────
    private static final Color BG_TOP       = new Color(8,   8,  18);
    private static final Color BG_BOT       = new Color(18,  8,  40);
    private static final Color ACCENT       = new Color(130, 60, 255);
    private static final Color ACCENT_LIGHT = new Color(180,120, 255);
    private static final Color GOLD         = new Color(255, 200,  60);
    private static final Color TEXT_PRIMARY = new Color(240, 235, 255);
    private static final Color TEXT_MUTED   = new Color(160, 150, 200);
    private static final Color CARD_BG      = new Color(255, 255, 255, 12);
    private static final Color CARD_BORDER  = new Color(130,  60, 255, 80);
    private static final Color ERROR_COL    = new Color(255,  80,  80);
    private static final Color SUCCESS_COL  = new Color( 80, 220, 120);
    private static final Color ADMIN_ACCENT = new Color(255, 160,  40);

    // ── Slide ──────────────────────────────────────────────────────
    private int     currentPage = 0;
    private int     targetPage  = 0;
    private float   slideOffset = 0f;
    private int     slideDir    = 1;
    private Timer   slideTimer;
    private boolean sliding     = false;

    // ── Fade ───────────────────────────────────────────────────────
    private float fadeAlpha = 0f;
    private Timer fadeTimer;

    // ── Panels ─────────────────────────────────────────────────────
    private JPanel welcomePanel, loginPanel, signupPanel, adminLoginPanel;
    private JPanel rootLayer;
    private int    pw, ph;

    // ── Dynamic sign-up sub-fields panel ──────────────────────────
    private JPanel  extraFieldsPanel;
    private int     extraFieldsY = 0;
    private JPanel  signupCard;
    private int     signupCardBaseH = 0;

    public WelcomePage() {
        setTitle("Campus Event Management System");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        pw = scr.width;
        ph = scr.height;

        rootLayer = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int W = getWidth(), H = getHeight();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
                g2.setPaint(new GradientPaint(0, 0, BG_TOP, W, H, BG_BOT));
                g2.fillRect(0, 0, W, H);
                g2.setPaint(new RadialGradientPaint(W / 2f, H * 0.3f, W * 0.55f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(100, 40, 220, 50), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, W, H);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }
        };
        rootLayer.setOpaque(true);
        setContentPane(rootLayer);

        welcomePanel    = buildWelcomePage(scr);
        loginPanel      = buildLoginPage(scr);
        signupPanel     = buildSignupPage(scr);
        adminLoginPanel = buildAdminLoginPage(scr);

        welcomePanel  .setOpaque(false);
        loginPanel    .setOpaque(false);
        signupPanel   .setOpaque(false);
        adminLoginPanel.setOpaque(false);

        welcomePanel  .setBounds(0,  0, pw, ph);
        loginPanel    .setBounds(pw, 0, pw, ph);
        signupPanel   .setBounds(pw, 0, pw, ph);
        adminLoginPanel.setBounds(pw, 0, pw, ph);

        rootLayer.add(welcomePanel);
        rootLayer.add(loginPanel);
        rootLayer.add(signupPanel);

        fadeTimer = new Timer(20, e -> {
            fadeAlpha = Math.min(1f, fadeAlpha + 0.04f);
            rootLayer.repaint();
            if (fadeAlpha >= 1f) ((Timer) e.getSource()).stop();
        });
        fadeTimer.start();

        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════
    private void navigateTo(int page) {
        if (sliding || page == currentPage) return;
        targetPage  = page;
        slideDir    = (page > currentPage) ? 1 : -1;
        slideOffset = 0f;
        sliding     = true;

        JPanel from = getPanel(currentPage);
        JPanel to   = getPanel(targetPage);
        to.setBounds(slideDir * pw, 0, pw, ph);

        slideTimer = new Timer(12, e -> {
            slideOffset = Math.min(1f, slideOffset + 0.06f);
            float ease  = easeInOut(slideOffset);
            from.setBounds((int)(-slideDir * pw * ease), 0, pw, ph);
            to  .setBounds((int)( slideDir * pw * (1f - ease)), 0, pw, ph);
            rootLayer.repaint();
            if (slideOffset >= 1f) {
                ((Timer) e.getSource()).stop();
                sliding     = false;
                currentPage = targetPage;
                from.setBounds(slideDir * pw, 0, pw, ph);
            }
        });
        slideTimer.start();
    }

    private JPanel getPanel(int page) {
        if (page == 1) return loginPanel;
        if (page == 2) return signupPanel;
        if (page == 3) return adminLoginPanel;
        return welcomePanel;
    }

    private float easeInOut(float t) {
        return t < 0.5f ? 2*t*t : (float)(-1 + (4 - 2*t) * t);
    }

    // ══════════════════════════════════════════════════════════════
    //  WELCOME PAGE
    // ══════════════════════════════════════════════════════════════
    private JPanel buildWelcomePage(Dimension scr) {
        int W = scr.width, H = scr.height, cx = W / 2;
        JPanel p = new JPanel(null);
        p.setOpaque(false);

        JButton loginBtn  = buildButton("Login",   false);
        JButton signupBtn = buildButton("Sign Up", true);
        loginBtn .setBounds(W - 240, 22, 105, 40);
        signupBtn.setBounds(W - 125, 22, 110, 40);
        loginBtn .addActionListener(e -> navigateTo(1));
        signupBtn.addActionListener(e -> navigateTo(2));
        p.add(loginBtn);
        p.add(signupBtn);

        int ls = Math.min(120, W / 12);
        JLabel logo = new JLabel(buildLogoIcon(ls));
        logo.setBounds(cx - ls/2, (int)(H*0.08), ls, ls);
        p.add(logo);

        JLabel college = makeLabel("MEPCO SCHLENK ENGINEERING COLLEGE",
            new Font("Georgia", Font.BOLD, clamp(18, 28, W/55)),
            new Color(220, 200, 255));
        college.setHorizontalAlignment(SwingConstants.CENTER);
        college.setBounds(40, (int)(H*0.08) + ls + 14, W-80, 40);
        p.add(college);

        int sepY = (int)(H*0.08) + ls + 60;
        JSeparator sep = buildSeparator();
        sep.setBounds(cx - W/4, sepY, W/2, 2);
        p.add(sep);

        JLabel headline = makeLabel("Campus Event Management",
            new Font("Georgia", Font.BOLD | Font.ITALIC, clamp(26, 48, W/32)),
            TEXT_PRIMARY);
        headline.setHorizontalAlignment(SwingConstants.CENTER);
        headline.setBounds(40, sepY + 16, W-80, 66);
        p.add(headline);

        JLabel tagline = makeLabel("\u201cWhere ideas meet action and memories are made\u201d",
            new Font("Georgia", Font.ITALIC, clamp(13, 18, W/72)), TEXT_MUTED);
        tagline.setHorizontalAlignment(SwingConstants.CENTER);
        tagline.setBounds(80, sepY + 78, W-160, 30);
        p.add(tagline);

        int cardY = sepY + 125, gap = 26, cnt = 3;
        int cw = Math.min(270, (W - gap*(cnt+1)) / cnt), ch = 128;
        int tw = cw*cnt + gap*(cnt-1), sx = cx - tw/2;
        String[][] cards = {
            {"\uD83D\uDCC5","Manage Events",    "Create, schedule and\ntrack campus events"},
            {"\uD83D\uDC65","Team Coordination","Assign roles, tasks\nand responsibilities"},
            {"\uD83D\uDCC8","Live Analytics",   "Real-time insights\nand participation data"},
        };
        for (int i = 0; i < 3; i++) {
            JPanel card = buildFeatureCard(cards[i][0], cards[i][1], cards[i][2], cw, ch);
            card.setBounds(sx + i*(cw+gap), cardY, cw, ch);
            p.add(card);
        }

        JLabel footer = makeLabel(
            "\u00a9 2025 Mepco Schlenk Engineering College  \u00b7  All Rights Reserved",
            new Font("SansSerif", Font.PLAIN, 11), new Color(100, 90, 140));
        footer.setHorizontalAlignment(SwingConstants.CENTER);
        footer.setBounds(0, H-34, W, 22);
        p.add(footer);

        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN PAGE  — username + password + DOB
    // ══════════════════════════════════════════════════════════════
    private JPanel buildLoginPage(Dimension scr) {
        int W = scr.width, H = scr.height, cx = W/2;
        JPanel p = new JPanel(null);
        p.setOpaque(false);

        JLabel college = makeLabel("MEPCO SCHLENK ENGINEERING COLLEGE",
            new Font("Georgia", Font.BOLD, clamp(14, 20, W/70)),
            new Color(180, 160, 220));
        college.setHorizontalAlignment(SwingConstants.CENTER);
        college.setBounds(0, 26, W, 28);
        p.add(college);

        // ── Card ── (taller to fit password field)
        int cw = 460, ch = 490, cardX = cx - cw/2, cardY = H/2 - ch/2;
        JPanel card = buildFormCard(true);
        card.setBounds(cardX, cardY, cw, ch);

        int ls = 48;
        card.add(centeredIcon(buildLogoIcon(ls), cw/2 - ls/2, 18, ls, ls));
        card.add(centered(makeLabel("Welcome Back",
            new Font("Georgia", Font.BOLD, 24), TEXT_PRIMARY), 0, 76, cw, 32));
        card.add(centered(makeLabel("Login to your account",
            new Font("Georgia", Font.ITALIC, 12), TEXT_MUTED), 0, 108, cw, 22));

        int fx = 40, fw = cw - 80;

        // Username
        card.add(fieldLabel("Username", fx, 140, fw));
        JTextField userField = styledField();
        userField.setBounds(fx, 156, fw, 38);
        card.add(userField);

        // Password
        card.add(fieldLabel("Password", fx, 204, fw));
        JPasswordField passField = styledPassword();
        passField.setBounds(fx, 220, fw, 38);
        card.add(passField);

        // DOB
        //card.add(fieldLabel("Date of Birth  (DD/MM/YYYY)", fx, 268, fw));
        JTextField dobField = styledField();
        dobField.setBounds(fx, 284, fw, 38);
        //card.add(dobField);

        // Status label
        JLabel statusLbl = makeLabel("", new Font("SansSerif", Font.BOLD, 12), ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0, 334, cw, 22);
        card.add(statusLbl);

        // Login button
        JButton loginBtn = buildButton("Login", true);
        loginBtn.setBounds(fx, 360, fw, 44);
        loginBtn.setFont(new Font("Georgia", Font.BOLD, 15));
        card.add(loginBtn);

        JLabel back = clickLabel("\u2190  Back to Home", ACCENT_LIGHT, () -> navigateTo(0));
        back.setBounds(fx, 418, 180, 22);
        card.add(back);

        JLabel toSignup = clickLabel("Don't have an account?  Sign Up", TEXT_MUTED, () -> navigateTo(2));
        toSignup.setHorizontalAlignment(SwingConstants.RIGHT);
        toSignup.setBounds(fx + 180, 418, fw - 180, 22);
        card.add(toSignup);

        p.add(card);

        loginBtn.addActionListener(e -> {
            String typedName = userField.getText().trim();
            String password  = new String(passField.getPassword()).trim();
            String dob       = dobField.getText().trim();

            if (typedName.isEmpty()) {
                statusLbl.setForeground(ERROR_COL);
                statusLbl.setText("Please enter your username.");
                return;
            }
            if (password.isEmpty()) {
                statusLbl.setForeground(ERROR_COL);
                statusLbl.setText("Please enter your password.");
                return;
            }
            doLogin(typedName, password, statusLbl);
        });

        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  VALIDATION HELPERS
    // ══════════════════════════════════════════════════════════════
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$";
        return email.matches(emailRegex) && email.length() <= 50;
    }

    private boolean isValidPhone(String phone) {
        String cleaned = phone.replaceAll("[\\s-]", "");
        if (cleaned.matches("\\d{10}")) return true;
        if (cleaned.matches("\\+91\\d{10}")) return true;
        return false;
    }

    private boolean isValidDOB(String dobStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            java.util.Date parsedDate = sdf.parse(dobStr);
            java.util.Date today = new java.util.Date();
            if (parsedDate.after(today)) return false;
            java.util.Calendar dob = java.util.Calendar.getInstance();
            dob.setTime(parsedDate);
            java.util.Calendar now = java.util.Calendar.getInstance();
            int age = now.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR);
            if (now.get(java.util.Calendar.MONTH) < dob.get(java.util.Calendar.MONTH) ||
                (now.get(java.util.Calendar.MONTH) == dob.get(java.util.Calendar.MONTH) &&
                 now.get(java.util.Calendar.DAY_OF_MONTH) < dob.get(java.util.Calendar.DAY_OF_MONTH))) {
                age--;
            }
            return age >= 16;
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    private String getDOBErrorMessage(String dobStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
            sdf.setLenient(false);
            java.util.Date parsedDate = sdf.parse(dobStr);
            java.util.Date today = new java.util.Date();
            if (parsedDate.after(today)) return "Date of birth cannot be in the future.";
            java.util.Calendar dob = java.util.Calendar.getInstance();
            dob.setTime(parsedDate);
            java.util.Calendar now = java.util.Calendar.getInstance();
            int age = now.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR);
            if (now.get(java.util.Calendar.MONTH) < dob.get(java.util.Calendar.MONTH) ||
                (now.get(java.util.Calendar.MONTH) == dob.get(java.util.Calendar.MONTH) &&
                 now.get(java.util.Calendar.DAY_OF_MONTH) < dob.get(java.util.Calendar.DAY_OF_MONTH))) {
                age--;
            }
            if (age < 16) return "You must be at least 16 years old to register.";
            return "Invalid date of birth.";
        } catch (java.text.ParseException e) {
            return "Invalid date format. Use DD/MM/YYYY (e.g., 25/03/2005).";
        }
    }

    private boolean isValidPassword(String password) {
        // At least 6 chars, contains letter and digit
        if (password.length() < 6) return false;
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit  = password.matches(".*[0-9].*");
        return hasLetter && hasDigit;
    }

    // ══════════════════════════════════════════════════════════════
    //  ADMIN LOGIN PAGE
    // ══════════════════════════════════════════════════════════════
    private JPanel buildAdminLoginPage(Dimension scr) {
        int W = scr.width, H = scr.height, cx = W/2;
        JPanel p = new JPanel(null);
        p.setOpaque(false);

        JLabel college = makeLabel("MEPCO SCHLENK ENGINEERING COLLEGE",
            new Font("Georgia", Font.BOLD, clamp(14, 20, W/70)),
            new Color(180, 160, 220));
        college.setHorizontalAlignment(SwingConstants.CENTER);
        college.setBounds(0, 26, W, 28);
        p.add(college);

        int cw = 460, ch = 470, cardX = cx - cw/2, cardY = H/2 - ch/2;
        JPanel card = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(28, 16, 8, 220));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(new Color(200, 130, 30, 90));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
                g2.setPaint(new GradientPaint(0,0,new Color(255,160,40,220),getWidth(),0,new Color(0,0,0,0)));
                g2.fillRoundRect(0, 0, getWidth(), 3, 3, 3);
            }
        };
        card.setOpaque(false);
        card.setBounds(cardX, cardY, cw, ch);

        int ls = 52;
        card.add(centeredIcon(buildAdminIcon(ls), cw/2 - ls/2, 22, ls, ls));

        JLabel titleLbl = makeLabel("Principal / Admin Login",
            new Font("Georgia", Font.BOLD, 22), new Color(255, 200, 100));
        titleLbl.setHorizontalAlignment(SwingConstants.CENTER);
        titleLbl.setBounds(0, 84, cw, 34);
        card.add(titleLbl);

        JLabel subtitle = makeLabel("Manage departments, clubs & incharges",
            new Font("Georgia", Font.ITALIC, 13), new Color(200, 170, 100));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        subtitle.setBounds(0, 118, cw, 22);
        card.add(subtitle);

        int fx = 40, fw = cw - 80;

        card.add(fieldLabelColored("Admin ID", fx, 156, fw, new Color(210, 170, 90)));
        JTextField adminIdField = styledFieldAmber();
        adminIdField.setBounds(fx, 174, fw, 40);
        card.add(adminIdField);

        card.add(fieldLabelColored("Password", fx, 224, fw, new Color(210, 170, 90)));
        JPasswordField passField = styledPasswordAmber();
        passField.setBounds(fx, 242, fw, 40);
        card.add(passField);

        card.add(fieldLabelColored("Admin Role", fx, 292, fw, new Color(210, 170, 90)));
        JComboBox<String> roleCombo = styledComboAmber(
            new String[]{"-- Select Role --", "Principal", "Department Admin"});
        roleCombo.setBounds(fx, 310, fw, 40);
        card.add(roleCombo);

        JLabel statusLbl = makeLabel("", new Font("SansSerif", Font.BOLD, 13), ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0, 360, cw, 22);
        card.add(statusLbl);

        JButton loginBtn = buildButtonAmber("Admin Login");
        loginBtn.setBounds(fx, 390, fw, 48);
        card.add(loginBtn);

        JLabel back = clickLabel("\u2190  Back to Home", new Color(180, 140, 60), () -> navigateTo(0));
        back.setBounds(fx, 452, fw, 24);
        card.add(back);

        p.add(card);
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  SIGN UP PAGE — compact layout with password field
    //  All font/field sizes reduced so "Back to Home" is visible
    // ══════════════════════════════════════════════════════════════
    private JPanel buildSignupPage(Dimension scr) {
        int W = scr.width, H = scr.height, cx = W/2;
        JPanel page = new JPanel(null);
        page.setOpaque(false);

        JLabel college = makeLabel("MEPCO SCHLENK ENGINEERING COLLEGE",
            new Font("Georgia", Font.BOLD, clamp(12, 17, W/70)),
            new Color(180, 160, 220));
        college.setHorizontalAlignment(SwingConstants.CENTER);
        college.setBounds(0, 10, W, 22);
        page.add(college);

        JPanel content = new JPanel(null);
        content.setOpaque(false);

        int cw = 500;
        int cardX = cx - cw/2;

        JScrollPane scroll = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBackground(new Color(30, 16, 60));
        scroll.setBounds(0, 38, W, H - 38);
        scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = new Color(130, 60, 255, 120);
                this.trackColor = new Color(20, 10, 45);
            }
        });
        page.add(scroll);

        // Compact field sizes
        final int fx = 36, fw = cw - 72;
        final int FH = 34;   // field height
        final int LH = 16;   // label height
        final int GAP = 8;   // gap between field bottom and next label

        JTextField   nameField    = styledFieldSm();
        JTextField   emailField   = styledFieldSm();
        JPasswordField passField  = styledPasswordSm();
        JPasswordField confirmPass = styledPasswordSm();
        JComboBox<String> typeCombo = styledComboSm(
            new String[]{"-- Select User Type --","Student","Staff","External_Guest"});
        JComboBox<String> genderCombo = styledComboSm(
            new String[]{"-- Select Gender --","Male","Female","Other"});
        JTextField   addrField   = styledFieldSm();
        JTextField   dobField    = styledFieldSm();

        extraFieldsPanel = new JPanel(null);
        extraFieldsPanel.setOpaque(false);

        JLabel statusLbl = makeLabel("", new Font("SansSerif", Font.BOLD, 11), ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);

        JButton regBtn = buildButton("Register", true);
        regBtn.setFont(new Font("Georgia", Font.BOLD, 14));

        JLabel back    = clickLabel("\u2190  Back to Home", ACCENT_LIGHT, () -> navigateTo(0));
        back.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JLabel toLogin = clickLabel("Already have an account?  Login", TEXT_MUTED, () -> navigateTo(1));
        toLogin.setFont(new Font("SansSerif", Font.PLAIN, 12));
        toLogin.setHorizontalAlignment(SwingConstants.RIGHT);

        int cardTop = 8;
        int ls = 40;

        signupCard = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 10, 45, 215));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
                g2.setPaint(new GradientPaint(0,0,new Color(255,200,60,200),getWidth(),0,new Color(0,0,0,0)));
                g2.fillRoundRect(0, 0, getWidth(), 3, 3, 3);
            }
        };
        signupCard.setOpaque(false);

        int cy = 14;

        JLabel logoLbl = new JLabel(buildLogoIcon(ls));
        logoLbl.setBounds(cw/2 - ls/2, cy, ls, ls);
        signupCard.add(logoLbl);
        cy += ls + 6;

        JLabel title = makeLabel("Create Account",
            new Font("Georgia", Font.BOLD, 20), TEXT_PRIMARY);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBounds(0, cy, cw, 28);
        signupCard.add(title);
        cy += 28 + 8;

        // ── Full Name ──
        signupCard.add(fieldLabelSmAt("Full Name", fx, cy, fw)); cy += LH;
        nameField.setBounds(fx, cy, fw, FH); signupCard.add(nameField); cy += FH + GAP;

        // ── Email ──
        signupCard.add(fieldLabelSmAt("Email Address", fx, cy, fw)); cy += LH;
        emailField.setBounds(fx, cy, fw, FH); signupCard.add(emailField); cy += FH + GAP;

        // ── Password ──
        signupCard.add(fieldLabelSmAt("Password  (min 6 chars, letter + digit)", fx, cy, fw)); cy += LH;
        passField.setBounds(fx, cy, fw, FH); signupCard.add(passField); cy += FH + GAP;

        // ── Confirm Password ──
        signupCard.add(fieldLabelSmAt("Confirm Password", fx, cy, fw)); cy += LH;
        confirmPass.setBounds(fx, cy, fw, FH); signupCard.add(confirmPass); cy += FH + GAP;

        // ── User Type ──
        signupCard.add(fieldLabelSmAt("User Type", fx, cy, fw)); cy += LH;
        typeCombo.setBounds(fx, cy, fw, FH); signupCard.add(typeCombo); cy += FH + GAP;

        // ── Gender ──
        signupCard.add(fieldLabelSmAt("Gender", fx, cy, fw)); cy += LH;
        genderCombo.setBounds(fx, cy, fw, FH); signupCard.add(genderCombo); cy += FH + GAP;

        // ── Address ──
        signupCard.add(fieldLabelSmAt("Address", fx, cy, fw)); cy += LH;
        addrField.setBounds(fx, cy, fw, FH); signupCard.add(addrField); cy += FH + GAP;

        // ── DOB ──
        signupCard.add(fieldLabelSmAt("Date of Birth  (DD/MM/YYYY)", fx, cy, fw)); cy += LH;
        dobField.setBounds(fx, cy, fw, FH); signupCard.add(dobField); cy += FH + GAP;

        extraFieldsY = cy;
        extraFieldsPanel.setBounds(0, cy, cw, 0);
        signupCard.add(extraFieldsPanel);

        signupCardBaseH = cy;

        statusLbl.setBounds(0, cy, cw, 20);
        signupCard.add(statusLbl);

        regBtn.setBounds(fx, cy + 24, fw, 40);
        signupCard.add(regBtn);

        back.setBounds(fx, cy + 74, 170, 20);
        signupCard.add(back);

        toLogin.setBounds(fx + 170, cy + 74, fw - 170, 20);
        signupCard.add(toLogin);

        int initialCardH = cy + 106;
        signupCardBaseH  = cy;

        signupCard.setBounds(cardX, cardTop, cw, initialCardH);
        content.add(signupCard);
        // Add generous bottom padding so "Back to Home" is always scrollable into view
        content.setPreferredSize(new Dimension(W, cardTop + initialCardH + 80));

        typeCombo.addActionListener(e -> {
            String sel = (String) typeCombo.getSelectedItem();
            rebuildExtraFields(sel, cw, fw, fx,
                statusLbl, regBtn, back, toLogin,
                content, cardX, cardTop, W);
        });

        regBtn.addActionListener(e -> {
            String name      = nameField.getText().trim();
            String email     = emailField.getText().trim();
            String password  = new String(passField.getPassword()).trim();
            String confirmPw = new String(confirmPass.getPassword()).trim();
            String userType  = ((String) typeCombo.getSelectedItem());
            String gender    = ((String) genderCombo.getSelectedItem());
            String address   = addrField.getText().trim();
            String dob       = dobField.getText().trim();

            // ── VALIDATION ────────────────────────────────────────
            if (name.isEmpty()) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please enter your full name."); return;
            }
            if (name.length() < 3) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Full name must be at least 3 characters."); return;
            }
            if (email.isEmpty()) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please enter your email address."); return;
            }
            if (!isValidEmail(email)) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Invalid email format (e.g., user@example.com)."); return;
            }
            if (password.isEmpty()) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please enter a password."); return;
            }
            if (!isValidPassword(password)) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Password: min 6 chars, must include letter & digit."); return;
            }
            if (!password.equals(confirmPw)) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Passwords do not match."); return;
            }
            if (userType.startsWith("--")) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please select a user type."); return;
            }
            if (gender.startsWith("--")) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please select your gender."); return;
            }
            if (address.isEmpty()) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please enter your address."); return;
            }
            if (address.length() < 5) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Address must be at least 5 characters."); return;
            }
            if (dob.isEmpty()) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText("Please enter your date of birth."); return;
            }
            if (!isValidDOB(dob)) {
                statusLbl.setForeground(ERROR_COL); statusLbl.setText(getDOBErrorMessage(dob)); return;
            }

            Component[] extras = extraFieldsPanel.getComponents();
            String extra1 = "", extra2 = "", extra3 = "";
            int tfCount = 0;
            JComboBox<?> extraCombo = null;
            for (Component cx2 : extras) {
                if (cx2 instanceof JTextField && ((JTextField)cx2).isEditable()) {
                    if (tfCount == 0) extra1 = ((JTextField)cx2).getText().trim();
                    else if (tfCount == 1) extra2 = ((JTextField)cx2).getText().trim();
                    else if (tfCount == 2) extra3 = ((JTextField)cx2).getText().trim();
                    tfCount++;
                } else if (cx2 instanceof JComboBox) {
                    extraCombo = (JComboBox<?>) cx2;
                }
            }

            doRegister(name, email, password,
                       userType.toLowerCase().replace(" ", "_"),
                       gender, address, dob,
                       extra1, extra2, extra3,
                       extraCombo != null ? (String) extraCombo.getSelectedItem() : "",
                       statusLbl);
        });

        return page;
    }

    // ── Rebuild dynamic extra fields (compact sizes) ──────────────
    private void rebuildExtraFields(String type,
            int cw, int fw, int fx,
            JLabel statusLbl, JButton regBtn,
            JLabel back, JLabel toLogin,
            JPanel content, int cardX, int cardTop, int W) {

        extraFieldsPanel.removeAll();
        int eh = 0;
        final int FH = 34, LH = 16, GAP = 8;

        if ("Student".equals(type)) {
            JLabel l1 = fieldLabelSmAt("Registration Number", fx, 0, fw);
            JTextField f1 = styledFieldSm(); f1.setBounds(fx, LH, fw, FH);
            JLabel l2 = fieldLabelSmAt("Year", fx, LH+FH+GAP, fw);
            JTextField f2 = styledFieldSm(); f2.setBounds(fx, LH*2+FH+GAP, fw, FH);
            JLabel l3 = fieldLabelSmAt("Section", fx, LH*2+FH*2+GAP*2, fw);
            JTextField f3 = styledFieldSm(); f3.setBounds(fx, LH*3+FH*2+GAP*2, fw, FH);
            extraFieldsPanel.add(l1); extraFieldsPanel.add(f1);
            extraFieldsPanel.add(l2); extraFieldsPanel.add(f2);
            extraFieldsPanel.add(l3); extraFieldsPanel.add(f3);
            eh = LH*3 + FH*3 + GAP*2 + GAP;

        } else if ("Staff".equals(type)) {
            JLabel l1 = fieldLabelSmAt("Department ID", fx, 0, fw);
            JTextField f1 = styledFieldSm(); f1.setBounds(fx, LH, fw, FH);
            JLabel l2 = fieldLabelSmAt("Designation", fx, LH+FH+GAP, fw);
            JTextField f2 = styledFieldSm(); f2.setBounds(fx, LH*2+FH+GAP, fw, FH);
            extraFieldsPanel.add(l1); extraFieldsPanel.add(f1);
            extraFieldsPanel.add(l2); extraFieldsPanel.add(f2);
            eh = LH*2 + FH*2 + GAP + GAP;

        } else if ("External_Guest".equals(type)) {
            JLabel l1 = fieldLabelSmAt("Organisation Name", fx, 0, fw);
            JTextField f1 = styledFieldSm(); f1.setBounds(fx, LH, fw, FH);
            JLabel l2 = fieldLabelSmAt("Role Type", fx, LH+FH+GAP, fw);
            JTextField f2 = styledFieldSm(); f2.setBounds(fx, LH*2+FH+GAP, fw, FH);
            extraFieldsPanel.add(l1); extraFieldsPanel.add(f1);
            extraFieldsPanel.add(l2); extraFieldsPanel.add(f2);
            eh = LH*2 + FH*2 + GAP + GAP;
        }

        int newY = extraFieldsY + eh + (eh > 0 ? 6 : 0);
        extraFieldsPanel.setBounds(0, extraFieldsY, cw, eh);

        statusLbl.setBounds(0,      newY,      cw,       20);
        regBtn   .setBounds(fx,     newY + 24, fw,       40);
        back     .setBounds(fx,     newY + 74, 170,      20);
        toLogin  .setBounds(fx+170, newY + 74, fw - 170, 20);

        int newCardH = newY + 106;
        signupCard.setBounds(cardX, cardTop, cw, newCardH);
        content.setPreferredSize(new Dimension(W, cardTop + newCardH + 80));

        extraFieldsPanel.revalidate(); extraFieldsPanel.repaint();
        signupCard.revalidate();       signupCard.repaint();
        content.revalidate();          content.repaint();
    }

    // ══════════════════════════════════════════════════════════════
    //  ORACLE — login (username + password + DOB)
    //  SQL needed:  ALTER TABLE users ADD (password VARCHAR2(60));
    // ══════════════════════════════════════════════════════════════
    private void doLogin(String name, String password, JLabel statusLbl) {

        new Thread(() -> {

            try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

                String sql = "SELECT user_id, user_type FROM users " +
                             "WHERE LOWER(name)=LOWER(?) AND password=?";

                PreparedStatement ps = con.prepareStatement(sql);

                ps.setString(1, name.trim());
                ps.setString(2, password.trim());

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {

                    int uid = rs.getInt("user_id");

                    String detectedType =
                            rs.getString("user_type").toLowerCase().trim();

                    SwingUtilities.invokeLater(() -> {

                        statusLbl.setForeground(SUCCESS_COL);

                        statusLbl.setText(
                                "Login successful! Welcome, " + name + "."
                        );

                        if ("student".equals(detectedType)) {

                            new StudentDashboard(
                                    uid, name, DB_URL, DB_USER, DB_PASS
                            );

                            dispose();

                        } else if ("staff".equals(detectedType)) {

                            new StaffDashboard(
                                    uid, name, DB_URL, DB_USER, DB_PASS
                            );

                            dispose();

                        } else if ("external_guest".equals(detectedType)) {

                            new ExternalGuestDashboard(
                                    uid, name, DB_URL, DB_USER, DB_PASS
                            );

                            dispose();

                        } else if ("admin".equals(detectedType)) {

                            new AdminDashboard();

                            dispose();

                        } else {

                            statusLbl.setForeground(ERROR_COL);

                            statusLbl.setText(
                                    "Unknown user type: " + detectedType
                            );
                        }
                    });

                } else {

                    SwingUtilities.invokeLater(() -> {

                        statusLbl.setForeground(ERROR_COL);

                        statusLbl.setText(
                                "Invalid username or password."
                        );
                    });
                }

            } catch (SQLException ex) {

                SwingUtilities.invokeLater(() -> {

                    statusLbl.setForeground(ERROR_COL);

                    statusLbl.setText(
                            "DB Error: " +
                            ex.getMessage().substring(
                                    0,
                                    Math.min(50, ex.getMessage().length())
                            )
                    );
                });
            }

        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  ORACLE — register (insert into users + sub-table)
    //  Requires:  ALTER TABLE users ADD (password VARCHAR2(60));
    // ══════════════════════════════════════════════════════════════
    private void doRegister(String name, String email, String password,
                            String userType, String gender, String address, String dobStr,
                            String extra1, String extra2, String extra3, String extraCombo,
                            JLabel statusLbl) {
        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                sdf.setLenient(false);
                java.util.Date parsedDate = sdf.parse(dobStr);
                java.sql.Date sqlDate = new java.sql.Date(parsedDate.getTime());

                try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    con.setAutoCommit(false);

                    ResultSet seqRs = con.createStatement()
                        .executeQuery("SELECT NVL(MAX(user_id),0)+1 FROM users");
                    int userId = seqRs.next() ? seqRs.getInt(1) : 1;

                    // NOTE: Run this DDL once in SQL*Plus before using:
                    //   ALTER TABLE users ADD (password VARCHAR2(60));
                    String sql1 = "INSERT INTO users (user_id, name, email, password, user_type, gender, address, dob) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    PreparedStatement ps1 = con.prepareStatement(sql1);
                    ps1.setInt   (1, userId);
                    ps1.setString(2, name.length()    > 10 ? name.substring(0,10)    : name);
                    ps1.setString(3, email.length()   > 15 ? email.substring(0,15)   : email);
                    ps1.setString(4, password.length()> 60 ? password.substring(0,60): password);
                    ps1.setString(5, userType.length()> 15 ? userType.substring(0,15): userType);
                    ps1.setString(6, gender.length()  > 6  ? gender.substring(0,6)   : gender);
                    ps1.setString(7, address.length() > 50 ? address.substring(0,50) : address);
                    ps1.setDate  (8, sqlDate);
                    ps1.executeUpdate();

                    if ("student".equals(userType)) {
                        if (extra1.isEmpty() || extra2.isEmpty() || extra3.isEmpty()) {
                            con.rollback();
                            SwingUtilities.invokeLater(() -> {
                                statusLbl.setForeground(ERROR_COL);
                                statusLbl.setText("Fill all Student fields.");
                            });
                            return;
                        }
                        PreparedStatement ps2 = con.prepareStatement(
                            "INSERT INTO student (user_id, reg_num, year, section) VALUES (?,?,?,?)");
                        ps2.setInt   (1, userId);
                        ps2.setString(2, extra1);
                        ps2.setInt   (3, Integer.parseInt(extra2));
                        ps2.setString(4, extra3);
                        ps2.executeUpdate();

                    } else if ("staff".equals(userType)) {
                        if (extra1.isEmpty() || extra2.isEmpty()) {
                            con.rollback();
                            SwingUtilities.invokeLater(() -> {
                                statusLbl.setForeground(ERROR_COL);
                                statusLbl.setText("Fill all Staff fields.");
                            });
                            return;
                        }
                        PreparedStatement ps2 = con.prepareStatement(
                            "INSERT INTO staff (user_id, dept_id, designation) VALUES (?,?,?)");
                        ps2.setInt   (1, userId);
                        ps2.setInt   (2, Integer.parseInt(extra1));
                        ps2.setString(3, extra2.length() > 10 ? extra2.substring(0,10) : extra2);
                        ps2.executeUpdate();

                    } else if ("external_guest".equals(userType)) {
                        if (extra1.isEmpty() || extra2.isEmpty()) {
                            con.rollback();
                            SwingUtilities.invokeLater(() -> {
                                statusLbl.setForeground(ERROR_COL);
                                statusLbl.setText("Fill all Guest fields.");
                            });
                            return;
                        }
                        PreparedStatement ps2 = con.prepareStatement(
                            "INSERT INTO external_guest (user_id, organisation_name, role_type) VALUES (?,?,?)");
                        ps2.setInt   (1, userId);
                        ps2.setString(2, extra1.length() > 30 ? extra1.substring(0,30) : extra1);
                        ps2.setString(3, extra2.length() > 10 ? extra2.substring(0,10) : extra2);
                        ps2.executeUpdate();
                    }

                    con.commit();
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setForeground(SUCCESS_COL);
                        statusLbl.setText("Registered successfully! Please login.");
                    });
                }
            } catch (java.text.ParseException pe) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Invalid date. Use DD/MM/YYYY.");
                });
            } catch (NumberFormatException ne) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Year / Dept ID must be numbers.");
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    String msg = ex.getMessage();
                    if (msg.contains("ORA-00001")) statusLbl.setText("User ID or Email already exists.");
                    else statusLbl.setText("DB Error: " + msg.substring(0, Math.min(60, msg.length())));
                });
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  REUSABLE COMPONENTS
    // ══════════════════════════════════════════════════════════════
    private JPanel buildFormCard(boolean violetTop) {
        JPanel card = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 10, 45, 215));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 24, 24);
                Color lc = violetTop ? ACCENT : new Color(255, 200, 60, 200);
                g2.setPaint(new GradientPaint(0, 0, lc, getWidth(), 0, new Color(0,0,0,0)));
                g2.fillRoundRect(0, 0, getWidth(), 3, 3, 3);
            }
        };
        card.setOpaque(false);
        return card;
    }

    private JPanel buildFeatureCard(String emoji, String title, String body, int w, int h) {
        JPanel card = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
                g2.setPaint(new GradientPaint(0,0,new Color(150,80,255,80),getWidth(),0,new Color(0,0,0,0)));
                g2.fillRoundRect(0, 0, getWidth(), 4, 4, 4);
            }
        };
        card.setOpaque(false);
        JLabel iconL = new JLabel(emoji);
        iconL.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        iconL.setBounds(14, 12, 36, 32);
        card.add(iconL);
        JLabel tl = makeLabel(title, new Font("Georgia", Font.BOLD, 14), TEXT_PRIMARY);
        tl.setBounds(14, 50, w-28, 22);
        card.add(tl);
        String[] lines = body.split("\n");
        for (int i = 0; i < lines.length; i++) {
            JLabel bl = makeLabel(lines[i], new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
            bl.setBounds(14, 74 + i*16, w-28, 18);
            card.add(bl);
        }
        return card;
    }

    private JSeparator buildSeparator() {
        return new JSeparator() {
            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth(); if (w <= 0) return;
                Graphics2D g2 = (Graphics2D) g;
                g2.setPaint(new LinearGradientPaint(0, 0, w, 0,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0), ACCENT, new Color(0,0,0,0)}));
                g2.fillRect(0, 0, w, getHeight());
            }
        };
    }

    private JButton buildButton(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (primary) {
                    g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),getWidth(),getHeight(),new Color(90,20,200)));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                } else {
                    g2.setColor(new Color(255,255,255,18));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                    g2.setColor(CARD_BORDER);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false);      btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia", Font.BOLD, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(GOLD);        btn.repaint(); }
            public void mouseExited (MouseEvent e) { btn.setForeground(Color.WHITE); btn.repaint(); }
        });
        return btn;
    }

    private JButton buildButtonAmber(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(200,120,20),getWidth(),getHeight(),new Color(140,70,10)));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false);      btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia", Font.BOLD, 15));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(new Color(255,240,180)); btn.repaint(); }
            public void mouseExited (MouseEvent e) { btn.setForeground(Color.WHITE);            btn.repaint(); }
        });
        return btn;
    }

    // ── Standard-size styled components ───────────────────────────
    @SuppressWarnings("unchecked")
    private JComboBox<String> styledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(30, 16, 60));
        cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 14));
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                lbl.setBackground(isSelected ? new Color(80,40,160) : new Color(25,12,50));
                lbl.setForeground(TEXT_PRIMARY);
                lbl.setBorder(new EmptyBorder(4,10,4,10));
                lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
                return lbl;
            }
        });
        return cb;
    }

    // ── Small-size styled components (for compact signup) ─────────
    @SuppressWarnings("unchecked")
    private JComboBox<String> styledComboSm(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(30, 16, 60));
        cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                lbl.setBackground(isSelected ? new Color(80,40,160) : new Color(25,12,50));
                lbl.setForeground(TEXT_PRIMARY);
                lbl.setBorder(new EmptyBorder(3,8,3,8));
                lbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
                return lbl;
            }
        });
        return cb;
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> styledComboAmber(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(40, 22, 8));
        cb.setForeground(new Color(255, 210, 120));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 14));
        cb.setBorder(BorderFactory.createLineBorder(new Color(180, 120, 30, 100), 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus);
                lbl.setBackground(isSelected ? new Color(100,60,10) : new Color(35,18,5));
                lbl.setForeground(new Color(255,210,120));
                lbl.setBorder(new EmptyBorder(4,10,4,10));
                lbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
                return lbl;
            }
        });
        return cb;
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1), new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return f;
    }

    private JTextField styledFieldSm() {
        JTextField f = new JTextField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1), new EmptyBorder(3,10,3,10)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return f;
    }

    private JTextField styledFieldAmber() {
        JTextField f = new JTextField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180,120,30,100), 1), new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(40,22,8)); f.setForeground(new Color(255,220,140));
        f.setCaretColor(new Color(255,180,60)); f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return f;
    }

    private JPasswordField styledPassword() {
        JPasswordField f = new JPasswordField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1), new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return f;
    }

    private JPasswordField styledPasswordSm() {
        JPasswordField f = new JPasswordField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1), new EmptyBorder(3,10,3,10)));
        f.setBackground(new Color(30,16,60)); f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_LIGHT); f.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return f;
    }

    private JPasswordField styledPasswordAmber() {
        JPasswordField f = new JPasswordField();
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180,120,30,100), 1), new EmptyBorder(4,12,4,12)));
        f.setBackground(new Color(40,22,8)); f.setForeground(new Color(255,220,140));
        f.setCaretColor(new Color(255,180,60)); f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        return f;
    }

    private JLabel makeLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font); l.setForeground(color); l.setOpaque(false);
        return l;
    }

    private JLabel centeredIcon(ImageIcon icon, int x, int y, int w, int h) {
        JLabel l = new JLabel(icon); l.setBounds(x, y, w, h); return l;
    }

    private JLabel centered(JLabel l, int x, int y, int w, int h) {
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBounds(x, y, w, h); return l;
    }

    private JLabel fieldLabel(String text, int x, int y, int w) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 12), TEXT_MUTED);
        l.setBounds(x, y, w, 18); return l;
    }

    private JLabel fieldLabelColored(String text, int x, int y, int w, Color color) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 12), color);
        l.setBounds(x, y, w, 18); return l;
    }

    /** Compact field label for signup page */
    private JLabel fieldLabelSmAt(String text, int x, int y, int w) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 11), TEXT_MUTED);
        l.setBounds(x, y, w, 16); return l;
    }

    private JLabel fieldLabelAt(String text, int x, int y, int w) {
        return fieldLabel(text, x, y, w);
    }

    private JLabel clickLabel(String text, Color base, Runnable action) {
        JLabel l = makeLabel(text, new Font("SansSerif", Font.PLAIN, 13), base);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e)  { action.run(); }
            public void mouseEntered(MouseEvent e)  { l.setForeground(GOLD); }
            public void mouseExited(MouseEvent e)   { l.setForeground(base); }
        });
        return l;
    }

    private int clamp(int min, int max, int val) {
        return Math.max(min, Math.min(max, val));
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGO & ADMIN ICON
    // ══════════════════════════════════════════════════════════════
    private ImageIcon buildLogoIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(130, 60, 255, 55));
        g.fillOval(0, 0, size, size);
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(size * 0.04f));
        g.drawOval((int)(size*0.03),(int)(size*0.03),(int)(size*0.94),(int)(size*0.94));
        g.setPaint(new RadialGradientPaint(size/2f, size/2f, size*0.42f,
            new float[]{0f,1f}, new Color[]{new Color(160,80,255,120),new Color(0,0,0,0)}));
        g.fillOval((int)(size*0.08),(int)(size*0.08),(int)(size*0.84),(int)(size*0.84));
        g.setColor(ACCENT_LIGHT);
        int s2=size/2, cap=(int)(size*0.26);
        g.fillPolygon(new int[]{s2,s2+cap,s2,s2-cap},
            new int[]{(int)(size*0.28),(int)(size*0.40),(int)(size*0.52),(int)(size*0.40)},4);
        g.setColor(new Color(220,180,255));
        int tw=(int)(size*0.36),th=(int)(size*0.10);
        g.fillRoundRect(s2-tw/2,(int)(size*0.22),tw,th,6,6);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(size*0.025f));
        int tx=s2+tw/2-(int)(size*0.04);
        g.drawLine(tx,(int)(size*0.27),tx,(int)(size*0.52));
        g.fillOval(tx-(int)(size*0.04),(int)(size*0.51),(int)(size*0.08),(int)(size*0.08));
        g.setStroke(new BasicStroke(1.5f));
        drawStar(g,(int)(size*0.18),(int)(size*0.22),(int)(size*0.06),GOLD);
        drawStar(g,(int)(size*0.80),(int)(size*0.72),(int)(size*0.05),ACCENT_LIGHT);
        g.dispose();
        return new ImageIcon(img);
    }

    private ImageIcon buildAdminIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int[] sx = {size/2,(int)(size*0.85),(int)(size*0.85),size/2,(int)(size*0.15),(int)(size*0.15)};
        int[] sy = {(int)(size*0.08),(int)(size*0.20),(int)(size*0.60),(int)(size*0.92),(int)(size*0.60),(int)(size*0.20)};
        g.setPaint(new GradientPaint(0,0,new Color(200,130,20,180),size,size,new Color(120,70,10,180)));
        g.fillPolygon(sx, sy, 6);
        g.setColor(new Color(255,190,60,200));
        g.setStroke(new BasicStroke(size*0.04f));
        g.drawPolygon(sx, sy, 6);
        int lx=size/2, ly=(int)(size*0.52), lr=(int)(size*0.14);
        g.setColor(new Color(255,240,180));
        g.fillRoundRect(lx-lr, ly-(int)(size*0.06), lr*2, (int)(size*0.22), 6, 6);
        g.setColor(new Color(200,130,20));
        g.setStroke(new BasicStroke(size*0.05f));
        g.drawArc(lx-lr+(int)(size*0.04),(int)(size*0.30),lr*2-(int)(size*0.08),(int)(size*0.20),0,180);
        g.dispose();
        return new ImageIcon(img);
    }

    private void drawStar(Graphics2D g, int cx, int cy, int r, Color c) {
        g.setColor(c);
        for (int i=0;i<4;i++) {
            double a=Math.toRadians(i*45);
            g.drawLine((int)(cx+Math.cos(a)*r),(int)(cy+Math.sin(a)*r),
                       (int)(cx-Math.cos(a)*r),(int)(cy-Math.sin(a)*r));
        }
        g.fillOval(cx-2,cy-2,4,4);
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl","true");
        SwingUtilities.invokeLater(WelcomePage::new);
    }
}

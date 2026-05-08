package thread;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.sql.*;
import java.time.LocalDate;
import java.time.Period;
import java.util.Date;

public class StudentDashboard extends JFrame {

    private final String dbUrl, dbUser, dbPass;
    private final int    userId;
    private final String userName;
    private DefaultTableModel myClubsTableModel;

    private static final Color BG_TOP        = new Color(8,   8,  18);
    private static final Color BG_BOT        = new Color(18,  8,  40);
    private static final Color ACCENT        = new Color(130, 60, 255);
    private static final Color ACCENT_LIGHT  = new Color(180,120, 255);
    private static final Color GOLD          = new Color(255, 200,  60);
    private static final Color TEXT_PRIMARY  = new Color(240, 235, 255);
    private static final Color TEXT_MUTED    = new Color(160, 150, 200);
    private static final Color CARD_BG       = new Color(255, 255, 255, 14);
    private static final Color CARD_BORDER   = new Color(130,  60, 255, 80);
    private static final Color SUCCESS_COL   = new Color( 80, 220, 120);
    private static final Color ERROR_COL     = new Color(255,  80,  80);
    private static final Color WARN_COL      = new Color(255, 180,  60);
    private static final Color SIDEBAR_BG    = new Color(10,   5,  28, 240);
    private static final Color SIDEBAR_ACT   = new Color(130,  60, 255, 160);

    private static final int SIDEBAR_W_PX = 220;
    private static final int TOP_H        = 56;

    // pages: 0=Overview 1=MyClubs 2=JoinClub 3=ClubEvents
    //        4=MyEvents 5=Feedback 6=MyAttendance 7=Certificates
    private int     curPage  = 0;
    private int     tgtPage  = 0;
    private float   slideOff = 0f;
    private int     slideDir = 1;
    private Timer   slideTimer;
    private boolean sliding  = false;

    private JPanel rootLayer;
    private JPanel contentArea;
    private JPanel[] pages;
    private JButton[] sideItems;

    private String studentName="", studentEmail="", studentGender="", studentAddress="";
    private String studentDOB="", studentAge="0", regNum="", stuYear="", stuSection="";

    private DefaultTableModel overviewClubsRefModel;
    private DefaultTableModel joinClubModel;
    private DefaultTableModel clubEventsModel;
    private DefaultTableModel myEventsModel;
    private DefaultTableModel feedbackHistModel;
    private DefaultTableModel myAttendanceModel;
    private DefaultTableModel certificatesModel;

    private JPanel myAttTotalCard, myAttPresentCard, myAttAbsentCard;
    private JPanel certTotalCard;

    private JComboBox<String> feedbackEvCombo;
    private JPanel[] overviewStatCards;

    private javax.swing.Timer autoRefreshTimer;

    private int pw, ph;

    public StudentDashboard(int userId, String userName,
                            String dbUrl, String dbUser, String dbPass) {
        this.userId   = userId;
        this.userName = userName;
        this.dbUrl    = dbUrl;
        this.dbUser   = dbUser;
        this.dbPass   = dbPass;

        setTitle("Student Dashboard — " + userName);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        pw = scr.width; ph = scr.height;

        rootLayer = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int W = getWidth(), H = getHeight();
                g2.setPaint(new GradientPaint(0,0,BG_TOP,W,H,BG_BOT));
                g2.fillRect(0,0,W,H);
                g2.setPaint(new RadialGradientPaint(W/2f, H*0.3f, W*0.5f,
                    new float[]{0f,1f},
                    new Color[]{new Color(100,40,220,40), new Color(0,0,0,0)}));
                g2.fillRect(0,0,W,H);
            }
        };
        rootLayer.setOpaque(true);
        setContentPane(rootLayer);

        buildTopBar(scr);
        buildSidebar(scr);

        int cW = pw - SIDEBAR_W_PX, cH = ph - TOP_H;
        contentArea = new JPanel(null);
        contentArea.setOpaque(false);
        contentArea.setBounds(SIDEBAR_W_PX, TOP_H, cW, cH);
        rootLayer.add(contentArea);

        pages = new JPanel[8];
        for (int i = 0; i < 8; i++) {
            pages[i] = new JPanel(null);
            pages[i].setOpaque(false);
            pages[i].setBounds(i == 0 ? 0 : cW, 0, cW, cH);
            contentArea.add(pages[i]);
        }

        loadStudentData(() -> {
            buildOverviewPage(scr);
            buildMyClubsPage(scr);
            buildJoinClubPage(scr);
            buildClubEventsPage(scr);
            buildMyEventsPage(scr);
            buildFeedbackPage(scr);
            buildMyAttendancePage(scr);
            buildCertificatesPage(scr);
            startAutoRefresh();
        });

        setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════
    //  AUTO-REFRESH
    // ══════════════════════════════════════════════════════════════
    private void startAutoRefresh() {
        autoRefreshTimer = new javax.swing.Timer(15000, e -> {
            if (curPage == 1) loadMyClubsData(myClubsTableModel);
            if (curPage == 6) refreshMyAttendancePage();
            if (curPage == 7) refreshCertificatesPage();
        });
        autoRefreshTimer.start();
    }

    private void refreshMyAttendancePage() {
        if (myAttendanceModel == null) return;
        myAttendanceModel.setRowCount(0);
        loadMyAttendanceData(myAttendanceModel, myAttTotalCard, myAttPresentCard, myAttAbsentCard);
    }

    private void refreshCertificatesPage() {
        if (certificatesModel == null) return;
        certificatesModel.setRowCount(0);
        loadCertificatesData(certificatesModel);
    }

    // ── TOP BAR ───────────────────────────────────────────────────
    private void buildTopBar(Dimension scr) {
        JPanel top = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(8,4,22,235));
                g2.fillRect(0,0,getWidth(),getHeight());
                g2.setPaint(new LinearGradientPaint(0,TOP_H-2,getWidth(),TOP_H-2,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(0,TOP_H-2,getWidth(),2);
            }
        };
        top.setOpaque(false); top.setBounds(0,0,pw,TOP_H);

        JLabel logo = new JLabel(buildLogoIcon(34));
        logo.setBounds(16,(TOP_H-34)/2,34,34); top.add(logo);

        JLabel title = makeLabel("Campus Event System",new Font("Georgia",Font.BOLD,16),TEXT_PRIMARY);
        title.setBounds(58,(TOP_H-20)/2,220,20); top.add(title);

        JLabel greet = makeLabel("Welcome, "+userName+" (Student)",
            new Font("Georgia",Font.ITALIC,13), TEXT_MUTED);
        greet.setBounds(pw-380,(TOP_H-18)/2,250,18); top.add(greet);

        JButton logout = buildButton("Logout", false);
        logout.setBounds(pw-126,(TOP_H-34)/2,110,34);
        logout.addActionListener(e -> {
            if (autoRefreshTimer != null) autoRefreshTimer.stop();
            dispose();
            SwingUtilities.invokeLater(WelcomePage::new);
        });
        top.add(logout);
        rootLayer.add(top);
    }

    // ── SIDEBAR ───────────────────────────────────────────────────
    private void buildSidebar(Dimension scr) {
        JPanel sidebar = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SIDEBAR_BG); g2.fillRect(0,0,getWidth(),getHeight());
                g2.setPaint(new LinearGradientPaint(SIDEBAR_W_PX-2,0,SIDEBAR_W_PX-2,getHeight(),
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(SIDEBAR_W_PX-2,0,2,getHeight());
            }
        };
        sidebar.setOpaque(false);
        sidebar.setBounds(0,TOP_H,SIDEBAR_W_PX,ph-TOP_H);

        JPanel badge = avatarPanel(userName, 64);
        badge.setBounds(SIDEBAR_W_PX/2-32,20,64,64); sidebar.add(badge);

        JLabel uName = makeLabel(userName, new Font("Georgia",Font.BOLD,14), TEXT_PRIMARY);
        uName.setHorizontalAlignment(SwingConstants.CENTER);
        uName.setBounds(0,90,SIDEBAR_W_PX,20); sidebar.add(uName);

        JLabel uRole = makeLabel("Student", new Font("SansSerif",Font.PLAIN,11), ACCENT_LIGHT);
        uRole.setHorizontalAlignment(SwingConstants.CENTER);
        uRole.setBounds(0,112,SIDEBAR_W_PX,16); sidebar.add(uRole);

        JSeparator div = buildSeparator();
        div.setBounds(20,136,SIDEBAR_W_PX-40,2); sidebar.add(div);

        String[][] menu = {
            {"\uD83C\uDFE0", "Overview"},
            {"\uD83C\uDFEB", "My Clubs"},
            {"\u2795",       "Join Club"},
            {"\uD83D\uDCC5", "Club Events"},
            {"\uD83D\uDCCB", "My Events"},
            {"\uD83D\uDCAC", "Feedback"},
            {"\uD83D\uDCC8", "My Attendance"},
            {"\uD83C\uDFC5", "Certificates"},
        };
        sideItems = new JButton[menu.length];
        int my = 148;
        for (int i = 0; i < menu.length; i++) {
            final int idx = i;
            sideItems[i] = sidebarButton(menu[i][0]+" "+menu[i][1], i == 0);
            sideItems[i].setBounds(12, my, SIDEBAR_W_PX-24, 38);
            sideItems[i].addActionListener(e -> navigateTo(idx));
            sidebar.add(sideItems[i]);
            my += 43;
        }
        rootLayer.add(sidebar);
    }

    private void navigateTo(int page) {
        if (sliding || page == curPage) return;
        tgtPage = page; slideDir = (page > curPage) ? 1 : -1; slideOff = 0f; sliding = true;
        int cW = pw - SIDEBAR_W_PX;
        JPanel from = pages[curPage], to = pages[tgtPage];
        to.setBounds(slideDir * cW, 0, cW, contentArea.getHeight());
        for (int i = 0; i < sideItems.length; i++) setSideActive(sideItems[i], i == page);
        slideTimer = new Timer(12, e -> {
            slideOff = Math.min(1f, slideOff + 0.065f);
            float ease = easeInOut(slideOff);
            from.setBounds((int)(-slideDir*cW*ease), 0, cW, contentArea.getHeight());
            to.setBounds((int)(slideDir*cW*(1f-ease)), 0, cW, contentArea.getHeight());
            rootLayer.repaint();
            if (slideOff >= 1f) {
                ((Timer)e.getSource()).stop();
                sliding = false; curPage = tgtPage;
                from.setBounds(slideDir*cW, 0, cW, contentArea.getHeight());
            }
        });
        slideTimer.start();
    }

    private float easeInOut(float t) {
        return t < 0.5f ? 2*t*t : (float)(-1+(4-2*t)*t);
    }

    // ── LOAD STUDENT PROFILE DATA ─────────────────────────────────
    private void loadStudentData(Runnable onDone) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT u.name,u.email,u.gender,u.address,u.dob," +
                    "s.reg_num,s.year,s.section " +
                    "FROM users u JOIN student s ON u.user_id=s.user_id " +
                    "WHERE u.user_id=?");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    studentName    = rs.getString("name");
                    studentEmail   = rs.getString("email");
                    studentGender  = rs.getString("gender");
                    studentAddress = rs.getString("address");
                    regNum         = rs.getString("reg_num");
                    stuYear        = rs.getString("year");
                    stuSection     = rs.getString("section");
                    java.sql.Date dob = rs.getDate("dob");
                    if (dob != null) {
                        studentDOB = dob.toString();
                        studentAge = String.valueOf(
                            Period.between(dob.toLocalDate(), LocalDate.now()).getYears());
                    }
                }
            } catch (SQLException ex) { studentName = userName; }
            SwingUtilities.invokeLater(onDone);
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 0 — OVERVIEW
    // ══════════════════════════════════════════════════════════════
    private void buildOverviewPage(Dimension scr) {
        JPanel pg = pages[0];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H, cx = W/2;

        JPanel content = new JPanel(null);
        content.setOpaque(false);
        content.setPreferredSize(new Dimension(W, 820));
        JScrollPane sc = styledScroll(content);
        sc.setBounds(0,0,W,H); pg.add(sc);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> refreshOverviewStats());
        content.add(refreshBtn);

        JLabel ttl = makeLabel("Student Overview",
            new Font("Georgia",Font.BOLD|Font.ITALIC,28), TEXT_PRIMARY);
        ttl.setHorizontalAlignment(SwingConstants.CENTER);
        ttl.setBounds(0,24,W-200,38); content.add(ttl);

        JSeparator sep = buildSeparator();
        sep.setBounds(cx-W/5,68,W/5*2,2); content.add(sep);

        // profile card
        int pcW=340, pcH=380, pcX=36, pcY=88;
        JPanel pc = glassCard(); pc.setBounds(pcX,pcY,pcW,pcH); content.add(pc);
        JPanel av = avatarPanel(studentName, 72);
        av.setBounds(pcW/2-36,20,72,72); pc.add(av);
        JLabel nm = makeLabel(studentName, new Font("Georgia",Font.BOLD,17), TEXT_PRIMARY);
        nm.setHorizontalAlignment(SwingConstants.CENTER);
        nm.setBounds(0,100,pcW,24); pc.add(nm);
        JLabel rb = roleBadge("Student  |  "+regNum);
        rb.setBounds(pcW/2-80,128,160,22); pc.add(rb);
        String[][] rows = {
            {"\uD83D\uDCE7", studentEmail},
            {"\u2640\u2642", studentGender},
            {"\uD83D\uDCC5", "DOB: "+studentDOB+"  (Age "+studentAge+")"},
            {"\uD83D\uDCDD", "Year "+stuYear+"  |  Section "+stuSection},
            {"\uD83D\uDCCD", studentAddress.isEmpty() ? "—" : studentAddress},
        };
        int ry = 162;
        for (String[] r : rows) {
            JLabel ic = new JLabel(r[0]);
            ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,13));
            ic.setBounds(18,ry,26,20); pc.add(ic);
            JLabel vl = makeLabel(r[1], new Font("SansSerif",Font.PLAIN,13), TEXT_MUTED);
            vl.setBounds(48,ry,pcW-60,20); pc.add(vl);
            ry += 30;
        }

        // stat cards
        int scX=pcX+pcW+28, scY=pcY;
        int scW=(W-scX-32)/3, scH=120, scGap=14;
        String[][] stats = {
            {"\uD83C\uDFEB","My Clubs",
             "SELECT COUNT(DISTINCT club_id) FROM members_in WHERE user_id=?"},
            {"\uD83D\uDCCB","Events Registered",
             "SELECT COUNT(DISTINCT event_id) FROM registers WHERE user_id=?"},
            {"\uD83C\uDFC5","Certificates",
             "SELECT COUNT(*) FROM certificate WHERE user_id=?"},
        };
        overviewStatCards = new JPanel[stats.length];
        for (int i = 0; i < stats.length; i++) {
            JPanel st = statCard(stats[i][0], stats[i][1], "0");
            st.setBounds(scX+i*(scW+scGap), scY, scW, scH);
            content.add(st);
            overviewStatCards[i] = st;
            loadStatCount(stats[i][2], st);
        }

        // rules card
        int rulesY = scY+scH+18;
        JPanel rules = glassCard();
        rules.setBounds(scX,rulesY,W-scX-32,160); content.add(rules);
        JLabel rT = makeLabel("Club & Event Rules",
            new Font("Georgia",Font.BOLD,15), ACCENT_LIGHT);
        rT.setBounds(20,14,400,22); rules.add(rT);
        String rulesHtml =
            "<html><body style='color:rgb(160,150,200);font-family:SansSerif;font-size:12px;line-height:1.7'>" +
            "<b style='color:rgb(240,235,255)'>Club Membership:</b> " +
            "You can be a member of a minimum of 1 and a maximum of 4 clubs.<br>" +
            "<b style='color:rgb(240,235,255)'>Event Registration:</b> " +
            "You can only register for events that belong to clubs you are a member of. " +
            "Use <i>Club Events</i> to browse and register.<br>" +
            "<b style='color:rgb(240,235,255)'>Feedback:</b> " +
            "You can submit feedback once per registered event.<br>" +
            "<b style='color:rgb(240,235,255)'>Leave Club:</b> " +
            "Use <i>My Clubs</i> to request to leave a club. The staff incharge must approve." +
            "</body></html>";
        JLabel rL = new JLabel(rulesHtml);
        rL.setBounds(20,42,W-scX-72,120); rules.add(rL);

        content.revalidate(); content.repaint();
    }

    private void refreshOverviewStats() {
        if (overviewStatCards == null) return;
        String[] sqls = {
            "SELECT COUNT(DISTINCT club_id) FROM members_in WHERE user_id=?",
            "SELECT COUNT(DISTINCT event_id) FROM registers WHERE user_id=?",
            "SELECT COUNT(*) FROM certificate WHERE user_id=?"
        };
        for (int i = 0; i < sqls.length; i++) loadStatCount(sqls[i], overviewStatCards[i]);
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 1 — MY CLUBS  (with leave-request button)
    // ══════════════════════════════════════════════════════════════
    private void buildMyClubsPage(Dimension scr) {
        JPanel pg = pages[1];
        pg.removeAll();
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pageTitle(pg, "My Clubs", W);

        // ── info banner ───────────────────────────────────────────
        JPanel banner = glassCard();
        banner.setBounds(24, 66, W-48, 44); pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "\u2139  To leave a club, click \"Request Leave\". " +
            "The staff incharge must approve — you must always remain in at least 1 club.",
            new Font("SansSerif",Font.PLAIN,12), ACCENT_LIGHT);
        bannerLbl.setBounds(14, 12, W-76, 20); banner.add(bannerLbl);

        // ── Clubs table ───────────────────────────────────────────
        // Columns: Club ID | Club Name | Created Date | Total Members | My Role | Leave Status | Action
        String[] cols = {"Club ID","Club Name","Created Date","Total Members","My Role","Leave Status","Action"};
        myClubsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 6; }
        };
        JTable table = new JTable(myClubsTableModel);
        styleTable(table);
        table.setRowHeight(44);

        // ── Leave Status renderer (colour-coded) ──────────────────
        table.getColumn("Leave Status").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl,v,sel,foc,row,col);
                String s = v == null ? "—" : v.toString();
                switch (s) {
                    case "Pending"  -> { lbl.setForeground(WARN_COL);    lbl.setText("\u23F3 Pending"); }
                    case "Approved" -> { lbl.setForeground(SUCCESS_COL); lbl.setText("\u2713 Approved"); }
                    case "Rejected" -> { lbl.setForeground(ERROR_COL);   lbl.setText("\u2717 Rejected"); }
                    default         -> { lbl.setForeground(TEXT_MUTED);  lbl.setText("—"); }
                }
                lbl.setFont(new Font("SansSerif",Font.BOLD,12));
                lbl.setBackground(sel ? new Color(100,50,200,120)
                    : row%2==0 ? new Color(12,6,30) : new Color(20,10,45));
                lbl.setOpaque(true);
                lbl.setBorder(new EmptyBorder(0,12,0,12));
                return lbl;
            }
        });

        // ── Action button renderer ────────────────────────────────
        table.getColumn("Action").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object v, boolean sel, boolean foc, int row, int col) {
                String leaveStatus = (String) tbl.getValueAt(row, 5);
                if ("Pending".equals(leaveStatus)) {
                    JButton btn = buildButton("\u23F3 Pending...", false);
                    btn.setFont(new Font("Georgia",Font.BOLD,11));
                    btn.setEnabled(false);
                    return btn;
                } else if ("Approved".equals(leaveStatus)) {
                    JButton btn = buildButton("\u2713 Left", false);
                    btn.setFont(new Font("Georgia",Font.BOLD,11));
                    btn.setEnabled(false);
                    return btn;
                } else {
                    JButton btn = buildButton("\uD83D\uDEAA Request Leave", true);
                    btn.setFont(new Font("Georgia",Font.BOLD,11));
                    return btn;
                }
            }
        });

        // ── Action button editor ──────────────────────────────────
        table.getColumn("Action").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            private JButton currentBtn;

            @Override public Component getTableCellEditorComponent(
                    JTable tbl, Object v, boolean sel, int row, int col) {
                String leaveStatus = (String) tbl.getValueAt(row, 5);

                if ("Pending".equals(leaveStatus)) {
                    currentBtn = buildButton("\u23F3 Pending...", false);
                    currentBtn.setFont(new Font("Georgia",Font.BOLD,11));
                    currentBtn.setEnabled(false);
                } else if ("Approved".equals(leaveStatus)) {
                    currentBtn = buildButton("\u2713 Left", false);
                    currentBtn.setFont(new Font("Georgia",Font.BOLD,11));
                    currentBtn.setEnabled(false);
                } else {
                    currentBtn = buildButton("\uD83D\uDEAA Request Leave", true);
                    currentBtn.setFont(new Font("Georgia",Font.BOLD,11));
                    currentBtn.addActionListener(e -> {
                        fireEditingStopped();
                        int clubId   = (Integer) tbl.getValueAt(row, 0);
                        String cName = (String)  tbl.getValueAt(row, 1);
                        doRequestLeave(clubId, cName, myClubsTableModel);
                    });
                }
                return currentBtn;
            }

            @Override public Object getCellEditorValue() { return ""; }
        });

        // column widths
        table.getColumn("Club ID")      .setPreferredWidth(70);
        table.getColumn("Club Name")    .setPreferredWidth(220);
        table.getColumn("Created Date") .setPreferredWidth(110);
        table.getColumn("Total Members").setPreferredWidth(120);
        table.getColumn("My Role")      .setPreferredWidth(120);
        table.getColumn("Leave Status") .setPreferredWidth(130);
        table.getColumn("Action")       .setPreferredWidth(150);

        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24, 118, W-48, H-148); pg.add(sp);

        // ── Status label (shows feedback after an action) ─────────
        JLabel statusLbl = makeLabel("", new Font("SansSerif",Font.BOLD,12), SUCCESS_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(24, H-26, W-48, 20); pg.add(statusLbl);

        // ── Refresh button ────────────────────────────────────────
        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180, 20, 160, 36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> {
            statusLbl.setText("");
            loadMyClubsData(myClubsTableModel);
        });
        pg.add(refreshBtn);

        loadMyClubsData(myClubsTableModel);
        pg.revalidate(); pg.repaint();
    }

    // ── DB loader for My Clubs (includes leave request status) ────
    private void loadMyClubsData(DefaultTableModel model) {
        model.setRowCount(0);
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                // Join members_in with club, and LEFT JOIN club_leave_request for status
                PreparedStatement ps = con.prepareStatement(
                    "SELECT c.club_id, c.club_name, c.created_date, " +
                    "       (SELECT COUNT(*) FROM members_in m2 WHERE m2.club_id=c.club_id) AS total_members, " +
                    "       mi.role_type, " +
                    "       NVL((SELECT clr.status FROM club_leave_request clr " +
                    "            WHERE clr.user_id=mi.user_id AND clr.club_id=c.club_id " +
                    "            AND clr.status='Pending' AND ROWNUM=1), " +
                    "           (SELECT clr2.status FROM club_leave_request clr2 " +
                    "            WHERE clr2.user_id=mi.user_id AND clr2.club_id=c.club_id " +
                    "            AND clr2.status='Approved' AND ROWNUM=1)) AS leave_status " +
                    "FROM   members_in mi " +
                    "JOIN   club c ON mi.club_id=c.club_id " +
                    "WHERE  mi.user_id=? " +
                    "ORDER  BY c.club_name");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row = {
                        rs.getInt(1),       // Club ID
                        rs.getString(2),    // Club Name
                        rs.getDate(3),      // Created Date
                        rs.getInt(4),       // Total Members
                        rs.getString(5),    // My Role
                        rs.getString(6),    // Leave Status (may be null)
                        ""                  // Action placeholder
                    };
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() ->
                    model.addRow(new Object[]{"—","Error: "+ex.getMessage(),"—","—","—","—","—"}));
            }
        }).start();
    }

    // ── Submit a leave request via Oracle stored procedure ────────
    private void doRequestLeave(int clubId, String clubName, DefaultTableModel model) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "<html>Request to leave <b>" + clubName + "</b>?<br><br>" +
            "The staff incharge must approve before you are removed.<br>" +
            "You must always remain in at least 1 club.</html>",
            "Confirm Leave Request", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                // Call the Oracle stored procedure
                CallableStatement cs = con.prepareCall(
                    "{ CALL submit_leave_request(?, ?) }");
                cs.setInt(1, userId);
                cs.setInt(2, clubId);
                cs.execute();
                cs.close();

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "<html>Leave request submitted for <b>" + clubName + "</b>.<br>" +
                        "The staff incharge will review your request.</html>",
                        "Request Submitted", JOptionPane.INFORMATION_MESSAGE);
                    loadMyClubsData(model);
                    refreshOverviewStats();
                });
            } catch (SQLException ex) {
                String msg = ex.getMessage();
                // Extract meaningful part from Oracle error
                if (msg != null && msg.contains("ORA-20")) {
                    int start = msg.indexOf("ORA-20");
                    // Oracle user-defined errors: ORA-20xxx: message
                    msg = msg.substring(start).split("\n")[0];
                    // Strip the ORA-20xxx: prefix to get the human message
                    if (msg.contains(":")) msg = msg.substring(msg.indexOf(":")+1).trim();
                }
                final String finalMsg = msg;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    finalMsg, "Cannot Submit Request", JOptionPane.WARNING_MESSAGE));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 2 — JOIN CLUB
    // ══════════════════════════════════════════════════════════════
    private void buildJoinClubPage(Dimension scr) {
        JPanel pg = pages[2];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pageTitle(pg, "Join a Club", W);

        JPanel banner = glassCard(); banner.setBounds(24,66,W-48,44); pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "You can join a maximum of 4 clubs. All clubs you have not yet joined are shown below.",
            new Font("SansSerif",Font.PLAIN,12), ACCENT_LIGHT);
        bannerLbl.setBounds(14,12,W-76,20); banner.add(bannerLbl);

        String[] cols = {"Club ID","Club Name","Created Date","Total Members","Action"};
        joinClubModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return c==4; }
        };
        JTable table = new JTable(joinClubModel); styleTable(table); table.setRowHeight(42);

        table.getColumn("Action").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl,Object v,boolean sel,boolean foc,int row,int col) {
                JButton btn = buildButton("Join", true);
                btn.setFont(new Font("Georgia",Font.BOLD,12)); return btn;
            }
        });
        table.getColumn("Action").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public Component getTableCellEditorComponent(
                    JTable tbl,Object v,boolean sel,int row,int col) {
                JButton btn = buildButton("Join", true);
                btn.setFont(new Font("Georgia",Font.BOLD,12));
                btn.addActionListener(e -> {
                    int clubId = (Integer) tbl.getValueAt(row, 0);
                    doJoinClub(clubId, joinClubModel, row);
                    fireEditingStopped();
                });
                return btn;
            }
        });

        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24,118,W-48,H-148); pg.add(sp);

        JLabel infoLbl = makeLabel("", new Font("SansSerif",Font.BOLD,12), ERROR_COL);
        infoLbl.setHorizontalAlignment(SwingConstants.CENTER);
        infoLbl.setBounds(24,H-26,W-48,20); pg.add(infoLbl);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> loadJoinClubData(joinClubModel, infoLbl));
        pg.add(refreshBtn);

        loadJoinClubData(joinClubModel, infoLbl);
    }

    private void loadJoinClubData(DefaultTableModel model, JLabel infoLbl) {
        model.setRowCount(0); infoLbl.setText("");
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement chk = con.prepareStatement(
                    "SELECT COUNT(*) FROM members_in WHERE user_id=?");
                chk.setInt(1, userId);
                ResultSet cr = chk.executeQuery();
                int myClubCount = cr.next() ? cr.getInt(1) : 0;
                if (myClubCount >= 4) {
                    SwingUtilities.invokeLater(() -> {
                        infoLbl.setForeground(ERROR_COL);
                        infoLbl.setText("You are already a member of 4 clubs (maximum).");
                    });
                    return;
                }
                PreparedStatement ps = con.prepareStatement(
                    "SELECT c.club_id,c.club_name,c.created_date," +
                    "COUNT(m.user_id) AS total_members " +
                    "FROM club c " +
                    "LEFT JOIN members_in m ON c.club_id=m.club_id " +
                    "WHERE c.club_id NOT IN " +
                    "  (SELECT club_id FROM members_in WHERE user_id=?) " +
                    "GROUP BY c.club_id,c.club_name,c.created_date " +
                    "ORDER BY c.club_name");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                boolean hasRows = false;
                while (rs.next()) {
                    hasRows = true;
                    Object[] row = {rs.getInt(1),rs.getString(2),rs.getDate(3),
                        rs.getInt(4),"Join"};
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
                if (!hasRows) {
                    SwingUtilities.invokeLater(() -> {
                        infoLbl.setForeground(TEXT_MUTED);
                        infoLbl.setText("You have joined all available clubs.");
                    });
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() ->
                    model.addRow(new Object[]{"—","Error","—","—","—"}));
            }
        }).start();
    }

    private void doJoinClub(int clubId, DefaultTableModel model, int row) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement chk = con.prepareStatement(
                    "SELECT COUNT(*) FROM members_in WHERE user_id=?");
                chk.setInt(1, userId);
                ResultSet cr = chk.executeQuery();
                int myCount = cr.next() ? cr.getInt(1) : 0;
                if (myCount >= 4) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "You are already a member of 4 clubs (maximum).",
                        "Limit Reached", JOptionPane.WARNING_MESSAGE));
                    return;
                }
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO members_in(club_id,user_id,role_type) VALUES(?,?,'member')");
                ps.setInt(1, clubId); ps.setInt(2, userId);
                ps.executeUpdate();
                SwingUtilities.invokeLater(() -> {
                    model.removeRow(row);
                    JOptionPane.showMessageDialog(this,
                        "Successfully joined the club!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                    loadMyClubsData(myClubsTableModel);
                    refreshOverviewStats();
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "Error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 3 — CLUB EVENTS
    // ══════════════════════════════════════════════════════════════
    private void buildClubEventsPage(Dimension scr) {
        JPanel pg = pages[3];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pageTitle(pg, "Club Events", W);

        JPanel banner = glassCard(); banner.setBounds(24,66,W-48,44); pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "Only events from clubs you are a member of are shown. Click Register to sign up.",
            new Font("SansSerif",Font.PLAIN,12), ACCENT_LIGHT);
        bannerLbl.setBounds(14,12,W-76,20); banner.add(bannerLbl);

        String[] cols = {"Event ID","Event Title","Club","Start Date","End Date",
                         "Start Time","End Time","Registered","Action"};
        clubEventsModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return c==8; }
        };
        JTable table = new JTable(clubEventsModel); styleTable(table); table.setRowHeight(42);

        table.getColumn("Action").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl,Object v,boolean sel,boolean foc,int row,int col) {
                boolean done = "Yes".equals(v);
                Date endDate = (Date) tbl.getValueAt(row, 4);
                boolean eventPassed = endDate != null && endDate.before(new Date());
                String btnText = done ? "Registered" : (eventPassed ? "Event Passed" : "Register");
                JButton btn = buildButton(btnText, !done && !eventPassed);
                btn.setFont(new Font("Georgia",Font.BOLD,11));
                btn.setEnabled(!done && !eventPassed);
                return btn;
            }
        });
        table.getColumn("Action").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public Component getTableCellEditorComponent(
                    JTable tbl,Object v,boolean sel,int row,int col) {
                boolean done = "Yes".equals(v);
                Date endDate = (Date) tbl.getValueAt(row, 4);
                boolean eventPassed = endDate != null && endDate.before(new Date());
                String btnText = done ? "Registered" : (eventPassed ? "Event Passed" : "Register");
                JButton btn = buildButton(btnText, !done && !eventPassed);
                btn.setFont(new Font("Georgia",Font.BOLD,11));
                if (!done && !eventPassed) {
                    btn.addActionListener(e -> {
                        int evId = (Integer) tbl.getValueAt(row, 0);
                        doRegisterEvent(evId, clubEventsModel, row);
                        fireEditingStopped();
                    });
                }
                return btn;
            }
        });

        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24,118,W-48,H-148); pg.add(sp);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> loadClubEventsData(clubEventsModel));
        pg.add(refreshBtn);

        loadClubEventsData(clubEventsModel);
    }

    private void loadClubEventsData(DefaultTableModel model) {
        model.setRowCount(0);
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT e.event_id,e.event_title,c.club_name,e.start_date,e.end_date," +
                    "e.start_time,e.end_time," +
                    "CASE WHEN r.user_id IS NOT NULL THEN 'Yes' ELSE 'No' END AS is_reg " +
                    "FROM event e " +
                    "JOIN club c ON e.club_id=c.club_id " +
                    "LEFT JOIN registers r ON e.event_id=r.event_id AND r.user_id=? " +
                    "WHERE e.club_id IN (SELECT club_id FROM members_in WHERE user_id=?) " +
                    "ORDER BY e.start_date DESC");
                ps.setInt(1, userId); ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row = {rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getDate(4),rs.getDate(5),rs.getString(6),rs.getString(7),
                        rs.getString(8),rs.getString(8)};
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> model.addRow(
                    new Object[]{"—","Error","—","—","—","—","—","—","—"}));
            }
        }).start();
    }

    private void doRegisterEvent(int eventId, DefaultTableModel model, int row) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO registers(user_id,event_id,reg_date,status) " +
                    "VALUES(?,?,SYSDATE,'Registered')");
                ps.setInt(1, userId); ps.setInt(2, eventId);
                ps.executeUpdate();
                SwingUtilities.invokeLater(() -> {
                    model.setValueAt("Yes", row, 7);
                    model.setValueAt("Yes", row, 8);
                    JOptionPane.showMessageDialog(this,
                        "Registered successfully!", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                    loadMyEventsData(myEventsModel);
                    refreshOverviewStats();
                    if (feedbackEvCombo != null) loadFeedbackEventCombo(feedbackEvCombo);
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    ex.getMessage().contains("ORA-00001") ? "Already registered." :
                    "Error: "+ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 4 — MY EVENTS
    // ══════════════════════════════════════════════════════════════
    private void buildMyEventsPage(Dimension scr) {
        JPanel pg = pages[4];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pageTitle(pg, "My Events", W);

        String[] cols = {"Event ID","Event Title","Club","Start Date","End Date",
                         "Reg Date","Status","Attendance"};
        myEventsModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return false; }
        };
        JTable table = new JTable(myEventsModel);

        table.getColumn("Status").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl,Object v,boolean sel,boolean foc,int row,int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(
                    tbl,v,sel,foc,row,col);
                Date endDate = (Date) tbl.getValueAt(row, 4);
                Date today   = new Date();
                String status = (String) v;
                if ("Registered".equalsIgnoreCase(status)
                        && endDate != null && endDate.before(today)) {
                    l.setText("Completed"); l.setForeground(SUCCESS_COL);
                } else { l.setForeground(TEXT_PRIMARY); }
                l.setOpaque(true);
                l.setBackground(sel ? new Color(100,50,200,120) :
                    row%2==0 ? new Color(12,6,30) : new Color(20,10,45));
                l.setBorder(new EmptyBorder(0,12,0,12));
                return l;
            }
        });
        styleTable(table);

        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24,72,W-48,H-100); pg.add(sp);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> loadMyEventsData(myEventsModel));
        pg.add(refreshBtn);

        loadMyEventsData(myEventsModel);
    }

    private void loadMyEventsData(DefaultTableModel model) {
        model.setRowCount(0);
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT e.event_id,e.event_title,c.club_name,e.start_date,e.end_date," +
                    "r.reg_date,r.status," +
                    "NVL((SELECT sa.attendance_status FROM student_attendance sa " +
                    "  WHERE sa.user_id=? AND sa.event_id=r.event_id AND ROWNUM=1),'Not Marked') " +
                    "FROM registers r " +
                    "JOIN event e ON r.event_id=e.event_id " +
                    "JOIN club c ON e.club_id=c.club_id " +
                    "WHERE r.user_id=? ORDER BY e.start_date DESC");
                ps.setInt(1, userId); ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row = {rs.getInt(1),rs.getString(2),rs.getString(3),
                        rs.getDate(4),rs.getDate(5),rs.getDate(6),
                        rs.getString(7),rs.getString(8)};
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> model.addRow(
                    new Object[]{"—","Error","—","—","—","—","—","—"}));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 5 — FEEDBACK
    // ══════════════════════════════════════════════════════════════
    private void buildFeedbackPage(Dimension scr) {
        JPanel pg = pages[5];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H, cx = W/2;
        pageTitle(pg, "Give Feedback", W);

        String[] cols = {"Event Title","Club","Rating","Comments"};
        feedbackHistModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return false; }
        };
        JTable histTable = new JTable(feedbackHistModel); styleTable(histTable);
        int tableH = (H-100)/2;
        JScrollPane hsp = styledTableScroll(histTable);
        hsp.setBounds(24,72,W-48,tableH); pg.add(hsp);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        refreshBtn.addActionListener(e -> {
            feedbackHistModel.setRowCount(0);
            loadFeedbackHistory(feedbackHistModel);
            if (feedbackEvCombo != null) loadFeedbackEventCombo(feedbackEvCombo);
        });
        pg.add(refreshBtn);
        loadFeedbackHistory(feedbackHistModel);

        int fy = 72+tableH+14;
        int cw = 560, cardX = cx-cw/2;
        JPanel card = glassCard();
        card.setBounds(cardX,fy,cw,H-fy-24); pg.add(card);

        int fx=28, fw=cw-56, cy=18;
        JLabel fTitle = makeLabel("Submit New Feedback",
            new Font("Georgia",Font.BOLD,15), ACCENT_LIGHT);
        fTitle.setBounds(fx,cy,300,22); card.add(fTitle); cy+=34;

        JLabel evLbl = makeLabel("Select Event",
            new Font("SansSerif",Font.PLAIN,12), TEXT_MUTED);
        evLbl.setBounds(fx,cy,fw-120,18); card.add(evLbl);
        JLabel ratLbl2 = makeLabel("Rating",
            new Font("SansSerif",Font.PLAIN,12), TEXT_MUTED);
        ratLbl2.setBounds(fx+fw-110,cy,110,18); card.add(ratLbl2); cy+=18;

        feedbackEvCombo = styledCombo(new String[]{"-- Loading events --"});
        feedbackEvCombo.setBounds(fx,cy,fw-120,38); card.add(feedbackEvCombo);
        JComboBox<Integer> ratCombo = styledIntCombo(new Integer[]{5,4,3,2,1});
        ratCombo.setBounds(fx+fw-110,cy,110,38); card.add(ratCombo); cy+=50;

        JLabel cmtLbl = makeLabel("Comments (max 100 chars)",
            new Font("SansSerif",Font.PLAIN,12), TEXT_MUTED);
        cmtLbl.setBounds(fx,cy,fw,18); card.add(cmtLbl); cy+=18;
        JTextArea cmtArea = new JTextArea();
        cmtArea.setBackground(new Color(30,16,60)); cmtArea.setForeground(TEXT_PRIMARY);
        cmtArea.setFont(new Font("SansSerif",Font.PLAIN,12));
        cmtArea.setLineWrap(true); cmtArea.setWrapStyleWord(true);
        cmtArea.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cmtArea.setBounds(fx,cy,fw,70); card.add(cmtArea); cy+=80;

        JLabel statusLbl = makeLabel("", new Font("SansSerif",Font.BOLD,12), ERROR_COL);
        statusLbl.setHorizontalAlignment(SwingConstants.CENTER);
        statusLbl.setBounds(0,cy,cw,20); card.add(statusLbl);

        JButton submitBtn = buildButton("Submit Feedback", true);
        submitBtn.setBounds(fx,cy+24,180,40); card.add(submitBtn);

        loadFeedbackEventCombo(feedbackEvCombo);

        submitBtn.addActionListener(e -> {
            String sel = (String) feedbackEvCombo.getSelectedItem();
            Integer rat = (Integer) ratCombo.getSelectedItem();
            String cmt = cmtArea.getText().trim();
            if (sel == null || sel.startsWith("--")) {
                statusLbl.setForeground(ERROR_COL);
                statusLbl.setText("Select an event."); return;
            }
            int evId = Integer.parseInt(sel.split("\\|")[0].trim());
            submitFeedback(evId, rat, cmt, statusLbl, feedbackHistModel, cmtArea);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 6 — MY ATTENDANCE
    // ══════════════════════════════════════════════════════════════
    private void buildMyAttendancePage(Dimension scr) {
        JPanel pg = pages[6];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pg.removeAll();
        pageTitle(pg, "My Attendance", W);

        int cardY=72, cardH=90, cardW=190, gap=18;
        myAttTotalCard   = statCard("📋","Total Events","—");
        myAttPresentCard = statCard("✅","Present","—");
        myAttAbsentCard  = statCard("❌","Absent","—");
        myAttTotalCard  .setBounds(24,               cardY, cardW, cardH);
        myAttPresentCard.setBounds(24+cardW+gap,     cardY, cardW, cardH);
        myAttAbsentCard .setBounds(24+2*(cardW+gap), cardY, cardW, cardH);
        pg.add(myAttTotalCard); pg.add(myAttPresentCard); pg.add(myAttAbsentCard);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180,20,160,36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        pg.add(refreshBtn);

        JPanel banner = glassCard();
        banner.setBounds(24,cardY+cardH+10,W-48,36); pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "\u2139  Attendance is marked by your staff/incharge after the event. " +
            "This page auto-refreshes every 15 seconds.",
            new Font("SansSerif",Font.PLAIN,12), TEXT_MUTED);
        bannerLbl.setBounds(12,9,W-96,18); banner.add(bannerLbl);

        String[] cols = {"Date","Event Name","Type","Status"};
        myAttendanceModel = new DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c) { return false; }
        };
        JTable table = new JTable(myAttendanceModel);
        styleTable(table); table.setRowHeight(36);

        table.getColumn("Status").setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t,Object v,boolean sel,boolean foc,int row,int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    t,v,sel,foc,row,col);
                String s = v == null ? "" : v.toString();
                if ("Present".equalsIgnoreCase(s)) {
                    lbl.setForeground(SUCCESS_COL); lbl.setText("\u2713  Present");
                } else if ("Absent".equalsIgnoreCase(s)) {
                    lbl.setForeground(ERROR_COL); lbl.setText("\u2717  Absent");
                } else {
                    lbl.setForeground(new Color(255,180,60)); lbl.setText("\u2014  "+s);
                }
                lbl.setFont(new Font("SansSerif",Font.BOLD,13));
                lbl.setBackground(sel ? new Color(100,50,200,120) :
                    row%2==0 ? new Color(12,6,30) : new Color(20,10,45));
                lbl.setOpaque(true);
                lbl.setBorder(new EmptyBorder(0,12,0,12));
                return lbl;
            }
        });
        table.getColumn("Status")    .setPreferredWidth(120);
        table.getColumn("Event Name").setPreferredWidth(340);
        table.getColumn("Type")      .setPreferredWidth(160);
        table.getColumn("Date")      .setPreferredWidth(110);

        int tableY = cardY+cardH+54;
        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24, tableY, W-48, H-tableY-24); pg.add(sp);

        loadMyAttendanceData(myAttendanceModel,
            myAttTotalCard, myAttPresentCard, myAttAbsentCard);

        refreshBtn.addActionListener(e -> {
            myAttendanceModel.setRowCount(0);
            setStatCardValue(myAttTotalCard,   "—");
            setStatCardValue(myAttPresentCard, "—");
            setStatCardValue(myAttAbsentCard,  "—");
            loadMyAttendanceData(myAttendanceModel,
                myAttTotalCard, myAttPresentCard, myAttAbsentCard);
        });
    }

    private void loadMyAttendanceData(DefaultTableModel model,
                                      JPanel totalCard,
                                      JPanel presentCard,
                                      JPanel absentCard) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT sa.attendance_date, se.event_title, se.event_type, " +
                    "sa.attendance_status " +
                    "FROM student_attendance sa " +
                    "JOIN staff_event se ON sa.event_id = se.event_id " +
                    "WHERE sa.user_id = ? " +
                    "UNION ALL " +
                    "SELECT sa.attendance_date, e.event_title, 'Club Event', " +
                    "sa.attendance_status " +
                    "FROM student_attendance sa " +
                    "JOIN event e ON sa.event_id = e.event_id " +
                    "WHERE sa.user_id = ? " +
                    "ORDER BY 1 DESC");
                ps.setInt(1, userId); ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                int total=0, present=0, absent=0;
                while (rs.next()) {
                    total++;
                    String status = rs.getString(4);
                    if ("Present".equalsIgnoreCase(status)) present++;
                    else if ("Absent".equalsIgnoreCase(status)) absent++;
                    Object[] row = {rs.getDate(1),rs.getString(2),
                        rs.getString(3),status};
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
                final int fT=total, fP=present, fA=absent;
                SwingUtilities.invokeLater(() -> {
                    setStatCardValue(totalCard,   String.valueOf(fT));
                    setStatCardValue(presentCard, String.valueOf(fP));
                    setStatCardValue(absentCard,  String.valueOf(fA));
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() ->
                    model.addRow(new Object[]{"—","Error: "+ex.getMessage(),"—","—"}));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  PAGE 7 — CERTIFICATES
    // ══════════════════════════════════════════════════════════════
    private void buildCertificatesPage(Dimension scr) {
        JPanel pg = pages[7];
        int W = pw - SIDEBAR_W_PX, H = ph - TOP_H;
        pg.removeAll();
        pageTitle(pg, "My Certificates", W);

        certTotalCard = statCard("\uD83C\uDFC5", "Total Certificates", "—");
        certTotalCard.setBounds(24, 72, 210, 90);
        pg.add(certTotalCard);

        JButton refreshBtn = buildButton("\u21BA Refresh", true);
        refreshBtn.setBounds(W-180, 20, 160, 36);
        refreshBtn.setFont(new Font("Georgia",Font.BOLD,12));
        pg.add(refreshBtn);

        JPanel banner = glassCard();
        banner.setBounds(24, 172, W-48, 38);
        pg.add(banner);
        JLabel bannerLbl = makeLabel(
            "\u2139  Certificates are issued by staff after events. " +
            "Click \"\uD83C\uDF93 View\" to open, download PNG, or print/save as PDF.",
            new Font("SansSerif",Font.PLAIN,12), TEXT_MUTED);
        bannerLbl.setBounds(12, 10, W-96, 18);
        banner.add(bannerLbl);

        String[] cols = {"Cert ID","Event Name","Club","Certificate Type","Issue Date","Action"};
        certificatesModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 5; }
        };
        JTable table = new JTable(certificatesModel);
        styleTable(table); table.setRowHeight(42);

        table.getColumn("Action").setCellRenderer(new TableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl, Object v, boolean sel,
                    boolean foc, int row, int col) {
                JButton btn = buildButton("\uD83C\uDF93 View", true);
                btn.setFont(new Font("Georgia",Font.BOLD,12));
                return btn;
            }
        });

        table.getColumn("Action").setCellEditor(new DefaultCellEditor(new JCheckBox()) {
            @Override public Component getTableCellEditorComponent(
                    JTable tbl, Object v, boolean sel, int row, int col) {
                JButton btn = buildButton("\uD83C\uDF93 View", true);
                btn.setFont(new Font("Georgia",Font.BOLD,12));
                btn.addActionListener(e -> {
                    fireEditingStopped();
                    Object val = tbl.getValueAt(row, 0);
                    if (!(val instanceof Integer)) {
                        JOptionPane.showMessageDialog(null, "Invalid Certificate");
                        return;
                    }
                    int certId = (Integer) val;
                    String evName    = (String)  tbl.getValueAt(row, 1);
                    String certType  = (String)  tbl.getValueAt(row, 3);
                    Object issueDateObj = tbl.getValueAt(row, 4);
                    String issueDate = issueDateObj != null ? issueDateObj.toString() : "—";
                    openCertificateViewer(certId, evName, certType, issueDate);
                });
                return btn;
            }
        });

        table.getColumn("Cert ID")          .setPreferredWidth(70);
        table.getColumn("Event Name")       .setPreferredWidth(280);
        table.getColumn("Club")             .setPreferredWidth(160);
        table.getColumn("Certificate Type") .setPreferredWidth(140);
        table.getColumn("Issue Date")       .setPreferredWidth(110);
        table.getColumn("Action")           .setPreferredWidth(110);

        int tableY = 218;
        JScrollPane sp = styledTableScroll(table);
        sp.setBounds(24, tableY, W-48, H-tableY-24);
        pg.add(sp);

        loadCertificatesData(certificatesModel);

        refreshBtn.addActionListener(e -> {
            certificatesModel.setRowCount(0);
            setStatCardValue(certTotalCard, "—");
            loadCertificatesData(certificatesModel);
        });
    }

    private void loadCertificatesData(DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT cert.certificate_id, e.event_title, c.club_name, " +
                    "       cert.certificate_type, cert.issue_date " +
                    "FROM certificate cert " +
                    "JOIN event e ON cert.event_id = e.event_id " +
                    "JOIN club  c ON e.club_id     = c.club_id " +
                    "WHERE cert.user_id = ? " +
                    "UNION ALL " +
                    "SELECT cert.certificate_id, se.event_title, 'Staff Event', " +
                    "       cert.certificate_type, cert.issue_date " +
                    "FROM certificate cert " +
                    "JOIN staff_event se ON cert.event_id = se.event_id " +
                    "WHERE cert.user_id = ? " +
                    "ORDER BY issue_date DESC");
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    count++;
                    Object[] row = {
                        rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getDate(5), "\uD83C\uDF93 View"
                    };
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
                final int finalCount = count;
                SwingUtilities.invokeLater(() ->
                    setStatCardValue(certTotalCard, String.valueOf(finalCount)));
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    model.addRow(new Object[]{"—","Error: "+ex.getMessage(),"—","—","—","—"});
                    setStatCardValue(certTotalCard, "0");
                });
            }
        }).start();
    }

    private void openCertificateViewer(int certId, String evName,
                                        String certType, String issueDate) {
        new Thread(() -> {
            CertificateViewer.CertData cd = new CertificateViewer.CertData();
            cd.certId      = certId;
            cd.studentName = studentName;
            cd.eventName   = evName;
            cd.certType    = certType;
            cd.issueDate   = issueDate;
            cd.deptName    = "";
            try (Connection con = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT NVL(c.dept_name, '') " +
                    "FROM certificate cert " +
                    "JOIN event e ON cert.event_id = e.event_id " +
                    "JOIN club  c ON e.club_id     = c.club_id " +
                    "WHERE cert.certificate_id = ? " +
                    "UNION ALL " +
                    "SELECT '' " +
                    "FROM certificate cert " +
                    "JOIN staff_event se ON cert.event_id = se.event_id " +
                    "WHERE cert.certificate_id = ? " +
                    "AND NOT EXISTS (" +
                    "  SELECT 1 FROM certificate c2 JOIN event e2 ON c2.event_id = e2.event_id " +
                    "  WHERE c2.certificate_id = ?)");
                ps.setInt(1, certId); ps.setInt(2, certId); ps.setInt(3, certId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) cd.deptName = rs.getString(1);
            } catch (SQLException ignored) {}
            SwingUtilities.invokeLater(() ->
                CertificateViewer.show(StudentDashboard.this, cd));
        }).start();
    }

    // ══════════════════════════════════════════════════════════════
    //  SHARED DB HELPERS
    // ══════════════════════════════════════════════════════════════
    private void loadFeedbackHistory(DefaultTableModel model) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT f.feedback_id,e.event_title,c.club_name,f.rating,f.comments " +
                    "FROM feedback f " +
                    "JOIN event e ON f.event_id=e.event_id " +
                    "JOIN club c ON e.club_id=c.club_id " +
                    "WHERE f.user_id=? ORDER BY f.feedback_id DESC");
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Object[] row = {rs.getString(2),rs.getString(3),
                        rs.getInt(4),rs.getString(5)};
                    SwingUtilities.invokeLater(() -> model.addRow(row));
                }
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() ->
                    model.addRow(new Object[]{"—","Error","—","—","—"}));
            }
        }).start();
    }

    private void loadFeedbackEventCombo(JComboBox<String> combo) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                PreparedStatement ps = con.prepareStatement(
                    "SELECT e.event_id,e.event_title,c.club_name " +
                    "FROM registers r " +
                    "JOIN event e ON r.event_id=e.event_id " +
                    "JOIN club c ON e.club_id=c.club_id " +
                    "WHERE r.user_id=? " +
                    "AND e.end_date < SYSDATE " +
                    "AND e.event_id NOT IN " +
                    "  (SELECT event_id FROM feedback WHERE user_id=?) " +
                    "ORDER BY e.event_title");
                ps.setInt(1, userId); ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
                m.addElement("-- Select Event --");
                while (rs.next())
                    m.addElement(rs.getInt(1)+" | "+rs.getString(2)+
                        " ("+rs.getString(3)+")");
                SwingUtilities.invokeLater(() -> combo.setModel(m));
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    combo.removeAllItems(); combo.addItem("-- Error --");
                });
            }
        }).start();
    }

    private void submitFeedback(int eventId, int rating, String comments,
                                JLabel statusLbl, DefaultTableModel histModel,
                                JTextArea cmtArea) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass)) {
                ResultSet seq = con.createStatement()
                    .executeQuery("SELECT NVL(MAX(feedback_id),0)+1 FROM feedback");
                int fbId = seq.next() ? seq.getInt(1) : 1;
                String cmt = comments.length()>100
                    ? comments.substring(0,100) : comments;
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO feedback(feedback_id,user_id,event_id,rating,comments) " +
                    "VALUES(?,?,?,?,?)");
                ps.setInt(1,fbId); ps.setInt(2,userId); ps.setInt(3,eventId);
                ps.setInt(4,rating); ps.setString(5,cmt);
                ps.executeUpdate();
                histModel.setRowCount(0);
                loadFeedbackHistory(histModel);
                loadFeedbackEventCombo(feedbackEvCombo);
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(SUCCESS_COL);
                    statusLbl.setText("Feedback submitted successfully!");
                    cmtArea.setText("");
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLbl.setForeground(ERROR_COL);
                    statusLbl.setText("Error: "+ex.getMessage()
                        .substring(0, Math.min(50,ex.getMessage().length())));
                });
            }
        }).start();
    }

    private void loadStatCount(String sql, JPanel card) {
        new Thread(() -> {
            try (Connection con = DriverManager.getConnection(dbUrl,dbUser,dbPass);
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                String val = rs.next() ? String.valueOf(rs.getInt(1)) : "0";
                SwingUtilities.invokeLater(() -> setStatCardValue(card, val));
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> setStatCardValue(card, "0"));
            }
        }).start();
    }

    private void setStatCardValue(JPanel card, String value) {
        int count = 0;
        for (Component c : card.getComponents()) {
            if (c instanceof JLabel) {
                count++;
                if (count == 2) { ((JLabel)c).setText(value); break; }
            }
        }
        card.repaint();
    }

    // ══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════
    private void pageTitle(JPanel pg, String text, int W) {
        JLabel t = makeLabel(text,
            new Font("Georgia",Font.BOLD|Font.ITALIC,26), TEXT_PRIMARY);
        t.setHorizontalAlignment(SwingConstants.LEFT);
        t.setBounds(24,18,W-220,36); pg.add(t);
        JSeparator s = buildSeparator();
        s.setBounds(24,58,W/3,2); pg.add(s);
    }

    private JPanel glassCard() {
        JPanel c = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
                g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,18,18);
                g2.setPaint(new GradientPaint(0,0,new Color(150,80,255,70),
                    getWidth(),0,new Color(0,0,0,0)));
                g2.fillRoundRect(0,0,getWidth(),3,3,3);
            }
        };
        c.setOpaque(false); return c;
    }

    private JPanel statCard(String emoji, String label, String value) {
        JPanel c = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(130,60,255,28));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
                g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);
            }
        };
        c.setOpaque(false);
        JLabel ic = new JLabel(emoji);
        ic.setFont(new Font("Segoe UI Emoji",Font.PLAIN,22));
        ic.setBounds(12,12,30,28); c.add(ic);
        JLabel vl = makeLabel(value, new Font("Georgia",Font.BOLD,28), TEXT_PRIMARY);
        vl.setBounds(12,40,120,36); c.add(vl);
        JLabel lb = makeLabel(label, new Font("SansSerif",Font.PLAIN,11), TEXT_MUTED);
        lb.setBounds(12,76,160,18); c.add(lb);
        return c;
    }

    private JPanel avatarPanel(String name, int size) {
        JPanel av = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new RadialGradientPaint(
                    getWidth()/2f, getHeight()/2f, getWidth()/2f,
                    new float[]{0f,1f},
                    new Color[]{new Color(130,60,255,200),
                                new Color(80,20,180,200)}));
                g2.fillOval(0,0,getWidth()-1,getHeight()-1);
                String init = name.isEmpty() ? "S" :
                    String.valueOf(name.charAt(0)).toUpperCase();
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Georgia",Font.BOLD,(int)(size*0.42)));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(init,
                    getWidth()/2-fm.stringWidth(init)/2,
                    getHeight()/2+fm.getAscent()/2-2);
            }
        };
        av.setOpaque(false); return av;
    }

    private JLabel roleBadge(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0,0,new Color(130,60,255),
                    getWidth(),0,new Color(80,20,180)));
                g2.fillRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                super.paintComponent(g);
            }
        };
        l.setFont(new Font("SansSerif",Font.BOLD,11));
        l.setForeground(Color.WHITE); l.setOpaque(false); return l;
    }

    private JButton sidebarButton(String text, boolean active) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (getClientProperty("active") == Boolean.TRUE) {
                    g2.setColor(SIDEBAR_ACT);
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                    g2.setColor(new Color(180,120,255));
                    g2.fillRoundRect(0,6,4,getHeight()-12,4,4);
                }
                super.paintComponent(g);
            }
        };
        btn.putClientProperty("active", active);
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(active ? TEXT_PRIMARY : TEXT_MUTED);
        btn.setFont(new Font("SansSerif",Font.PLAIN+(active?Font.BOLD:0),13));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (btn.getClientProperty("active") != Boolean.TRUE)
                    btn.setForeground(TEXT_PRIMARY);
            }
            public void mouseExited(MouseEvent e) {
                if (btn.getClientProperty("active") != Boolean.TRUE)
                    btn.setForeground(TEXT_MUTED);
            }
        });
        return btn;
    }

    private void setSideActive(JButton btn, boolean active) {
        btn.putClientProperty("active", active);
        btn.setForeground(active ? TEXT_PRIMARY : TEXT_MUTED);
        btn.setFont(new Font("SansSerif",Font.PLAIN+(active?Font.BOLD:0),13));
        btn.repaint();
    }

    private JButton buildButton(String text, boolean primary) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (primary) {
                    g2.setPaint(new GradientPaint(0,0,new Color(140,60,255),
                        getWidth(),getHeight(),new Color(90,20,200)));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                } else {
                    g2.setColor(new Color(255,255,255,18));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                    g2.setColor(CARD_BORDER); g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                }
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false); btn.setBorderPainted(false);
        btn.setFocusPainted(false); btn.setOpaque(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Georgia",Font.BOLD,13));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(GOLD); btn.repaint(); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(Color.WHITE); btn.repaint(); }
        });
        return btn;
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> styledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(30,16,60)); cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif",Font.PLAIN,13));
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> l,Object v,int i,boolean s,boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l,v,i,s,f);
                lb.setBackground(s ? new Color(80,40,160) : new Color(25,12,50));
                lb.setForeground(TEXT_PRIMARY);
                lb.setBorder(new EmptyBorder(4,10,4,10)); return lb;
            }
        });
        return cb;
    }

    @SuppressWarnings("unchecked")
    private JComboBox<Integer> styledIntCombo(Integer[] items) {
        JComboBox<Integer> cb = new JComboBox<>(items);
        cb.setBackground(new Color(30,16,60)); cb.setForeground(TEXT_PRIMARY);
        cb.setFont(new Font("SansSerif",Font.PLAIN,13));
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> l,Object v,int i,boolean s,boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l,v,i,s,f);
                lb.setBackground(s ? new Color(80,40,160) : new Color(25,12,50));
                lb.setForeground(TEXT_PRIMARY);
                lb.setBorder(new EmptyBorder(4,10,4,10)); return lb;
            }
        });
        return cb;
    }

    private JScrollPane styledScroll(JPanel content) {
        JScrollPane sp = new JScrollPane(content,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null); sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setBackground(new Color(30,16,60));
        return sp;
    }

    private JScrollPane styledTableScroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(CARD_BORDER,1));
        sp.setOpaque(false);
        sp.getViewport().setBackground(new Color(12,6,30));
        sp.getVerticalScrollBar().setBackground(new Color(30,16,60));
        return sp;
    }

    private void styleTable(JTable t) {
        t.setBackground(new Color(12,6,30)); t.setForeground(TEXT_PRIMARY);
        t.setFont(new Font("SansSerif",Font.PLAIN,13)); t.setRowHeight(32);
        t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0,0));
        t.setSelectionBackground(new Color(100,50,200,120));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(new Color(25,12,55));
        t.getTableHeader().setForeground(ACCENT_LIGHT);
        t.getTableHeader().setFont(new Font("Georgia",Font.BOLD,13));
        t.getTableHeader().setBorder(
            BorderFactory.createLineBorder(CARD_BORDER,1));
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable tbl,Object v,boolean sel,boolean foc,int row,int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(
                    tbl,v,sel,foc,row,col);
                l.setOpaque(true);
                l.setBackground(sel ? new Color(100,50,200,120) :
                    row%2==0 ? new Color(12,6,30) : new Color(20,10,45));
                l.setForeground(TEXT_PRIMARY);
                l.setFont(new Font("SansSerif",Font.PLAIN,13));
                l.setBorder(new EmptyBorder(0,12,0,12)); return l;
            }
        });
    }

    private JSeparator buildSeparator() {
        return new JSeparator() {
            @Override protected void paintComponent(Graphics g) {
                int w = getWidth(); if (w <= 0) return;
                Graphics2D g2 = (Graphics2D)g;
                g2.setPaint(new LinearGradientPaint(0,0,w,0,
                    new float[]{0f,0.5f,1f},
                    new Color[]{new Color(0,0,0,0),ACCENT,new Color(0,0,0,0)}));
                g2.fillRect(0,0,w,getHeight());
            }
        };
    }

    private JLabel makeLabel(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font); l.setForeground(color); l.setOpaque(false); return l;
    }

    private ImageIcon buildLogoIcon(int size) {
        BufferedImage img = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(130,60,255,55)); g.fillOval(0,0,size,size);
        g.setColor(ACCENT); g.setStroke(new BasicStroke(size*0.04f));
        g.drawOval((int)(size*0.03),(int)(size*0.03),
            (int)(size*0.94),(int)(size*0.94));
        g.setPaint(new RadialGradientPaint(size/2f,size/2f,size*0.42f,
            new float[]{0f,1f},
            new Color[]{new Color(160,80,255,120),new Color(0,0,0,0)}));
        g.fillOval((int)(size*0.08),(int)(size*0.08),
            (int)(size*0.84),(int)(size*0.84));
        g.setColor(ACCENT_LIGHT);
        int s2=size/2, cap=(int)(size*0.26);
        g.fillPolygon(
            new int[]{s2,s2+cap,s2,s2-cap},
            new int[]{(int)(size*0.28),(int)(size*0.40),
                      (int)(size*0.52),(int)(size*0.40)},4);
        g.setColor(new Color(220,180,255));
        int tw=(int)(size*0.36), th=(int)(size*0.10);
        g.fillRoundRect(s2-tw/2,(int)(size*0.22),tw,th,6,6);
        g.setColor(GOLD); g.setStroke(new BasicStroke(size*0.025f));
        int tx = s2+tw/2-(int)(size*0.04);
        g.drawLine(tx,(int)(size*0.27),tx,(int)(size*0.52));
        g.fillOval(tx-(int)(size*0.04),(int)(size*0.51),
            (int)(size*0.08),(int)(size*0.08));
        g.dispose(); return new ImageIcon(img);
    }
}

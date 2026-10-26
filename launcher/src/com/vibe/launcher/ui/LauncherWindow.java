package com.vibe.launcher.ui;

import com.vibe.launcher.auth.Account;
import com.vibe.launcher.config.LauncherConfig;
import com.vibe.launcher.game.FabricInstaller;
import com.vibe.launcher.game.GameLauncher;
import com.vibe.launcher.game.ModDependencies;
import com.vibe.launcher.game.ModUpdater;
import com.vibe.launcher.game.SelfHeal;
import com.vibe.launcher.game.VersionInstaller;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * The launcher window: pick a version, sign in, press Play.
 *
 * Threading contract (the reason this class exists at all):
 *  - every network call, download and game launch happens on a worker thread;
 *  - the UI is only ever touched from `SwingUtilities.invokeLater`;
 *  - a single [busy] flag stops the user from starting two of anything.
 *
 * Nothing here can throw at the user: every worker wraps its body in a
 * try/catch that reports into the log and the status bar.
 */
public final class LauncherWindow extends JFrame {

    private final Path appDir;
    private final Path configFile;
    private LauncherConfig config;

    private final DefaultListModel<String> versionModel = new DefaultListModel<>();
    private final Map<String, VersionInstaller.VersionEntry> versionIndex = new HashMap<>();
    private final VersionGrid versionGrid = new VersionGrid();

    private final List<VersionCard> supportedVersionCards = new ArrayList<>();
    private final List<VersionCard> unsupportedVersionCards = new ArrayList<>();
    private boolean viewingSupportedVersions = true;
    private RoundedButton supportedTabBtn;
    private RoundedButton unsupportedTabBtn;
    private JLabel versionSubLabel;

    private final JLabel accountLabel = new JLabel();
    private final JLabel statusLabel = new JLabel("Ready");
    private final VibeProgressBar progress = new VibeProgressBar();
    private final JTextArea logArea = new JTextArea();
    private final JButton playButton = RoundedButton.primary("PLAY");

    // -- Auto-update -------------------------------------------------------
    /** Message shown next to the update button; empty when there is nothing. */
    private final JLabel updateLabel = new JLabel("");
    private RoundedButton updateButton;
    /** Result of the last check, reused by the download action. */
    private ModUpdater.Result lastUpdate;

    private final JCheckBox fabricBox = new JCheckBox("Fabric loader");
    private final JCheckBox closeBox = new JCheckBox("Close launcher when the game starts");
    /** Installs the Sodium/Lithium-style performance pack on first launch. */
    private final JCheckBox perfBox = new JCheckBox("FPS Boost (Sodium + optimisation pack)");
    private final JSpinner memorySpinner;
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JTextField jvmArgsField = new JTextField();
    private final JTextField serverField = new JTextField();
    private final JTextField portField = new JTextField();
    /** GitHub repo the mod updates from; blank disables the updater. */
    private final JTextField updateRepoField = new JTextField();

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private Process runningGame;

    // -- Navigation --------------------------------------------------------
    /** The left navigation column. */
    private SidebarNav sidebar;
    /** Holds one card per navigation entry, cross-fading between them. */
    private CardStack cardPanel;
    /** One component per navigation entry, keyed by card id. */
    private final Map<String, JComponent> cards = new HashMap<>();
    /** The landing screen. */
    private HomeScreen homeScreen;

    // -- Card ids ----------------------------------------------------------
    private static final String CARD_HOME = "home";
    private static final String CARD_VERSIONS = "versions";
    private static final String CARD_LOG = "log";
    private static final String CARD_SETTINGS = "settings";
    private static final String CARD_MODS = "mods";
    private static final String CARD_ACCOUNT = "account";
    private static final String CARD_STORE = "store";
    private static final String CARD_SERVER = "server";

    // -- Mods card ---------------------------------------------------------
    /** Rows of the installed-mod list. */
    private JPanel modsList;
    /** Summary line above the list. */
    private JLabel modsSummary;

    // -- Account card ------------------------------------------------------
    /** The signed-in name, shown large on the account card. */
    private JLabel accountNameLabel = new JLabel();
    private JLabel accountStatusLabel = new JLabel();

    public LauncherWindow(Path appDir, Path configFile) {
        super("Vibe Client");
        this.appDir = appDir;
        this.configFile = configFile;
        this.config = LauncherConfig.load(configFile);

        this.memorySpinner = new JSpinner(new SpinnerNumberModel(config.memoryMb, 512, 32768, 256));
        this.widthSpinner = new JSpinner(new SpinnerNumberModel(config.windowWidth, 320, 7680, 1));
        this.heightSpinner = new JSpinner(new SpinnerNumberModel(config.windowHeight, 240, 4320, 1));

        buildUi();
        applyIcon();
        applyConfigToUi();
        append("Vibe Client Launcher ready.");
        append("Game folder: " + gameDir());
        refreshVersions();

        // Quiet check on startup: only reports when something is actually
        // available, so a stable install stays silent.
        if (config.autoCheckUpdates) {
            SwingUtilities.invokeLater(() -> checkForUpdates(true));
        }
    }

    // =======================================================================
    // UI construction
    // =======================================================================

    /**
     * Sets the window icon.
     *
     * Loaded as a resource so the same jar works when the app is installed
     * (resources are inside the packaged runtime image) and when it runs from
     * the source tree. A missing icon must never stop the launcher, so every
     * failure is swallowed.
     */
    private void applyIcon() {
        try (InputStream in = LauncherWindow.class.getResourceAsStream("/vibeclient-256.png")) {
            if (in == null) return;
            BufferedImage image = ImageIO.read(in);
            if (image != null) setIconImage(image);
        } catch (Exception ignored) {
            // Cosmetic only.
        }
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 720));
        setSize(1080, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // The animated backdrop IS the content pane; everything else is added
        // on top of it. Every child panel is made non-opaque so the neon shows
        // through, which is what gives the window depth instead of flat panels
        // sitting on a flat colour.
        // Procedural deep-space backdrop. Replaces ArtBackground, which loaded
        // /hero.jpg from the classpath — an asset package-exe.cmd never packed,
        // so the shipped launcher silently fell back to a flat gradient.
        SpaceBackground background = new SpaceBackground();
        background.setLayout(new BorderLayout());
        setContentPane(background);

        // West: the navigation column. Its callback shows the card and keeps
        // the sidebar's own highlight in sync — the sidebar paints the active
        // row itself, so it must never be out of step with the visible card.
        sidebar = new SidebarNav(id -> {
            showCard(id);
            sidebar.setSelected(id);
        });
        sidebar.setItems(List.of(
            new SidebarNav.Item(CARD_HOME, "Home"),
            new SidebarNav.Item(CARD_VERSIONS, "Versions"),
            new SidebarNav.Item(CARD_LOG, "Log"),
            new SidebarNav.Item(CARD_SETTINGS, "Settings"),
            new SidebarNav.Item(CARD_MODS, "Mods"),
            new SidebarNav.Item(CARD_STORE, "Cosmetics"),
            new SidebarNav.Item(CARD_SERVER, "Vibe Network"),
            new SidebarNav.Item(CARD_ACCOUNT, "Account")));
        sidebar.setOnAccount(() -> openAccountDialog());
        add(sidebar, BorderLayout.WEST);

        // Center: one card per navigation entry, swapped by a CardLayout.
        // A CardLayout instead of JTabbedPane: Nimbus' tab shapes are painted
        // by baked-in painters that ignore every colour setting, and fighting
        // them produced an invisible active tab.
        homeScreen = new HomeScreen(new HomeScreen.Listener() {
            @Override
            public void onPlay() {
                startGame();
            }

            @Override
            public void onOpenVersions() {
                showCard(CARD_VERSIONS);
                sidebar.setSelected(CARD_VERSIONS);
            }

            @Override
            public void onAccount() {
                openAccountDialog();
            }

            @Override
            public void onNavigate(String cardId) {
                showCard(cardId);
                sidebar.setSelected(cardId);
            }
        });

        // An animated stack rather than a plain CardLayout: an instant card
        // swap is the one hard cut left in the window after every other
        // surface was animated, and it reads as unfinished. See CardStack.
        cardPanel = new CardStack();
        cardPanel.setOpaque(false);
        cards.put(CARD_HOME, homeScreen);
        cards.put(CARD_VERSIONS, buildVersionsPanel());
        cards.put(CARD_LOG, buildLogPanel());
        cards.put(CARD_SETTINGS, buildSettingsPanel());
        cards.put(CARD_MODS, buildModsPanel());
        cards.put(CARD_STORE, buildStorePanel());
        cards.put(CARD_SERVER, new ServerScreen());
        cards.put(CARD_ACCOUNT, buildAccountPanel());
        add(cardPanel, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        // Restore window state-independent settings that live in fields.
        fabricBox.addActionListener(e -> config.useFabric = fabricBox.isSelected());
        closeBox.addActionListener(e -> config.closeLauncherOnStart = closeBox.isSelected());
        perfBox.addActionListener(e -> config.performancePack = perfBox.isSelected());

        // Only one thing at a time.
        progress.setVisible(false);
        progress.setStringPainted(true);

        // The landing screen is what the window opens on.
        showCard(CARD_HOME);

        // The home screen and the sidebar need the current selection before
        // the first paint.
        homeScreen.setVersion(config.selectedVersion);
        homeScreen.setGameFolder(config.gameDir);
    }

    /**
     * Switches the visible card.
     *
     * The sidebar paints its own active row, so the caller is responsible for
     * keeping the two in step; the home screen's own shortcuts do that
     * themselves.
     */
    private void showCard(String card) {
        if (cardPanel == null) return;
        JComponent target = cards.get(card);
        if (target == null) return;
        cardPanel.show(target);

        // The home screen already carries the primary PLAY button, so the
        // footer's copy is hidden there. Everywhere else it is the only way to
        // launch, so it comes back. Without this the window showed two PLAY
        // buttons side by side, which reads as a UI bug to anyone who sees it.
        playButton.setVisible(!"home".equals(card));
    }

    private JPanel buildVersionsPanel() {
        JPanel panel = Theme.panel();
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(Theme.padding(12, 12, 12, 12));

        JPanel topContainer = new JPanel(new BorderLayout(0, 4));
        topContainer.setOpaque(false);

        JLabel caption = new JLabel("Versions");
        caption.setFont(Theme.BODY_BOLD);
        caption.setForeground(Theme.TEXT);
        topContainer.add(caption, BorderLayout.NORTH);

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setOpaque(false);

        supportedTabBtn = RoundedButton.primary("Supported (Client)");
        supportedTabBtn.addActionListener(e -> {
            viewingSupportedVersions = true;
            updateVersionTabStyles();
            updateVersionGridDisplay();
            selectConfiguredVersion();
        });

        unsupportedTabBtn = RoundedButton.secondary("Unsupported (Vanilla)");
        unsupportedTabBtn.addActionListener(e -> {
            viewingSupportedVersions = false;
            updateVersionTabStyles();
            updateVersionGridDisplay();
            selectConfiguredVersion();
        });

        tabs.add(supportedTabBtn);
        tabs.add(unsupportedTabBtn);
        topContainer.add(tabs, BorderLayout.CENTER);

        versionSubLabel = new JLabel("Supported versions (1.21 family) with Vibe Client mods.");
        versionSubLabel.setFont(Theme.SMALL);
        versionSubLabel.setForeground(Theme.TEXT_MUTED);
        topContainer.add(versionSubLabel, BorderLayout.SOUTH);

        panel.add(topContainer, BorderLayout.NORTH);

        versionGrid.setOnSelect(card -> {
            config.selectedVersion = card.id;
            // A loader version belongs to one game version only.
            config.fabricLoaderVersion = null;
            homeScreen.setVersion(card.id);
            warnIfUnsupported(card.id);
        });

        panel.add(versionGrid.wrap(), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);

        JButton refresh = RoundedButton.secondary("Refresh");
        refresh.addActionListener(e -> refreshVersions());

        JButton installedOnly = RoundedButton.secondary("Installed");
        installedOnly.addActionListener(e -> showInstalledOnly());

        buttons.add(refresh);
        buttons.add(installedOnly);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void updateVersionTabStyles() {
        if (supportedTabBtn == null || unsupportedTabBtn == null) return;
        if (viewingSupportedVersions) {
            supportedTabBtn.setAccent(true);
            unsupportedTabBtn.setAccent(false);
            if (versionSubLabel != null) {
                versionSubLabel.setText("Supported versions (1.21 family) with Vibe Client mods.");
            }
        } else {
            supportedTabBtn.setAccent(false);
            unsupportedTabBtn.setAccent(true);
            if (versionSubLabel != null) {
                versionSubLabel.setText("Unsupported versions run in pure vanilla mode without Vibe Client mods.");
            }
        }
    }

    private void updateVersionGridDisplay() {
        if (viewingSupportedVersions) {
            versionGrid.setCards(supportedVersionCards);
        } else {
            versionGrid.setCards(unsupportedVersionCards);
        }
    }

    /**
     * The launch-settings column.
     *
     * Returns a scroll pane, not the panel itself: the form is taller than a
     * small window and GridBagLayout clips the last rows rather than shrinking
     * them, which cut the action buttons in half.
     */
    private javax.swing.JComponent buildSettingsPanel() {
        JPanel panel = Theme.panel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(Theme.padding(14, 16, 14, 16));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 0, 5, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 0;

        JLabel caption = new JLabel("Launch settings");
        caption.setFont(Theme.BODY_BOLD);
        caption.setForeground(Theme.TEXT);
        c.gridwidth = 2;
        panel.add(caption, c);
        c.gridwidth = 1;

        addRow(panel, c, "Memory (MB)", memorySpinner, 1);
        addRow(panel, c, "Window width", widthSpinner, 2);
        addRow(panel, c, "Window height", heightSpinner, 3);
        addRow(panel, c, "Extra JVM args", jvmArgsField, 4);
        addRow(panel, c, "Server (optional)", serverField, 5);
        addRow(panel, c, "Server port", portField, 6);
        addRow(panel, c, "Update repo", updateRepoField, 7);

        c.gridx = 0;
        c.gridy = 8;
        c.gridwidth = 2;
        Theme.styleCheckBox(fabricBox);
        panel.add(fabricBox, c);

        c.gridy = 9;
        Theme.styleCheckBox(closeBox);
        panel.add(closeBox, c);

        c.gridy = 10;
        Theme.styleCheckBox(perfBox);
        perfBox.setToolTipText("Installs Sodium, Lithium and the rest of the "
            + "client-side optimisation pack from Modrinth");
        panel.add(perfBox, c);

        // Actions.
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);

        JButton selfHeal = RoundedButton.secondary("Check & repair files");
        selfHeal.addActionListener(e -> runSelfHeal());

        JButton openGame = RoundedButton.secondary("Game folder");
        openGame.addActionListener(e -> openFolder(gameDir()));

        JButton openMods = RoundedButton.secondary("Mods folder");
        openMods.addActionListener(e -> openFolder(gameDir().resolve("mods")));

        JButton checkUpdates = RoundedButton.secondary("Check updates");
        checkUpdates.addActionListener(e -> checkForUpdates(false));

        actions.add(selfHeal);
        actions.add(openGame);
        actions.add(openMods);
        actions.add(checkUpdates);

        // Extra vertical room above the buttons: primary buttons draw their
        // neon rim OUTSIDE the plate, so the default 5px inset is not enough
        // and the rim visually collides with the checkbox above it.
        c.gridy = 11;
        c.insets = new Insets(12, 0, 8, 8);
        panel.add(actions, c);
        c.insets = new Insets(5, 0, 5, 8);

        // Push everything up.
        c.gridy = 12;
        c.weighty = 1;
        panel.add(new JLabel(""), c);

        // Wrapped in a scroll pane instead of being added bare. The settings
        // column is taller than a small window, and GridBagLayout silently
        // clips the last rows — which is exactly how the action buttons ended
        // up cut in half at the bottom. A scroll pane makes every row reachable
        // at any window size.
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        // Vertical only: horizontal scrolling in a settings form is a smell.
        scroll.setHorizontalScrollBarPolicy(
            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setOpaque(false);
        return scroll;
    }

    private void addRow(JPanel panel, GridBagConstraints c, String label,
                        java.awt.Component field, int row) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        JLabel text = new JLabel(label);
        text.setForeground(Theme.TEXT_DIM);
        text.setFont(Theme.BODY);
        panel.add(text, c);

        c.gridx = 1;
        c.weightx = 1;
        if (field instanceof JTextField) {
            JTextField tf = (JTextField) field;
            tf.setBackground(Theme.PANEL_LIGHT);
            tf.setForeground(Theme.TEXT);
            tf.setCaretColor(Theme.TEXT);
            tf.setBorder(Theme.padding(5, 6, 5, 6));
            tf.setFont(Theme.BODY);
        } else if (field instanceof javax.swing.JComponent component) {
            // Spinners: style the field inside as well, or a bright white box
            // shows through the dark layout.
            Theme.styleField(component);
            if (field instanceof javax.swing.JSpinner spinner) {
                JComponent editor = spinner.getEditor();
                if (editor instanceof javax.swing.JSpinner.DefaultEditor def) {
                    Theme.styleField(def.getTextField());
                }
            }
        }
        panel.add(field, c);
    }

    /** The log card: the launcher's output and the game's stdout. */
    private JPanel buildLogPanel() {
        JPanel panel = Theme.panel();
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(Theme.padding(12, 12, 12, 12));

        JLabel caption = new JLabel("Log");
        caption.setFont(Theme.BODY_BOLD);
        caption.setForeground(Theme.TEXT);
        panel.add(caption, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setBackground(new Color(0x0E, 0x10, 0x13));
        logArea.setForeground(new Color(0xC8, 0xCC, 0xD4));
        logArea.setFont(Theme.MONO);
        logArea.setBorder(Theme.padding(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * The mods card: what is actually installed in `<game>/mods`.
     *
     * The folder does not exist on a fresh install and is created by the
     * launcher on the first launch, so the card has to cope with it being
     * missing rather than treating that as an error.
     */
    private JPanel buildModsPanel() {
        JPanel panel = Theme.panel();
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(Theme.padding(12, 12, 12, 12));

        JLabel caption = new JLabel("Mods");
        caption.setFont(Theme.BODY_BOLD);
        caption.setForeground(Theme.TEXT);
        panel.add(caption, BorderLayout.NORTH);

        modsSummary = new JLabel(" ");
        modsSummary.setFont(Theme.BODY);
        modsSummary.setForeground(Theme.TEXT_DIM);

        modsList = new JPanel();
        modsList.setOpaque(false);
        modsList.setLayout(new BoxLayout(modsList, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(modsList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(modsSummary, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);

        JButton refresh = RoundedButton.secondary("Refresh");
        refresh.addActionListener(e -> refreshMods());

        JButton openMods = RoundedButton.secondary("Open mods folder");
        openMods.addActionListener(e -> openFolder(gameDir().resolve("mods")));

        buttons.add(refresh);
        buttons.add(openMods);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshMods();
        return panel;
    }

    /**
     * The account card: who is signed in, and a way to change that.
     */
    private JPanel buildAccountPanel() {
        JPanel panel = Theme.panel();
        panel.setLayout(new BorderLayout(0, 8));
        panel.setBorder(Theme.padding(12, 12, 12, 12));

        JLabel caption = new JLabel("Account");
        caption.setFont(Theme.BODY_BOLD);
        caption.setForeground(Theme.TEXT);
        panel.add(caption, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);
        card.setBorder(Theme.padding(18, 20, 18, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 4, 0);
        JLabel nameCaption = new JLabel("SIGNED IN AS");
        nameCaption.setFont(Theme.SMALL);
        nameCaption.setForeground(Theme.ACCENT_2);
        card.add(nameCaption, c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 4, 0);
        accountNameLabel.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,
            java.awt.Font.BOLD, 20));
        accountNameLabel.setForeground(Theme.TEXT);
        card.add(accountNameLabel, c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 18, 0);
        accountStatusLabel.setFont(Theme.BODY);
        accountStatusLabel.setForeground(Theme.TEXT_DIM);
        card.add(accountStatusLabel, c);

        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        JButton switchAccount = RoundedButton.primary("Switch account");
        switchAccount.addActionListener(e -> openAccountDialog());
        card.add(switchAccount, c);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(card, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    /**
     * The cosmetics card.
     *
     * The store itself is a self-contained component; this window only owns
     * the equipped id (it is part of the launcher config, so it survives
     * restarts) and the sync with the home screen.
     */
    private JComponent buildStorePanel() {
        // A one-element holder: the listener needs to refresh the store's
        // own "equipped" state, but the store does not exist until the
        // constructor returns. Self-reference through a final array is the
        // standard way around that in a lambda.
        final StoreScreen[] holder = new StoreScreen[1];
        StoreScreen store = new StoreScreen(id -> {
            config.equippedCosmetic = id == null ? "" : id;
            saveConfig();
            homeScreen.setCosmetic(id);
            if (holder[0] != null) holder[0].setEquipped(id);
        });
        holder[0] = store;
        store.setEquipped(config.equippedCosmetic);
        homeScreen.setCosmetic(config.equippedCosmetic);
        return store;
    }

    /**
     * The status bar.
     *
     * Two deliberate decisions:
     *
     *  1. **It does not duplicate the hero's PLAY button.** The home screen is
     *     built around one large call to action; a second identical button
     *     below it is the classic "which one do I press" defect. The footer
     *     button is shown only on the other cards, which have no play button of
     *     their own — there it is the only way to launch.
     *
     *  2. **The progress bar is slim and lives next to the status text.** The
     *     old footer reserved 280x16 permanently, so an idle launcher showed a
     *     wide empty track that read as a stuck 0% download.
     */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.prepare(g);
                int w = getWidth();
                int h = getHeight();
                // Translucent bar so the space scene still shows through, with
                // a hairline rule on top to separate it from the content.
                g2.setColor(new Color(8, 9, 16, 214));
                g2.fillRect(0, 0, w, h);
                g2.setColor(Theme.BORDER);
                g2.drawLine(0, 0, w, 0);
                g2.dispose();
            }
        };
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(11, 18, 11, 18));

        statusLabel.setForeground(Theme.TEXT_DIM);
        statusLabel.setFont(Theme.BODY);

        progress.setPreferredSize(new Dimension(150, 6));
        progress.setVisible(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(statusLabel);
        left.add(progress);

        playButton.setPreferredSize(new Dimension(150, 40));
        playButton.addActionListener(e -> startGame());

        // Update strip: hidden until a check actually finds something, so a
        // stable install shows nothing at all.
        updateLabel.setForeground(Theme.ACCENT);
        updateLabel.setFont(Theme.BODY_BOLD);
        updateButton = RoundedButton.secondary("Update now");
        updateButton.setVisible(false);
        updateButton.addActionListener(e -> applyUpdate());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(updateLabel);
        right.add(updateButton);
        right.add(playButton);

        footer.add(left, BorderLayout.CENTER);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    // =======================================================================
    // Auto-update
    // =======================================================================

    /**
     * Checks GitHub for a newer client build, off the UI thread.
     *
     * Runs at startup and from the footer button. Silent when the updater is
     * unconfigured: a repository that does not exist would otherwise error on
     * every launch, which trains people to ignore the message entirely.
     */
    private void checkForUpdates(boolean quiet) {
        ModUpdater.setRepository(config.updateRepository);
        if (!ModUpdater.isConfigured()) {
            if (!quiet) append("[updates] set a GitHub repository first, e.g. you/vibeclient");
            return;
        }
        updateButton.setEnabled(false);
        if (!quiet) setStatus("Checking for updates...", true);

        Thread worker = new Thread(() -> {
            ModUpdater.Result result = ModUpdater.check(gameDir());
            SwingUtilities.invokeLater(() -> {
                updateButton.setEnabled(true);
                setStatus("Ready", false);
                lastUpdate = result;

                if (!result.checked) {
                    append("[updates] " + result.message);
                    return;
                }
                if (result.available) {
                    updateLabel.setText("Update " + result.latestTag + " available");
                    updateButton.setVisible(true);
                    append("[updates] " + result.message);
                } else {
                    updateLabel.setText("");
                    updateButton.setVisible(false);
                    if (!quiet) append("[updates] " + result.message);
                }
            });
        }, "vibe-update-check");
        worker.setDaemon(true);
        worker.start();
    }

    /** Downloads the pending update and tells the user to restart. */
    private void applyUpdate() {
        ModUpdater.Result result = lastUpdate;
        if (result == null || !result.available) return;

        updateButton.setEnabled(false);
        setStatus("Downloading " + result.latestTag + "...", true);

        Thread worker = new Thread(() -> {
            String installed = ModUpdater.download(gameDir(), result);
            SwingUtilities.invokeLater(() -> {
                updateButton.setEnabled(true);
                setStatus("Ready", false);
                if (installed == null) {
                    append("[updates] download failed");
                    JOptionPane.showMessageDialog(this,
                        "Could not download the update. The current build still works.",
                        "Vibe Client", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                append("[updates] installed " + installed);
                updateLabel.setText("Updated to " + result.latestTag);
                updateButton.setVisible(false);
                JOptionPane.showMessageDialog(this,
                    "Updated to " + result.latestTag + ".\nRestart the game to use it.",
                    "Vibe Client", JOptionPane.INFORMATION_MESSAGE);
            });
        }, "vibe-update-download");
        worker.setDaemon(true);
        worker.start();
    }

    // =======================================================================
    // Config <-> UI
    // =======================================================================

    private void applyConfigToUi() {
        memorySpinner.setValue(config.memoryMb);
        widthSpinner.setValue(config.windowWidth);
        heightSpinner.setValue(config.windowHeight);
        jvmArgsField.setText(config.extraJvmArgs == null ? "" : config.extraJvmArgs);
        serverField.setText(config.serverAddress == null ? "" : config.serverAddress);
        portField.setText(config.serverPort == null ? "" : config.serverPort);
        updateRepoField.setText(config.updateRepository == null ? "" : config.updateRepository);
        fabricBox.setSelected(config.useFabric);
        closeBox.setSelected(config.closeLauncherOnStart);
        perfBox.setSelected(config.performancePack);
        updateAccountLabel();
    }

    private void readConfigFromUi() {
        config.memoryMb = ((Number) memorySpinner.getValue()).intValue();
        config.windowWidth = ((Number) widthSpinner.getValue()).intValue();
        config.windowHeight = ((Number) heightSpinner.getValue()).intValue();
        config.extraJvmArgs = jvmArgsField.getText().trim();
        config.serverAddress = serverField.getText().trim();
        config.serverPort = portField.getText().trim();
        config.updateRepository = updateRepoField.getText().trim();
        config.useFabric = fabricBox.isSelected();
        config.closeLauncherOnStart = closeBox.isSelected();
        config.performancePack = perfBox.isSelected();
    }

    private void saveConfig() {
        readConfigFromUi();
        try {
            config.save(configFile);
        } catch (IOException e) {
            append("[warn] could not save settings: " + e.getMessage());
        }
    }

    private void updateAccountLabel() {
        Account account = config.activeAccount();
        String name;
        String status;
        if (account == null) {
            name = "Not signed in";
            status = "Add a Microsoft or offline account to play online";
            accountLabel.setText(name);
            accountLabel.setForeground(Theme.WARN);
        } else if (account.isOnline()) {
            name = account.username;
            status = "Microsoft account";
            accountLabel.setText(name + " (Microsoft)");
            accountLabel.setForeground(Theme.OK);
        } else {
            name = account.username;
            status = "Offline account";
            accountLabel.setText(name + " (offline)");
            accountLabel.setForeground(Theme.TEXT);
        }

        // The sidebar's own account card, the home screen and the account card
        // all show the same identity.
        sidebar.setAccount(name, status);
        homeScreen.setAccount(name, status);
        homeScreen.setVersion(config.selectedVersion);
        homeScreen.setGameFolder(config.gameDir);
        accountNameLabel.setText(name);
        accountStatusLabel.setText(status);
    }

    // =======================================================================
    // Version list
    // =======================================================================

    private void refreshVersions() {
        if (!busy.compareAndSet(false, true)) return;
        setStatus("Loading version list...", true);

        Thread worker = new Thread(() -> {
            try {
                List<VersionInstaller.VersionEntry> versions = VersionInstaller.listVersions();
                SwingUtilities.invokeLater(() -> {
                    versionIndex.clear();
                    supportedVersionCards.clear();
                    unsupportedVersionCards.clear();
                    for (VersionInstaller.VersionEntry entry : versions) {
                        versionIndex.put(entry.id, entry);
                        VersionCard card = new VersionCard(entry.id, entry.id,
                            entry.isRelease() ? "Release" : entry.type);
                        if (LauncherConfig.isSupportedFamily(entry.id)) {
                            supportedVersionCards.add(card);
                        } else {
                            unsupportedVersionCards.add(card);
                        }
                    }
                    updateVersionGridDisplay();
                    selectConfiguredVersion();
                    setStatus("Loaded " + versions.size() + " versions", false);
                    finishBusy();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendError("version list failed: " + e.getMessage());
                    setStatus("Could not load versions", false);
                    finishBusy();
                });
            }
        }, "vibe-versions");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Lists the files in `<game>/mods`.
     *
     * Runs on a worker thread: the folder is on a disk that may be a network
     * share, and a slow disk must not freeze the window. A missing folder is
     * the normal state of a fresh install, not an error.
     */
    private void refreshMods() {
        Path modsDir = gameDir().resolve("mods");
        Thread worker = new Thread(() -> {
            List<String> names = new ArrayList<>();
            String summary;
            if (!Files.isDirectory(modsDir)) {
                summary = "No mods folder yet — it is created on the first launch.";
            } else {
                try (Stream<Path> stream = Files.list(modsDir)) {
                    stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .forEach(names::add);
                    summary = names.isEmpty()
                        ? "The mods folder is empty."
                        : names.size() + " file(s) in " + modsDir;
                } catch (Exception e) {
                    summary = "Could not read the mods folder: " + e.getMessage();
                }
            }
            final String message = summary;
            SwingUtilities.invokeLater(() -> showMods(names, message));
        }, "vibe-mods");
        worker.setDaemon(true);
        worker.start();
    }

    /** Fills the mods card. Runs on the UI thread. */
    private void showMods(List<String> names, String summary) {
        modsSummary.setText(summary);
        modsList.removeAll();
        for (String name : names) {
            JLabel row = new JLabel(name);
            row.setFont(Theme.MONO);
            row.setForeground(Theme.TEXT);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setBorder(Theme.padding(3, 0, 3, 0));
            modsList.add(row);
        }
        modsList.revalidate();
        modsList.repaint();
    }

    private void selectConfiguredVersion() {
        versionGrid.selectById(config.selectedVersion);
    }

    private void showInstalledOnly() {
        Path versionsDir = gameDir().resolve("versions");
        if (!Files.isDirectory(versionsDir)) {
            setStatus("No versions installed yet", false);
            return;
        }
        try (Stream<Path> stream = Files.list(versionsDir)) {
            java.util.List<VersionCard> cards = new java.util.ArrayList<>();
            stream.filter(Files::isDirectory)
                .map(path -> path.getFileName().toString())
                .sorted()
                .forEach(id -> cards.add(new VersionCard(id, id, "Installed")));
            versionGrid.setCards(cards);
            setStatus("Installed versions: " + cards.size(), false);
        } catch (Exception e) {
            appendError("could not list versions: " + e.getMessage());
        }
    }

    // =======================================================================
    // Accounts
    // =======================================================================

    private void openAccountDialog() {
        AccountDialog dialog = new AccountDialog(this, config, this::saveConfig, this::append);
        dialog.setVisible(true);
        updateAccountLabel();
    }

    // =======================================================================
    // Launch
    // =======================================================================

    /**
     * Warns (in the log and the status bar) when the picked version is not the
     * one the client is built for.
     *
     * This is not cosmetic. The mod declares `~1.21.1`, so on 1.21.11 Fabric
     * does not load it at all and the game starts fully vanilla. Without this
     * warning the user sees "the client does nothing" and has no way to know
     * the cause is a version dropdown.
     */
    private void warnIfUnsupported(String versionId) {
        if (LauncherConfig.isSupportedFamily(versionId)) {
            setStatus("Ready", false);
            return;
        }
        String message = "Vibe Client supports the 1.21 family — on " + versionId
            + " the game starts, but without the client.";
        append("[warn] " + message);
        setStatus(message, false);
    }

    private void startGame() {
        if (runningGame != null && runningGame.isAlive()) {
            setStatus("The game is already running", false);
            return;
        }

        // Soft gate: outside the 1.21 family the mod will not load. The game
        // still starts (vanilla), but the user must confirm they want that —
        // an unexpected vanilla game reads as "the client is broken".
        if (!LauncherConfig.isSupportedFamily(config.selectedVersion)) {
            setStatus("Outside the supported 1.21 family", false);
            int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Vibe Client supports the Minecraft 1.21 family (1.21.x).\n\n"
                    + "You have " + config.selectedVersion + " selected. The game will"
                    + " start, but as plain vanilla — the client mod will not load.\n\n"
                    + "Start anyway?",
                "Vibe Client", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
            if (choice != javax.swing.JOptionPane.YES_OPTION) {
                setStatus("Cancelled — pick a 1.21.x version", false);
                return;
            }
            append("[warn] starting " + config.selectedVersion + " without the client mod");
        }

        if (!busy.compareAndSet(false, true)) return;

        saveConfig();
        playButton.setEnabled(false);
        homeScreen.setPlayEnabled(false);
        setStatus("Preparing...", true);

        Account account = config.activeAccount();
        if (account == null) {
            append("No account selected, using offline player '" + config.playerName + "'.");
            account = Account.offline(config.playerName);
            config.putAccount(account);
            saveConfig();
            updateAccountLabel();
        }

        final Account launchAccount = account;
        Thread worker = new Thread(() -> {
            try {
                String launchId = prepareVersionChain();
                if (config.useFabric && launchId.startsWith("fabric-loader-")) {
                    // Purge first. Mods left over from another Minecraft version
                    // make Fabric refuse to start the game outright, and the
                    // "Incompatible mods found" screen never says which file to
                    // delete. Only launcher-managed mods are touched.
                    purgeIncompatibleMods();

                    installModDependencies(config.fabricLoaderVersion);
                    if (config.performancePack) {
                        installPerformancePack(config.fabricLoaderVersion);
                    }
                }
                installBundledMod();

                GameLauncher.Request request = new GameLauncher.Request();
                request.gameDir = gameDir();
                request.appDir = appDir;
                request.versionId = launchId;
                request.versionJsonUrl = versionIndex.containsKey(launchId)
                    ? versionIndex.get(launchId).url : null;
                request.account = launchAccount;
                request.width = config.windowWidth;
                request.height = config.windowHeight;
                request.extraJvmArgs = buildJvmArgs();
                request.serverAddress = config.serverAddress;
                request.serverPort = config.serverPort;

                GameLauncher.Listener listener = new GameLauncher.Listener() {
                    @Override
                    public void status(String text) {
                        SwingUtilities.invokeLater(() -> setStatus(text, true));
                    }

                    @Override
                    public void progress(long done, long total) {
                        SwingUtilities.invokeLater(() -> setProgress(done, total));
                    }
                };

                Process process = GameLauncher.launch(request, listener);
                runningGame = process;
                SwingUtilities.invokeLater(() -> {
                    setStatus("Game started", false);
                    playButton.setEnabled(true);
                    homeScreen.setPlayEnabled(true);
                    finishBusy();
                    if (config.closeLauncherOnStart) {
                        dispose();
                    }
                });
                streamOutput(process);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendError("launch failed: " + e.getMessage());
                    setStatus("Launch failed", false);
                    playButton.setEnabled(true);
                    homeScreen.setPlayEnabled(true);
                    finishBusy();
                    JOptionPane.showMessageDialog(this,
                        "Could not start the game:\n" + e.getMessage(),
                        "Launch failed", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "vibe-launch");
        worker.setDaemon(true);
        worker.start();
    }

    /** Installs the vanilla version, and the Fabric profile when enabled. */
    private String prepareVersionChain() throws IOException {
        String vanillaId = config.selectedVersion;
        VersionInstaller.VersionEntry entry = versionIndex.get(vanillaId);
        if (entry == null && !Files.isRegularFile(
            VersionInstaller.versionJsonFile(gameDir(), vanillaId))) {
            throw new IOException("unknown version: " + vanillaId);
        }

        VersionInstaller.Listener listener = new VersionInstaller.Listener() {
            @Override
            public void status(String text) {
                SwingUtilities.invokeLater(() -> setStatus(text, true));
            }

            @Override
            public void progress(long done, long total) {
                SwingUtilities.invokeLater(() -> setProgress(done, total));
            }
        };

        VersionInstaller.Resolved vanilla = VersionInstaller.resolve(
            gameDir(), vanillaId, entry != null ? entry.url : null, listener);
        VersionInstaller.install(gameDir(), vanilla, listener);

        if (!config.useFabric) return vanillaId;

        String loader = config.fabricLoaderVersion;
        if (loader == null || loader.isBlank()) {
            List<FabricInstaller.LoaderVersion> loaders = FabricInstaller.listLoaders(vanillaId);
            loader = FabricInstaller.pickLoader(loaders);
            if (loader == null) {
                throw new IOException("Fabric has no loader for " + vanillaId);
            }
            config.fabricLoaderVersion = loader;
        }

        return FabricInstaller.install(gameDir(), vanillaId, loader, listener);
    }

    /** Installs Fabric mod dependencies the bundled client cannot start without. */
    private void installModDependencies(String loaderVersion) {        try {
            ModDependencies.installRequired(gameDir(), config.selectedVersion, loaderVersion,
                new VersionInstaller.Listener() {
                    @Override
                    public void status(String text) {
                        SwingUtilities.invokeLater(() -> setStatus(text, true));
                    }

                    @Override
                    public void progress(long done, long total) {
                        SwingUtilities.invokeLater(() -> setProgress(done, total));
                    }
                });
        } catch (Exception e) {
            append("[warn] dependency install: " + e.getMessage());
        }
    }

    /**
     * Removes launcher-managed mods that do not support the selected version.
     *
     * Runs before the dependency pass so a version switch ends with a clean mod
     * folder instead of a mixture that Fabric rejects on startup.
     */
    private void purgeIncompatibleMods() {
        try {
            var removed = ModDependencies.purgeIncompatible(
                gameDir(), config.selectedVersion,
                new VersionInstaller.Listener() {
                    @Override
                    public void status(String text) {
                        SwingUtilities.invokeLater(() -> setStatus(text, true));
                    }

                    @Override
                    public void progress(long done, long total) {
                        // Nothing to show for a handful of deletions.
                    }
                });
            for (String name : removed) {
                append("Removed incompatible mod: " + name);
            }
        } catch (Exception e) {
            append("[warn] mod cleanup: " + e.getMessage());
        }
    }

    /**
     * Installs the optional performance pack.
     *
     * Silent per-mod failures by design: the pack is an optimisation, so an
     * offline machine or a project that does not support this game version must
     * never block the launch.
     */
    private void installPerformancePack(String loaderVersion) {
        try {
            List<String> installed = ModDependencies.installPerformancePack(
                gameDir(), config.selectedVersion, loaderVersion,
                new VersionInstaller.Listener() {
                    @Override
                    public void status(String text) {
                        SwingUtilities.invokeLater(() -> setStatus(text, true));
                    }

                    @Override
                    public void progress(long done, long total) {
                        // Many small downloads; a progress bar would flicker.
                    }
                });
            if (!installed.isEmpty()) {
                append("FPS Boost installed: " + String.join(", ", installed));
            }
        } catch (Exception e) {
            append("[warn] performance pack: " + e.getMessage());
        }
    }

    /**
     * Installs the client mod into `<game>/mods`.
     *
     * Two rules, both learned from real breakage:
     *
     *  1. Stale copies are removed. Fabric refuses to start when it finds two
     *     jars with the same mod id, so shipping 0.2 without deleting 0.1
     *     turned an update into a crash on launch. The bundled jar always wins:
     *     exactly one `vibeclient-*.jar` survives.
     *  2. An identical jar is not copied again вЂ” the launcher runs this on
     *     every start and the file is compared by size.
     */
    private void installBundledMod() {
        try {
            Path mods = gameDir().resolve("mods");
            Files.createDirectories(mods);
            if (!Files.isDirectory(appDir)) return;

            List<Path> sources = new ArrayList<>();
            try (Stream<Path> stream = Files.list(appDir)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> p.getFileName().toString().startsWith("vibeclient-"))
                    .filter(p -> !p.getFileName().toString().contains("-sources"))
                    .forEach(sources::add);
            }
            if (sources.isEmpty()) return;

            // Names we are about to ship вЂ” anything else matching the pattern
            // is an older version and must go.
            java.util.Set<String> shipped = new java.util.HashSet<>();
            for (Path jar : sources) shipped.add(jar.getFileName().toString());

            try (Stream<Path> existing = Files.list(mods)) {
                List<Path> stale = new ArrayList<>();
                existing.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("vibeclient-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !shipped.contains(p.getFileName().toString()))
                    .forEach(stale::add);
                for (Path old : stale) {
                    Files.deleteIfExists(old);
                    append("Removed old client build: " + old.getFileName());
                }
            }

            for (Path jar : sources) {
                Path target = mods.resolve(jar.getFileName());
                // Same size is a cheap "already there" check; the jar is small
                // and the launcher runs this on every start.
                if (Files.isRegularFile(target) && Files.size(target) == Files.size(jar)) continue;
                Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
                append("Installed mod: " + target.getFileName());
            }
        } catch (Exception e) {
            append("[warn] could not install the bundled mod: " + e.getMessage());
        }
    }

    private String buildJvmArgs() {
        StringBuilder sb = new StringBuilder();
        sb.append("-Xmx").append(config.memoryMb).append('M');
        // The equipped cosmetic crosses the process boundary as a system
        // property: the mod reads it on startup and falls back to its own
        // config when absent (game started without the launcher).
        if (config.equippedCosmetic != null && !config.equippedCosmetic.isBlank()) {
            sb.append(" -Dvibeclient.cosmetic=").append(config.equippedCosmetic);
        }
        if (config.extraJvmArgs != null && !config.extraJvmArgs.isBlank()) {
            sb.append(' ').append(config.extraJvmArgs);
        }
        return sb.toString();
    }

    /** Streams the game's stdout into the Log tab until it exits. */
    private void streamOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String text = line;
                SwingUtilities.invokeLater(() -> append(text));
            }
            int exit = process.waitFor();
            SwingUtilities.invokeLater(() -> {
                append("Game exited with code " + exit);
                setStatus(exit == 0 ? "Game closed" : "Game exited with code " + exit, false);
            });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> append("[warn] output stream ended: " + e.getMessage()));
        }
    }

    // =======================================================================
    // Self-heal
    // =======================================================================

    private void runSelfHeal() {
        if (!busy.compareAndSet(false, true)) return;
        saveConfig();
        setStatus("Checking files...", true);

        Thread worker = new Thread(() -> {
            try {
                String vanillaId = config.selectedVersion;
                VersionInstaller.VersionEntry entry = versionIndex.get(vanillaId);
                VersionInstaller.Resolved resolved = VersionInstaller.resolve(
                    gameDir(), vanillaId, entry != null ? entry.url : null, null);

                SelfHeal.Report report = SelfHeal.repair(gameDir(), resolved,
                    new VersionInstaller.Listener() {
                        @Override
                        public void status(String text) {
                            SwingUtilities.invokeLater(() -> setStatus(text, true));
                        }

                        @Override
                        public void progress(long done, long total) {
                            SwingUtilities.invokeLater(() -> setProgress(done, total));
                        }
                    });

                SwingUtilities.invokeLater(() -> {
                    append(report.summary());
                    for (String failure : report.failures) appendError(failure);
                    setStatus(report.summary(), false);
                    finishBusy();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendError("repair failed: " + e.getMessage());
                    setStatus("Repair failed", false);
                    finishBusy();
                });
            }
        }, "vibe-heal");
        worker.setDaemon(true);
        worker.start();
    }

    // =======================================================================
    // Small helpers
    // =======================================================================

    private Path gameDir() {
        return Path.of(config.gameDir);
    }

    private void openFolder(Path folder) {
        try {
            GameLauncher.openFolder(folder);
        } catch (Exception e) {
            appendError("could not open " + folder + ": " + e.getMessage());
        }
    }

    private void setStatus(String text, boolean working) {
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setForeground(working ? Theme.TEXT : Theme.TEXT_DIM);
        progress.setVisible(working);
        if (working) {
            // Most of this launcher's work is a single network round trip with
            // no countable items (list versions, check updates, resolve a
            // version). In that case there is no honest percentage to show, so
            // the bar sweeps instead of sitting at a literal 0% — which reads
            // as "stuck" and was the actual complaint about the old footer.
            progress.setIndeterminate(true);
        } else {
            progress.setIndeterminate(false);
            progress.setValue(0);
            progress.setMaximum(100);
        }
    }

    private void setProgress(long done, long total) {
        if (total <= 0) {
            progress.setIndeterminate(true);
            return;
        }
        progress.setIndeterminate(false);
        progress.setMaximum(1000);
        progress.setValue((int) Math.min(1000, done * 1000 / total));
    }

    private void finishBusy() {
        busy.set(false);
        progress.setVisible(false);
    }

    private void append(String text) {
        logArea.append(text + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void appendError(String text) {
        append("[ERROR] " + text);
    }
}

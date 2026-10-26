package com.vibe.launcher.ui;

import com.vibe.launcher.auth.Account;
import com.vibe.launcher.auth.MicrosoftAuth;
import com.vibe.launcher.config.LauncherConfig;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Account chooser: offline nicknames and Microsoft sign-in side by side.
 *
 * Offline is the default and needs no network. The Microsoft path uses the
 * device-code flow, so the user finishes the sign-in in a real browser and the
 * launcher only waits for the result — no password ever touches this code.
 */
final class AccountDialog extends JDialog {

    private final LauncherConfig config;
    private final Runnable onChanged;
    private final Consumer<String> log;

    private final DefaultListModel<String> accountModel = new DefaultListModel<>();
    private final JList<String> accountList = new JList<>(accountModel);
    private final JTextField nameField = new JTextField(16);
    private final JLabel hint = new JLabel(" ");

    private final AtomicBoolean signingIn = new AtomicBoolean(false);
    private final AtomicBoolean keepWaiting = new AtomicBoolean(true);

    AccountDialog(Frame owner, LauncherConfig config, Runnable onChanged, Consumer<String> log) {
        super(owner, "Accounts", true);
        this.config = config;
        this.onChanged = onChanged;
        this.log = log;
        buildUi();
        reloadAccounts();
    }

    private void buildUi() {
        setSize(new Dimension(560, 420));
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(Theme.BG);

        JLabel title = new JLabel("Accounts");
        title.setFont(Theme.TITLE);
        title.setForeground(Theme.TEXT);
        title.setBorder(Theme.padding(12, 16, 0, 16));
        add(title, BorderLayout.NORTH);

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setBackground(Theme.PANEL_LIGHT);
        accountList.setForeground(Theme.TEXT);
        accountList.setFont(Theme.BODY);

        JScrollPane scroll = new JScrollPane(accountList);
        scroll.setBorder(Theme.padding(6, 16, 6, 16));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(Theme.BG);
        bottom.setBorder(Theme.padding(0, 16, 12, 16));

        JPanel offlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        offlineRow.setOpaque(false);
        JLabel nickLabel = new JLabel("Nickname:");
        nickLabel.setForeground(Theme.TEXT_DIM);
        nickLabel.setFont(Theme.BODY);
        nameField.setBackground(Theme.PANEL_LIGHT);
        nameField.setForeground(Theme.TEXT);
        nameField.setCaretColor(Theme.TEXT);
        nameField.setBorder(Theme.padding(5, 6, 5, 6));
        nameField.setFont(Theme.BODY);

        JButton addOffline = Theme.button("Add offline account");
        addOffline.addActionListener(e -> addOffline());

        offlineRow.add(nickLabel);
        offlineRow.add(nameField);
        offlineRow.add(addOffline);

        JPanel microsoftRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        microsoftRow.setOpaque(false);
        JButton microsoft = Theme.primaryButton("Sign in with Microsoft");
        microsoft.addActionListener(e -> startMicrosoftLogin());
        microsoftRow.add(microsoft);

        JPanel activateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        activateRow.setOpaque(false);
        JButton activate = Theme.button("Use selected");
        activate.addActionListener(e -> activateSelected());
        JButton remove = Theme.button("Remove");
        remove.addActionListener(e -> removeSelected());
        JButton close = Theme.button("Close");
        close.addActionListener(e -> dispose());
        activateRow.add(activate);
        activateRow.add(remove);
        activateRow.add(close);

        hint.setForeground(Theme.TEXT_DIM);
        hint.setFont(Theme.SMALL);

        bottom.add(offlineRow);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(microsoftRow);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(activateRow);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(hint);

        add(bottom, BorderLayout.SOUTH);
    }

    private void reloadAccounts() {
        accountModel.clear();
        for (Account account : config.accounts) {
            String kind = account.isOnline() ? "Microsoft" : "offline";
            String marker = config.activeAccount() == account ? "\u2713 " : "   ";
            accountModel.addElement(marker + account.username + "  (" + kind + ")");
        }
        if (config.activeAccount >= 0 && config.activeAccount < accountModel.size()) {
            accountList.setSelectedIndex(config.activeAccount);
        }
    }

    private void addOffline() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            hint.setText("Enter a nickname first.");
            hint.setForeground(Theme.WARN);
            return;
        }
        Account account = Account.offline(name).validated();
        config.putAccount(account);
        persist();
        nameField.setText("");
        hint.setText("Added offline account " + account.username);
        hint.setForeground(Theme.OK);
    }

    private void activateSelected() {
        int index = accountList.getSelectedIndex();
        if (index < 0 || index >= config.accounts.size()) {
            hint.setText("Select an account first.");
            hint.setForeground(Theme.WARN);
            return;
        }
        config.activeAccount = index;
        persist();
    }

    private void removeSelected() {
        int index = accountList.getSelectedIndex();
        if (index < 0 || index >= config.accounts.size()) return;
        config.removeAccount(index);
        persist();
    }

    private void persist() {
        reloadAccounts();
        if (onChanged != null) onChanged.run();
    }

    // -- Microsoft ----------------------------------------------------------

    private void startMicrosoftLogin() {
        String clientId = config.microsoftClientId;
        if (clientId == null || clientId.isBlank()) {
            clientId = promptForClientId();
            if (clientId == null) return;
            config.microsoftClientId = clientId;
            persist();
        }
        final String finalClientId = clientId;

        if (!signingIn.compareAndSet(false, true)) return;
        keepWaiting.set(true);
        hint.setText("Requesting a sign-in code...");
        hint.setForeground(Theme.TEXT_DIM);

        Thread worker = new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode(finalClientId);
                SwingUtilities.invokeLater(() -> showDeviceCode(code));

                Account account = MicrosoftAuth.awaitLogin(finalClientId, code,
                    keepWaiting::get,
                    text -> SwingUtilities.invokeLater(() -> {
                        hint.setText(text);
                        hint.setForeground(Theme.TEXT_DIM);
                    }));

                SwingUtilities.invokeLater(() -> {
                    config.putAccount(account);
                    persist();
                    hint.setText("Signed in as " + account.username);
                    hint.setForeground(Theme.OK);
                    signingIn.set(false);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    hint.setText("Sign-in failed: " + e.getMessage());
                    hint.setForeground(Theme.ERROR);
                    if (log != null) log.accept("[warn] Microsoft sign-in: " + e.getMessage());
                    signingIn.set(false);
                });
            }
        }, "vibe-msa");
        worker.setDaemon(true);
        worker.start();
    }

    private void showDeviceCode(MicrosoftAuth.DeviceCode code) {
        hint.setText("Open " + code.verificationUri + " and enter " + code.userCode);
        hint.setForeground(Theme.ACCENT);

        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(code.verificationUri));
            }
        } catch (Exception ignored) {
            // The code is on screen; the user can open the page manually.
        }

        JOptionPane.showMessageDialog(this,
            "1. Open: " + code.verificationUri + "\n"
                + "2. Enter this code: " + code.userCode + "\n\n"
                + "The launcher finishes automatically once you approve.",
            "Microsoft sign-in", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Asks for the Azure application id and explains where to get one. */
    private String promptForClientId() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.setBackground(Theme.BG);

        JLabel explanation = new JLabel("<html>Paste an Azure application (client) ID.<br>"
            + "Register a free app at portal.azure.com &rarr; App registrations,<br>"
            + "enable \"Allow public client flows\" and add the<br>"
            + "\"XboxLive.signin\" permission. One ID works for all users.</html>");
        explanation.setForeground(Theme.TEXT);
        panel.add(explanation);

        JTextField field = new JTextField(36);
        field.setBackground(Theme.PANEL_LIGHT);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        panel.add(field);

        int result = JOptionPane.showConfirmDialog(this, panel, "Microsoft client ID",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return null;
        String value = field.getText().trim();
        return value.isEmpty() ? null : value;
    }
}

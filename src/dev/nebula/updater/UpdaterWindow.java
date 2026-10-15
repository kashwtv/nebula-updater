package dev.nebula.updater;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;

final class UpdaterWindow extends JFrame {
    private final Updater u;
    private final JLabel status = new JLabel("Ready");
    private final JTextField url = new JTextField(40);
    private final JTextField mods = new JTextField(40);
    private final JButton go = new JButton("Check & Update");

    UpdaterWindow(Updater u) {
        super("Nebula Updater " + Updater.VERSION);
        this.u = u;
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        try { u.load(); } catch (Exception e) { status.setText("Config error: " + e.getMessage()); }
        url.setText(u.url);
        mods.setText(u.modsFolder);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(mods.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Pick your mods folder");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) mods.setText(fc.getSelectedFile().getAbsolutePath());
        });
        go.addActionListener(e -> start());
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0; p.add(new JLabel("Download URL"), g);
        g.gridx = 1; g.weightx = 1; p.add(url, g);
        g.gridx = 0; g.gridy = 1; g.weightx = 0; p.add(new JLabel("Mods folder"), g);
        g.gridx = 1; g.weightx = 1; p.add(mods, g);
        g.gridx = 2; g.weightx = 0; p.add(browse, g);
        g.gridx = 1; g.gridy = 2; p.add(go, g);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 3; p.add(status, g);
        setContentPane(p);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    private void start() {
        go.setEnabled(false);
        u.url = url.getText().trim();
        u.modsFolder = Updater.expand(mods.getText().trim());
        try { u.save(); } catch (Exception e) { status.setText("Can't save updater.json: " + e.getMessage()); go.setEnabled(true); return; }
        new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                return u.run(this::publish);
            }

            @Override
            protected void process(java.util.List<String> chunks) { status.setText(chunks.get(chunks.size() - 1)); }

            @Override
            protected void done() {
                try { status.setText(get()); }
                catch (Exception e) { status.setText("Error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())); }
                go.setEnabled(true);
            }
        }.execute();
    }
}

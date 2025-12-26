package com.belifegroupe.commission_splitter.ui;

import com.belifegroupe.commission_splitter.service.CommissionSplitterService;
import com.belifegroupe.commission_splitter.service.SplitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * UI Swing simple :
 * - choix PDF
 * - bouton démarrer
 * - progression et statut
 * - ouvrir dossier de sortie / ouvrir le zip
 */
@Component
public class DesktopUi {

    private static final Logger log = LoggerFactory.getLogger(DesktopUi.class);

    private final CommissionSplitterService splitterService;

    public DesktopUi(CommissionSplitterService splitterService) {
        this.splitterService = splitterService;
    }

    /**
     * Dès que Spring est prêt, on affiche l'UI.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startUi() {
        SwingUtilities.invokeLater(this::createAndShow);
    }

    private void createAndShow() {
        JFrame frame = new JFrame("Commission Splitter");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(640, 300);
        frame.setLocationRelativeTo(null);

        JLabel selectedLabel = new JLabel("Aucun fichier sélectionné.");
        selectedLabel.setForeground(new Color(60, 60, 60));

        JButton chooseBtn = new JButton("Choisir le PDF...");
        JButton startBtn = new JButton("Démarrer");
        startBtn.setEnabled(false);

        JButton openOutBtn = new JButton("Ouvrir le dossier de sortie");
        openOutBtn.setEnabled(false);

        JButton openZipBtn = new JButton("Ouvrir le ZIP");
        openZipBtn.setEnabled(false);

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(false);

        JLabel status = new JLabel(" ");
        status.setForeground(new Color(30, 30, 30));

        final File[] chosenFile = new File[1];
        final Path[] lastZip = new Path[1];
        final Path[] lastOut = new Path[1];

        chooseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.setDialogTitle("Sélectionner le PDF mensuel");

            int res = fc.showOpenDialog(frame);
            if (res == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                chosenFile[0] = f;
                selectedLabel.setText(f.getAbsolutePath());

                // Activation si PDF
                startBtn.setEnabled(f.getName().toLowerCase().endsWith(".pdf"));
                status.setText("Prêt à démarrer.");
            }
        });

        startBtn.addActionListener(e -> {
            if (chosenFile[0] == null) return;

            // UI: désactiver boutons pendant traitement
            startBtn.setEnabled(false);
            chooseBtn.setEnabled(false);
            openOutBtn.setEnabled(false);
            openZipBtn.setEnabled(false);
            progress.setIndeterminate(true);
            status.setText("Traitement en cours... (cela peut prendre plusieurs minutes)");

            // Traitement en background via SwingWorker (recommandé pour Swing)
            SwingWorker<SplitResult, Void> worker = new SwingWorker<>() {

                @Override
                protected SplitResult doInBackground() throws Exception {
                    // Appel du service Spring (traitement lourd)
                    return splitterService.split(chosenFile[0].toPath());
                }

                @Override
                protected void done() {
                    progress.setIndeterminate(false);
                    chooseBtn.setEnabled(true);
                    startBtn.setEnabled(true);

                    try {
                        SplitResult result = get(); // peut lever ExecutionException
                        lastZip[0] = result.zipPath;
                        lastOut[0] = result.outputDir;

                        status.setText("erminé ✅ Pages: " + result.totalPages +
                                " | Agents: " + result.agentsCount +
                                " | Erreurs: " + result.invalidPages
                               );

                        openOutBtn.setEnabled(result.outputDir != null);
                        openZipBtn.setEnabled(result.zipPath != null);

                    }
                    catch (ExecutionException ee) {
                        Throwable root = ee.getCause() != null ? ee.getCause() : ee;
                        log.error("Erreur pendant le traitement (ExecutionException)", root);
                        status.setText("Erreur ❌ " + (root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName()));
                        openOutBtn.setEnabled(false);
                        openZipBtn.setEnabled(false);

                    } catch (Exception ex) {
                        log.error("Erreur pendant le traitement", ex);
                        status.setText("Erreur ❌ " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
                        openOutBtn.setEnabled(false);
                        openZipBtn.setEnabled(false);
                    }

                }
            };

            worker.execute();
        });

        openOutBtn.addActionListener(e -> {
            try {
                if (lastOut[0] != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(lastOut[0].toFile());
                }
            } catch (Exception ignored) {}
        });

        openZipBtn.addActionListener(e -> {
            try {
                if (lastZip[0] != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(lastZip[0].toFile());
                }
            } catch (Exception ignored) {}
        });

        // Layout simple
        JPanel root = new JPanel();
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        root.add(new JLabel("1) Sélectionnez le PDF mensuel"));
        root.add(Box.createVerticalStrut(8));
        root.add(selectedLabel);
        root.add(Box.createVerticalStrut(10));
        root.add(chooseBtn);

        root.add(Box.createVerticalStrut(14));
        root.add(new JLabel("2) Lancez le traitement"));
        root.add(Box.createVerticalStrut(8));
        root.add(startBtn);

        root.add(Box.createVerticalStrut(14));
        root.add(progress);
        root.add(Box.createVerticalStrut(10));
        root.add(status);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(openOutBtn);
        actions.add(openZipBtn);

        root.add(Box.createVerticalStrut(12));
        root.add(actions);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

}

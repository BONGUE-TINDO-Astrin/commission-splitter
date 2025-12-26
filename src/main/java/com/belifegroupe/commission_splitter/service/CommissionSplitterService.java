package com.belifegroupe.commission_splitter.service;

import com.belifegroupe.commission_splitter.CommissionSplitterApplication;
import com.belifegroupe.commission_splitter.config.AppProperties;
import com.belifegroupe.commission_splitter.pdf.PdfPageMetaExtractor;
import com.belifegroupe.commission_splitter.utils.ZipUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service métier : lit le PDF, regroupe les pages par agent et génère:
 * - un PDF par agent
 * - un index.csv
 * - un ZIP final (optionnel)
 */
@Service
public class CommissionSplitterService {
    private static final Logger log = LoggerFactory.getLogger(CommissionSplitterService.class);
    private final AppProperties props;

    public CommissionSplitterService(AppProperties props) {
        this.props = props;
    }

    /**
     * Lance le traitement sur un PDF choisi via l'UI.
     */
    public SplitResult split(Path inputPdf) throws Exception {

        log.info("split() inputPdf={}", inputPdf);

        if (inputPdf == null || !Files.exists(inputPdf)) {
            throw new IllegalArgumentException("Fichier PDF introuvable.");
        }

        // 1) Dossier de sortie fixe
        Path baseOut = resolveOutputDir(props.getOutput().getTargetDir());
        log.info("baseOut={}", baseOut.toAbsolutePath());


        // 2) Dossier spécifique à cette exécution (évite d'écraser)
        String baseName = stripExtension(inputPdf.getFileName().toString());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path runDir = baseOut.resolve(props.getOutput().getFolderPrefix() + "_" + baseName + "_" + timestamp);
        Path errorsDir = runDir.resolve("errors");
        Path pdfOutDir = runDir.resolve("pdf");

        Files.createDirectories(runDir);
        log.info("runDir created: {}", runDir.toAbsolutePath());
        Files.createDirectories(errorsDir);
        Files.createDirectories(pdfOutDir);

        Files.writeString(
                runDir.resolve("_RUN_STARTED.txt"),
                "Traitement lancé - " + java.time.LocalDateTime.now() + System.lineSeparator() +
                        "Input PDF = " + inputPdf.toAbsolutePath() + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        PdfPageMetaExtractor extractor = new PdfPageMetaExtractor(props);

        // pagesByAgent: codeAgent -> liste d'index de pages PDF
        Map<String, List<Integer>> pagesByAgent = new LinkedHashMap<>();
        int invalidPages = 0;

        try (PDDocument doc = PDDocument.load(inputPdf.toFile())) {
            int totalPages = doc.getNumberOfPages();
            String currentAgent = null;

            for (int i = 0; i < totalPages; i++) {

                var meta = extractor.extract(doc, i);

                //Condition juste pour debug
                if (i == 0) {
                    System.out.println("DEBUG page 1: internalPageNo=" + meta.internalPageNo + ", agentCode=" + meta.agentCode);
                }

                if (meta.internalPageNo == null) {
                    // Page suite (pas d'entête) : rattache au dernier agent connu
                    if (currentAgent == null) {
                        invalidPages++;
                        exportSinglePage(doc, i, errorsDir.resolve("page_" + (i + 1) + "_NO_CONTEXT.pdf"));
                    } else {
                        pagesByAgent.computeIfAbsent(currentAgent, k -> new ArrayList<>()).add(i);
                    }
                    continue;
                }

                if (meta.internalPageNo == 1) {
                    // Début d'un nouveau bloc agent
                    if (meta.agentCode == null || meta.agentCode.isBlank()) {
                        invalidPages++;
                        exportSinglePage(doc, i, errorsDir.resolve("page_" + (i + 1) + "_PAGE1_NO_AGENT.pdf"));
                        currentAgent = null;
                        continue;
                    }

                    currentAgent = meta.agentCode;
                    pagesByAgent.computeIfAbsent(currentAgent, k -> new ArrayList<>()).add(i);
                    continue;
                }

                // internalPageNo > 1
                // IMPORTANT: on NE change JAMAIS currentAgent ici.
                if (currentAgent == null) {
                    invalidPages++;
                    exportSinglePage(doc, i, errorsDir.resolve("page_" + (i + 1) + "_NO_CURRENT_AGENT.pdf"));
                    continue;
                }

                pagesByAgent.computeIfAbsent(currentAgent, k -> new ArrayList<>()).add(i);

            }

            // Générer PDF par agent
            for (var entry : pagesByAgent.entrySet()) {
                String agent = sanitizeFileName(entry.getKey());
                Path agentPdf = pdfOutDir.resolve(agent + ".pdf");
                writeAgentPdf(doc, entry.getValue(), agentPdf);
            }

            // Écrire index.csv
            writeIndexCsv(runDir.resolve("index.csv"), pagesByAgent, pdfOutDir);

            // ZIP final (optionnel)
            Path zipPath = null;
            if (props.getOutput().isZip()) {
                zipPath = baseOut.resolve(runDir.getFileName().toString() + ".zip");
                ZipUtils.zipFolder(runDir, zipPath);
            }

            Files.writeString(
                    runDir.resolve("_RUN_FINISHED.txt"),
                    "Traitement terminé - " + java.time.LocalDateTime.now() + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            return new SplitResult(runDir, zipPath, pagesByAgent.size(), totalPages, invalidPages);
        }
    }

    // ----------------- Helpers -----------------

    /**
     * Crée le dossier fixe de sortie si absent.
     */
    private Path resolveOutputDir(String configured) throws IOException {
        Path appHome = resolveAppHomeDir();     // ✅ re-utilisée ici
        Path base;

        if (configured == null || configured.isBlank()) {
            base = appHome.resolve("output");
        } else {
            Path p = Paths.get(configured);
            base = p.isAbsolute() ? p : appHome.resolve(p);
        }

        base = base.toAbsolutePath().normalize();
        Files.createDirectories(base);
        return base;
    }

    /**
     * Copie les pages concernées dans un nouveau PDF (sans modifier le contenu).
     */
    private void writeAgentPdf(PDDocument src, List<Integer> pages, Path out) throws Exception {
        try (PDDocument target = new PDDocument()) {
            for (Integer idx : pages) {
                // importPage copie la page "telle quelle"
                target.importPage(src.getPage(idx));
            }
            target.save(out.toFile());
        }
    }

    /**
     * Exporte une page isolée en erreur (debug/analyse).
     */
    private void exportSinglePage(PDDocument src, int pageIndex, Path out) throws Exception {
        try (PDDocument single = new PDDocument()) {
            single.importPage(src.getPage(pageIndex));
            single.save(out.toFile());
        }
    }

    /**
     * Index simple pour audit.
     */
    private void writeIndexCsv(Path out, Map<String, List<Integer>> pagesByAgent, Path pdfOutDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("code_agent,nb_pages,fichier\n");
        for (var e : pagesByAgent.entrySet()) {
            String agent = sanitizeFileName(e.getKey());
            sb.append(agent).append(",")
                    .append(e.getValue().size()).append(",")
                    .append(pdfOutDir.resolve(agent + ".pdf").getFileName())
                    .append("\n");
        }
        Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return (idx > 0) ? name.substring(0, idx) : name;
    }

    /**
     * Nettoie pour un nom de fichier Windows/Linux.
     */
    private String sanitizeFileName(String s) {
        if (s == null) return "UNKNOWN";
        String x = s.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return x.isBlank() ? "UNKNOWN" : x;
    }

    private Path resolveAppHomeDir() {
        try {
            // Donne le dossier du .jar en prod, ou la racine de classes en IDE
            ApplicationHome home = new ApplicationHome(CommissionSplitterApplication.class);
            Path dir = home.getDir().toPath();
            return dir.toAbsolutePath().normalize();
        } catch (Exception e) {
            // Fallback : dossier de lancement (le plus fiable si ton .bat fait cd /d %~dp0)
            return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
    }


}

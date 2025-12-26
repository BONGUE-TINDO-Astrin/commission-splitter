package com.belifegroupe.commission_splitter.service;


import java.nio.file.Path;

/**
 * Résultat du traitement, affiché dans l'UI.
 */
public class SplitResult {
    public final Path outputDir;
    public final Path zipPath;      // peut être null si zip=false
    public final int agentsCount;
    public final int totalPages;
    public final int invalidPages;

    public SplitResult(Path outputDir, Path zipPath, int agentsCount, int totalPages, int invalidPages) {
        this.outputDir = outputDir;
        this.zipPath = zipPath;
        this.agentsCount = agentsCount;
        this.totalPages = totalPages;
        this.invalidPages = invalidPages;
    }
}

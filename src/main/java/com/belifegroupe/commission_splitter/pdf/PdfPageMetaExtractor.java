package com.belifegroupe.commission_splitter.pdf;

import com.belifegroupe.commission_splitter.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait sur une page :
 * - internalPageNo => à partir de "PAGE X" sur la ligne 3
 * - agentCode => sur la ligne 4 (3e token depuis la droite)
 * IMPORTANT:
 * - Si internalPageNo est absent => page "suite": on NE tente PAS d'extraire le code agent.
 */
public class PdfPageMetaExtractor {

    public static class PageMeta {
        public final Integer internalPageNo; // null => page suite
        public final String agentCode;       // null si page suite ou anomalie

        public PageMeta(Integer internalPageNo, String agentCode) {
            this.internalPageNo = internalPageNo;
            this.agentCode = agentCode;
        }
    }

    private final AppProperties.Parsing parsing;
    private final Pattern pagePattern;

    public PdfPageMetaExtractor(AppProperties props) {
        this.parsing = props.getParsing();
        this.pagePattern = Pattern.compile(parsing.getPageRegex(), Pattern.CASE_INSENSITIVE);
    }

    public PageMeta extract(PDDocument doc, int pageIndex) throws IOException {

        // 1) Extraire le texte brut de la page (PDF textuel => OK)
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);

        String pageText = stripper.getText(doc);
        if (pageText == null || pageText.isBlank()) {
            // page vide => on considère "suite" (ou erreur selon ton choix)
            return new PageMeta(null, null);
        }

        // 2) Construire "headerText" = premières lignes AVEC contenu (ignore lignes vides)
        //    => c'est ici qu'on respecte la définition: "3e ligne = 3e ligne avec contenu"
        String headerText = buildHeaderText(pageText, 12); // 12 lignes avec contenu max

        // 3) Extraire le numéro interne "PAGE X" depuis l'entête uniquement
        Integer internalPageNo = parseInternalPageFromHeader(headerText);

        // 4) Si pas de PAGE => page suite (pas d'entête)
        if (internalPageNo == null) {
            return new PageMeta(null, null);
        }

        // 5) Extraire le code agent depuis l'entête uniquement
        String agentCode = parseAgentCodeFromHeader(headerText);

        return new PageMeta(internalPageNo, agentCode);
    }

    /**
     * Construit un texte d'entête en prenant les N premières lignes NON vides.
     * On évite de scanner toute la page (corps + résumé).
     */
    private String buildHeaderText(String pageText, int maxNonEmptyLines) {
        String[] rawLines = pageText.split("\\R"); // toutes formes de retours ligne
        List<String> headerLines = new ArrayList<>();

        for (String line : rawLines) {
            if (line == null) continue;

            // Normaliser NBSP + trim
            String normalized = line.replace('\u00A0', ' ').trim();

            if (!normalized.isEmpty()) {
                headerLines.add(normalized);
                if (headerLines.size() >= maxNonEmptyLines) break;
            }
        }

        // Join avec un espace: plus simple pour regex
        return String.join(" ", headerLines);
    }

    /**
     * Parse "PAGE X" sur le header uniquement.
     * IMPORTANT: on cherche bien "PAGE" + nombre, pas le numéro de page du lecteur PDF.
     * Exemple présent dans un fichier: "... 31/01/25 PAGE 1 ..."
     */
    private Integer parseInternalPageFromHeader(String headerText) {
        if (headerText == null) return null;

        // Utilise ta regex si tu veux, sinon on met une regex robuste.
        Pattern p = Pattern.compile("PAGE\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(headerText);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Extrait le code agent depuis l'entête uniquement.
     * On s'ancre sur AGENCY/AGENCE, et on capture le token juste avant.
     * Exemple: " ... A10299002 AGENCY YD01 ..."
     *
     * Important: on force au moins un chiffre dans le code pour éviter de capturer "YEAR".
     */
    private String parseAgentCodeFromHeader(String headerText) {
        if (headerText == null) return null;

        // Normaliser espaces multiples pour stabiliser
        String h = headerText.replace('\u00A0', ' ');

        // 1) Cherche "CODE + AGENCY/AGENCE"
        //    - ([A-Z0-9]*\\d+[A-Z0-9]*) => impose au moins un chiffre (évite YEAR)
        //    - \\s+ (espaces "larges" inclus)
        Pattern p = Pattern.compile("\\b([A-Z0-9]*\\d+[A-Z0-9]*)\\b\\s+(?:AGENCY|AGENCE)\\b",
                Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(h);
        if (m.find()) {
            return cleanup(m.group(1));
        }

        // 2) Si AGENCY absent (rare), fallback: NULL (page invalide)
        return null;
    }

    private String cleanup(String s) {
        if (s == null) return null;
        String x = s.replaceAll("[\\u00A0\\s]+", "");
        x = x.replaceAll("[^\\p{L}\\p{N}_\\-./]", "");
        return x.isBlank() ? null : x;
    }

}

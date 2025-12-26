package com.belifegroupe.commission_splitter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Output output = new Output();
    private Parsing parsing = new Parsing();

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Parsing getParsing() { return parsing; }
    public void setParsing(Parsing parsing) { this.parsing = parsing; }

    public static class Output {
        /** Dossier fixe de sortie. */
        private String targetDir = "./output";
        /** Générer un ZIP final. */
        private boolean zip = true;
        /** Préfixe des dossiers générés. */
        private String folderPrefix = "Commissions_SPLIT";

        public String getTargetDir() { return targetDir; }
        public void setTargetDir(String targetDir) { this.targetDir = targetDir; }

        public boolean isZip() { return zip; }
        public void setZip(boolean zip) { this.zip = zip; }

        public String getFolderPrefix() { return folderPrefix; }
        public void setFolderPrefix(String folderPrefix) { this.folderPrefix = folderPrefix; }
    }

    public static class Parsing {
//        private int pageLineIndex = 2;
//        private int agentLineIndex = 3;
//        private int agentTokenFromRight = 3;
        private String pageRegex = "PAGE\\s*(\\d+)";
//        private float yTolerance = 2.5f;
//        private float tokenGapTolerance = 3.0f;

//        public int getPageLineIndex() { return pageLineIndex; }
//        public void setPageLineIndex(int pageLineIndex) { this.pageLineIndex = pageLineIndex; }
//
//        public int getAgentLineIndex() { return agentLineIndex; }
//        public void setAgentLineIndex(int agentLineIndex) { this.agentLineIndex = agentLineIndex; }
//
//        public int getAgentTokenFromRight() { return agentTokenFromRight; }
//        public void setAgentTokenFromRight(int agentTokenFromRight) { this.agentTokenFromRight = agentTokenFromRight; }

        public String getPageRegex() { return pageRegex; }
        public void setPageRegex(String pageRegex) { this.pageRegex = pageRegex; }

//        public float getyTolerance() { return yTolerance; }
//        public void setyTolerance(float yTolerance) { this.yTolerance = yTolerance; }
//
//        public float getTokenGapTolerance() { return tokenGapTolerance; }
//        public void setTokenGapTolerance(float tokenGapTolerance) { this.tokenGapTolerance = tokenGapTolerance; }
    }

}

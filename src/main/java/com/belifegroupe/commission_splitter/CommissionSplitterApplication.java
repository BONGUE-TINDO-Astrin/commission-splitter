package com.belifegroupe.commission_splitter;

import com.belifegroupe.commission_splitter.config.AppProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CommissionSplitterApplication {

	public static void main(String[] args) {
        // IMPORTANT: headless(false) permet d'afficher une UI Swing (JFrame, JFileChooser, etc.)
        new SpringApplicationBuilder(CommissionSplitterApplication.class)
                .headless(false)
                .run(args);
    }
}

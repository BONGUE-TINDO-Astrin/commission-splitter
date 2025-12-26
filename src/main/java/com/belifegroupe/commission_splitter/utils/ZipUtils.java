package com.belifegroupe.commission_splitter.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public ZipUtils() {
    }

    public static void zipFolder(Path sourceDir, Path zipFile) throws IOException {
        Path root = sourceDir.toAbsolutePath();
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walk(root)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry entry = new ZipEntry(root.relativize(path).toString().replace("\\", "/"));
                        try {
                            zs.putNextEntry(entry);
                            try (InputStream is = Files.newInputStream(path)) {
                                is.transferTo(zs);
                            }
                            zs.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

}

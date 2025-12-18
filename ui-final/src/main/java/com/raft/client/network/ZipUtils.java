package com.raft.client.network;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utilidad para comprimir carpetas a ZIP en memoria.
 */
public class ZipUtils {
    public static byte[] zipDirectoryToBytes(File directory) throws IOException {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            throw new IOException("Directorio inválido para comprimir: " + (directory != null ? directory.getAbsolutePath() : "null"));
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            zipRecursively(directory, directory, zos);
            zos.finish();
            return baos.toByteArray();
        }
    }

    private static void zipRecursively(File root, File current, ZipOutputStream zos) throws IOException {
        File[] entries = current.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            String relativePath = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            if (f.isDirectory()) {
                // Asegurar entrada de directorio
                if (!relativePath.endsWith("/")) relativePath += "/";
                zos.putNextEntry(new ZipEntry(relativePath));
                zos.closeEntry();
                zipRecursively(root, f, zos);
            } else {
                zos.putNextEntry(new ZipEntry(relativePath));
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        zos.write(buf, 0, r);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}

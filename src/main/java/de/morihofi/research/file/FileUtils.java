package de.morihofi.research.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class FileUtils {
    public static ByteBuffer readFile(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return ByteBuffer.wrap(data);
    }

    public static long getFileSize(Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        return attrs.size();
    }
}

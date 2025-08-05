package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class StreamingIntegrationTest {

    @Test
    public void largeFileRoundtrip() throws Exception {
        Path tempDir = Files.createTempDirectory("cabtest");
        Path largeFile = tempDir.resolve("large.bin");

        // create ~5MB random file without keeping all bytes in memory
        byte[] chunk = new byte[1024];
        java.util.Random rnd = new java.util.Random(1234);
        try (FileChannel fc = FileChannel.open(largeFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < 5 * 1024; i++) {
                rnd.nextBytes(chunk);
                fc.write(ByteBuffer.wrap(chunk));
            }
        }

        long size = Files.size(largeFile);
        byte[] original = digest(largeFile);

        CabArchive archive = new CabArchive();
        InputStream in = Files.newInputStream(largeFile);
        archive.addFile("large.bin", in, size, (short) 0, (short) 0, LocalDateTime.now());

        CabGenerator generator = new CabGenerator(archive);
        Path cabPath = tempDir.resolve("test.cab");
        try (FileChannel out = FileChannel.open(cabPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            generator.writeCabinet(out);
        }

        Path extractDir = tempDir.resolve("out");
        Files.createDirectories(extractDir);
        try (FileChannel cabIn = FileChannel.open(cabPath, StandardOpenOption.READ)) {
            CabExtractor.extractToDirectory(cabIn, extractDir);
        }

        byte[] extracted = digest(extractDir.resolve("large.bin"));
        assertArrayEquals(original, extracted);
    }

    private static byte[] digest(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return md.digest();
    }
}


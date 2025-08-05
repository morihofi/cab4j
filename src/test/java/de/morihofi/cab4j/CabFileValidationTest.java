package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CabFileValidationTest {

    @Test
    public void allowMaxFileCount() {
        CabArchive archive = new CabArchive();
        IntStream.range(0, CabArchive.MAX_FILES).forEach(i ->
                archive.addFile("f" + i, ByteBuffer.allocate(1)));

        assertThrows(IllegalArgumentException.class,
                () -> archive.addFile("overflow", ByteBuffer.allocate(1)));
    }

    @Test
    public void allowMaxFileSize() {
        CabArchive archive = new CabArchive();
        assertDoesNotThrow(() ->
                archive.addFile("max", new java.io.ByteArrayInputStream(new byte[0]),
                        CabArchive.MAX_FILE_SIZE, (short) 0, (short) 0, java.time.LocalDateTime.now()));
    }

    @Test
    public void rejectTooLargeFile() {
        CabArchive archive2 = new CabArchive();
        assertThrows(IllegalArgumentException.class,
                () -> archive2.addFile("big", new java.io.ByteArrayInputStream(new byte[0]),
                        CabArchive.MAX_FILE_SIZE + 1L, (short) 0, (short) 0, java.time.LocalDateTime.now()));
    }
}

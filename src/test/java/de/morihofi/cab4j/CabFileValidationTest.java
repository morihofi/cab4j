package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

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
        ByteBuffer maxBuf = ByteBuffer.allocate(CabArchive.MAX_FILE_SIZE);
        CabArchive archive = new CabArchive();
        assertDoesNotThrow(() -> archive.addFile("max", maxBuf));
    }

    @Test
    public void rejectTooLargeFile() {
        ByteBuffer tooBig = ByteBuffer.allocate(CabArchive.MAX_FILE_SIZE + 1);
        CabArchive archive2 = new CabArchive();
        assertThrows(IllegalArgumentException.class,
                () -> archive2.addFile("big", tooBig));
    }
}

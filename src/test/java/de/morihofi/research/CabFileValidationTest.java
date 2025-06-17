package de.morihofi.research;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class CabFileValidationTest {

    @Test
    public void allowMaxFileCount() {
        CabFile cabFile = new CabFile();
        IntStream.range(0, CabFile.MAX_FILES).forEach(i ->
                cabFile.addFile("f" + i, ByteBuffer.allocate(1)));

        assertThrows(IllegalArgumentException.class,
                () -> cabFile.addFile("overflow", ByteBuffer.allocate(1)));
    }

    @Test
    public void allowMaxFileSize() {
        ByteBuffer maxBuf = ByteBuffer.allocate(CabFile.MAX_FILE_SIZE);
        CabFile cabFile = new CabFile();
        assertDoesNotThrow(() -> cabFile.addFile("max", maxBuf));
    }

    @Test
    public void rejectTooLargeFile() {
        ByteBuffer tooBig = ByteBuffer.allocate(CabFile.MAX_FILE_SIZE + 1);
        CabFile cabFile = new CabFile();
        assertThrows(IllegalArgumentException.class,
                () -> cabFile.addFile("big", tooBig));
    }
}

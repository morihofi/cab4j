package de.morihofi.research;

import org.junit.jupiter.api.Test;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    public void fileSizeBoundary() throws Exception {
        // create a temporary sparse file for mapping
        try (RandomAccessFile raf = new RandomAccessFile("temp.dat", "rw")) {
            raf.setLength(CabFile.MAX_FILE_SIZE);
            ByteBuffer maxBuf = raf.getChannel()
                    .map(FileChannel.MapMode.READ_ONLY, 0, CabFile.MAX_FILE_SIZE);

            CabFile cabFile = new CabFile();
            assertDoesNotThrow(() -> cabFile.addFile("max", maxBuf));
        }
        Files.deleteIfExists(Paths.get("temp.dat"));

        try (RandomAccessFile raf = new RandomAccessFile("temp2.dat", "rw")) {
            raf.setLength(((long) CabFile.MAX_FILE_SIZE) + 1);
            ByteBuffer tooBig = raf.getChannel()
                    .map(FileChannel.MapMode.READ_ONLY, 0, ((long) CabFile.MAX_FILE_SIZE) + 1);

            CabFile cabFile = new CabFile();
            assertThrows(IllegalArgumentException.class,
                    () -> cabFile.addFile("big", tooBig));
        }
        Files.deleteIfExists(Paths.get("temp2.dat"));
    }
}

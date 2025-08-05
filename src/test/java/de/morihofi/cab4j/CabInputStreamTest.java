package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CabInputStreamTest {

    @Test
    public void addFromInputStream() throws Exception {
        byte[] data = "stream-data".getBytes();
        CabArchive archive = new CabArchive();
        archive.addFile("stream.txt", new ByteArrayInputStream(data), data.length, (short) 0, (short) 0, LocalDateTime.now());
        CabGenerator generator = new CabGenerator(archive);
        ByteBuffer cab = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);
        assertArrayEquals(data, TestData.toArray(extracted.get("stream.txt")));
    }

    @Test
    public void addZeroLengthFile() throws Exception {
        byte[] data = new byte[0];
        CabArchive archive = new CabArchive();
        archive.addFile("empty.bin", new ByteArrayInputStream(data), data.length, (short) 0, (short) 0, LocalDateTime.now());
        CabGenerator generator = new CabGenerator(archive);
        ByteBuffer cab = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);
        assertArrayEquals(data, TestData.toArray(extracted.get("empty.bin")));
    }
}


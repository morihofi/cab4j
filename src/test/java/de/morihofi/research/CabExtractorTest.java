package de.morihofi.research;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CabExtractorTest {

    @Test
    public void packAndExtract() throws Exception {
        byte[] hello = Files.readAllBytes(Paths.get("test/hello.c"));
        byte[] welcome = Files.readAllBytes(Paths.get("test/welcome.c"));

        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", hello);
        cabFile.addFile("welcome.c", welcome);

        ByteBuffer buf = cabFile.createCabinet();

        Map<String, byte[]> extracted = CabExtractor.extract(buf);

        assertArrayEquals(hello, extracted.get("hello.c"));
        assertArrayEquals(welcome, extracted.get("welcome.c"));
    }
}

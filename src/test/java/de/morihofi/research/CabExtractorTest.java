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
        ByteBuffer hello = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/hello.c")));
        ByteBuffer welcome = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/welcome.c")));

        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", hello);
        cabFile.addFile("welcome.c", welcome);

        ByteBuffer buf = cabFile.createCabinet();

        Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);

        byte[] helloArr = new byte[hello.remaining()];
        hello.duplicate().get(helloArr);
        byte[] welcomeArr = new byte[welcome.remaining()];
        welcome.duplicate().get(welcomeArr);

        byte[] extractedHello = new byte[extracted.get("hello.c").remaining()];
        extracted.get("hello.c").duplicate().get(extractedHello);
        byte[] extractedWelcome = new byte[extracted.get("welcome.c").remaining()];
        extracted.get("welcome.c").duplicate().get(extractedWelcome);

        assertArrayEquals(helloArr, extractedHello);
        assertArrayEquals(welcomeArr, extractedWelcome);
    }

    @Test
    public void addFileFromPath() throws Exception {
        CabFile cabFile = new CabFile();
        cabFile.addFile("hello.c", Paths.get("test/hello.c"));

        ByteBuffer buf = cabFile.createCabinet();

        Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);

        byte[] helloFile = Files.readAllBytes(Paths.get("test/hello.c"));
        byte[] extractedHello = new byte[extracted.get("hello.c").remaining()];
        extracted.get("hello.c").duplicate().get(extractedHello);

        assertArrayEquals(helloFile, extractedHello);
    }
}

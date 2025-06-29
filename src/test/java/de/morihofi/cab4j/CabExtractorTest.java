package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import de.morihofi.cab4j.structures.CfFile;
import de.morihofi.cab4j.structures.CfFolder;
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

        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello);
        archive.addFile("welcome.c", welcome);
        CabGenerator generator = new CabGenerator(archive);

        ByteBuffer buf = generator.createCabinet();

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
    public void packAndExtractCompressed() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/hello.c")));
        ByteBuffer welcome = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/welcome.c")));

        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", hello);
        archive.addFile("welcome.c", welcome);
        CabGenerator generator = new CabGenerator(archive);
        generator.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP);

        ByteBuffer buf = generator.createCabinet();

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
        CabArchive archive = new CabArchive();
        archive.addFile("hello.c", Paths.get("test/hello.c"));
        CabGenerator generator = new CabGenerator(archive);

        ByteBuffer buf = generator.createCabinet();

        Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);

        byte[] helloFile = Files.readAllBytes(Paths.get("test/hello.c"));
        byte[] extractedHello = new byte[extracted.get("hello.c").remaining()];
        extracted.get("hello.c").duplicate().get(extractedHello);

        assertArrayEquals(helloFile, extractedHello);
    }

    @Test
    public void extractWithAttributes() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/hello.c")));
        CabArchive archive2 = new CabArchive();
        archive2.addFile("hello.c", hello, (short) (CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN));
        CabGenerator generator2 = new CabGenerator(archive2);

        ByteBuffer buf = generator2.createCabinet();

        Map<String, CabExtractor.ExtractedFile> extracted = CabExtractor.extractWithAttributes(buf);

        assertTrue(extracted.containsKey("hello.c"));
        assertEquals((short) (CfFile.ATTRIB_READONLY | CfFile.ATTRIB_HIDDEN), extracted.get("hello.c").attribs);
    }

    @Test
    public void packAndExtractMultiFolder() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/hello.c")));
        ByteBuffer welcome = ByteBuffer.wrap(Files.readAllBytes(Paths.get("test/welcome.c")));

        CabArchive archive3 = new CabArchive();
        archive3.addFile("hello.c", hello, (short) 0, (short) 0);
        archive3.addFile("welcome.c", welcome, (short) 0, (short) 1);
        CabGenerator generator3 = new CabGenerator(archive3);

        ByteBuffer buf = generator3.createCabinet();

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
}

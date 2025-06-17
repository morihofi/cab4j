package de.morihofi.research;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CabDirectoryTest {

    @Test
    public void directoryPackingAndSplitting() throws Exception {
        Path temp = Files.createTempDirectory("cabdir");
        Path sub = Files.createDirectories(temp.resolve("sub"));
        Files.copy(Paths.get("test/hello.c"), temp.resolve("hello.c"));
        Files.copy(Paths.get("test/welcome.c"), sub.resolve("welcome.c"));

        CabFile cab = new CabFile();
        cab.addDirectory(temp);
        List<ByteBuffer> cabs = cab.createCabinetSet(200); // force split

        assertTrue(cabs.size() >= 1);

        Map<String, ByteBuffer> extracted = new LinkedHashMap<>();
        for (ByteBuffer b : cabs) {
            extracted.putAll(CabExtractor.extract(b));
        }

        assertArrayEquals(Files.readAllBytes(Paths.get("test/hello.c")),
                toArray(extracted.get("hello.c")));
        assertArrayEquals(Files.readAllBytes(Paths.get("test/welcome.c")),
                toArray(extracted.get("sub/welcome.c")));
    }

    private static byte[] toArray(ByteBuffer b) {
        byte[] arr = new byte[b.remaining()];
        b.duplicate().get(arr);
        return arr;
    }
}

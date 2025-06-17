package de.morihofi.cab4j;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CabDirectoryTest {

    @Test
    public void directoryPackingAndSplitting() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path temp = fs.getPath("cabdir");
            Files.createDirectory(temp);
            Path sub = Files.createDirectories(temp.resolve("sub"));
            Files.write(temp.resolve("hello.c"), TestData.HELLO_C);
            Files.write(sub.resolve("welcome.c"), TestData.WELCOME_C);

            CabFile cab = new CabFile();
            cab.addDirectory(temp);
            List<ByteBuffer> cabs = cab.createCabinetSet(200); // force split

            assertFalse(cabs.isEmpty());

            Map<String, ByteBuffer> extracted = new LinkedHashMap<>();
            for (ByteBuffer b : cabs) {
                extracted.putAll(CabExtractor.extract(b));
            }

            assertArrayEquals(TestData.HELLO_C,
                    TestData.toArray(extracted.get("hello.c")));
            assertArrayEquals(TestData.WELCOME_C,
                    TestData.toArray(extracted.get("sub/welcome.c")));
        }
    }

}

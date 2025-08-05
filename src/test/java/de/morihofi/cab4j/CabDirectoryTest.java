package de.morihofi.cab4j;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CabDirectoryTest {

    @Test
    public void directoryPackingAndSplitting() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path temp = fs.getPath("cabdir");
            Files.createDirectory(temp);
            Path sub = Files.createDirectories(temp.resolve("sub"));
            Files.write(temp.resolve("hello.c"), TestData.HELLO_C);
            Files.write(sub.resolve("welcome.c"), TestData.WELCOME_C);

            CabArchive archive = new CabArchive();
            archive.addDirectory(temp);
            CabGenerator generator = new CabGenerator(archive);
            ByteBuffer cab = generator.createCabinet();

            Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);
            assertFalse(extracted.isEmpty());

            assertArrayEquals(TestData.HELLO_C,
                    TestData.toArray(extracted.get("hello.c")));
            assertArrayEquals(TestData.WELCOME_C,
                    TestData.toArray(extracted.get("sub/welcome.c")));
        }
    }

    @Test
    public void missingDirectoryThrowsIOException() throws Exception {
        Path temp = Files.createTempDirectory("cabdir");
        Path missing = temp.resolve("missing");
        CabArchive archive = new CabArchive();
        assertThrows(IOException.class, () -> archive.addDirectory(missing));
    }

}

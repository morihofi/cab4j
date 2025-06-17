package de.morihofi.cab4j;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JimfsCabTest {

    @Test
    public void addDirectoryUsingJimfs() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path root = fs.getPath("src");
            Files.createDirectory(root);
            Path sub = Files.createDirectories(root.resolve("sub"));
            Files.write(root.resolve("hello.c"), "hi".getBytes());
            Files.write(sub.resolve("welcome.c"), "welcome".getBytes());

            CabArchive archive = new CabArchive();
            archive.addDirectory(root);
            CabGenerator generator = new CabGenerator(archive);
            ByteBuffer buf = generator.createCabinet();

            Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);
            assertArrayEquals("hi".getBytes(), TestData.toArray(extracted.get("hello.c")));
            assertArrayEquals("welcome".getBytes(), TestData.toArray(extracted.get("sub/welcome.c")));
        }
    }

    @Test
    public void extractToJimfsDirectory() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap("hello".getBytes());
        CabArchive archive2 = new CabArchive();
        archive2.addFile("hello.txt", hello);
        CabGenerator generator2 = new CabGenerator(archive2);
        ByteBuffer cabBuffer = generator2.createCabinet();

        try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path out = fs.getPath("out");
            Files.createDirectory(out);

            CabExtractor.extractToDirectory(cabBuffer, out, false);
            assertTrue(Files.exists(out.resolve("hello.txt")));
            assertArrayEquals("hello".getBytes(), Files.readAllBytes(out.resolve("hello.txt")));
        }
    }

    @Test
    public void addDirectoryWindowsFs() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
            Path root = fs.getPath("src");
            Files.createDirectory(root);
            Files.write(root.resolve("hello.txt"), "content".getBytes());

            CabArchive archive3 = new CabArchive();
            archive3.addDirectory(root);
            CabGenerator generator3 = new CabGenerator(archive3);
            ByteBuffer buf = generator3.createCabinet();

            Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);
            assertArrayEquals("content".getBytes(), TestData.toArray(extracted.get("hello.txt")));
        }
    }

}

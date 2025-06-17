package de.morihofi.cab4j;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.nio.file.FileSystem;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.file.attribute.FileTime;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CabDateTimeTest {
    @Test
    public void preserveFileTime() throws Exception {
        try (FileSystem fs = Jimfs.newFileSystem(Configuration.windows())) {
            Path temp = fs.getPath("f.txt");
            Files.write(temp, "hi".getBytes());
            LocalDateTime ts = LocalDateTime.of(2023, 1, 2, 3, 4, 6);
            Files.setLastModifiedTime(temp, FileTime.from(ts.atZone(ZoneId.systemDefault()).toInstant()));

            CabArchive archive = new CabArchive();
            archive.addFile("f.txt", temp);
            CabGenerator generator = new CabGenerator(archive);
            ByteBuffer buf = generator.createCabinet();

            CabExtractor.ExtractedFile ex = CabExtractor.extractWithAttributes(buf).get("f.txt");
            assertNotNull(ex);
            assertEquals(ts, ex.lastModified);
        }
    }
}

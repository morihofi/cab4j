package de.morihofi.research;

import de.morihofi.research.CabExtractor;
import de.morihofi.research.CabFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CabDateTimeTest {
    @Test
    public void preserveFileTime() throws Exception {
        Path temp = Files.createTempFile("cabdt", ".txt");
        Files.write(temp, "hi".getBytes());
        LocalDateTime ts = LocalDateTime.of(2023, 1, 2, 3, 4, 6);
        Files.setLastModifiedTime(temp, FileTime.from(ts.atZone(ZoneId.systemDefault()).toInstant()));

        CabFile cabFile = new CabFile();
        cabFile.addFile("f.txt", temp);
        ByteBuffer buf = cabFile.createCabinet();

        CabExtractor.ExtractedFile ex = CabExtractor.extractWithAttributes(buf).get("f.txt");
        assertNotNull(ex);
        assertEquals(ts, ex.lastModified);

        Files.deleteIfExists(temp);
    }
}

package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import de.morihofi.cab4j.structures.CfFolder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CabLargeFileTest {

    private static byte[] createLargeData(int size) {
        byte[] arr = new byte[size];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte) (i & 0xFF);
        }
        return arr;
    }

    @Test
    public void packAndExtractLargeFile() throws Exception {
        byte[] data = createLargeData(200_000);
        CabArchive archive = new CabArchive();
        archive.addFile("big.bin", ByteBuffer.wrap(data));
        CabGenerator generator = new CabGenerator(archive);

        ByteBuffer cab = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);

        assertArrayEquals(data, TestData.toArray(extracted.get("big.bin")));
    }

    @Test
    public void packAndExtractLargeFileCompressed() throws Exception {
        byte[] data = createLargeData(200_000);
        CabArchive archive = new CabArchive();
        archive.addFile("big.bin", ByteBuffer.wrap(data));
        CabGenerator generator = new CabGenerator(archive);
        generator.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_MSZIP);

        ByteBuffer cab = generator.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(cab);

        assertArrayEquals(data, TestData.toArray(extracted.get("big.bin")));
    }
}

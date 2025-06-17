package de.morihofi.cab4j;

import de.morihofi.cab4j.structures.CfFolder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CabLzxTest {
    @Test
    public void lzxCompressionRoundtrip() throws Exception {
        ByteBuffer hello = ByteBuffer.wrap(TestData.HELLO_C);
        CabFile cabFile = new CabFile();
        cabFile.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_LZX);
        cabFile.addFile("hello.c", hello);
        ByteBuffer buf = cabFile.createCabinet();
        Map<String, ByteBuffer> extracted = CabExtractor.extract(buf);
        byte[] orig = TestData.HELLO_C;
        byte[] out = new byte[extracted.get("hello.c").remaining()];
        extracted.get("hello.c").duplicate().get(out);
        assertArrayEquals(orig, out);
    }
}

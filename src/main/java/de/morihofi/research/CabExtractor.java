package de.morihofi.research;

import de.morihofi.research.util.ChecksumHelper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

public class CabExtractor {

    public static Map<String, ByteBuffer> extract(ByteBuffer cabBuffer) {
        ByteBuffer buffer = cabBuffer.duplicate();
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        byte[] signature = new byte[4];
        buffer.get(signature);
        if (signature[0] != 'M' || signature[1] != 'S' || signature[2] != 'C' || signature[3] != 'F') {
            throw new IllegalArgumentException("Invalid CAB file");
        }

        buffer.getInt(); // reserved1
        buffer.getInt(); // cbCabinet
        buffer.getInt(); // reserved2
        int coffFiles = buffer.getInt();
        buffer.getInt(); // reserved3
        buffer.get(); // version minor
        buffer.get(); // version major
        short cFolders = buffer.getShort();
        short cFiles = buffer.getShort();
        buffer.getShort(); // flags
        buffer.getShort(); // setID
        buffer.getShort(); // iCabinet

        // folders (only first is used)
        int[] folderCoffCabStart = new int[cFolders];
        short[] folderCCfData = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            folderCoffCabStart[i] = buffer.getInt();
            folderCCfData[i] = buffer.getShort();
            buffer.getShort(); // typeCompress
        }

        buffer.position(coffFiles);

        class FileEntry {
            String name;
            int size;
        }

        FileEntry[] files = new FileEntry[cFiles];
        for (int i = 0; i < cFiles; i++) {
            FileEntry fe = new FileEntry();
            fe.size = buffer.getInt();
            buffer.getInt(); // uoffFolderStart
            buffer.getShort(); // iFolder
            buffer.getShort(); // date
            buffer.getShort(); // time
            buffer.getShort(); // attribs
            StringBuilder sb = new StringBuilder();
            byte b;
            while ((b = buffer.get()) != 0) {
                sb.append((char) (b & 0xFF));
            }
            fe.name = sb.toString();
            files[i] = fe;
        }

        // Read CFDATA blocks (assume 1 per folder)
        int[] csum = new int[cFolders];
        short[] cbData = new short[cFolders];
        short[] cbUncomp = new short[cFolders];
        for (int i = 0; i < cFolders; i++) {
            csum[i] = buffer.getInt();
            cbData[i] = buffer.getShort();
            cbUncomp[i] = buffer.getShort();
        }

        Map<String, ByteBuffer> result = new LinkedHashMap<>();
        if (cFolders > 0) {
            ByteBuffer checksumBuf = ByteBuffer.allocate(Short.toUnsignedInt(cbData[0]) + 4);
            checksumBuf.order(ByteOrder.LITTLE_ENDIAN);
            checksumBuf.putShort(cbData[0]);
            checksumBuf.putShort(cbUncomp[0]);

            for (FileEntry fe : files) {
                ByteBuffer slice = buffer.slice();
                slice.limit(fe.size);
                ByteBuffer data = ByteBuffer.allocate(fe.size);
                data.put(slice);
                data.flip();
                buffer.position(buffer.position() + fe.size);
                checksumBuf.put(data.duplicate());
                result.put(fe.name, data);
            }

            checksumBuf.flip();
            int calculated = ChecksumHelper.cabChecksum(checksumBuf);
            if (calculated != csum[0]) {
                throw new IllegalStateException("CFDATA checksum mismatch");
            }
        }

        return result;
    }
}

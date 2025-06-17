package de.morihofi.research;

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
        for (int i = 0; i < cFolders; i++) {
            buffer.getInt(); // csum
            buffer.getShort(); // cbData
            buffer.getShort(); // cbUncomp
        }

        Map<String, ByteBuffer> result = new LinkedHashMap<>();
        for (FileEntry fe : files) {
            ByteBuffer slice = buffer.slice();
            slice.limit(fe.size);
            ByteBuffer data = ByteBuffer.allocate(fe.size);
            data.put(slice);
            data.flip();
            buffer.position(buffer.position() + fe.size);
            result.put(fe.name, data);
        }

        return result;
    }
}

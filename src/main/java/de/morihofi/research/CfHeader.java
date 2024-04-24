package de.morihofi.research;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfHeader {
    public static final byte[] SIGNATURE = {0x4d, 0x53, 0x43, 0x46};
    public static final int RESERVED_1 = 0;
    public static final int RESERVED_2 = 0;
    public static final int RESERVED_3 = 0;
    public static final byte VERSION_MINOR = 3;
    public static final byte VERSION_MAJOR = 1;


    public static ByteBuffer build(int totalSize, int coffFilesOffset, short numOfCFoldersEntries, short numOfCFiles) {
        ByteBuffer bb = ByteBuffer.allocate(SIGNATURE.length + 32);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.put(SIGNATURE);
        bb.putInt(RESERVED_1);
        bb.putInt(totalSize);
        bb.putInt(RESERVED_2);
        bb.putInt(coffFilesOffset);
        bb.putInt(RESERVED_3);
        bb.put(VERSION_MINOR);
        bb.put(VERSION_MAJOR);
        bb.putShort(numOfCFoldersEntries);
        bb.putShort(numOfCFiles);
        bb.putShort((short) 0); //Flags -> all zero
        bb.putShort((short) 0); //Set ID, should be random
        bb.putShort((short) 0); //iCabinet is sequential number of this cabinet in a multicabinet set. (zero is first)
        // cbCFHeader, cbCFFolder, cbCFData, abReserve,
        // szCabinetPrev, szDiskPrev, szCabinetNext, szDiskNext
        // are ignored cause flags are 0

        bb.flip(); // Reset position to start, for reading
        return bb.asReadOnlyBuffer();
    }
}

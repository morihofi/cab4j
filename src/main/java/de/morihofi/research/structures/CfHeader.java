package de.morihofi.research.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfHeader {
    private final byte[] SIGNATURE = {0x4d, 0x53, 0x43, 0x46};
    private final int RESERVED_1 = 0;
    private final int RESERVED_2 = 0;
    private final int RESERVED_3 = 0;
    private final byte VERSION_MINOR = 3;
    private final byte VERSION_MAJOR = 1;

    private int cbCabinet = 0;
    private short cFolders = 0;
    private short cFiles = 0;
    private int coffFiles = 0;

    public void setCbCabinet(int cbCabinet) {
        this.cbCabinet = cbCabinet;
    }

    public void setCoffFiles(int coffFiles) {
        this.coffFiles = coffFiles;
    }

    public void setCFolders(short cFolders) {
        this.cFolders = cFolders;
    }

    public void setCFiles(short cFiles) {
        this.cFiles = cFiles;
    }

    public ByteBuffer build() {
        ByteBuffer bb = ByteBuffer.allocate(SIGNATURE.length + 32);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.put(SIGNATURE);
        bb.putInt(RESERVED_1);
        bb.putInt(cbCabinet);
        bb.putInt(RESERVED_2);
        bb.putInt(coffFiles);
        bb.putInt(RESERVED_3);
        bb.put(VERSION_MINOR);
        bb.put(VERSION_MAJOR);
        bb.putShort(cFolders);
        bb.putShort(cFiles);
        bb.putShort((short) 0); //Flags -> all zero
        bb.putShort((short) 0); //Set ID, should be random
        bb.putShort((short) 0); //iCabinet is sequential number of this cabinet in a multicabinet set. (zero is first)
        // cbCFHeader, cbCFFolder, cbCFData, abReserve,
        // szCabinetPrev, szDiskPrev, szCabinetNext, szDiskNext
        // are ignored cause flags are 0

        bb.flip(); // Reset position to start, for reading
        return bb;
    }
}

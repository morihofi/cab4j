package de.morihofi.cab4j.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfHeader {
    private static final byte[] SIGNATURE = {0x4d, 0x53, 0x43, 0x46};
    private static final int RESERVED_1 = 0;
    private static final int RESERVED_2 = 0;
    private static final int RESERVED_3 = 0;
    private static final byte VERSION_MINOR = 3;
    private static final byte VERSION_MAJOR = 1;

    private int cbCabinet = 0;
    private short cFolders = 0;
    private short cFiles = 0;
    private int coffFiles = 0;
    private short setID = 0;
    private short iCabinet = 0;

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

    public int getCbCabinet() {
        return cbCabinet;
    }

    public short getcFolders() {
        return cFolders;
    }

    public short getcFiles() {
        return cFiles;
    }

    public int getCoffFiles() {
        return coffFiles;
    }

    public short getSetID() {
        return setID;
    }

    public void setSetID(short setID) {
        this.setID = setID;
    }

    public short getiCabinet() {
        return iCabinet;
    }

    public void setiCabinet(short iCabinet) {
        this.iCabinet = iCabinet;
    }

    public int getByteSize() {
        return SIGNATURE.length + 32;
    }

    public ByteBuffer build() {
        ByteBuffer bb = ByteBuffer.allocate(getByteSize());
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
        bb.putShort(setID); // Cabinet set ID
        bb.putShort(iCabinet); //iCabinet is sequential number of this cabinet in a multicabinet set. (zero is first)
        // cbCFHeader, cbCFFolder, cbCFData, abReserve,
        // szCabinetPrev, szDiskPrev, szCabinetNext, szDiskNext
        // are ignored cause flags are 0

        bb.flip(); // Reset position to start, for reading
        return bb;
    }
}

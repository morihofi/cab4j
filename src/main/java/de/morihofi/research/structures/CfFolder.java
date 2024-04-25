package de.morihofi.research.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfFolder {
    private int coffCabStart;
    private short cCfData;
    private short typeCompress;

    /* private int abReserve; optional */
    public enum COMPRESS_TYPE {
        TCOMP_MASK_TYPE(0x000F),
        TCOMP_TYPE_NONE(0x0000),
        TCOMP_TYPE_MSZIP(0x0001),
        TCOMP_TYPE_QUANTUM(0x0002),
        TCOMP_TYPE_LZX(0x0003);


        private final int value;

        COMPRESS_TYPE(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public int getCoffCabStart() {
        return coffCabStart;
    }

    public void setCoffCabStart(int coffCabStart) {
        this.coffCabStart = coffCabStart;
    }

    public short getcCfData() {
        return cCfData;
    }

    public void setcCfData(short cCfData) {
        this.cCfData = cCfData;
    }

    public short getTypeCompress() {
        return typeCompress;
    }

    public void setTypeCompress(short typeCompress) {
        this.typeCompress = typeCompress;
    }

    public void setTypeCompress(COMPRESS_TYPE compressType) {
        this.typeCompress = (short) compressType.getValue();
    }

    public int getByteSize(){
        return 8;
    }


    public ByteBuffer build() {
        ByteBuffer bb = ByteBuffer.allocate(getByteSize());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(coffCabStart); // 4 bytes
        bb.putShort(cCfData); // 2 bytes
        bb.putShort(typeCompress); // 2 bytes
        /* bb.putInt(abReverse) */

        bb.flip(); // Reset position to start, for reading
        return bb;

    }
}

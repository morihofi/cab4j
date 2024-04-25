package de.morihofi.research.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfData {
    private int csum = 0; //Placeholder
    private short cbData;
    private short cbUncomp;

    public int getCsum() {
        return csum;
    }

    public void setCsum(int csum) {
        this.csum = csum;
    }

    public short getCbData() {
        return cbData;
    }

    public void setCbData(short cbData) {
        this.cbData = cbData;
    }

    public short getCbUncomp() {
        return cbUncomp;
    }

    public void setCbUncomp(short cbUncomp) {
        this.cbUncomp = cbUncomp;
    }


    public ByteBuffer build() {

        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(csum); // 4 bytes
        bb.putShort(cbData); // 2 bytes
        bb.putShort(cbUncomp); // 2 bytes

        bb.flip(); // Reset position to start, for reading

        return bb;
    }
}

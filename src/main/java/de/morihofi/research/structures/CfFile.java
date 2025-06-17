package de.morihofi.research.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CfFile {
    public static final short ATTRIB_READONLY = 0x01;
    public static final short ATTRIB_HIDDEN = 0x02;
    public static final short ATTRIB_SYSTEM = 0x04;
    public static final short ATTRIB_ARCHIVE = 0x20;
    private int cbFile;
    private int uoffFolderStart = 0;
    private short iFolder = 0;
    private short date;
    private short time;
    private short attribs;
    private byte[] szName;


    public enum IFOLDER_CONTINUED {
        IFOLD_CONTINUED_FROM_PREV(0xFFFD),
        IFOLD_CONTINUED_TO_NEXT(0xFFFE),
        IFOLD_CONTINUED_PREV_AND_NEXT(0xFFFF);


        private final int value;

        IFOLDER_CONTINUED(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public int getCbFile() {
        return cbFile;
    }

    public void setCbFile(int cbFile) {
        this.cbFile = cbFile;
    }

    public int getUoffFolderStart() {
        return uoffFolderStart;
    }

    public void setUoffFolderStart(int uoffFolderStart) {
        this.uoffFolderStart = uoffFolderStart;
    }

    public short getiFolder() {
        return iFolder;
    }

    public void setiFolder(short iFolder) {
        this.iFolder = iFolder;
    }

    public void setiFolder(IFOLDER_CONTINUED ifolderContinued) {
        this.iFolder = (short) ifolderContinued.getValue();
    }

    public short getDate() {
        return date;
    }

    public void setDate(short date) {
        this.date = date;
    }

    public short getTime() {
        return time;
    }

    public void setTime(short time) {
        this.time = time;
    }

    public short getAttribs() {
        return attribs;
    }

    public void setAttribs(short attribs) {
        this.attribs = attribs;
    }

    public byte[] getSzName() {
        return szName;
    }

    public void setSzName(byte[] szName) {
        this.szName = szName;
    }

    public int getByteSize(){
        return 16 + szName.length + 1;
    }


    public ByteBuffer build() {

        ByteBuffer bb = ByteBuffer.allocate(getByteSize());
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(cbFile); // 4 bytes
        bb.putInt(uoffFolderStart); // 4 bytes
        bb.putShort(iFolder); // 2 bytes
        bb.putShort(date); // 2 bytes
        bb.putShort(time); // 2 bytes
        bb.putShort(attribs); // 2 bytes
        bb.put(szName); // variable
        bb.put((byte) 0x0); // NULL termination byte

        bb.flip(); // Reset position to start, for reading

        return bb;
    }
}

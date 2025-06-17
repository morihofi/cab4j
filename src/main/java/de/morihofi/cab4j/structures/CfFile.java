package de.morihofi.cab4j.structures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

    /**
     * Encode a {@link LocalDate} into the CAB DOS date format.
     */
    public static short encodeDate(LocalDate date) {
        int val = ((date.getYear() - 1980) << 9) | (date.getMonthValue() << 5) | date.getDayOfMonth();
        return (short) val;
    }

    /**
     * Encode a {@link LocalTime} into the CAB DOS time format.
     */
    public static short encodeTime(LocalTime time) {
        int val = (time.getHour() << 11) | (time.getMinute() << 5) | (time.getSecond() / 2);
        return (short) val;
    }

    /**
     * Decode the CAB DOS date format into a {@link LocalDate}.
     */
    public static LocalDate decodeDate(short value) {
        int v = Short.toUnsignedInt(value);
        int day = v & 0x1F;
        int month = (v >> 5) & 0x0F;
        int year = (v >> 9) + 1980;
        return LocalDate.of(year, month, day);
    }

    /**
     * Decode the CAB DOS time format into a {@link LocalTime}.
     */
    public static LocalTime decodeTime(short value) {
        int v = Short.toUnsignedInt(value);
        int second = (v & 0x1F) * 2;
        int minute = (v >> 5) & 0x3F;
        int hour = (v >> 11) & 0x1F;
        return LocalTime.of(hour, minute, second);
    }

    /**
     * Set both date and time fields using a {@link LocalDateTime}.
     */
    public void setDateTime(LocalDateTime dt) {
        this.date = encodeDate(dt.toLocalDate());
        this.time = encodeTime(dt.toLocalTime());
    }

    /**
     * Retrieve the stored date and time as a {@link LocalDateTime}.
     */
    public LocalDateTime getDateTime() {
        return LocalDateTime.of(decodeDate(date), decodeTime(time));
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

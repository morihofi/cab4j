package de.morihofi.cab4j;

import de.morihofi.cab4j.archive.CabArchive;
import de.morihofi.cab4j.generator.CabGenerator;
import de.morihofi.cab4j.structures.CfFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @deprecated use {@link CabArchive} together with {@link CabGenerator} instead.
 * This class remains for backward compatibility and simply delegates all
 * generator related methods to an internal {@link CabGenerator} instance.
 */
@Deprecated
public class CabFile extends CabArchive {

    private final CabGenerator generator = new CabGenerator(this);

    public ByteBuffer createCabinet() throws IOException {
        return generator.createCabinet();
    }

    public boolean isEnableChecksum() {
        return generator.isEnableChecksum();
    }

    public void setEnableChecksum(boolean enableChecksum) {
        generator.setEnableChecksum(enableChecksum);
    }

    public CfFolder.COMPRESS_TYPE getCompressionType() {
        return generator.getCompressionType();
    }

    public void setCompressionType(CfFolder.COMPRESS_TYPE compressionType) {
        generator.setCompressionType(compressionType);
    }

    public void resetCabinetSet() {
        generator.resetCabinetSet();
    }

    public static void main(String[] args) throws IOException {
        CabFile cabFile = new CabFile();
        cabFile.setEnableChecksum(true);
        cabFile.setCompressionType(CfFolder.COMPRESS_TYPE.TCOMP_TYPE_QUANTUM);
        cabFile.addFile("hello.c", Paths.get("test/hello.c"));
        cabFile.addFile("welcome.c", Paths.get("test/welcome.c"));

        ByteBuffer cabFileBuffer = cabFile.createCabinet();

        try (FileChannel fc = FileChannel.open(Paths.get("output.cab"), StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            fc.write(cabFileBuffer);
        }
    }
}

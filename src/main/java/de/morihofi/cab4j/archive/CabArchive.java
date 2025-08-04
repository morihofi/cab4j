package de.morihofi.cab4j.archive;

import de.morihofi.cab4j.file.FileUtils;
import de.morihofi.cab4j.structures.CfFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the contents of a cabinet archive. It merely stores file data and
 * related metadata. Use {@link de.morihofi.cab4j.generator.CabGenerator} to
 * actually create a cabinet file from an instance of this class.
 */
public class CabArchive {

    /**
     * Maximum amount of files allowed in a single cabinet file as specified in
     * the MS-CAB documentation.
     */
    public static final int MAX_FILES = 0xFFFF;

    /**
     * Maximum uncompressed size of an input file (in bytes) that can be stored
     * in a cabinet file. This value originates from the official specification
     * and represents the largest value that fits into the 32‑bit file size
     * fields.
     */
    public static final int MAX_FILE_SIZE = 0x7FFF8000;

    /**
     * Internal representation of a file within the archive.
     */
    public static class FileEntry {
        public final ByteBuffer data;
        public final short attribs;
        public final short folder;
        public final java.time.LocalDateTime lastModified;

        public FileEntry(ByteBuffer data, short attribs, short folder, java.time.LocalDateTime ts) {
            this.data = data;
            this.attribs = attribs;
            this.folder = folder;
            this.lastModified = ts;
        }
    }

    private final Map<String, FileEntry> files = new LinkedHashMap<>();

    /**
     * Add a file using the provided byte buffer.
     */
    public void addFile(String filename, ByteBuffer bytes) {
        addFile(filename, bytes, (short) 0, (short) 0, java.time.LocalDateTime.now());
    }

    /** Add a file with custom DOS attributes. */
    public void addFile(String filename, ByteBuffer bytes, short attribs) {
        addFile(filename, bytes, attribs, (short) 0, java.time.LocalDateTime.now());
    }

    /** Add a file specifying the folder index. */
    public void addFile(String filename, ByteBuffer bytes, short attribs, short folder) {
        addFile(filename, bytes, attribs, folder, java.time.LocalDateTime.now());
    }

    /**
     * Add a file with all options available.
     */
    public void addFile(String filename, ByteBuffer bytes, short attribs, short folder, java.time.LocalDateTime timestamp) {
        if (files.size() >= MAX_FILES) {
            throw new IllegalArgumentException("CAB File limit reached");
        }
        if (bytes.remaining() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Byte size for file \"" + filename + "\" is too large (" + bytes.remaining()
                            + " bytes). Max allowed size is " + MAX_FILE_SIZE + " bytes");
        }
        files.put(filename, new FileEntry(bytes, attribs, folder, timestamp));
    }

    /** Convenience method using a byte array. */
    public void addFile(String filename, byte[] bytes) {
        addFile(filename, ByteBuffer.wrap(bytes), (short) 0, (short) 0, java.time.LocalDateTime.now());
    }

    /**
     * Adds a file from the given path to the archive, preserving DOS attributes
     * and modification time when available.
     */
    public void addFile(String filename, Path path) throws IOException {
        ByteBuffer data = FileUtils.readFile(path);
        short attribs = 0;
        try {
            DosFileAttributes dos = Files.readAttributes(path, DosFileAttributes.class);
            if (dos.isReadOnly()) attribs |= CfFile.ATTRIB_READONLY;
            if (dos.isHidden()) attribs |= CfFile.ATTRIB_HIDDEN;
            if (dos.isSystem()) attribs |= CfFile.ATTRIB_SYSTEM;
            if (dos.isArchive()) attribs |= CfFile.ATTRIB_ARCHIVE;
        } catch (UnsupportedOperationException ignored) {
            // DOS attributes not supported on this platform
        }
        FileTime ft = Files.getLastModifiedTime(path);
        java.time.LocalDateTime ts = java.time.LocalDateTime.ofInstant(ft.toInstant(), java.time.ZoneId.systemDefault());
        addFile(filename, data, attribs, (short) 0, ts);
    }

    /**
     * Recursively adds all files from the given directory. The path inside the
     * cabinet mirrors the relative path to the supplied directory.
     */
    public void addDirectory(Path directory) throws IOException {
        Files.walk(directory)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    Path rel = directory.relativize(p);
                    String name = rel.toString().replace('\\', '/');
                    try {
                        addFile(name, p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /** Returns the stored file entries. */
    public Map<String, FileEntry> getFileEntries() {
        return Collections.unmodifiableMap(files);
    }
}

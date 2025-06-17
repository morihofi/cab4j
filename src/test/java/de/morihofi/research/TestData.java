package de.morihofi.research;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class TestData {
    public static final byte[] HELLO_C = (
            "#include <stdio.h>\n" +
            "\n" +
            "void main(void)\n" +
            "{\n" +
            "    printf(\"Hello, world!\\n\");\n" +
            "}\n"
    ).getBytes(StandardCharsets.UTF_8);

    public static final byte[] WELCOME_C = (
            "#include <stdio.h>\n" +
            "\n" +
            "void main(void)\n" +
            "{\n" +
            "    printf(\"Welcome!\\n\");\n" +
            "}\n"
    ).getBytes(StandardCharsets.UTF_8);

    private TestData() {}

    public static byte[] toArray(ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.duplicate().get(arr);
        return arr;
    }
}

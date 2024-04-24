package de.morihofi.research;

import java.nio.ByteBuffer;

public class HexView {
    public static void hexView(ByteBuffer byteBuffer) {
        // Number of bytes per row
        int bytesPerRow = 16;
        int position = byteBuffer.position();
        byte[] byteArray = byteBuffer.array();

        // Header
        System.out.println("Offset(h) 00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F  | Decoded Text");
        System.out.println("--------------------------------------------------------------------------------");

        // ANSI color codes
        String defaultColor = "\033[0m"; // Resets to the default console color
        String highlightColor = "\033[30;103m"; // Black text on yellow background

        // Process the byte array in rows of 16
        for (int i = 0; i < byteArray.length; i += bytesPerRow) {
            // Print offset
            System.out.format("%08X  ", i);

            // Print each byte in hex
            for (int j = 0; j < bytesPerRow; j++) {
                int byteIndex = i + j;
                if (byteIndex < byteArray.length) {
                    if (byteIndex == position) {
                        System.out.format("%s%02X%s ", highlightColor, byteArray[byteIndex], defaultColor);
                    } else {
                        System.out.format("%02X ", byteArray[byteIndex]);
                    }
                } else {
                    System.out.print("   ");
                }
            }

            System.out.print(" | ");

            // Print ASCII characters or "." for non-printable characters
            for (int j = 0; j < bytesPerRow; j++) {
                int byteIndex = i + j;
                if (byteIndex < byteArray.length) {
                    byte b = byteArray[byteIndex];
                    char ascii = (char) (b & 0xFF);
                    if (byteIndex == position) { // Start highlight on position
                        System.out.print(highlightColor);
                    }
                    if (ascii >= 32 && ascii < 127) { // Check if in ASCII range
                        System.out.print(ascii);
                    } else { //Not in ASCII range
                        System.out.print(".");
                    }
                    if (byteIndex == position) { // Stop highlight on position
                        System.out.print(defaultColor);
                    }
                }
            }

            // Move to the next line
            System.out.println();
        }
        byteBuffer.position(position); //reset cursor
    }
}

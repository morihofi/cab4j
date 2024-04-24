package de.morihofi.research;

import java.nio.ByteBuffer;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        ByteBuffer header = CfHeader.build(1, 1, (short) 1, (short) 1);

        header.flip();
        HexView.hexView(header);
    }
}
package de.morihofi.research.util;

public class ChecksumHelper {

    public static int byteArrayToInt(byte[] bytes){
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    public static byte[] calculateChecksum(byte[] data) {
        int n = data.length;
        byte[] C = new byte[4];

        for (int i = 0; i < 4; i++) {
            C[i] = (byte) calculateS(i + 1, n, data);
        }

        return C;
    }

    private static int calculateS(int b, int n, byte[] data) {
        if (b > n % 4 && b <= n) {
            return data[n - b] & 0xFF;
        } else if (b <= n % 4) {
            return data[n - b] & 0xFF;
        } else {
            return calculateS(b - 4, n, data) ^ (data[n - (b % n)] & 0xFF);
        }
    }

    public static void main(String[] args) {
        byte[] data = { /* Ihr Datenarray */ };
        byte[] checksum = calculateChecksum(data);

        System.out.println("Die berechnete Prüfsumme ist:");
        for (byte value : checksum) {
            System.out.printf("%02X ", value);
        }
    }
}

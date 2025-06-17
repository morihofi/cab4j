package de.morihofi.research.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Helper methods for checksum calculation.
 */
public class ChecksumHelper {

    /**
     * Calculates the CAB checksum for the supplied data block. The algorithm
     * implements the procedure described in section 3.1 of the MS-CAB
     * specification. The checksum is computed by XOR-ing all 32&nbsp;bit little
     * endian words of the data. Remaining bytes (1&ndash;3) are appended in
     * reverse order and padded with zeroes.
     *
     * @param data data over which the checksum should be calculated
     * @return checksum value
     */
    public static int cabChecksum(ByteBuffer data) {
        ByteBuffer buf = data.duplicate();
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int csum = 0;
        while (buf.remaining() >= 4) {
            csum ^= buf.getInt();
        }

        if (buf.remaining() > 0) {
            int word = 0;
            int shift = 0;
            for (int i = buf.limit() - 1; i >= buf.position(); i--) {
                word |= (buf.get(i) & 0xFF) << shift;
                shift += 8;
            }
            csum ^= word;
        }

        return csum;
    }
}

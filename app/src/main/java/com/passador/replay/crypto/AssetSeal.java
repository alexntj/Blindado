package com.passador.replay.crypto;

/** Remove o envelope RMP2 do Assembly-CSharp-patch.bytes. */
public final class AssetSeal {
    private AssetSeal() {}

    public static byte[] unwrap(String filename, byte[] data) throws Exception {
        if (!"Assembly-CSharp-patch.bytes".equals(filename)) return data;
        if (data == null || data.length < 0x124) {
            throw new java.io.IOException("File protegido invalido");
        }
        if (data[0] != 'R' || data[1] != 'M' || data[2] != 'P' || data[3] != '2') {
            throw new java.io.IOException("Envelope RMP2 ausente");
        }
        int start = 0x104;
        if (data[start] != 'R' || data[start+1] != 'M'
                || data[start+2] != 'P' || data[start+3] != '1') {
            throw new java.io.IOException("Corpo RMP1 ausente");
        }
        byte[] body = new byte[data.length - start];
        System.arraycopy(data, start, body, 0, body.length);
        return body;
    }
}

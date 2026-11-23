package com.passador.replay.crypto;

import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Decripta assets .bytes protegidos com RMP1 (AES-256-GCM). */
public final class AssetCipher {

    private static final String KEY_PATCH =
        "435b4b0a0f9171e295eb64c1dbaafad17052da4832512d99256e877fe00159e9";
    private static final String KEY_DEFAULT =
        "bcc3edc4aed298824c258a3b6468cd62b52386f4bcf17fb5213a358d65ca7839";

    private AssetCipher() {}

    public static byte[] decode(String filename, byte[] data) throws Exception {
        byte[] body = AssetSeal.unwrap(filename, data);
        if (body.length < 32 || body[0] != 'R' || body[1] != 'M'
                || body[2] != 'P' || body[3] != '1') {
            throw new java.io.IOException("File protegido invalido");
        }
        String hex = "Assembly-CSharp-patch.bytes".equals(filename) ? KEY_PATCH : KEY_DEFAULT;
        byte[] key = deriveKey(hex);
        byte[] iv = new byte[12];
        System.arraycopy(body, 4, iv, 0, 12);
        int ctLen = body.length - 16;
        byte[] ct = new byte[ctLen - 16];
        byte[] tag = new byte[16];
        System.arraycopy(body, 16, ct, 0, ct.length);
        System.arraycopy(body, body.length - 16, tag, 0, 16);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
        cipher.updateAAD(filename.getBytes(StandardCharsets.UTF_8));

        byte[] full = new byte[ct.length + tag.length];
        System.arraycopy(ct, 0, full, 0, ct.length);
        System.arraycopy(tag, 0, full, ct.length, tag.length);
        byte[] out = cipher.doFinal(full);
        java.util.Arrays.fill(key, (byte) 0);
        return out;
    }

    private static byte[] deriveKey(String hex) {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hb = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            int mixer = ((i * 0x1D) + 0x3B) & 0xFF;
            mixer ^= 0x5D;
            mixer &= 0xFF;
            key[i] = (byte) (hb ^ mixer);
        }
        return key;
    }
}

package com.passador.replay.installer;

import android.content.Context;
import android.os.Environment;
import com.passador.replay.crypto.AssetCipher;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ModuleInstaller {
    public static final String[] FILES = {
        "Assembly-CSharp-patch.bytes", "localConfig.json", "ffrtc_log.txt",
        "reportnew.db", "ShaderStripSettings", "auth.txt"
    };
    public interface Shell { String exec(String cmd) throws Exception; }
    public interface Progress { void onUpdate(String message); }

    private ModuleInstaller() {}

    public static String dir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/Android/data/com.dts.freefireth/files";
    }

    public static String direct(Context ctx, Progress p) throws Exception {
        File folder = new File(dir());
        if (!folder.isDirectory() && !folder.mkdirs())
            throw new IOException("Pasta do jogo inacessivel.");
        if (!folder.canWrite())
            throw new IOException("Sem permissao de escrita na pasta do jogo.");

        for (String name : FILES) {
            if (p != null) p.onUpdate("Copiando " + name + "...");
            byte[] raw = readAsset(ctx, name);
            byte[] payload = AssetCipher.decode(name, raw);

            File tmp = new File(folder, name + ".xyz.tmp");
            File dst = new File(folder, name);
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(payload);
                fos.getFD().sync();
            }
            byte[] check = Files.readAllBytes(tmp.toPath());
            if (!java.util.Arrays.equals(payload, check)) {
                tmp.delete();
                throw new IOException("Verificacao falhou: " + name);
            }
            Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return "Instalado em " + dir();
    }

    public static String viaShell(Context ctx, Shell s, Progress p) throws Exception {
        String base = dir();
        s.exec("mkdir -p '" + base + "' && echo READY");
        for (String name : FILES) {
            if (p != null) p.onUpdate("Enviando " + name + "...");
            byte[] raw = readAsset(ctx, name);
            byte[] payload = AssetCipher.decode(name, raw);
            String tmp = base + "/" + name + ".xyz.tmp";
            String dst = base + "/" + name;
            s.exec(": > '" + tmp + "' && echo READY");
            String b64 = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP);
            final int chunk = 3000;
            for (int i = 0; i < b64.length(); i += chunk) {
                String part = b64.substring(i, Math.min(i + chunk, b64.length()));
                String r = s.exec("printf '%s' '" + part
                    + "' | base64 -d >> '" + tmp + "' && echo CHUNK_OK");
                if (r == null || !r.contains("CHUNK_OK"))
                    throw new IOException("Transferencia falhou: " + name);
            }
            String hash = sha256(payload);
            String v = s.exec("h=$(sha256sum '" + tmp + "'); [ \"${h%% *}\" = '"
                + hash + "' ] && echo VERIFIED");
            if (v == null || !v.contains("VERIFIED"))
                throw new IOException("Verificacao falhou: " + name);
            String mv = s.exec("chmod 644 '" + tmp + "' && mv -f '" + tmp
                + "' '" + dst + "' && echo INSTALLED");
            if (mv == null || !mv.contains("INSTALLED"))
                throw new IOException("Instalacao falhou: " + name);
        }
        return "Instalado via shell em " + base;
    }

    private static byte[] readAsset(Context ctx, String name) throws IOException {
        try (InputStream in = ctx.getAssets().open(name);
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toByteArray();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] h = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

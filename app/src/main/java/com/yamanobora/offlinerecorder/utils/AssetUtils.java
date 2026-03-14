package com.yamanobora.offlinerecorder.utils;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetUtils {

    // assets 内のファイルを cache ディレクトリにコピーして、その絶対パスを返す
    public static String copyAssetToCache(Context context, String assetPath) throws IOException {
        AssetManager am = context.getAssets();

        // 出力先: /data/data/…/cache/models/HACHI-Summary.gguf みたいな場所
        File outFile = new File(context.getCacheDir(), assetPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            // models ディレクトリなどを作成
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IOException("Failed to create dir: " + parent.getAbsolutePath());
            }
        }

        try (InputStream is = am.open(assetPath);
             FileOutputStream os = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
        }

        return outFile.getAbsolutePath();
    }
}
package com.mnn.llm;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static void copyAssetsFolderIfNotExists(Context context, String assetFolder, String targetFolder) {
        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(assetFolder);
            if (files == null) return;

            // Create target folder if it doesn't exist
            File targetDir = new File(targetFolder);
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            for (String filename : files) {
                String assetPath = assetFolder + "/" + filename;
                InputStream in = assetManager.open(assetPath);
                File outFile = new File(targetDir, filename);
                FileOutputStream out = new FileOutputStream(outFile);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static List<String> listAllAssets(Context context, String path) {
        List<String> assetList = new ArrayList<>();
        AssetManager assetManager = context.getAssets();

        try {
            String[] files = assetManager.list(path);
            if (files != null) {
                for (String file : files) {
                    String fullPath = path.isEmpty() ? file : path + "/" + file;
                    // Recursively list subdirectories
                    if (assetManager.list(fullPath).length > 0) {
                        assetList.addAll(listAllAssets(context, fullPath));
                    } else {
                        assetList.add(fullPath);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return assetList;
    }
}

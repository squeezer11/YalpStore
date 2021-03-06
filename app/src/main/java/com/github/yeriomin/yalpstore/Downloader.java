package com.github.yeriomin.yalpstore;

import android.content.Context;
import android.os.StatFs;
import android.util.Log;

import com.github.yeriomin.playstoreapi.AndroidAppDeliveryData;
import com.github.yeriomin.playstoreapi.AppFileMetadata;
import com.github.yeriomin.yalpstore.model.App;

import java.io.File;

public class Downloader {

    private DownloadManagerInterface dm;
    private File storagePath;

    public Downloader(Context context) {
        this.dm = DownloadManagerFactory.get(context);
        storagePath = Paths.getYalpPath(context);
    }

    public void download(App app, AndroidAppDeliveryData deliveryData) {
        DownloadState state = DownloadState.get(app.getPackageName());
        state.setApp(app);
        DownloadManagerInterface.Type type = shouldDownloadDelta(app, deliveryData)
            ? DownloadManagerInterface.Type.DELTA
            : DownloadManagerInterface.Type.APK
        ;
        state.setStarted(dm.enqueue(app, deliveryData, type));
        if (deliveryData.getAdditionalFileCount() > 0) {
            checkAndStartObbDownload(state, deliveryData, true);
        }
        if (deliveryData.getAdditionalFileCount() > 1) {
            checkAndStartObbDownload(state, deliveryData, false);
        }
    }

    public boolean enoughSpace(AndroidAppDeliveryData deliveryData) {
        long bytesNeeded = deliveryData.getDownloadSize();
        if (deliveryData.getAdditionalFileCount() > 0) {
            bytesNeeded += deliveryData.getAdditionalFile(0).getSize();
        }
        if (deliveryData.getAdditionalFileCount() > 1) {
            bytesNeeded += deliveryData.getAdditionalFile(1).getSize();
        }
        try {
            StatFs stat = new StatFs(storagePath.getPath());
            return (long) stat.getBlockSize() * (long) stat.getAvailableBlocks() >= bytesNeeded;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void checkAndStartObbDownload(DownloadState state, AndroidAppDeliveryData deliveryData, boolean main) {
        App app = state.getApp();
        AppFileMetadata metadata = deliveryData.getAdditionalFile(main ? 0 : 1);
        File file = Paths.getObbPath(app.getPackageName(), metadata.getVersionCode(), main);
        prepare(file, metadata.getSize());
        if (!file.exists()) {
            state.setStarted(dm.enqueue(
                app,
                deliveryData,
                main ? DownloadManagerInterface.Type.OBB_MAIN : DownloadManagerInterface.Type.OBB_PATCH
            ));
        }
    }

    static private void prepare(File file, long expectedSize) {
        Log.i(Downloader.class.getSimpleName(), "file.exists()=" + file.exists() + " file.length()=" + file.length() + " metadata.getSize()=" + expectedSize);
        if (file.exists() && file.length() != expectedSize) {
            Log.i(Downloader.class.getSimpleName(), "Deleted old obb file: " + file.delete());
        }
        file.getParentFile().mkdirs();
    }

    static private boolean shouldDownloadDelta(App app, AndroidAppDeliveryData deliveryData) {
        File currentApk = InstalledApkCopier.getCurrentApk(app);
        return app.getVersionCode() > app.getInstalledVersionCode()
            && deliveryData.hasPatchData()
            && null != currentApk
            && currentApk.exists()
        ;
    }
}

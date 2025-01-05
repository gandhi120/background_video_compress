package com.backgroundvideocompress;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoCompressionWorker extends ListenableWorker {

    private final ExecutorService executorService;

    public VideoCompressionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        executorService = Executors.newSingleThreadExecutor();
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        SettableFuture<Result> future = SettableFuture.create();

        executorService.submit(() -> {
            try {
                String inputVideoPath = getInputData().getString("inputVideoPath");
                String outputVideoPath = getInputData().getString("outputVideoPath");

                if (inputVideoPath == null || outputVideoPath == null) {
                    future.set(Result.failure());
                    return;
                }

                boolean compressionSuccess = compressVideo(inputVideoPath, outputVideoPath);

                if (compressionSuccess) {
                    future.set(Result.success());
                } else {
                    future.set(Result.failure());
                }
            } catch (Exception e) {
                e.printStackTrace();
                future.set(Result.failure());
            }
        });

        return future;
    }

    private boolean compressVideo(String inputPath, String outputPath) {
        // FFmpeg command for compression
        String cmd = String.format(
                "-i %s -vcodec libx264 -crf 28 -preset ultrafast %s",
                inputPath, outputPath
        );

        int rc = FFmpeg.execute(cmd);

        if (rc == 0) {
            // Compression successful
            return true;
        } else {
            // Compression failed
            return false;
        }
    }
}

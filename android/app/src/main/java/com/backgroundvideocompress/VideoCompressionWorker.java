package com.backgroundvideocompress;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.Session;
import com.arthenica.ffmpegkit.ReturnCode;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.Random;
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
                String inputVideoPath = getInputData().getString("videoPath");

                String outputDir = getApplicationContext().getCacheDir().getPath();
                Random random = new Random();
                int randomInt = random.nextInt(10000);

                String outputVideoPath = "/storage/emulated/0/DCIM/.digiQC/video/compressed_video_" + randomInt + ".mp4";

                Log.d("TAG:inputVideoPath", "startWork::inputVideoPath " + inputVideoPath);
                Log.d("TAG", "startWork: " + outputVideoPath);

                if (inputVideoPath == null || outputVideoPath == null) {
                    future.set(Result.failure());
                    return;
                }

                boolean compressionSuccess = compressVideo(inputVideoPath, outputVideoPath);
                Log.d("TAG", "startWork:compressionSuccess " + compressionSuccess);

                if (compressionSuccess) {
                    future.set(Result.success());
                } else {
                    future.set(Result.failure());
                }
            } catch (Exception e) {
                Log.e("TAG", "startWork: Exception", e);
                future.set(Result.failure());
            }
        });

        return future;
    }

    private boolean compressVideo(String inputPath, String outputPath) {
        File inputFile = new File(inputPath);
        File outputFile = new File(outputPath);

        if (!inputFile.exists()) {
            Log.e("VideoCompression", "Input file does not exist: " + inputPath);
            return false;
        }

        if (outputFile.exists()) {
            boolean deleted = outputFile.delete();
            if (!deleted) {
                Log.e("VideoCompression", "Failed to delete existing output file");
                return false;
            }
        }

        String cmd = String.format(
                "-i %s -vcodec libx264 -crf 30 -preset veryfast -acodec aac -b:a 96k -movflags +faststart %s",
                inputPath, outputPath
        );

        Log.d("VideoCompression", "Executing FFmpeg command: " + cmd);

        Session session = FFmpegKit.execute(cmd);

        if (ReturnCode.isSuccess(session.getReturnCode())) {
            Log.d("VideoCompression", "Compression successful");
            return true;
        } else {
            Log.e("VideoCompression", "Compression failed with RC: " + session.getReturnCode());
            printFFmpegLogs(session);
            return false;
        }
    }

    private void printFFmpegLogs(Session session) {
        Log.d("FFmpegLog", "FFmpeg logs: " + session.getLogsAsString());
        Log.d("FFmpegStats", "Session state: " + session.getState());
    }
}

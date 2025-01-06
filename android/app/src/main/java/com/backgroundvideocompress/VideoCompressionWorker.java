package com.backgroundvideocompress;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;


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
//                String outputVideoPath = getInputData().getString("outputVideoPath");
                String outputDir = getApplicationContext().getCacheDir().getPath();
                // Define output file path
                Random random = new Random();
                int randomInt = random.nextInt(10000); // Generates a random integer between 0 and 9999

                String outputVideoPath = "/storage/emulated/0/DCIM/.digiQC/video/compressed_video_" + randomInt + ".mp4";

//                String outputVideoPath = "/storage/emulated/0/DCIM/.digiQC/video/" + "compressed_video.mp4";
                Log.d("TAG:inputVideoPath", "startWork::inputVideoPath "+inputVideoPath);

                Log.d("TAG", "startWork: "+outputVideoPath);


                if (inputVideoPath == null || outputVideoPath == null) {
                    future.set(Result.failure());
                    return;
                }

                boolean compressionSuccess = compressVideo(inputVideoPath, outputVideoPath);
                Log.d("TAG", "startWork:compressionSuccess "+compressionSuccess);

                if (compressionSuccess) {
                    future.set(Result.success());
                } else {
                    future.set(Result.failure());
                }
            } catch (Exception e) {
                Log.d("TAG", "startWork:exception "+e);

                e.printStackTrace();
                future.set(Result.failure());
            }
        });

        return future;
    }

    private boolean compressVideo(String inputPath, String outputPath) {
        // FFmpeg command for compression
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
        com.arthenica.mobileffmpeg.Config.enableLogCallback(message -> {
            Log.d("FFmpegLog", message.getText());
        });

        int rc = FFmpeg.execute(cmd);

        if (rc == 0) {
            Log.d("VideoCompression", "Compression successful");

            // Compression successful
            return true;
        } else {
            Log.e("VideoCompression", "Compression failed with RC: " + rc);
            printFFmpegLogs();

            // Compression failed
            return false;
        }
    }
    // Helper method to print FFmpeg logs
    private void printFFmpegLogs() {
        com.arthenica.mobileffmpeg.Config.enableLogCallback(message -> {
            Log.d("FFmpegLog", message.getText());
        });

        com.arthenica.mobileffmpeg.Config.enableStatisticsCallback(statistics -> {
            Log.d("FFmpegStats", "Frame: " + statistics.getVideoFrameNumber() +
                    ", Time: " + statistics.getTime() +
                    ", FPS: " + statistics.getVideoFps());
        });
    }
}

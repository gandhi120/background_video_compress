package com.backgroundvideocompress;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VideoCompressionWorker extends ListenableWorker {

    private final ExecutorService executorService;

    public VideoCompressionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        executorService = Executors.newSingleThreadExecutor(); // Use 4 threads for chunk compression
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        SettableFuture<Result> future = SettableFuture.create();

        executorService.submit(() -> {
            try {
                String inputVideoPath = getInputData().getString("videoPath");
                String basePath = "/storage/emulated/0/DCIM/.digiQC/video/";
                String randomNumber = String.valueOf(new Random().nextInt(900000) + 100000); // Random 6-digit number
                String outputVideoPath = basePath + "final_compressed_video_" + randomNumber + ".mp4";

                String chunkDir = getApplicationContext().getExternalFilesDir("chunks").getAbsolutePath();
                String compressedChunkDir = getApplicationContext().getExternalFilesDir("compressed_chunks").getAbsolutePath();
                if (inputVideoPath == null) {
                    future.set(Result.failure());
                    return;
                }
                // Step 1: Split the video into chunks
                splitVideo(inputVideoPath, chunkDir);
                // Step 2: Compress the chunks
                compressChunks(chunkDir, compressedChunkDir);
                // Step 3: Merge the compressed chunks
                mergeChunks(compressedChunkDir, outputVideoPath);

                future.set(Result.success());
            } catch (Exception e) {
                Log.e("VideoCompressionWorker", "Exception in startWork: " + e.getMessage());
                e.printStackTrace();
                future.set(Result.failure());
            } finally {
                executorService.shutdown();
            }
        });

        return future;
    }

    private void splitVideo(String inputPath, String chunkDir) {
        File chunkDirectory = new File(chunkDir);
        File inputFile = new File(inputPath);

        if (!inputFile.exists()) {
            Log.e("splitVideo", "Input file does not exist: " + inputPath);
            return;
        }
        if (!chunkDirectory.exists() && !chunkDirectory.mkdirs()) {
            Log.e("splitVideo", "Failed to create chunk directory: " + chunkDir);
            return;
        }
        if (!chunkDirectory.canWrite()) {
            Log.e("splitVideo", "Cannot write to chunk directory: " + chunkDir);
            return;
        }

        // Clean up any existing chunks in the directory
        for (File file : chunkDirectory.listFiles()) {
            Log.d("splitVideo", "Deleting existing chunk: " + file.getName());
            file.delete();
        }

        String timestamp = String.valueOf(System.currentTimeMillis());

        String chunkFileName = chunkDir + "/chunk_" + timestamp + "_%03d.mp4";
        Log.d("chunkFileName", "chunkFileName: "+chunkFileName);
        ExecutorService splitExecutor = Executors.newSingleThreadExecutor();

        splitExecutor.submit(() -> {
            String cmd = String.format(
                    "-y -i \"%s\" -c copy -map 0 -f segment -segment_time 10 \"%s\"",
                    inputPath,chunkFileName
            );
            Log.d("splitVideo", "Executing FFmpeg split command: " + cmd);

            int rc = FFmpeg.execute(cmd);
            if (rc != 0) {
                Log.e("splitVideo", "Failed to split video. RC: " + rc);
            } else {
                Log.d("splitVideo", "Video split successfully into chunks.");
            }
        });

        splitExecutor.shutdown();
        try {
            if (!splitExecutor.awaitTermination(5, TimeUnit.MINUTES)) {
                Log.e("splitVideo", "Splitting task did not finish in time.");
            }
        } catch (InterruptedException e) {
            Log.e("splitVideo", "Splitting was interrupted: " + e.getMessage());
        }
    }


    private void compressChunks(String chunkDir, String compressedChunkDir) {
        Log.d("compressChunks:START", "compressChunks: 'START");
        File compressChunk = new File(compressedChunkDir);
        if (!compressChunk.exists()) {
            boolean created = compressChunk.mkdirs();
            if (!created) {
                Log.e("compressedChunkDir", "Failed to create chunk directory.");
                return;
            }
        }
        if (compressChunk.exists()) {
            for (File file : compressChunk.listFiles()) {
                Log.d("delete", "Deleting file:compressChunk " + file.getName());
                file.delete();
            }
        }

        File[] chunks = new File(chunkDir).listFiles((dir, name) -> name.endsWith(".mp4"));
        if (chunks == null || chunks.length == 0) {
            Log.e("VideoCompressionWorker", "No valid chunks found to compress.");
            return;
        }
        if (chunks == null) return;

        ExecutorService compressExecutor = Executors.newSingleThreadExecutor();

        // Use a list to track tasks
        List<Runnable> compressionTasks = new ArrayList<>();
        for (File chunk : chunks) {
            compressExecutor.submit(() -> {
                    String compressedChunkPath = compressedChunkDir + "/compressed_" + chunk.getName();
                    String cmd = String.format(
                            "-y -i %s -vcodec libx264 -crf 28 -preset veryfast -acodec aac -b:a 96k -movflags +faststart %s",
                            chunk.getPath(), compressedChunkPath
                    );
                    Log.d("FFmpegDebug", "Chunk Input Path: " + chunk.getPath());
                    int rc = FFmpeg.execute(cmd);
                    if (rc != 0) {
                        Log.e("VideoCompressionWorker", "Failed to compress chunk: " + chunk.getName() + ". RC: " + rc);
                    } else {
                        Log.d("compressChunks", "Compressed chunk successfully: " + chunk.getName());
                    }
            });
        }

        Log.d("shutdown:task", "shutdown:task");
        compressExecutor.shutdown();
        try {
            if (!compressExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                Log.e("VideoCompressionWorker", "Compression tasks did not finish in time.");
            }
        } catch (InterruptedException e) {
            Log.e("VideoCompressionWorker", "Compression was interrupted: " + e.getMessage());
        }
    }


private void mergeChunks(String compressedChunkDir, String finalOutputPath) {
            Log.d("mergerChunkStart", "mergeChunks");
    File[] compressedChunks = new File(compressedChunkDir).listFiles((dir, name) -> name.endsWith(".mp4"));
    if (compressedChunks == null || compressedChunks.length == 0) {
        Log.e("VideoCompressionWorker", "No compressed chunks found to merge.");
        return;
    }
    // Sort the chunks to ensure proper order
    Arrays.sort(compressedChunks, (f1, f2) -> f1.getName().compareTo(f2.getName()));

    StringBuilder fileList = new StringBuilder();
    for (File chunk : compressedChunks) {
        fileList.append("file '").append(chunk.getAbsolutePath()).append("'\n");
    }
    Log.d("mergeChunks", "File list content:\n" + fileList);

    File listFile = new File(compressedChunkDir, "file_list.txt");
    try (FileWriter writer = new FileWriter(listFile)) {
        writer.write(fileList.toString());
    } catch (IOException e) {
        Log.e("VideoCompressionWorker", "Failed to write file list for merging: " + e.getMessage());
        return;
    }
    Log.d("TAG:finalOutputPath", "finalOutputPath: "+finalOutputPath);
    String cmd = String.format(
            "-f concat -safe 0 -i %s -c copy -y %s",
            listFile.getAbsolutePath(), finalOutputPath
    );
    int rc = FFmpeg.execute(cmd);
    if (rc != 0) {
        Log.e("VideoCompressionWorker", "Failed to merge chunks. RC: " + rc);
    } else {
        Log.d("VideoCompressionWorker", "Successfully merged chunks into: " + finalOutputPath);
    }

}
}

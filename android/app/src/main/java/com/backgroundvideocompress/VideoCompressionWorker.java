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
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;

public class VideoCompressionWorker extends ListenableWorker {

    private final ExecutorService executorService;

    public VideoCompressionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        executorService = Executors.newFixedThreadPool(4); // Use 4 threads for chunk compression
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        SettableFuture<Result> future = SettableFuture.create();

        executorService.submit(() -> {
            try {
                String inputVideoPath = getInputData().getString("videoPath");
                String outputVideoPath = "/storage/emulated/0/DCIM/.digiQC/video/final_compressed_video.mp4";
                String chunkDir = getApplicationContext().getExternalFilesDir("chunks").getAbsolutePath();
                String compressedChunkDir = getApplicationContext().getExternalFilesDir("compressed_chunks").getAbsolutePath();
                if (inputVideoPath == null) {
                    future.set(Result.failure());
                    return;
                }

                splitVideo(inputVideoPath, chunkDir);
                compressChunks(chunkDir, compressedChunkDir);
//                mergeChunks(compressedChunkDir, outputVideoPath);

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
        if (!chunkDirectory.exists()) {
            boolean created = chunkDirectory.mkdirs();
            if (!created) {
                Log.e("VideoCompressionWorker", "Failed to create chunk directory.");
                return;
            }
        }
        if (!chunkDirectory.canWrite()) {
            Log.e("chunkDirectory.canWrite", "Cannot write to chunk directory: " + chunkDirectory);
            return;
        }

        if (chunkDirectory.exists()) {
            for (File file : chunkDirectory.listFiles()) {
                Log.d("delete", "Deleting file: " + file.getName());
                file.delete();
            }
        }

                String cmd = String.format(
                        "-y -i \"%s\" -c copy -map 0 -f segment -segment_time 10 \"%s/chunk_%%03d.mp4\"",
                        inputPath, chunkDir
        );
        int rc = FFmpeg.execute(cmd);


        Log.d("VideoCompressionWorker", "RC:9090" + rc);
        if (rc != 0) {
            Log.e("VideoCompressionWorker", "Failed to split video. RC: " + rc);
        }

    }

    private void compressChunks(String chunkDir, String compressedChunkDir) {
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

        for (File chunk : chunks) {
            executorService.submit(() -> {
                String compressedChunkPath = compressedChunkDir + "/compressed_" + chunk.getName();
                String cmd = String.format(
                        "-y -i %s -vcodec libx264 -crf 28 -preset veryfast -acodec aac -b:a 96k -movflags +faststart %s",
                        chunk.getPath(), compressedChunkPath
                );
                Log.d("FFmpegDebug", "Chunk Input Path: " + chunk.getPath());
                int rc = FFmpeg.execute(cmd);
                Log.d("compressChunks", "compressChunks:rc "+rc);
                if (rc != 0) {
                    Log.e("VideoCompressionWorker", "Failed to compress chunk: " + chunk.getName() + ". RC: " + rc);
                }
            });
        }
        com.arthenica.mobileffmpeg.Config.enableLogCallback(message -> {
            Log.d("FFmpegLog10", message.getText());
        });
    }

    private void mergeChunks(String compressedChunkDir, String finalOutputPath) {
        File[] compressedChunks = new File(compressedChunkDir).listFiles((dir, name) -> name.endsWith(".mp4"));
        if (compressedChunks == null) return;

        StringBuilder fileList = new StringBuilder();

        for (File chunk : compressedChunks) {
            fileList.append("file '").append(chunk.getPath()).append("'\n");
        }
        Log.d("VideoCompressionWorker", "File list content:\n" + fileList);

        File listFile = new File(compressedChunkDir, "file_list.txt");
        Log.e("VideoCompressionWorker:listFile", "listFile" + listFile);
        try (FileWriter writer = new FileWriter(listFile)) {
            writer.write(fileList.toString());
        } catch (IOException e) {
            Log.e("VideoCompressionWorker", "Failed to write file list for merging: " + e.getMessage());
        }
        Log.d("finalOutputPath:1", "finalOutputPath" + listFile);
        Log.d("finalOutputPath:2", "listFile.getPath:" + listFile.getPath());
        String cmd = String.format(
                "-f concat -safe 0 -i %s -c copy -y %s",
                listFile.getAbsolutePath(), finalOutputPath
        );
        int rc = FFmpeg.execute(cmd);
        if (rc != 0) {
            Log.e("VideoCompressionWorker", "Failed to merge chunks. RC: " + rc);
        }

    }
}

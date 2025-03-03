package com.backgroundvideocompress;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
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
import java.nio.ByteBuffer;
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


//    private void compressChunks(String chunkDir, String compressedChunkDir) {
//        Log.d("compressChunks:START", "compressChunks: 'START");
//        File compressChunk = new File(compressedChunkDir);
//        if (!compressChunk.exists()) {
//            boolean created = compressChunk.mkdirs();
//            if (!created) {
//                Log.e("compressedChunkDir", "Failed to create chunk directory.");
//                return;
//            }
//        }
//        if (compressChunk.exists()) {
//            for (File file : compressChunk.listFiles()) {
//                Log.d("delete", "Deleting file:compressChunk " + file.getName());
//                file.delete();
//            }
//        }
//
//        File[] chunks = new File(chunkDir).listFiles((dir, name) -> name.endsWith(".mp4"));
//        if (chunks == null || chunks.length == 0) {
//            Log.e("VideoCompressionWorker", "No valid chunks found to compress.");
//            return;
//        }
//        if (chunks == null) return;
//
//        ExecutorService compressExecutor = Executors.newFixedThreadPool(4);
//
//        // Use a list to track tasks
//        List<Runnable> compressionTasks = new ArrayList<>();
//        for (File chunk : chunks) {
//            Log.d("chunkFORLOOP", "chunkFORLOOP");
//            compressExecutor.submit(() -> {
//                    String compressedChunkPath = compressedChunkDir + "/compressed_" + chunk.getName();
//
//                        String cmd = String.format(
//                            "-y -i %s -vcodec libx264 -crf 30 -preset veryfast -acodec aac -b:a 96k -movflags +faststart %s",
//                            chunk.getPath(), compressedChunkPath
//                    );
//                    Log.d("FFmpegDebug", "Chunk Input Path: " + chunk.getPath());
//                    int rc = FFmpeg.execute(cmd);
//                    if (rc != 0) {
//                        Log.e("VideoCompressionWorker", "Failed to compress chunk: " + chunk.getName() + ". RC: " + rc);
//                    } else {
//                        Log.d("compressChunks", "Compressed chunk successfully: " + chunk.getName());
//                    }
//            });
//        }
//
//        Log.d("shutdown:task", "shutdown:task");
//        compressExecutor.shutdown();
//        try {
//            if (!compressExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
//                Log.e("VideoCompressionWorker", "Compression tasks did not finish in time.");
//            }
//        } catch (InterruptedException e) {
//            Log.e("VideoCompressionWorker", "Compression was interrupted: " + e.getMessage());
//        }
//    }

    private void compressChunks(String chunkDir, String compressedChunkDir) {
        Log.d("compressChunks:START", "Compressing chunks...");

        File compressChunk = new File(compressedChunkDir);
        if (!compressChunk.exists() && !compressChunk.mkdirs()) {
            Log.e("compressedChunkDir", "Failed to create compressed chunk directory.");
            return;
        }

        // Clean the output directory
        for (File file : compressChunk.listFiles()) {
            Log.d("compressChunks", "Deleting old compressed chunk: " + file.getName());
            file.delete();
        }

        File[] chunks = new File(chunkDir).listFiles((dir, name) -> name.endsWith(".mp4"));
        if (chunks == null || chunks.length == 0) {
            Log.e("VideoCompressionWorker", "No valid chunks found to compress.");
            return;
        }

        ExecutorService compressExecutor = Executors.newFixedThreadPool(4); // Adjust thread pool size if necessary

        for (File chunk : chunks) {
            compressExecutor.submit(() -> {
                String compressedChunkPath = compressedChunkDir + "/compressed_" + chunk.getName();
                try {
                    compressVideo(chunk.getPath(), compressedChunkPath);
                    Log.d("compressChunks", "Compressed chunk successfully: " + chunk.getName());
                } catch (Exception e) {
                    Log.e("VideoCompressionWorker", "Failed to compress chunk: " + chunk.getName(), e);
                }
            });
        }

        compressExecutor.shutdown();
        try {
            if (!compressExecutor.awaitTermination(10, TimeUnit.MINUTES)) {
                Log.e("VideoCompressionWorker", "Compression tasks did not finish in time.");
            }
        } catch (InterruptedException e) {
            Log.e("VideoCompressionWorker", "Compression was interrupted: " + e.getMessage());
        }
    }




    private void compressVideo(String inputPath, String outputPath) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(inputPath);

        int videoTrackIndex = -1;
        MediaFormat inputFormat = null;

        // Find the video track
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                videoTrackIndex = i;
                inputFormat = format;
                extractor.selectTrack(i);
                break;
            }
        }

        if (videoTrackIndex == -1 || inputFormat == null) {
            throw new IllegalArgumentException("No video track found in file: " + inputPath);
        }

        // Configure encoder
        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH) / 2; // Reduce resolution
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT) / 2;
        int bitrate = 1000000; // 1 Mbps
        int frameRate = 30;

        MediaFormat outputFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        MediaCodec encoder = MediaCodec.createEncoderByType("video/avc");
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        MediaMuxer muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        encoder.start();

        // Add video track to the muxer
        int muxerTrackIndex = -1;
        boolean isTrackAdded = false;

        boolean isEOS = false;
        while (!isEOS) {
            // Extract frame
            int inputBufferIndex = encoder.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                int sampleSize = extractor.readSampleData(inputBuffer, 0);

                if (sampleSize < 0) {
                    encoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    isEOS = true;
                } else {
                    long presentationTimeUs = extractor.getSampleTime();
                    encoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                    extractor.advance();
                }
            }

            // Get encoded data
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                if (!isTrackAdded) {
                    // Add track to the muxer (must happen before writing data)
                    MediaFormat encoderOutputFormat = encoder.getOutputFormat();
                    muxerTrackIndex = muxer.addTrack(encoderOutputFormat);
                    muxer.start();
                    isTrackAdded = true;
                }

                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo);
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000);
            }
        }

        // Finish encoding
        encoder.stop();
        encoder.release();
        muxer.stop();
        muxer.release();
        extractor.release();
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

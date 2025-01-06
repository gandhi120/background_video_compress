package com.backgroundvideocompress;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class VideoModule extends ReactContextBaseJavaModule {

    public VideoModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @NonNull
    @Override
    public String getName() {
        return "VideoModule";
    }

    @ReactMethod
    public void startVideoCompression(String videoPath, Promise promise) {
        try {
            Data inputData = new Data.Builder()
                    .putString("videoPath", videoPath)
                    .build();
            Log.d("TAG:inputData", "startWork::inputData "+inputData);

            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(VideoCompressionWorker.class)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(getReactApplicationContext()).enqueue(workRequest);

            promise.resolve("Video compression started");
        } catch (Exception e) {
            promise.reject("Video compression failed", e);
        }
    }
}

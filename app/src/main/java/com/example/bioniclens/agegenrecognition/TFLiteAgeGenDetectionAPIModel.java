package com.example.bioniclens.agegenrecognition;

import android.graphics.Bitmap;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.Nullable;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.metadata.MetadataExtractor;

public class TFLiteAgeGenDetectionAPIModel implements Detector{

    private static final String TAG = "TFLiteObjectDetectionAPIModelWithInterpreter";

    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // Float model
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private boolean isModelQuantized;

    private int inputSize;
    private final List<String> labels = new ArrayList<>();
    private int[] intValues;
    private float[][][] outputLocations;
    private float[][] outputClasses;

    private float[][] outputScores;

    private float[] numDetections;

    private ByteBuffer imgData;

    private MappedByteBuffer tfLiteModel;
    private Interpreter.Options tfLiteOptions;
    private Interpreter tfLite;

    private float[][] ageMap;
    private float[][] genderMap;
    private static final int AGE_SIZE = 4;
    private static final int GENDER_SIZE = 2;
    private static final float WHITE_THRESH = 255f;

    private TFLiteAgeGenDetectionAPIModel() {}


    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    public static Detector create(
            final Context context,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final boolean isQuantized)
            throws IOException {
        final TFLiteAgeGenDetectionAPIModel d = new TFLiteAgeGenDetectionAPIModel();

        AssetManager am = context.getAssets();
        InputStream is = am.open(labelFilename);

        MappedByteBuffer modelFile = loadModelFile(context.getAssets(), modelFilename);
        MetadataExtractor metadata = new MetadataExtractor(modelFile);
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                d.labels.add(line);
            }
        }

        d.inputSize = inputSize;

        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(NUM_THREADS);
            options.setUseXNNPACK(true);
            d.tfLite = new Interpreter(modelFile, options);
            d.tfLiteModel = modelFile;
            d.tfLiteOptions = options;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1;
        } else {
            numBytesPerChannel = 4;
        }
        d.imgData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

        d.outputLocations = new float[1][NUM_DETECTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTIONS];
        d.outputScores = new float[1][NUM_DETECTIONS];
        d.numDetections = new float[1];
        return d;
    }

    @Nullable
    @Override
    public String recognizeImage(@Nullable Bitmap bitmap) {
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else {
                    imgData.putFloat((pixelValue & 0xFF) / WHITE_THRESH);
                    imgData.putFloat(((pixelValue >> 8) & 0xFF) / WHITE_THRESH);
                    imgData.putFloat(((pixelValue >> 16) & 0xFF) / WHITE_THRESH);
                }
            }
        }
        Trace.endSection();
        Trace.beginSection("feed");
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();

        ageMap = new float[1][AGE_SIZE];
        genderMap = new float[1][GENDER_SIZE];

        outputMap.put(0, ageMap);
        outputMap.put(1, genderMap);

        Trace.endSection();
        Trace.beginSection("run");

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        Log.w("AGE", "!!!AGE: " + ageMap[0][0] + "|" + ageMap[0][1] + "|" + ageMap[0][2] + "|" + ageMap[0][3]);
        Log.w("GENDER", "!!!GENDER: " + genderMap[0][0] + "|" + genderMap[0][1]);

        Integer ageInd = 0;
        Integer genderInd = 0;

        Float max = -1f;
        for (int i = 0; i < ageMap[0].length; i++) {
            float currValue = ageMap[0][i];

            if (currValue > max) {
                max = currValue;
                ageInd = i;
            }
        }

        max = -1f;
        for (int i = 0; i < genderMap[0].length; i++) {
            float currValue = genderMap[0][i];
            if (currValue > max) {
                max = currValue;
                genderInd = i;
            }
        }
        List<String> genderList = Arrays.asList("Female", "Male");
        List<String> ageList = Arrays.asList("0-14yo", "15-40yo", "41-60yo", "61-100yo");
        String label = "AGE: " + ageList.get(ageInd) + " | Gender: " + genderList.get(genderInd);

        Trace.endSection();

        final int numDetectionsOutput = 1;
        final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);

        Trace.endSection();
        return label;
    }

    @Override
    public void enableStatLogging(boolean debug) {

    }

    @Nullable
    @Override
    public String getStatString() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public void setNumThreads(int numThreads) {

    }

    @Override
    public void setUseNNAPI(boolean isChecked) {

    }
}

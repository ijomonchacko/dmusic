package com.dmusic.android.dmusic;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import java.io.File;

public class HttpsStreamingTestActivity extends Activity {
    private static final String TAG = "HttpsStreamingTest";
    private TextView statusText;
    private Button testButton;
    
    // Test HTTPS URLs for validation
    private static final String[] TEST_URLS = {
        "https://www2.cs.uic.edu/~i101/SoundFiles/BabyElephantWalk60.wav",
        "https://archive.org/download/testmp3testfile/mpthreetest.mp3",
        "https://sample-videos.com/zip/10/mp3/mp3-sample.mp3"
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout
        statusText = new TextView(this);
        statusText.setText("Ready to test HTTPS streaming with Mobile FFmpeg");
        statusText.setPadding(20, 20, 20, 20);
        
        testButton = new Button(this);
        testButton.setText("Test HTTPS Streaming");
        testButton.setOnClickListener(v -> runHttpsTests());
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.addView(statusText);
        layout.addView(testButton);
        
        setContentView(layout);
        
        // Log capabilities on startup
        BytedecoFFmpegStreamingEngine.logCapabilities();
    }
    
    private void runHttpsTests() {
        statusText.setText("🚀 Starting HTTPS streaming tests...");
        testButton.setEnabled(false);
        
        Log.i(TAG, "🚀 Starting comprehensive HTTPS streaming tests");
        
        // Test each URL
        for (int i = 0; i < TEST_URLS.length; i++) {
            final String url = TEST_URLS[i];
            final int testNumber = i + 1;
            
            runOnUiThread(() -> 
                statusText.setText(String.format("🔍 Testing URL %d/%d: %s", 
                    testNumber, TEST_URLS.length, url))
            );
            
            Log.i(TAG, String.format("🔍 Test %d: %s", testNumber, url));
            
            // Test streaming capability
            BytedecoFFmpegStreamingEngine.testHttpsStreaming(url)
                .thenAccept(success -> {
                    runOnUiThread(() -> {
                        String result = success ? "✅ SUCCESS" : "❌ FAILED";
                        Log.i(TAG, String.format("Test %d result: %s", testNumber, result));
                        
                        if (success && testNumber == 1) {
                            // If first test succeeds, try conversion
                            testConversion(url);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "❌ Test exception: " + throwable.getMessage(), throwable);
                    return null;
                });
        }
        
        // Re-enable button after tests
        new android.os.Handler().postDelayed(() -> {
            runOnUiThread(() -> {
                testButton.setEnabled(true);
                statusText.setText("✅ HTTPS streaming tests completed! Check logs for results.");
            });
        }, 10000); // 10 seconds
    }
    
    private void testConversion(String url) {
        try {
            File outputDir = new File(getExternalFilesDir(null), "ffmpeg_test");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, "test_https_stream.aac");
            String outputPath = outputFile.getAbsolutePath();
            
            runOnUiThread(() -> 
                statusText.setText("🔄 Testing HTTPS stream conversion...")
            );
            
            Log.i(TAG, "🔄 Testing stream conversion to: " + outputPath);
            
            BytedecoFFmpegStreamingEngine.convertHttpsStream(url, outputPath)
                .thenAccept(success -> {
                    runOnUiThread(() -> {
                        if (success) {
                            statusText.setText("🎉 HTTPS streaming and conversion SUCCESSFUL! 🎉");
                            Log.i(TAG, "🎉 HTTPS streaming is fully functional!");
                        } else {
                            statusText.setText("⚠️ HTTPS test passed but conversion failed");
                            Log.w(TAG, "⚠️ Streaming works but conversion needs debugging");
                        }
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "❌ Conversion exception: " + throwable.getMessage(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            Log.e(TAG, "❌ Setup exception: " + e.getMessage(), e);
        }
    }
} 
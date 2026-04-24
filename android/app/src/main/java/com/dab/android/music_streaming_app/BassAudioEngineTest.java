package com.dmusic.android.dmusic;

import android.util.Log;

/**
 * Test class for BASS Audio Engine
 */
public class BassAudioEngineTest {
    private static final String TAG = "BassAudioEngineTest";
    
    // Native test methods
    public native void testEngine();
    public native String getBassVersion();
    public native String getDeviceInfo();
    
    /**
     * Run all tests
     */
    public static void runTests() {
        Log.i(TAG, "=== Starting BASS Audio Engine Java Tests ===");
        
        try {
            BassAudioEngineTest test = new BassAudioEngineTest();
            
            // Test 1: Get BASS version
            Log.i(TAG, "Test 1: Getting BASS version");
            String version = test.getBassVersion();
            Log.i(TAG, "BASS Version: " + version);
            
            // Test 2: Get device info
            Log.i(TAG, "Test 2: Getting device information");
            String deviceInfo = test.getDeviceInfo();
            Log.i(TAG, "Device Info:\n" + deviceInfo);
            
            // Test 3: Test engine functionality
            Log.i(TAG, "Test 3: Testing engine functionality");
            test.testEngine();
            
            // Test 4: Test Java wrapper
            Log.i(TAG, "Test 4: Testing Java wrapper");
            testJavaWrapper();
            
            Log.i(TAG, "=== All Tests Completed ===");
            
        } catch (Exception e) {
            Log.e(TAG, "Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test Java wrapper functionality
     */
    private static void testJavaWrapper() {
        Log.i(TAG, "Creating BassAudioEngine instance...");
        BassAudioEngine engine = new BassAudioEngine();
        
        try {
            // Test initialization
            Log.i(TAG, "Testing initialization...");
            if (engine.initialize()) {
                Log.i(TAG, "✓ Engine initialized successfully");
            } else {
                Log.e(TAG, "✗ Engine initialization failed: " + engine.getLastError());
                return;
            }
            
            // Test status
            Log.i(TAG, "Testing status...");
            Log.i(TAG, "Is initialized: " + engine.isInitialized());
            Log.i(TAG, "Is playing: " + engine.isPlaying());
            Log.i(TAG, "Is paused: " + engine.isPaused());
            
            // Test volume
            Log.i(TAG, "Testing volume...");
            Log.i(TAG, "Initial volume: " + engine.getVolume());
            engine.setVolume(0.5f);
            Log.i(TAG, "Volume after setting to 0.5: " + engine.getVolume());
            
            // Test position/duration
            Log.i(TAG, "Testing position/duration...");
            Log.i(TAG, "Position: " + engine.getPosition() + " seconds");
            Log.i(TAG, "Duration: " + engine.getDuration() + " seconds");
            
            Log.i(TAG, "✓ Java wrapper tests completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Java wrapper test failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up
            Log.i(TAG, "Disposing engine...");
            engine.dispose();
        }
    }
}

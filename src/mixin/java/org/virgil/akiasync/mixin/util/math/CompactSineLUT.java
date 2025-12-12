package org.virgil.akiasync.mixin.util.math;

import net.minecraft.util.Mth;
import java.lang.reflect.Field;

public class CompactSineLUT {
    
    private static final int[] SINE_TABLE_INT = new int[16384 + 1];
    
    private static final float SINE_TABLE_MIDPOINT;
    
    private static boolean initialized = false;
    
    private static float[] getSinTable() {
        try {
            // Try to access Mth.SIN via reflection since it's private in 1.21.4
            Field sinField = Mth.class.getDeclaredField("SIN");
            sinField.setAccessible(true);
            return (float[]) sinField.get(null);
        } catch (Exception e) {
            // Fallback: generate our own sine table
            float[] table = new float[65536];
            for (int i = 0; i < 65536; i++) {
                table[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
            }
            return table;
        }
    }
    
    static {
        try {
            float[] sinTable = getSinTable();
            
            for (int i = 0; i < SINE_TABLE_INT.length; i++) {
                SINE_TABLE_INT[i] = Float.floatToRawIntBits(sinTable[i]);
            }
            
            SINE_TABLE_MIDPOINT = sinTable[sinTable.length / 2];
            
            for (int i = 0; i < sinTable.length && i < SINE_TABLE_INT.length; i++) {
                float expected = sinTable[i];
                float value = lookup(i);
                
                if (expected != value) {
                    throw new IllegalArgumentException(
                        String.format("LUT error at index %d (expected: %s, found: %s)", 
                            i, expected, value));
                }
            }
            
            initialized = true;
        } catch (Exception e) {
            System.err.println("[AkiAsync] Failed to initialize CompactSineLUT: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("CompactSineLUT initialization failed", e);
        }
    }
    
    public static void init() {
        
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static float sin(float f) {
        return lookup((int) (f * 10430.378f) & 0xFFFF);
    }
    
    public static float cos(float f) {
        return lookup((int) (f * 10430.378f + 16384.0f) & 0xFFFF);
    }
    
    private static float lookup(int index) {
        
        if (index == 32768) {
            return SINE_TABLE_MIDPOINT;
        }
        
        int neg = (index & 0x8000) << 16;
        
        int mask = (index << 17) >> 31;
        
        int pos = (0x8001 & mask) + (index ^ mask);
        
        pos &= 0x7fff;
        
        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }
    
    public static String getStats() {
        int originalSize = 65536; 
        int compactSize = SINE_TABLE_INT.length;
        int savedBytes = (originalSize - compactSize) * 4; 
        double reduction = (1.0 - (double) compactSize / originalSize) * 100;
        
        return String.format(
            "CompactSineLUT: %d entries (%.1f%% reduction, saved %d KB)",
            compactSize, reduction, savedBytes / 1024
        );
    }
}

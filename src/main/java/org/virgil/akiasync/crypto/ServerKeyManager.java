package org.virgil.akiasync.crypto;

public class ServerKeyManager {
    
    private byte[] currentKey;
    
    public ServerKeyManager() {
        this.currentKey = new byte[32];
    }
    
    public byte[] getCurrentKey() {
        return currentKey;
    }
    
    public byte[] getServerKey() {
        return currentKey;
    }
    
    public void rotateKey() {
        // Key rotation logic
    }
}

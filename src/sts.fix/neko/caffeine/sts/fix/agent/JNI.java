package neko.caffeine.sts.fix.agent;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import static java.io.File.*;

public interface JNI extends Library {
    
    static JNI init() {
        final String key = "jna.library.path", home = System.getProperty("java.home");
        switch (Platform.getOSType()) {
            case Platform.WINDOWS, Platform.WINDOWSCE -> {
                final String bin = home + separator + "bin";
                System.setProperty(key, bin + pathSeparator + System.getProperty(key, ""));
            }
            case Platform.MAC                         -> {
                final String lib = home + separator + "lib", server = lib + separator + "server";
                System.setProperty(key, lib + pathSeparator + server + pathSeparator + System.getProperty(key, ""));
            }
        }
        return Native.loadLibrary("jvm", JNI.class);
    }
    
    JNI jni = init();
    
    Instrument instrument = Native.loadLibrary("instrument", Instrument.class);
    
    interface Instrument extends Library {
        
        int Agent_OnAttach(Pointer p_vm, String path, Pointer p_reserved = null);
        
        static void attachAgent(final String path, final Pointer p_reserved = null) = checkJNIError(instrument.Agent_OnAttach(contextVM(), path, p_reserved));
        
    }
    
    static void checkJNIError(final int jniReturnCode) throws LastErrorException {
        if (jniReturnCode != 0)
            throw new LastErrorException(jniReturnCode);
    }
    
    int JNI_GetCreatedJavaVMs(PointerByReference p_vms, int count, IntByReference p_found);
    
    static Pointer contextVM() throws LastErrorException {
        final PointerByReference p_vms = { };
        final IntByReference p_found = { };
        checkJNIError(jni.JNI_GetCreatedJavaVMs(p_vms, 1, p_found));
        return p_vms.getValue();
    }
    
}

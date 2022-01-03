package neko.caffeine.sts.fix.agent;

import java.lang.instrument.Instrumentation;

public class LiveInjector {
    
    public static String agentArgs;
    
    public static Instrumentation instrumentation;
    
    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        LiveInjector.agentArgs = agentArgs;
        LiveInjector.instrumentation = instrumentation;
    }
    
}

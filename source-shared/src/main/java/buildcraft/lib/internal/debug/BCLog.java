/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.lib.internal.debug;

import buildcraft.lib.internal.core.BuildCraftAPI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BCLog {
    public static final Logger logger = LogManager.getLogger("BuildCraft");
    									//LogUtils.getLogger();
    
    private static final java.util.Set<String> CAUGHT_CONTEXTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Deactivate constructor */
    private BCLog() {}

    /**
     * Logs an exception that was caught and handled. The first occurrence per context is logged at WARN with the
     * stack trace; repeats are logged at DEBUG so per-frame or per-block paths cannot flood the log.
     */
    public static void caught(String context, Throwable error) {
        if (context == null) {
            context = "unknown";
        }
        if (CAUGHT_CONTEXTS.add(context)) {
            logger.warn("Caught exception in {} (repeats are logged at DEBUG)", context, error);
        } else {
            logger.debug("Caught exception in {}", context, error);
        }
    }

    @Deprecated
    public static void logErrorAPI(String mod, Throwable error, Class<?> classFile) {
        logErrorAPI(error, classFile);
        
     
    }

    public static void logErrorAPI(Throwable error, Class<?> classFile) {
        StringBuilder msg = new StringBuilder("API error! Please update your mods. Error: ");
        msg.append(error);
        StackTraceElement[] stackTrace = error.getStackTrace();
        if (stackTrace.length > 0) {
            msg.append(", ").append(stackTrace[0]);
        }
        
        logger.error(msg.toString());

        if (classFile != null) {
            msg.append("API error: ").append(classFile.getSimpleName()).append(" is loaded from ").append(classFile.getProtectionDomain()
                    .getCodeSource().getLocation());
            logger.error(msg.toString());
        }
    }

    @Deprecated
    public static String getVersion() {
        return BuildCraftAPI.getVersion();
    }
    
    public static void d(String s) {
    	logger.debug(new Exception().getStackTrace()[1].getClassName() + " : " + s);
    }
    
    public static void d(boolean flag) {
    	if(flag)
    		logger.debug(new Exception().getStackTrace()[1].getClassName() + " : Catched!");
    }
    
    public static void d(boolean flag, String msg) {
    	if(flag)
    		logger.debug(new Exception().getStackTrace()[1].getClassName() + " : "+msg);
    }
}

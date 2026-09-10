package android.util;

/** Desktop stand-in so swallowed exceptions are visible off-device. */
public class Log {
    public static String getStackTraceString(Throwable t) {
        if (t == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
    public static int d(String tag, String msg) { System.err.println("D/" + tag + ": " + msg); return 0; }
    public static int e(String tag, String msg) { System.err.println("E/" + tag + ": " + msg); return 0; }
    public static int i(String tag, String msg) { System.err.println("I/" + tag + ": " + msg); return 0; }
    public static int w(String tag, String msg) { System.err.println("W/" + tag + ": " + msg); return 0; }
    public static int v(String tag, String msg) { System.err.println("V/" + tag + ": " + msg); return 0; }
}

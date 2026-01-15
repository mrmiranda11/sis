package com.experian.util

class MiscUtil {
    public static String getStackTrace(Throwable e) {
        if (e == null) return "No Exception"
        def msg = "${e.getClass().simpleName} / $e.message:\n"
        def sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        return msg+sw.toString()
    }
}

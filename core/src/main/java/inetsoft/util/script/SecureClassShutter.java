package inetsoft.util.script;

import org.mozilla.javascript.ClassShutter;

import java.util.*;

/**
 * Secure ClassShutter implementation for Rhino JavaScript engine
 * Blocks access to potentially dangerous classes that could cause security vulnerabilities
 */
public class SecureClassShutter implements ClassShutter {
   // Dangerous packages that should be completely blocked
   private static final Set<String> BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
      "java.lang.reflect",
      "java.lang.invoke",
      "java.security",
      "java.net",
      "java.io",
      "java.nio",
      "java.util.concurrent",
      "javax.script",
      "sun.",
      "com.sun.",
      "jdk.internal.",
      "java.lang.management",
      "javax.management",
      "java.rmi",
      "javax.naming",
      "java.sql",
      "javax.sql",
      "org.xml.sax",
      "javax.xml",
      "java.beans"
   ));

   // Specific dangerous classes that should be blocked
   private static final Set<String> BLOCKED_CLASSES = new HashSet<>(Arrays.asList(
      "java.lang.System",
      "java.lang.Runtime",
      "java.lang.Process",
      "java.lang.ProcessBuilder",
      "java.lang.Class",
      "java.lang.ClassLoader",
      "java.lang.Thread",
      "java.lang.ThreadDeath",
      "java.lang.ThreadGroup",
      "java.lang.ThreadLocal",
      "java.lang.InheritableThreadLocal",
      "java.lang.SecurityManager",
      "java.lang.Package",
      "java.lang.Compiler",
      "java.util.ServiceLoader",
      "java.awt.Desktop",
      "javax.swing.JFileChooser",
      "java.io.File",
      "java.io.FileInputStream",
      "java.io.FileOutputStream",
      "java.io.FileReader",
      "java.io.FileWriter",
      "java.io.RandomAccessFile",
      "java.net.URL",
      "java.net.URLConnection",
      "java.net.HttpURLConnection",
      "java.net.Socket",
      "java.net.ServerSocket",
      "java.net.DatagramSocket",
      "java.net.MulticastSocket"
   ));

   // Safe classes that are explicitly allowed (whitelist approach for sensitive areas)
   private static final Set<String> ALLOWED_CLASSES = new HashSet<>(Arrays.asList(
      "java.lang.String",
      "java.lang.Integer",
      "java.lang.Long",
      "java.lang.Double",
      "java.lang.Float",
      "java.lang.Boolean",
      "java.lang.Character",
      "java.lang.Byte",
      "java.lang.Short",
      "java.lang.Number",
      "java.lang.Object",
      "java.util.ArrayList",
      "java.util.HashMap",
      "java.util.HashSet",
      "java.util.LinkedList",
      "java.util.TreeMap",
      "java.util.TreeSet",
      "java.util.Date",
      "java.util.Calendar",
      "java.util.GregorianCalendar",
      "java.util.TimeZone",
      "java.util.Locale",
      "java.util.UUID",
      "java.util.regex.Pattern",
      "java.util.regex.Matcher",
      "java.text.SimpleDateFormat",
      "java.text.DecimalFormat",
      "java.text.NumberFormat",
      "java.math.BigDecimal",
      "java.math.BigInteger"
   ));

   @Override
   public boolean visibleToScripts(String className) {
      // Null or empty class names are not allowed
      if(className == null || className.isEmpty()) {
         return false;
      }

      // Block dangerous packages
      for(String blockedPackage : BLOCKED_PACKAGES) {
         if(className.startsWith(blockedPackage)) {
            logSecurityViolation("Blocked package access", className);
            return false;
         }
      }

      // Block specific dangerous classes
      if(BLOCKED_CLASSES.contains(className)) {
         logSecurityViolation("Blocked class access", className);
         return false;
      }

      // Block array classes of dangerous types
      if(className.startsWith("[L") && className.endsWith(";")) {
         String arrayType = className.substring(2, className.length() - 1);
         if(!visibleToScripts(arrayType)) {
            logSecurityViolation("Blocked array type access", className);
            return false;
         }
      }

      // Allow explicitly safe classes
      if(ALLOWED_CLASSES.contains(className)) {
         return true;
      }

      // Allow basic Java lang classes (with some exceptions already blocked above)
      if(className.startsWith("java.lang.") && !className.contains("$")) {
         // Additional safety check for nested classes
         return isBasicJavaLangClass(className);
      }

      // Allow basic collections and utilities
      if(className.startsWith("java.util.") && !className.contains("concurrent")) {
         return isBasicUtilClass(className);
      }

      // Allow basic math classes
      if(className.startsWith("java.math.")) {
         return isBasicMathClass(className);
      }

      // Allow basic text classes
      if(className.startsWith("java.text.") && !className.contains("spi")) {
         return isBasicTextClass(className);
      }

      // Block everything else by default (fail-safe approach)
      logSecurityViolation("Blocked unknown class", className);
      return false;
   }

   private boolean isBasicJavaLangClass(String className) {
      // Allow only basic, safe java.lang classes
      return className.matches("java\\.lang\\.(String|Integer|Long|Double|Float|Boolean|Character|Byte|Short|Number|Object|Math|StrictMath|StringBuilder|StringBuffer|Enum|Comparable|Iterable|CharSequence|Appendable|Readable|AutoCloseable|Exception|RuntimeException|Error|Throwable)");
   }

   private boolean isBasicUtilClass(String className) {
      // Allow basic collections and utilities, but block advanced features
      return !className.contains("concurrent") &&
         !className.contains("spi") &&
         !className.contains("logging") &&
         !className.contains("prefs") &&
         !className.contains("jar") &&
         !className.contains("zip") &&
         !className.contains("stream") &&
         !className.contains("ServiceLoader");
   }

   private boolean isBasicMathClass(String className) {
      // Allow basic math classes only
      return className.matches("java\\.math\\.(BigDecimal|BigInteger|MathContext|RoundingMode)");
   }

   private boolean isBasicTextClass(String className) {
      // Allow basic text formatting classes only
      return className.matches("java\\.text\\.(DateFormat|SimpleDateFormat|NumberFormat|DecimalFormat|MessageFormat|FieldPosition|ParsePosition|Format|Collator|BreakIterator|Normalizer|AttributedString|AttributedCharacterIterator)");
   }

   private void logSecurityViolation(String reason, String className) {
      // Log security violations for monitoring
      System.err.println("SECURITY WARNING: " + reason + " - " + className);
      // You might want to use a proper logging framework here
      // logger.warn("Security violation: {} - {}", reason, className);
   }

   /**
    * Factory method to create and configure a secure Rhino context
    */
   public static org.mozilla.javascript.Context createSecureContext() {
      org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();

      if(!(cx instanceof TimeoutContext)) { // TimeoutContext sets SecureClassShutter in constructor
         try {
            // Set the class shutter
            cx.setClassShutter(new SecureClassShutter());

            // Additional security settings
            //cx.setOptimizationLevel(-1); // Disable optimization for security
            //cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
         }
         catch(Exception e) {
            org.mozilla.javascript.Context.exit();
            throw e;
         }
      }

      return cx;
   }

   /* Example usage method
    public static void main(String[] args) {
       org.mozilla.javascript.Context cx = createSecureContext();
       try {
          org.mozilla.javascript.Scriptable scope = cx.initStandardObjects();

          // This should work - basic operations
          Object result1 = cx.evaluateString(scope, "var x = 'Hello World'; x.length;", "test", 1, null);
          System.out.println("Safe operation result: " + result1);

          // This should be blocked - trying to access System
          try {
             cx.evaluateString(scope, "java.lang.System.exit(0);", "test", 1, null);
             System.out.println("ERROR: Security bypass detected!");
          }
          catch(Exception e) {
            System.out.println("Security working: " + e.getMessage());
          }
       }
       finally {
         org.mozilla.javascript.Context.exit();
       }
    }
    */
}

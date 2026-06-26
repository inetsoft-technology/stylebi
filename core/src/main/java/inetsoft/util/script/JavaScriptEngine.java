/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.util.script;

import inetsoft.report.internal.ImageLocation;
import inetsoft.report.internal.MetaImage;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.internal.DateComparisonUtil;
import inetsoft.util.*;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.script.graal.ScriptScope;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.pojava.datetime.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.text.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static script utility functions and per-thread script state. These are the
 * Rhino-free remnants of the old Rhino-based JavaScriptEngine; the actual
 * scripting runtime is now provided by
 * {@link inetsoft.util.script.graal.GraalJavaScriptEngine}. Only the utility
 * functions used by scripts (date/number/array helpers) and the thread-local
 * script state (exec scope, error counts, on-click flag) remain here, since
 * they are referenced from many call sites across the code base.
 *
 * @version 6.1, 5/27/2004
 * @author InetSoft Technology Corp
 */
public class JavaScriptEngine {
   // increment script error count
   public static void incrementError(Object error) {
      getThreadLocals().scriptErrors.get().compute(error, (o, i) -> (i == null) ? 1 : ++i);
   }

   /**
    * Mark the current thread as executing a script by pushing the active
    * scope. Must be balanced with {@link #popExecScriptable()} in a finally
    * block. This is a dedicated per-thread stack scoped tightly to actual
    * script evaluation (mirrors the pre-GraalJS engine), and is intentionally
    * separate from {@link FormulaContext}'s scope stack (which is also pushed
    * during non-script operations such as viewsheet data processing).
    */
   public static void pushExecScriptable(ScriptScope scope) {
      getThreadLocals().execScriptable.get().push(scope);
   }

   /**
    * Pop the active script scope. Removes the thread-local entirely once the
    * stack is empty so reused (pooled) threads are not left flagged as script
    * threads.
    */
   public static void popExecScriptable() {
      Stack<ScriptScope> stack = getThreadLocals().execScriptable.get();

      if(!stack.isEmpty()) {
         stack.pop();
      }

      if(stack.isEmpty()) {
         getThreadLocals().execScriptable.remove();
      }
   }

   /**
    * Get the scriptable executing the actual script, or null if the current
    * thread is not inside script evaluation.
    */
   public static ScriptScope getExecScriptable() {
      Stack<ScriptScope> stack = getThreadLocals().execScriptable.get();
      return stack.isEmpty() ? null : stack.peek();
   }

   public static boolean isScriptThread() {
      return getExecScriptable() != null;
   }

   public static void resetScriptThread() {
      getThreadLocals().execScriptable.remove();
   }

   /**
    * Check if the script already threw the max number of errors specified by the
    * <code>script.max.errors</code> property
    */
   public static boolean errorsExceeded(Object error) {
      final String maxErrorProp = maxErrorsProperty.get();
      final Integer maxErrors = Tool.getIntegerData(maxErrorProp);

      if(maxErrors == null) {
         return false;
      }

      int errorCount = getThreadLocals().scriptErrors.get().getOrDefault(error, 0);

      if(errorCount == maxErrors) {
         LOG.warn("Script Max Errors Exceeded (error count: {})", maxErrors);
         getThreadLocals().scriptErrors.get().put(error, ++errorCount);
      }

      return errorCount > maxErrors;
   }

   /**
    * Create a new instance of an object.
    */
   public static Object newInstance(String cls) throws Exception {
      return Class.forName(cls).newInstance();
   }

   /**
    * Check if an object is an array.
    */
   public static boolean isArray(Object val) {
      return JSObject.isArray(val);
   }

   /**
    * get the index of an object in array.
    */
   public static int indexOf(Object arr, Object val) {
      arr = unwrap(arr);
      val = unwrap(val);

      if(!isArray(arr)) {
         return -1;
      }
      else {
         Object[] a = split(arr);

         for(int i = 0; i < a.length; i++) {
            if(val != null && val.equals(a[i])) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Check if an object is null.
    */
   public static boolean isNull(Object val) {
      return unwrap(val) == null;
   }

   /**
    * Check if an object is a date.
    */
   public static boolean isDate(Object val) {
      return getDate(val) != null;
   }

   /**
    * Check if an object is a number.
    */
   public static boolean isNumber(Object val) {
      return unwrap(val) instanceof Number;
   }

   /**
    * Format a date object into a string.
    */
   public static String formatDate(Object val, String fmtstr) {
      Date date = getDate(val);
      SimpleDateFormat fmt = Tool.createDateFormat(fmtstr, ThreadContext.getLocale());

      return (date != null) ? fmt.format(date) : "NaD";
   }

   /**
    * Format a number into a string.
    * @param round specify the rounding model. Use one of the following:
    * ROUND_UP, ROUND_DOWN, ROUND_CEILING, ROUND_FLOOR, ROUND_HALF_UP,
    * ROUND_HALF_DOWN, ROUND_HALF_EVEN, ROUND_UNNECESSARY. Control the
    * same behavior as BigDecimal.
    */
   public static String formatNumber(double num, String fmtstr, Object round) {
      DecimalFormat fmt = null;

      round = unwrap(round);

      if(round != null) {
         try {
            fmt = new RoundDecimalFormat(fmtstr);
            ((RoundDecimalFormat) fmt).setRoundingByName(round.toString());
         }
         catch(Exception ex) {
            LOG.warn("Failed to round number: " + fmtstr + ", " + round, ex);
         }
      }

      if(fmt == null) {
         fmt = new DecimalFormat(fmtstr);
      }

      return fmt.format(num);
   }

   /**
    * Parse a string as a date.
    */
   public static Date parseDate(String str, Object opt) {
      if(str == null || "null".equals(str)) {
         return null;
      }

      opt = unwrap(opt);

      boolean parseTime = false;
      String fmt = null;

      if(opt instanceof Boolean) {
         parseTime = (Boolean) opt;
      }
      else if(opt instanceof String) {
         fmt = (String) opt;
      }

      if(fmt != null) {
         try {
            Map<String, DateFormat> fmts = getThreadLocals().dateFormats.get();
            DateFormat datefmt = fmts.get(fmt);

            if(datefmt == null) {
               fmts.put(fmt, datefmt = Tool.createDateFormat(fmt));
            }

            return datefmt.parse(str);
         }
         catch(Exception ex) {
            // Bug #11456, format "yyyy-MMM-dd" can not support some locale, for example
            // locale china.
            try {
               return Tool.createDateFormat(fmt, Locale.US).parse(str);
            }
            catch(Exception e) {
               LOG.warn("Failed to parse date " + str + " using format: " + fmt + ": " + e);
               return null;
            }
         }
      }
      else if(!parseTime) {
         try {
            return DateFormat.getDateInstance().parse(str);
         }
         catch(Exception ex) {
            // If can't get proper format, use default format.
            try {
               DateTime dt = DateTime.parse(str, CoreTool.getDateTimeConfig());
               return dt.toDate();
            }
            catch(Exception e) {
               LOG.warn("Failed to parse date using default format: " + str + ": " + e);
               return null;
            }
         }
      }
      else {
         DateFormat df = DateFormat.getTimeInstance(DateFormat.LONG);

         try {
            return df.parse(str);
         }
         catch(Exception exl) {
            df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
            try {
               return df.parse(str);
            }
            catch(Exception exm) {
               df = DateFormat.getTimeInstance(DateFormat.SHORT);

               try {
                  return df.parse(str);
               }
               catch(Exception exs) {
                  LOG.warn("Failed to parse date using SHORT format: " + str + ": " + exs);
                  return null;
               }
            }
         }
      }
   }

   /**
    * Add an interval to a date.
    */
   public static Date dateAdd(String interval, int amount, Object date) {
      Date dateVal = getDate(date);

      if(dateVal != null) {
         Calendar cal = CoreTool.calendar.get();
         int field = getDateInterval(interval);

         if(interval.equals("q")) {
            amount *= 3;
         }

         cal.setTime(dateVal);
         cal.add(field, amount);
         return cal.getTime();
      }

      return null;
   }

   /**
    * Calculate the difference of two dates on the specified interval.
    * Available interval include:
    *    yyyy   Year
    *    q      Quarter
    *    m      Month
    *    w      Week
    *    ws     Week
    *    y      Day
    *    d      Day
    *    h      Hour
    *    n      Minute
    *    s      Second
    */
   public static double dateDiff(String interval, Object date1, Object date2) {
      Date dateVal1 = getDate(date1);
      Date dateVal2 = getDate(date2);

      if(dateVal1 != null && dateVal2 != null) {
         final ZonedDateTime zonedDate1 = Instant.ofEpochMilli(dateVal1.getTime())
            .atZone(ZoneId.systemDefault());
         final ZonedDateTime zonedDate2 = Instant.ofEpochMilli(dateVal2.getTime())
            .atZone(ZoneId.systemDefault());

         if(interval.equals("y") || interval.equals("d") || interval.equals("w")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.DAYS);
         }
         else if(interval.equals("ws") || interval.equals("ww")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.WEEKS);
         }
         else if(interval.equals("h")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.HOURS);
         }
         else if(interval.equals("n")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.MINUTES);
         }
         else if(interval.equals("s")) {
            return zonedDate1.until(zonedDate2, ChronoUnit.SECONDS);
         }
         else {
            GregorianCalendar cal1 = CoreTool.calendar.get();
            GregorianCalendar cal2 = CoreTool.calendar2.get();
            int field = getDateInterval(interval);

            cal1.setTime(dateVal1);
            cal2.setTime(dateVal2);

            if(interval.equals("yyyy")) {
               return cal2.get(field) - cal1.get(field);
            }
            else if(interval.equals("q") || interval.equals("m")) {
               int diff = cal2.get(field) - cal1.get(field);

               diff = diff + (cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR))
                  * 12;

               if(interval.equals("q")) {
                  diff /= 3;
               }

               return diff;
            }
         }
      }

      return 0;
   }

   /**
    * Extract a date interval.
    */
   public static double datePart(String interval, Object date, boolean applyWeekStart) {
      return datePartForceWeekOfMonth(interval, date, applyWeekStart, -1);
   }

   /**
    * Extract a date interval.
    */
   public static double datePartForceWeekOfMonth(String interval, Object date,
                                                 boolean applyWeekStart,
                                                 int forceDcToDateWeekOfMonth)
   {
      if(forceDcToDateWeekOfMonth > 0 && "wm".equals(interval)) {
         return forceDcToDateWeekOfMonth;
      }

      Date dateVal = getDate(date);

      if(dateVal != null) {
         Calendar cal = CoreTool.calendar.get();
         int oldStart = applyWeekStart ? cal.getFirstDayOfWeek() : 0;
         int minFirstWeek = cal.getMinimalDaysInFirstWeek();

         try {
            if(applyWeekStart) {
               cal.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
            }

            cal.setMinimalDaysInFirstWeek(7);
            cal.setTime(dateVal);

            // month of quarter
            switch(interval) {
               // month of quarter
            case "mq":
               return cal.get(Calendar.MONTH) % 3 + 1;
            // month of quarter of full week
            case "wmq":
               cal.add(Calendar.DATE, -(cal.get(Calendar.DAY_OF_WEEK) - 1));
               DateComparisonUtil.adjustCalendarByForceWM(cal, forceDcToDateWeekOfMonth);

               return cal.get(Calendar.MONTH) % 3 + 1;
            // week of quarter
            case "wq":
               return DateComparisonUtil.getWeekOfQuarter(cal, cal.getFirstDayOfWeek());
               // week of year
            case "wy":
               int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
               cal.add(Calendar.DATE, -(dayOfWeek - 1));
               int weekOfMonth = cal.get(Calendar.WEEK_OF_MONTH);

               if(DateComparisonUtil.adjustCalendarByForceWM(cal, forceDcToDateWeekOfMonth)) {
                  weekOfMonth = forceDcToDateWeekOfMonth;
               }

               return (cal.get(Calendar.MONTH) + 1) * 10 + weekOfMonth;
           // day of quarter
            case "dq":
               int endDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
               cal.add(Calendar.MONTH, -cal.get(Calendar.MONTH) % 3);
               cal.set(Calendar.DAY_OF_MONTH, 1);
               int startDayOfYear = cal.get(Calendar.DAY_OF_YEAR);

               return endDayOfYear - startDayOfYear + 1;
            case "q":
               int month = cal.get(Calendar.MONTH);
               return (month / 3) + 1;
            default:
               int field = getDateInterval(interval);

               if(field == GregorianCalendar.MONTH) {
                  return cal.get(field) + 1;
               }

               int val = cal.get(field);

               // if week-of-month is before the 1st week, it's the last week of previous month.
               if(val < 1 && "wm".equals(interval)) {
                  cal.add(Calendar.DATE, -(cal.get(Calendar.DAY_OF_WEEK) - 1));
                  val = cal.get(field);
               }

               return val;
            }
         }
         finally {
            if(applyWeekStart) {
               cal.setFirstDayOfWeek(oldStart);
            }

            cal.setMinimalDaysInFirstWeek(minFirstWeek);
         }
      }

      return 0;
   }

   /**
    * Load an image from resource. This method is called if it originates from
    * a script/expression.
    */
   public static Image getImageJS(Object img) {
      // use getImageJS only from js. (54833)
      if("true".equals(SreeEnv.getProperty("javascript.getImageFunction.disabled"))) {
         return null;
      }

      return getImage(img);
   }

   /**
    * Load an image from resource.
    */
   public static Image getImage(Object img) {
      Image image = null;
      ImageLocation iloc = null;
      img = unwrap(img);
      boolean imageavaliable = false;

      // string is a resource, or ascii encoded string
      if((img instanceof String) || (img instanceof URL)) {
         // try string as resource first
         InputStream input = null;

         try {
            if((img instanceof String) && img.toString().indexOf("://") > 0) {
               iloc = new ImageLocation(".");
               iloc.setPath(img.toString());
               iloc.setPathType(ImageLocation.IMAGE_URL);
               img = new URL((String) img);
               input = ((URL) img).openStream();
            }
            else if(img instanceof String) {
               String str = (String) img;

               if(str.isEmpty()) {
                  return null;
               }

               File file = FileSystemService.getInstance().getFile(str);

               if(file.exists()) {
                  input = new FileInputStream(file);
                  iloc = new ImageLocation(".");
                  iloc.setPath(str);
                  iloc.setPathType(ImageLocation.FULL_PATH);
                  iloc.setLastModified(file.lastModified());
               }
               else {
                  input = JavaScriptEngine.class.getResourceAsStream(str);
                  iloc = new ImageLocation(".");
                  iloc.setPath(str);
                  iloc.setPathType(ImageLocation.RESOURCE);
               }
            }
            else {
               input = ((URL) img).openStream();
            }
         }
         catch(Throwable ex) {// ignore, may not be a resource
         }

         if(input != null) {
            // @by larryl, don't create an image here (memory issue). The image
            // can be used by MetaImage on demand with proper caching
            // @by stephenwebster, For Bug #33237
            // In order to prevent access to non image data, create the image from
            // end user on demand
            if(FormulaContext.isRestricted()) {
               if(iloc != null && iloc.getPath() != null &&
                  iloc.getPath().toLowerCase().endsWith(".svg"))
               {
                  // bug #652262, ImageIO (via Tool.getImage) does not support SVG
                  try {
                     image = SVGSupport.getInstance().getSVGImage(input);
                  }
                  catch(Exception e) {
                     LOG.warn("Failed to load SVG from {}", iloc.getPath(), e);
                     image = null;
                  }
               }
               else {
                  image = Tool.getImage(input);
               }
            }

            imageavaliable = true;

            try {
               input.close();
            }
            catch(Exception ex) {
               LOG.debug("Failed to close input stream", ex);
            }
         }
         else if(img instanceof String) {
            String str = (String) img;
            // check if it's ascii hex
            boolean hex = true;
            str = Tool.replace(str, "\n", "");

            // only check the first 100 for efficiency
            for(int i = 0; i < str.length() && i < 100; i++) {
               char ch = Character.toUpperCase(str.charAt(i));

               if(!Character.isDigit(ch) && (ch < 'A' || ch > 'F')) {
                  hex = false;
                  break;
               }
            }

            byte[] buf = null;

            if(hex) {
               buf = Encoder.decodeAsciiHex(str);
            }
            else {
               buf = Encoder.decodeAscii85(str);
            }

            image = Tool.getImage(new ByteArrayInputStream(buf));
         }
      }
      else if(img instanceof byte[]) {
         image = Tool.getImage(new ByteArrayInputStream((byte[]) img));
      }
      else if(img instanceof Image) {
         image = (Image) img;
      }

      boolean success = true;

      if(image != null) {
         success = Tool.waitForImage(image);
      }
      else if(!imageavaliable && iloc == null) {
         LOG.error("Failed to load image from script: " + img);
      }

      if(iloc != null) {
         try {
            MetaImage meta = new MetaImage(iloc);
            Image image0 = image;

            // @by stephenwebster, For Bug #33237
            // In order to prevent access to non image data by the end user
            // do not return a MetaImage object
            if(!FormulaContext.isRestricted()) {
               image = meta;
            }

            if(success) {
               meta.setImage(image0);
            }

            // @by billh, fix customer bug bug1312487574948
            // for invalid image location, return null rather than meta image
            // @by gregm fix bug1335822368895, do not override the MetaImage by
            // assigning MetaImage.getImage to image.
            if(!iloc.isImageExisting()) {
               return null;
            }

            if(image != null) {
               boolean val = Tool.waitForImage(image);
               image = !val ? null : image;
            }
         }
         catch(Exception ex) {
            LOG.error(
                        "Failed to load image from location: " + iloc, ex);
         }
      }

      return image;
   }

   /**
    * Trim a string on both ends.
    */
   public static String trim(String str) {
      return str.trim();
   }

   /**
    * Trim a string on left side.
    */
   public static String ltrim(String str) {
      int idx = 0;

      for(; idx < str.length(); idx++) {
         if(!Character.isSpace(str.charAt(idx))) {
            break;
         }
      }

      return (idx > 0) ? str.substring(idx) : str;
   }

   /**
    * Trim a string on right side.
    */
   public static String rtrim(String str) {
      int idx = str.length() - 1;

      for(; idx >= 0; idx--) {
         if(!Character.isSpace(str.charAt(idx))) {
            idx++;
            break;
         }
      }

      if(idx < 0) {
         return "";
      }

      return str.substring(0, idx);
   }

   /**
    * Split a string by a delimiter.
    */
   public static String[] split(String str, Object delim, Object count) {
      delim = unwrap(delim);
      if(delim == null) {
         delim = " ";
      }

      int iCount = -1;

      count = unwrap(count);
      if(count != null) {
         iCount = ((Number) count).intValue();
      }

      return (((String) delim).length() == 1) ?
         Tool.split(str, ((String) delim).charAt(0), iCount) :
         Tool.split(str, (String) delim, iCount);
   }

   /**
    * Print a message to the log.
    */
   public static void log(Object msg) {
      msg = unwrap(msg);

      if(msg instanceof Throwable) {
         Throwable ex = (Throwable) msg;
         LOG.warn(ex.getMessage(), ex);
      }
      else {
         LOG.info(String.valueOf(msg));
      }
   }

   /**
    * Show a message to user (only works for VS).
    */
   public static void alert(Object msg, Object level) {
      msg = unwrap(msg);

      if(level == null) {
         level = MessageCommand.Type.INFO;
      }
      else if(!(level instanceof MessageCommand.Type)) {
         level = unwrap(level);

         if(level instanceof String) {
            try {
               level = MessageCommand.Type.valueOf(((String) level).toUpperCase());
            }
            catch(Exception e) {
               level = MessageCommand.Type.INFO;
            }

            if(level == MessageCommand.Type.PROGRESS) {
               level = MessageCommand.Type.INFO;
            }
         }
         else {
            level = MessageCommand.Type.INFO;
         }
      }

      final UserMessage userMessage = new UserMessage(String.valueOf(msg), ((MessageCommand.Type) level).code());
      CoreTool.addUserMessage(userMessage);
   }

   /**
    * Show a message to user (only works for VS).
    */
   public static void confirm(String msg) {
      CoreTool.addConfirmMessage(msg);
   }

   /**
    * Get a Java date object from a date or a javascript date.
    */
   public static Date getDate(Object val) {
      val = unwrap(val);

      if(val instanceof Date) {
         return (Date) val;
      }

      return null;
   }

   /**
    * Converts date interval value to calendar field.
    */
   private static int getDateInterval(String interval) {
      int field = GregorianCalendar.DAY_OF_YEAR;

      if(interval.equals("yyyy")) {
         field = GregorianCalendar.YEAR;
      }
      else if(interval.equals("q")) {
         field = GregorianCalendar.MONTH;
      }
      else if(interval.equals("m")) {
         field = GregorianCalendar.MONTH;
      }
      else if(interval.equals("y")) {
         field = GregorianCalendar.DAY_OF_YEAR;
      }
      else if(interval.equals("d")) {
         field = GregorianCalendar.DAY_OF_MONTH;
      }
      else if(interval.equals("w")) {
         field = GregorianCalendar.DAY_OF_WEEK;
      }
      else if(interval.equals("ww")) {
         field = GregorianCalendar.WEEK_OF_YEAR;
      }
      else if(interval.equals("wm")) {
         field = GregorianCalendar.WEEK_OF_MONTH;
      }
      else if(interval.equals("h")) {
         field = GregorianCalendar.HOUR;
      }
      else if(interval.equals("n")) {
         field = GregorianCalendar.MINUTE;
      }
      else if(interval.equals("s")) {
         field = GregorianCalendar.SECOND;
      }

      return field;
   }

   /**
    * Get string object from a number or a javascript number.
    */
   public static String numberToString(Object val) {
      Object nval = unwrap(val);

      if(nval instanceof Number) {
         return Tool.toString(nval);
      }

      return val == null ? null : val.toString();
   }

   /**
    * Unwrapp a wrapper.
    */
   public static Object unwrap(Object obj) {
      return ScriptUtil.unwrap(obj);
   }

   /**
    * Split string into string array.
    */
   public static String[] splitStr(Object str) {
      return JSObject.splitStr(str);
   }

   /**
    * Split an object into an array.
    */
   public static Object[] split(Object str) {
      return JSObject.split(str);
   }

   /**
    * Add a JS object to a scope's prototype chain.
    *
    * <p>TODO(cutover): Rhino prototype-chain injection is obsolete under
    * GraalJS, which resolves names through the FormulaContext scope chain
    * (see BindingRootProxy). This is now a no-op; the dynamic-library and
    * worksheet scope members are exposed through the scope objects directly.
    */
   public static void addToPrototype(Object scope, Object jsobj) {
      // no-op (see javadoc)
   }

   public static void setOnClickScript(boolean value) {
      getThreadLocals().onClickScript.set(value);
   }

   public static boolean isOnClickScript() {
      return getThreadLocals().onClickScript.get();
   }

   private static ThreadLocals getThreadLocals() {
      return ConfigurationContext.getContext().computeIfAbsent(THREAD_LOCALS, k -> new ThreadLocals());
   }

   private static SreeEnv.Value maxErrorsProperty = new SreeEnv.Value("script.max.errors", 30000);

   private static final Logger LOG = LoggerFactory.getLogger(JavaScriptEngine.class);

   private static final String THREAD_LOCALS = JavaScriptEngine.class.getName() + ".threadLocals";

   private static final class ThreadLocals {
      private final ThreadLocal<Map<Object, Integer>> scriptErrors =
         ThreadLocal.withInitial(HashMap::new);
      private final ThreadLocal<Map<String, DateFormat>> dateFormats =
         ThreadLocal.withInitial(ConcurrentHashMap::new);
      private final ThreadLocal<Boolean> onClickScript = ThreadLocal.withInitial(() -> Boolean.FALSE);
      // dedicated stack of scopes for the actively-executing script; drives
      // isScriptThread()/getExecScriptable(). Pushed/popped only around actual
      // script evaluation (see GraalJavaScriptEngine.exec).
      private final ThreadLocal<Stack<ScriptScope>> execScriptable =
         ThreadLocal.withInitial(Stack::new);
   }
}

/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util;

import inetsoft.sree.RepletRequest;
import inetsoft.uql.schema.DateValue;
import inetsoft.util.pojava.datetime.DateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The static date formats (the CoreTool format caches and thread locals, DateValue.DEFAULT and
 * the RepletRequest formats) are created when their class is loaded, so a test that changes the
 * default locale after that only reaches their format path. This test starts a JVM whose
 * default locale is set before any of them is loaded, as on a th_TH or ja_JP_JP server, and
 * checks that they format and parse Gregorian dates (Bug #77605).
 */
@Tag("core")
class GregorianStartupLocaleTest {
   @ParameterizedTest
   @ValueSource(strings = { "th-TH", "th-TH-u-nu-thai", "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void staticFormatsAreGregorianUnderStartupLocale(String tag) throws Exception {
      String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
      Process process = new ProcessBuilder(
         java, "-cp", System.getProperty("java.class.path"), "-Djava.awt.headless=true",
         "-Duser.timezone=America/New_York", Child.class.getName(), tag)
         .redirectErrorStream(true)
         .start();
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      try(InputStream in = process.getInputStream()) {
         in.transferTo(output);
      }

      assertTrue(process.waitFor(2, TimeUnit.MINUTES), tag + " did not finish");
      String text = output.toString(StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), text);
      assertTrue(text.contains(Child.DONE), text);
   }

   /**
    * Runs in the started JVM. It sets the default locale before it touches any class that
    * creates a static date format.
    */
   static final class Child {
      public static void main(String[] args) throws Exception {
         Locale.setDefault(Locale.forLanguageTag(args[0]));
         List<String> errors = new ArrayList<>();
         java.sql.Date date = new java.sql.Date(date(1994, 6, 15));
         java.sql.Timestamp ts = new java.sql.Timestamp(date(1994, 6, 15) + 10 * 3600_000L);

         // more calls than the 256 clones of each format cache
         for(int i = 0; i < 600; i++) {
            check(errors, "getDataString(date)", "1994-06-15", CoreTool.getDataString(date));
            check(errors, "getDataString(ts)", "1994-06-15 10:00:00", CoreTool.getDataString(ts));
            check(errors, "parseDate", date.getTime(), CoreTool.parseDate("1994-06-15").getTime());
            check(errors, "parseDateTime", ts.getTime(),
                  CoreTool.parseDateTime("1994-06-15 10:00:00").getTime());
         }

         check(errors, "parseDate leap day", date(1996, 2, 29),
               CoreTool.parseDate("1996-02-29").getTime());
         check(errors, "parseDate year", date(1994, 1, 1), CoreTool.parseDate("1994").getTime());
         check(errors, "dateFmt", "{d '1994-06-15'}", CoreTool.dateFmt.get().format(date));
         check(errors, "dateFmt parse", date.getTime(),
               CoreTool.dateFmt.get().parse("{d '1994-06-15'}").getTime());
         check(errors, "timeInstantFmt parse", ts.getTime(),
               CoreTool.timeInstantFmt.get().parse("{ts '1994-06-15 10:00:00'}").getTime());
         check(errors, "DateValue.DEFAULT", "1994-06-15", DateValue.DEFAULT.format(date));
         check(errors, "DateValue.DEFAULT parse", date.getTime(),
               DateValue.DEFAULT.parse("1994-06-15").getTime());
         check(errors, "two digit year", date.getTime(),
               new ExtendedDateFormat("MM/dd/yy").parse("06/15/94").getTime());
         check(errors, "pojava", date.getTime(), DateTime.parse("1994-06-15").toMillis());
         check(errors, "pojava fallback", date.getTime(),
               CoreTool.parseDate("June 15, 1994").getTime());

         // the schedule parameters are written and read with the RepletRequest formats
         RepletRequest request = new RepletRequest();
         request.setParameter("d", date);
         request.setParameter("ts", ts);
         StringWriter buf = new StringWriter();
         PrintWriter writer = new PrintWriter(buf);
         request.writeXML(writer);
         writer.flush();
         String xml = buf.toString();
         check(errors, "RepletRequest xml", true,
               xml.contains("1994-06-15") && xml.contains("1994-06-15 10:00:00"));
         RepletRequest read = new RepletRequest();
         read.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
         check(errors, "RepletRequest date", date.getTime(),
               ((Date) read.getParameter("d")).getTime());
         check(errors, "RepletRequest timestamp", ts.getTime(),
               ((Date) read.getParameter("ts")).getTime());

         if(!errors.isEmpty()) {
            System.out.println(args[0] + " " + Calendar.getInstance().getCalendarType() + ": " +
                               String.join("; ", new LinkedHashSet<>(errors)));
            System.out.flush();
            System.exit(1);
         }

         System.out.println(DONE);
         System.out.flush();
         System.exit(0);
      }

      private static void check(List<String> errors, String name, Object expected, Object actual) {
         if(!Objects.equals(expected, actual)) {
            errors.add(name + " expected " + expected + " but was " + actual);
         }
      }

      private static long date(int year, int month, int day) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.clear();
         cal.set(year, month - 1, day);
         return cal.getTimeInMillis();
      }

      static final String DONE = "GREGORIAN_STARTUP_OK";
   }
}

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
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.*;
import inetsoft.util.pojava.datetime.DateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dates are formatted and parsed in the Gregorian calendar whatever the default locale or the
 * user locale of the thread, and dates persisted in the Buddhist or Japanese calendar before
 * that are read back as the same Gregorian dates (Bug #77605).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GregorianCalendarDefaultLocaleTest {
   @BeforeEach
   void saveDefaults() {
      defaultLocale = Locale.getDefault();
      formatLocale = Locale.getDefault(Locale.Category.FORMAT);
   }

   @AfterEach
   void restoreDefaults() {
      Locale.setDefault(defaultLocale);
      Locale.setDefault(Locale.Category.FORMAT, formatLocale);
      ThreadContext.setLocale(null);
      SreeEnv.remove(COMPAT_PROPERTY);
      SreeEnv.remove(JAPANESE_COMPAT_PROPERTY);
      SreeEnv.remove("locale.available");
   }

   @ParameterizedTest
   @ValueSource(strings = { "th-TH", "th-TH-u-nu-thai", "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void extendedDateFormatIsGregorianUnderDefaultLocale(String tag) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(tag));
      Date date = date(1994, 6, 15);

      for(SimpleDateFormat fmt : new SimpleDateFormat[] {
         new ExtendedDateFormat("yyyy-MM-dd"), new ExtendedDateFormat("yyyy-MM-dd", Locale.getDefault()),
         CoreTool.createDateFormat("yyyy-MM-dd"), CoreTool.createGregorianDateFormat("yyyy-MM-dd") })
      {
         assertEquals("gregory", fmt.getCalendar().getCalendarType(), tag);
         assertEquals(date, fmt.parse("1994-06-15"), tag);
         assertEquals("1994-06-15", ascii(fmt.format(date)), tag);
      }

      // the JDK styles keep the locale pattern but use the Gregorian calendar
      for(String style : new String[] { "FULL", "LONG", "MEDIUM", "SHORT" }) {
         SimpleDateFormat fmt = CoreTool.createDateFormat(style);
         assertEquals("gregory", fmt.getCalendar().getCalendarType(), tag + " " + style);
         assertEquals(date, fmt.parse(fmt.format(date)), tag + " " + style);
      }
   }

   // the static format caches were built when CoreTool was loaded (normally under en_US), so
   // this covers their format() under a th/ja default and the thread local formats created
   // under it. GregorianStartupLocaleTest builds the static formats under a th/ja startup locale
   @ParameterizedTest
   @ValueSource(strings = { "th-TH", "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void persistentRoundTripIsGregorianUnderDefaultLocale(String tag) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(tag));
      java.sql.Date date = new java.sql.Date(date(1994, 6, 15).getTime());
      java.sql.Timestamp ts = new java.sql.Timestamp(date(1994, 6, 15).getTime());

      // more calls than the 256 clones of each format cache
      for(int i = 0; i < 600; i++) {
         assertEquals("1994-06-15", CoreTool.getDataString(date), tag);
         assertEquals("1994-06-15 00:00:00", CoreTool.getDataString(ts), tag);
         assertEquals(date.getTime(), CoreTool.parseDate("1994-06-15").getTime(), tag);
      }

      // the thread local formats are created on first use, i.e. under the default locale
      callInNewThread(() -> {
         assertEquals("{d '1994-06-15'}", CoreTool.dateFmt.get().format(date), tag);
         assertEquals(date.getTime(), CoreTool.dateFmt.get().parse("{d '1994-06-15'}").getTime());
         return null;
      });
   }

   @Test
   void thaiUserLocaleDoesNotPoisonSharedFormats() throws Exception {
      Locale.setDefault(Locale.US);
      java.sql.Date date = new java.sql.Date(date(1994, 6, 15).getTime());
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         // a th_TH user formats first on a pooled thread, through every shared format
         pool.submit(() -> {
            ThreadContext.setLocale(Catalog.parseLocale("th_TH"));

            for(int i = 0; i < 600; i++) {
               assertEquals("1994-06-15", CoreTool.getDataString(date));
            }

            assertEquals("{d '1994-06-15'}", CoreTool.dateFmt.get().format(date));
            assertEquals("1994-06-15", DateValue.DEFAULT.format(date));
            return null;
         }).get();

         // then an en user on the same pooled thread and on another thread
         pool.submit(() -> {
            ThreadContext.setLocale(Locale.US);
            assertEquals("{d '1994-06-15'}", CoreTool.dateFmt.get().format(date));
            ThreadContext.setLocale(null);
            return null;
         }).get();
      }
      finally {
         pool.shutdownNow();
      }

      for(int i = 0; i < 600; i++) {
         assertEquals("1994-06-15", CoreTool.getDataString(date));
      }

      assertEquals("1994-06-15", DateValue.DEFAULT.format(date));
      assertEquals(date.getTime(), CoreTool.parseDate("1994-06-15").getTime());
   }

   @Test
   void thaiUserDisplayFormatKeepsThaiNamesWithGregorianYear() {
      Locale.setDefault(Locale.US);
      ThreadContext.setLocale(Catalog.parseLocale("th_TH"));
      String text = new ExtendedDateFormat("d MMMM yyyy").format(date(2026, 1, 15));

      assertTrue(text.endsWith(" 2026"), text);
      assertFalse(text.contains("January"), text);
   }

   @ParameterizedTest
   @ValueSource(strings = { "th-TH", "ja-JP-u-ca-japanese", "en-US" })
   void twoDigitYearIsReadInTheGregorianWindow(String tag) throws Exception {
      Locale.setDefault(Locale.forLanguageTag(tag));
      Date expected = date(1994, 6, 15);

      assertEquals(expected, new ExtendedDateFormat("dd/MM/yy").parse("15/06/94"), tag);
      assertEquals(expected, CoreTool.createGregorianDateFormat("dd/MM/yy").parse("15/06/94"),
                   tag);

      SimpleDateFormat jdk = CoreTool.setGregorianCalendar(new SimpleDateFormat("dd/MM/yy"));
      assertEquals(expected, jdk.parse("15/06/94"), tag);
   }

   @Test
   void getGregorianLocaleKeepsGregorianLocales() {
      Locale jaJP = new Locale("ja", "JP_JP");
      Locale iso = Locale.forLanguageTag("de-DE-u-ca-iso8601");

      assertSame(Locale.US, CoreTool.getGregorianLocale(Locale.US));
      assertSame(jaJP, CoreTool.getGregorianLocale(jaJP));
      assertSame(iso, CoreTool.getGregorianLocale(iso));
      assertEquals("gregory", Calendar.getInstance(
         CoreTool.getGregorianLocale(Locale.forLanguageTag("th-TH"))).getCalendarType());
      assertEquals("gregory", Calendar.getInstance(
         CoreTool.getGregorianLocale(new Locale("ja", "JP", "JP"))).getCalendarType());
   }

   @Test
   void buddhistCompatRewritesTheYearBeforeParsing() {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");

      assertEquals("1996-02-29", CoreTool.toGregorianPersistentDate("2539-02-29"));
      assertEquals("{d '1996-02-29'}", CoreTool.toGregorianPersistentDate("{d '2539-02-29'}"));
      assertEquals("{ts '2026-01-01 10:20:30'}",
                   CoreTool.toGregorianPersistentDate("{ts '2569-01-01 10:20:30'}"));
      assertEquals("1900-01-01", CoreTool.toGregorianPersistentDate("2443-01-01"));

      // Gregorian, sentinel, BC and time values are kept
      for(String val : new String[] { "2026-01-01", "2442-12-31", "9999-12-31", "3000-01-01",
                                      "0000-01-01", "-0043-03-15", "{t '10:20:30'}", "10:20:30",
                                      "25390-01-01", "", "abc" })
      {
         assertEquals(val, CoreTool.toGregorianPersistentDate(val));
      }

      int last = LocalDate.now().getYear() + 643;
      assertEquals(last - 543 + "-01-01", CoreTool.toGregorianPersistentDate(last + "-01-01"));
      assertEquals(last + 1 + "-01-01", CoreTool.toGregorianPersistentDate(last + 1 + "-01-01"));

      // the leap day survives, which a parse followed by a year shift cannot do
      java.sql.Date date = (java.sql.Date) CoreTool.getPersistentData(XSchema.DATE, "2539-02-29");
      assertEquals(date(1996, 2, 29).getTime(), date.getTime());

      Object[] arr = (Object[]) CoreTool.getPersistentData(
         CoreTool.ARRAY, CoreTool.DATE + "~2539-02-29^" + CoreTool.DATE + "~2026-03-01");
      assertEquals(date(1996, 2, 29).getTime(), ((Date) arr[0]).getTime());
      assertEquals(date(2026, 3, 1).getTime(), ((Date) arr[1]).getTime());

      // live data is never rewritten
      Date live = (Date) CoreTool.getData(XSchema.TIME_INSTANT, "2569-01-01 00:00:00");
      assertEquals(date(2569, 1, 1).getTime(), live.getTime());
   }

   @Test
   void buddhistCompatGate() {
      Locale.setDefault(Locale.US);
      assertEquals("2569-01-01", CoreTool.toGregorianPersistentDate("2569-01-01"));

      SreeEnv.setProperty("locale.available", "en_US:th_TH");
      assertEquals("2026-01-01", CoreTool.toGregorianPersistentDate("2569-01-01"));

      SreeEnv.setProperty(COMPAT_PROPERTY, "false");
      assertEquals("2569-01-01", CoreTool.toGregorianPersistentDate("2569-01-01"));

      SreeEnv.remove("locale.available");
      SreeEnv.setProperty(COMPAT_PROPERTY, "auto");
      Locale.setDefault(Locale.forLanguageTag("th-TH"));
      assertEquals("2026-01-01", CoreTool.toGregorianPersistentDate("2569-01-01"));

      SreeEnv.setProperty(COMPAT_PROPERTY, "false");
      assertEquals("2569-01-01", CoreTool.toGregorianPersistentDate("2569-01-01"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void japaneseCompatReadsTheCurrentEra(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      // the year before Reiwa 1, the era the Japanese calendar read era-less years in
      int offset = 2018;

      assertEquals(String.format("%04d-06-15", 6 + offset),
                   CoreTool.toGregorianPersistentDate("0006-06-15"));
      assertEquals(String.format("{d '%04d-01-01'}", 64 + offset),
                   CoreTool.toGregorianPersistentDate("{d '0064-01-01'}"));
      assertEquals("0065-01-01", CoreTool.toGregorianPersistentDate("0065-01-01"));
      assertEquals("0000-01-01", CoreTool.toGregorianPersistentDate("0000-01-01"));
      assertEquals("-0043-03-15", CoreTool.toGregorianPersistentDate("-0043-03-15"));
      assertEquals("1994-06-15", CoreTool.toGregorianPersistentDate("1994-06-15"));

      // Reiwa 6 is the leap year 2024
      java.sql.Date date = (java.sql.Date) CoreTool.getPersistentData(XSchema.DATE, "0006-02-29");
      assertEquals(date(2024, 2, 29).getTime(), date.getTime());

      Locale.setDefault(Locale.US);
      assertEquals("0006-06-15", CoreTool.toGregorianPersistentDate("0006-06-15"));
   }

   // a Gregorian date saved since the fix that is not a valid Reiwa date is kept (Bug #78114)
   @ParameterizedTest
   @ValueSource(strings = { "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void japaneseCompatKeepsDatesThatAreNotReiwa(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      assertEquals("japanese", Calendar.getInstance().getCalendarType(), tag);

      // Reiwa started on 2019-05-01
      assertEquals("0001-01-01", CoreTool.toGregorianPersistentDate("0001-01-01"));
      assertEquals("{d '0001-01-01'}", CoreTool.toGregorianPersistentDate("{d '0001-01-01'}"));
      assertEquals("{ts '0001-01-01 00:00:00'}",
                   CoreTool.toGregorianPersistentDate("{ts '0001-01-01 00:00:00'}"));
      assertEquals("0001-01-01 00:00:00",
                   CoreTool.toGregorianPersistentDate("0001-01-01 00:00:00"));
      assertEquals("0001-04-30", CoreTool.toGregorianPersistentDate("0001-04-30"));
      assertEquals("2019-05-01", CoreTool.toGregorianPersistentDate("0001-05-01"));
      assertEquals("{ts '2019-05-01 00:00:00'}",
                   CoreTool.toGregorianPersistentDate("{ts '0001-05-01 00:00:00'}"));
      // Reiwa 4 is not a leap year
      assertEquals("0004-02-29", CoreTool.toGregorianPersistentDate("0004-02-29"));
      // a legacy value without a month and day is read by its year
      assertEquals("2026", CoreTool.toGregorianPersistentDate("0008"));

      // a save after the read writes the same date back
      Object date = CoreTool.getPersistentData(XSchema.DATE, "0001-01-01");
      assertEquals("0001-01-01", CoreTool.getPersistentDataString(date));
      Object ts = CoreTool.getPersistentData(XSchema.TIME_INSTANT, "0001-01-01 00:00:00");
      assertEquals("0001-01-01 00:00:00", CoreTool.getPersistentDataString(ts));
   }

   @ParameterizedTest
   @ValueSource(strings = { "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void japaneseCompatFalseKeepsTheYear(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));
      SreeEnv.setProperty(JAPANESE_COMPAT_PROPERTY, "false");

      assertEquals("0050-01-01", CoreTool.toGregorianPersistentDate("0050-01-01"));
      assertEquals("0008-01-15", CoreTool.toGregorianPersistentDate("0008-01-15"));
      assertEquals("{d '0008-01-15'}", CoreTool.toGregorianPersistentDate("{d '0008-01-15'}"));

      SreeEnv.setProperty(JAPANESE_COMPAT_PROPERTY, "auto");
      assertEquals("2026-01-15", CoreTool.toGregorianPersistentDate("0008-01-15"));
   }

   @Test
   void japaneseCompatTrueReadsReiwaOnAGregorianJvm() {
      Locale.setDefault(Locale.US);
      assertEquals("0008-01-15", CoreTool.toGregorianPersistentDate("0008-01-15"));

      SreeEnv.setProperty(JAPANESE_COMPAT_PROPERTY, "true");
      assertEquals("2026-01-15", CoreTool.toGregorianPersistentDate("0008-01-15"));
      assertEquals("{ts '2026-01-15 13:00:00'}",
                   CoreTool.toGregorianPersistentDate("{ts '0008-01-15 13:00:00'}"));
      // the era check still applies
      assertEquals("0001-01-01", CoreTool.toGregorianPersistentDate("0001-01-01"));
   }

   @Test
   void plainJapaneseLocaleIsGregorian() {
      Locale.setDefault(Locale.forLanguageTag("ja-JP"));
      assertEquals("gregory", Calendar.getInstance().getCalendarType());

      assertEquals("0008-01-15", CoreTool.toGregorianPersistentDate("0008-01-15"));
      assertEquals("0001-05-01", CoreTool.toGregorianPersistentDate("0001-05-01"));
      assertEquals("0001-01-01", CoreTool.toGregorianPersistentDate("0001-01-01"));
   }

   @Test
   void legacyBuddhistConditionValueIsRead() throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      Condition cond = new Condition(XSchema.DATE);
      cond.addValue(new java.sql.Date(date(1996, 2, 29).getTime()));
      String xml = toXML(cond::writeXML);
      // the value is written byte encoded
      String value = Tool.byteEncode("{d '1996-02-29'}", true);

      assertTrue(xml.contains(value), xml);
      Condition legacy = new Condition();
      legacy.parseXML(parse(xml.replace(value, Tool.byteEncode("{d '2539-02-29'}", true)))
                         .getDocumentElement());

      assertEquals(date(1996, 2, 29).getTime(), ((Date) legacy.getValue(0)).getTime());
   }

   @Test
   void legacyBuddhistConditionArrayItemIsRead() throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      Condition cond = new Condition(XSchema.DATE);
      cond.addValue(new Object[] { new java.sql.Date(date(1996, 2, 29).getTime()) });
      String xml = toXML(cond::writeXML);

      assertTrue(xml.contains("{d '1996-02-29'}"), xml);
      Condition legacy = new Condition();
      legacy.parseXML(parse(xml.replace("1996-02-29", "2539-02-29")).getDocumentElement());

      Object[] items = (Object[]) legacy.getValue(0);
      assertEquals("{d '1996-02-29'}", items[0]);
   }

   @Test
   void legacyBuddhistVariableValueStringIsRead() throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      UserVariable var = new UserVariable("v");
      var.setTypeNode(XSchema.createPrimitiveType(XSchema.DATE));
      var.setValueNode(XValueNode.createValueNode(
         new java.sql.Date(date(1996, 2, 29).getTime()), "v", XSchema.DATE));
      String xml = toXML(var::writeXML);

      // a file written without the value node, read through the valueString fallback
      xml = xml.replaceAll("(?s)<valuenode.*?</valuenode>", "").replace("1996-02-29", "2539-02-29");
      assertTrue(xml.contains("<valueString><![CDATA[2539-02-29]]>"), xml);
      UserVariable legacy = new UserVariable();
      legacy.parseXML(parse(xml).getDocumentElement());

      assertEquals(date(1996, 2, 29).getTime(), ((Date) legacy.getValueNode().getValue()).getTime());
   }

   @Test
   void legacyBuddhistScheduleParameterIsRead() throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      RepletRequest request = new RepletRequest();
      request.setParameter("d", new java.sql.Date(date(1996, 2, 29).getTime()));
      long ts = date(2026, 1, 1).getTime() + 10 * 3600_000L;
      request.setParameter("ts", new java.sql.Timestamp(ts));
      String xml = toXML(request::writeXML);

      assertTrue(xml.contains("1996-02-29") && xml.contains("2026-01-01 10:00:00"), xml);
      RepletRequest legacy = new RepletRequest();
      legacy.parseXML(parse(xml.replace("1996-02-29", "2539-02-29")
                                  .replace("2026-01-01 10:00:00", "2569-01-01 10:00:00"))
                         .getDocumentElement());

      assertEquals(date(1996, 2, 29).getTime(), ((Date) legacy.getParameter("d")).getTime());
      assertEquals(ts, ((Date) legacy.getParameter("ts")).getTime());
   }

   @Test
   void legacyBuddhistVariableDefaultIsRead() throws Exception {
      SreeEnv.setProperty(COMPAT_PROPERTY, "true");
      XValueNode node = XValueNode.createValueNode(
         new java.sql.Date(date(1996, 2, 29).getTime()), "v", XSchema.DATE);
      String xml = toXML(node::writeXML);

      assertTrue(xml.contains("1996-02-29"), xml);
      XValueNode legacy = XValueNode.createValueNode(
         parse(xml.replace("1996-02-29", "2539-02-29")).getDocumentElement());

      assertEquals(date(1996, 2, 29).getTime(), ((Date) legacy.getValue()).getTime());
   }

   @ParameterizedTest
   @ValueSource(strings = { "th-TH", "ja-JP-u-ca-japanese", "ja-JP-x-lvariant-JP" })
   void pojavaParsesGregorianUnderDefaultLocale(String tag) {
      Locale.setDefault(Locale.forLanguageTag(tag));

      assertEquals(date(1994, 6, 15).getTime(), DateTime.parse("1994-06-15").toMillis(), tag);
      assertEquals(date(1996, 2, 29).getTime(), DateTime.parse("1996-02-29").toMillis(), tag);
      assertEquals(date(1994, 6, 15).getTime(),
                   DateTime.parse("1994-06-15", CoreTool.getDateTimeConfig()).toMillis(), tag);
      assertEquals(date(2026, 1, 15).getTime(),
                   DateTime.parse("1994-06-15").shift(inetsoft.util.pojava.datetime.CalendarUnit.MONTH, 379)
                      .truncate(inetsoft.util.pojava.datetime.CalendarUnit.MONTH)
                      .shift(inetsoft.util.pojava.datetime.CalendarUnit.DAY, 14).toMillis(), tag);
   }

   private static Date date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTime();
   }

   // th-TH-u-nu-thai formats Thai digits through SimpleDateFormat, which is about the digits
   // and not the calendar
   private static String ascii(String text) {
      StringBuilder buf = new StringBuilder();

      for(char c : text.toCharArray()) {
         buf.append(Character.isDigit(c) ? (char) ('0' + Character.digit(c, 10)) : c);
      }

      return buf.toString();
   }

   private static String toXML(java.util.function.Consumer<PrintWriter> writer) {
      StringWriter buf = new StringWriter();
      PrintWriter out = new PrintWriter(buf);
      writer.accept(out);
      out.flush();
      return buf.toString();
   }

   private static Document parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml));
   }

   private static <T> T callInNewThread(Callable<T> fn) throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor();

      try {
         return pool.submit(fn).get();
      }
      finally {
         pool.shutdownNow();
      }
   }

   private static final String COMPAT_PROPERTY = "date.legacy.buddhist.compat";
   private static final String JAPANESE_COMPAT_PROPERTY = "date.legacy.japanese.compat";
   private Locale defaultLocale;
   private Locale formatLocale;
}

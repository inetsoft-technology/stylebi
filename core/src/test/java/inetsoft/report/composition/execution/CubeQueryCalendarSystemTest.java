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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.XMetaInfo;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77598: an as-date XMLA level without a database locale parses the member captions of the
 * OLAP server as Gregorian dates whatever the calendar system of the JVM default locale is
 * (Buddhist for th_TH, Japanese imperial for ja_JP_JP). An explicit database locale keeps its
 * own calendar. The runtime (CubeQuery) and the data source preview (XmlaDatasourceService)
 * must agree.
 *
 * Bug #77876: a two-digit year (MM/dd/yy) must also read as Gregorian 2026, not 2526 (th_TH) or
 * 26 (ja_JP_JP) from a two-digit-year window computed in the default locale's calendar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CubeQueryCalendarSystemTest {
   private static final Locale TH = Locale.of("th", "TH");
   private static final Locale JA = Locale.of("ja", "JP", "JP");

   private Locale oldLocale;

   @BeforeEach
   void saveLocale() {
      oldLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(oldLocale);
   }

   @Test
   void cubeQueryParsesGregorianWithoutLocale() throws Exception {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         Locale.setDefault(locale);
         assertEquals(2026, parseYear(cubeQueryFormat(createInfo(null))),
                      "default locale " + locale);
      }
   }

   @Test
   void previewParsesGregorianWithoutLocale() throws Exception {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         Locale.setDefault(locale);
         assertEquals(2026, parseYear(previewFormat(createInfo(null))),
                      "default locale " + locale);
      }
   }

   @Test
   void cubeQueryParsesTwoDigitYearGregorianWithoutLocale() throws Exception {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         try {
            Locale.setDefault(locale);
            assertEquals(2026, parseYear(cubeQueryFormat(createInfo(null, "MM/dd/yy")),
                                         "01/15/26"), "default locale " + locale);
         }
         finally {
            Locale.setDefault(oldLocale);
         }
      }
   }

   @Test
   void previewParsesTwoDigitYearGregorianWithoutLocale() throws Exception {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         try {
            Locale.setDefault(locale);
            assertEquals(2026, parseYear(previewFormat(createInfo(null, "MM/dd/yy")),
                                         "01/15/26"), "default locale " + locale);
         }
         finally {
            Locale.setDefault(oldLocale);
         }
      }
   }

   // an explicit Thai database locale means the captions use the Buddhist calendar
   @Test
   void explicitThaiLocaleKeepsBuddhistCalendar() throws Exception {
      Locale.setDefault(Locale.US);
      // 2026 in the Buddhist calendar is Gregorian 1483
      assertEquals(1483, parseYear(cubeQueryFormat(createInfo(TH))));
      assertEquals(1483, parseYear(previewFormat(createInfo(TH))));
   }

   private static XMetaInfo createInfo(Locale locale) {
      return createInfo(locale, "yyyy-MM-dd");
   }

   private static XMetaInfo createInfo(Locale locale, String pattern) {
      XMetaInfo minfo = new XMetaInfo();
      minfo.setAsDate(true);
      minfo.setDatePattern(pattern);
      minfo.setLocale(locale);
      return minfo;
   }

   private static SimpleDateFormat cubeQueryFormat(XMetaInfo minfo) throws Exception {
      Method method = CubeQuery.class.getDeclaredMethod("createDateFormat", XMetaInfo.class);
      method.setAccessible(true);
      return (SimpleDateFormat) method.invoke(null, minfo);
   }

   private static SimpleDateFormat previewFormat(XMetaInfo minfo) throws Exception {
      XmlaDatasourceService service =
         new XmlaDatasourceService(null, null, null, null, null, null, null);
      Method method =
         XmlaDatasourceService.class.getDeclaredMethod("createParseDateFormat", XMetaInfo.class);
      method.setAccessible(true);
      return (SimpleDateFormat) method.invoke(service, minfo);
   }

   // read the year of the parsed date in the Gregorian calendar, never through a formatter of
   // the default locale, which would hide a calendar shift
   private static int parseYear(SimpleDateFormat format) throws Exception {
      return parseYear(format, "2026-01-01");
   }

   private static int parseYear(SimpleDateFormat format, String text) throws Exception {
      Date date = format.parse(text);
      Calendar calendar = new GregorianCalendar();
      calendar.setTime(date);
      return calendar.get(Calendar.YEAR);
   }
}

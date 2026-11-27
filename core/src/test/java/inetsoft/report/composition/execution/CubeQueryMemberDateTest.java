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
import inetsoft.uql.xmla.MemberObject;
import inetsoft.web.portal.service.datasource.XmlaDatasourceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82471: an as-date XMLA level with a date pattern must be parsed from the member caption.
 * For SQL Server and Mondrian typed cubes the full caption of a member is a bracketed
 * unique-name path ("[1997]", "[26].[01/26].[01/15/26]") that no date pattern can parse. Both
 * the worksheet runtime (CubeQuery.parseDate, shared by the column read and by the equal,
 * greater, less and between conditions) and the data source preview must agree.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CubeQueryMemberDateTest {
   @Test
   void yearLevelParsesCaptionNotPath() throws Exception {
      SimpleDateFormat fmt = format("yyyy");
      MemberObject m = member("[Time].[1997]", "1997", "[1997]");
      assertEquals(date(1997, 1, 1), CubeQuery.parseDate(m, fmt));
      assertEquals(date(1997, 1, 1), previewCell(m, fmt));
   }

   @Test
   void dayLevelParsesCaptionNotPath() throws Exception {
      SimpleDateFormat fmt = format("MM/dd/yy");
      MemberObject m = member("[Time].[26].[01/26].[01/15/26]", "01/15/26",
                              "[26].[01/26].[01/15/26]");
      assertEquals(date(2026, 1, 15), CubeQuery.parseDate(m, fmt));
      assertEquals(date(2026, 1, 15), previewCell(m, fmt));
   }

   @Test
   void twoDigitYearWindow() throws Exception {
      SimpleDateFormat fmt = format("MM/dd/yy");
      assertEquals(date(1999, 12, 31),
                   CubeQuery.parseDate(member("[a]", "12/31/99", "[x].[12/31/99]"), fmt));
      assertEquals(date(2045, 7, 4),
                   CubeQuery.parseDate(member("[a]", "07/04/45", "[x].[07/04/45]"), fmt));
   }

   // the equal, greater, less and between conditions compare through the same parseDate
   @Test
   void rangeConditionComparisonsUseCaption() throws Exception {
      SimpleDateFormat fmt = format("MM/dd/yy");
      Date lo = CubeQuery.parseDate(
         member("[a].[01/01/26]", "01/01/26", "[y].[01/01/26]"), fmt);
      Date hi = CubeQuery.parseDate(
         member("[a].[01/31/26]", "01/31/26", "[y].[01/31/26]"), fmt);
      Date mid = CubeQuery.parseDate(
         member("[a].[01/15/26]", "01/15/26", "[y].[01/15/26]"), fmt);

      assertNotNull(lo);
      assertNotNull(hi);
      assertNotNull(mid);
      assertTrue(lo.compareTo(mid) < 0 && mid.compareTo(hi) < 0);
      // a condition value given as plain text or as a date
      assertEquals(mid, CubeQuery.parseDate("01/15/26", fmt));
      assertEquals(mid, CubeQuery.parseDate(new Date(mid.getTime()), fmt));
   }

   @Test
   void membersWithoutCaptionAndNullSafe() throws Exception {
      SimpleDateFormat fmt = format("yyyy");
      // generic handler, originalContent=false: full caption is the plain caption
      assertEquals(date(1997, 1, 1), CubeQuery.parseDate(new MemberObject("[a]", "1997"), fmt));
      // no caption, bracketed full caption
      assertEquals(date(1997, 1, 1),
                   CubeQuery.parseDate(member("[Time].[1997]", null, "[Time].[1997]"), fmt));
      // only the unique name
      assertEquals(date(1997, 1, 1), CubeQuery.parseDate(new MemberObject("[Time].[1997]"), fmt));
      // nothing to parse, no exception
      assertNull(CubeQuery.parseDate(new MemberObject(), fmt));
      assertNull(CubeQuery.parseDate(null, fmt));
      assertEquals("", previewCell(new MemberObject(), fmt));
   }

   @Test
   void unparsableCaptionStillEmpty() throws Exception {
      SimpleDateFormat fmt = format("yyyy");
      MemberObject m = member("[a]", "Q1 1997", "[x].[Q1 1997]");
      assertNull(CubeQuery.parseDate(m, fmt));
      assertEquals("", previewCell(m, fmt));
   }

   private static SimpleDateFormat format(String pattern) throws Exception {
      XMetaInfo minfo = new XMetaInfo();
      minfo.setAsDate(true);
      minfo.setDatePattern(pattern);
      Method method = CubeQuery.class.getDeclaredMethod("createDateFormat", XMetaInfo.class);
      method.setAccessible(true);
      return (SimpleDateFormat) method.invoke(null, minfo);
   }

   private static Object previewCell(Object data, SimpleDateFormat fmt) throws Exception {
      Method method = XmlaDatasourceService.class.getDeclaredMethod(
         "convertDateCell", Object.class, SimpleDateFormat.class);
      method.setAccessible(true);
      return method.invoke(null, data, fmt);
   }

   private static MemberObject member(String uName, String caption, String fullCaption)
      throws Exception
   {
      MemberObject m = new MemberObject(uName, fullCaption);
      Field f = MemberObject.class.getDeclaredField("caption");
      f.setAccessible(true);
      f.set(m, caption);
      return m;
   }

   private static Date date(int year, int month, int day) {
      return new GregorianCalendar(year, month - 1, day).getTime();
   }
}

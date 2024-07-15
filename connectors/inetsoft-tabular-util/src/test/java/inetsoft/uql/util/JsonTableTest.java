/*
 * inetsoft-tabular-util - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.ObjectWrapper;
import org.junit.jupiter.api.*;
import org.pojava.datetime.DateTime;

import java.io.File;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JsonTableTest {
   @BeforeAll
   public static void setSreeHome() {
      File home = new File("build/sreeHome");

      if(!home.isDirectory() && !home.mkdirs()) {
         throw new RuntimeException("Failed to create test sree.home: " + home);
      }

      System.setProperty("sree.home", home.getAbsolutePath());
   }

   @Test
   public void testSimple() {
      String json = "[{\"state\": \"NJ\", \"value\": 20}, {\"state\": \"NY\", \"value\": 30}]";
      XNodeTable tbl = new XNodeTable(new JsonTable(json, 0));
      tbl.moreRows(999);

      assertEquals(tbl.getColCount(), 2);
      assertEquals(tbl.getRowCount(), 3);
      assertEquals("NJ", tbl.getObject(1, 0));
      assertEquals(20.0, tbl.getObject(1, 1));
      assertEquals("NY", tbl.getObject(2, 0));
      assertEquals(30.0, tbl.getObject(2, 1));
   }

   @Test
   public void testNested() {
      String json = "[{\"addr\": {\"state\": \"NJ\", \"zip\": \"08854\"}, \"value\": 20}, {\"addr\": {\"state\": \"NY\", \"zip\": \"10021\"}, \"value\": 30}]";
      XNodeTable tbl = new XNodeTable(new JsonTable(json, 0));
      tbl.moreRows(999);

      assertEquals(tbl.getColCount(), 3);
      assertEquals(tbl.getRowCount(), 3);
      assertEquals("addr.state", tbl.getObject(0, 0));
      assertEquals("addr.zip", tbl.getObject(0, 1));
      assertEquals("NJ", tbl.getObject(1, 0));
      assertEquals("08854", tbl.getObject(1, 1));
      assertEquals(20.0, tbl.getObject(1, 2));
      assertEquals("NY", tbl.getObject(2, 0));
      assertEquals("10021", tbl.getObject(2, 1));
      assertEquals(30.0, tbl.getObject(2, 2));
   }

   @Test
   public void testMixed() {
      String json = "[{\"state\": \"NJ\", \"value\": 20}, {\"city\": \"NYC\", \"value\": 30}]";
      XNodeTable tbl = new XNodeTable(new JsonTable(json, 0));
      tbl.moreRows(999);

      assertEquals(tbl.getColCount(), 3);
      assertEquals(tbl.getRowCount(), 3);

      assertEquals("state", tbl.getObject(0, 0));
      assertEquals("value", tbl.getObject(0, 1));
      assertEquals("city", tbl.getObject(0, 2));
      assertEquals("NJ", tbl.getObject(1, 0));
      assertEquals(20.0, tbl.getObject(1, 1));
      assertNull(tbl.getObject(1, 2));
      assertNull(tbl.getObject(2, 0));
      assertEquals(30.0, tbl.getObject(2, 1));
      assertEquals("NYC", tbl.getObject(2, 2));
   }

//   @Test
   public void testParsing() {
      String json = "[{\"timeStamp\": \"2012-04-23T18:25:43.511Z\", \"dateOnly\": \"2019-03-31\", \"dateFmt\": \"4/14/2019\", \"intQuote\": \"3\", \"intNull\": 5, \"intFmt\": \"1,234\", \"doubleGood\": 1.414, \"doubleNull\": 2.71828, \"doubleQuote\": \"1.234\"}, {\"timeStamp\": \"2019-08-19T16:27:10.123Z\", \"dateOnly\": \"2019-08-18\", \"dateFmt\": \"5/21/2018\", \"intNull\": null, \"intQuote\": \"4\", \"intFmt\": \"9,876\", \"doubleQuote\": \"3.14159\", \"doubleGood\": 1.01}]";
      XNodeTable tbl = new XNodeTable(new JsonTable(json, 999));

      tbl.moreRows(999);
      //inetsoft.report.internal.Util.printTable(tbl);
      assertEquals(DateTime.parse("2012-04-23 18:25:43.511Z").toTimestamp(), tbl.getObject(1, 0));
      assertEquals(new java.sql.Date(DateTime.parse("2019-03-31 00:00:00.0").toMillis()), tbl.getObject(1, 1));
      assertEquals(DateTime.parse("2019-04-14 00:00:00.0").toDate(), tbl.getObject(1, 2));
      assertEquals("3", tbl.getObject(1, 3));
   }

//   @Test
   @Disabled("Doesn't work on the build server")
   public void testFormat() {
      String json = "[{\"datestr\": \"20120423\", \"amount\": \"$123\"}]";
      JsonTable jsonTbl = new JsonTable();
      jsonTbl.setColumnType("datestr", XSchema.DATE);
      jsonTbl.setColumnFormat("datestr", "DateFormat");
      jsonTbl.setColumnFormatExtent("datestr", "yyyyMMdd");
      jsonTbl.setColumnType("amount", XSchema.DOUBLE);
      jsonTbl.setColumnFormat("amount", "CurrencyFormat");
      jsonTbl.load(json);
      XNodeTable tbl = new XNodeTable(jsonTbl);

      tbl.moreRows(999);
      //inetsoft.report.internal.Util.printTable(tbl);
      assertEquals(DateTime.parse("2012-04-23").toDate(), tbl.getObject(1, 0));
      assertEquals(123.0, tbl.getObject(1, 1));
   }

   @Test
   public void nullColumnDefaultsToStringType() {
      String json = "[{\"null column\": null}]";
      final JsonTable jsonTable = new JsonTable(json, 0);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertNull(tbl.getObject(1, 0));
      assertEquals(String.class, tbl.getColType(0));
      assertEquals(XSchema.STRING, jsonTable.getColumnType("null column"));
   }

   @Test
   public void stringValueAfterDateValueIsParsedCorrectly() {
      String json = "[{\"col\": \"12/29/2022\"}, {\"col\": \"test\"}]";
      final JsonTable jsonTable = new JsonTable(json, 0);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertEquals("test", tbl.getObject(2, 0));
      assertEquals(String.class, tbl.getColType(0));
      assertEquals(XSchema.STRING, jsonTable.getColumnType("col"));
   }

   @Test
   public void parsesObjectWrapper() {
      Object value = 123;
      final Map<String, Object> map = new HashMap<>();
      map.put("obj", new ObjectWrapper() {
         @Override
         public Object unwrap() {
            return 123;
         }
      });

      final JsonTable jsonTable = new JsonTable(map, 0);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertEquals(value, tbl.getObject(1, 0));
      assertEquals(XSchema.INTEGER, jsonTable.getColumnType("obj"));
   }

   @Test
   public void booleanType() {
      String json = "[{\"col\": true}, {\"col\": false}]";
      final JsonTable jsonTable = new JsonTable(json, 0);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertEquals(true, tbl.getObject(1, 0));
      assertEquals(false, tbl.getObject(2, 0));
      assertEquals(Boolean.class, tbl.getColType(0));
      assertEquals(XSchema.BOOLEAN, jsonTable.getColumnType("col"));
   }

   @Test
   public void timeType() {
      typeParsing("\"01:02:03\"", java.sql.Time.valueOf(LocalTime.of(1, 2, 3)), java.sql.Time.class);
      typeParsing("\"1:02:03\"", java.sql.Time.valueOf(LocalTime.of(1, 2, 3)), java.sql.Time.class);
      typeParsing("\"1:2:3\"", "1:2:3", String.class);
      typeParsing("\"1:2\"", "1:2", String.class);
   }

   private void typeParsing(String str, Object value, Class<?> type) {
      final String json = "[{\"col\": " + str + " }]";
      final JsonTable jsonTable = new JsonTable(json, 0);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertEquals(value, tbl.getObject(1, 0));
      assertEquals(type, tbl.getColType(0));
   }
}

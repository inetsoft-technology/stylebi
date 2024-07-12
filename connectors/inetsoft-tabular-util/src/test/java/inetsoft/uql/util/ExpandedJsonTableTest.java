/*
 * inetsoft-tabular-util - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.util;

import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pojava.datetime.DateTime;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExpandedJsonTableTest {
   @BeforeAll
   public static void setSreeHome() {
      File home = new File("build/sreeHome");

      if(!home.isDirectory() && !home.mkdirs()) {
         throw new RuntimeException("Failed to create test sree.home: " + home);
      }

      System.setProperty("sree.home", home.getAbsolutePath());
   }

   @Test
   public void testMixed() {
      String json = "[\n" +
         "  {\n" +
         "    \"state\": \"NJ\",\n" +
         "    \"value\": 20\n" +
         "  },\n" +
         "  {\n" +
         "    \"city\": \"NYC\",\n" +
         "    \"value\": 30\n" +
         "  }\n" +
         "]";
      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, null));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "state", "value", "city" },
         { "NJ", 20D, null },
         { null, 30D, "NYC" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);
   }

//   @Test
   public void testParsing() {
      String json = "[\n" +
         "  {\n" +
         "    \"timeStamp\": \"2012-04-23T18:25:43.511Z\",\n" +
         "    \"dateOnly\": \"2019-03-31\",\n" +
         "    \"dateFmt\": \"4/14/2019\",\n" +
         "    \"intQuote\": \"3\",\n" +
         "    \"intNull\": 5,\n" +
         "    \"intFmt\": \"1,234\",\n" +
         "    \"doubleGood\": 1.414,\n" +
         "    \"doubleNull\": 2.71828,\n" +
         "    \"doubleQuote\": \"1.234\"\n" +
         "  },\n" +
         "  {\n" +
         "    \"timeStamp\": \"2019-08-19T16:27:10.123Z\",\n" +
         "    \"dateOnly\": \"2019-08-18\",\n" +
         "    \"dateFmt\": \"5/21/2018\",\n" +
         "    \"intNull\": null,\n" +
         "    \"intQuote\": \"4\",\n" +
         "    \"intFmt\": \"9,876\",\n" +
         "    \"doubleQuote\": \"3.14159\",\n" +
         "    \"doubleGood\": 1.01\n" +
         "  }\n" +
         "]";
      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, null));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "timeStamp", "dateOnly", "dateFmt", "intQuote", "intNull", "intFmt", "doubleGood", "doubleNull", "doubleQuote" },
         { DateTime.parse("2012-04-23 18:25:43.511Z").toTimestamp(), DateTime.parse("2019-03-31 00:00:00.0").toDate(), DateTime.parse("2019-04-14 00:00:00.0").toDate(), "3", 5D, "1,234", 1.414D, 2.71828D, "1.234" },
         { DateTime.parse("2019-08-19 16:27:10.123Z").toTimestamp(), DateTime.parse("2019-08-18 00:00:00.0").toDate(), DateTime.parse("2018-05-21 00:00:00.0").toDate(), "4", null, "9,876", 1.01D, null, "3.14159" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);
   }

   @Test
   public void testParsingWithPath() {
      String json = "[\n" +
         "  {\n" +
         "    \"name\": \"Object 1\",\n" +
         "    \"list1\": [ \"Item 1.1.1\", \"Item 1.1.2\" ],\n" +
         "    \"list2\": [ \"Item 1.2.1\", \"Item 1.2.2\" ]\n" +
         "  },\n" +
         "  {\n" +
         "    \"name\": \"Object 2\",\n" +
         "    \"list1\": [ \"Item 2.1.1\", \"Item 2.1.2\" ],\n" +
         "    \"list2\": [ \"Item 2.2.1\", \"Item 2.2.2\" ]\n" +
         "  }\n" +
         "]";

      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, "list1"));
      DefaultTable expected = new DefaultTable(new Object[][]{
         { "name", "list2", "list1" },
         { "Object 1", "[\"Item 1.2.1\",\"Item 1.2.2\"]", "Item 1.1.1" },
         { "Object 1", "[\"Item 1.2.1\",\"Item 1.2.2\"]", "Item 1.1.2" },
         { "Object 2", "[\"Item 2.2.1\",\"Item 2.2.2\"]", "Item 2.1.1" },
         { "Object 2", "[\"Item 2.2.1\",\"Item 2.2.2\"]", "Item 2.1.2" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);

      actual = new XNodeTable(new ExpandedJsonTable(json, "list2"));
      expected = new DefaultTable(new Object[][]{
         { "name", "list1", "list2" },
         { "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "Item 1.2.1" },
         { "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "Item 1.2.2" },
         { "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "Item 2.2.1" },
         { "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "Item 2.2.2" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);

      actual = new XNodeTable(new ExpandedJsonTable(json, null));
      expected = new DefaultTable(new Object[][]{
         { "name", "list1", "list2" },
         { "Object 1", "Item 1.1.1", "Item 1.2.1" },
         { "Object 1", "Item 1.1.2", "Item 1.2.2" },
         { "Object 2", "Item 2.1.1", "Item 2.2.1" },
         { "Object 2", "Item 2.1.2", "Item 2.2.2" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);

      json = "{\n" +
         "  \"id\": 1,\n" +
         "  \"items\": [\n" +
         "    {\n" +
         "      \"id\": 1,\n" +
         "      \"name\": \"Object 1\",\n" +
         "      \"list1\": [ \"Item 1.1.1\", \"Item 1.1.2\" ],\n" +
         "      \"list2\": [ \"Item 1.2.1\", \"Item 1.2.2\" ]\n" +
         "    },\n" +
         "    {\n" +
         "      \"id\": 2,\n" +
         "      \"name\": \"Object 2\",\n" +
         "      \"list1\": [ \"Item 2.1.1\", \"Item 2.1.2\" ],\n" +
         "      \"list2\": [ \"Item 2.2.1\", \"Item 2.2.2\" ]\n" +
         "    }\n" +
         "  ]\n" +
         "}";

      actual = new XNodeTable(new ExpandedJsonTable(json, "items.list1"));
      expected = new DefaultTable(new Object[][]{
         { "id", "items.id", "items.name", "items.list2", "items.list1" },
         { 1D, 1D, "Object 1", "[\"Item 1.2.1\",\"Item 1.2.2\"]", "Item 1.1.1" },
         { 1D, 1D, "Object 1", "[\"Item 1.2.1\",\"Item 1.2.2\"]", "Item 1.1.2" },
         { 1D, 2D, "Object 2", "[\"Item 2.2.1\",\"Item 2.2.2\"]", "Item 2.1.1" },
         { 1D, 2D, "Object 2", "[\"Item 2.2.1\",\"Item 2.2.2\"]", "Item 2.1.2" }
      });
//      inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);

      actual = new XNodeTable(new ExpandedJsonTable(json, "items.list2"));
      expected = new DefaultTable(new Object[][]{
         { "id", "items.id", "items.name", "items.list1", "items.list2" },
         { 1D, 1D, "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "Item 1.2.1" },
         { 1D, 1D, "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "Item 1.2.2" },
         { 1D, 2D, "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "Item 2.2.1" },
         { 1D, 2D, "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "Item 2.2.2" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);

      actual = new XNodeTable(new ExpandedJsonTable(json, null));
      expected = new DefaultTable(new Object[][]{
         { "id", "items.id", "items.name", "items.list1", "items.list2" },
         { 1D, 1D, "Object 1", "Item 1.1.1", "Item 1.2.1" },
         { 1D, 1D, "Object 1", "Item 1.1.2", "Item 1.2.2" },
         { 1D, 2D, "Object 2", "Item 2.1.1", "Item 2.2.1" },
         { 1D, 2D, "Object 2", "Item 2.1.2", "Item 2.2.2" }
      });
      assertTableEquals(expected, actual);
//      inetsoft.report.internal.Util.printTable(actual);
   }

   @Test
   public void testExpandLevelsWithArray() {
      String json = "[\n" +
         "  {\n" +
         "    \"name\": \"Object 1\",\n" +
         "    \"list1\": [ \"Item 1.1.1\", \"Item 1.1.2\" ],\n" +
         "    \"list2\": [ \"Item 1.2.1\", \"Item 1.2.2\" ]\n" +
         "  },\n" +
         "  {\n" +
         "    \"name\": \"Object 2\",\n" +
         "    \"list1\": [ \"Item 2.1.1\", \"Item 2.1.2\" ],\n" +
         "    \"list2\": [ \"Item 2.2.1\", \"Item 2.2.2\" ]\n" +
         "  }\n" +
         "]";

      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, 1));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "name", "list1", "list2" },
         { "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "[\"Item 1.2.1\",\"Item 1.2.2\"]" },
         { "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "[\"Item 2.2.1\",\"Item 2.2.2\"]" },
      });
      //inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);

      actual = new XNodeTable(new ExpandedJsonTable(json, 3));
      expected = new DefaultTable(new Object[][] {
         { "name", "list1", "list2" },
         { "Object 1", "Item 1.1.1", "Item 1.2.1" },
         { "Object 1", "Item 1.1.2", "Item 1.2.2" },
         { "Object 2", "Item 2.1.1", "Item 2.2.1" },
         { "Object 2", "Item 2.1.2", "Item 2.2.2" },
      });
      //inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);
   }

   @Test
   public void testExpandDeepArray() {
      final String json =
         "[\n" +
         "  {\n" +
         "    \"person\": {\n" +
         "      \"name\": \"Alan\",\n" +
         "      \"id\": \"1\"\n" +
         "    },\n" +
         "    \"orgs\": {\n" +
         "      \"old\": [\n" +
         "        \"A LLC\",\n" +
         "        \"B Corp\"\n" +
         "      ],\n" +
         "      \"current\": [\n" +
         "        \"A Org\",\n" +
         "        \"B Enterprises\"\n" +
         "      ]\n" +
         "    },\n" +
         "    \"location\": {\n" +
         "      \"address\": \"89 Atlantic Ave, Piqua, OH 45356\"\n" +
         "    }\n" +
         "  },\n" +
         "  {\n" +
         "    \"person\": {\n" +
         "      \"name\": \"Bob\",\n" +
         "      \"id\": \"2\"\n" +
         "    },\n" +
         "    \"orgs\": {\n" +
         "      \"old\": [\n" +
         "        \"A LLC\",\n" +
         "        \"Men In Black\"\n" +
         "      ],\n" +
         "      \"current\": [\n" +
         "        \"A Org\",\n" +
         "        \"Men In White\"\n" +
         "      ]\n" +
         "    },\n" +
         "    \"location\": {\n" +
         "      \"address\": \"87 Joy Ridge Street, Olive Branch, MS 38654\"\n" +
         "    }\n" +
         "  }\n" +
         "]";

      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, Integer.MAX_VALUE));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "person.name", "person.id", "orgs.old", "orgs.current", "location.address" },
         { "Alan", "1", "A LLC", "A Org", "89 Atlantic Ave, Piqua, OH 45356" },
         { "Alan", "1", "B Corp", "B Enterprises", "89 Atlantic Ave, Piqua, OH 45356" },
         { "Bob", "2", "A LLC", "A Org", "87 Joy Ridge Street, Olive Branch, MS 38654" },
         { "Bob", "2", "Men In Black", "Men In White", "87 Joy Ridge Street, Olive Branch, MS 38654" },
      });
      //inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);
   }

   @Test
   public void testExpandLevelsWithMap() {
      String json = "{\"lastpage\": true, \"values\": [\n" +
         "  {\n" +
         "    \"name\": \"Object 1\",\n" +
         "    \"list1\": [ \"Item 1.1.1\", \"Item 1.1.2\" ],\n" +
         "    \"list2\": [ \"Item 1.2.1\", \"Item 1.2.2\" ]\n" +
         "  },\n" +
         "  {\n" +
         "    \"name\": \"Object 2\",\n" +
         "    \"list1\": [ \"Item 2.1.1\", \"Item 2.1.2\" ],\n" +
         "    \"list2\": [ \"Item 2.2.1\", \"Item 2.2.2\" ]\n" +
         "  }\n" +
         "]}";

      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(json, 1));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "lastpage", "values.name", "values.list1", "values.list2" },
         { true, "Object 1", "[\"Item 1.1.1\",\"Item 1.1.2\"]", "[\"Item 1.2.1\",\"Item 1.2.2\"]" },
         { true, "Object 2", "[\"Item 2.1.1\",\"Item 2.1.2\"]", "[\"Item 2.2.1\",\"Item 2.2.2\"]" },
      });
      //inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);
   }

   @Test
   public void lookupDataIsExpanded() {
      final LinkedHashMap<String, Object> rootObj = new LinkedHashMap<>();
      final List<Object> arr = new ArrayList<>();
      rootObj.put("values", arr);

      final Map<String, Object> regularObj = new LinkedHashMap<>();
      arr.add(regularObj);
      regularObj.put("type", "regular");
      regularObj.put("list", Arrays.asList("Item 1.1", "Item 1.2"));


      final LookupMap lookupObj = new LookupMap(1);
      arr.add(lookupObj);
      lookupObj.put("type", "lookup");
      lookupObj.put("list", Arrays.asList("Item 2.1", "Item 2.2"));

      XNodeTable actual = new XNodeTable(new ExpandedJsonTable(rootObj, 1));
      DefaultTable expected = new DefaultTable(new Object[][] {
         { "values.type", "values.list" },
         { "regular", "[Item 1.1, Item 1.2]" },
         { "lookup", "Item 2.1" },
         { "lookup", "Item 2.2" },
      });

//      inetsoft.report.internal.Util.printTable(actual);
      assertTableEquals(expected, actual);
   }

   @Test
   public void nullColumnDefaultsToStringType() {
      String json = "[{\"null column\": null}]";
      final ExpandedJsonTable jsonTable = new ExpandedJsonTable(json, null);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertNull(tbl.getObject(1, 0));
      assertEquals(String.class, tbl.getColType(0));
      assertEquals(XSchema.STRING, jsonTable.getColumnType("null column"));
   }

   @Test
   public void stringValueAfterDateValueIsParsedCorrectly() {
      String json = "[{\"values\": [" +
         "{\"col\": \"12/29/2022\"}," +
         "{\"col\": \"test\"}" +
         "]}]";
      final ExpandedJsonTable jsonTable = new ExpandedJsonTable(json, Integer.MAX_VALUE);
      XNodeTable tbl = new XNodeTable(jsonTable);
      tbl.moreRows(999);

      assertEquals("test", tbl.getObject(2, 0));
      assertEquals(String.class, tbl.getColType(0));
      assertEquals(XSchema.STRING, jsonTable.getColumnType("values.col"));
   }

   private void assertTableEquals(XTable expected, XTable actual) {
      assertNotNull(actual);
      expected.moreRows(XTable.EOT);
      actual.moreRows(XTable.EOT);
      assertEquals(expected.getRowCount(), actual.getRowCount());
      assertEquals(expected.getColCount(), actual.getColCount());
      assertEquals(expected.getHeaderRowCount(), actual.getHeaderRowCount());

      for(int r = 0; r < expected.getRowCount(); r++) {
         for(int c = 0; c < expected.getColCount(); c++) {
            Object expectedValue = expected.getObject(r, c);
            Object actualValue = actual.getObject(r, c);
            assertEquals(expectedValue, actualValue);
         }
      }
   }
}

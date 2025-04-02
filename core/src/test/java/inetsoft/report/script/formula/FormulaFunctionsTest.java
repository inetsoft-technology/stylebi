/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.script.formula;

import inetsoft.report.lens.*;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.util.DefaultTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
public class FormulaFunctionsTest {
   /**
    * test fixData with array
    */
   @ParameterizedTest
   @MethodSource("provideFixDataTestCases")
   void testFixDataWithArrays(Object input, Object expected) {
      if(input instanceof Object[] && expected instanceof Object[]) {
         assertArrayEquals((Object[])expected, (Object[])FormulaFunctions.fixData(input));
      }
      else {
         assertEquals(expected, FormulaFunctions.fixData(input));
      }
   }

   private static Stream<Arguments> provideFixDataTestCases() {
      return Stream.of(
         //Test case 1: with an array of objects and an array of objects
         Arguments.of(new Object[] {"banana", "apple", "cherry"}, new Object[]{"banana", "apple", "cherry"}),
         //Test case 2: with an array of objects and a string
         Arguments.of(new Object[] {"apple"}, "apple"),
         //Test case 3: with an array of objects and an array of objects
         Arguments.of(new Object[] {null}, new Object[] {null}),
         //Test case 4: with a string and a string
         Arguments.of("test1", "test1"),
         //Test case 5: with a null and an empty string
         Arguments.of(null,"")
      );
   }

   /**
    * test mapList with array
    */
   @ParameterizedTest
   @MethodSource("provideMapListArrayTestCases")
   void testMapListWithArray(Object arrObj, Object mappingObj, String options, Object[] expected) {
      Object result = FormulaFunctions.mapList(arrObj, mappingObj, options);
      assertArrayEquals(expected, (Object[]) result);
   }

   private static Stream<Arguments> provideMapListArrayTestCases() {
      return Stream.of(
         Arguments.of(
            new Object[]{"a", "b", "c", "c"},
            new Object[]{new String[]{"a", "b"}, "aAndb"},
            "others=leaveothers,sort=asc,distinct=true",
            new Object[]{"aAndb", "c"}
         ),
         Arguments.of(
            new Object[]{"a", "b", "c", "d"},
            new Object[]{new String[]{"a"}, "isA"},
            "manualvalues=isA;Others,sortotherslast=true",
            new Object[]{"isA", "Others"}
         )
      );
   }

   /**
    * test mapList with date array
    */
   @ParameterizedTest
   @MethodSource("provideMapListDateArrayTestCases")
   void testMapListWithDateArray(Object[] dateArray, Object mappingObj, String options, Object[] expected) {
      Object result = FormulaFunctions.mapList(dateArray, mappingObj, options);
      assertArrayEquals(expected, (Object[]) result);
   }

   private static Stream<Arguments> provideMapListDateArrayTestCases() {
      // Create an array of dates
      Object[] dateArray1 = {
         new Date(2021 - 1900, 0, 1),
         new Date(2023 - 1900, 5, 15),
         new Date(2025 - 1900, 11, 31),
         new Date(2026 - 1900, 9, 20)
      };
      // Clone the date array
      Object[] dateArray2 = dateArray1.clone();
      Object[] dateArray3 = dateArray1.clone();

      // Create an object that contains a date array and a string
      Object mappingDateObj = new Object[] {
         new Object[] {
            new Date(2021 - 1900, 0, 1),
            new Date(2025 - 1900, 11, 31)},
            "2001 and 2025"
      };

      return Stream.of(
         // Test case 1: Sort by descending order, group others, and leave others
         Arguments.of(dateArray1, mappingDateObj,
                      "others=groupothers,sort=desc",
                      new Object[]{"Others", "2001 and 2025"}),
         // Test case 2: Round to year, interval of 2.0, leave others, and not a timeseries
         Arguments.of(dateArray2, mappingDateObj,
                      "rounddate=year,interval=2.0,others=leaveothers,timeseries=false",
                      new Object[]{ new Date(2022 - 1900, 0, 1),
                                    new Date(2026 - 1900, 0, 1),
                                    "2001 and 2025"}),
         // Test case 3: Sort by month, leave others, and return the number of months and days
         Arguments.of(dateArray3, mappingDateObj,
                      "sort=false,date=month,others=leaveothers",
                      new Object[]{ "2001 and 2025",6, 10 })
      );
   }

   @Test
   void testMapListWithXTable() {
      DefaultTableLens table1 = table.clone();
      DefaultTableLens table2 = table.clone();

      Object mappingDateObj = new Object[] { new Object[] {
                        new Date(2021 - 1900, 0, 1),
                        new Date(2025 - 1900, 11, 31)},
                        "2001 and 2025" };

      //test top2 and group others together is false, sot=none, others=leaveOthers, year level
      Object res2 = FormulaFunctions.mapList(table1, mappingDateObj,
                                             "field=date,sort=desc,sort2=desc,sorton=id," +
                                                "maxrows=2,rounddate=year,interval=1.0,others=leaveothers");
      assertArrayEquals(new Object[]{new Date(2026 - 1900, 0, 1),
                                     new Date(2023 - 1900, 0, 1)}, (Object[])res2);


      //test top3 and groupothers is true, sot=asc, others=groupothers, month level and interval is 2
      String options3 = "field=date,remainder=Others ,sortotherslast=true,sort=desc," +
         "sort2=asc,sorton=id,maxrows=3,rounddate=month, interval=2.0,others=leaveothers";
      Object res3 = FormulaFunctions.mapList(table2, mappingDateObj, options3  );

      assertArrayEquals(new Object[]{new Date(2023 - 1900, 4, 1),
                                     new Date(2026 - 1900, 8, 1),"2001 and 2025"}, (Object[])res3);
   }

   @Test
   void testMapListThrowExceptionWhenNoField() {
      DefaultTableLens table1 = table.clone();
      Object mappingDateObj = new Object[] { new Object[] {
         new Date(2021 - 1900, 0, 1),
         new Date(2025 - 1900, 11, 31)},
                                             "2001 and 2025" };

      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         FormulaFunctions.mapList(table1, mappingDateObj, "sort=desc,sort2=desc,sorton=id,sorton2=id"));

      assertEquals("mapList: field is required for table", exception.getMessage());
   }

   /**
    * test mapList with manual values
    */
   @Test
   void testMapListWithManualValues() {
      DefaultTableLens table1 = table.clone();

      Object mappingDateObj = new Object[] { new Object[] {
         new Date(2021 - 1900, 0, 1),
         new Date(2025 - 1900, 11, 31)},
                                             "isEqualTo20210101" };

      String options = "field=date,manualvalues=2023-01-01 00^_^00^_^00;isEqualTo20210101;2026-01-01 00^_^00," +
         "remainder=Others,sortotherslast=true,sorton=id,maxrows=3,rounddate=year,interval=1.0,others=leaveothers";

      Object res1 = FormulaFunctions.mapList(table1, mappingDateObj, options);

      assertArrayEquals(new Object[]{new Date(2023 - 1900, 0, 1),"isEqualTo20210101",
                                     new Date(2026 - 1900, 0, 1)}, (Object[])res1);
   }

   /**
    * test toList with manual values
    */
   @Test
   void testToListWithManualValues() {
      DefaultTableLens table1 = table.clone();
      Object res1 = FormulaFunctions.toList(table1,
                                            "field=name,manualvalues=c;a;b,sorton=id,maxrows=3");

      assertArrayEquals(new Object[]{"c","a","b"}, (Object[])res1);
   }

   /**
    * test union with array
    */
   @ParameterizedTest
   @MethodSource("provideUnionArrayTestCases")
   void testUnionArray(Object[] arr1, Object[] arr2, Object[] expected) {
      Object result = FormulaFunctions.union(arr1, arr2, null);
      assertArrayEquals(expected, (Object[]) result);
   }

   private static Stream<Arguments> provideUnionArrayTestCases() {
      // Return a stream of arguments for the unionArray method
      return Stream.of(
         // Test case 1: Two integer arrays
         Arguments.of(new Object[]{1, 2, 2}, new Object[]{3, 4}, new Object[]{1, 2, 3, 4}),
         // Test case 2: Two string arrays
         Arguments.of(new Object[]{1, 2, 2}, new Object[]{"a", "a", "c"}, new Object[]{1, 2, "a", "c"}),
         // Test case 3: Two arrays of objects
         Arguments.of(
            new Object[][]{{"a", 1}, {"a", 1}, {"c", 3}},
            new Object[][]{{"d", 4}, {"e", 5}},
            new Object[][]{{"a", 1}, {"a", 1}, {"c", 3}, {"d", 4}, {"e", 5}}
         ),
         // Test case 4: One string array and one array of objects
         Arguments.of(
            new Object[]{"a", "c"},
            new Object[][]{{"a", 1}, {"a", 1}, {"c", 3}},
            new Object[]{"a", "c", new Object[]{"a", 1}, new Object[]{"a", 1}, new Object[]{"c", 3}}
         )
      );
   }

   /**
    * test union with XTable
    */
   @Test
   void testUnionWithXTable() {
      DefaultTable table1 = new DefaultTable(new Object[][] {
         { "state", "id", "city" },
         { "NJ", 20D, null },
         { null, 30D, "NYC" }
      });

      DefaultTable table2 = new DefaultTable(new Object[][] {
         { "state", "id", "city" },
         { "NY", 100, null },
         { "CA", 200, null }
      });

      Object res2 = FormulaFunctions.union(table1, table2, "fields=state:city");
      assertInstanceOf(XTable.class, res2, "Result should be of type XTable");

      // Cast to XTable and verify its contents
      XTable unionTable = (XTable) res2;
      assertEquals(5, unionTable.getRowCount(), "Union table should have 5 rows");
      assertEquals(3, unionTable.getColCount(), "Union table should have 3 columns");
      assertEquals("NJ", unionTable.getObject(1, 0), "First row, first column should match");
      assertEquals(null, unionTable.getObject(4, 2), "Last row, first column should match");
   }

   /**
    * test intersect with array
    */
   @ParameterizedTest
   @MethodSource("provideIntersectArrayTestCases")
   void testIntersectWithArray(Object[] arr1, Object[] arr2, Object[] expected) {
      Object result = FormulaFunctions.intersect(arr1, arr2, null);
      assertArrayEquals(expected, (Object[]) result);
   }

   private static Stream<Arguments> provideIntersectArrayTestCases() {
      //Create a stream of arguments for testing the intersectArray method
      return Stream.of(
         //Test case 1: Two arrays with common elements
         Arguments.of(new Object[]{1, 2, 3}, new Object[]{2, 3, 4}, new Object[]{2, 3}),
         //Test case 2: Two arrays with no common elements
         Arguments.of(new Object[]{1, 2, 3}, new Object[]{4, 5, 6}, new Object[]{}),
         //Test case 3: One array is empty
         Arguments.of(new Object[]{1, 2, 3}, new Object[]{}, new Object[]{}),
         //Test case 4: Both arrays are empty
         Arguments.of(new Object[]{}, new Object[]{}, new Object[]{}),
         //Test case 5: Arrays with different data types
         Arguments.of(new Object[]{1, "a", 3.14}, new Object[]{"a", 2, 3.14}, new Object[]{"a", 3.14})
      );
   }

   /**
    * test intersect with XTable
    */
   @Test
   void testIntersectWithXTable() {
      DefaultTable table1 = new DefaultTable(new Object[][]{
         { "state", "id", "city" },
         { "NJ", 20D, null },
         { "NY", 30D, "NYC" }
      });
      DefaultTable table2 = new DefaultTable(new Object[][]{
         { "state", "id", "city" },
         { "NY", 30D, "NYC" },
         { "CA", 40D, "LA" }
      });

      Object result = FormulaFunctions.intersect(table1, table2, "fields=state:state");
      assertInstanceOf(XTable.class, result, "Result should be of type XTable");

      XTable intersectTable = (XTable) result;
      assertEquals(2, intersectTable.getRowCount(), "Intersect table should have 2 rows (including header)");
      assertEquals(3, intersectTable.getColCount(), "Intersect table should have 3 columns");
      assertEquals("NY", intersectTable.getObject(1, 0), "First row, first column should match");
      assertEquals(30D, intersectTable.getObject(1, 1), "First row, second column should match");
      assertEquals("NYC", intersectTable.getObject(1, 2), "First row, third column should match");

      //check if options fields not right, compare value by state and city
      Object result2 = FormulaFunctions.intersect(table1, table2, "fields=state:city");
      XTable intersectTable2 = (XTable) result2;
      assertEquals(1, intersectTable2.getRowCount(), "row count should be 1");
   }

   DefaultTableLens table = new DefaultTableLens(new Object[][]{
      {"name", "id", "date"},
      {"a", 1, new Date(2021 - 1900, 0, 1)},
      {"c", 2, new Date(2023 - 1900, 5, 15)},
      {"a", 3, new Date(2025 - 1900, 11, 31)},
      {"b", 2, new Date(2026 - 1900, 9, 20)}
   });

   @Test
   public void testSerializeUnion() throws Exception {
      String options = "fields=col1:col1";
      Object originalTable = FormulaFunctions.union(XTableUtil.getDefaultTableLens(),
                                                    XTableUtil.getDefaultTableLens(),
                                                    options);
      Assertions.assertInstanceOf(XTable.class, originalTable);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize((XTable) originalTable);
      Assertions.assertInstanceOf(XTable.class, deserializedTable);
   }

   @Test
   public void testSerializeIntersect() throws Exception {
      String options = "fields=col1:col1";
      Object originalTable = FormulaFunctions.intersect(XTableUtil.getDefaultTableLens(),
                                                        XTableUtil.getDefaultTableLens(),
                                                        options);
      Assertions.assertInstanceOf(XTable.class, originalTable);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize((XTable) originalTable);
      Assertions.assertInstanceOf(XTable.class, deserializedTable);
   }
}

/*
 * inetsoft-r - StyleBI is a business intelligence web application.
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
package com.inetsoft.connectors.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mozilla.javascript.*;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Disabled
class RScriptableTest {
   @BeforeAll
   static void startServer() {
      container = new RServeContainer();
      container.start();
   }

   @AfterAll
   static void stopServer() {
      System.err.println("==== BEGIN SERVER OUTPUT ====");
      new BufferedReader(new StringReader(container.getLogs())).lines()
         .filter(s -> !s.isEmpty())
         .forEach(System.err::println);
      System.err.println("===== END SERVER OUTPUT =====");
      container.stop();
   }

   @BeforeEach
   void enterContext() {
      context = Context.enter();
      scope = context.initStandardObjects();
//      scope.put("R", scope, new RScriptable());
      scope.put("rServerHost", scope, "localhost");
      scope.put("rServerPort", scope, container.getPort());
      scope.put("rServerUsername", scope, "rclient");
      scope.put("rServerPassword", scope, "password");
   }

   @AfterEach
   void exitContext() {
      Context.exit();
   }

   @Test
   void testConnect() throws Exception {
      Script script = compile("testConnect.js");
      Object result = script.exec(context, scope);
      assertNotNull(result);
      assertThat(result, instanceOf(RConnectionScriptable.class));
   }

   static Stream<Arguments> assignScalarProvider() {
      return Stream.of(
         arguments("testAssignInteger", 1.0),
         arguments("testAssignDouble", 2.1),
         arguments("testAssignBoolean", true),
         arguments("testAssignString", "A"),
         arguments("testAssignDate", "2020-12-01T00:00:00Z")
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("assignScalarProvider")
   void testAssignScalar(String sourceName, Object expected) throws Exception {
      Script script = compile(sourceName + ".js");
      Object actual = script.exec(context, scope);
      assertNotNull(actual);
      assertEquals(expected, actual);
   }

   static Stream<Arguments> assignArrayProvider() {
      return Stream.of(
         arguments("testAssignIntegerArray", Arrays.asList(1D, 2D, 3D)),
         arguments("testAssignDoubleArray", Arrays.asList(2.1, 3.2, 4.3)),
         arguments("testAssignBooleanArray", Arrays.asList(true, false, true)),
         arguments("testAssignStringArray", Arrays.asList("A", "B", "C")),
         arguments("testAssignDateArray", Arrays.asList(
            "2020-12-01T00:00:00Z", "2020-12-02T00:00:00Z", "2020-12-03T00:00:00Z"))
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("assignArrayProvider")
   void testAssignArray(String sourceName, List<Object> expected) throws Exception {
      Script script = compile(sourceName + ".js");
      Object actual = script.exec(context, scope);
      assertNotNull(actual);
      assertTrue(actual.getClass().isArray());
      assertEquals(expected.size(), Array.getLength(actual));
      assertAll(IntStream.range(0, expected.size())
                   .mapToObj(i -> () -> assertEquals(expected.get(i), Array.get(actual, i))));
   }

   @Test
   void testAssignTable() throws Exception {
      defineScriptTables();
      Script script = compile("testAssignTable.js");
      Object actual = script.exec(context, scope);
      assertNotNull(actual);
      assertThat(actual, instanceOf(TableArray.class));
      Object[][] expected = {
         { "OrderID", "CustomerID", "OrderDate" },
         { 10308, 2, "2020-09-18T00:00:00Z" },
         { 10309, 37, "2020-09-19T00:00:00Z" },
         { 10310, 77, "2020-09-20T00:00:00Z" }
      };
      assertTableEquals(expected, ((TableArray) actual).getElementTable());
   }

   @Test
   void testRunScript() throws Exception {
      Script script = compile("testRunScript.js");
      Object actual = script.exec(context, scope);
      assertNotNull(actual);
      assertThat(actual, instanceOf(TableArray.class));
      Object[][] expected = {
         { "OrderID", "CustomerID", "OrderDate" },
         { 10308D, 2D, "2020-09-18" },
         { 10309D, 37D, "2020-09-19" },
         { 10310D, 77D, "2020-09-20" }
      };
      assertTableEquals(expected, ((TableArray) actual).getElementTable());
   }

   @Test
   void testComplexScript() throws Exception {
      defineScriptTables();
      Script script = compile("testComplexScript.js");
      Object actual = script.exec(context, scope);
      assertNotNull(actual);
      assertThat(actual, instanceOf(NativeObject.class));
      NativeObject actualObject = (NativeObject) actual;
      Object actualMean = actualObject.get("mean", actualObject);
      assertNotNull(actualMean);
      assertEquals(10308D, actualMean);
      Object actualTable = actualObject.get("joined", actualObject);
      assertNotNull(actualTable);
      assertThat(actualTable, instanceOf(TableArray.class));
      Object[][] expected = {
         { "CustomerID", "OrderID", "OrderDate", "CustomerName", "ContactName", "Country" },
         { 2, 10308, "2020-09-18T00:00:00Z", "Ana Trujillo Emparedados y helados", "Ana Trujillo", "Mexico" }
      };
      assertTableEquals(expected, ((TableArray) actualTable).getElementTable());
   }

   private Script compile(String sourceName) throws IOException {
      try(BufferedReader reader = openResource(sourceName)) {
         return context.compileReader(reader, sourceName, 1, null);
      }
   }

   private BufferedReader openResource(String fileName) {
      return new BufferedReader(new InputStreamReader(
         getClass().getResourceAsStream(fileName), StandardCharsets.UTF_8));
   }

   @SuppressWarnings("SimplifyStreamApiCallChains")
   private void defineScriptTables() throws Exception {
      List<Object[]> orders;
      List<Object[]> customers;

      try(BufferedReader reader = openResource("orders.csv")) {
         orders = Stream.concat(
            Collections.singleton(new Object[] { "OrderID", "CustomerID", "OrderDate" }).stream(),
            reader.lines()
               .filter(s -> !s.isEmpty())
               .skip(1L)
               .map(s -> s.split(","))
               .map(r -> new Object[]{ Integer.valueOf(r[0]), Integer.valueOf(r[1]), Date.from(Instant.parse(r[2])) }))
            .collect(Collectors.toList());
      }

      try(BufferedReader reader = openResource("customers.csv")) {
         customers = Stream.concat(
            Collections.singleton(new Object[]{ "CustomerID", "CustomerName", "ContactName", "Country" }).stream(),
            reader.lines()
               .skip(1L)
               .filter(s -> !s.isEmpty())
               .map(s -> s.split(","))
               .map(r -> new Object[]{ Integer.valueOf(r[0]), r[1], r[2], r[3] }))
            .collect(Collectors.toList());
      }

      DefaultTableLens ordersTable = new DefaultTableLens(orders.toArray(new Object[0][]));
      ordersTable.setHeaderRowCount(1);
      DefaultTableLens customersTable = new DefaultTableLens(customers.toArray(new Object[0][]));
      customersTable.setHeaderRowCount(1);

      scope.put("ordersTable", scope, new TableArray(ordersTable));
      scope.put("customersTable", scope, new TableArray(customersTable));
   }

   private void assertTableEquals(Object[][] expected, XTable actual) {
      for(int i = 0; actual.moreRows(i); i++) {
         Assertions.assertEquals(expected[1].length, actual.getColCount(),
                                 "Wrong number of columns");

         for(int c = 0; c < actual.getColCount(); c++) {
            Assertions.assertEquals(expected[i][c], actual.getObject(i, c),
                                    "Wrong value in cell [" + i + "," + c + "]");
         }
      }

      Assertions.assertEquals(expected.length, actual.getRowCount(), "Wrong number of rows");
   }

   private Context context;
   private Scriptable scope;
   private static RServeContainer container;
}
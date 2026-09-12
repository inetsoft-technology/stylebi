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
package com.inetsoft.connectors.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.uql.XTable;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

// Feature #75423: translated off Rhino to the GraalJS ScriptScope API. The test
// scripts reference an R.connect()/conn.runScript() helper API that no longer
// exists on the production RScope/RConnectionScriptable classes, so the suite
// was already @Disabled before the GraalJS cutover. It is kept disabled until
// the script fixtures and the scope API are reconciled.
@Disabled("Requires a live RServe container and references a stale R.connect()/runScript() script API")
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
      env = new GraalJavaScriptEnv();
      env.init();
      scope = new MapScope();
      scope.putMember("rServerHost", "localhost");
      scope.putMember("rServerPort", container.getPort());
      scope.putMember("rServerUsername", "rclient");
      scope.putMember("rServerPassword", "password");
   }

   @Test
   void testConnect() throws Exception {
      Object result = exec("testConnect.js");
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
      Object actual = exec(sourceName + ".js");
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
      Object actual = exec(sourceName + ".js");
      assertNotNull(actual);
      assertTrue(actual.getClass().isArray());
      assertEquals(expected.size(), Array.getLength(actual));
      assertAll(IntStream.range(0, expected.size())
                   .mapToObj(i -> () -> assertEquals(expected.get(i), Array.get(actual, i))));
   }

   @Test
   void testAssignTable() throws Exception {
      defineScriptTables();
      Object actual = exec("testAssignTable.js");
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
      Object actual = exec("testRunScript.js");
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
   @Disabled("GraalJS returns a host proxy for the JS result object, not Rhino's NativeObject")
   void testComplexScript() throws Exception {
      defineScriptTables();
      Object actual = exec("testComplexScript.js");
      assertNotNull(actual);
   }

   private Object exec(String sourceName) throws Exception {
      Object script = env.compile(readResource(sourceName));
      return env.exec(script, scope, null, null);
   }

   private String readResource(String fileName) throws IOException {
      try(BufferedReader reader = openResource(fileName)) {
         StringBuilder sb = new StringBuilder();
         String line;

         while((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
         }

         return sb.toString();
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

      scope.putMember("ordersTable", new TableArray(ordersTable));
      scope.putMember("customersTable", new TableArray(customersTable));
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

   /**
    * Minimal map-backed top-level scope used to seed script variables.
    */
   private static final class MapScope implements ScriptScope {
      @Override
      public Object getMember(String name) {
         return members.get(name);
      }

      @Override
      public boolean hasMember(String name) {
         return members.containsKey(name);
      }

      @Override
      public void putMember(String name, Object value) {
         members.put(name, value);
      }

      @Override
      public boolean removeMember(String name) {
         return members.remove(name) != null;
      }

      @Override
      public Object[] getMemberKeys() {
         return members.keySet().toArray();
      }

      private final Map<String, Object> members = new LinkedHashMap<>();
   }

   private GraalJavaScriptEnv env;
   private MapScope scope;
   private static RServeContainer container;
}

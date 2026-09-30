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
package inetsoft.report.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The worksheet variable table is saved to and loaded from the runtime sheet state as JSON.
 * The deserializer must not load, initialize or construct a class named in that JSON
 * (Bug #77447), while the parameter values the server writes must still round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VariableTableJsonTest {
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void classNamedInJsonIsNotInitializedOrConstructed(boolean inBaseTable) throws Exception {
      String entries = "{\"scalar\":{\"type\":\"" + SentinelScalar.class.getName() +
         "\",\"value\":{\"foo\":\"bar\"}},\"array\":{\"type\":\"[L" +
         SentinelArray.class.getName() + ";\",\"value\":[{\"foo\":\"bar\"}]}}";
      String table = tableJson(entries, "null");
      String json = inBaseTable ? tableJson("{}", table) : table;

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertTrue(INITIALIZED.isEmpty(), "static initializer ran: " + INITIALIZED);
      assertTrue(CONSTRUCTED.isEmpty(), "constructor ran: " + CONSTRUCTED);
      assertNotNull(result);

      VariableTable vars = inBaseTable ? result.getBaseTable() : result;
      assertNotNull(vars);
      assertEquals(Map.of("foo", "bar"), vars.get("scalar"));
      assertEquals(List.of(Map.of("foo", "bar")), vars.get("array"));
   }

   @Test
   void parameterValuesRoundTrip() throws Exception {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("string", "text");
      values.put("boolean", true);
      values.put("char", 'x');
      values.put("byte", (byte) 3);
      values.put("short", (short) 7);
      values.put("int", 42);
      values.put("long", 1234567890123L);
      values.put("float", 1.5f);
      values.put("double", 2.25);
      values.put("bigInteger", new BigInteger("12345678901234567890"));
      values.put("date", new Date(1_790_000_000_000L));
      values.put("sqlDate", new java.sql.Date(1_790_000_000_000L));
      values.put("timestamp", new java.sql.Timestamp(1_790_000_000_123L));
      values.put("objects", new Object[] { "a", "b" });
      values.put("strings", new String[] { "c", "d" });
      values.put("ints", new Integer[] { 1, 2 });

      VariableTable table = new VariableTable();
      values.forEach(table::put);
      table.put("null", null);
      table.put("bigDecimal", new BigDecimal("1.5"));
      table.put("time", java.sql.Time.valueOf("10:13:20"));
      // values of other types, e.g. put by a script, are kept as the plain JSON value
      table.put("list", new ArrayList<>(List.of("e", "f")));
      table.put("map", new HashMap<>(Map.of("g", "h")));
      VariableTable base = new VariableTable();
      base.put("baseDate", new java.sql.Date(1_790_000_000_000L));
      table.setBaseTable(base);

      String json = new RuntimeViewsheet().saveJson(table, mapper);
      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);
      assertNotNull(result);

      for(Map.Entry<String, Object> e : values.entrySet()) {
         Object expected = e.getValue();
         Object actual = result.get(e.getKey());
         assertNotNull(actual, e.getKey());
         assertEquals(expected.getClass(), actual.getClass(), e.getKey());

         if(expected instanceof Object[] array) {
            assertArrayEquals(array, (Object[]) actual, e.getKey());
         }
         else {
            assertEquals(expected, actual, e.getKey());
         }
      }

      assertTrue(result.contains("null"));
      assertNull(result.get("null"));
      assertInstanceOf(BigDecimal.class, result.get("bigDecimal"));
      assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) result.get("bigDecimal")));
      assertInstanceOf(java.sql.Time.class, result.get("time"));
      assertEquals("10:13:20", result.get("time").toString());
      assertEquals(List.of("e", "f"), result.get("list"));
      assertEquals(Map.of("g", "h"), result.get("map"));
      assertEquals(base.get("baseDate"), result.getBaseTable().get("baseDate"));
   }

   @Test
   void entryWithoutTypeIsSkipped() throws Exception {
      String json = tableJson("{\"kept\":{\"type\":\"java.lang.String\",\"value\":\"v\"}," +
                                 "\"untyped\":{\"value\":\"v\"}}", "null");

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertEquals("v", result.get("kept"));
      assertFalse(result.contains("untyped"));
   }

   private static String tableJson(String entries, String baseTable) {
      return "{\"session\":null,\"vartable\":" + entries + ",\"notIgnoreNull\":[]," +
         "\"basetable\":" + baseTable + ",\"runtimeValue\":true,\"copyParameterTS\":0}";
   }

   private final ObjectMapper mapper = RuntimeSheetCache.createObjectMapper();
   private static final Set<String> INITIALIZED = ConcurrentHashMap.newKeySet();
   private static final Set<String> CONSTRUCTED = ConcurrentHashMap.newKeySet();

   public static class SentinelScalar {
      static { INITIALIZED.add("SentinelScalar"); }

      public SentinelScalar() {
         CONSTRUCTED.add("SentinelScalar");
      }

      public String getFoo() {
         return foo;
      }

      public void setFoo(String foo) {
         this.foo = foo;
      }

      private String foo;
   }

   public static class SentinelArray extends SentinelScalar {
      static { INITIALIZED.add("SentinelArray"); }
   }
}

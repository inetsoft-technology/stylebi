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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
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
      table.put("time", new java.sql.Time(1_790_000_000_123L));
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
      // the date part and millis of a Time are kept (Bug #77528)
      assertEquals(1_790_000_000_123L, ((java.sql.Time) result.get("time")).getTime());
      assertEquals(List.of("e", "f"), result.get("list"));
      assertEquals(Map.of("g", "h"), result.get("map"));
      assertEquals(base.get("baseDate"), result.getBaseTable().get("baseDate"));
   }

   @Test
   void typedArraysRoundTrip() throws Exception {
      Map<String, Object[]> values = new LinkedHashMap<>();
      values.put("chars", new Character[] { 'a', 'b' });
      values.put("booleans", new Boolean[] { true, false });
      values.put("bytes", new Byte[] { 1, 2 });
      values.put("shorts", new Short[] { 3, 4 });
      values.put("longs", new Long[] { 1L, 1234567890123L });
      values.put("floats", new Float[] { 1.5f });
      values.put("doubles", new Double[] { 2.25, 3.5 });
      values.put("bigIntegers", new BigInteger[] { new BigInteger("12345678901234567890") });
      values.put("bigDecimals", new BigDecimal[] { new BigDecimal("1.5") });
      values.put("dates", new Date[] { new Date(1_790_000_000_000L) });
      values.put("sqlDates", new java.sql.Date[] { new java.sql.Date(1_790_000_000_000L) });
      values.put("timestamps", new java.sql.Timestamp[] { new java.sql.Timestamp(1_790_000_000_123L) });

      VariableTable table = new VariableTable();
      values.forEach(table::put);

      String json = new RuntimeViewsheet().saveJson(table, mapper);
      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);
      assertNotNull(result);

      for(Map.Entry<String, Object[]> e : values.entrySet()) {
         Object actual = result.get(e.getKey());
         assertNotNull(actual, e.getKey());
         assertEquals(e.getValue().getClass(), actual.getClass(), e.getKey());
         assertArrayEquals(e.getValue(), (Object[]) actual, e.getKey());
      }
   }

   @Test
   void unknownTypeDoesNotDropOtherVariables() throws Exception {
      // a class that is not on the classpath and a value Jackson cannot construct used to fail
      // the whole table, which loadJson turned into null
      String json = tableJson("{\"missing\":{\"type\":\"com.example.DoesNotExist\",\"value\":\"x\"}," +
                                 "\"kept\":{\"type\":\"java.lang.String\",\"value\":\"v\"}}", "null");
      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertEquals("x", result.get("missing"));
      assertEquals("v", result.get("kept"));

      VariableTable table = new VariableTable();
      table.put("color", new java.awt.Color(1, 2, 3));
      table.put("kept", "v");
      result = RuntimeSheet.loadJson(
         VariableTable.class, new RuntimeViewsheet().saveJson(table, mapper), mapper);

      assertNotNull(result);
      assertInstanceOf(Map.class, result.get("color"));
      assertEquals("v", result.get("kept"));
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

   @Test
   void objectArrayElementsKeepTheirTypes() throws Exception {
      // Object[] is the type of a multi-value parameter, its elements used to come back as the
      // plain JSON value (Bug #77472)
      Object[] nested = new Object[] {
         java.sql.Date.valueOf("2026-09-15"), "n1", new Object[] { new BigDecimal("2.50") }, null
      };
      Object[] values = new Object[] {
         new Date(1_790_000_000_123L), java.sql.Date.valueOf("2026-09-15"),
         new java.sql.Timestamp(1_790_000_000_123L), java.sql.Time.valueOf("10:13:20"),
         new BigDecimal("1.50"), new BigDecimal("12345678901234567890.123456789"),
         BigInteger.valueOf(5), new BigInteger("12345678901234567890"), 5L, 1234567890123L,
         (short) 3, (byte) 2, 7, 1.5f, 2.25, 'c', true, "text", null, nested,
         new String[] { "s1", "s2" }
      };

      VariableTable table = new VariableTable();
      table.put("mixed", values);
      table.put("dates", new Object[] { java.sql.Date.valueOf("2026-09-15"),
                                        java.sql.Date.valueOf("2026-09-20") });
      table.put("one", new Object[] { new java.sql.Timestamp(1_790_000_000_123L) });
      table.put("empty", new Object[0]);
      VariableTable base = new VariableTable();
      base.put("baseDates", new Object[] { java.sql.Date.valueOf("2026-09-15"), null });
      table.setBaseTable(base);

      VariableTable result = roundTrip(table);

      assertSameValues(values, result.get("mixed"), "mixed");
      assertSameValues(table.get("dates"), result.get("dates"), "dates");
      assertSameValues(table.get("one"), result.get("one"), "one");
      assertSameValues(table.get("empty"), result.get("empty"), "empty");
      assertSameValues(base.get("baseDates"), result.getBaseTable().get("baseDates"), "baseDates");
   }

   @Test
   void bigDecimalKeepsScaleAndPrecision() throws Exception {
      VariableTable table = new VariableTable();
      table.put("scale", new BigDecimal("1.50"));
      table.put("precision", new BigDecimal("12345678901234567890.123456789"));
      table.put("array", new BigDecimal[] { new BigDecimal("1.50"), new BigDecimal("1E+3") });

      VariableTable result = roundTrip(table);

      assertSameValues(table.get("scale"), result.get("scale"), "scale");
      assertSameValues(table.get("precision"), result.get("precision"), "precision");
      assertSameValues(table.get("array"), result.get("array"), "array");
   }

   @Test
   void dateOneOfConditionMatchesAfterReload() throws Exception {
      VariableTable table = new VariableTable();
      table.put("p", new Object[] { java.sql.Date.valueOf("2026-09-15"),
                                    java.sql.Date.valueOf("2026-09-20") });

      VariableTable result = roundTrip(table);

      for(VariableTable vars : new VariableTable[] { table, result }) {
         AssetCondition condition = new AssetCondition(XSchema.DATE);
         condition.setOperation(XCondition.ONE_OF);
         UserVariable variable = new UserVariable("p");
         variable.setTypeNode(XSchema.createPrimitiveType(XSchema.DATE));
         condition.addValue(variable);
         condition.replaceVariable(vars);

         assertFalse(condition.isIgnored());
         assertTrue(condition.evaluate(java.sql.Date.valueOf("2026-09-15")));
         assertTrue(condition.evaluate(java.sql.Timestamp.valueOf("2026-09-20 00:00:00")));
         assertFalse(condition.evaluate(java.sql.Date.valueOf("2026-09-16")));
      }
   }

   @Test
   void jsonWithoutElementTypesStillLoads() throws Exception {
      // the state written before Bug #77472 has no element types and numeric decimals
      String json = tableJson("{\"p\":{\"type\":\"[Ljava.lang.Object;\",\"value\":" +
                                 "[1789444800000,\"05:13:20\",1.5,null,[\"n1\",1]]}," +
                                 "\"bd\":{\"type\":\"java.math.BigDecimal\",\"value\":1.50}}",
                              "null");

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertArrayEquals(new Object[] { 1789444800000L, "05:13:20", 1.5, null, List.of("n1", 1) },
                        (Object[]) result.get("p"));
      assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) result.get("bd")));
   }

   @Test
   void oldReaderIgnoresElementTypes() throws Exception {
      VariableTable table = new VariableTable();
      table.put("p", new Object[] { java.sql.Date.valueOf("2026-09-15"),
                                    java.sql.Time.valueOf("10:13:20"), new BigDecimal("1.50") });
      table.put("bd", new BigDecimal("1.50"));
      table.put("time", java.sql.Time.valueOf("10:13:20"));
      JsonNode vartable = mapper.readTree(new RuntimeViewsheet().saveJson(table, mapper))
         .get("vartable");

      // the reader before Bug #77472 converted only the "type" and "value" fields, nothing may
      // be written as a number where it expects a string (e.g. java.sql.Time)
      assertArrayEquals(new Object[] { java.sql.Date.valueOf("2026-09-15").getTime(), "10:13:20",
                                       "1.50" }, (Object[]) oldRead(vartable.get("p")));
      assertEquals(new BigDecimal("1.50"), oldRead(vartable.get("bd")));
      assertEquals(java.sql.Time.valueOf("10:13:20"), oldRead(vartable.get("time")));
   }

   @Test
   void invalidElementTypesFallBackPerElement() throws Exception {
      String json = tableJson(
         "{\"short\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[1789444800000,1789444800000]," +
            "\"elementTypes\":[\"java.sql.Date\"]}," +
         "\"long\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[1789444800000]," +
            "\"elementTypes\":[\"java.sql.Date\",\"java.lang.String\"]}," +
         "\"bad\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[\"abc\",\"x\",1,[1]]," +
            "\"elementTypes\":[\"java.sql.Date\",\"" + SentinelScalar.class.getName() +
            "\",{\"foo\":1},\"java.lang.String\"]}," +
         "\"notArray\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[1]," +
            "\"elementTypes\":\"java.sql.Date\"}," +
         "\"kept\":{\"type\":\"java.lang.String\",\"value\":\"v\"}}", "null");

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertTrue(INITIALIZED.isEmpty(), "static initializer ran: " + INITIALIZED);
      assertTrue(CONSTRUCTED.isEmpty(), "constructor ran: " + CONSTRUCTED);
      assertSameValues(new Object[] { new java.sql.Date(1789444800000L), 1789444800000L },
                       result.get("short"), "short");
      assertSameValues(new Object[] { new java.sql.Date(1789444800000L) }, result.get("long"),
                       "long");
      assertArrayEquals(new Object[] { "abc", "x", 1, List.of(1) }, (Object[]) result.get("bad"));
      assertArrayEquals(new Object[] { 1 }, (Object[]) result.get("notArray"));
      assertEquals("v", result.get("kept"));
   }

   @Test
   void timesKeepDatePartMillisAndNanos() throws Exception {
      // a Time is written as "HH:mm:ss" and a Timestamp as epoch millis, the date part, millis
      // and nanos used to be lost (Bug #77528)
      java.sql.Time time = new java.sql.Time(1_790_000_000_123L);
      java.sql.Timestamp timestamp = new java.sql.Timestamp(1_790_000_000_123L);
      timestamp.setNanos(123_456_789);
      VariableTable table = new VariableTable();
      table.put("time", time);
      table.put("timestamp", timestamp);
      table.put("times", new java.sql.Time[] { time, null });
      table.put("timestamps", new java.sql.Timestamp[] { timestamp });
      table.put("objects", new Object[] { time, timestamp, "s", new Object[] { time }, null });
      VariableTable base = new VariableTable();
      base.put("baseTime", time);
      table.setBaseTable(base);

      VariableTable result = roundTrip(table);

      for(String name : new String[] { "time", "timestamp", "times", "timestamps", "objects" }) {
         assertSameValues(table.get(name), result.get(name), name);
      }

      assertSameValues(time, result.getBaseTable().get("baseTime"), "baseTime");
      assertEquals(123_456_789, ((java.sql.Timestamp) result.get("timestamp")).getNanos());
      assertEquals(123_456_789,
                   ((java.sql.Timestamp) ((Object[]) result.get("objects"))[1]).getNanos());
   }

   @Test
   void oldReaderIgnoresTimes() throws Exception {
      java.sql.Time time = new java.sql.Time(java.sql.Time.valueOf("10:13:20").getTime() + 123);
      java.sql.Timestamp timestamp = new java.sql.Timestamp(1_790_000_000_123L);
      timestamp.setNanos(123_456_789);
      VariableTable table = new VariableTable();
      table.put("time", time);
      table.put("timestamp", timestamp);
      table.put("objects", new Object[] { time, "s" });
      JsonNode vartable = mapper.readTree(new RuntimeViewsheet().saveJson(table, mapper))
         .get("vartable");

      // "value" keeps the encoding that every older reader converts, "times" is added
      assertEquals(mapper.readTree("{\"type\":\"java.sql.Time\",\"value\":\"10:13:20\"," +
                                      "\"times\":{\"time\":" + time.getTime() + "}}"),
                   vartable.get("time"));
      assertEquals(mapper.readTree("{\"type\":\"java.sql.Timestamp\",\"value\":1790000000123," +
                                      "\"times\":{\"time\":1790000000123,\"nanos\":123456789}}"),
                   vartable.get("timestamp"));
      assertEquals(mapper.readTree("{\"type\":\"[Ljava.lang.Object;\"," +
                                      "\"value\":[\"10:13:20\",\"s\"]," +
                                      "\"elementTypes\":[\"java.sql.Time\",\"java.lang.String\"]," +
                                      "\"times\":[{\"time\":" + time.getTime() + "},null]}"),
                   vartable.get("objects"));

      assertEquals(java.sql.Time.valueOf("10:13:20"), oldRead(vartable.get("time")));
      assertEquals(new java.sql.Timestamp(1_790_000_000_123L), oldRead(vartable.get("timestamp")));
      assertArrayEquals(new Object[] { "10:13:20", "s" },
                        (Object[]) oldRead(vartable.get("objects")));
   }

   @Test
   void jsonWithoutTimesStillLoads() throws Exception {
      // the state written before Bug #77528 has no "times"
      String json = tableJson(
         "{\"time\":{\"type\":\"java.sql.Time\",\"value\":\"10:13:20\"}," +
         "\"timestamp\":{\"type\":\"java.sql.Timestamp\",\"value\":1790000000123}," +
         "\"times\":{\"type\":\"[Ljava.sql.Time;\",\"value\":[\"10:13:20\",null]}," +
         "\"objects\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[\"10:13:20\",1790000000123]," +
            "\"elementTypes\":[\"java.sql.Time\",\"java.sql.Timestamp\"]}}", "null");

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertSameValues(java.sql.Time.valueOf("10:13:20"), result.get("time"), "time");
      assertSameValues(new java.sql.Timestamp(1_790_000_000_123L), result.get("timestamp"),
                       "timestamp");
      assertSameValues(new java.sql.Time[] { java.sql.Time.valueOf("10:13:20"), null },
                       result.get("times"), "times");
      assertSameValues(new Object[] { java.sql.Time.valueOf("10:13:20"),
                                      new java.sql.Timestamp(1_790_000_000_123L) },
                       result.get("objects"), "objects");
   }

   @Test
   void invalidTimesAreIgnored() throws Exception {
      String json = tableJson(
         "{\"time\":{\"type\":\"java.sql.Time\",\"value\":\"10:13:20\",\"times\":\"x\"}," +
         "\"timestamp\":{\"type\":\"java.sql.Timestamp\",\"value\":1790000000123," +
            "\"times\":{\"time\":1790000000123,\"nanos\":-1}}," +
         "\"string\":{\"type\":\"java.lang.String\",\"value\":\"v\",\"times\":{\"time\":1}}," +
         "\"objects\":{\"type\":\"[Ljava.lang.Object;\",\"value\":[\"10:13:20\",\"s\"]," +
            "\"elementTypes\":[\"java.sql.Time\",\"java.lang.String\"]," +
            "\"times\":[{\"time\":\"y\"}]}}", "null");

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      assertSameValues(java.sql.Time.valueOf("10:13:20"), result.get("time"), "time");
      assertSameValues(new java.sql.Timestamp(1_790_000_000_123L), result.get("timestamp"),
                       "timestamp");
      assertEquals("v", result.get("string"));
      assertSameValues(new Object[] { java.sql.Time.valueOf("10:13:20"), "s" },
                       result.get("objects"), "objects");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void badValueDoesNotDropOtherVariables(boolean inBaseTable) throws Exception {
      // one value that can't be converted made loadJson return null for the whole table
      long time = java.sql.Time.valueOf("10:13:20").getTime() + 123;
      String entries = "{\"bad\":{\"type\":\"java.lang.Integer\",\"value\":\"abc\"}," +
         "\"badArray\":{\"type\":\"[Ljava.sql.Time;\",\"value\":[\"10:13:20\",\"bad\",null]," +
            "\"times\":[{\"time\":" + time + "}]}," +
         "\"badTimestamp\":{\"type\":\"java.sql.Timestamp\",\"value\":\"2026-09-21 10:13:20\"}," +
         "\"good\":{\"type\":\"java.lang.String\",\"value\":\"v\"}}";
      String table = tableJson(entries, "null");
      String json = inBaseTable ?
         tableJson("{\"top\":{\"type\":\"java.lang.String\",\"value\":\"t\"}}", table) : table;

      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);

      assertNotNull(result);
      VariableTable vars = inBaseTable ? result.getBaseTable() : result;
      assertNotNull(vars);

      if(inBaseTable) {
         assertEquals("t", result.get("top"));
      }

      assertEquals("abc", vars.get("bad"));
      assertEquals("2026-09-21 10:13:20", vars.get("badTimestamp"));
      assertEquals("v", vars.get("good"));
      // a typed array falls back per element to an Object[], never to a List
      assertSameValues(new Object[] { new java.sql.Time(time), "bad", null },
                       vars.get("badArray"), "badArray");
   }

   @Test
   void missingFieldsDoNotDropTheTable() throws Exception {
      VariableTable result = RuntimeSheet.loadJson(
         VariableTable.class,
         "{\"vartable\":{\"p\":{\"type\":\"java.lang.String\",\"value\":\"v\"}}}", mapper);

      assertNotNull(result);
      assertEquals("v", result.get("p"));
      assertNull(result.getBaseTable());

      result = RuntimeSheet.loadJson(VariableTable.class, "{\"session\":null}", mapper);
      assertNotNull(result);
      assertFalse(result.keys().hasMoreElements());
   }

   private VariableTable roundTrip(VariableTable table) throws Exception {
      String json = new RuntimeViewsheet().saveJson(table, mapper);
      VariableTable result = RuntimeSheet.loadJson(VariableTable.class, json, mapper);
      assertNotNull(result, json);
      return result;
   }

   // the conversion done by the deserializer before Bug #77472, which ignores other fields
   private Object oldRead(JsonNode entry) throws Exception {
      Class<?> type = Class.forName(entry.get("type").asText());
      return mapper.convertValue(entry.get("value"), type);
   }

   private static void assertSameValues(Object expected, Object actual, String name) {
      if(expected == null) {
         assertNull(actual, name);
         return;
      }

      assertNotNull(actual, name);
      assertEquals(expected.getClass(), actual.getClass(), name);

      if(expected instanceof Object[] array) {
         Object[] actualArray = (Object[]) actual;
         assertEquals(array.length, actualArray.length, name);

         for(int i = 0; i < array.length; i++) {
            assertSameValues(array[i], actualArray[i], name + "[" + i + "]");
         }
      }
      else {
         // equals, not compareTo, so that the BigDecimal scale is compared too
         assertEquals(expected, actual, name);

         if(expected instanceof Date date) {
            assertEquals(date.getTime(), ((Date) actual).getTime(), name);
         }
      }
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

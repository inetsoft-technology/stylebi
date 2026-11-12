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
package inetsoft.uql;

import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JSON state of a variable table (Bugs #77472, #77528): a value keeps its Java type,
 * the elements of a multi-value parameter keep theirs, and a Time or Timestamp keeps its
 * millis and nanos.
 */
@Tag("core")
class VariableTableJsonTypedValueTest {
   private final ObjectMapper mapper = new ObjectMapper();

   private Object roundTrip(Object value) throws Exception {
      VariableTable original = new VariableTable();
      original.put("p", value);

      VariableTable restored =
         mapper.readValue(mapper.writeValueAsString(original), VariableTable.class);
      return restored.get("p");
   }

   @Nested
   class ScalarValues {
      @Test
      void numbers_keepTheirType() throws Exception {
         assertEquals(15, roundTrip(15));
         assertEquals(3000000000L, roundTrip(3000000000L));
         assertEquals((short) 7, roundTrip((short) 7));
         assertEquals((byte) 3, roundTrip((byte) 3));
         assertEquals(1.5, roundTrip(1.5));
         assertEquals(1.5f, roundTrip(1.5f));
         assertEquals(new BigInteger("123456789012345678901234567890"),
                      roundTrip(new BigInteger("123456789012345678901234567890")));
      }

      @Test
      void longBeyondIntRange_staysLong() throws Exception {
         assertInstanceOf(Long.class, roundTrip(3000000000L));
      }

      @Test
      void smallLong_staysLongNotInteger() throws Exception {
         assertInstanceOf(Long.class, roundTrip(42L));
      }

      @Test
      void bigDecimal_keepsItsScale() throws Exception {
         Object value = roundTrip(new BigDecimal("1.10"));

         assertInstanceOf(BigDecimal.class, value);
         assertEquals("1.10", value.toString());
      }

      @Test
      void booleanCharacterAndString_keepTheirType() throws Exception {
         assertEquals(Boolean.TRUE, roundTrip(Boolean.TRUE));
         assertEquals('x', roundTrip('x'));
         assertEquals("text", roundTrip("text"));
      }

      @Test
      void dates_keepTheirClassAndMillis() throws Exception {
         long millis = 1710460800000L;

         Object date = roundTrip(new java.sql.Date(millis));
         assertInstanceOf(java.sql.Date.class, date);
         assertEquals(millis, ((java.util.Date) date).getTime());

         Object utilDate = roundTrip(new java.util.Date(millis));
         assertEquals(java.util.Date.class, utilDate.getClass());
         assertEquals(millis, ((java.util.Date) utilDate).getTime());
      }

      @Test
      void timestamp_keepsNanos() throws Exception {
         Timestamp timestamp = new Timestamp(1710460800123L);
         timestamp.setNanos(123456789);

         Object value = roundTrip(timestamp);

         assertInstanceOf(Timestamp.class, value);
         assertEquals(timestamp, value);
         assertEquals(123456789, ((Timestamp) value).getNanos());
      }

      @Test
      void time_keepsItsMillis() throws Exception {
         Time time = new Time(1710460800123L);

         Object value = roundTrip(time);

         assertInstanceOf(Time.class, value);
         assertEquals(time.getTime(), ((Time) value).getTime());
      }
   }

   @Nested
   class ArrayValues {
      @Test
      void multiValueParameter_keepsEachElementType() throws Exception {
         Object[] value = { 1, 3000000000L, 2.5, "a", Boolean.TRUE };

         assertArrayEquals(value, (Object[]) roundTrip(value));
      }

      @Test
      void multiValueDates_keepTheirClass() throws Exception {
         Object[] value = { new java.sql.Date(1710460800000L), new java.sql.Date(1710547200000L) };

         Object[] restored = (Object[]) roundTrip(value);

         assertInstanceOf(java.sql.Date.class, restored[0]);
         assertInstanceOf(java.sql.Date.class, restored[1]);
         assertArrayEquals(value, restored);
      }

      @Test
      void multiValueTimestamps_keepNanos() throws Exception {
         Timestamp t1 = new Timestamp(1710460800123L);
         t1.setNanos(123456789);
         Timestamp t2 = new Timestamp(1710547200000L);
         Object[] value = { t1, t2 };

         Object[] restored = (Object[]) roundTrip(value);

         assertEquals(123456789, ((Timestamp) restored[0]).getNanos());
         assertEquals(t2, restored[1]);
      }

      @Test
      void nullElement_staysNull() throws Exception {
         Object[] restored = (Object[]) roundTrip(new Object[] { "a", null, 3 });

         assertArrayEquals(new Object[] { "a", null, 3 }, restored);
      }

      @Test
      void nestedArray_keepsItsElementTypes() throws Exception {
         Object[] value = { new Object[] { 1, 3000000000L }, "x" };

         Object[] restored = (Object[]) roundTrip(value);

         assertArrayEquals(new Object[] { 1, 3000000000L }, (Object[]) restored[0]);
         assertEquals("x", restored[1]);
      }

      @Test
      void stringArray_staysStringArray() throws Exception {
         Object restored = roundTrip(new String[] { "a", "b" });

         assertInstanceOf(String[].class, restored);
         assertArrayEquals(new String[] { "a", "b" }, (String[]) restored);
      }
   }

   @Nested
   class StateWrittenByAnOlderVersion {
      @Test
      void arrayWithoutElementTypes_isReadAsPlainJsonValues() throws Exception {
         String json = "{\"vartable\":{\"p\":{\"type\":\"[Ljava.lang.Object;\"," +
            "\"value\":[\"a\",2]}}}";

         Object[] value = (Object[]) mapper.readValue(json, VariableTable.class).get("p");

         assertArrayEquals(new Object[] { "a", 2 }, value);
      }

      @Test
      void timestampWithoutTimes_isReadFromTheValueField() throws Exception {
         String json = "{\"vartable\":{\"p\":{\"type\":\"java.sql.Timestamp\"," +
            "\"value\":1710460800000}}}";

         Object value = mapper.readValue(json, VariableTable.class).get("p");

         assertInstanceOf(Timestamp.class, value);
         assertEquals(1710460800000L, ((Timestamp) value).getTime());
      }

      @Test
      void unknownType_isKeptAsPlainJsonValue() throws Exception {
         String json = "{\"vartable\":{\"p\":{\"type\":\"com.example.Unknown\",\"value\":\"v\"}," +
            "\"q\":{\"type\":\"java.lang.String\",\"value\":\"kept\"}}}";

         VariableTable restored = mapper.readValue(json, VariableTable.class);

         assertEquals("v", restored.get("p"));
         assertEquals("kept", restored.get("q"));
      }
   }

   private VariableTable roundTripTable(VariableTable original) throws Exception {
      return mapper.readValue(mapper.writeValueAsString(original), VariableTable.class);
   }

   private static Object field(VariableTable table, String name) throws Exception {
      java.lang.reflect.Field field = VariableTable.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(table);
   }

   @Nested
   class HardeningRows {
      private java.util.TimeZone oldZone;

      @BeforeEach
      void saveDefaults() {
         oldZone = java.util.TimeZone.getDefault();
      }

      @AfterEach
      void restoreDefaults() {
         java.util.TimeZone.setDefault(oldZone);
      }

      @DisplayName("J01 one value per supported type, scalar and in an array, keeps class and value")
      @ParameterizedTest(name = "[{index}] {0}")
      @MethodSource("typedValues")
      void j01_typedValues(Object value) throws Exception {
         Object restored = roundTrip(value);
         assertEquals(value.getClass(), restored.getClass());
         assertEquals(value, restored);

         Object[] array = { value, value };
         Object[] restoredArray = (Object[]) roundTrip(array);
         assertEquals(value.getClass(), restoredArray[0].getClass());
         assertArrayEquals(array, restoredArray);
      }

      static java.util.stream.Stream<Object> typedValues() {
         return java.util.stream.Stream.of((byte) 3, (short) 7, 1.5f, new BigInteger("12345678901234567890"),
                                           'c', 42, 42L, 2.5, true, "s");
      }

      @DisplayName("J01 a typed Integer[] keeps its elements")
      @Test
      void j01_integerArray() throws Exception {
         Object restored = roundTrip(new Integer[] { 1, 2 });
         assertInstanceOf(Object[].class, restored);
         assertArrayEquals(new Object[] { 1, 2 }, (Object[]) restored);
      }

      @DisplayName("J02 NaN, infinity and big numbers keep class and value")
      @ParameterizedTest(name = "[{index}] {0}")
      @MethodSource("extremeNumbers")
      void j02_extremeNumbers(Object value) throws Exception {
         Object restored = roundTrip(value);
         assertEquals(value.getClass(), restored.getClass());
         assertEquals(value, restored);

         Object[] restoredArray = (Object[]) roundTrip(new Object[] { value, 1 });
         assertEquals(value.getClass(), restoredArray[0].getClass());
         assertEquals(value, restoredArray[0]);
      }

      static java.util.stream.Stream<Object> extremeNumbers() {
         return java.util.stream.Stream.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                                           Float.NaN, BigInteger.ONE.shiftLeft(70), Long.MIN_VALUE);
      }

      @DisplayName("J03 nulls beside Timestamp / Time keep position and nanos")
      @Test
      void j03_nullsWithTimes() throws Exception {
         Timestamp ts = new Timestamp(1710460800123L);
         ts.setNanos(123456789);
         Time time = new Time(1710460800000L);

         Object[] restored = (Object[]) roundTrip(new Object[] { null, ts, time });
         assertNull(restored[0]);
         assertEquals(ts, restored[1]);
         assertEquals(123456789, ((Timestamp) restored[1]).getNanos());
         assertInstanceOf(Time.class, restored[2]);

         Object[] nested = (Object[]) roundTrip(new Object[] { new Object[] { null, ts } });
         Object[] inner = (Object[]) nested[0];
         assertNull(inner[0]);
         assertEquals(ts, inner[1]);
         assertEquals(123456789, ((Timestamp) inner[1]).getNanos());
      }

      @DisplayName("J04 base tables, flags and timestamps survive the round trip")
      @Test
      void j04_stateFields() throws Exception {
         VariableTable top = new VariableTable(false);
         VariableTable base1 = new VariableTable();
         VariableTable base2 = new VariableTable();
         top.put("a", 1);
         base1.put("b1", "x");
         base2.put("b2", 2.5);
         top.addBaseTable(base1);
         top.addBaseTable(base2);
         top.setAsIs("a", true);
         top.setNotIgnoredNull("a");
         // copyParameterTS has no public accessor; if the field is renamed this test fails by reflection, not by behavior
         java.lang.reflect.Field ts = VariableTable.class.getDeclaredField("copyParameterTS");
         ts.setAccessible(true);
         ts.setLong(top, 123456789L);

         VariableTable restored = roundTripTable(top);
         VariableTable copy = top.clone();

         for(String name : new String[] { "a", "b1", "b2", "none" }) {
            assertEquals(top.contains(name), restored.contains(name), "contains " + name);
            assertEquals(top.get(name), restored.get(name), "get " + name);
            assertEquals(copy.get(name), restored.get(name), "clone get " + name);
         }

         java.util.Set<String> keys = new java.util.TreeSet<>(java.util.Collections.list(top.keys()));
         assertEquals(keys, new java.util.TreeSet<>(java.util.Collections.list(restored.keys())));
         assertEquals(top.isAsIs("a"), restored.isAsIs("a"));
         assertEquals(top.isNotIgnoredNull("a"), restored.isNotIgnoredNull("a"));
         assertEquals(field(top, "copyParameterTS"), field(restored, "copyParameterTS"));
         assertNotNull(restored.getBaseTable(), "first base table");
         assertNotNull(restored.getBaseTable().getBaseTable(), "second base table");
      }

      @DisplayName("J04b the default runtimeValue is true before and after a round trip")
      @Test
      void j04b_runtimeValueDefault() throws Exception {
         VariableTable table = new VariableTable();
         table.put("a", 1);
         assertEquals(true, field(table, "runtimeValue"));
         assertEquals(true, field(roundTripTable(table), "runtimeValue"));
      }

      @DisplayName("J05b a table without context variables (as RuntimeViewsheet saves it) round-trips")
      @Test
      void j05b_withoutContextVariables() throws Exception {
         VariableTable source = new VariableTable();
         source.put("keep", "k");
         source.put("n", 3000000000L);
         source.put("__principal__", "p");
         source.put("_USER_", "u");

         VariableTable saved = new VariableTable();

         for(String name : java.util.Collections.list(source.keys())) {
            if(!VariableTable.isContextVariable(name)) {
               saved.put(name, source.get(name));
            }
         }

         VariableTable restored = roundTripTable(saved);
         assertEquals("k", restored.get("keep"));
         assertEquals(3000000000L, restored.get("n"));
         assertFalse(restored.contains("__principal__"));
      }

      @DisplayName("J06 formats are not persisted")
      @Test
      void j06_formats() throws Exception {
         VariableTable table = new VariableTable();
         table.put("d", new java.util.Date(0));
         table.putFormat("d", "yyyy");

         assertNull(roundTripTable(table).getFormat("d"));
      }

      @DisplayName("J07 special characters survive as scalar, element and name")
      @ParameterizedTest(name = "[{index}]")
      @MethodSource("specialStrings")
      void j07_specialStrings(String value) throws Exception {
         assertEquals(value, roundTrip(value));
         assertArrayEquals(new Object[] { value, "z" }, (Object[]) roundTrip(new Object[] { value, "z" }));

         VariableTable table = new VariableTable();
         table.put("k" + value, "v");
         assertEquals("v", roundTripTable(table).get("k" + value));
      }

      static java.util.stream.Stream<String> specialStrings() {
         return java.util.stream.Stream.of("\"", "\\", "\u0000", "]]>", "line1\nline2", "😀",
                                           "x".repeat(10000));
      }

      @DisplayName("J08 Date / Timestamp / Time keep millis and nanos in every zone")
      @ParameterizedTest(name = "[{index}] {0}")
      @ValueSource(strings = { "UTC", "America/Los_Angeles", "Pacific/Auckland" })
      void j08_zones(String zone) throws Exception {
         java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
         long millis = 1710460800123L;
         Timestamp ts = new Timestamp(millis);
         ts.setNanos(123456789);

         assertEquals(millis, ((java.util.Date) roundTrip(new java.util.Date(millis))).getTime());
         assertEquals(millis, ((java.util.Date) roundTrip(new java.sql.Date(millis))).getTime());
         Timestamp restoredTs = (Timestamp) roundTrip(ts);
         assertEquals(ts.getTime(), restoredTs.getTime());
         assertEquals(123456789, restoredTs.getNanos());
         assertEquals(millis, ((Time) roundTrip(new Time(millis))).getTime());
      }
   }
}

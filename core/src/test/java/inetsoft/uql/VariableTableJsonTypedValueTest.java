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
}

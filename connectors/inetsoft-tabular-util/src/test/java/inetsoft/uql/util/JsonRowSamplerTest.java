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
package inetsoft.uql.util;

import org.junit.jupiter.api.Test;

import javax.json.Json;
import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No Spring context here, same as {@link JsonShapeDistillerTest}: the sampler is a pure function
 * over a parsed JSON value and stands up nothing.
 *
 * <p>Most cases are driven through hand-built maps, because that is what {@code selectData} hands
 * the sampler once JSONPath has run. The JSON-P object model is covered separately
 * ({@link #convertsJsonPValuesToPlainJava()}), since it is the model whose leaves are wrapper
 * objects rather than Java values.</p>
 */
public class JsonRowSamplerTest {
   private static Map<String, Object> map(Object... keysAndValues) {
      Map<String, Object> map = new LinkedHashMap<>();

      for(int i = 0; i < keysAndValues.length; i += 2) {
         map.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }

      return map;
   }

   private static Object parse(String json) {
      try(javax.json.JsonReader reader = Json.createReader(new StringReader(json))) {
         return reader.read();
      }
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> row(List<?> rows, int index) {
      return (Map<String, Object>) assertInstanceOf(Map.class, rows.get(index));
   }

   @Test
   public void keepsRowsAsRawNestedObjects() {
      // NOT flattened: JsonTable.walkRecord would report this row as columns id / dispute.status,
      // and the whole point of sampling before loadStreamed is that the caller sees the response's
      // own shape. A nested object stays a nested object.
      List<?> rows = JsonRowSampler.sample(
         List.of(map("id", "ch_1", "dispute", map("status", "won"))), 20).getRows();

      assertEquals(1, rows.size());
      Map<String, Object> row = row(rows, 0);
      assertEquals("ch_1", row.get("id"));
      assertNull(row.get("dispute.status"));

      Map<?, ?> dispute = assertInstanceOf(Map.class, row.get("dispute"));
      assertEquals("won", dispute.get("status"));
   }

   @Test
   public void capsRowCountAndReportsTruncated() {
      JsonRowSampler.Result result = JsonRowSampler.sample(
         List.of(map("gid", "1"), map("gid", "2"), map("gid", "3")), 2);

      assertEquals(2, result.getRows().size());
      assertEquals("1", row(result.getRows(), 0).get("gid"));
      assertEquals("2", row(result.getRows(), 1).get("gid"));
      // The caller has NOT seen every distinct value, which is the whole reason this flag exists.
      assertTrue(result.isTruncated());
   }

   @Test
   public void reportsNoTruncationWhenEverythingFits() {
      JsonRowSampler.Result result = JsonRowSampler.sample(
         List.of(map("gid", "1"), map("gid", "2")), 20);

      assertEquals(2, result.getRows().size());
      assertFalse(result.isTruncated());
   }

   @Test
   public void yieldsNoRowsWhenTheSelectionIsNotAList() {
      // A jsonPath that selected a single object, a scalar, or nothing. Not an error: the caller
      // simply gets no sample.
      for(Object selected : new Object[] { map("id", "ch_1"), "scalar", null }) {
         JsonRowSampler.Result result = JsonRowSampler.sample(selected, 20);

         assertTrue(result.getRows().isEmpty(), "expected no rows for " + selected);
         assertFalse(result.isTruncated(), "expected no truncation for " + selected);
      }
   }

   @Test
   public void keepsScalarRows() {
      // A bare array of scalars is a legitimate response, and skipping non-object elements would
      // silently make "20 rows" mean fewer than 20.
      List<?> rows = JsonRowSampler.sample(List.of("a", "b"), 20).getRows();

      assertEquals(List.of("a", "b"), rows);
   }

   @Test
   public void replacesAnOverlongStringWithAMarkerRatherThanTruncatingIt() {
      String long300 = "x".repeat(300);
      JsonRowSampler.Result result = JsonRowSampler.sample(
         List.of(map("gid", "1234567890", "description", long300)), 20,
         JsonRowSampler.DEFAULT_MAX_NODES, JsonRowSampler.DEFAULT_MAX_DEPTH, 200);

      Map<String, Object> row = row(result.getRows(), 0);
      // A truncated id would be a wrong value that looks entirely valid, and would be sent as a
      // parameter. The marker cannot be mistaken for data.
      assertEquals("<omitted: 300 chars>", row.get("description"));
      assertEquals("1234567890", row.get("gid"));
      assertTrue(result.isTruncated());
   }

   @Test
   public void replacesASubtreeBeyondTheDepthCapWithAMarker() {
      JsonRowSampler.Result result = JsonRowSampler.sample(
         List.of(map("a", map("b", map("c", "deep")))), 20,
         JsonRowSampler.DEFAULT_MAX_NODES, 2, JsonRowSampler.DEFAULT_MAX_STRING);

      Map<String, Object> row = row(result.getRows(), 0);
      Map<?, ?> a = assertInstanceOf(Map.class, row.get("a"));
      assertEquals(JsonRowSampler.OMITTED_DEPTH, a.get("b"));
      assertTrue(result.isTruncated());
   }

   @Test
   public void dropsAWholeRowWhenTheNodeBudgetRunsOut() {
      // Three nodes per row (the object plus two leaves), so the second row does not fit in four.
      JsonRowSampler.Result result = JsonRowSampler.sample(
         List.of(map("a", "1", "b", "2"), map("a", "3", "b", "4")), 20, 4,
         JsonRowSampler.DEFAULT_MAX_DEPTH, JsonRowSampler.DEFAULT_MAX_STRING);

      // A half row is a lie: it reads as a row whose fields are absent from the response.
      assertEquals(1, result.getRows().size());
      Map<String, Object> row = row(result.getRows(), 0);
      assertEquals("1", row.get("a"));
      assertEquals("2", row.get("b"));
      assertTrue(result.isTruncated());
   }

   @Test
   public void returnsRowsThatAreUnmodifiableAllTheWayDown() {
      // Held by the query, shared with every clone of it and with the response that reports them,
      // so an in-place mutation anywhere would corrupt state shared across query instances.
      List<?> rows = JsonRowSampler.sample(
         List.of(map("id", "ch_1", "dispute", map("status", "won"))), 20).getRows();

      Map<String, Object> row = row(rows, 0);
      Map<?, ?> nested = assertInstanceOf(Map.class, row.get("dispute"));

      assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) rows).add(map()));
      assertThrows(UnsupportedOperationException.class, () -> row.put("id", "other"));
      assertThrows(UnsupportedOperationException.class,
                   () -> ((Map<String, Object>) nested).put("status", "lost"));
   }

   @Test
   public void convertsJsonPValuesToPlainJava() {
      // JSON-P leaves are wrapper objects (JsonString, JsonNumber, JsonValue.NULL). Left as they
      // are, the response would serialize the wrappers rather than the values, and a caller could
      // not use one as a parameter. Converted through the same function the table uses, so a
      // sampled value and the built table's value agree.
      List<?> rows = JsonRowSampler.sample(
         parse("[{\"id\":\"ch_1\",\"amount\":2000,\"live\":true,\"note\":null,"
                  + "\"tags\":[\"a\"],\"dispute\":{\"status\":\"won\"}}]"), 20).getRows();

      Map<String, Object> row = row(rows, 0);
      assertEquals("ch_1", row.get("id"));
      assertEquals(2000.0, row.get("amount"));
      assertEquals(Boolean.TRUE, row.get("live"));
      assertNull(row.get("note"));
      assertEquals(List.of("a"), row.get("tags"));

      Map<?, ?> dispute = assertInstanceOf(Map.class, row.get("dispute"));
      assertEquals("won", dispute.get("status"));
   }
}

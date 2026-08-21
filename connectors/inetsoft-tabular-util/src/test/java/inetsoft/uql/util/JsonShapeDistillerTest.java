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

import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonStructure;
import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No Spring context here, unlike {@link JsonTableTest}: the distiller is a pure function over a
 * parsed JSON value and stands up nothing.
 *
 * <p>Every case is driven through the JSON-P reader rather than through hand-built maps, because
 * JSON-P is one of the two object models that reach {@code distill} in production and it is the one
 * whose types need the special handling ({@code JsonNumber}, {@code JsonValue.NULL}). The plain-map
 * model is covered separately in {@link #distillsPlainJavaModelToo()}.</p>
 */
public class JsonShapeDistillerTest {
   private static Object parse(String json) {
      try(javax.json.JsonReader reader = Json.createReader(new StringReader(json))) {
         JsonStructure structure = reader.read();
         return structure;
      }
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> shapeOf(String json) {
      Object shape = JsonShapeDistiller.distill(parse(json)).getShape();
      assertInstanceOf(Map.class, shape);
      return (Map<String, Object>) shape;
   }

   @Test
   public void keepsEnvelopeAndRowArraySeparate() {
      Map<String, Object> shape = shapeOf(
         "{\"object\":\"list\",\"has_more\":false,\"data\":["
            + "{\"id\":\"ch_1\",\"amount\":2000,\"currency\":\"usd\"}]}");

      assertEquals(CoreTool.STRING, shape.get("object"));
      assertEquals(CoreTool.BOOLEAN, shape.get("has_more"));

      List<?> data = assertInstanceOf(List.class, shape.get("data"));
      assertEquals(1, data.size());

      Map<?, ?> row = assertInstanceOf(Map.class, data.get(0));
      assertEquals(CoreTool.STRING, row.get("id"));
      assertEquals(CoreTool.STRING, row.get("currency"));
   }

   @Test
   public void typesEveryJsonNumberAsDouble() {
      // Not integer, even for a whole number: JsonTable.getTypeClass maps every JsonNumber to
      // Double, so this is what the built table will report and the shape has to agree.
      Map<String, Object> shape = shapeOf("{\"amount\":2000,\"rate\":1.5}");

      assertEquals(CoreTool.DOUBLE, shape.get("amount"));
      assertEquals(CoreTool.DOUBLE, shape.get("rate"));
   }

   @Test
   public void carriesNoValues() {
      String json = "{\"id\":\"ch_1KsecretID\",\"email\":\"someone@example.com\"}";
      String rendered = shapeOf(json).toString();

      assertFalse(rendered.contains("ch_1KsecretID"));
      assertFalse(rendered.contains("someone@example.com"));
   }

   @Test
   public void mergesArrayElementsSoAnOptionalFieldSurvives() {
      // The first element omits "refunded". Taking element 0 alone would lose it, which is exactly
      // the field a caller most needs to be told about.
      Map<String, Object> shape = shapeOf(
         "{\"data\":[{\"id\":\"a\"},{\"id\":\"b\",\"refunded\":true}]}");

      List<?> data = assertInstanceOf(List.class, shape.get("data"));
      Map<?, ?> row = assertInstanceOf(Map.class, data.get(0));

      assertEquals(CoreTool.STRING, row.get("id"));
      assertEquals(CoreTool.BOOLEAN, row.get("refunded"));
   }

   @Test
   public void emptyArrayYieldsNoElementShape() {
      Map<String, Object> shape = shapeOf("{\"data\":[]}");
      assertEquals(Collections.emptyList(), shape.get("data"));
   }

   @Test
   public void nestedArrayKeepsItsElementShapeForExpansion() {
      Map<String, Object> shape = shapeOf(
         "{\"data\":[{\"id\":\"a\",\"fee_details\":[{\"amount\":30,\"type\":\"fee\"}]}]}");

      Map<?, ?> row = (Map<?, ?>) ((List<?>) shape.get("data")).get(0);
      List<?> fees = assertInstanceOf(List.class, row.get("fee_details"));
      Map<?, ?> fee = assertInstanceOf(Map.class, fees.get(0));

      assertEquals(CoreTool.DOUBLE, fee.get("amount"));
      assertEquals(CoreTool.STRING, fee.get("type"));
   }

   @Test
   public void collapsesDictionaryKeyedByOpaqueId() {
      // Three entries only -- no count threshold could ever see this, so the key-shape rule is the
      // only thing standing between a global catalogue and three real customer ids.
      Map<String, Object> shape = shapeOf(
         "{\"balances\":{"
            + "\"cus_9f2aB1\":{\"available\":400},"
            + "\"cus_71bdX9\":{\"available\":0},"
            + "\"cus_44kkQ2\":{\"available\":12}}}");

      Map<?, ?> balances = assertInstanceOf(Map.class, shape.get("balances"));
      assertEquals(Set.of(JsonShapeDistiller.WILDCARD_KEY), balances.keySet());

      Map<?, ?> element = assertInstanceOf(Map.class, balances.get(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(CoreTool.DOUBLE, element.get("available"));
      assertFalse(shape.toString().contains("cus_"));
   }

   @Test
   public void collapsesDictionaryKeyedByDate() {
      Map<String, Object> shape = shapeOf(
         "{\"daily\":{\"2026-08-01\":{\"count\":3},\"2026-08-02\":{\"count\":9}}}");

      Map<?, ?> daily = assertInstanceOf(Map.class, shape.get("daily"));
      assertEquals(Set.of(JsonShapeDistiller.WILDCARD_KEY), daily.keySet());
   }

   @Test
   public void collapsesLargeHomogeneousDictionaryWithOrdinaryKeys() {
      // Keys are plain words, so the key-shape rule cannot fire; count plus homogeneity must.
      StringBuilder json = new StringBuilder("{\"byRegion\":{");

      for(int i = 0; i < JsonShapeDistiller.DICTIONARY_MIN_KEYS + 5; i++) {
         json.append(i > 0 ? "," : "").append("\"region").append((char) ('a' + i % 26))
            .append(i).append("\":{\"total\":1}");
      }

      json.append("}}");

      Map<?, ?> byRegion = assertInstanceOf(Map.class, shapeOf(json.toString()).get("byRegion"));
      assertEquals(Set.of(JsonShapeDistiller.WILDCARD_KEY), byRegion.keySet());
   }

   @Test
   public void doesNotCollapseAWideRecord() {
      // 60 flat fields with MIXED shapes. Over the count threshold, so only the homogeneity
      // requirement keeps the real field names from being stripped to "*".
      StringBuilder json = new StringBuilder("{");

      for(int i = 0; i < 60; i++) {
         json.append(i > 0 ? "," : "").append("\"field").append(i).append("\":")
            .append(i % 3 == 0 ? "1" : (i % 3 == 1 ? "\"s\"" : "{\"nested\":true}"));
      }

      json.append("}");
      Map<String, Object> shape = shapeOf(json.toString());

      assertFalse(shape.containsKey(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(60, shape.size());
      assertEquals(CoreTool.DOUBLE, shape.get("field0"));
      assertEquals(CoreTool.STRING, shape.get("field1"));
   }

   @Test
   public void doesNotMistakeSnakeCaseFieldNamesForIds() {
      // Regression. The prefixed-id pattern once read "two-to-six lowercase letters, underscore,
      // four-plus alphanumerics", which is also the shape of has_more / fee_details /
      // available_on / reporting_category. One such key collapsed the entire record and replaced
      // every real field name with "*" -- on the majority of real responses, since snake_case is
      // the prevailing JSON field convention.
      Map<String, Object> shape = shapeOf(
         "{\"has_more\":false,\"available_on\":1,\"reporting_category\":\"x\","
            + "\"fee_details\":[{\"amount\":1}]}");

      assertFalse(shape.containsKey(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(CoreTool.BOOLEAN, shape.get("has_more"));
      assertEquals(CoreTool.DOUBLE, shape.get("available_on"));
      assertEquals(CoreTool.STRING, shape.get("reporting_category"));
      assertInstanceOf(List.class, shape.get("fee_details"));
   }

   @Test
   public void doesNotMistakeAWordFollowedByANumberForAnId() {
      // Fiscal-year, quarter and batch fields are named this way. The id pattern once required only
      // a digit in the suffix, which batch_2024 and fy_2023 satisfy -- and one matching key collapses
      // the whole record, so a single such field cost every real field name in it.
      Map<String, Object> shape = shapeOf(
         "{\"batch_2024\":1,\"fy_2023\":2,\"revenue\":3,\"detail\":{\"n\":1}}");

      assertFalse(shape.containsKey(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(CoreTool.DOUBLE, shape.get("batch_2024"));
      assertEquals(CoreTool.DOUBLE, shape.get("fy_2023"));
   }

   @Test
   public void doesNotCollapseFieldNamesThatFallOutsideThePattern() {
      // Two more that were raised as suspects and do NOT match: q1_2025's prefix carries a digit, so
      // it is not `[a-z]{2,6}`, and week_52's suffix is under the four-character minimum. Pinned so
      // the pattern cannot widen into them later.
      Map<String, Object> shape = shapeOf("{\"q1_2025\":1,\"week_52\":2}");

      assertFalse(shape.containsKey(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(2, shape.size());
   }

   @Test
   public void stillCollapsesPrefixedIdsThatCarryADigit() {
      Map<String, Object> shape = shapeOf(
         "{\"m\":{\"evt_00Ab\":{\"n\":1},\"acct_1H2x\":{\"n\":2}}}");

      Map<?, ?> m = assertInstanceOf(Map.class, shape.get("m"));
      assertEquals(Set.of(JsonShapeDistiller.WILDCARD_KEY), m.keySet());
   }

   @Test
   public void doesNotCollapseAUniformNarrowRecord() {
      // Homogeneous but far under the threshold, and the keys are field names. A record.
      Map<String, Object> shape = shapeOf("{\"first\":\"a\",\"last\":\"b\",\"city\":\"c\"}");

      assertFalse(shape.containsKey(JsonShapeDistiller.WILDCARD_KEY));
      assertEquals(3, shape.size());
   }

   @Test
   public void mixedTypesDegradeToStringLikeTheTableDoes() {
      Map<String, Object> shape = shapeOf("{\"data\":[{\"v\":1},{\"v\":\"text\"}]}");

      Map<?, ?> row = (Map<?, ?>) ((List<?>) shape.get("data")).get(0);
      assertEquals(CoreTool.STRING, row.get("v"));
   }

   @Test
   public void jsonNullIsNotTypedFromItsImplementationClass() {
      Map<String, Object> shape = shapeOf("{\"note\":null}");
      assertEquals(CoreTool.NULL, shape.get("note"));
   }

   @Test
   public void nullMergesAwayWhenAnotherElementHasTheType() {
      Map<String, Object> shape = shapeOf("{\"data\":[{\"note\":null},{\"note\":\"text\"}]}");

      Map<?, ?> row = (Map<?, ?>) ((List<?>) shape.get("data")).get(0);
      assertEquals(CoreTool.STRING, row.get("note"));
   }

   @Test
   public void topLevelArrayShapesToASingleElement() {
      Object shape = JsonShapeDistiller.distill(parse("[{\"id\":\"a\"},{\"id\":\"b\"}]")).getShape();
      List<?> list = assertInstanceOf(List.class, shape);

      assertEquals(1, list.size());
      assertEquals(CoreTool.STRING, ((Map<?, ?>) list.get(0)).get("id"));
   }

   @Test
   public void reportsTruncationWhenTheNodeCapFires() {
      JsonShapeDistiller.Result result = JsonShapeDistiller.distill(
         parse("{\"a\":1,\"b\":2,\"c\":3,\"d\":4}"), 3, JsonShapeDistiller.DEFAULT_MAX_DEPTH);

      assertTrue(result.isTruncated());
   }

   @Test
   public void reportsTruncationWhenTheDepthCapFires() {
      JsonShapeDistiller.Result result = JsonShapeDistiller.distill(
         parse("{\"a\":{\"b\":{\"c\":{\"d\":1}}}}"), JsonShapeDistiller.DEFAULT_MAX_NODES, 2);

      assertTrue(result.isTruncated());
   }

   @Test
   @SuppressWarnings("unchecked")
   public void returnsAnUnmodifiableShape() {
      // The shape is held by a query, shared by reference with every clone of it and with the
      // response that reports it, so "immutable once set" has to be enforced rather than documented:
      // one in-place put would corrupt state shared across query instances, and nothing would fail
      // where the mistake was made.
      Map<String, Object> shape = shapeOf(
         "{\"data\":[{\"id\":\"a\",\"customer\":{\"city\":\"x\"}}]}");

      assertThrows(UnsupportedOperationException.class, () -> shape.put("extra", "string"));

      // ...and all the way down, not just at the root.
      List<Object> data = (List<Object>) shape.get("data");
      assertThrows(UnsupportedOperationException.class, () -> data.add("string"));

      Map<String, Object> row = (Map<String, Object>) data.get(0);
      assertThrows(UnsupportedOperationException.class, () -> row.put("extra", "string"));

      Map<String, Object> customer = (Map<String, Object>) row.get("customer");
      assertThrows(UnsupportedOperationException.class, () -> customer.put("extra", "string"));
   }

   @Test
   public void doesNotReportTruncationOnAnOrdinaryResponse() {
      JsonShapeDistiller.Result result = JsonShapeDistiller.distill(
         parse("{\"data\":[{\"id\":\"a\",\"amount\":1}]}"));

      assertFalse(result.isTruncated());
   }

   @Test
   @SuppressWarnings("unchecked")
   public void distillsPlainJavaModelToo() {
      // jayway's Jackson provider hands back plain maps and lists rather than JSON-P values, and
      // both models reach this code depending on the path the response took.
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", "ch_1");
      row.put("amount", 2000);
      row.put("live", Boolean.TRUE);

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("data", new ArrayList<>(List.of(row)));

      Map<String, Object> shape =
         (Map<String, Object>) JsonShapeDistiller.distill(response).getShape();
      Map<?, ?> shapedRow = (Map<?, ?>) ((List<?>) shape.get("data")).get(0);

      assertEquals(CoreTool.STRING, shapedRow.get("id"));
      assertEquals(CoreTool.INTEGER, shapedRow.get("amount"));
      assertEquals(CoreTool.BOOLEAN, shapedRow.get("live"));
   }
}

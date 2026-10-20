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
package inetsoft.web.wiz.binding.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.binding.model.graph.calc.PercentCalcInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks {@link FieldRef}'s wire contract as a <em>request</em> type.
 *
 * <p>It is easy to read it as a response shape — the chart and table reads return it — but it is
 * also the body of every binding write: {@code ShelfRequest.fields}, {@code SingleShelfRequest},
 * {@code AestheticFieldRequest}, {@code TableShelfRequest}, {@code TableFieldRequest}. It gained
 * two components and two secondary constructors for the per-measure chart type, and records bind
 * through the canonical constructor, so the reads a caller was already sending have to keep
 * binding with those components absent. Nothing covered that, and the live verification for that
 * change was about typing a measure rather than binding one.
 */
@Tag("core")
class FieldRefBindingTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void bindsTheShapeEveryCallerAlreadySends() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "PAID", "type": "measure", "aggregate": "Sum" }
         """, FieldRef.class);

      assertEquals("PAID", ref.column());
      assertEquals("measure", ref.type());
      assertEquals("Sum", ref.aggregate());
      assertNull(ref.dateLevel());
      assertNull(ref.namedGroup());
      assertNull(ref.chartType(), "absent means absent, not zero");
      assertNull(ref.runtimeChartType());
   }

   @Test
   void bindsADimensionWithItsDateLevel() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "ORDER_DATE", "type": "dimension", "dateLevel": "Year" }
         """, FieldRef.class);

      assertEquals("dimension", ref.type());
      assertEquals("Year", ref.dateLevel());
      assertNull(ref.chartType());
   }

   /** The shape a shelf write actually arrives in, since `fields` is a list. */
   @Test
   void bindsAListOfRefsTheWayShelfRequestHoldsThem() throws Exception {
      List<FieldRef> refs = mapper.readValue(
         """
         [ { "column": "PAID", "type": "measure", "aggregate": "Sum" },
           { "column": "DISCOUNT", "type": "measure", "aggregate": "Sum" } ]
         """, mapper.getTypeFactory().constructCollectionType(List.class, FieldRef.class));

      assertEquals(2, refs.size());
      assertEquals("DISCOUNT", refs.get(1).column());
      assertNull(refs.get(0).chartType());
   }

   /**
    * A ref read back from the chart read carries these, and handing it straight to a write is the
    * obvious next move. Binding has to keep working — the refusal is
    * {@code FieldRefFactory.requireType}'s job, and it can only refuse what reached it.
    */
   @Test
   void bindsTheTwoReadOnlyComponentsSoTheWriteCanRefuseThem() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "QUANTITY", "type": "measure", "aggregate": "Sum",
           "chartType": 5, "runtimeChartType": 1 }
         """, FieldRef.class);

      assertEquals(Integer.valueOf(5), ref.chartType());
      assertEquals(Integer.valueOf(1), ref.runtimeChartType());
   }

   @Test
   void serialisesBackToTheSameShape() throws Exception {
      String json = mapper.writeValueAsString(
         new FieldRef("PAID", "measure", "Sum", null, null, 5, 1));

      // Compared as JSON: a read-back's explicit calculateInfo null binds to the clear request
      // (bug #78865), which is deliberately not equal to the absent one the original carried.
      assertEquals(json, mapper.writeValueAsString(mapper.readValue(json, FieldRef.class)));
   }

   /**
    * Bug #76931 (VTB-034): {@code secondaryColumn} -- the second column a Covariance/Correlation/
    * WeightedAverage aggregate compares {@code column} against -- was entirely absent from this
    * record, so Jackson's default strict deserialization rejected any request naming it before
    * the handler ever ran.
    */
   @Test
   void bindsAndRoundTripsSecondaryColumn() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "DISCOUNT", "type": "measure", "aggregate": "Correlation",
           "secondaryColumn": "PAID" }
         """, FieldRef.class);

      assertEquals("PAID", ref.secondaryColumn());

      String json = mapper.writeValueAsString(ref);
      assertEquals(json, mapper.writeValueAsString(mapper.readValue(json, FieldRef.class)));
   }

   /**
    * Bug #78865: an explicit {@code calculateInfo: null} asks to clear the calculator, an omitted
    * key leaves it alone. Jackson maps both to a Java null on a record component, and for a
    * missing creator property it asks {@code getAbsentValue}, which delegates to
    * {@code getNullValue} by default -- so a deserializer overriding only the latter would make
    * an omitted key clear too (and silently strip every calculator on every write that omits it).
    */
   @Test
   void tellsAnExplicitNullCalculateInfoFromAnOmittedOne() throws Exception {
      FieldRef explicit = mapper.readValue(
         """
         { "column": "PAID", "type": "measure", "aggregate": "Sum", "calculateInfo": null }
         """, FieldRef.class);
      FieldRef omitted = mapper.readValue(
         """
         { "column": "PAID", "type": "measure", "aggregate": "Sum" }
         """, FieldRef.class);

      assertTrue(explicit.clearsCalculateInfo());
      assertNull(explicit.calculateInfo(), "the sentinel must never leave the record");
      assertFalse(omitted.clearsCalculateInfo(), "an omitted key must keep the calculator");
      assertNull(omitted.calculateInfo());
   }

   @Test
   void aCalculatorObjectStillBindsToItsSubtypeAndIsNotAClear() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "PAID", "type": "measure", "aggregate": "Sum",
           "calculateInfo": { "classType": "PERCENT", "level": 1 } }
         """, FieldRef.class);

      assertInstanceOf(PercentCalcInfo.class, ref.calculateInfo());
      assertFalse(ref.clearsCalculateInfo());
   }

   @Test
   void anExplicitNullInsideAListOfRefsClearsOnlyThatEntry() throws Exception {
      List<FieldRef> refs = mapper.readValue(
         """
         [ { "column": "A", "type": "measure", "calculateInfo": null },
           { "column": "B", "type": "measure" } ]
         """, mapper.getTypeFactory().constructCollectionType(List.class, FieldRef.class));

      assertTrue(refs.get(0).clearsCalculateInfo());
      assertFalse(refs.get(1).clearsCalculateInfo());
   }

   @Test
   void theClearRequestSerialisesAsNullAndSurvivesAColumnRename() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "paid", "type": "measure", "calculateInfo": null, "n": "90" }
         """, FieldRef.class);

      assertTrue(mapper.readTree(mapper.writeValueAsString(ref)).get("calculateInfo").isNull());

      FieldRef renamed = ref.withColumns("PAID", "OTHER");

      assertTrue(renamed.clearsCalculateInfo());
      assertEquals("PAID", renamed.column());
      assertEquals("OTHER", renamed.secondaryColumn());
      assertEquals("90", renamed.operand());
   }

   /** Bug #78856: the N/P operand, a string so a dynamic one round-trips. */
   @Test
   void bindsAndRoundTripsTheOperandAsAString() throws Exception {
      FieldRef ref = mapper.readValue(
         """
         { "column": "PAID", "type": "measure", "aggregate": "PthPercentile",
           "n": "$(p)" }
         """, FieldRef.class);

      assertEquals("$(p)", ref.operand());
      assertEquals("$(p)", mapper.readTree(mapper.writeValueAsString(ref)).get("n").asText(),
                   "the wire key is n, the one the plugin and get_table_binding use");
      assertNull(mapper.readValue(
         """
         { "column": "PAID", "type": "measure" }
         """, FieldRef.class).operand());
   }
}

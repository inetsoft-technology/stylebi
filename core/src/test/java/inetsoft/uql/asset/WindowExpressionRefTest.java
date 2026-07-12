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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WindowExpressionRef}. These pin the pushdown-parity contract: the
 * synthesized {@link WindowExpressionRef#getExpression()} text must be byte-for-byte identical
 * to what the wiz {@code expandWindowColumns} helper (wiz-services/src/v1/services/windowColumns.ts)
 * produces today.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowExpressionRefTest {

   private static SortRef sort(String attr, int order) {
      SortRef sortRef = new SortRef(new AttributeRef(attr));
      sortRef.setOrder(order);
      return sortRef;
   }

   // ---- getExpression() parity ------------------------------------------------------------

   @Test
   void rowNumber_withPartitionAndOrder() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new AttributeRef("stage")),
         List.of(sort("amount", XConstants.SORT_DESC)));

      assertEquals("ROW_NUMBER() OVER (PARTITION BY field['stage'] ORDER BY field['amount'] DESC)",
                   ref.getExpression());
   }

   @Test
   void ntile_withOrderOnly() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "NTILE", null, 4,
         List.of(),
         List.of(sort("amount", XConstants.SORT_DESC)));

      assertEquals("NTILE(4) OVER (ORDER BY field['amount'] DESC)", ref.getExpression());
   }

   @Test
   void lag_withOffsetAndOrder() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "LAG", new AttributeRef("amount"), 1,
         List.of(),
         List.of(sort("t", XConstants.SORT_ASC)));

      assertEquals("LAG(field['amount'], 1) OVER (ORDER BY field['t'] ASC)", ref.getExpression());
   }

   @Test
   void sum_withPartitionOnly_noOrderBy() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0,
         List.of(new AttributeRef("stage")),
         List.of());

      assertEquals("SUM(field['amount']) OVER (PARTITION BY field['stage'])", ref.getExpression());
   }

   @Test
   void neitherPartitionNorOrder_emitsEmptyParens() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(), List.of());

      assertEquals("SUM(field['amount']) OVER ()", ref.getExpression());
   }

   @Test
   void lag_withoutOffset_omitsComma() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "LAG", new AttributeRef("amount"), 0,
         List.of(),
         List.of(sort("t", XConstants.SORT_ASC)));

      assertEquals("LAG(field['amount']) OVER (ORDER BY field['t'] ASC)", ref.getExpression());
   }

   // ---- isSQL() ------------------------------------------------------------------------------

   @Test
   void isSQL_isTrue() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, List.of(), List.of(sort("t", XConstants.SORT_ASC)));
      assertTrue(ref.isSQL());
   }

   // ---- XML round-trip -----------------------------------------------------------------------

   @Test
   void xmlRoundTrip_preservesAllFields() throws Exception {
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new AttributeRef("stage")),
         List.of(sort("amount", XConstants.SORT_DESC)));

      WindowExpressionRef copy = (WindowExpressionRef) writeAndParse(ref);

      assertEquals(ref.getFn(), copy.getFn());
      assertEquals(ref.getN(), copy.getN());
      assertNull(copy.getArgRef());
      assertEquals(1, copy.getPartitionBy().size());
      assertEquals("stage", copy.getPartitionBy().get(0).getName());
      assertEquals(1, copy.getOrderBy().size());
      assertEquals("amount", copy.getOrderBy().get(0).getName());
      assertEquals(XConstants.SORT_DESC, copy.getOrderBy().get(0).getOrder());
      assertEquals(ref.getExpression(), copy.getExpression());
   }

   @Test
   void xmlRoundTrip_preservesName() throws Exception {
      // The column name/alias (e.g. "rnk") is a base-class ExpressionRef attribute set by the
      // wire (WorksheetTableService.applyWindowColumns -> setName). It MUST survive the
      // worksheet's XML serialize/reload cycle, otherwise the column reloads nameless and cannot
      // be bound by create_viewsheet ("Unknown field []").
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new AttributeRef("stage")),
         List.of(sort("amount", XConstants.SORT_DESC)));
      ref.setName("rnk");
      ref.setDataType(inetsoft.uql.schema.XSchema.INTEGER);

      WindowExpressionRef copy = (WindowExpressionRef) writeAndParse(ref);

      assertEquals("rnk", copy.getName(),
                   "the window column name must survive XML serialization");
      assertEquals(inetsoft.uql.schema.XSchema.INTEGER, copy.getDataType(),
                   "the window column data type must survive XML serialization");
      assertEquals(ref.getExpression(), copy.getExpression());
   }

   @Test
   void xmlRoundTrip_preservesArgRef() throws Exception {
      WindowExpressionRef ref = new WindowExpressionRef(
         "LAG", new AttributeRef("amount"), 1, List.of(), List.of(sort("t", XConstants.SORT_ASC)));

      WindowExpressionRef copy = (WindowExpressionRef) writeAndParse(ref);

      assertNotNull(copy.getArgRef());
      assertEquals("amount", copy.getArgRef().getName());
      assertEquals(1, copy.getN());
      assertEquals(ref.getExpression(), copy.getExpression());
   }

   private static DataRef writeAndParse(WindowExpressionRef ref) throws Exception {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      ref.writeXML(writer);
      writer.flush();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      Document doc = factory.newDocumentBuilder().parse(
         new ByteArrayInputStream(sw.toString().getBytes()));
      Element elem = doc.getDocumentElement();

      return inetsoft.uql.erm.AbstractDataRef.createDataRef(elem);
   }

   // ---- getBaseAttribute must not unwrap past the window ref ---------------------------------

   @Test
   void getBaseAttribute_stopsAtWindowRef_forNoArgFunction() {
      // ROW_NUMBER has a null argRef. WindowExpressionRef must NOT be a DataRefWrapper — otherwise
      // AssetUtil.getBaseAttribute unwraps a ColumnRef(WindowExpressionRef) straight past the window
      // ref to its null argRef, and query validation NPEs (PreAssetQuery.getContainedAttributes ->
      // attr.isExpression() on null). It must be treated as a base expression attribute, exactly
      // like a plain ExpressionRef (and like the AssetQuery window-pushdown guard assumes).
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0,
         List.of(new AttributeRef("stage")),
         List.of(sort("amount", XConstants.SORT_DESC)));
      ColumnRef col = new ColumnRef(ref);

      DataRef base = inetsoft.uql.asset.internal.AssetUtil.getBaseAttribute(col);

      assertNotNull(base, "getBaseAttribute must not unwrap past a no-arg window ref to a null argRef");
      assertInstanceOf(WindowExpressionRef.class, base,
                       "getBaseAttribute must return the WindowExpressionRef itself");
      assertTrue(base.isExpression());
   }

   // ---- getAttributes() must yield AttributeRefs (framework contract) ------------------------

   @Test
   void getAttributes_yieldsAttributeRefs_notColumnRefs() {
      // The wire (WorksheetTableService.applyWindowColumns) passes ColumnRefs for partitionBy/
      // orderBy (from ColumnSelection.getAttribute). getAttributes() must yield AttributeRefs
      // parsed from the field['..'] tokens (the ExpressionRef contract) — the query framework
      // casts each contained ref to AttributeRef (PreAssetQuery.getExprAttributes), so a ColumnRef
      // there is a ClassCastException during column-selection validation.
      ColumnRef stage = new ColumnRef(new AttributeRef("sales_stage"));
      SortRef amountSort = new SortRef(new ColumnRef(new AttributeRef("amount")));
      amountSort.setOrder(XConstants.SORT_DESC);
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, List.of(stage), List.of(amountSort));

      List<String> names = new ArrayList<>();
      Enumeration<?> e = ref.getAttributes();

      while(e.hasMoreElements()) {
         DataRef r = (DataRef) e.nextElement();
         assertInstanceOf(AttributeRef.class, r,
            "contained refs must be AttributeRefs parsed from field[..] tokens, not "
               + r.getClass().getSimpleName());
         names.add(r.getName());
      }

      assertTrue(names.contains("sales_stage"), "partitionBy column must be a contained attribute");
      assertTrue(names.contains("amount"), "orderBy column must be a contained attribute");
   }

   // ---- clone() deep-copies the lists ---------------------------------------------------------

   @Test
   void clone_deepCopiesPartitionByList() {
      List<DataRef> partitionBy = new ArrayList<>(List.of(new AttributeRef("stage")));
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, partitionBy, new ArrayList<>());

      WindowExpressionRef clone = (WindowExpressionRef) ref.clone();
      clone.getPartitionBy().add(new AttributeRef("region"));

      assertEquals(1, ref.getPartitionBy().size(), "mutating the clone must not affect the original");
      assertEquals(2, clone.getPartitionBy().size());
   }

   @Test
   void clone_deepCopiesOrderByList() {
      List<SortRef> orderBy = new ArrayList<>(List.of(sort("amount", XConstants.SORT_DESC)));
      WindowExpressionRef ref = new WindowExpressionRef(
         "ROW_NUMBER", null, 0, new ArrayList<>(), orderBy);

      WindowExpressionRef clone = (WindowExpressionRef) ref.clone();
      clone.getOrderBy().add(sort("t", XConstants.SORT_ASC));

      assertEquals(1, ref.getOrderBy().size(), "mutating the clone must not affect the original");
      assertEquals(2, clone.getOrderBy().size());
   }

   @Test
   void clone_deepCopiesArgRef() {
      // ColumnRef.clone() always allocates a new instance (unlike AttributeRef, which returns
      // `this` as a documented immutability optimization), so it is the right vehicle to prove
      // WindowExpressionRef.clone() actually calls argRef.clone() rather than sharing the
      // reference.
      ColumnRef argRef = new ColumnRef(new AttributeRef("amount"));
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", argRef, 0, new ArrayList<>(), new ArrayList<>());

      WindowExpressionRef clone = (WindowExpressionRef) ref.clone();

      assertNotSame(ref.getArgRef(), clone.getArgRef(),
                    "argRef must be deep-copied, not shared by reference");
      assertEquals(ref.getArgRef().getName(), clone.getArgRef().getName());
   }

   // ---- ROWS frame (Phase 3 Task 1) -----------------------------------------------------------

   @Test
   void explicitRowsFrame_emitted() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("t", XConstants.SORT_ASC)));
      ref.setFrame("PRECEDING", 2, "CURRENT_ROW", 0);
      assertEquals(
         "SUM(field['amount']) OVER (PARTITION BY field['stage'] ORDER BY field['t'] ASC "
         + "ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)",
         ref.getExpression());
   }

   @Test
   void lastValue_frameless_defaultsToWholePartition() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "LAST_VALUE", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("t", XConstants.SORT_ASC)));
      assertEquals(
         "LAST_VALUE(field['amount']) OVER (PARTITION BY field['stage'] ORDER BY field['t'] ASC "
         + "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)",
         ref.getExpression());
   }

   @Test
   void firstValue_frameless_emitsNoFrame_byteParity() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "FIRST_VALUE", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("t", XConstants.SORT_ASC)));
      assertEquals(
         "FIRST_VALUE(field['amount']) OVER (PARTITION BY field['stage'] ORDER BY field['t'] ASC)",
         ref.getExpression());
   }

   @Test
   void frameless_aggregate_byteParity_unchanged() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("t", XConstants.SORT_ASC)));
      assertEquals(
         "SUM(field['amount']) OVER (PARTITION BY field['stage'] ORDER BY field['t'] ASC)",
         ref.getExpression());
   }

   @Test
   void setFrame_precedingWithZeroOffset_throws() {
      // PRECEDING/FOLLOWING require a positive offset — setFrame is a programmatic-construction
      // entry point that bypasses WorksheetTableService's wire-level validation, so it must guard
      // itself: the in-memory WindowTableLens.boundPos does raw p+/-offset arithmetic and trusts
      // whatever is stored here.
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(), List.of(sort("t", XConstants.SORT_ASC)));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> ref.setFrame("PRECEDING", 0, "CURRENT_ROW", 0));
      assertTrue(ex.getMessage().contains("PRECEDING"));
   }

   @Test
   void setFrame_invalidBoundToken_throws() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(), List.of(sort("t", XConstants.SORT_ASC)));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> ref.setFrame("BOGUS_BOUND", 0, "CURRENT_ROW", 0));
      assertTrue(ex.getMessage().contains("BOGUS_BOUND"));
   }

   @Test
   void lastValue_lowercaseFn_stillDefaultsToWholePartition() {
      // A raw /ws/table caller sending lowercase fn must not silently lose the whole-partition
      // frame synthesis (Fix 5) — "last_value".equals("LAST_VALUE") is false, so the frame-less
      // branch must use equalsIgnoreCase.
      WindowExpressionRef ref = new WindowExpressionRef(
         "last_value", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("t", XConstants.SORT_ASC)));
      assertTrue(ref.getExpression().contains(
         "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"),
         "lowercase-fn LAST_VALUE must still emit the whole-partition frame: " + ref.getExpression());
   }

   @Test
   void frame_xmlRoundTrip() throws Exception {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(), List.of(sort("t", XConstants.SORT_ASC)));
      ref.setFrame("PRECEDING", 3, "FOLLOWING", 1);
      WindowExpressionRef copy = (WindowExpressionRef) writeAndParse(ref);
      assertEquals("PRECEDING", copy.getFrameStartBound());
      assertEquals(3, copy.getFrameStartOffset());
      assertEquals("FOLLOWING", copy.getFrameEndBound());
      assertEquals(1, copy.getFrameEndOffset());
      assertEquals(ref.getExpression(), copy.getExpression());
   }

   // ---- RANGE/GROUPS frame (Phase 4 Task 1) ---------------------------------------------------

   @Test
   void rangeNumericFrame_emitsRangeSql() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amount"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("amount", XConstants.SORT_ASC)));
      ref.setFrame("RANGE", "PRECEDING", 1000, "CURRENT_ROW", 0, null);
      assertTrue(ref.getExpression().contains(
         "RANGE BETWEEN 1000 PRECEDING AND CURRENT ROW"));
      assertEquals("RANGE", ref.getFrameMode());
      assertNull(ref.getFrameOffsetUnit());
   }

   @Test
   void rangeDateFrame_emitsIntervalSql() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amt"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("d", XConstants.SORT_ASC)));
      ref.setFrame("RANGE", "PRECEDING", 7, "CURRENT_ROW", 0, "day");
      assertTrue(ref.getExpression().contains(
         "RANGE BETWEEN INTERVAL '7 day' PRECEDING AND CURRENT ROW"));
      assertEquals("day", ref.getFrameOffsetUnit());
   }

   @Test
   void groupsFrame_emitsGroupsSql() {
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amt"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("d", XConstants.SORT_ASC)));
      ref.setFrame("GROUPS", "PRECEDING", 2, "CURRENT_ROW", 0, null);
      assertTrue(ref.getExpression().contains(
         "GROUPS BETWEEN 2 PRECEDING AND CURRENT ROW"));
      assertEquals("GROUPS", ref.getFrameMode());
   }

   @Test
   void rowsFrame_byteParityWithPhase3() {
      // mode omitted / legacy 4-arg overload -- ROWS output must be byte-identical to Phase 3.
      WindowExpressionRef ref = new WindowExpressionRef(
         "SUM", new AttributeRef("amt"), 0, List.of(new AttributeRef("stage")),
         List.of(sort("d", XConstants.SORT_ASC)));
      ref.setFrame("PRECEDING", 2, "CURRENT_ROW", 0);
      assertTrue(ref.getExpression().contains("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW"));
      assertEquals("ROWS", ref.getFrameMode());
      assertNull(ref.getFrameOffsetUnit());
   }
}

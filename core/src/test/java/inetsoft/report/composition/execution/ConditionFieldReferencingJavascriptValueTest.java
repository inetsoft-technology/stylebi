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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WBS-042 / Redmine #76627 regression: {@code field['ColumnName']} inside a worksheet
 * CONDITION's JAVASCRIPT-typed value now gets a genuine per-row binding (mirroring a worksheet
 * expression column's own {@code field['Col']} mechanism), instead of being resolved once as a
 * fixed scalar before any row is known (see {@code bug-wbs042/DESIGN.md}).
 *
 * <p>Built from {@code JoinDuplicateColumnAliasMechanismTest}'s real (non-mocked)
 * {@link AssetQuerySandbox}/{@link Worksheet}/{@link EmbeddedTableAssembly} wiring, and
 * {@code AssetDataCache}'s own {@code AssetQuery.createAssetQuery(table, mode, box, false, ts,
 * true, false)} call shape for running a table assembly's own query directly.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionFieldReferencingJavascriptValueTest {
   /** All (CUSTOMER_ID, REGION_ID) row pairs actually present in the rendered table, skipping the header. */
   private static Set<String> rowPairs(TableLens lens) {
      Set<String> pairs = new HashSet<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         pairs.add(lens.getObject(r, 0) + "/" + lens.getObject(r, 1));
      }

      return pairs;
   }

   /**
    * Builds a worksheet with a single embedded CUSTOMERS(CUSTOMER_ID, REGION_ID) table and three
    * data rows: two where the columns are equal ("A"/"A", "D"/"D") and one where they are not
    * ("B"/"C") -- real per-row data that a {@code CUSTOMER_ID = field['REGION_ID']} condition
    * should discriminate between, not match uniformly (all rows / zero rows).
    */
   private static EmbeddedTableAssembly customersTable(Worksheet ws) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "CUSTOMERS");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef("CUSTOMER_ID")));
      cs.addAttribute(new ColumnRef(new AttributeRef("REGION_ID")));
      table.setColumnSelection(cs, false);
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "CUSTOMER_ID", "REGION_ID" },
            { "A", "A" },
            { "B", "C" },
            { "D", "D" },
         }));
      ws.addAssembly(table);
      return table;
   }

   private static TableLens run(Worksheet ws, EmbeddedTableAssembly table) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws, null, new VariableTable());
      box.setActive(true);

      AssetQuery query = AssetQuery.createAssetQuery(
         table, AssetQuerySandbox.LIVE_MODE, box, false, -1L, true, false);
      TableLens lens = query.getTableLens(new VariableTable());
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /**
    * The falsifiable claim from bug-wbs042/01-diagnosis.md, now expected to hold: a JS-typed
    * condition value referencing field['REGION_ID'] must discriminate per row (match only where
    * CUSTOMER_ID == REGION_ID), not match every row (the pre-fix symptom) and not match zero rows.
    */
   @Test
   void fieldReferencingJavascriptConditionDiscriminatesPerRow() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = customersTable(ws);
      ColumnRef customerId = (ColumnRef) table.getColumnSelection(false).getAttribute("CUSTOMER_ID");

      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression("field['REGION_ID']");
      cond.addValue(eval);

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(customerId, cond, 0));
      table.setPreConditionList(conds);

      TableLens lens = run(ws, table);

      assertEquals(Set.of("A/A", "D/D"), rowPairs(lens),
         "field['REGION_ID'] must discriminate per row -- only rows where CUSTOMER_ID == " +
         "REGION_ID should remain, not all rows (the pre-fix symptom) and not zero rows");
   }

   /**
    * No regression on the dominant, already-working case: a JAVASCRIPT-typed condition value
    * that does NOT reference field[...] (a one-time scalar, e.g. a literal) must still resolve
    * exactly as before -- once per query, not per row.
    */
   @Test
   void plainJavascriptConditionWithoutFieldReferenceStillWorks() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = customersTable(ws);
      ColumnRef regionId = (ColumnRef) table.getColumnSelection(false).getAttribute("REGION_ID");

      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression("'C'");
      cond.addValue(eval);

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(regionId, cond, 0));
      table.setPreConditionList(conds);

      TableLens lens = run(ws, table);

      assertEquals(Set.of("B/C"), rowPairs(lens),
         "a plain JAVASCRIPT condition value with no field[...] reference must still resolve " +
         "to its literal scalar and filter correctly, exactly as before this change");
   }

   /**
    * The SQL-typed sibling is untouched by this change: {@link ExpressionValue#referencesField()}
    * -- the only new gate this change adds to condition mergeability -- is gated to JAVASCRIPT,
    * so an SQL-typed field['Col'] expression is never affected by it and keeps using its existing
    * textual substitution (PreAssetQuery#parseFieldExpression, not modified by this change at
    * all). See ExpressionValueReferencesFieldTest#sqlTypeWithFieldBracketSyntax_false for the
    * direct unit-level proof of this gate.
    */
   @Test
   void sqlTypedFieldReferenceIsNotFlaggedByTheNewDetector() {
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.SQL);
      eval.setExpression("field['REGION_ID']");

      assertEquals(false, eval.referencesField(),
         "the mergeability gate this change adds only ever fires for JAVASCRIPT-typed values -- " +
         "an SQL-typed field['Col'] expression must be reported as not needing the new per-row " +
         "path, leaving parseFieldExpression's own textual substitution as the sole, unaffected " +
         "mechanism for it");
   }

   /**
    * Fix-round regression (code review of PR #5230, finding #1): {@code AssetQuery
    * #getSummaryTableLens}'s in-memory ("Java-side") grouping branch applies postConditions via
    * {@code AssetConditionGroup2}/{@code SummaryFilter2} -- a completely different call path from
    * the per-row {@code AssetConditionGroup}/{@code ConditionFilter} mechanism the original pass
    * fixed, reached whenever grouping/aggregation itself isn't pushed to SQL (true for ANY
    * {@link EmbeddedTableAssembly} with a GROUP BY, not an edge case). The original pass left this
    * path unreached, so a field-referencing JAVASCRIPT postCondition (HAVING) here still resolved
    * {@code field[...]} exactly once, before any group was known -- the original bug, just on a
    * different table shape.
    *
    * <p>Per the review's option (b): {@code AssetConditionGroup2} evaluates a postCondition against
    * a {@code GroupNode}'s {@code Object[]} of already-aggregated values, not a {@link TableLens}
    * row, and there is no reliable column-name-to-array-position mapping for that shape to safely
    * bind {@code field['Col']} against (unlike the per-row path, which reuses {@code TableRow}'s
    * existing header-based column lookup on a real table). Rather than risk a silently-wrong
    * per-row binding, this case now fails loud with a clear, field-named error at condition-group
    * construction time -- asserted here.</p>
    */
   @Test
   void fieldReferencingJavascriptPostConditionOnInMemoryGroupingFailsLoud() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "SALES");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef regionCol = new ColumnRef(new AttributeRef("REGION"));
      ColumnRef salesCol = new ColumnRef(new AttributeRef("SALES_AMOUNT"));
      cs.addAttribute(regionCol);
      cs.addAttribute(salesCol);
      table.setColumnSelection(cs, false);
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double" },
         new Object[][]{
            { "REGION", "SALES_AMOUNT" },
            { "East", 100.0 },
            { "East", 200.0 },
            { "West", 50.0 },
         }));
      ws.addAssembly(table);

      // GROUP BY REGION, SUM(SALES_AMOUNT) -- an EmbeddedTableAssembly has no SQL backing, so this
      // aggregation is always evaluated in-memory (mexecuted == false), reaching AssetConditionGroup2
      // (not the per-row AssetConditionGroup path this PR's other tests already cover).
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(regionCol));
      ainfo.addAggregate(new AggregateRef(salesCol, AggregateFormula.SUM));
      table.setAggregateInfo(ainfo);
      table.setAggregate(true);

      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression("field['REGION']");
      cond.addValue(eval);

      ConditionList postConds = new ConditionList();
      postConds.append(new ConditionItem(regionCol, cond, 0));
      table.setPostConditionList(postConds);

      ScriptException ex = assertThrows(ScriptException.class, () -> run(ws, table),
         "a field-referencing JAVASCRIPT postCondition reaching the in-memory-grouping " +
         "(AssetConditionGroup2/SummaryFilter2) path must fail loud instead of silently " +
         "resolving field[...] once before any group is known");
      assertTrue(ex.getMessage() != null && ex.getMessage().contains("field"),
         "the failure should name the actual problem (a field[...]/field. reference), not a " +
         "generic/unrelated error: " + ex.getMessage());
   }

   /**
    * Fix-round regression (code review of PR #5230, finding #2): {@link AssetCondition#evaluate}
    * short-circuits a ONE_OF/CONTAINS condition with its own one-value cache ({@code lvalue}/
    * {@code lresult}) whenever {@link AssetCondition#isOptimized()} is true -- a fast path this
    * class never disables for a field[...] binding, because {@code ConditionGroup#addCondition}
    * only ever flips a condition's {@code optimized} flag off when one of its VALUES is a
    * {@link inetsoft.uql.erm.DataRef} (the pre-existing "field-as-value" mechanism); a field[...]
    * JAVASCRIPT expression is an {@link ExpressionValue}, which that check never sees. So a
    * ONE_OF/CONTAINS condition built from field[...] stays optimized, and {@code
    * AssetConditionGroup#evaluate}'s {@code clearCache()} call (which only resets {@link
    * inetsoft.uql.Condition}'s {@code sortedValues}) never touches that cache -- two consecutive
    * rows whose tested column happens to repeat the same value, but whose field[...]-referenced
    * column differs, wrongly reuse the first row's cached boolean instead of being re-evaluated.
    *
    * <p>Table: CUSTOMER_ID repeats "A" across rows 1-2 while REGION_ID differs (row 1: A/A, row 2:
    * A/B); condition is {@code CUSTOMER_ID ONE_OF [field['REGION_ID']]}. Row 1 must match (A is
    * one of {A}); row 2 must NOT match (A is not one of {B}) -- the pre-fix symptom is row 2
    * wrongly matching too, because AssetCondition's cache still holds row 1's (lvalue="A",
    * lresult=true).</p>
    */
   @Test
   void fieldReferencingOneOfConditionDoesNotReuseAPriorRowsCachedResult() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "CUSTOMERS");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef("CUSTOMER_ID")));
      cs.addAttribute(new ColumnRef(new AttributeRef("REGION_ID")));
      table.setColumnSelection(cs, false);
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" },
         new Object[][]{
            { "CUSTOMER_ID", "REGION_ID" },
            { "A", "A" },
            { "A", "B" },
            { "C", "C" },
         }));
      ws.addAssembly(table);
      ColumnRef customerId = (ColumnRef) table.getColumnSelection(false).getAttribute("CUSTOMER_ID");

      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression("field['REGION_ID']");
      cond.addValue(eval);

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(customerId, cond, 0));
      table.setPreConditionList(conds);

      TableLens lens = run(ws, table);

      assertEquals(Set.of("A/A", "C/C"), rowPairs(lens),
         "row 2 (A/B) must be re-evaluated against its own field['REGION_ID'] (\"B\") and " +
         "excluded, not match because row 1 (A/A) already cached lvalue=\"A\"/lresult=true for " +
         "the same tested CUSTOMER_ID value");
   }
}

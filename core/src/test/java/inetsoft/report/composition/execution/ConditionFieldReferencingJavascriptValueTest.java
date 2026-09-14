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
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

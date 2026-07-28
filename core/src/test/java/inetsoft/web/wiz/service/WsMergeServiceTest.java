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
package inetsoft.web.wiz.service;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression coverage for a live bug: when two charts merged into the same dashboard both bind
 * to the same physical table (matched by {@code SourceInfo}, e.g. two charts both reading
 * {@code product_template}) but one chart's own copy selects more columns than the other's,
 * {@link WsMergeService#mergeColumns} only added the extra columns to the merged table's PUBLIC
 * column selection -- never its PRIVATE (actually-selected/fetched) selection. The very next
 * {@code resetColumnSelection()} call during query construction regenerates the public selection
 * FROM the private one, silently discarding the merge and reverting the shared table back to only
 * the narrower chart's columns. Confirmed live: a dashboard merging a "category" chart (whose own
 * copy of the product_template table selects just 2 columns) with a "product" chart (whose own
 * copy additionally selects a JSON name column used by a downstream JS-expression calc) ended up
 * with the JS expression's dependency silently missing, evaluating to null for every row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsMergeServiceTest {
   private final WsMergeService service = new WsMergeService();

   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName,
                                                            String... columns)
   {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.product_template");
      table.setSourceInfo(si);
      ColumnSelection cs = new ColumnSelection();

      for(String name : columns) {
         AttributeRef ref = new AttributeRef(null, name);
         ref.setDataType(XSchema.STRING);
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(XSchema.STRING);
         cs.addAttribute(col);
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   @Test
   void mergingATableWithExtraColumnsAddsThemToBothPublicAndPrivateSelection() {
      // dashWS already has a narrower chart's own copy of the shared physical table merged in.
      Worksheet dashWS = new Worksheet();
      dashWS.addAssembly(physicalTable(dashWS, "PT", "pt_id", "categ_id"));

      // A second chart's own worksheet binds to the SAME physical source, but selects one more
      // column (the JSON name column a downstream calc depends on).
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTable(vizWS, "PT", "pt_id", "categ_id", "product_name_json"));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      // ensureBaseHasPrevMirror renames the pre-existing "PT" to "PT_base" once a prevMirror is
      // created over it -- that renamed table is where mergeColumns adds the extra column.
      TableAssembly merged = (TableAssembly) dashWS.getAssembly("PT_base");
      assertNotNull(merged, "expected the existing 'PT' to be promoted to 'PT_base'");

      ColumnSelection privateCols = merged.getColumnSelection(false);
      assertNotNull(privateCols.getAttribute("product_name_json"),
         "merged column must be added to the PRIVATE selection, not just public -- otherwise " +
         "the next resetColumnSelection() silently drops it again");

      ColumnSelection publicCols = merged.getColumnSelection(true);
      assertNotNull(publicCols.getAttribute("product_name_json"));

      // "PT" (the prevMirror created over the renamed base) is the table downstream joins
      // actually reference -- it needs the SAME fix independently: ensureBaseHasPrevMirror's own
      // prevMirror-refresh step only updated the public selection until this fix, so "PT" is
      // where the bug reproduced live even after mergeColumns alone was fixed.
      TableAssembly prevMirror = (TableAssembly) dashWS.getAssembly("PT");
      assertNotNull(prevMirror, "expected a prevMirror named 'PT' to be created");

      // A mirror's PRIVATE selection references its base's columns by OUTER ATTRIBUTE --
      // qualified by the base's name ("PT_base.product_name_json"), matching the qualified shape
      // of its sibling columns ("PT_base.pt_id") -- not the base's bare column name.
      ColumnSelection mirrorPrivateCols = prevMirror.getColumnSelection(false);
      assertNotNull(mirrorPrivateCols.getAttribute("PT_base.product_name_json"),
         "the prevMirror's PRIVATE selection must also get the merged column (outer-attribute " +
         "qualified, matching its siblings), not just public");

      ColumnSelection mirrorPublicCols = prevMirror.getColumnSelection(true);
      assertNotNull(mirrorPublicCols.getAttribute("product_name_json"));
   }

   private static GroupRef groupRef(String field) {
      AttributeRef ref = new AttributeRef(null, field);
      ref.setDataType(XSchema.STRING);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(XSchema.STRING);
      return new GroupRef(col);
   }

   private static AggregateRef aggregateRef(String field, AggregateFormula formula) {
      AttributeRef ref = new AttributeRef(null, field);
      ref.setDataType(XSchema.DOUBLE);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(XSchema.DOUBLE);
      return new AggregateRef(col, formula);
   }

   private static PhysicalBoundTableAssembly physicalTableWithAggregate(
      Worksheet ws, String assemblyName, String groupField, String aggregateField,
      AggregateFormula formula)
   {
      PhysicalBoundTableAssembly table = physicalTable(ws, assemblyName, groupField, aggregateField);
      AggregateInfo aggr = new AggregateInfo();
      aggr.addGroup(groupRef(groupField));
      aggr.addAggregate(aggregateRef(aggregateField, formula));
      table.setAggregateInfo(aggr);
      return table;
   }

   /**
    * Regression for a reproduced live crash: two charts on a dashboard both bind to the same
    * physical table (e.g. two independent charts both grouping odoo's sale_order by
    * Quarter(date_order), each with its own distinct aggregate) but with DIFFERENT groupings/
    * aggregates baked directly onto their own BoundTableAssembly (the shape create_worksheet_table
    * produces). The second chart to merge used to stack its own condMirror on top of "prevMirror"
    * -- which, per ensureBaseHasPrevMirror, inherits the FIRST chart's own AggregateInfo -- so the
    * second chart's aggregation ran on top of an ALREADY aggregated, differently-grouped result.
    * That produced a duplicate output column name the SQL engine silently disambiguated with a
    * "_1" suffix, which the FIRST chart's own (unrelated) VSChartInfo binding was never told
    * about, crashing ITS graph render the next time its dashboard tile opened
    * (ColumnNotFoundException) -- confirmed live against a real StyleBI deployment. The fix:
    * stack on the raw "_base" table instead whenever prevMirror carries its own non-empty
    * AggregateInfo.
    */
   @Test
   void mergingTwoChartsWithDifferentOwnAggregatesOnTheSamePhysicalTableStacksOnTheRawBase() {
      Worksheet dashWS = new Worksheet();
      // First chart: e.g. a boxplot grouping by "quarter", aggregating "amount_total".
      dashWS.addAssembly(physicalTableWithAggregate(dashWS, "PT", "quarter", "amount_total", AggregateFormula.NONE));

      // Second chart: a DIFFERENT own aggregation (e.g. Count/Average) on the SAME physical table.
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithAggregate(vizWS, "PT", "quarter", "order_count", AggregateFormula.COUNT_ALL));

      Map<String, String> wsRenameMap = service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      String secondChartFinalName = wsRenameMap.get("PT");
      assertNotNull(secondChartFinalName, "expected the second chart's table to be mapped to a merged name");

      TableAssembly secondChartMirror = (TableAssembly) dashWS.getAssembly(secondChartFinalName);
      assertNotNull(secondChartMirror, "expected the second chart's condMirror to exist in dashWS");
      assertEquals(MirrorTableAssembly.class, secondChartMirror.getClass());

      // Must stack on "PT_base" (the raw, unaggregated table) -- NOT "PT" (prevMirror, which
      // carries the FIRST chart's own incompatible "quarter"/"amount_total" aggregation).
      String stackedOn = ((MirrorTableAssembly) secondChartMirror).getAssemblyName();
      assertEquals("PT_base", stackedOn,
         "the second chart's own aggregation must stack on the raw base table, not on a " +
         "prevMirror that already carries a different chart's own incompatible aggregation");
   }

   private static ColumnRef rawColumn(String name) {
      AttributeRef ref = new AttributeRef(null, name);
      ref.setDataType(XSchema.STRING);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(XSchema.STRING);
      return col;
   }

   /**
    * Builds a table whose public (output) selection is JUST its aggregate's own output names,
    * and whose private (fetched) selection carries BOTH those output names AND the genuine
    * underlying raw column the aggregation reads from -- the exact shape confirmed live for a
    * create_worksheet_table-style baked-in AggregateInfo (private=[Quarter(date_order),
    * date_order, order_count] for a table grouping date_order by quarter and counting rows).
    */
   private static PhysicalBoundTableAssembly physicalTableWithGroupedAggregate(
      Worksheet ws, String assemblyName, String rawGroupField, String groupOutputName,
      String aggregateOutputName, AggregateFormula formula)
   {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.product_template"));

      ColumnSelection priv = new ColumnSelection();
      priv.addAttribute(rawColumn(groupOutputName));
      priv.addAttribute(rawColumn(rawGroupField));
      priv.addAttribute(rawColumn(aggregateOutputName));
      table.setColumnSelection(priv, false);

      ColumnSelection pub = new ColumnSelection();
      pub.addAttribute(rawColumn(groupOutputName));
      pub.addAttribute(rawColumn(aggregateOutputName));
      table.setColumnSelection(pub, true);

      AggregateInfo aggr = new AggregateInfo();
      aggr.addGroup(groupRef(groupOutputName));
      aggr.addAggregate(aggregateRef(aggregateOutputName, formula));
      table.setAggregateInfo(aggr);
      return table;
   }

   /**
    * Regression for the actual root cause of a reproduced live crash (the sibling test above
    * fixed a related-but-different hazard in the SAME merge). {@link WsMergeService#mergeColumns}
    * used to merge a source table's PUBLIC selection into the shared physical base unconditionally
    * -- but when the source table carries its OWN baked-in {@link AggregateInfo} (the shape
    * create_worksheet_table produces), that public selection reflects AGGREGATE OUTPUT names
    * (e.g. a date-grouped "Quarter(date_order)", an aggregate alias "order_count"), not genuine
    * physical columns. Merging those names onto the shared base made it falsely claim to already
    * HAVE a column with that name -- so a DIFFERENT, unrelated chart also merged onto the same
    * physical table (e.g. one computing its OWN "Quarter(date_order)" via chart-level date
    * grouping, with no aggregateInfo of its own at all) collided with that bogus pre-existing
    * "raw" column. StyleBI's SQL builder then silently disambiguated the alias with a "_1" suffix
    * neither chart's own VSChartInfo binding was told about, crashing the OTHER (unrelated)
    * chart's graph render with ColumnNotFoundException the next time its dashboard tile opened --
    * confirmed live against a real StyleBI deployment (error: "Column not found: Quarter(date_order)
    * in amount_total,Quarter(date_order)_1"). Fix: merge only the genuine underlying raw column(s)
    * an aggregated source table's own grouping/aggregates read from -- never its own output names.
    */
   @Test
   void mergingAChartsOwnAggregateOutputColumnsDoesNotPolluteTheSharedRawBase() {
      Worksheet dashWS = new Worksheet();
      // First chart (e.g. a boxplot): plain, unaggregated view of the raw table -- its OWN
      // date-grouping (if any) happens at the chart-binding level, not baked into the worksheet.
      dashWS.addAssembly(physicalTable(dashWS, "PT", "date_order", "amount_total", "id"));

      // Second chart (e.g. order-count): its OWN baked-in aggregation grouping "date_order" by
      // quarter (output "Quarter(date_order)") and counting rows (output "order_count").
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithGroupedAggregate(
         vizWS, "PT", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      TableAssembly base = (TableAssembly) dashWS.getAssembly("PT_base");
      assertNotNull(base, "expected the existing 'PT' to be promoted to 'PT_base'");

      ColumnSelection publicCols = base.getColumnSelection(true);
      assertNull(publicCols.getAttribute("Quarter(date_order)"),
         "the second chart's own aggregate-OUTPUT column name must NOT be merged onto the shared " +
         "raw base -- it isn't a genuine physical column, and a false pre-existing column with " +
         "that name collides with ANY other chart that independently produces the same output name");
      assertNull(publicCols.getAttribute("order_count"),
         "same for the aggregate's own alias -- not a genuine raw column either");

      assertNotNull(publicCols.getAttribute("date_order"),
         "the genuine underlying raw column the aggregation reads from must still be merged, so " +
         "the shared base can supply it to whatever mirror stacks on it");
   }
}

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
import inetsoft.uql.viewsheet.Viewsheet;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

   /**
    * Regression for a THIRD, additional hardening in the same merge path (found after the two
    * above were already fixed and re-verified live): the condMirror stacked for a chart's own
    * conditions/aggregation only had its PUBLIC column selection set
    * ({@code condMirror.setColumnSelection(..., true)}), never its PRIVATE one. Some validation
    * paths regenerate a table's public selection FROM its private one, which -- left at its
    * default-empty state -- would silently drop every aggregate output. Not confirmed as the
    * actual live crash mechanism (see the isAggregate() tests below for that), but a real,
    * independently-justified hardening matching the SAME "public without private" pattern this
    * file's very first test already covers for mergeColumns.
    */
   @Test
   void condMirrorGetsBothPublicAndPrivateColumnSelection() {
      Worksheet dashWS = new Worksheet();
      dashWS.addAssembly(physicalTable(dashWS, "PT", "date_order", "amount_total", "id"));

      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithGroupedAggregate(
         vizWS, "PT", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL));

      Map<String, String> wsRenameMap = service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      String condMirrorName = wsRenameMap.get("PT");
      assertNotNull(condMirrorName, "expected the second chart's table to be mapped to a merged name");
      TableAssembly condMirror = (TableAssembly) dashWS.getAssembly(condMirrorName);
      assertNotNull(condMirror);

      ColumnSelection privateCols = condMirror.getColumnSelection(false);
      assertNotNull(privateCols.getAttribute("order_count"),
         "condMirror's PRIVATE selection must carry the aggregate's own output name -- otherwise " +
         "a later resetColumnSelection() (regenerating public FROM private) silently drops it, " +
         "even though the public selection alone looked correct right after the merge");
      assertNotNull(privateCols.getAttribute("Quarter(date_order)"),
         "same for the group's own output name");
      assertNotNull(privateCols.getAttribute("date_order"),
         "and the genuine underlying raw column the aggregation reads from");

      ColumnSelection publicCols = condMirror.getColumnSelection(true);
      assertNotNull(publicCols.getAttribute("order_count"));
      assertNotNull(publicCols.getAttribute("Quarter(date_order)"));
   }

   /**
    * Regression for a genuine (if incomplete on its own) correctness gap found while chasing the
    * "Aggregate not found: avg_order_value" live crash: {@link AbstractTableAssembly#isAggregate()}
    * short-circuits to {@code false} when {@code getAggregateInfo().isEmpty()}, but when the info
    * is NOT empty, it ALSO requires a SEPARATE flag ({@code TableAssemblyInfo.isAggregate}, default
    * false) to be true -- {@code setAggregateInfo} alone never sets it. Keeping this flag
    * consistent with the info's own emptiness is correct regardless of the mergeMirrorColumns
    * side-effect below (see that test for the mechanism that fully explained the live crash).
    */
   @Test
   void prevMirrorAndCondMirrorIsAggregateFlagStaysConsistentWithTheirAggregateInfo() {
      Worksheet dashWS = new Worksheet();
      // First chart onto this physical table carries ITS OWN non-empty AggregateInfo (the
      // shape that becomes prevMirror once a second chart merges against it).
      dashWS.addAssembly(physicalTableWithGroupedAggregate(
         dashWS, "PT", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL));

      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithGroupedAggregate(
         vizWS, "PT", "date_order", "Quarter(date_order)", "avg_order_value", AggregateFormula.AVG));

      Map<String, String> wsRenameMap = service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      TableAssembly prevMirror = (TableAssembly) dashWS.getAssembly("PT");
      assertNotNull(prevMirror, "expected a prevMirror named 'PT' to be created");
      assertFalse(prevMirror.getAggregateInfo().isEmpty(), "prevMirror should inherit the first chart's own aggregation");
      assertTrue(prevMirror.isAggregate(),
         "prevMirror.isAggregate() must be true when its AggregateInfo is non-empty -- setAggregateInfo " +
         "alone does not set this separate flag, and leaving it false lets the worksheet persist/reload " +
         "path silently drop the AggregateInfo (confirmed live)");

      String condMirrorName = wsRenameMap.get("PT");
      assertNotNull(condMirrorName);
      TableAssembly condMirror = (TableAssembly) dashWS.getAssembly(condMirrorName);
      assertNotNull(condMirror);
      assertFalse(condMirror.getAggregateInfo().isEmpty());
      assertTrue(condMirror.isAggregate(), "same requirement for condMirror's own aggregation");
   }

   /**
    * Regression for the actual, final root cause of the "Aggregate not found: avg_order_value"
    * live crash (all four fixes/hardenings above were real but insufficient on their own).
    * {@link WsMergeService#ensureBaseHasPrevMirror} used to give prevMirror's own AggregateInfo
    * refs the SAME bare DataRef names as the original (pre-merge) chart's table -- e.g. a bare
    * "order_count" -- even though {@code mergeMirrorColumns} may separately need to add a
    * differently-qualified ("PT_base.order_count") column to prevMirror's own selection for a
    * LATER chart sharing the table. That left prevMirror internally inconsistent: its
    * AggregateInfo pointed at a bare name while its own selection only carried the qualified one.
    * This mismatch was invisible in-memory (compose-time code never re-validates existing refs),
    * but StyleBI's OWN generic query engine (AssetQuery#createAssetQuery, invoked for EVERY table
    * on EVERY viewsheet open via AssetQuerySandbox#refreshColumnSelection -- completely
    * independent of this class) independently re-resolves a mirror's aggregate columns down to
    * their base table using this SAME outer-attribute qualification, then calls
    * table.setColumnSelection(..., false), which triggers AggregateInfo#validate() against the
    * newly-qualified selection. Confirmed live: prevMirror's own AggregateInfo read back correctly
    * (2 aggregates) immediately after compose, but a FRESH viewsheet open of the SAVED dashboard
    * (reproducing the user-visible crash) read back only the group -- both aggregates silently
    * dropped, because their bare-named refs no longer matched the query engine's own re-qualified
    * selection, while the group survived because groups are never routed through that same
    * base-resolution step. Fix: qualify prevMirror's own AggregateInfo aggregate refs (not
    * groups) with the SAME outer-attribute naming up front, and keep prevMirror's own column
    * selection in sync, so the query engine's independent re-derivation always finds a match.
    */
   @Test
   void prevMirrorAggregateRefsAreOuterAttributeQualifiedToMatchTheQueryEnginesOwnResolution() {
      Worksheet dashWS = new Worksheet();
      // First chart onto this physical table carries its own group + aggregate -- becomes
      // prevMirror once a second chart merges against it.
      dashWS.addAssembly(physicalTableWithGroupedAggregate(
         dashWS, "PT", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL));

      // Second chart sharing the same physical source triggers ensureBaseHasPrevMirror's
      // promotion of "PT" to prevMirror + "PT_base".
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithGroupedAggregate(
         vizWS, "PT", "date_order", "Quarter(date_order)", "avg_order_value", AggregateFormula.AVG));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      TableAssembly prevMirror = (TableAssembly) dashWS.getAssembly("PT");
      assertNotNull(prevMirror, "expected a prevMirror named 'PT' to be created");

      AggregateInfo prevAggr = prevMirror.getAggregateInfo();
      assertEquals(1, prevAggr.getAggregateCount());
      String aggregateRefName = prevAggr.getAggregate(0).getName();
      assertEquals("PT_base.order_count", aggregateRefName,
         "prevMirror's own AggregateRef must be qualified with the base table's name -- matching " +
         "the SAME outer-attribute shape AssetQuery#createAssetQuery independently re-derives for " +
         "this mirror's aggregate columns on every viewsheet open, so that re-derivation finds a " +
         "match instead of silently dropping the aggregate as unresolvable");

      // The group must stay BARE -- groups are read directly off the mirror (no aggregation
      // needed), so the query engine never re-qualifies them; qualifying it too would just
      // break the group instead of fixing anything.
      assertEquals(1, prevAggr.getGroupCount());
      assertEquals("Quarter(date_order)", prevAggr.getGroup(0).getName(),
         "prevMirror's own GroupRef must stay bare -- the query engine never re-qualifies groups, " +
         "so qualifying this one would only break it");

      // prevMirror's own column selection must carry a matching entry under the SAME qualified
      // name the AggregateRef now points at -- otherwise the ref and the selection disagree.
      ColumnSelection prevMirrorPrivate = prevMirror.getColumnSelection(false);
      assertNotNull(prevMirrorPrivate.getAttribute(aggregateRefName),
         "prevMirror's own private column selection must contain an entry matching its own " +
         "AggregateRef's (now-qualified) name");
   }

   /**
    * Regression for the third-chart-onward path: once a prevMirror already exists for a
    * physical table (tagged {@link WsMergeService#PROP_WIZ_MERGED}), {@link
    * WsMergeService#ensureBaseHasPrevMirror} short-circuits via its {@code wizMirror} lookup and
    * returns early WITHOUT re-promoting or re-qualifying anything. This is the branch the
    * name-return refactor (returning the mirror's own name instead of the caller re-deriving it
    * from a "_base" suffix) changed the most, and it must keep working for a third (and any
    * later) chart merging onto the same already-promoted table -- reusing the exact same
    * prevMirror, with the first chart's own qualified aggregation from the earlier test still
    * intact.
    */
   @Test
   void aThirdChartSharingTheSamePhysicalTableReusesTheExistingPrevMirrorUnchanged() {
      Worksheet dashWS = new Worksheet();
      dashWS.addAssembly(physicalTableWithGroupedAggregate(
         dashWS, "PT", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL));

      Worksheet secondVizWS = new Worksheet();
      secondVizWS.addAssembly(physicalTableWithGroupedAggregate(
         secondVizWS, "PT", "date_order", "Quarter(date_order)", "avg_order_value", AggregateFormula.AVG));
      service.mergeWorksheet(secondVizWS, dashWS, "suffix1", new HashMap<>());

      TableAssembly prevMirrorAfterSecond = (TableAssembly) dashWS.getAssembly("PT");
      String aggregateRefNameAfterSecond = prevMirrorAfterSecond.getAggregateInfo().getAggregate(0).getName();

      // Third chart, again sharing the same physical source, with its own third aggregate.
      Worksheet thirdVizWS = new Worksheet();
      thirdVizWS.addAssembly(physicalTableWithGroupedAggregate(
         thirdVizWS, "PT", "date_order", "Quarter(date_order)", "max_order_value", AggregateFormula.MAX));
      Map<String, String> thirdRenameMap = service.mergeWorksheet(thirdVizWS, dashWS, "suffix2", new HashMap<>());

      TableAssembly prevMirrorAfterThird = (TableAssembly) dashWS.getAssembly("PT");
      assertSame(prevMirrorAfterSecond, prevMirrorAfterThird,
         "the third chart must reuse the EXACT SAME prevMirror instance -- ensureBaseHasPrevMirror's " +
         "wizMirror early-return must not create a second, competing prevMirror");

      assertEquals(aggregateRefNameAfterSecond, prevMirrorAfterThird.getAggregateInfo().getAggregate(0).getName(),
         "the first chart's own qualified AggregateRef must be completely untouched by a third " +
         "chart's own merge -- the early-return path must not re-run any qualification/rebind logic");

      // The third chart's own condMirror must still exist and carry its own aggregation,
      // proving the shared prevMirror is still usable as a stacking point for new arrivals.
      String thirdCondMirrorName = thirdRenameMap.get("PT");
      assertNotNull(thirdCondMirrorName);
      TableAssembly thirdCondMirror = (TableAssembly) dashWS.getAssembly(thirdCondMirrorName);
      assertNotNull(thirdCondMirror);
      assertFalse(thirdCondMirror.getAggregateInfo().isEmpty());
   }

   /**
    * Regression for the {@code outerMirror != null} branch of {@link
    * WsMergeService#ensureBaseHasPrevMirror}: when the bare table name a chart's own worksheet
    * binds to is ALREADY a {@link MirrorTableAssembly} created by some OTHER, unrelated mechanism
    * (not tagged {@link WsMergeService#PROP_WIZ_MERGED} -- e.g. StyleBI's own generic
    * Viewsheet#createMirrorTable, which wraps ANY table a VS chart binds to), {@code
    * findMergeableTable} (which only matches {@code BoundTableAssembly}) resolves to the
    * WRAPPED table underneath, not the mirror itself. Promoting that wrapped table's own name
    * would create a prevMirror no VS binding can ever reach. This adapts the pre-existing outer
    * mirror in place instead.
    */
   @Test
   void adaptsAPreExistingNonWizMirrorInPlaceInsteadOfCreatingAnUnreachableOne() {
      Worksheet dashWS = new Worksheet();
      PhysicalBoundTableAssembly wrapped = physicalTableWithGroupedAggregate(
         dashWS, "PT_actual", "date_order", "Quarter(date_order)", "order_count", AggregateFormula.COUNT_ALL);
      dashWS.addAssembly(wrapped);

      // Pre-existing mirror at the bare name "PT" wrapping "PT_actual" -- simulating a mirror
      // created by some OTHER mechanism entirely (Viewsheet#createMirrorTable), tagged
      // VS_MIRROR_TABLE (as that mechanism always does) but NOT PROP_WIZ_MERGED, and (as that
      // mechanism always produces) with an EMPTY AggregateInfo of its own.
      MirrorTableAssembly preExistingOuterMirror = new MirrorTableAssembly(dashWS, "PT", wrapped);
      preExistingOuterMirror.setProperty(Viewsheet.VS_MIRROR_TABLE, "true");
      dashWS.addAssembly(preExistingOuterMirror);
      assertTrue(preExistingOuterMirror.getAggregateInfo().isEmpty());

      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTableWithGroupedAggregate(
         vizWS, "PT", "date_order", "Quarter(date_order)", "avg_order_value", AggregateFormula.AVG));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      // The pre-existing mirror must be ADAPTED IN PLACE -- same instance, now carrying the
      // first ("PT_actual") chart's own qualified aggregation -- not replaced or orphaned.
      assertSame(preExistingOuterMirror, dashWS.getAssembly("PT"),
         "the pre-existing outer mirror must be adapted in place, not replaced");
      assertFalse(preExistingOuterMirror.getAggregateInfo().isEmpty(),
         "the adapted mirror must now carry the first chart's own aggregation");
      assertEquals("PT_actual.order_count", preExistingOuterMirror.getAggregateInfo().getAggregate(0).getName());
      assertEquals("true", preExistingOuterMirror.getProperty(WsMergeService.PROP_WIZ_MERGED),
         "the adapted mirror must be tagged so a later chart's wizMirror lookup recognizes it as " +
         "already promoted");

      // The wrapped table must still be reachable and stripped to raw/full data, exactly like
      // the "no pre-existing outer mirror" branch's "_base" table.
      TableAssembly wrappedAfter = (TableAssembly) dashWS.getAssembly("PT_actual");
      assertNotNull(wrappedAfter);
      assertTrue(wrappedAfter.getAggregateInfo().isEmpty());
   }

   /**
    * Regression for a reproduced live crash, a false-positive sibling of the "adapts a
    * pre-existing non-wiz mirror" test above: {@code ensureBaseHasPrevMirror}'s outerMirror
    * lookup used to match ANY MirrorTableAssembly whose base pointer equals the physical
    * table's name -- not just one actually created by {@code Viewsheet#createMirrorTable} (which
    * tags its mirror {@code VS_MIRROR_TABLE}). An entirely unrelated chart can have its OWN real,
    * business-logic aggregate mirror built directly on the same raw physical table (e.g. a
    * "SO_QREV" quarterly-revenue rollup mirror of "SO") for reasons that have nothing to do with
    * wiz's dashboard-merge machinery -- that mirror also satisfies a bare base-pointer-name
    * match. Without requiring the VS_MIRROR_TABLE tag, that unrelated chart's mirror gets
    * misidentified as the empty createMirrorTable placeholder, and ensureBaseHasPrevMirror
    * overwrites its real AggregateInfo with the (empty) one belonging to whichever OTHER chart's
    * plain physical table happened to match by SourceInfo -- silently wiping out its group/
    * aggregate columns. Confirmed live: a chart's own quarterly-grouped chain (Quarter(date)
    * group + SUM/MEDIAN aggregates) lost its aggregation entirely, collapsing to a bare
    * passthrough of the raw table, the moment a second, unrelated chart sharing the same
    * physical source was merged into the same dashboard afterward.
    */
   @Test
   void anUnrelatedChartsOwnAggregateMirrorSharingTheSameBaseIsNotMistakenForAnOuterMirror() {
      Worksheet dashWS = new Worksheet();
      PhysicalBoundTableAssembly rawPhysical = physicalTable(dashWS, "PT_actual", "date_order", "amount_total");
      dashWS.addAssembly(rawPhysical);

      // An unrelated chart's OWN business-logic aggregate mirror of "PT_actual" -- NOT tagged
      // VS_MIRROR_TABLE (nothing wraps it via Viewsheet#createMirrorTable), carrying its own
      // real, non-empty AggregateInfo. This is structurally identical to the "SO_QREV" mirror
      // that lost its aggregation in the live crash.
      MirrorTableAssembly unrelatedAggregateMirror = new MirrorTableAssembly(dashWS, "PT_QREV", rawPhysical);
      AggregateInfo ownAggr = new AggregateInfo();
      ownAggr.addGroup(groupRef("quarter"));
      ownAggr.addAggregate(aggregateRef("revenue", AggregateFormula.SUM));
      unrelatedAggregateMirror.setAggregateInfo(ownAggr);
      dashWS.addAssembly(unrelatedAggregateMirror);

      // A second chart binding to the SAME physical source, with no aggregation of its own --
      // e.g. a plain "SO" bind that some OTHER chart uses directly.
      Worksheet vizWS = new Worksheet();
      vizWS.addAssembly(physicalTable(vizWS, "PT_actual", "date_order", "amount_total"));

      service.mergeWorksheet(vizWS, dashWS, "suffix1", new HashMap<>());

      // The unrelated mirror's own aggregation must survive untouched -- not overwritten with
      // an empty AggregateInfo borrowed from the second chart's plain physical table.
      TableAssembly stillThere = (TableAssembly) dashWS.getAssembly("PT_QREV");
      assertNotNull(stillThere, "the unrelated chart's own aggregate mirror must still exist");
      assertFalse(stillThere.getAggregateInfo().isEmpty(),
         "the unrelated mirror's own aggregation must not be wiped out by an unrelated merge");
      assertEquals("revenue", stillThere.getAggregateInfo().getAggregate(0).getName());

      // Since the unrelated mirror must NOT be mistaken for an outer mirror, the merge takes the
      // "no pre-existing outer mirror" branch: the raw physical table is renamed to "_base" and a
      // FRESH, empty prevMirror is created at the bare name.
      TableAssembly base = (TableAssembly) dashWS.getAssembly("PT_actual_base");
      assertNotNull(base, "the raw physical table must be renamed to \"_base\"");
      assertTrue(base.getAggregateInfo().isEmpty());
   }
}

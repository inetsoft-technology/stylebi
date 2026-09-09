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
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redmine #76400 (PWA-001) regression: a worksheet table that has had {@code set_group_aggregate}
 * applied (a group + an aliased aggregate, exactly as {@code WorksheetMutationSupport
 * .applyAggregateInfo} performs it -- alias set on the SAME live {@link ColumnRef} already in the
 * table's private {@link ColumnSelection}, no copy) silently serves wrong/degenerate data to a
 * chart bound to it.
 *
 * <p><b>This test's empirical result CONTRADICTS the mechanism predicted by
 * {@code docs/teams/2026-09-07-bug-76400-group-aggregate-chart-poison/02-root-cause.md}.</b> That
 * document predicted a "double-add" (the table's declared public {@link ColumnSelection} ending up
 * with TWO aggregate-shaped output columns, one real and one a silently-dropped chart-derived
 * pseudo-column). What this test actually observes is different: the chart's own aggregate is lost
 * COMPLETELY (zero aggregates survive in {@link AggregateInfo}, and the declared public column
 * selection shows only the group column, not two aggregate-shaped columns) -- see
 * {@code 03-repro.md} for the full empirically-traced mechanism (a column-resolution mismatch
 * between the chart's design-time-resolved {@link ColumnRef} -- which embeds the RAW,
 * pre-alias attribute name -- and the auto {@code "V_"}-prefixed per-viewsheet mirror
 * ({@link inetsoft.uql.asset.Worksheet#getVSTableAssembly}) that every VS chart binds through,
 * whose own column selection exposes only the ALIASED name).</p>
 *
 * <p>Built from {@code CrosstabNamedGroupEndToEndTest} as a direct template (same package, same
 * Spring context set, same from-scratch {@link Worksheet}/{@link ViewsheetSandbox}/{@link
 * AssetQuerySandbox} wiring), swapping {@link CrosstabVSAQuery} for {@link ChartVSAQuery} per
 * {@code 01-hypothesis-aoa-unittest.md} Part B -- though the actual observed mechanism below
 * departs from that file's predicted assertions once the test was run against real code.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartGroupAggregatePoisonEndToEndTest {
   /** All row labels (column 0) actually present in the rendered chart table, skipping the header. */
   private static Set<String> rowLabels(TableLens lens) {
      Set<String> labels = new HashSet<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         labels.add(String.valueOf(lens.getObject(r, 0)));
      }

      return labels;
   }

   /**
    * Sub-repro 2 (charter, "Directly, no mirror"): {@code set_group_aggregate} applied straight
    * to the source table, chart bound to the RAW column {@code SALES_AMOUNT} with an explicit
    * SUM formula (the alias {@code TOTAL_SALES} itself cannot be bound in the live product --
    * {@code set_chart_shelf} refuses it, per the charter's own sub-repro 2 description).
    *
    * <p>NOTE: binding to {@code TOTAL_SALES} directly instead was also tried during this test's
    * construction and produces the IDENTICAL empirical result below (aggregate resolution fails
    * either way) -- the failure is not about which name the chart binds, it is about the auto
    * "V_" mirror's column selection only ever exposing the ALIASED name while the chart's
    * design-time-resolved column carries the RAW pre-alias attribute underneath.</p>
    */
   @Test
   void noMirrorDirectAggregateOnSourceTable() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "SalesFull");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef regionCol = new ColumnRef(new AttributeRef("REGION"));
      ColumnRef salesCol = new ColumnRef(new AttributeRef("SALES_AMOUNT"));
      cs.addAttribute(regionCol);
      cs.addAttribute(salesCol);
      table.setColumnSelection(cs, false);

      // Round numbers so a post-fix assertion can be exact, standing in for the real repro's
      // approximate ~10.3M (USA East) / ~7.4M (USA West).
      Object[][] rows = new Object[][]{
         {"REGION", "SALES_AMOUNT"},
         {"USA East", 5_000_000.0}, {"USA East", 5_000_000.0},
         {"USA West", 3_500_000.0}, {"USA West", 3_500_000.0},
      };
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double" }, rows));
      ws.addAssembly(table);

      // Mirrors WorksheetMutationSupport.applyAggregateInfo's own sequencing exactly: build the
      // GroupRef/AggregateRef against the table's live private ColumnSelection first, THEN set
      // the alias on the SAME live ColumnRef object (not a fresh one) -- per 00-map.md's
      // object-identity chain.
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(regionCol));
      AggregateRef ar = new AggregateRef(salesCol, AggregateFormula.SUM);
      salesCol.setAlias("TOTAL_SALES");
      ainfo.addAggregate(ar, false);
      table.setAggregateInfo(ainfo);
      table.setAggregate(true);

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "SalesFull"));
      vs.addAssembly(chart);

      VSChartInfo cinfo = new DefaultVSChartInfo();

      VSChartDimensionRef regionDim = new VSChartDimensionRef();
      regionDim.setDataRef(new AttributeRef("REGION"));
      regionDim.setGroupColumnValue("REGION");
      regionDim.setDataType(XSchema.STRING);
      cinfo.addXField(regionDim);

      VSChartAggregateRef salesAgg = new VSChartAggregateRef();
      salesAgg.setDataRef(new AttributeRef("SALES_AMOUNT"));
      salesAgg.setColumnValue("SALES_AMOUNT");
      salesAgg.setFormulaValue(AggregateFormula.SUM.getFormulaName());
      cinfo.addYField(salesAgg);

      chart.setVSChartInfo(cinfo);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, false, null, "SalesFull", null);

      ChartVSAQuery query = new ChartVSAQuery(box, "Chart1", false);

      // Isolate the state right after createTableAssembly's clone + refs-loop run (private
      // method, reflection needed) -- BEFORE getTableAssembly's own further steps
      // (applyDiscreteAggregates/mergePostCondition) run, to pin down exactly where the loss
      // happens.
      Method createTableAssemblyMethod = ChartVSAQuery.class.getDeclaredMethod("createTableAssembly", boolean.class);
      createTableAssemblyMethod.setAccessible(true);
      TableAssembly createdTable = (TableAssembly) createTableAssemblyMethod.invoke(query, false);
      AggregateInfo createdAinfo = createdTable.getAggregateInfo();
      System.out.println("[bug-76400 no-mirror] right after createTableAssembly returns: groups=" +
         createdAinfo.getGroupCount() + " aggregates=" + createdAinfo.getAggregateCount() +
         " declared public column count=" + createdTable.getColumnSelection(true).getAttributeCount());

      // EXPECTED (CORRECT) BEHAVIOR asserted below -- this is what should hold once the bug is
      // fixed, so this test is RED against current code, per the "failing case first" convention.
      //
      // ACTUAL (BUGGY) BEHAVIOR OBSERVED, empirically, by this test against unfixed code --
      // CONTRADICTING 02-root-cause.md's predicted mechanism (see this file's class Javadoc):
      // the chart's own SUM(SALES_AMOUNT) aggregate does not get DOUBLED alongside the
      // worksheet's own pre-existing TOTAL_SALES aggregate as 02-root-cause.md predicted -- it is
      // LOST ENTIRELY (createdAinfo.getAggregateCount() == 0, not 2; declared public column count
      // == 1, not 2). Root cause (traced empirically, not in the prior static analysis): every VS
      // chart binds through an auto-created per-viewsheet mirror
      // (Worksheet.getVSTableAssembly, a "V_"-prefixed MirrorTableAssembly distinct from any
      // explicit add_mirror) whose own column selection is built from the worksheet table's
      // PUBLIC selection -- which, once resetColumnSelection has run, exposes only the ALIASED
      // name (TOTAL_SALES). The chart's aggregate ref, however, gets design-time-resolved (via
      // cinfo.update against the worksheet table's own column selection) into an actual ColumnRef
      // object wrapping the RAW attribute (SALES_AMOUNT) underneath its alias. At query time,
      // AssetUtil.getColumnRefFromAttribute unwraps a ColumnRef-typed input ref to its raw
      // DataRef before matching (AssetUtil.java:555-557), discarding the alias and searching the
      // mirror's columns for a column literally attributed 'SALES_AMOUNT' -- which does not exist
      // there (only 'TOTAL_SALES' does), so resolution fails and the aggregate is dropped with no
      // error.
      assertEquals(1, createdAinfo.getGroupCount(),
         "REGION group should resolve correctly (uncontested per 02-root-cause.md chain step 3)");
      assertEquals(1, createdAinfo.getAggregateCount(),
         "the chart's own SUM(SALES_AMOUNT)/TOTAL_SALES aggregate should resolve and survive -- " +
         "ACTUAL (buggy, pre-fix): 0, the aggregate is lost entirely (see this test's class " +
         "Javadoc for the empirically-traced mechanism)");
      assertEquals(2, createdTable.getColumnSelection(true).getAttributeCount(),
         "declared public ColumnSelection should show 2 columns (REGION + the aggregate) -- " +
         "ACTUAL (buggy, pre-fix): 1, only REGION survives");

      // Negative control tied to the AOA-inert hypothesis (Part A of 01-hypothesis-aoa-unittest.md):
      // no *_mirror assembly should exist in ws -- this should hold both before and after any fix
      // targeting the refs-loop; its failure would refute Part A specifically. Still holds under
      // this test's actual (different) mechanism, since the AOA gate's own boolean algebra
      // (01-hypothesis-aoa-unittest.md Part A) never depended on aggregate count.
      assertNull(ws.getAssembly(Assembly.TABLE_VS + "Chart1_mirror"),
         "no AOA mirror table should be created for this repro shape (no ranking/percent/discrete/calc)");

      TableLens lens = query.getTableLens();
      lens.moreRows(TableLens.EOT);

      // Record the actual observed shape (the P3 brief's "genuine remaining gap" / final
      // observable) BEFORE asserting against it, per the "record what you actually observe" norm.
      //
      // Per team-lead follow-up (round-3 cross-check by investigator-group-dimension): before
      // trusting a "VSDataSet.indexOfHeader(chartRef.getFullName()) == -1" claim, print/confirm
      // the REAL materialized header string for each column, rather than assuming it's the bare
      // alias. Printed unconditionally below, for the record -- though see this test's actual
      // result: there is no aggregate column at all here (AggregateInfo.getAggregateCount()==0,
      // confirmed above), so there is no "aggregate column's header string" to check in this
      // sub-repro's run. The only column present is the group column.
      Set<String> labels = rowLabels(lens);
      System.out.println("[bug-76400 no-mirror] lens.getColCount()=" + lens.getColCount() +
         " lens.getRowCount()=" + lens.getRowCount() + " row labels=" + labels);

      for(int c = 0; c < lens.getColCount(); c++) {
         System.out.println("[bug-76400 no-mirror] header[" + c + "] = " + lens.getObject(0, c) +
            " (class=" + (lens.getObject(0, c) == null ? "null" : lens.getObject(0, c).getClass().getSimpleName()) + ")");
      }

      for(String label : labels) {
         StringBuilder rowDump = new StringBuilder("[bug-76400 no-mirror] row '" + label + "': ");

         for(int c = 0; c < lens.getColCount(); c++) {
            rowDump.append(lens.getObject(findRow(lens, label), c)).append(c < lens.getColCount() - 1 ? ", " : "");
         }

         System.out.println(rowDump);
      }

      // EXPECTED (CORRECT) BEHAVIOR: both regions render, with REGION + 1 real aggregate column,
      // and correct per-region totals.
      // ACTUAL (BUGGY) BEHAVIOR OBSERVED: lens.getColCount()==1 (REGION only, the aggregate
      // column never materializes since it was dropped from AggregateInfo entirely -- see
      // assertion above), lens.getRowCount()==3 (header + 2 data rows -- the GROUP side is
      // unaffected, only the aggregate/VALUE side is broken), row labels={"USA West","USA East"}
      // (both regions DO still appear, contradicting the originally-reported "1 row collapse" --
      // see 03-repro.md's "what this cannot falsify" section).
      assertEquals(2, lens.getColCount(), "expect REGION + 1 real aggregate column");
      assertEquals(Set.of("USA East", "USA West"), labels, "both regions should render");

      // Per-region totals, once the aggregate column exists: USA East = 5,000,000 + 5,000,000 =
      // 10,000,000; USA West = 3,500,000 + 3,500,000 = 7,000,000.
      assertEquals(10_000_000.0, ((Number) lens.getObject(findRow(lens, "USA East"), 1)).doubleValue(), 0.01);
      assertEquals(7_000_000.0, ((Number) lens.getObject(findRow(lens, "USA West"), 1)).doubleValue(), 0.01);

      // Per 02-root-cause.md row 7's spirit, re-targeted at the ACTUAL mechanism: construct a
      // VSDataSet exactly as ChartVSAQuery.getData() does (`new VSDataSet(data, refs)` at
      // ChartVSAQuery.java:187) and confirm the chart's own bound measure -- looked up by its
      // full formula-qualified name -- IS found once the fix lands.
      ChartVSAssembly chartClone = (ChartVSAssembly) query.getAssembly();
      VSDataRef[] refs = chartClone.getVSChartInfo().getRTFields();
      VSDataSet dataset = new VSDataSet(lens, refs);
      VSAggregateRef rtSalesAgg = null;

      for(VSDataRef ref : refs) {
         if(ref instanceof VSAggregateRef) {
            rtSalesAgg = (VSAggregateRef) ref;
            break;
         }
      }

      int headerIdx = dataset.indexOfHeader(rtSalesAgg.getFullName());
      System.out.println("[bug-76400 no-mirror] VSDataSet.indexOfHeader('" + rtSalesAgg.getFullName() +
         "') = " + headerIdx + " (-1 means not found -- ACTUAL/buggy result observed by this test)");
      org.junit.jupiter.api.Assertions.assertTrue(headerIdx >= 0,
         "VSDataSet should find the chart's own bound measure by its full name -- " +
         "ACTUAL (buggy, pre-fix): -1, not found (the measure the chart thinks it's plotting " +
         "resolves to no column at all)");
   }

   /**
    * Sub-repro 1 (charter, "Via a mirror"): {@code add_mirror(SalesByRegion, SalesFull)} then
    * {@code set_group_aggregate} applied to the MIRROR itself (not the raw source), chart bound to
    * the MIRROR with the chart binding the aggregate's ALIAS {@code TOTAL_SALES} directly (per the
    * charter, unlike {@link #noMirrorDirectAggregateOnSourceTable()} above, which binds the raw
    * column name since {@code set_chart_shelf} refuses the alias in that shape).
    *
    * <p>DIAGNOSTIC PASS: traces (1) whether the same auto {@code "V_"} per-viewsheet mirror
    * mechanism {@link #noMirrorDirectAggregateOnSourceTable()} found also fires ON TOP of this
    * EXPLICIT mirror (a "mirror of a mirror"), and (2) whether the aggregate resolves correctly, is
    * lost entirely (as the no-mirror sub-repro found), or is doubled (as {@code 02-root-cause.md}
    * originally predicted) -- printed and asserted from the actual observed values, not assumed.</p>
    */
   @Test
   void mirrorSubRepro() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly source = new EmbeddedTableAssembly(ws, "SalesFull");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef regionCol = new ColumnRef(new AttributeRef("REGION"));
      ColumnRef salesCol = new ColumnRef(new AttributeRef("SALES_AMOUNT"));
      cs.addAttribute(regionCol);
      cs.addAttribute(salesCol);
      source.setColumnSelection(cs, false);

      // Round numbers so a post-fix assertion can be exact, standing in for the real repro's
      // approximate ~10.3M (USA East) / ~7.4M (USA West).
      Object[][] rows = new Object[][]{
         {"REGION", "SALES_AMOUNT"},
         {"USA East", 5_000_000.0}, {"USA East", 5_000_000.0},
         {"USA West", 3_500_000.0}, {"USA West", 3_500_000.0},
      };
      source.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "double" }, rows));
      ws.addAssembly(source);

      // add_mirror(SalesByRegion, SalesFull) -- the same 5-arg constructor
      // ChartVSAQuery.createMirrorTableAssembly itself uses (03-repro.md's own API-surface
      // confirmation).
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "SalesByRegion", null, false, source);
      ws.addAssembly(mirror);

      // set_group_aggregate applied to the MIRROR itself: resolve against the MIRROR's OWN live
      // private ColumnSelection (built by MirrorTableAssembly.updateColumnSelection() from the
      // source's public columns, entity-qualified to "SalesFull" -- see
      // MirrorTableAssembly.java:97-152), alias set on that SAME live ColumnRef object --
      // mirroring WorksheetMutationSupport.applyAggregateInfo's sequencing exactly, same as
      // noMirrorDirectAggregateOnSourceTable above, but targeting the mirror's columns instead of
      // the raw source's.
      ColumnSelection mirrorCs = mirror.getColumnSelection(false);
      ColumnRef mirrorRegionCol = (ColumnRef) mirrorCs.getAttribute("REGION");
      ColumnRef mirrorSalesCol = (ColumnRef) mirrorCs.getAttribute("SALES_AMOUNT");
      assertNotNull(mirrorRegionCol, "mirror's own private ColumnSelection should resolve REGION");
      assertNotNull(mirrorSalesCol, "mirror's own private ColumnSelection should resolve SALES_AMOUNT");

      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(mirrorRegionCol));
      AggregateRef ar = new AggregateRef(mirrorSalesCol, AggregateFormula.SUM);
      mirrorSalesCol.setAlias("TOTAL_SALES");
      ainfo.addAggregate(ar, false);
      mirror.setAggregateInfo(ainfo);
      mirror.setAggregate(true);

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "SalesByRegion"));
      vs.addAssembly(chart);

      VSChartInfo cinfo = new DefaultVSChartInfo();

      VSChartDimensionRef regionDim = new VSChartDimensionRef();
      regionDim.setDataRef(new AttributeRef("REGION"));
      regionDim.setGroupColumnValue("REGION");
      regionDim.setDataType(XSchema.STRING);
      cinfo.addXField(regionDim);

      // Binds by ALIAS (TOTAL_SALES), not the raw column -- per the charter, this is what
      // distinguishes this sub-repro from noMirrorDirectAggregateOnSourceTable's raw-column bind
      // (set_chart_shelf refuses the alias in the no-mirror case; it is bindable here).
      VSChartAggregateRef salesAgg = new VSChartAggregateRef();
      salesAgg.setDataRef(new AttributeRef("TOTAL_SALES"));
      salesAgg.setColumnValue("TOTAL_SALES");
      salesAgg.setFormulaValue(AggregateFormula.SUM.getFormulaName());
      cinfo.addYField(salesAgg);

      chart.setVSChartInfo(cinfo);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, mirrorCs, false, null, "SalesByRegion", null);

      ChartVSAQuery query = new ChartVSAQuery(box, "Chart1", false);

      // Isolate the state right after createTableAssembly's clone + refs-loop run (private
      // method, reflection needed), exactly as noMirrorDirectAggregateOnSourceTable does.
      Method createTableAssemblyMethod = ChartVSAQuery.class.getDeclaredMethod("createTableAssembly", boolean.class);
      createTableAssemblyMethod.setAccessible(true);
      TableAssembly createdTable = (TableAssembly) createTableAssemblyMethod.invoke(query, false);
      AggregateInfo createdAinfo = createdTable.getAggregateInfo();

      System.out.println("[bug-76400 mirror] right after createTableAssembly returns: groups=" +
         createdAinfo.getGroupCount() + " aggregates=" + createdAinfo.getAggregateCount() +
         " declared public column count=" + createdTable.getColumnSelection(true).getAttributeCount());

      // TRACE 1: does the auto "V_" per-viewsheet mirror mechanism (Worksheet.getVSTableAssembly)
      // fire ON TOP of this EXPLICIT mirror too (a "mirror of a mirror")? bname here is
      // "SalesByRegion" itself (the chart's own source name) -- if the mechanism fires the same way
      // it did for the no-mirror sub-repro, ws should now contain a "V_SalesByRegion" assembly
      // wrapping the EXPLICIT mirror "SalesByRegion", not the raw source "SalesFull" directly.
      //
      // CONFIRMED, empirically: yes -- ws.getAssembly("V_SalesByRegion") exists and wraps
      // "SalesByRegion" (the explicit mirror), not "SalesFull" (the raw source). This holds
      // regardless of the fix targeting the aggregate-resolution bug below, so it is asserted here
      // as a settled structural fact, not a symptom of the bug.
      Assembly autoMirror = ws.getAssembly(Assembly.TABLE_VS + "SalesByRegion");
      System.out.println("[bug-76400 mirror] auto V_-mirror created: " + (autoMirror != null) +
         (autoMirror instanceof MirrorTableAssembly
            ? ", wrapping " + ((MirrorTableAssembly) autoMirror).getAssembly().getName()
            : ""));
      assertNotNull(autoMirror,
         "the SAME auto V_-mirror mechanism the no-mirror sub-repro found should fire here too, " +
         "wrapping the explicit mirror -- a genuine mirror-of-a-mirror");
      assertTrue(autoMirror instanceof MirrorTableAssembly);
      assertEquals("SalesByRegion", ((MirrorTableAssembly) autoMirror).getAssembly().getName(),
         "the auto V_-mirror should wrap the EXPLICIT mirror \"SalesByRegion\", not the raw source " +
         "\"SalesFull\" directly -- confirming a genuine mirror-of-a-mirror, not a single-hop mirror");

      // TRACE 2: does the aggregate resolve correctly (1), get lost entirely like the no-mirror
      // case (0), or get DOUBLED as 02-root-cause.md originally predicted (2)?
      //
      // CONFIRMED, empirically: LOST ENTIRELY (0), the SAME mechanism/outcome as
      // noMirrorDirectAggregateOnSourceTable -- NOT doubled. EXPECTED (CORRECT) behavior asserted
      // below (1) is what should hold once fixed, so this test is RED against current code, per the
      // "failing case first" convention -- same shape as the no-mirror test above.
      System.out.println("[bug-76400 mirror] ainfo.getGroupCount()=" + createdAinfo.getGroupCount() +
         " ainfo.getAggregateCount()=" + createdAinfo.getAggregateCount());
      assertEquals(1, createdAinfo.getGroupCount(),
         "REGION group should resolve correctly (uncontested per 02-root-cause.md chain step 3, " +
         "and confirmed to hold for both sub-repros by investigator-group-dimension)");
      assertEquals(1, createdAinfo.getAggregateCount(),
         "the chart's own SUM(TOTAL_SALES) aggregate, bound by ALIAS, should resolve and survive " +
         "exactly once -- ACTUAL (buggy, pre-fix): 0, lost entirely -- the SAME mechanism/outcome " +
         "as noMirrorDirectAggregateOnSourceTable, NOT doubled as 02-root-cause.md originally " +
         "predicted (see this file's class Javadoc and 03-repro.md's mirror-sub-repro section for " +
         "the full trace)");
      assertEquals(2, createdTable.getColumnSelection(true).getAttributeCount(),
         "declared public ColumnSelection should show 2 columns (REGION + the aggregate) -- " +
         "ACTUAL (buggy, pre-fix): 1, only REGION survives");

      TableLens lens = query.getTableLens();
      lens.moreRows(TableLens.EOT);
      Set<String> labels = rowLabels(lens);
      System.out.println("[bug-76400 mirror] lens.getColCount()=" + lens.getColCount() +
         " lens.getRowCount()=" + lens.getRowCount() + " row labels=" + labels);

      for(int c = 0; c < lens.getColCount(); c++) {
         System.out.println("[bug-76400 mirror] header[" + c + "] = " + lens.getObject(0, c) +
            " (class=" + (lens.getObject(0, c) == null ? "null" : lens.getObject(0, c).getClass().getSimpleName()) + ")");
      }

      for(String label : labels) {
         StringBuilder rowDump = new StringBuilder("[bug-76400 mirror] row '" + label + "': ");

         for(int c = 0; c < lens.getColCount(); c++) {
            rowDump.append(lens.getObject(findRow(lens, label), c)).append(c < lens.getColCount() - 1 ? ", " : "");
         }

         System.out.println(rowDump);
      }

      // TRACE 3: does this produce something closer to the ACTUAL reported live symptom (a single
      // wrong slice ~3.8M, not zero rows and not a clean 2-row render) than either the no-mirror
      // test's finding (aggregate entirely absent) or 02-root-cause.md's original prediction
      // (doubled, but resolves to 1 real column)?
      //
      // CONFIRMED, empirically: NO -- this sub-repro produces the IDENTICAL shape to the no-mirror
      // test (lens.getColCount()==1, REGION only; lens.getRowCount()==3, both regions present in
      // the labels). It does NOT match the live-reported symptom any more closely than the
      // no-mirror finding did: the live bug shows ONE row with a wrong VALUE (implying an aggregate
      // column exists but is miscomputed), whereas both this test and the no-mirror test show the
      // aggregate column ABSENT ENTIRELY while BOTH regions still appear in the group column. The
      // mirror wrapping changes nothing about which mechanism fires or what it produces.
      assertEquals(2, lens.getColCount(), "expect REGION + 1 real aggregate column");
      assertEquals(Set.of("USA East", "USA West"), labels, "both regions should render");

      // Per-region totals, once the aggregate column exists: USA East = 5,000,000 + 5,000,000 =
      // 10,000,000; USA West = 3,500,000 + 3,500,000 = 7,000,000.
      assertEquals(10_000_000.0, ((Number) lens.getObject(findRow(lens, "USA East"), 1)).doubleValue(), 0.01);
      assertEquals(7_000_000.0, ((Number) lens.getObject(findRow(lens, "USA West"), 1)).doubleValue(), 0.01);

      ChartVSAssembly chartClone = (ChartVSAssembly) query.getAssembly();
      VSDataRef[] refs = chartClone.getVSChartInfo().getRTFields();
      VSDataSet dataset = new VSDataSet(lens, refs);
      VSAggregateRef rtSalesAgg = null;

      for(VSDataRef ref : refs) {
         if(ref instanceof VSAggregateRef) {
            rtSalesAgg = (VSAggregateRef) ref;
            break;
         }
      }

      int headerIdx = dataset.indexOfHeader(rtSalesAgg.getFullName());
      System.out.println("[bug-76400 mirror] VSDataSet.indexOfHeader('" + rtSalesAgg.getFullName() +
         "') = " + headerIdx + " (-1 means not found -- ACTUAL/buggy result observed by this test)");
      assertTrue(headerIdx >= 0,
         "VSDataSet should find the chart's own bound measure by its full name -- " +
         "ACTUAL (buggy, pre-fix): -1, not found (the measure the chart thinks it's plotting " +
         "resolves to no column at all)");
   }

   private static int findRow(TableLens lens, String label) {
      for(int r = 1; r < lens.getRowCount(); r++) {
         if(label.equals(String.valueOf(lens.getObject(r, 0)))) {
            return r;
         }
      }

      throw new AssertionError("no row for '" + label + "'");
   }
}

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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.MirrorTableAssembly;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for {@link Viewsheet#createMirrorTable}, a GENERIC mechanism (used by any
 * viewsheet, not wiz-specific) that wraps a chart's bound table in an "outer" mirror under the
 * table's own bare name the first time a chart binds to it -- so VS-level condition/binding
 * isolation between assemblies doesn't mutate a directly-shared raw table. It used to only skip
 * re-wrapping when the existing mirror matched its OWN "{name}+OUTER_TABLE_SUFFIX" naming
 * convention, so a table ALREADY wrapped by a DIFFERENT mirror (e.g. one created by a completely
 * separate merge/compose step, such as a wiz dashboard's own prevMirror) got renamed out of the
 * way and re-wrapped in a brand-new, empty-AggregateInfo mirror every time this method ran again
 * (it reruns on every {@link #resetWS()}, i.e. every worksheet repopulate -- once per chart added
 * to a running viewsheet). Confirmed live: a wiz dashboard's first chart crashed rendering with
 * "Aggregate not found: &lt;name&gt;" only once enough additional charts sharing its physical
 * table had been merged in afterward, each triggering another repopulate/re-wrap cycle.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetCreateMirrorTableTest {

   private static PhysicalBoundTableAssembly aggregatedPhysicalTable(Worksheet ws, String name) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public.sale_order"));

      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(rawColumn("Quarter"));
      cs.addAttribute(rawColumn("order_count"));
      table.setColumnSelection(cs, false);

      AggregateInfo aggr = new AggregateInfo();
      aggr.addGroup(groupRef("Quarter"));
      aggr.addAggregate(aggregateRef("order_count"));
      table.setAggregateInfo(aggr);
      table.setAggregate(true);
      return table;
   }

   private static ColumnRef rawColumn(String name) {
      AttributeRef ref = new AttributeRef(null, name);
      ref.setDataType(XSchema.STRING);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(XSchema.STRING);
      return col;
   }

   private static GroupRef groupRef(String field) {
      return new GroupRef(rawColumn(field));
   }

   private static AggregateRef aggregateRef(String field) {
      return new AggregateRef(rawColumn(field), AggregateFormula.COUNT_ALL);
   }

   /**
    * Builds a worksheet where "T" is ALREADY a mirror over "T_base" -- simulating the state a
    * wiz dashboard's own WsMergeService#ensureBaseHasPrevMirror produces, entirely independent
    * of this test/class -- then attaches a chart bound to "T" and triggers a viewsheet
    * repopulate (the same {@code resetWS()} path a chart-merge or viewsheet-open runs through).
    */
   @Test
   void doesNotReWrapATableAlreadyMirroredBySomeOtherMechanism() throws Exception {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly base = aggregatedPhysicalTable(ws, "T_base");
      ws.addAssembly(base);
      MirrorTableAssembly existingMirror = new MirrorTableAssembly(ws, "T", base);
      ws.addAssembly(existingMirror);

      AssetEntry wentry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Viewsheet vs = new Viewsheet(wentry);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T"));
      vs.addAssembly(chart);

      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getSheet(eq(wentry), isNull(), eq(false), any(AssetContent.class))).thenReturn(ws);

      vs.repopulateWorksheet(rep, null);

      Worksheet resultWs = vs.getBaseWorksheet();
      assertNotNull(resultWs);

      // "T" must still be the SAME pre-existing mirror -- not replaced by a fresh, unrelated one.
      assertSame(existingMirror, resultWs.getAssembly("T"),
         "createMirrorTable must not re-wrap a table that is already ANY MirrorTableAssembly, " +
         "regardless of what created it or what naming convention it used");

      // "T_base" must be untouched -- no additional rename/re-wrap should have occurred.
      assertNotNull(resultWs.getAssembly("T_base"));
      assertNull(resultWs.getAssembly("T_base_O"),
         "no additional outer-wrap rename should have happened for an already-mirrored table");

      // The base's own aggregation must survive completely unrelated to this chart's own binding.
      PhysicalBoundTableAssembly baseAfter = (PhysicalBoundTableAssembly) resultWs.getAssembly("T_base");
      assertFalse(baseAfter.getAggregateInfo().isEmpty());
      assertEquals(1, baseAfter.getAggregateInfo().getGroupCount());
      assertEquals(1, baseAfter.getAggregateInfo().getAggregateCount());
   }
}

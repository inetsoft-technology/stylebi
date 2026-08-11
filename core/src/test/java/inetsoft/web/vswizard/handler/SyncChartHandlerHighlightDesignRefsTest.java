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
package inetsoft.web.vswizard.handler;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSCube;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.graph.handler.ChartRegionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Regression for the reported bug: a highlight applied to a chart via /viewsheet/highlight
 * disappeared after switching chart type through the GUI (changeType), and — even after fixing
 * that — disappeared AGAIN the next time the chart re-executed (e.g. a second changeType call).
 *
 * <p>Root cause: {@link SyncChartHandler#syncHighlight} matched on {@code VSChartInfo#getRTFields}
 * (runtime refs). Runtime X/Y refs are transient — every execution discards them and clones fresh
 * ones from the DESIGN refs (see {@code WizVsService#applyHighlight}'s own comment on why it attaches
 * to design refs for exactly this reason). A highlight copied onto a runtime ref only survives until
 * the chart's next execution, at which point the ref carrying it is discarded and replaced with a
 * highlight-less clone of the (never-touched) design ref.
 *
 * <p>Drives the real handler (not mocks) — same reasoning as {@link SyncChartHandlerNullTempInfoTest}:
 * VSChartInfo/ChartVSAssembly need SreeEnv/Spring statics that can't be stubbed away.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SyncChartHandlerHighlightDesignRefsTest {
   private static ChartVSAssembly newChart(Viewsheet vs, String name, String measureColumn) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      VSChartInfo info = new VSChartInfo();

      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("STATE");
      info.addXField(dim);

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(measureColumn);
      agg.setFormulaValue("Sum");
      info.addYField(agg);

      chart.setVSChartInfo(info);
      chart.setXCube(new VSCube());
      chart.setChartDescriptor(new ChartDescriptor());
      return chart;
   }

   private static HighlightGroup redAboveThreshold() {
      HighlightGroup group = new HighlightGroup();
      TextHighlight highlight = new TextHighlight();
      highlight.setName("highlight1");
      group.addHighlight("highlight1", highlight);
      return group;
   }

   private static VSChartAggregateRef designAggregate(ChartVSAssembly chart) {
      for(ChartRef ref : chart.getVSChartInfo().getBindingRefs(false)) {
         if(ref instanceof VSChartAggregateRef aggRef) {
            return aggRef;
         }
      }

      throw new IllegalStateException("no aggregate ref bound");
   }

   /**
    * The fix: syncHighlight must land the copy on the TARGET's design ref, so it is still there the
    * next time the chart executes (runtime refs get regenerated fresh from design on every execution;
    * a copy that only ever touched the runtime ref would vanish on that next execution).
    */
   @Test
   void copiesHighlightOntoTheTargetsDesignRef() {
      SyncChartHandler handler = new SyncChartHandler(mock(ChartRegionHandler.class));
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly from = newChart(vs, "Source", "SALES_AMOUNT");
      ChartVSAssembly target = newChart(vs, "Target", "SALES_AMOUNT");

      designAggregate(from).setHighlightGroup(redAboveThreshold());
      assertNull(designAggregate(target).getHighlightGroup(), "target starts with no highlight");

      handler.syncHighlight(from, target);

      assertNotNull(designAggregate(target).getHighlightGroup(),
         "syncHighlight must copy onto the target's DESIGN ref — a copy that only reached a " +
         "transient runtime ref would be silently discarded the next time the chart executes");
   }

   /** No matching column on the target — nothing to copy onto, must not throw. */
   @Test
   void noMatchingColumnIsANoOp() {
      SyncChartHandler handler = new SyncChartHandler(mock(ChartRegionHandler.class));
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly from = newChart(vs, "Source", "SALES_AMOUNT");
      ChartVSAssembly target = newChart(vs, "Target", "ORDER_COUNT");

      designAggregate(from).setHighlightGroup(redAboveThreshold());

      handler.syncHighlight(from, target);

      assertNull(designAggregate(target).getHighlightGroup());
   }
}

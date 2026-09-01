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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * recordFieldSettingsOnTempChart — the write side of the wizard temp chart as durable binding state,
 * and the column guard that keeps it honest.
 *
 * <p>A wiz conversation reuses ONE recommendation runtime, so its temp chart describes exactly one of
 * the session's charts at a time. Recording a DIFFERENT chart's settings onto it (the user edits an
 * earlier history card) would make it lie about the chart it names — and RECORD being authoritative,
 * it would also clear what that chart had, so the next chart-type change pushes the wrong settings
 * back. The guard is a column comparison: settings can move between two bindings of the same columns,
 * nothing can move between different ones.
 */
@Tag("core")
class WizVsServiceRecordFieldSettingsTest {
   private static final String AUTO_BINDING_RUNTIME_ID = "wiz-rec-1";

   private VSWizardTemporaryInfoService tempInfoService;
   private VSTemporaryInfo tempInfo;
   private WizVsService service;
   private Principal user;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      tempInfoService = mock(VSWizardTemporaryInfoService.class);
      user = mock(Principal.class);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(viewsheetService.getViewsheet(AUTO_BINDING_RUNTIME_ID, user)).thenReturn(rvs);

      tempInfo = mock(VSTemporaryInfo.class);
      when(tempInfoService.getVSTemporaryInfo(rvs)).thenReturn(tempInfo);

      service = new WizVsService(viewsheetService, null, null, null, tempInfoService, null);
   }

   /** Puts a temp chart bound to {@code columns} behind the recommendation runtime, and returns its refs. */
   private VSChartDimensionRef[] tempChartBoundTo(String... columns) {
      VSChartDimensionRef[] refs = new VSChartDimensionRef[columns.length];
      ChartRef[] x = new ChartRef[columns.length];

      for(int i = 0; i < columns.length; i++) {
         refs[i] = mock(VSChartDimensionRef.class);
         when(refs[i].getGroupColumnValue()).thenReturn(columns[i]);
         x[i] = refs[i];
      }

      VSChartInfo info = mock(VSChartInfo.class);
      when(info.getXFields()).thenReturn(x);
      when(info.getYFields()).thenReturn(new ChartRef[0]);

      ChartVSAssembly tempChart = mock(ChartVSAssembly.class);
      when(tempChart.getVSChartInfo()).thenReturn(info);
      when(tempInfo.getTempChart()).thenReturn(tempChart);

      return refs;
   }

   private static DataRef rankedSnapshotDim(String column) {
      VSChartDimensionRef dim = mock(VSChartDimensionRef.class);
      when(dim.getGroupColumnValue()).thenReturn(column);
      when(dim.getRankingOptionValue()).thenReturn(String.valueOf(XCondition.TOP_N));
      when(dim.getRankingNValue()).thenReturn("3");
      return dim;
   }

   @Test
   void recordsOntoTheSameBindingAndMovesTheMarkerToThatAssembly() {
      VSChartDimensionRef temp = tempChartBoundTo("MONTH")[0];

      service.recordFieldSettingsOnTempChart(
         AUTO_BINDING_RUNTIME_ID, new DataRef[] { rankedSnapshotDim("MONTH") }, "Chart3", user);

      verify(temp).setRankingOptionValue(String.valueOf(XCondition.TOP_N));
      verify(temp).setRankingNValue("3");
      // The marker follows even for an in-place edit: an explicit re-bind lands in a NEWLY named
      // assembly, so "same chart, same name" is the exception rather than the rule here.
      verify(tempInfo).setWizSourceAssemblyName("Chart3");
   }

   /**
    * The load-bearing case. Recording an earlier card's settings onto a temp chart bound to different
    * columns would half-apply them (only the columns that happen to pair) and, being authoritative,
    * clear the rest — so instead the marker is dropped and changeType rebuilds from the chart itself.
    */
   @Test
   void skipsADifferentBindingAndClearsTheMarker() {
      VSChartDimensionRef temp = tempChartBoundTo("REGION")[0];

      service.recordFieldSettingsOnTempChart(
         AUTO_BINDING_RUNTIME_ID, new DataRef[] { rankedSnapshotDim("MONTH") }, "Chart3", user);

      verify(temp, never()).setRankingOptionValue(any());
      verify(tempInfo).setWizSourceAssemblyName(null);
   }

   /** A column added by an explicit re-bind cannot reach the temp chart, so it is stale for that chart. */
   @Test
   void skipsAnAddedColumnAndClearsTheMarker() {
      tempChartBoundTo("MONTH");

      service.recordFieldSettingsOnTempChart(
         AUTO_BINDING_RUNTIME_ID,
         new DataRef[] { rankedSnapshotDim("MONTH"), rankedSnapshotDim("amount") }, "Chart3", user);

      verify(tempInfo).setWizSourceAssemblyName(null);
   }

   /** No wizard runtime (the MCP path) or nothing to record: the marker must not be touched either. */
   @Test
   void nothingToRecordLeavesTheMarkerAlone() {
      tempChartBoundTo("MONTH");

      service.recordFieldSettingsOnTempChart(null, new DataRef[] { rankedSnapshotDim("MONTH") },
                                             "Chart3", user);
      service.recordFieldSettingsOnTempChart(AUTO_BINDING_RUNTIME_ID, new DataRef[0], "Chart3", user);
      service.recordFieldSettingsOnTempChart(AUTO_BINDING_RUNTIME_ID, null, "Chart3", user);

      verify(tempInfo, never()).setWizSourceAssemblyName(any());
   }

   /** An RVS that never went through autoBinding has no temp chart to record on or invalidate. */
   @Test
   void aMissingTempChartIsANoOp() {
      when(tempInfo.getTempChart()).thenReturn(null);

      service.recordFieldSettingsOnTempChart(
         AUTO_BINDING_RUNTIME_ID, new DataRef[] { rankedSnapshotDim("MONTH") }, "Chart3", user);

      verify(tempInfo, never()).setWizSourceAssemblyName(any());
   }
}

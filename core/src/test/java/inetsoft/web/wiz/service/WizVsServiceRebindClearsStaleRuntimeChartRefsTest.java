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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.CreateVisualizationModel;
import inetsoft.web.wiz.model.VisualizationConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the "wiz in-place chart mutation staleness" class of bug (community #3907),
 * second instance, fixed in {@code WizVsService#createViewsheetInternal} right after a chart
 * assembly is (re)bound onto an ALREADY-EXECUTED runtime ({@code createdRuntimeId == false}).
 *
 * <p>{@code rebindAssembly} clones the caller-supplied primary assembly's {@code VSAssemblyInfo}
 * -- {@code ChartVSAssemblyInfo.clone(false)} deep-clones the design {@code VSChartInfo} but does
 * NOT clone its RUNTIME ref caches
 * ({@code getRTYFields()}/{@code getRTXFields()}; {@code AbstractChartInfo.clone()} never touches
 * them, so they survive as the SAME array reference). A prior execution's runtime refs riding
 * along on a freshly rebound design (e.g. a measure's {@code secondaryY} flip) then silently
 * shadow the new design binding at render time -- the chart appears to ignore the rebind
 * entirely. The fix clears both RT ref arrays and drops the sandbox's cached graph immediately
 * after the rebound assembly is added to the runtime, forcing the next render to resolve fresh
 * refs from the new design binding.
 *
 * <p>Rather than driving two full sequential executions through a real {@link ViewsheetSandbox}
 * (heavy, and not what the fix changed), this test proves the fix's actual mechanism directly:
 * given a primary assembly whose {@code VSChartInfo} already carries non-empty, stale RT ref
 * arrays alongside a fresh design binding (exactly the shape {@code rebindAssembly}'s clone
 * produces), the rebound assembly's {@code VSChartInfo} must come out of
 * {@code createViewsheetInternal} with those RT arrays cleared, and the sandbox's cached graph for
 * that assembly must have been dropped -- both are the necessary and sufficient conditions for
 * the next render to resolve the new design binding instead of the stale runtime one. Follows the
 * {@link WizVsServiceFilterCopyTest} construction pattern (real {@link WizVsService} wrapped in a
 * spy so the heavy sandbox-execution/binding-echo/persistence machinery can be stubbed out).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceRebindClearsStaleRuntimeChartRefsTest {

   @Test
   void rebindingAChartOnAnAlreadyExecutedRuntimeClearsStaleRTRefsAndDropsTheCachedGraph()
      throws Exception
   {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      Principal user = mock(Principal.class);

      WizVsService real = new WizVsService(viewsheetService, engine, securityEngine);
      WizVsService service = spy(real);

      // Source worksheet resolveSourceContext must find, with a primary table assembly.
      AssetEntry sourceWsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "ws1", null);
      Worksheet worksheet = new Worksheet();
      PhysicalBoundTableAssembly baseTable = new PhysicalBoundTableAssembly(worksheet, "BASE");
      worksheet.addAssembly(baseTable);
      worksheet.setPrimaryAssembly("BASE");
      when(engine.getSheet(any(AssetEntry.class), eq(user), eq(true), any()))
         .thenReturn(worksheet);

      // An EXISTING, already-executed runtime (createdRuntimeId == false: model supplies
      // runtimeId "rt-1" below). Real Viewsheet -- in this (non-"createdRuntimeId") path,
      // createViewsheetInternal's targetVs IS this same vs object (mutated in place), so no
      // separate captured-argument bookkeeping is needed to inspect the result.
      Viewsheet vs = new Viewsheet(sourceWsEntry, false, true, null, null);
      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt-1");
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      when(viewsheetService.getViewsheet(eq("rt-1"), eq(user))).thenReturn(rvs);

      // Bypass the heavy, separately-tested sandbox-execution/binding-echo/persistence machinery --
      // this test is only about the RT-ref-clearing wiring itself.
      doReturn(new CreateViewsheetResult()).when(service).executeAndExtract(any(), any(), anyInt());
      doReturn(null).when(service).collectFlatBinding(any());
      doReturn("vs-identifier").when(service).persistViewsheet(any(), any(), any());

      // The wizard setup path's fully-configured primary assembly: a fresh DESIGN binding (Sales
      // on the secondary axis) plus a STALE RUNTIME ref cache left over from a prior execution
      // (of an earlier binding) that rebindAssembly's clone carries forward unchanged.
      Viewsheet wizardVs = new Viewsheet();
      ChartVSAssembly preConfigured = new ChartVSAssembly(wizardVs, "wizardChart");
      preConfigured.setPrimary(true);
      DefaultVSChartInfo chartInfo = new DefaultVSChartInfo();

      VSChartAggregateRef designY = new VSChartAggregateRef();
      designY.setColumnValue("Sales");
      designY.setFormulaValue("Sum");
      designY.setSecondaryY(true);
      chartInfo.addYField(designY);

      VSChartDimensionRef designX = new VSChartDimensionRef();
      designX.setGroupColumnValue("Category");
      chartInfo.addXField(designX);

      VSChartAggregateRef staleRTY = new VSChartAggregateRef();
      staleRTY.setColumnValue("StaleMeasure");
      chartInfo.setRTYFields(new inetsoft.uql.viewsheet.graph.ChartRef[] { staleRTY });

      VSChartDimensionRef staleRTX = new VSChartDimensionRef();
      staleRTX.setGroupColumnValue("StaleDim");
      chartInfo.setRTXFields(new inetsoft.uql.viewsheet.graph.ChartRef[] { staleRTX });

      preConfigured.setVSChartInfo(chartInfo);

      // Sanity check on the test's own fixture: the staleness must actually be present before the
      // fix runs, else this test would trivially pass for the wrong reason.
      assertEquals(1, chartInfo.getRTYFields().length);
      assertEquals(1, chartInfo.getRTXFields().length);

      VisualizationConfig.DataSource dataSource = new VisualizationConfig.DataSource();
      dataSource.setSource(sourceWsEntry.toIdentifier());
      VisualizationConfig config = new VisualizationConfig();
      config.setData(dataSource);

      CreateVisualizationModel model = new CreateVisualizationModel();
      model.setRuntimeId("rt-1");
      model.setConfig(config);
      model.setPrimaryAssembly(preConfigured);

      CreateViewsheetResult result = service.createViewsheet(model, user);

      VSAssembly reboundAssembly = vs.getAssembly(result.getAssemblyName());
      ChartVSAssembly reboundChart = assertInstanceOf(ChartVSAssembly.class, reboundAssembly);
      VSChartInfo reboundInfo = reboundChart.getVSChartInfo();

      // getRTYFields()/getRTXFields() fall back to the DESIGN fields once the RT array is empty
      // (AbstractChartInfo: "ryrefs != null && ryrefs.length > 0 ? ryrefs : getYFields()") -- so an
      // empty-length check alone would pass even with the bug present, since both the stale RT
      // array and the design array here happen to have exactly one element. The real assertion is
      // WHICH ref resolves: it must be the fresh design measure ("Sales"), not the stale runtime
      // one ("StaleMeasure") that a rebind-without-the-fix would keep shadowing it with.
      assertEquals(1, reboundInfo.getRTYFields().length);
      assertEquals("Sales", ((VSChartAggregateRef) reboundInfo.getRTYFields()[0]).getColumnValue(),
                   "stale RT Y ref must be cleared so it falls through to the fresh design binding");

      assertEquals(1, reboundInfo.getRTXFields().length);
      assertEquals("Category", ((VSChartDimensionRef) reboundInfo.getRTXFields()[0]).getGroupColumnValue(),
                   "stale RT X ref must be cleared so it falls through to the fresh design binding");

      // The sandbox's cached graph for this assembly must also be dropped, forcing re-resolution
      // against the now-clean design binding on the next render.
      verify(sandbox).clearGraph(result.getAssemblyName());
   }
}

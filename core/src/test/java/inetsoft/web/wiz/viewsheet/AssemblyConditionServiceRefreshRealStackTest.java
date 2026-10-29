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
package inetsoft.web.wiz.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.data.DataSet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.drm.*;
import inetsoft.web.binding.model.*;
import inetsoft.web.binding.service.DataRefModelFactory;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.VSConditionDialogService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.viewsheet.service.ParameterService;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Bug #79543: {@code set_condition} returned before the target assembly was re-executed whenever
 * ANY assembly of the viewsheet held a prompt variable nobody had answered, so the first read
 * after the write returned the previous condition's data. The Composer's condition service sends
 * a parameter prompt and returns early in that case, and nothing answers the prompt on the agent
 * path. Runs the real condition, assembly-info and refresh services on the real stack
 * (EmbeddedVS1) with an unset-variable condition on another assembly, and reads the target's
 * data straight after the write.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class AssemblyConditionServiceRefreshRealStackTest {
   /** The reported case: a chart, with an unresolved variable on another assembly. */
   @Test
   void chartReflectsTheNewConditionWithAnUnresolvedVariableElsewhere() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs);
      AssemblyConditionService service = service(rvs);
      int unfiltered = rows(rvs, CHART);
      assertTrue(unfiltered > 0, "the chart plots data before any condition");

      service.set("tok", principal(), TABLE, List.of(variableClause()), "");
      service.set("tok", principal(), CHART, List.of(noMatch()), "");

      assertEquals(0, rows(rvs, CHART), "no read in between: the new condition must apply");

      service.set("tok", principal(), CHART, List.of(), "");

      assertEquals(unfiltered, rows(rvs, CHART), "clearing it must apply just as promptly");
   }

   /** Any assembly type the condition dialog accepts is stale the same way, not only charts. */
   @Test
   void tableReflectsTheNewConditionWithAnUnresolvedVariableElsewhere() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs);
      AssemblyConditionService service = service(rvs);
      int unfiltered = rows(rvs, TABLE);
      assertTrue(unfiltered > 0, "the table shows rows before any condition");

      // the variable sits on the chart this time
      service.set("tok", principal(), CHART, List.of(variableClause()), "");
      service.set("tok", principal(), TABLE, List.of(noMatch()), "");

      assertEquals(0, rows(rvs, TABLE), "no read in between: the new condition must apply");

      service.set("tok", principal(), TABLE, List.of(), "");

      assertEquals(unfiltered, rows(rvs, TABLE));
   }

   /** Control: with no unresolved variable the write was always current. */
   @Test
   void chartReflectsTheNewConditionWhenNoVariableIsUnresolved() throws Exception {
      RuntimeViewsheet rvs = open();
      addChart(rvs);
      AssemblyConditionService service = service(rvs);

      service.set("tok", principal(), CHART, List.of(noMatch()), "");

      assertEquals(0, rows(rvs, CHART));
   }

   /**
    * Bug #79535: the rollback of a write that fails after it was committed went through the
    * write's own model, whose revision the commit had just bumped, so the real
    * {@code setModel} answered with a write-conflict message and left the NEW condition stored
    * while the caller was told "Nothing was changed". Mock-based tests cannot see this: only the
    * real service checks the revision.
    */
   @Test
   void aWriteThatFailsAfterTheCommitIsRolledBackToTheExactPreviousConditions() throws Exception {
      RuntimeViewsheet rvs = open();
      service(rvs).set("tok", principal(), TABLE, List.of(noMatch()), "");
      assertEquals(0, rows(rvs, TABLE), "the previous condition filters everything");
      AssemblyConditionService failing = service(rvs, true);

      assertThrows(RuntimeException.class,
                   () -> failing.set("tok", principal(), TABLE, List.of(), ""));

      Map<String, Object> read = failing.read("tok", principal(), TABLE);
      assertEquals(1, read.get("conditionCount"),
                   "the previous condition must be back, not the cleared list");
      assertEquals(0, rows(rvs, TABLE), "and it must filter again");
   }

   private static ConditionVocabulary.Clause variableClause() {
      return new ConditionVocabulary.Clause(
         "type", "equals", List.of(Map.of("type", "variable", "name", "unset79543")), null,
         false, false, 0);
   }

   private static ConditionVocabulary.Clause noMatch() {
      return new ConditionVocabulary.Clause(
         "type", "equals", List.of("no such type"), null, false, false, 0);
   }

   private static int rows(RuntimeViewsheet rvs, String name) throws Exception {
      Object data = rvs.getViewsheetSandbox().orElseThrow().getData(name);

      if(data instanceof DataSet set) {
         return set.getRowCount();
      }

      TableLens lens = (TableLens) data;
      lens.moreRows(Integer.MAX_VALUE);
      return Math.max(0, lens.getRowCount() - lens.getHeaderRowCount());
   }

   /**
    * The data-ref model factories the way Spring wires them in production: the wrapper
    * factories delegate to the service, which the shared test bean (built with {@code new})
    * never injects.
    */
   private static DataRefModelFactoryService wiredRefService() {
      List<DataRefModelFactory<?, ?>> factories = List.of(
         new AggregateRefModel.AggregateRefModelFactory(),
         new AttributeRefModel.AttributeRefModelFactory(),
         new ColumnRefModel.ColumnRefModelFactory(),
         new CalculateRefModel.CalculateRefModelFactory(),
         new ExpressionRefModel.ExpressionRefModelFactory(),
         new DateRangeRefModel.DateRangeRefModelFactory(),
         new NumericRangeRefModel.NumericRangeRefModelFactory());
      DataRefModelFactoryService service = new DataRefModelFactoryService(factories);

      for(DataRefModelFactory<?, ?> factory : factories) {
         if(factory instanceof DataRefModelWrapperFactory<?, ?> wrapper) {
            wrapper.setDataRefModelFactoryService(service);
         }
      }

      return service;
   }

   private AssemblyConditionService service(RuntimeViewsheet rvs) throws Exception {
      return service(rvs, false);
   }

   private AssemblyConditionService service(RuntimeViewsheet rvs, boolean firstRefreshFails)
      throws Exception
   {
      QueryManagerService queries = mock(QueryManagerService.class);
      DataRefModelFactoryService dataRefFactory = wiredRefService();
      VSAssemblyInfoHandler handler = new VSAssemblyInfoHandler(
         lifecycle, dataRefFactory, parameterService, dataSourceRegistry, queries);
      VSConditionDialogService conditions = new VSConditionDialogService(
         dataRefFactory, handler, viewsheetService, dataSourceRegistry, queries);
      VSRefreshService refresh = new VSRefreshService(
         lifecycle, viewsheetService, treeService, bookmarkService, parameterService,
         xSessionService);

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
            mutation.run(rvs, rvs.getID(), d);
            return d.getWarnings();
         });
      });
      if(firstRefreshFails) {
         refresh = spy(refresh);
         doThrow(new RuntimeException("refresh failed")).doCallRealMethod().when(refresh)
            .refreshVsAssembly(any(), any(), any(), any(), any());
      }

      return new AssemblyConditionService(sessions, conditions, refresh);
   }

   private static void addChart(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) table.getSourceInfo().clone());
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      box.resetDataMap(CHART);
   }

   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      return rvs;
   }

   private XPrincipal principal() {
      return (XPrincipal) user;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;
   @Autowired CoreLifecycleService lifecycle;
   @Autowired ParameterService parameterService;
   @Autowired DataSourceRegistry dataSourceRegistry;
   @Autowired VSObjectTreeService treeService;
   @Autowired VSBookmarkService bookmarkService;
   @Autowired XSessionService xSessionService;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart1";
}

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
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.composer.vs.objects.event.ConvertToFreehandTableEvent;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bug #78406 round 3: {@code set_date_comparison}, {@code clear_date_comparison} and
 * {@code add}/{@code remove_hierarchy_dimension} re-run the table they write to, but their
 * services threw away the warning {@link ViewsheetSessionService#mutate} captured, so a crosstab
 * whose script failed read as plain success. Each test drives the REAL service stack (EmbeddedVS1,
 * a crosstab over a table whose JS pre-condition throws, the real {@code VSAssemblyInfoHandler} and
 * dialog services) through a seam that does what {@code mutate} does around the mutation, and
 * asserts the script failure is returned. Every case uses a fresh crosstab and a CHANGED
 * comparison: an identical repeat executes nothing and so warns nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class DateComparisonHierarchyRelayRealStackTest {
   @Test
   void setDateComparisonReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      addFailingExpressionCondition(rvs);

      Map<String, Object> result =
         dateComparison(rvs).set("tok", principal(), CROSSTAB, comparison(), "");

      assertFailureRelayed(warningsOf(result));
   }

   @Test
   void setDateComparisonIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);

      Map<String, Object> result =
         dateComparison(rvs).set("tok", principal(), CROSSTAB, comparison(), "");

      assertFalse(result.containsKey("warnings"), result.toString());
   }

   @Test
   void addHierarchyDimensionReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      addFailingExpressionCondition(rvs);

      Map<String, Object> result = hierarchy(rvs).add(
         "tok", principal(), CROSSTAB, List.of("type", "wind"), null, "");

      assertFailureRelayed(warningsOf(result));
   }

   @Test
   void addHierarchyDimensionIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);

      Map<String, Object> result = hierarchy(rvs).add(
         "tok", principal(), CROSSTAB, List.of("type", "wind"), null, "");

      assertFalse(result.containsKey("warnings"), result.toString());
   }

   @Test
   void clearDateComparisonReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      DateComparisonService service = dateComparison(rvs);
      service.set("tok", principal(), CROSSTAB, comparison(), "");
      addFailingExpressionCondition(rvs);

      assertFailureRelayed(service.clear("tok", principal(), CROSSTAB, ""));
   }

   @Test
   void clearDateComparisonIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      DateComparisonService service = dateComparison(rvs);
      service.set("tok", principal(), CROSSTAB, comparison(), "");

      assertEquals(List.of(), service.clear("tok", principal(), CROSSTAB, ""));
   }

   @Test
   void removeHierarchyDimensionReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      HierarchyDimensionService service = hierarchy(rvs);
      service.add("tok", principal(), CROSSTAB, List.of("type", "wind"), null, "");
      addFailingExpressionCondition(rvs);

      Map<String, Object> result = service.remove("tok", principal(), CROSSTAB, 0, "");

      assertFailureRelayed(warningsOf(result));
   }

   /** The unfixed convert answered plain success and left an empty 0x0 freehand table. */
   @Test
   void convertRefusesACrosstabWhoseScriptFailed() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      addFailingExpressionCondition(rvs);
      ComposerVSTableService tables = mock(ComposerVSTableService.class);
      AssemblyConvertService service = convert(rvs, tables);

      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
         () -> service.convert("tok", principal(), CROSSTAB, "freehand_table", ""));

      assertTrue(refused.getMessage().contains("did not render"), refused.getMessage());
      assertTrue(refused.getMessage().contains("undefinedFn"), refused.getMessage());
      verifyNoInteractions(tables);
   }

   @Test
   void convertStillConvertsAHealthyCrosstab() throws Exception {
      RuntimeViewsheet rvs = open();
      addCrosstab(rvs);
      ComposerVSTableService tables = mock(ComposerVSTableService.class);

      convert(rvs, tables).convert("tok", principal(), CROSSTAB, "freehand_table", "");

      verify(tables).convertToFreehandTable(
         anyString(), any(ConvertToFreehandTableEvent.class), any(Principal.class), anyString(),
         any());
   }

   private AssemblyConvertService convert(RuntimeViewsheet rvs, ComposerVSTableService tables)
      throws Exception
   {
      return new AssemblyConvertService(
         seam(rvs), mock(ComposerVSSelectionListService.class),
         mock(ComposerRangeSliderService.class), tables);
   }

   private static DateComparisonService.Comparison comparison() {
      return new DateComparisonService.Comparison("1", "year", null, true, null, null, null, null,
                                                  null, null, null);
   }

   @SuppressWarnings("unchecked")
   private static List<String> warningsOf(Map<String, Object> result) {
      Object warnings = result.get("warnings");
      return warnings == null ? List.of() : (List<String>) warnings;
   }

   private static void assertFailureRelayed(List<String> warnings) {
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX),
                 warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   private DateComparisonService dateComparison(RuntimeViewsheet rvs) throws Exception {
      return new DateComparisonService(seam(rvs), dateComparisonDialogService());
   }

   private DateComparisonDialogService dateComparisonDialogService() {
      return new DateComparisonDialogService(
         viewsheetService, assemblyInfoHandler(), mock(VisualFrameModelFactoryService.class));
   }

   private HierarchyDimensionService hierarchy(RuntimeViewsheet rvs) throws Exception {
      VSDialogService dialogs = mock(VSDialogService.class);
      when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(10, 10));
      when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(300, 200));
      CrosstabPropertyDialogService crosstabs = new CrosstabPropertyDialogService(
         objectPropertyService(), dialogs, viewsheetService, assemblyInfoHandler(),
         new VSTrapService(dataSourceRegistry));

      return new HierarchyDimensionService(
         seam(rvs), mock(ChartPropertyDialogService.class), crosstabs);
   }

   private VSAssemblyInfoHandler assemblyInfoHandler() {
      return new VSAssemblyInfoHandler(
         coreLifecycleService, mock(DataRefModelFactoryService.class),
         mock(ParameterService.class), dataSourceRegistry, mock(QueryManagerService.class));
   }

   private VSObjectPropertyService objectPropertyService() {
      return new VSObjectPropertyService(
         coreLifecycleService, new VSColumnHandler(viewsheetService), treeService,
         mock(VSAssemblyInfoHandler.class), mock(VSWizardTemporaryInfoService.class),
         compositionService, sharedFilterService, dataSourceRegistry,
         mock(QueryManagerService.class));
   }

   /**
    * Does around the mutation what {@link ViewsheetSessionService#mutate} does (run it under the
    * capturing dispatcher, return its warnings); the checkpoint and the broadcast are not part of
    * what is under test.
    */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         Principal agent = inv.getArgument(1);
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
            mutation.run(rvs, rvs.getID(), dispatcher);
            return dispatcher.getWarnings();
         });
      });
      return sessions;
   }

   /** A crosstab on TableView1's worksheet source with a date dimension, so a date comparison can be set on it. */
   private static void addCrosstab(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      crosstab.setSourceInfo(table.getSourceInfo());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

      AttributeRef dateAttr = new AttributeRef(null, "datetime");
      dateAttr.setDataType(XSchema.DATE);
      ColumnRef dateCol = new ColumnRef(dateAttr);
      dateCol.setDataType(XSchema.DATE);
      VSDimensionRef date = new VSDimensionRef(dateCol);
      date.setGroupColumnValue("datetime");
      date.setDateLevelValue(DateRangeRef.YEAR_INTERVAL + "");
      cinfo.setDesignRowHeaders(new DataRef[] { date });

      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
   }

   private static void addFailingExpressionCondition(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      CrosstabVSAssembly crosstab = (CrosstabVSAssembly) box.getViewsheet().getAssembly(CROSSTAB);
      DataRef type = table.getColumnSelection().getAttribute("type");
      assertNotNull(type, "type column");

      ExpressionValue expression = new ExpressionValue();
      expression.setExpression("undefinedFn(1)");
      expression.setType(ExpressionValue.JAVASCRIPT);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(expression);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(type, cond, 0));
      crosstab.setPreConditionList(list);
      box.resetDataMap(CROSSTAB);
   }

   /** The runtime plus the principal it was opened with; a different one is "Invalid user". */
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
   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired VSObjectTreeService treeService;
   @Autowired VSCompositionService compositionService;
   @Autowired SharedFilterService sharedFilterService;
   @Autowired DataSourceRegistry dataSourceRegistry;

   private Principal user;
   private static final String TABLE = "TableView1";
   private static final String CROSSTAB = "ProbeXt";
}

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
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.IntegerEditorModel;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78406 round 4: {@code set_column_options} re-runs the Form table it writes to, but
 * {@link ColumnOptionService#set} threw away the warning {@link ViewsheetSessionService#mutate}
 * captured, so a Form table whose script failed read as plain success. The test drives the REAL
 * {@code ColumnOptionService} and {@code VSInputService} (EmbeddedVS1, a Form table whose JS
 * pre-condition throws) through a seam that does what {@code mutate} does around the mutation, and
 * asserts the script failure is returned.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ColumnOptionRelayRealStackTest {
   @Test
   void setColumnOptionsReturnsTheScriptFailure() throws Exception {
      RuntimeViewsheet rvs = open();
      makeForm(rvs);
      addFailingExpressionCondition(rvs);

      List<String> warnings = setIntegerEditor(rvs);

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX),
                 warnings.get(0));
      assertTrue(warnings.get(0).contains("undefinedFn"), warnings.get(0));
   }

   @Test
   void setColumnOptionsIsSilentWhenTheScriptIsFine() throws Exception {
      RuntimeViewsheet rvs = open();
      makeForm(rvs);

      assertEquals(List.of(), setIntegerEditor(rvs));
   }

   private List<String> setIntegerEditor(RuntimeViewsheet rvs) throws Exception {
      IntegerEditorModel editor = new IntegerEditorModel();
      editor.setMinimum(0);
      editor.setMaximum(100000);
      VSDialogService dialogs = mock(VSDialogService.class);
      when(dialogs.getAssemblyPosition(any(), any())).thenReturn(new Point(10, 10));
      when(dialogs.getAssemblySize(any(), any())).thenReturn(new Dimension(300, 200));
      VSColumnHandler columns = new VSColumnHandler(viewsheetService);
      VSAssemblyInfoHandler infoHandler = new VSAssemblyInfoHandler(
         coreLifecycleService, mock(DataRefModelFactoryService.class),
         mock(ParameterService.class), dataSourceRegistry, mock(QueryManagerService.class));
      VSObjectPropertyService properties = new VSObjectPropertyService(
         coreLifecycleService, columns, treeService, mock(VSAssemblyInfoHandler.class),
         mock(VSWizardTemporaryInfoService.class), compositionService, sharedFilterService,
         dataSourceRegistry, mock(QueryManagerService.class));
      VSInputService inputs = new VSInputService(
         mock(VSObjectService.class), coreLifecycleService, viewsheetService, properties, dialogs,
         new VSTrapService(dataSourceRegistry), mock(DataRefModelFactoryService.class),
         infoHandler, columns, mock(QueryManagerService.class));

      return new ColumnOptionService(seam(rvs), inputs, columns)
         .set("tok", principal(), TABLE, 0, true, "Integer", editor, "");
   }

   /**
    * Does around the mutation what {@link ViewsheetSessionService#mutate} does (run it under the
    * capturing dispatcher, return its warnings); the checkpoint and the broadcast are not part of
    * what is under test.
    */
   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
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

   private static void makeForm(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      ((TableVSAssemblyInfo) table.getVSAssemblyInfo()).setForm(true);
   }

   private static void addFailingExpressionCondition(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
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
      table.setPreConditionList(list);
      box.resetDataMap(TABLE);
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
}

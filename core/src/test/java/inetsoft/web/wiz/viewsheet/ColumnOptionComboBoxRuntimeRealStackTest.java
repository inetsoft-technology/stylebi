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
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.FormRef;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.portal.controller.database.QueryManagerService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80806: in a runtime (Preview) pairing the second {@code set_column_options} on a ComboBox
 * column was a silent no-op, because {@code ViewsheetSandbox.executeView} restores the option's
 * list from the cached {@code T^<col>^__FORM_OPTION__} entry, which no write ever cleared. A
 * values-only list was also stored with no labels, so {@code FormTableRowService} (which scans the
 * labels) could not restrict input. Drives the REAL {@code ColumnOptionService},
 * {@code VSInputService} and execute path on EmbeddedVS1 whose Form table has FormRef columns.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ColumnOptionComboBoxRuntimeRealStackTest {
   @Test
   void everyComboBoxRewriteTakesEffect() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      assertTrue(rvs.getViewsheetSandbox().orElseThrow().isRuntime());

      setCombo(service, "string", new String[] { "a", "b" }, new String[] { "a", "b" });
      assertList(service, "a", "b");
      setCombo(service, "string", new String[] { "c", "d" }, new String[] { "c", "d" });
      assertList(service, "c", "d");
      setCombo(service, "string", new String[] { "e", "f" }, new String[] { "e", "f" });
      assertList(service, "e", "f");
   }

   @Test
   void aTextWriteBetweenTwoComboBoxWritesDoesNotRestoreTheOldList() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "a", "b" }, new String[] { "a", "b" });
      service.set("tok", currentUser, TABLE, 0, true, "Text", new TextEditorModel(), "");
      setCombo(service, "string", new String[] { "c", "d" }, new String[] { "c", "d" });

      assertList(service, "c", "d");
   }

   @Test
   void disablingTheEditorBetweenTwoComboBoxWritesDoesNotRestoreTheOldList() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "a", "b" }, new String[] { "a", "b" });
      service.set("tok", currentUser, TABLE, 0, false, null, null, "");
      setCombo(service, "string", new String[] { "c", "d" }, new String[] { "c", "d" });

      assertList(service, "c", "d");
   }

   @Test
   void aValuesOnlyListDefaultsItsLabelsToTheValues() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "g", "h" }, new String[0]);

      assertList(service, "g", "h");
   }

   @Test
   void aValuesOnlyListWithNoLabelsAtAllDefaultsToo() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "g", "h" }, null);

      assertList(service, "g", "h");
   }

   @Test
   void aNullValueKeepsANullLabel() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "x", null }, new String[0]);

      VariableListDialogModel list = readList(service);
      assertArrayEquals(new String[] { "x", null }, list.getValues());
      assertArrayEquals(new String[] { "x", null }, list.getLabels());
   }

   @Test
   void explicitLabelsAreKept() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "string", new String[] { "1", "2" }, new String[] { "One", "Two" });

      VariableListDialogModel list = readList(service);
      assertArrayEquals(new String[] { "1", "2" }, list.getValues());
      assertArrayEquals(new String[] { "One", "Two" }, list.getLabels());
   }

   @Test
   void anIntegerListDefaultsItsLabelsToTheTypedText() throws Exception {
      RuntimeViewsheet rvs = open();
      makeFormWithFormRefs(rvs);
      ColumnOptionService service = service(rvs);

      setCombo(service, "integer", new String[] { "1", "2" }, new String[0]);

      assertArrayEquals(new String[] { "1", "2" }, readList(service).getLabels());
   }

   private static void assertList(ColumnOptionService service, String... expected)
      throws Exception
   {
      VariableListDialogModel list = readList(service);
      assertArrayEquals(expected, list.getLabels(), "labels");
      assertArrayEquals(expected, list.getValues(), "values");
   }

   private static VariableListDialogModel readList(ColumnOptionService service) throws Exception {
      ColumnOptionDialogModel model = service.get("tok", currentUser, TABLE, 0);
      assertTrue(model.getEditor() instanceof ComboBoxEditorModel,
                 "editor is " + model.getEditor());
      return ((ComboBoxEditorModel) model.getEditor()).getVariableListDialogModel();
   }

   private static void setCombo(ColumnOptionService service, String dataType, String[] values,
                                String[] labels) throws Exception
   {
      VariableListDialogModel list = new VariableListDialogModel();
      list.setDataType(dataType);
      list.setValues(values);
      list.setLabels(labels);
      ComboBoxEditorModel editor = new ComboBoxEditorModel();
      editor.setEmbedded(true);
      editor.setDataType(dataType);
      editor.setVariableListDialogModel(list);
      service.set("tok", currentUser, TABLE, 0, true, "ComboBox", editor, "");
   }

   private ColumnOptionService service(RuntimeViewsheet rvs) throws Exception {
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

      return new ColumnOptionService(seam(rvs), inputs, columns);
   }

   /** Runs reads and mutations on the one runtime viewsheet, as the session service would. */
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
      when(sessions.read(anyString(), any(), any())).thenAnswer(inv -> {
         Principal agent = inv.getArgument(1);
         ViewsheetSessionService.Read<?> read = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher ->
            read.run(rvs, rvs.getID(), dispatcher));
      });
      return sessions;
   }

   /** Does what the Form toggle does: every column becomes a FormRef. */
   private static void makeFormWithFormRefs(RuntimeViewsheet rvs) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      TableVSAssembly table = (TableVSAssembly) box.getViewsheet().getAssembly(TABLE);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setForm(true);
      ColumnSelection columns = info.getColumnSelection();
      ColumnSelection copy = (ColumnSelection) Tool.clone(columns);
      columns.clear();

      for(int i = 0; i < copy.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) copy.getAttribute(i);

         if(ref != null) {
            columns.addAttribute(i, FormRef.toFormRef(ref));
         }
      }

      box.resetDataMap(TABLE);
   }

   private RuntimeViewsheet open() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      currentUser = rvs.getUser();
      return rvs;
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

   private static Principal currentUser;
   private static final String TABLE = "TableView1";
}

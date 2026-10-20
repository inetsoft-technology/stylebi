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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.command.AddVSObjectCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.event.*;
import inetsoft.web.viewsheet.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78878, the reported Viewer sequence: a ComboBox, a Spinner and a SelectionList, all
 * with submit-on-change off, and a Submit button. On the click the Viewer first sends the
 * pending SelectionList pick (its own apply event, with the Submit as the event source) and
 * then the Submit's onclick event with the ComboBox and Spinner values. Both events write to
 * one dispatcher, and the last model sent for each assembly is what the browser ends up
 * showing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class VSInputServiceSubmitAfterSelectionTest {
   @BeforeEach
   void savePrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void refreshOffShowsAllThreeValues() throws Exception {
      assertBrowserShowsSubmittedValues(false);
   }

   @Test
   void refreshOnShowsAllThreeValues() throws Exception {
      assertBrowserShowsSubmittedValues(true);
   }

   private void assertBrowserShowsSubmittedValues(boolean refresh) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();

      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "CategoryPicker");
      ComboBoxVSAssemblyInfo comboInfo = (ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo();
      comboInfo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      comboInfo.setDataType(XSchema.STRING);
      String[] categories = { "(All)", "Games", "Toys" };
      ListData data = new ListData();
      data.setValues(categories);
      data.setLabels(categories);
      data.setDataType(XSchema.STRING);
      comboInfo.setListData(data);
      comboInfo.setValues(categories);
      comboInfo.setLabels(categories);
      comboInfo.setSelectedObject("(All)");
      comboInfo.setSubmitOnChange(false);
      vs.addAssembly(combo);

      SpinnerVSAssembly spinner = new SpinnerVSAssembly(vs, "TopNSpinner");
      SpinnerVSAssemblyInfo spinnerInfo = (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
      spinnerInfo.setMinValue("1");
      spinnerInfo.setMaxValue("20");
      spinnerInfo.setIncrementValue("1");
      spinnerInfo.setSelectedObject(1);
      spinnerInfo.setSubmitOnChange(false);
      vs.addAssembly(spinner);

      String table = ((TableVSAssembly) vs.getAssembly("TableView1")).getSourceInfo().getSource();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "RegionList");
      SelectionListVSAssemblyInfo listInfo = (SelectionListVSAssemblyInfo) list.getInfo();
      listInfo.setTableName(table);
      listInfo.setDataRef(new ColumnRef(new AttributeRef(null, "type")));
      listInfo.setSubmitOnChange(false);
      vs.addAssembly(list);

      SubmitVSAssembly button = new SubmitVSAssembly(vs, "ApplyButton");
      ((SubmitVSAssemblyInfo) button.getVSAssemblyInfo()).setRefresh(refresh);
      vs.addAssembly(button);

      vs.addAssembly(new TextVSAssembly(vs, "Echo"));
      vs.getViewsheetInfo().setOnLoad(
         "Echo.text = 'cat=' + CategoryPicker.selectedObject + ';reg=' + RegionList.selectedObjects" +
         " + ';n=' + TopNSpinner.selectedObject;");

      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      String region = ((SelectionListVSAssembly) vs.getAssembly("RegionList")).getSelectionList()
         .getSelectionValue(0).getValue();
      assertEquals("cat=(All);reg=;n=1", echoText());

      String rid = vsResource.getRuntimeId();
      Principal principal = ThreadContext.getPrincipal();
      CommandDispatcher browser = dispatcher();

      // 1. the pending selection list pick, sent at once by the global submit
      ApplySelectionListEvent selectionEvent = new ApplySelectionListEvent();
      selectionEvent.setType(ApplySelectionListEvent.Type.APPLY);
      ApplySelectionListEvent.Value pick = new ApplySelectionListEvent.Value();
      pick.setValue(new String[] { region });
      pick.setSelected(true);
      selectionEvent.setValues(new ArrayList<>(List.of(pick)));
      selectionEvent.setEventSource("ApplyButton");
      selectionService.applySelection(rid, "RegionList", selectionEvent, principal, browser, "");

      // 2. the debounced onclick with the pending form values
      VSSubmitEvent submitEvent = ImmutableVSSubmitEvent.builder()
         .values(new InputValue[] {
            ImmutableInputValue.builder().assemblyName("CategoryPicker").value("Games").build(),
            ImmutableInputValue.builder().assemblyName("TopNSpinner").value(5).build() })
         .build();
      VSInputService inputService = new VSInputService(
         objectService, coreLifecycleService, viewsheetService, mock(VSObjectPropertyService.class),
         null, null, null, null, null, null);
      inputService.onClick(rid, "ApplyButton", "1", "1", submitEvent, "", principal, browser);

      String expected = "cat=Games;reg=" + region + ";n=5";
      assertEquals(expected, echoText(), "server-side onLoad result");
      assertEquals(expected, ((VSTextModel) lastModel(browser, "Echo")).getText());
      assertEquals("Games",
                   ((VSComboBoxModel) lastModel(browser, "CategoryPicker")).getSelectedObject());
      assertEquals("5", ((VSSpinnerModel) lastModel(browser, "TopNSpinner")).getCurrentLabel());
   }

   private String echoText() {
      return ((TextVSAssemblyInfo) vsResource.getRuntimeViewsheet().getViewsheet()
         .getAssembly("Echo").getVSAssemblyInfo()).getText();
   }

   /**
    * The model of the last add or refresh command sent for an assembly over both events.
    */
   private static VSObjectModel<?> lastModel(CommandDispatcher dispatcher, String name) {
      VSObjectModel<?> last = null;

      for(Invocation invocation : mockingDetails(dispatcher).getInvocations()) {
         if(!invocation.getMethod().getName().equals("sendCommand")) {
            continue;
         }

         for(Object arg : invocation.getArguments()) {
            VSObjectModel<?> model = null;

            if(arg instanceof AddVSObjectCommand add) {
               model = add.getModel();
            }
            else if(arg instanceof RefreshVSObjectCommand refresh) {
               model = refresh.getInfo();
            }

            if(model != null && name.equals(model.getAbsoluteName())) {
               last = model;
            }
         }
      }

      assertNotNull(last, "nothing was sent for " + name);
      return last;
   }

   private static CommandDispatcher dispatcher() {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);
      when(dispatcher.detach()).thenReturn(dispatcher);
      when(dispatcher.iterator()).thenAnswer(inv -> Collections.emptyIterator());
      return dispatcher;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired ViewsheetService viewsheetService;
   @Autowired VSObjectService objectService;
   @Autowired VSSelectionService selectionService;

   private Principal oldPrincipal;
}

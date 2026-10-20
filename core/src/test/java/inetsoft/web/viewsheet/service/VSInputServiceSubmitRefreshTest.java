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
import inetsoft.uql.asset.EmbeddedTableAssembly;
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
 * Bug #78878: a Submit button with "Refresh Dashboard after Submit" off applies the submitted
 * form values on the server, but the click must also send the client the assemblies onLoad
 * recomputed from them and the submitted inputs themselves. Each test clicks the Submit through
 * {@link VSInputService#onClick(String, String, String, String, VSSubmitEvent, String,
 * Principal, CommandDispatcher)} and checks the commands the click sends.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
class VSInputServiceSubmitRefreshTest {
   @BeforeEach
   void savePrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void restorePrincipal() {
      ThreadContext.setContextPrincipal(oldPrincipal);
   }

   @Test
   void refreshOffSendsOnLoadResult() throws Exception {
      CommandDispatcher dispatcher = submit(false);

      assertEquals(EXPECTED_ECHO, echo().getText(), "server-side onLoad result");
      assertEquals(EXPECTED_ECHO, ((VSTextModel) lastModel(dispatcher, "Echo")).getText());
   }

   @Test
   void refreshOffSendsSubmittedInputs() throws Exception {
      assertSubmittedInputsSent(submit(false));
   }

   @Test
   void refreshOnSendsOnLoadResultAndSubmittedInputs() throws Exception {
      CommandDispatcher dispatcher = submit(true);

      assertEquals(EXPECTED_ECHO, ((VSTextModel) lastModel(dispatcher, "Echo")).getText());
      assertSubmittedInputsSent(dispatcher);
   }

   @Test
   void refreshOffWritesSubmittedValueIntoBoundEmbeddedCell() throws Exception {
      submit(false);

      assertEquals("written", embeddedCell());
   }

   @Test
   void refreshOnWritesSubmittedValueIntoBoundEmbeddedCell() throws Exception {
      submit(true);

      assertEquals("written", embeddedCell());
   }

   private void assertSubmittedInputsSent(CommandDispatcher dispatcher) {
      assertEquals("Games",
                   ((VSComboBoxModel) lastModel(dispatcher, "CategoryPicker")).getSelectedObject());
      assertEquals(5.0, ((VSSpinnerModel) lastModel(dispatcher, "TopNSpinner"))
         .getValue().doubleValue());
      assertEquals("5", ((VSSpinnerModel) lastModel(dispatcher, "TopNSpinner")).getCurrentLabel());
      assertEquals(30.0, ((VSSliderModel) lastModel(dispatcher, "RateSlider"))
         .getValue().doubleValue());
      assertEquals("b", ((VSTextInputModel) lastModel(dispatcher, "NoteInput")).getText());
      assertEquals("B", ((VSRadioButtonModel) lastModel(dispatcher, "ModeRadio"))
         .getSelectedObject());
      assertArrayEquals(new Object[] { "X" },
                        ((VSCheckBoxModel) lastModel(dispatcher, "FlagsCheck")).getSelectedObjects());
   }

   /**
    * Add the form inputs, a Submit button and an onLoad-driven text to the viewsheet, then
    * click the Submit with pending values for every input.
    */
   private CommandDispatcher submit(boolean refresh) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();

      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, "CategoryPicker");
      setEmbeddedValues((ListInputVSAssemblyInfo) combo.getVSAssemblyInfo(),
                        "(All)", "Games", "Toys");
      ((ComboBoxVSAssemblyInfo) combo.getVSAssemblyInfo()).setSelectedObject("(All)");
      vs.addAssembly(combo);

      SpinnerVSAssembly spinner = new SpinnerVSAssembly(vs, "TopNSpinner");
      SpinnerVSAssemblyInfo spinnerInfo = (SpinnerVSAssemblyInfo) spinner.getVSAssemblyInfo();
      spinnerInfo.setMinValue("1");
      spinnerInfo.setMaxValue("20");
      spinnerInfo.setIncrementValue("1");
      spinnerInfo.setSelectedObject(1);
      spinnerInfo.setSubmitOnChange(false);
      vs.addAssembly(spinner);

      SliderVSAssembly slider = new SliderVSAssembly(vs, "RateSlider");
      SliderVSAssemblyInfo sliderInfo = (SliderVSAssemblyInfo) slider.getVSAssemblyInfo();
      sliderInfo.setMinValue("0");
      sliderInfo.setMaxValue("100");
      sliderInfo.setIncrementValue("10");
      sliderInfo.setSelectedObject(0);
      sliderInfo.setSubmitOnChange(false);
      vs.addAssembly(slider);

      TextInputVSAssembly note = new TextInputVSAssembly(vs, "NoteInput");
      ((TextInputVSAssemblyInfo) note.getVSAssemblyInfo()).setSelectedObject("a");
      ((TextInputVSAssemblyInfo) note.getVSAssemblyInfo()).setSubmitOnChange(false);
      vs.addAssembly(note);

      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "ModeRadio");
      setEmbeddedValues((ListInputVSAssemblyInfo) radio.getVSAssemblyInfo(), "A", "B");
      ((RadioButtonVSAssemblyInfo) radio.getVSAssemblyInfo()).setSelectedObject("A");
      vs.addAssembly(radio);

      CheckBoxVSAssembly check = new CheckBoxVSAssembly(vs, "FlagsCheck");
      setEmbeddedValues((ListInputVSAssemblyInfo) check.getVSAssemblyInfo(), "X", "Y");
      vs.addAssembly(check);

      // #75929: a submitted input bound to a cell of an embedded table in the base worksheet
      TextInputVSAssembly cellInput = new TextInputVSAssembly(vs, "CellInput");
      cellInput.setTableName(EMBEDDED_TABLE);
      cellInput.setColumnValue("name");
      cellInput.setRowValue("1");
      ((TextInputVSAssemblyInfo) cellInput.getVSAssemblyInfo()).setSelectedObject("before");
      ((TextInputVSAssemblyInfo) cellInput.getVSAssemblyInfo()).setSubmitOnChange(false);
      vs.addAssembly(cellInput);

      SubmitVSAssembly button = new SubmitVSAssembly(vs, "ApplyButton");
      ((SubmitVSAssemblyInfo) button.getVSAssemblyInfo()).setRefresh(refresh);
      vs.addAssembly(button);

      vs.addAssembly(new TextVSAssembly(vs, "Echo"));
      vs.getViewsheetInfo().setOnLoad(
         "Echo.text = 'cat=' + CategoryPicker.selectedObject + ';n=' + TopNSpinner.selectedObject" +
         " + ';rate=' + RateSlider.selectedObject + ';note=' + NoteInput.value" +
         " + ';mode=' + ModeRadio.selectedObject + ';flags=' + FlagsCheck.selectedObjects;");

      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      assertEquals("cat=(All);n=1;rate=0;note=a;mode=A;flags=", echo().getText());

      VSSubmitEvent event = ImmutableVSSubmitEvent.builder()
         .values(new InputValue[] {
            value("CategoryPicker", "Games"),
            value("TopNSpinner", 5),
            value("RateSlider", 30),
            value("NoteInput", "b"),
            value("ModeRadio", "B"),
            value("FlagsCheck", new Object[] { "X" }),
            value("CellInput", "written") })
         .build();

      VSInputService inputService = new VSInputService(
         objectService, coreLifecycleService, viewsheetService, mock(VSObjectPropertyService.class),
         null, null, null, null, null, null);
      CommandDispatcher dispatcher = dispatcher();
      inputService.onClick(vsResource.getRuntimeId(), "ApplyButton", "1", "1", event, "",
                           ThreadContext.getPrincipal(), dispatcher);

      return dispatcher;
   }

   private static void setEmbeddedValues(ListInputVSAssemblyInfo info, String... values) {
      info.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      info.setDataType(XSchema.STRING);
      ListData data = new ListData();
      data.setValues(values);
      data.setLabels(values);
      data.setDataType(XSchema.STRING);
      info.setListData(data);
      info.setValues(values);
      info.setLabels(values);
      info.setSubmitOnChange(false);
   }

   private static InputValue value(String name, Object value) {
      return ImmutableInputValue.builder().assemblyName(name).value(value).build();
   }

   private TextVSAssemblyInfo echo() {
      return (TextVSAssemblyInfo) vsResource.getRuntimeViewsheet().getViewsheet()
         .getAssembly("Echo").getVSAssemblyInfo();
   }

   private Object embeddedCell() {
      EmbeddedTableAssembly table = (EmbeddedTableAssembly) vsResource.getRuntimeViewsheet()
         .getViewsheet().getBaseWorksheet().getAssembly(EMBEDDED_TABLE);
      return table.getEmbeddedData().getObject(1, 0);
   }

   /**
    * The model of the last add or refresh command the click sent for an assembly.
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

      assertNotNull(last, "the submit click sent nothing for " + name);
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

   private static final String EMBEDDED_TABLE = "Query1";
   private static final String EXPECTED_ECHO =
      "cat=Games;n=5;rate=30;note=b;mode=B;flags=X";

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired ViewsheetService viewsheetService;
   @Autowired VSObjectService objectService;

   private Principal oldPrincipal;
}

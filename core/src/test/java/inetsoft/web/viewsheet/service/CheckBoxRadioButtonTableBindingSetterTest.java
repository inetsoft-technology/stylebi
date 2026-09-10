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

/*
 * Regression for bug #76551: unlike setTextInputPropertyDialogModel/setComboboxPropertyDialogModel/
 * setSliderPropertyDialogModel/setSpinnerPropertyDialogModel (bug #76478/#76530),
 * setCheckboxPropertyModel/setRadioButtonPropertyModel used to copy dataInputPaneModel.getTable()
 * straight into the assembly with no resolution at all, and persisted
 * dataInputPaneModel.isVariable() verbatim regardless of whether the table actually named a
 * variable. This let a garbage table name (or, for RadioButton, no table at all) reach the
 * assembly with variable:true honored unconditionally.
 *
 * VSInputTableBindingResolutionTest already covers resolveInputTableBinding/
 * resolvesToVariableBinding themselves, type-agnostically. This class instead calls through the
 * real setCheckboxPropertyModel/setRadioButtonPropertyModel methods (the interactive Composer
 * dialog-save path, which never touches the wiz-only AssemblyPropertyService/
 * requireVariableFlagAchievable safety net) to prove the fix actually reaches that layer, not
 * just the shared helper.
 */

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.RadioButtonVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.util.MessageException;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.model.vs.CheckboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.RadioButtonPropertyDialogModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.controller.VSTrapService;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class CheckBoxRadioButtonTableBindingSetterTest {
   // ── CheckBox ──────────────────────────────────────────────────────────────

   /** The exact reported repro shape: a garbage table name paired with {@code variable:true}. */
   @Test
   void setCheckboxPropertyModelThrowsOnAnUnresolvableTable() {
      Harness h = harnessForCheckbox(new Worksheet(), new CheckBoxVSAssemblyInfo());
      CheckboxPropertyDialogModel model = checkboxModel("TotallyUnknownTable", null, true);

      MessageException ex = assertThrows(MessageException.class, () ->
         h.service().setCheckboxPropertyModel("vs1", "Check1", model, "", principal(), null));

      assertTrue(ex.getMessage().contains("TotallyUnknownTable"), ex.getMessage());
   }

   /**
    * A real, resolvable table that is not a variable must not honor a stale/mismatched
    * {@code variable:true} request -- the setter must derive the flag from the binding, the same
    * way the four already-fixed types do, instead of trusting the client's raw boolean.
    */
   @Test
   void setCheckboxPropertyModelDerivesVariableFalseWhenTheResolvedTableIsARealNonVariableTable()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "Query1"));
      Harness h = harnessForCheckbox(ws, new CheckBoxVSAssemblyInfo());
      CheckboxPropertyDialogModel model = checkboxModel("Query1", null, true);

      h.service().setCheckboxPropertyModel("vs1", "Check1", model, "", principal(), null);

      CheckBoxVSAssemblyInfo persisted = (CheckBoxVSAssemblyInfo) h.capturePersistedInfo();
      assertFalse(persisted.isVariable(),
                  "a real, non-variable table must not honor a stale variable:true request");
      assertEquals("Query1", persisted.getTableName());
   }

   /** The mirror image: a table that genuinely resolves to a variable must still be honored. */
   @Test
   void setCheckboxPropertyModelPersistsVariableTrueWhenTheTableActuallyResolvesToAVariable()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "discountRate"));
      Harness h = harnessForCheckbox(ws, new CheckBoxVSAssemblyInfo());
      CheckboxPropertyDialogModel model = checkboxModel("$(discountRate)", null, true);

      h.service().setCheckboxPropertyModel("vs1", "Check1", model, "", principal(), null);

      CheckBoxVSAssemblyInfo persisted = (CheckBoxVSAssemblyInfo) h.capturePersistedInfo();
      assertTrue(persisted.isVariable());
      assertEquals("$(discountRate)", persisted.getTableName());
   }

   // ── RadioButton ───────────────────────────────────────────────────────────

   @Test
   void setRadioButtonPropertyModelThrowsOnAnUnresolvableTable() {
      Harness h = harnessForRadioButton(new Worksheet(), new RadioButtonVSAssemblyInfo());
      RadioButtonPropertyDialogModel model = radioButtonModel("TotallyUnknownTable", null, true);

      MessageException ex = assertThrows(MessageException.class, () ->
         h.service().setRadioButtonPropertyModel("vs1", "Radio1", model, "", principal(), null));

      assertTrue(ex.getMessage().contains("TotallyUnknownTable"), ex.getMessage());
   }

   /**
    * The exact reported worst case (bug #76551): RadioButton had zero gating at all, unlike
    * CheckBox's non-blank-table check -- no table/columnValue whatsoever still honored
    * {@code variable:true} unconditionally before this fix.
    */
   @Test
   void setRadioButtonPropertyModelDerivesVariableFalseWhenNoTableIsBoundAtAll() throws Exception {
      Harness h = harnessForRadioButton(new Worksheet(), new RadioButtonVSAssemblyInfo());
      RadioButtonPropertyDialogModel model = radioButtonModel(null, null, true);

      h.service().setRadioButtonPropertyModel("vs1", "Radio1", model, "", principal(), null);

      RadioButtonVSAssemblyInfo persisted = (RadioButtonVSAssemblyInfo) h.capturePersistedInfo();
      assertFalse(persisted.isVariable(),
                  "no table at all cannot possibly resolve to a variable -- must not persist true");
   }

   @Test
   void setRadioButtonPropertyModelPersistsVariableTrueWhenTheTableActuallyResolvesToAVariable()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "discountRate"));
      Harness h = harnessForRadioButton(ws, new RadioButtonVSAssemblyInfo());
      RadioButtonPropertyDialogModel model = radioButtonModel("$(discountRate)", null, true);

      h.service().setRadioButtonPropertyModel("vs1", "Radio1", model, "", principal(), null);

      RadioButtonVSAssemblyInfo persisted = (RadioButtonVSAssemblyInfo) h.capturePersistedInfo();
      assertTrue(persisted.isVariable());
      assertEquals("$(discountRate)", persisted.getTableName());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private record Harness(VSInputService service, VSObjectPropertyService propertyService) {
      /** The clone of the seed info that the setter actually wrote its changes onto. */
      VSAssemblyInfo capturePersistedInfo() throws Exception {
         ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
         verify(propertyService).editObjectProperty(
            any(), captor.capture(), any(), any(), any(), any(), any());
         return captor.getValue();
      }
   }

   private static CheckboxPropertyDialogModel checkboxModel(String table, String columnValue,
                                                             boolean variable)
   {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      model.getDataInputPaneModel().setTable(table);
      model.getDataInputPaneModel().setColumnValue(columnValue);
      model.getDataInputPaneModel().setVariable(variable);
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());
      return model;
   }

   private static RadioButtonPropertyDialogModel radioButtonModel(String table, String columnValue,
                                                                   boolean variable)
   {
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      model.getDataInputPaneModel().setTable(table);
      model.getDataInputPaneModel().setColumnValue(columnValue);
      model.getDataInputPaneModel().setVariable(variable);
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());
      return model;
   }

   private static Harness harnessForCheckbox(Worksheet ws, CheckBoxVSAssemblyInfo seed) {
      CheckBoxVSAssembly assembly = mock(CheckBoxVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(seed);
      return harness(ws, assembly);
   }

   private static Harness harnessForRadioButton(Worksheet ws, RadioButtonVSAssemblyInfo seed) {
      RadioButtonVSAssembly assembly = mock(RadioButtonVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(seed);
      return harness(ws, assembly);
   }

   private static Harness harness(Worksheet ws, VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getBaseWorksheet()).thenReturn(ws);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      // isKnownVariableName() merges this in unconditionally alongside the worksheet's own
      // variables; an unstubbed mock array getter defaults to null, which NPEs the merge.
      when(vs.getAllVariables()).thenReturn(new inetsoft.uql.schema.UserVariable[0]);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);

      try {
         when(viewsheetService.getViewsheet(anyString(), any())).thenReturn(rvs);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSObjectPropertyService propertyService = mock(VSObjectPropertyService.class);

      VSInputService service = new VSInputService(
         mock(VSObjectService.class),
         mock(CoreLifecycleService.class),
         viewsheetService,
         propertyService,
         mock(VSDialogService.class),
         mock(VSTrapService.class),
         mock(DataRefModelFactoryService.class),
         mock(VSAssemblyInfoHandler.class),
         mock(VSColumnHandler.class));

      return new Harness(service, propertyService);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}

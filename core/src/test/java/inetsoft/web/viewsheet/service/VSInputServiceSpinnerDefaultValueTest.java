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
 * Regression for Bug #79150 (found while investigating #79093).
 *
 * VSInputService.setSpinnerPropertyDialogModel parsed a Spinner's raw
 * dataInputPaneModel.defaultValue with Double.valueOf(...) but discarded the return value:
 *
 *    double dvalue = 0;
 *    try {
 *       Double.valueOf(dataInputPaneModel.getDefaultValue());
 *    }
 *    catch(Exception e) {
 *       //ignore it.
 *    }
 *    spinnerAssemblyInfo.setValue(dvalue);
 *
 * dvalue never left its 0 initializer, so ANY non-null defaultValue -- a valid number or not --
 * unconditionally reset the spinner's live value to 0.0, regardless of what was actually
 * submitted. A spinner at 42, written with defaultValue:"77", ended up at 0 -- not 42, not 77.
 *
 * The fix assigns the parsed value and only calls setValue on success, so a parse failure leaves
 * the spinner's current value alone instead of zeroing it (matching how a caught parse exception
 * is handled elsewhere in this file, e.g. convertWallClockDate's own catch returns the value
 * unchanged rather than substituting a sentinel).
 *
 * Behavioral guarantees covered:
 *
 * [G1] A valid numeric defaultValue is applied, as the real parsed number -- not 0.
 * [G2] A non-numeric defaultValue never calls setValue at all (the spinner's current value is
 *      left alone, not zeroed).
 * [G3] No defaultValue at all (null) never calls setValue either, as before.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LabelInfo;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.web.composer.model.vs.SpinnerPropertyDialogModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class VSInputServiceSpinnerDefaultValueTest {
   // [G1]
   @Test
   void aValidNumericDefaultValueIsAppliedAsTheRealParsedNumber() throws Exception {
      SpinnerVSAssemblyInfo info = writeDefaultValue("77");

      verify(info).setValue(77.0);
   }

   // [G2]
   @Test
   void aNonNumericDefaultValueNeverCallsSetValue() throws Exception {
      SpinnerVSAssemblyInfo info = writeDefaultValue("not-a-number");

      verify(info, never()).setValue(any());
   }

   // [G3]
   @Test
   void noDefaultValueNeverCallsSetValueEither() throws Exception {
      SpinnerVSAssemblyInfo info = writeDefaultValue(null);

      verify(info, never()).setValue(any());
   }

   /**
    * Drives the real {@code setSpinnerPropertyDialogModel} with a raw
    * {@code dataInputPaneModel.defaultValue} of {@code defaultValue}, and returns the mocked
    * {@link SpinnerVSAssemblyInfo} that the method actually operates on (its {@code clone()} is
    * stubbed to return itself, so {@code Tool.clone(...)} hands the setter the same mock
    * {@code verify(...)} checks below) -- a real {@code SpinnerVSAssemblyInfo} cannot be
    * instantiated outside a running Spring context (its constructor chain reads
    * {@code SreeEnv.getProperty(...)}), matching the existing {@code VSInputServiceSliderSnapTest}
    * / {@code VSInputServiceTextInputValidationTest} convention of mocking the assembly info.
    */
   private static SpinnerVSAssemblyInfo writeDefaultValue(String defaultValue) throws Exception {
      SpinnerVSAssemblyInfo assemblyInfo = mock(SpinnerVSAssemblyInfo.class);
      when(assemblyInfo.clone()).thenReturn(assemblyInfo);
      when(assemblyInfo.getLabelInfo()).thenReturn(new LabelInfo());

      SpinnerVSAssembly assembly = mock(SpinnerVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(assemblyInfo);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Spinner1")).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      Principal principal = mock(Principal.class);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet("vs1", principal)).thenReturn(rvs);

      VSObjectPropertyService vsObjectPropertyService = mock(VSObjectPropertyService.class);

      VSInputService service = new VSInputService(null, null, viewsheetService,
         vsObjectPropertyService, mock(VSDialogService.class), null, null, null, null, null);

      SpinnerPropertyDialogModel model = new SpinnerPropertyDialogModel();
      model.getDataInputPaneModel().setDefaultValue(defaultValue);
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());

      service.setSpinnerPropertyDialogModel("vs1", "Spinner1", model, "/", principal,
         mock(CommandDispatcher.class));

      return assemblyInfo;
   }
}

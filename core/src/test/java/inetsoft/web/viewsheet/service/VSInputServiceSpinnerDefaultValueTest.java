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
 *
 * Regression for Bug #79309: the raw defaultValue was stored unclamped (500, -5, 99 with max 50)
 * or non-finite (NaN, Infinity) although the min/max clamp of the selected object had just run.
 *
 * [G4] A finite defaultValue is clamped into [min, max] before it is stored.
 * [G5] A non-finite defaultValue (NaN, Infinity) is ignored exactly like unparsable text.
 *
 * Regression for Bug #79345: a script or variable bound written in the same call has no runtime
 * value yet, so getMin()/getMax() report the 0/100 fallback and the G4 clamp (and the clamp of the
 * existing value) pulled a value inside the bound down to 100 / up to 0.
 *
 * [G6] A side whose bound is dynamic (=script or $(var)) is not clamped, for the new default and
 *      for the existing value; a static side still is.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.SpinnerVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LabelInfo;
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;
import inetsoft.web.composer.model.vs.SpinnerPropertyDialogModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.test.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// VSUtil's static initializer needs the Spring application context
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
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

   // [G4]
   @Test
   void aDefaultValueAboveMaxIsClampedToMax() throws Exception {
      verify(writeDefaultValue("500", 0, 100)).setValue(100.0);
   }

   // [G4]
   @Test
   void aDefaultValueBelowMinIsClampedToMin() throws Exception {
      verify(writeDefaultValue("-5", 0, 100)).setValue(0.0);
   }

   // [G4]
   @Test
   void aDefaultValueAboveACustomMaxIsClampedToThatMax() throws Exception {
      verify(writeDefaultValue("99", 0, 50)).setValue(50.0);
   }

   // [G4]
   @Test
   void anInRangeDefaultValueIsStoredUnchanged() throws Exception {
      verify(writeDefaultValue("99", 0, 100)).setValue(99.0);
   }

   // [G5]
   @Test
   void nanAndInfiniteDefaultValuesAreIgnored() throws Exception {
      for(String text : new String[] { "NaN", "Infinity", "-Infinity" }) {
         verify(writeDefaultValue(text, 0, 100), never()).setValue(any());
      }
   }

   // [G6]
   @Test
   void aScriptMaxDoesNotClampTheDefaultValueToTheFallbackMax() throws Exception {
      verify(writeDefaultValue("150", "0", "=200", null)).setValue(150.0);
   }

   // [G6] a dynamic bound is never clamped against, whether or not an earlier call evaluated it
   @Test
   void aDefaultValueBeyondAScriptMaxIsStoredUnclamped() throws Exception {
      verify(writeDefaultValue("500", "0", "=200", null)).setValue(500.0);
   }

   // [G6]
   @Test
   void aScriptMinDoesNotClampTheDefaultValueToTheFallbackMin() throws Exception {
      verify(writeDefaultValue("-20", "=-50", "100", null)).setValue(-20.0);
   }

   // [G6]
   @Test
   void bothScriptBoundsLeaveTheDefaultValueAlone() throws Exception {
      verify(writeDefaultValue("150", "=-50", "=200", null)).setValue(150.0);
      verify(writeDefaultValue("-20", "=-50", "=200", null)).setValue(-20.0);
   }

   // [G6]
   @Test
   void aVariableBoundLeavesTheDefaultValueAlone() throws Exception {
      verify(writeDefaultValue("150", "0", "$(maxVar)", null)).setValue(150.0);
      verify(writeDefaultValue("-20", "$(minVar)", "100", null)).setValue(-20.0);
   }

   // [G6]
   @Test
   void theStaticSideStillClampsWhenTheOtherSideIsDynamic() throws Exception {
      // min is a script, max is static 50: 99 is clamped to 50, -20 is kept
      verify(writeDefaultValue("99", "=-50", "50", null)).setValue(50.0);
      verify(writeDefaultValue("-20", "=-50", "50", null)).setValue(-20.0);
      // max is a script, min is static 0: -5 is clamped to 0, 150 is kept
      verify(writeDefaultValue("-5", "0", "=200", null)).setValue(0.0);
      verify(writeDefaultValue("150", "0", "=200", null)).setValue(150.0);
   }

   // [G6]
   @Test
   void aNonFiniteDefaultValueIsStillIgnoredUnderDynamicBounds() throws Exception {
      verify(writeDefaultValue("NaN", "=-50", "=200", null), never()).setValue(any());
   }

   // [G6]
   @Test
   void aDynamicSideNeverResetsTheExistingValue() throws Exception {
      SpinnerVSAssemblyInfo info = writeDefaultValue(null, "0", "=300", 150.0);
      verify(info, never()).setSelectedObject(any());

      info = writeDefaultValue(null, "=-60", "100", -20.0);
      verify(info, never()).setSelectedObject(any());
   }

   // [G6]
   @Test
   void aStaticSideStillResetsTheExistingValue() throws Exception {
      verify(writeDefaultValue(null, "0", "100", 150.0)).setSelectedObject(100.0);
      verify(writeDefaultValue(null, "0", "100", -20.0)).setSelectedObject(0.0);
      // the dynamic min leaves -20 alone but the static max still resets 150
      verify(writeDefaultValue(null, "=-50", "100", 150.0)).setSelectedObject(100.0);
   }

   private static SpinnerVSAssemblyInfo writeDefaultValue(String defaultValue) throws Exception {
      return writeDefaultValue(defaultValue, 0, 100);
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
   private static SpinnerVSAssemblyInfo writeDefaultValue(String defaultValue, double min,
                                                          double max)
      throws Exception
   {
      return writeDefaultValue(defaultValue, min, max, null, null, null);
   }

   /**
    * Models a bound written in the same call: a dynamic bound string ({@code =script} or
    * {@code $(var)}) has no runtime value yet, so {@code getMin()/getMax()} return the 0/100
    * fallback while {@code getMinValue()/getMaxValue()} return the raw bound string.
    *
    * @param existingValue the value already stored in the spinner, or null for none.
    */
   private static SpinnerVSAssemblyInfo writeDefaultValue(String defaultValue, String minValue,
                                                          String maxValue, Double existingValue)
      throws Exception
   {
      double min = minValue.startsWith("=") || minValue.startsWith("$(") ? 0 :
         Double.parseDouble(minValue);
      double max = maxValue.startsWith("=") || maxValue.startsWith("$(") ? 100 :
         Double.parseDouble(maxValue);
      return writeDefaultValue(defaultValue, min, max, minValue, maxValue, existingValue);
   }

   private static SpinnerVSAssemblyInfo writeDefaultValue(String defaultValue, double min,
                                                          double max, String minValue,
                                                          String maxValue, Double existingValue)
      throws Exception
   {
      SpinnerVSAssemblyInfo assemblyInfo = mock(SpinnerVSAssemblyInfo.class);
      when(assemblyInfo.getMin()).thenReturn(min);
      when(assemblyInfo.getMax()).thenReturn(max);
      when(assemblyInfo.getMinValue()).thenReturn(minValue);
      when(assemblyInfo.getMaxValue()).thenReturn(maxValue);
      when(assemblyInfo.getSelectedObject()).thenReturn(existingValue);
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

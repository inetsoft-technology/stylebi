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
 * Regression for Bug #80072 (same defect as #79345, on the Slider setter).
 *
 * VSInputService.setSliderPropertyDialogModel clamped the slider's existing value against
 * getMin()/getMax() right after writing the bounds. A script or variable bound ("=400", "$(v)")
 * has no runtime value yet, so getMin()/getMax() report the 0/100 fallback and a held value of 250
 * was reset to 100.
 *
 * [S1] A dynamic side never resets the existing value; a static side still does.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.LabelInfo;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.web.composer.model.vs.SliderPropertyDialogModel;
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
class VSInputServiceSliderDynamicBoundTest {
   // [S1]
   @Test
   void aScriptMaxKeepsAHeldValueAboveTheFallbackMax() throws Exception {
      verify(write("0", "=400", 250.0), never()).setSelectedObject(any());
   }

   // [S1]
   @Test
   void scriptBoundsOnBothSidesKeepTheHeldValue() throws Exception {
      verify(write("=0", "=300", 150.0), never()).setSelectedObject(any());
   }

   // [S1]
   @Test
   void aScriptMinKeepsANegativeHeldValueBelowTheFallbackMin() throws Exception {
      verify(write("=-50", "100", -20.0), never()).setSelectedObject(any());
   }

   // [S1]
   @Test
   void aVariableBoundKeepsTheHeldValue() throws Exception {
      verify(write("0", "$(maxVar)", 250.0), never()).setSelectedObject(any());
      verify(write("$(minVar)", "100", -20.0), never()).setSelectedObject(any());
   }

   // [S1] control: a static max that holds the value keeps it
   @Test
   void aStaticMaxAboveTheHeldValueKeepsIt() throws Exception {
      verify(write("0", "500", 350.0), never()).setSelectedObject(any());
   }

   // [S1] existing behavior preserved: a static bound still clamps
   @Test
   void aStaticMaxBelowTheHeldValueStillClamps() throws Exception {
      verify(write("0", "100", 150.0)).setSelectedObject(100.0);
   }

   // [S1]
   @Test
   void aStaticMinAboveTheHeldValueStillClamps() throws Exception {
      verify(write("0", "100", -20.0)).setSelectedObject(0.0);
   }

   // [S1] the static side still clamps when the other side is dynamic
   @Test
   void theStaticSideStillClampsWhenTheOtherSideIsDynamic() throws Exception {
      verify(write("=-50", "100", 150.0)).setSelectedObject(100.0);
      verify(write("0", "=300", -5.0)).setSelectedObject(0.0);
   }

   /**
    * A dynamic bound string has no runtime value yet, so getMin()/getMax() return the 0/100
    * fallback while getMinValue()/getMaxValue() return the raw bound string.
    */
   private static SliderVSAssemblyInfo write(String minValue, String maxValue, Double held)
      throws Exception
   {
      double min = isDynamic(minValue) ? 0 : Double.parseDouble(minValue);
      double max = isDynamic(maxValue) ? 100 : Double.parseDouble(maxValue);

      SliderVSAssemblyInfo assemblyInfo = mock(SliderVSAssemblyInfo.class);
      when(assemblyInfo.getMin()).thenReturn(min);
      when(assemblyInfo.getMax()).thenReturn(max);
      when(assemblyInfo.getMinValue()).thenReturn(minValue);
      when(assemblyInfo.getMaxValue()).thenReturn(maxValue);
      when(assemblyInfo.getSelectedObject()).thenReturn(held);
      when(assemblyInfo.clone()).thenReturn(assemblyInfo);
      when(assemblyInfo.getLabelInfo()).thenReturn(new LabelInfo());

      SliderVSAssembly assembly = mock(SliderVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(assemblyInfo);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Slider1")).thenReturn(assembly);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      Principal principal = mock(Principal.class);
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getViewsheet("vs1", principal)).thenReturn(rvs);

      VSInputService service = new VSInputService(null, null, viewsheetService,
         mock(VSObjectPropertyService.class), mock(VSDialogService.class), null, null, null,
         null, null);

      SliderPropertyDialogModel model = new SliderPropertyDialogModel();
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());

      service.setSliderPropertyDialogModel("vs1", "Slider1", model, "/", principal,
         mock(CommandDispatcher.class));

      return assemblyInfo;
   }

   private static boolean isDynamic(String bound) {
      return bound.startsWith("=") || bound.startsWith("$(");
   }
}

/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.SliderVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSSliderModel extends VSNumericRangeModel<SliderVSAssembly> {
   public VSSliderModel(SliderVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      SliderVSAssemblyInfo assemblyInfo =
        (SliderVSAssemblyInfo) assembly.getVSAssemblyInfo();

      labels = assemblyInfo.getLabels();
      minVisible = assemblyInfo.isMinVisible();
      maxVisible = assemblyInfo.isMaxVisible();
      ticksVisible = assemblyInfo.isTickVisible();
      labelVisible = assemblyInfo.isLabelVisible();
      currentVisible = assemblyInfo.isCurrentVisible();
      snap = assemblyInfo.isSnap();

      // dark mode: the tick/value labels use the object foreground (default black) -> dark-on-dark.
      // Lift it to the light neutral unless the user/CSS set a foreground. Server-side so export agrees.
      String darkForeground = VSObjectChromeDefaults.textForegroundCss();

      if(darkForeground != null) {
         VSCompositeFormat objFmt = assemblyInfo.getFormat();

         if(objFmt == null ||
            !objFmt.getUserDefinedFormat().isForegroundValueDefined() &&
            !objFmt.getCSSFormat().isForegroundValueDefined())
         {
            getObjectFormat().setForeground(darkForeground);
         }
      }
   }

   // Get the tick labels
   public String[] getLabels() {
      return labels;
   }

   public boolean isMinVisible() {
      return minVisible;
   }

   public boolean isMaxVisible() {
      return maxVisible;
   }

   public boolean isTicksVisible() {
      return ticksVisible;
   }

   public boolean isLabelVisible() {
      return labelVisible;
   }

   public boolean isCurrentVisible() {
      return currentVisible;
   }

   public boolean isSnap() {
      return snap;
   }

   private String[] labels;
   private boolean minVisible;
   private boolean maxVisible;
   private boolean ticksVisible;
   private boolean labelVisible;
   private boolean currentVisible;
   private boolean snap;

   @Component
   public static final class VSSliderModelFactory
      extends VSObjectModelFactory<SliderVSAssembly, VSSliderModel>
   {
      public VSSliderModelFactory() {
         super(SliderVSAssembly.class);
      }

      @Override
      public VSSliderModel createModel(SliderVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSSliderModel(assembly, rvs);
      }
   }
}

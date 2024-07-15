/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

/**
 * SliderVSAssembly represents one slider assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SliderVSAssembly extends NumericRangeVSAssembly {
   /**
    * Constructor.
    */
   public SliderVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SliderVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new SliderVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.SLIDER_ASSET;
   }

   /**
    * If the tick is visible at runtime.
    * @return visibility of ticks.
    */
   public boolean isTickVisible() {
      return getSliderInfo().isTickVisible();
   }

   /**
    * If the tick is visible at design time.
    * @return visibility of ticks.
    */
   public boolean getTickVisibleValue() {
      return getSliderInfo().getTickVisibleValue();
   }

   /**
    * Set the visibility of ticks at design time.
    * @param visible the visibility of ticks.
    */
   public void setTickVisibleValue(boolean visible) {
      getSliderInfo().setTickVisibleValue(visible);
   }

   /**
    * If the tick label is visible at runtime.
    * @return visibility of min value.
    */
   public boolean isLabelVisible() {
      return getSliderInfo().isLabelVisible();
   }

   /**
    * If the tick label is visible at design time.
    * @return visibility of min value.
    */
   public boolean getLabelVisibleValue() {
      return getSliderInfo().getLabelVisibleValue();
   }

   /**
    * Set the visibility of tick labels at design time.
    * @param visible the visibility of tick labels.
    */
   public void setLabelVisibleValue(boolean visible) {
      getSliderInfo().setLabelVisibleValue(visible);
   }

   /**
    * If the max value is visible at runtime.
    * @return visibility of max value.
    */
   public boolean isMaxVisible() {
      return getSliderInfo().isMaxVisible();
   }

   /**
    * If the max value is visible at design time.
    * @return visibility of max value.
    */
   public boolean getMaxVisibleValue() {
      return getSliderInfo().getMaxVisibleValue();
   }

   /**
    * Set the visibility of max value at design time.
    * @param visible the visibility of max value.
    */
   public void setMaxVisibleValue(boolean visible) {
      getSliderInfo().setMaxVisibleValue(visible);
   }

   /**
    * If the min value is visible at runtime.
    * @return visibility of min value.
    */
   public boolean isMinVisible() {
      return getSliderInfo().isMinVisible();
   }

   /**
    * If the min value is visible at design time.
    * @return visibility of min value.
    */
   public boolean getMinVisibleValue() {
      return getSliderInfo().getMinVisibleValue();
   }

   /**
    * Set the visibility of min value at design time.
    * @param visible the visibility of min value.
    */
   public void setMinVisibleValue(boolean visible) {
      getSliderInfo().setMinVisibleValue(visible);
   }

   /**
    * If the current value is visible at runtime.
    * @return visibility of current value.
    */
   public boolean isCurrentVisible() {
      return getSliderInfo().isCurrentVisible();
   }

   /**
    * If the current value is visible at design time.
    * @return visibility of current value.
    */
   public boolean getCurrentVisibleValue() {
      return getSliderInfo().getCurrentVisibleValue();
   }

   /**
    * Set the visibility of current value at design time.
    * @param visible the visibility of current value.
    */
   public void setCurrentVisibleValue(boolean visible) {
      getSliderInfo().setCurrentVisibleValue(visible);
   }

   /**
    * Get slider assembly info.
    * @return the slider assembly info.
    */
   protected SliderVSAssemblyInfo getSliderInfo() {
      return (SliderVSAssemblyInfo) getInfo();
   }
}

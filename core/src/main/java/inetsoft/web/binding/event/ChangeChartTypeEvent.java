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
package inetsoft.web.binding.event;

/**
 * Class that encapsulates the parameters for change chart type event.
 *
 * @since 12.3
 */
public class ChangeChartTypeEvent {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the chart type.
    * @return chart type.
    */
   public int getType() {
      return type;
   }

   /**
    * Set the chart type.
    * @param type the chart type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Whether if multi chart.
    * @return true if multi chart, otherwise false.
    */
   public boolean isMulti() {
      return multi;
   }

   /**
    * Set if multi chart.
    * @param multi the multi chart.
    */
   public void setMulti(boolean multi) {
      this.multi = multi;
   }

   /**
    * Whether to stack all measures instead of display them as separate bars.
    * @return true if stack all measures, otherwise false.
    */
   public boolean isStackMeasures() {
      return stackMeasures;
   }

   /**
    * Set whether to stack all measures instead of display them as separate bars.
    * @param stackMeasures the specified value of whether to stack measures.
    */
   public void setStackMeasures(boolean stackMeasures) {
      this.stackMeasures = stackMeasures;
   }

   /**
    * Whether if separate chart.
    * @return true if separate chart, otherwise false.
    */
   public boolean isSeparate() {
      return separate;
   }

   /**
    * Set if separate chart.
    * @param separate the separate chart.
    */
   public void setSeparate(boolean separate) {
      this.separate = separate;
   }

   /**
    * Get aggregate ref name.
    * @return the aggregate ref name.
    */
   public String getRef() {
      return ref;
   }

   /**
    * Set aggregate ref name.
    * @param ref the aggregate ref name.
    */
   public void setRef(String ref) {
      this.ref = ref;
   }

   private String name;
   private int type;
   private boolean multi;
   private boolean stackMeasures;
   private boolean separate;
   private String ref;
}

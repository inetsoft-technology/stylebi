/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.event;

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.OriginalDescriptor;
import org.springframework.lang.Nullable;

/**
 * Class that encapsulates the parameters for change chart ref event.
 *
 * @since 12.3
 */
public class ChangeChartRefEvent {
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

   public String getFieldType() {
      return fieldType;
   }

   public void setFieldType(String fieldType) {
      this.fieldType = fieldType;
   }

   /**
    * Get the OriginalDescriptor of current edit chartref.
    */
   public OriginalDescriptor getRefOriginalDescriptor() {
      return this.refOriginalDescriptor;
   }

   /**
    * Set the OriginalDescriptor of current edit chartref.
    */
   public void getRefOriginalDescriptor(OriginalDescriptor odesc) {
      this.refOriginalDescriptor = odesc;
   }

   /**
    * Get chart binding model.
    * @return the chart binding model.
    */
   public ChartBindingModel getModel() {
      return model;
   }

   /**
    * Set chart binding model.
    * @param model the chart binding model.
    */
   public void setModel(ChartBindingModel model) {
      this.model = model;
   }

   private String name;

   @Nullable
   private String fieldType;
   private OriginalDescriptor refOriginalDescriptor;
   private ChartBindingModel model;
}

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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.ContentObject;

import java.util.List;

/**
 * Interface for all classes that implement a target action. A target action
 * performs any arbitrary action when the conditions of a target evaluate to
 * Implementers should not implement this class directly.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public interface VSChartRef extends ChartRef, ContentObject {
   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   void renameDepended(String oname, String nname, Viewsheet vs);

   /**
    * Get the dynamic property values.
    * @return the dynamic values.
    */
   List<DynamicValue> getDynamicValues();

   /**
    * Get the hyperlink dynamic property values.
    * @return the dynamic values.
    */
   List<DynamicValue> getHyperlinkDynamicValues();

   /**
    * Check if the column is a script.
    */
   boolean isScript();

   /**
    * Check if the column is a variable.
    */
   boolean isVariable();

   /**
    * Get runtime axis descriptor from this ref.
    * @return the axis descriptor.
    */
   AxisDescriptor getRTAxisDescriptor();

   /**
    * Set the runtime axis descriptor into this ref.
    * @param desc the axis descriptor.
    */
   void setRTAxisDescriptor(AxisDescriptor desc);

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   List<DataRef> update(Viewsheet vs, ColumnSelection columns);
}

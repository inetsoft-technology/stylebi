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

import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.viewsheet.VSDataRef;

/**
 * ChartRef, as a DataRef, it also stores the layout and format information
 * about axis, label, etc.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public interface ChartRef extends AssetObject, VSDataRef {
   /**
    * Get axis descriptor from this ref.
    */
   public AxisDescriptor getAxisDescriptor();

   /**
    * Set the axis descriptor into this ref.
    */
   public void setAxisDescriptor(AxisDescriptor desc);

   /**
    * Set the Axis CSS type.
    */
   public void setAxisCSS(String css);

   /**
    * Get the data format for this measure.
    */
   public CompositeTextFormat getTextFormat();

   /**
    * Set the data format for this measure.
    */
   public void setTextFormat(CompositeTextFormat fmt);

   /**
    * Check this ref is treat as dimension or measure.
    */
   public boolean isMeasure();

   /**
    * Check if data ref is visible.  Used mainly for drilling operations for
    * ChartRefs
    * @return Whether or not the ref should be included in output
    */
   public boolean isDrillVisible();

   /**
    * Set data ref visibility.  Used mainly for drilling operations for
    * ChartRefs
    * @param isVisible The visibility of the data ref.
    */
   public void setDrillVisible(boolean isVisible);
}

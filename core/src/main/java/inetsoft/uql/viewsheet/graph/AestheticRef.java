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

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;

/**
 * Interface AestheticRef, it stores aesthetic attributes and binding
 * information, it for all classes that implement a target action. A target
 * action performs any arbitrary action when the conditions of a target evaluate
 * to implementer should not implement this class directly.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public interface AestheticRef extends AssetObject, VSDataRef {
   /**
    * Get the legend frame wrapper.
    */
   public VisualFrameWrapper getVisualFrameWrapper();

   /**
    * Set the legend frame wrapper.
    */
   public void setVisualFrameWrapper(VisualFrameWrapper wrapper);

   /**
    * Get the legend frame.
    */
   public VisualFrame getVisualFrame();

   /**
    * Set the legend frame.
    */
   public void setVisualFrame(VisualFrame frame);

   /**
    * Get legend descriptor for the corresponding legend.
    */
   public LegendDescriptor getLegendDescriptor();

   /**
    * Set legend descriptor for the corresponding legend.
    */
   public void setLegendDescriptor(LegendDescriptor desc);

   /**
    * Check if the aesthetic frame has been changed from default.
    */
   public boolean isChanged();

   /**
    * Check if is measure.
    */
   public boolean isMeasure();

   /**
    * Get the dataRef.
    */
   public DataRef getDataRef();

   /**
    * Set the dataRef.
    */
   public void setDataRef(DataRef ref);

   /**
    * Get the runtime dataRef.
    */
   public DataRef getRTDataRef();

   /**
    * Whether AestheticRef just is applied during runtime;
    */
   public default boolean isRuntime() {
      return false;
   }
}

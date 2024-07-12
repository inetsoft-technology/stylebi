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

import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;

/**
 * Interface for tree chart.
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public interface RelationChartInfo extends MergedChartInfo {
   /**
    * Get the source field.
    */
   public ChartRef getSourceField();

   /**
    * Get the target field.
    */
   public ChartRef getTargetField();

   /**
    * Get the runtime source field.
    */
   public ChartRef getRTSourceField();

   /**
    * Get the runtime target field.
    */
   public ChartRef getRTTargetField();

   /**
    * Set the source field.
    */
   public void setSourceField(ChartRef ref);

   /**
    * Set the target field.
    */
   public void setTargetField(ChartRef ref);

   /**
    * Get the color field.
    */
   public AestheticRef getNodeColorField();

   /**
    * Set the color field.
    */
   public void setNodeColorField(AestheticRef field);

   /**
    * Get the size field.
    */
   public AestheticRef getNodeSizeField();

   /**
    * Set the size field.
    */
   public void setNodeSizeField(AestheticRef field);

   default DataRef getRTNodeColorField() {
      return getNodeColorField() != null ? getNodeColorField().getRTDataRef() : null;
   }

   default DataRef getRTNodeSizeField() {
      return getNodeSizeField() != null ? getNodeSizeField().getRTDataRef() : null;
   }

   public ColorFrameWrapper getNodeColorFrameWrapper();

   public void setNodeColorFrameWrapper(ColorFrameWrapper color);

   public SizeFrameWrapper getNodeSizeFrameWrapper();

   public void setNodeSizeFrameWrapper(SizeFrameWrapper size);
}

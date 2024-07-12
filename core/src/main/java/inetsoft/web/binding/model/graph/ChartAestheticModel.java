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
package inetsoft.web.binding.model.graph;

import inetsoft.web.binding.model.graph.aesthetic.*;

/**
 * Interface for classes that can be bound to aesthetic fields.
 *
 * @version 12.2
 * @author InetSoft Technology Corp.
 */
public interface ChartAestheticModel {
   /**
    * Get the chart type on the model.
    * @return the chart type.
    */
   public int getChartType();
   /**
    * Set the chart type on the model.
    * @param ctype the chart type to be set.
    */
   public void setChartType(int ctype);
   /**
    * Get the runtime chart type on the ref.
    * @return the chart type.
    */
   public int getRTChartType();
   /**
    * Set the runtime chart type on the ref.
    * @param ctype the runtime chart type to be set.
    */
   public void setRTChartType(int ctype);
   /**
    * Get color field model.
    */
   public AestheticInfo getColorField();
   /**
    * Set color field model.
    */
   public void setColorField(AestheticInfo colorField);
   /**
    * Get shape field model.
    */
   public AestheticInfo getShapeField();
   /**
    * Set shape field model.
    */
   public void setShapeField(AestheticInfo shapeField);
   /**
    * Get size field model.
    */
   public AestheticInfo getSizeField();
   /**
    * Set size field model.
    */
   public void setSizeField(AestheticInfo sizeField);
   /**
    * Get text field model.
    */
   public AestheticInfo getTextField();
   /**
    * Set text field model.
    */
   public void setTextField(AestheticInfo textField);
   /**
    * Set the color frame model.
    */
   public void setColorFrame(ColorFrameModel frame);
   /**
    * Get the color frame model.
    */
   public ColorFrameModel getColorFrame();
   /**
    * Set the shape frame model.
    */
   public void setShapeFrame(ShapeFrameModel frame);
   /**
    * Get the shape frame model.
    */
   public ShapeFrameModel getShapeFrame();
   /**
    * Set the text frame model.
    */
   public void setTextureFrame(TextureFrameModel frame);
   /**
    * Get the text frame model.
    */
   public TextureFrameModel getTextureFrame();
   /**
    * Set the line frame model.
    */
   public void setLineFrame(LineFrameModel frame);
   /**
    * Get the line frame model.
    */
   public LineFrameModel getLineFrame();
   /**
    * Set the size frame model.
    */
   public void setSizeFrame(SizeFrameModel frame);
   /**
    * Get the size frame model.
    */
   public SizeFrameModel getSizeFrame();
   /**
    * Get the aggregate descriptor.
    * @return the aggregate descriptor if in aggregate or null if not.
    */
   public OriginalDescriptor getAggregateDesc();

   // Relation charts
   /**
    * Set the node color frame model.
    */
   default void setNodeColorFrame(ColorFrameModel frame) {
   }
   /**
    * Get the node color frame model.
    */
   default ColorFrameModel getNodeColorFrame() {
      return null;
   }
   /**
    * Set the node size frame model.
    */
   default void setNodeSizeFrame(SizeFrameModel frame) {
   }
   /**
    * Get the node size frame model.
    */
   default SizeFrameModel getNodeSizeFrame() {
      return null;
   }

   default AestheticInfo getNodeColorField() {
      return null;
   }

   default void setNodeColorField(AestheticInfo aref) {
   }

   default AestheticInfo getNodeSizeField() {
      return null;
   }

   default void setNodeSizeField(AestheticInfo aref) {
   }
}

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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.aesthetic.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.graph.aesthetic.*;

/**
 * The API for classes that can be bound to aesthetic fields.
 *
 * @version 11.4
 * @author InetSoft Technology Corp.
 */
public interface ChartBindable {
   /**
    * Get the runtime chart type on the ref.
    * @return the runtime chart type.
    */
   public int getRTChartType();

   /**
    * Set the runtime chart type on the ref.
    */
   public void setRTChartType(int ctype);

   /**
    * Get the chart type on the ref.
    * @return the chart type.
    */
   public int getChartType();

   /**
    * Set the chart type of the chartinfo.
    * @param type the chart type.
    */
   public void setChartType(int type);

   /**
    * Get the color frame wrapper.
    */
   public ColorFrameWrapper getColorFrameWrapper();

   /**
    * Set the color frame wrapper.
    */
   public void setColorFrameWrapper(ColorFrameWrapper wrapper);

   /**
    * Get the shape frame wrapper.
    */
   public ShapeFrameWrapper getShapeFrameWrapper();

   /**
    * Set the shape frame wrapper.
    */
   public void setShapeFrameWrapper(ShapeFrameWrapper wrapper);

   /**
    * Get the line frame wrapper.
    */
   public LineFrameWrapper getLineFrameWrapper();

   /**
    * Set the line frame wrapper.
    */
   public void setLineFrameWrapper(LineFrameWrapper wrapper);

   /**
    * Get the texture frame wrapper.
    */
   public TextureFrameWrapper getTextureFrameWrapper();

   /**
    * Set the texture frame wrapper.
    */
   public void setTextureFrameWrapper(TextureFrameWrapper wrapper);

   /**
    * Get the size frame wrapper.
    */
   public SizeFrameWrapper getSizeFrameWrapper();

   /**
    * Set the size frame wrapper.
    */
   public void setSizeFrameWrapper(SizeFrameWrapper wrapper);

   /**
    * Get the color frame of this ref.
    * @return the color frame.
    */
   public ColorFrame getColorFrame();

   /**
    * Set the color frame of this ref.
    * @param clFrame the color frame.
    */
   public void setColorFrame(ColorFrame clFrame);

   /**
    * Get shape/texture frame of this ref.
    * @return the shape/texture frame.
    */
   public ShapeFrame getShapeFrame();

   /**
    * Set the shape/texture frame of this ref.
    * @param shFrame the shape/texture frame.
    */
   public void setShapeFrame(ShapeFrame shFrame);

   /**
    * Get the line frame of this ref.
    * @return the color frame.
    */
   public LineFrame getLineFrame();

   /**
    * Set the line frame of this ref.
    * @param lineFrame the line frame.
    */
   public void setLineFrame(LineFrame lineFrame);

   /**
    * Get the texture frame.
    * @return the texture frame.
    */
   public TextureFrame getTextureFrame();

   /**
    * Set the texture frame of this ref.
    * @param teFrame the texture frame.
    */
   public void setTextureFrame(TextureFrame teFrame);

   /**
    * Get the size frame.
    */
   public SizeFrame getSizeFrame();

   /**
    * Set the size frame.
    */
   public void setSizeFrame(SizeFrame zframe);

   /**
    * Get the color field.
    */
   public AestheticRef getColorField();

   /**
    * Set the color field.
    */
   public void setColorField(AestheticRef field);

   /**
    * Get the shape field.
    */
   public AestheticRef getShapeField();

   /**
    * Set the shape field.
    */
   public void setShapeField(AestheticRef field);

   /**
    * Get the size field.
    */
   public AestheticRef getSizeField();

   /**
    * Set the size field.
    */
   public void setSizeField(AestheticRef field);

   /**
    * Get the text field.
    */
   public AestheticRef getTextField();

   /**
    * Set the text field.
    */
   public void setTextField(AestheticRef field);

   /**
    * Get the runtime color field.
    */
   public DataRef getRTColorField();

   /**
    * Get the runtime shape field.
    */
   public DataRef getRTShapeField();

   /**
    * Get the runtime size field.
    */
   public DataRef getRTSizeField();

   /**
    * Get the runtime text field.
    */
   public DataRef getRTTextField();
}

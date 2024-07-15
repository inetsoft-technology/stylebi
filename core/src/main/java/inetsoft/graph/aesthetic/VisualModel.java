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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * A visual object is an object that has a visual representation on a graphic
 * output.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VisualModel {
   /**
    * Create an empty visual model.
    */
   public VisualModel() {
      super();
   }

   /**
    * Create a visual model for the graph element.
    */
   public VisualModel(DataSet data, ColorFrame colors, SizeFrame sizes,
                      ShapeFrame shapes, TextureFrame textures, LineFrame lines,
                      TextFrame texts) {
      super();

      this.data = data;
      this.colors = colors;
      this.sizes = sizes;
      this.shapes = shapes;
      this.textures = textures;
      this.lines = lines;
      this.texts = texts;
   }

   /**
    * Get the color of this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public Color getColor(String var, int idx) {
      Color color = (colors == null) ? null : colors.getColor(data, var, idx);
      return (color == null) ? StaticColorFrame.DEFAULT_COLOR : color;
   }

   /**
    * Get the size of this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public double getSize(String var, int idx) {
      return (sizes == null) ? 1 : sizes.getSize(data, var, idx);
   }

   /**
    * Get the shape to use to draw/fill this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public GShape getShape(String var, int idx) {
      return (shapes == null) ? null : shapes.getShape(data, var, idx);
   }

   /**
    * Get the texture to use to draw/fill this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public GTexture getTexture(String var, int idx) {
      return (textures == null) ? null : textures.getTexture(data, var, idx);
   }

   /**
    * Get the line to use to draw/fill this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public GLine getLine(String var, int idx) {
      return (lines == null) ? null : lines.getLine(data, var, idx);
   }

   /**
    * Get the text to be drawn for this object.
    * @param var the name of the variable column.
    * @param idx the index of the tuple that is rendered.
    */
   public Object getText(String var, int idx) {
      return (texts == null) ? null : texts.getText(data, var, idx);
   }

   /**
    * Get the color frame.
    */
   public ColorFrame getColorFrame() {
      return colors;
   }

   /**
    * Set the color frame.
    */
   public void setColorFrame(ColorFrame color) {
      this.colors = color;
   }

   /**
    * Get the size frame.
    */
   public SizeFrame getSizeFrame() {
      return sizes;
   }

   /**
    * Set the size frame.
    */
   public void setSizeFrame(SizeFrame size) {
      this.sizes = size;
   }

   /**
    * Get the shape frame.
    */
   public ShapeFrame getShapeFrame() {
      return shapes;
   }

   /**
    * Set the shape frame.
    */
   public void setShapeFrame(ShapeFrame shape) {
      this.shapes = shape;
   }

   /**
    * Get the line frame.
    */
   public LineFrame getLineFrame() {
      return lines;
   }

   /**
    * Set the line frame.
    */
   public void setLineFrame(LineFrame line) {
      this.lines = line;
   }

   /**
    * Get the texture frame.
    */
   public TextureFrame getTextureFrame() {
      return textures;
   }

   /**
    * Set the texture frame.
    */
   public void setTextureFrame(TextureFrame texture) {
      this.textures = texture;
   }

   /**
    * Get the text frame.
    */
   public TextFrame getTextFrame() {
      return texts;
   }

   /**
    * Set the text frame.
    */
   public void setTextFrame(TextFrame text) {
      this.texts = text;
   }

   /**
    * Get the dataset this visual model is based on.
    */
   public DataSet getDataSet() {
      return data;
   }

   private DataSet data;
   private ColorFrame colors;
   private SizeFrame sizes;
   private ShapeFrame shapes;
   private LineFrame lines;
   private TextureFrame textures;
   private TextFrame texts;

   private static final Logger LOG =
      LoggerFactory.getLogger(VisualModel.class);
}

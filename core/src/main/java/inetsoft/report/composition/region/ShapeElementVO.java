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
package inetsoft.report.composition.region;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.VOText;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;

/**
 * A visual object is an object that has a visual representation on a graphic
 * output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ShapeElementVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param gobj geometry object.
    */
   public ShapeElementVO(Geometry gobj, String mname) {
      super(gobj, mname);
   }

   /**
    * Get the visual object's text.
    * @return the VLabel of this visual object.
    */
   @Override
   public VOText[] getVOTexts() {
      return new VOText[0];
   }

   /**
    * Get shapes.
    * @return shapes.
    */
   @Override
   public Shape[] getShapes() {
      return new Shape[] {shape};
   }

   /**
    * Layout the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      // do nothing
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      // do nothing
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      return shape == null ? null : shape.getBounds();
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return -1;
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return -1;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      // do nothing
   }

   /**
    * Get shape.
    * @return shape of this VO contains.
    */
   public Shape getShape() {
      return shape;
   }

   /**
    * Set shape.
    * @param shape of this VO contains.
    */
   public void setShape(Shape shape) {
      this.shape = shape;
   }

   /**
    * Get the index of the line in the line map if this point is part of a line.
    */
   public int getLineIndex() {
      return lineIdx;
   }

   /**
    * Set the index of the line in the line map if this point is part of a line.
    */
   public void setLineIndex(int idx) {
      this.lineIdx = idx;
   }

   /**
    * Check if the dimension for the tooltip for this point (for radar).
    */
   public boolean isIgnoredDim(String dim) {
      return ignoredDims.contains(dim);
   }

   /**
    * Ignore the dimension for the tooltip for this point (for radar).
    */
   public void setIgnoredDim(String dim) {
      this.ignoredDims.add(dim);
   }

   private Shape shape; // corresponding shape definition
   private int lineIdx = -1;
   private Set<String> ignoredDims = new HashSet<>();
}

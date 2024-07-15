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
package inetsoft.graph.visual;

import inetsoft.graph.GraphPaintContext;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.geometry.Geometry;
import inetsoft.graph.geometry.GraphGeometry;
import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * This is a visual object corresponding to a nested graph.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class GraphVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    */
   public GraphVO(Geometry gobj) {
      super(gobj);

      GraphGeometry obj = (GraphGeometry) gobj;

      vgraph = new VGraph(obj.getCoordinate());
      vgraph.createVisuals(obj.getGGraph());
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
   }

   /**
    * Get the embedded cgraph.
    */
   public VGraph getVGraph() {
      return vgraph;
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      paint(g, GraphPaintContext.getDefault());
   }

   /**
    * Paint the visual object on the graphics.
    */
   public void paint(Graphics2D g, GraphPaintContext ctx) {
      Rectangle c = g.getClipBounds();
      Rectangle2D b = getBounds();

      // optimization, avoid painting a subgraph if not visible
      if(c == null || b == null ||
         b.intersects(c.x - 1, c.y - 1, c.width + 2, c.height + 2))
      {
         vgraph.paint(g, ctx);
      }
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      return vgraph.getCoordinate().getCoordBounds();
   }

   /**
    * Get the visual object's text.
    * @return always return null since GraphVO has no VOText.
    */
   @Override
   public VOText[] getVOTexts() {
      // GraphVO has no text
      return new VOText[0];
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return 0;
   }

   /**
    * Get the min height of this visualizable.
    * @return the min height of this visualizable.
    */
   @Override
   protected double getMinHeight0() {
      return 0;
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return 0;
   }

   /**
    * Get the preferred height of this visualizable.
    * @return the preferred height of this visualizable.
    */
   @Override
   protected double getPreferredHeight0() {
      return 0;
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      return new Shape[0];
   }

   /**
    * Get the minimum size factor of all elements.
    */
   @Override
   public double getSizeFactor() {
      return GTool.getMinSizeFactor(vgraph);
   }

   /**
    * Scale the shape to the specified size factor.
    */
   @Override
   public void scaleToSizeFactor(double factor) {
      GTool.scaleToSizeFactor(vgraph, factor);
   }

   /**
    * Check if this element needs to dodge from overlapping.
    */
   @Override
   public boolean requiresDodge() {
      return false;
   }

   @Override
   public void layoutCompleted() {
      super.layoutCompleted();
      vgraph.layoutCompleted();

      GraphGeometry obj = (GraphGeometry) getGeometry();
      obj.getGGraph().layoutCompleted();
   }

   private VGraph vgraph;
}

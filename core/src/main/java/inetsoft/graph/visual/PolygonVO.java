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

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;

import java.awt.*;
import java.awt.geom.*;

/**
 * This visual object represents a polygon in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class PolygonVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    */
   public PolygonVO(Geometry gobj, Coordinate coord) {
      super(gobj);
      this.coord = coord;

      PolygonGeometry geom = (PolygonGeometry) gobj;
      GraphElement elem = geom.getElement();
      Point2D loc = coord.getPosition(geom.getTuple());
      GShape gshape = getGShape();
      Object label = geom.getText(0);

      if(gshape instanceof PolyShape) {
         shape = ((PolyShape) gshape).getShape(coord);
      }
      else {
         double size = geom.getSize(0);
         double height = getPolygonSize(coord, size, false);
         double width = getPolygonSize(coord, size, true);
         shape = gshape.getShape(loc.getX(), loc.getY(), width, height);

         if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
            shape = GTool.centerShape(shape, true, width, height);
            shape = GTool.centerShape(shape, false, width, height);
         }
      }

      if(label != null) {
         vtext = new VOText(label, this, getMeasureName(), coord.getDataSet(),
                            geom.getSubRowIndex(), gobj);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
      }
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    * @param coord the visual object's coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      shape = (Shape) coord.transformShape(shape);
   }

   /**
    * Get the polygon shape.
    * @param paint true if for painting.
    */
   private Shape getPath0(boolean paint) {
      if(path0 == null || !getScreenTransform().equals(trans0)) {
         trans0 = (AffineTransform) getScreenTransform().clone();
         path0 = getScreenTransform().createTransformedShape(shape);
      }

      return path0;
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();
      Color color = gobj.getColor(0);
      GTexture fill = gobj.getTexture(0);
      GLine line = gobj.getLine(0);
      Color borderColor = elem.getBorderColor();
      GShape shape = getGShape();
      Graphics2D g2 = (Graphics2D) g.create();
      Shape path = getPath0(true);

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

      paintPolygon(g2, path, color, shape, fill, line, borderColor);
      g2.dispose();
   }

   /**
    * Paint the polygon shape.
    */
   private void paintPolygon(Graphics2D g2, Shape path, Color color,
			     GShape shape, GTexture texture, GLine line, Color lineColor)
   {
      boolean textured = texture != null;
      color = applyAlpha(color);

      g2.setColor(color);

      if(line != null) {
         g2.setStroke(line.getStroke());
      }

      if(!textured) {
         shape.paint(g2, path);
      }
      else {
         texture.paint(g2, path);
      }

      if(line != null && shape.isFill()) {
         paintLine(g2, path, line, lineColor != null ? lineColor : color.darker());
      }
      // force a border if texture is used otherwise the shape looks incomplete
      // and distorted
      else if(textured) {
         paintLine(g2, path, new GLine(1), lineColor != null ? lineColor : color);
      }
      else {
         if(lineColor != null) {
            paintLine(g2, path, new GLine(1), lineColor);
         }
      }
   }

   /**
    * Paint a line around the shape.
    */
   static void paintLine(Graphics2D g2, Shape path, GLine line, Color color) {
      g2 = (Graphics2D) g2.create();
      g2.setColor(color);
      g2.setStroke(line.getStroke());
      g2.draw(path);
      g2.dispose();
   }

   /**
    * Get the visual object's text.
    * @return the VLabel of this visual object.
    */
   @Override
   public VOText[] getVOTexts() {
      return new VOText[] {vtext};
   }

   /**
    * Layout text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext == null) {
         return;
      }

      vgraph.removeVisual(vtext);
      vgraph.addVisual(vtext);

      Shape shape = getPath0(false);
      GShape gshape = getGShape();
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();
      int placement = gobj.getLabelPlacement();
      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = vtext.getPreferredHeight();
      double tx, ty;
      double midx, midy;
      Rectangle2D box = shape.getBounds2D();

      switch(placement) {
      case GraphConstants.BOTTOM:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY();
         tx = midx - prefw / 2;
         ty = midy - TEXT_GAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_UP);
         break;
      case GraphConstants.LEFT:
         midx = box.getX();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx - prefw - TEXT_GAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_RIGHT);
         break;
      case GraphConstants.RIGHT:
         midx = box.getX() + box.getWidth();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx + TEXT_GAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_RIGHT);
         break;
      case GraphConstants.TOP:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight();
         tx = midx - prefw / 2;
         ty = midy + TEXT_GAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_UP);
         break;
      case GraphConstants.AUTO:
         placement = GraphConstants.CENTER;
      case GraphConstants.CENTER:
      default:
         Point2D cp = gshape.getCenter(shape);

         midx = cp.getX();
         midy = cp.getY();
         tx = midx - prefw / 2;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_UP);

         // text in middle of bar, support auto text color
         if(elem.isAutoTextColor()) {
            vtext.setAutoBackground(gobj.getColor(0));
         }
         break;
      }

      vtext.setPosition(new Point2D.Double(tx, ty));
      vtext.setSize(new DimensionD(prefw, prefh));

      Point2D pt = new Point2D.Double(midx, midy);
      Point2D offset = vtext.getRotationOffset(pt, placement);
      vtext.setOffset(offset);
      vtext.setPlacement(placement);
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      Rectangle2D box = getPath0(false).getBounds2D();

      // if borders exists, there is 0.5 pt (half of a line) outside of the area
      // for polygon (map), the position and size are in fractions, so we add
      // an extra 1 pixel to avoid clipping the shapes
      if(hasBorder() && !box.isEmpty()) {
         box = new Rectangle2D.Double(box.getX() - 1.5, box.getY() - 1.5,
                                      box.getWidth() + 3, box.getHeight() + 3);
      }

      return box;
   }

   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      // account for border (same as getBounds)
      return hasBorder() ? 1.5 : 0;
   }

   /**
    * Check if border line exists.
    */
   private boolean hasBorder() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();
      GTexture fill = gobj.getTexture(0);
      GLine line = gobj.getLine(0);
      GShape shape = getGShape();
      Color borderColor = elem.getBorderColor();
      return line != null || fill != null || shape.isOutline() || borderColor != null;
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      double textw = vtext == null ? 0 : vtext.getMinWidth();
      return Math.max(POLYGON_MIN_WIDTH, textw);
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      double textw = vtext == null ? 0 : vtext.getPreferredWidth();
      return Math.max(POLYGON_PREFERRED_WIDTH, textw);
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      return new Shape[] { getPath0(false) };
   }

   /**
    * Get the gshape for this polygon.
    */
   private GShape getGShape() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GShape shape = gobj.getShape(0);

      return (shape == null || shape == GShape.NIL) ? GShape.FILLED_SQUARE : shape;
   }

   /**
    * Calculate the polygon size.
    * @param wh true for width and false for height
    */
   protected double getPolygonSize(Coordinate coord, double size, boolean wh) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      SizeFrame frame = gobj.getVisualModel().getSizeFrame();
      double maxpolygon = wh ? coord.getMaxWidth() : coord.getMaxHeight();

      if(frame == null) {
         return maxpolygon;
      }

      double largest = frame.getLargest();
      double smallest = frame.getSmallest();
      double max = frame.getMax();

      if(largest == smallest) {
         return Math.max(1, maxpolygon / 2);
      }

      double minwidth = maxpolygon * smallest / max;
      double w = maxpolygon - minwidth;

      return minwidth + w * size / max;
   }

   @Override
   public PolygonVO clone() {
      PolygonVO obj = (PolygonVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private static final int POLYGON_MIN_WIDTH = 1;
   private static final int POLYGON_PREFERRED_WIDTH = 10;

   private Shape shape; // the shape to draw for this vo
   private VOText vtext;
   private Coordinate coord;
   // cached value
   private AffineTransform trans0; // transform as of last path0
   private Shape path0; // transformed shape ready for drawing
}

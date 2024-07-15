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

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.aesthetic.GTexture;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.MekkoElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.DimensionD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Visualizable object for a mekko area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class MekkoVO extends ElementVO {
   public MekkoVO(Geometry gobj, Coordinate coord, double base, double fraction,
                  double groupFraction, double[] tuple)
   {
      super(gobj);

      MekkoGeometry obj = (MekkoGeometry) gobj;
      MekkoElement elem = (MekkoElement) obj.getElement();

      Point2D loc = coord.getPosition(tuple);
      double w = groupFraction * Coordinate.GWIDTH;
      double h = fraction * Coordinate.GHEIGHT;
      double y = base * Coordinate.GHEIGHT;
      double x = loc.getX();
      this.rect = new Rectangle2D.Double(x, y, w, h);

      // overlaid shape shouldn't draw label
      if(elem.getHint("overlaid") == null) {
         Object label = obj.getText(0);

         if(label != null) {
            vtext = new VOText(label, this, getMeasureName(), coord.getDataSet(),
                               obj.getSubRowIndex(), obj);
            vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);

            if(elem.isAutoTextColor()) {
               vtext.setAutoBackground(obj.getColor(0));
            }
         }
      }
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      MekkoGeometry gobj = (MekkoGeometry) getGeometry();
      GraphElement elem = gobj.getElement();

      Shape shape = getScreenTransform().createTransformedShape(rect);
      Color color = gobj.getColor(0);
      GLine line = gobj.getLine(0);
      Color borderColor = elem.getBorderColor();
      GTexture texture = gobj.getTexture(0);
      Graphics2D g2 = (Graphics2D) g.create();

      color = applyAlpha(color);
      g2.setColor(color);

      if(texture != null) {
         texture.paint(g2, shape);
      }
      else {
         g2.fill(shape);
      }

      if(borderColor != null) {
         g2.setColor(borderColor);
      }
      else {
         g2.setColor(Color.WHITE);
      }

      if(line != null) {
         g2.setStroke(line.getStroke());
      }

      g2.draw(shape);
      g2.dispose();
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext == null) {
         return;
      }

      vgraph.removeVisual(vtext);
      vgraph.addVisual(vtext);

      Rectangle2D vbounds = vgraph.getPlotBounds();
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      int placement = gobj.getLabelPlacement();
      double prefw = getVOTextWidth(vtext, vgraph, gobj);
      double prefh = vtext.getPreferredHeight();
      double tx, ty;
      double midx, midy;
      Rectangle2D box = getScreenTransform().createTransformedShape(rect).getBounds2D();
      final int GAP = 2;

      if(prefw * 0.9 > box.getWidth() && prefw * 0.8 < box.getHeight()) {
         double tmp = prefw;
         prefw = prefh;
         prefh = tmp;
         TextSpec spec = vtext.getTextSpec().clone();
         spec.setRotation(90);
         vtext.setTextSpec(spec);
      }
      else if(prefh * 0.8 > box.getHeight()) {
         vtext.setZIndex(-1);
      }

      switch(placement) {
      case GraphConstants.BOTTOM:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY();
         tx = midx - prefw / 2;
         ty = midy + GAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         midx = box.getX();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx + GAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         midx = box.getX() + box.getWidth();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx - prefw - GAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         break;
      case GraphConstants.TOP:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight();
         tx = midx - prefw / 2;
         ty = midy - prefh - GAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.AUTO:
         placement = GraphConstants.CENTER;
      default: // auto or center
         Point2D cp = new Point2D.Double(box.getCenterX(), box.getCenterY());

         midx = cp.getX();
         midy = cp.getY();
         tx = midx - prefw / 2;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      }

      vtext.setCollisionModifier(VLabel.MOVE_NONE);
      vtext.setPosition(new Point2D.Double(Math.max(tx, vbounds.getX()), ty));
      vtext.setPlacement(placement);

      if(prefw * 0.8 > box.getWidth() - GAP) {
         vtext.setZIndex(-1);
      }
      // if wider than area, reduce the width and use ...
      else if(prefw > box.getWidth() - GAP) {
         prefw = Math.min(box.getWidth(), Math.max(30, box.getWidth() - GAP));
      }

      vtext.setSize(new DimensionD(prefw, prefh));

      Point2D pt = new Point2D.Double(midx, midy);
      Point2D offset = vtext.getRotationOffset(pt, placement);
      vtext.setOffset(offset);
   }

   @Override
   public void transform(Coordinate coord) {
      // tree map doesn't support polar coord
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      return getScreenTransform().createTransformedShape(rect).getBounds2D();
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return 1;
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return getMinWidth0();
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
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      Rectangle2D shape = getScreenTransform().createTransformedShape(rect).getBounds2D();
      return new Shape[] {shape};
   }

   // called if "overlay" is defined
   @Override
   public void overlay(VisualObject vo0) {
      ElementVO vo = (ElementVO) vo0;
      MekkoGeometry overObj = (MekkoGeometry) vo.getGeometry();
      MekkoGeometry gobj = (MekkoGeometry) getGeometry();

      double overSize = overObj.getSize(0);
      // overlay (brushing),  place at the bottom of the underlying shape
      Rectangle2D over = vo.getShapes()[0].getBounds2D();

      double mySize = gobj.getSize(0);
      double overW = over.getWidth();
      double overH = over.getHeight();
      double h = mySize * overH / overSize;
      rect = new Rectangle2D.Double(over.getX(), over.getY(), overW, h);
   }

   @Override
   public MekkoVO clone() {
      MekkoVO obj = (MekkoVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private Rectangle2D rect;
   private VOText vtext;

   private static final Logger LOG = LoggerFactory.getLogger(MekkoVO.class);
}

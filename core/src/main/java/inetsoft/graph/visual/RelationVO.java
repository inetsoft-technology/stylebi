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
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Visualizable object for a relation area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class RelationVO extends ElementVO {
   public RelationVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      RelationGeometry obj = (RelationGeometry) gobj;
      RelationElement elem = (RelationElement) obj.getElement();

      this.shape = obj.getShape();

      // only add label on 'all'
      if(elem.getHint("overlay") == null) {
         Object label = obj.getText();

         if(label != null) {
            String measure = getMeasureName();
            vtext = new VOText(label, this, measure, coord.getDataSet(), obj.getSubRowIndex(), obj);

            if(elem.isAutoTextColor() && elem.isLabelInside()) {
               vtext.setAutoBackground(obj.getColor());
            }

            // avoid edge line strikes through labels.
            if(!elem.isLabelInside() && vtext.getTextSpec().getBackground() == null) {
               vtext.getTextSpec().setBackground(new Color(1, 1, 1, 0.8f));
            }

            vtext.setWrapping(true);
            vtext.setCollisionModifier(VLabel.MOVE_FREE);
            vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }

         CurrentTextFrame.setCurrentField(null);
      }
   }

   private boolean isColorBound(RelationElement elem) {
      return elem.getNodeColorFrame() != null &&
         elem.getNodeColorFrame().getVisualField() != null ||
         // brushed, using auto text color
         elem.getHint("overlaid") != null;
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      RelationGeometry gobj = (RelationGeometry) getGeometry();
      RelationElement elem = (RelationElement) gobj.getElement();

      if(this.shape == null) {
         return;
      }

      Graphics2D g2 = (Graphics2D) g.create();

      GTool.setRenderingHint(g2, false);

      Shape shape = getScreenTransform().createTransformedShape(this.shape);
      Color borderColor = elem.getBorderColor();
      GTexture texture = gobj.getTexture(0);
      Color color = gobj.getColor();
      Color fillColor = elem.getFillColor();
      GLine line = gobj.getLine(0);

      color = applyAlpha(color);

      g2.setColor(color);

      // use node fill color to fill if color is not bound.
      if(fillColor != null && !isColorBound(elem)) {
         g2.setColor(fillColor);
         g2.fill(shape);
         // use the static color for border.
         borderColor = color;
      }
      else if(texture != null) {
         texture.paint(g2, shape);
      }
      // fill color if color is from binding.
      else {
         g2.fill(shape);
      }

      if(borderColor != null) {
         g2.setColor(borderColor);
         g2.setStroke(line != null ? line.getStroke() : new BasicStroke(1));
         g2.draw(shape);
      }

      g2.dispose();
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext == null || shape == null) {
         return;
      }

      vgraph.removeVisual(vtext);
      vgraph.addVisual(vtext);

      RelationGeometry gobj = (RelationGeometry) getGeometry();
      RelationElement elem = (RelationElement) gobj.getElement();

      Rectangle2D box = getScreenTransform().createTransformedShape(shape).getBounds2D();
      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = elem.isLabelInside()
         ? vtext.getPreferredHeight(box.getWidth(), box.getHeight()) : vtext.getPreferredHeight();

      Rectangle2D vbounds = vgraph.getPlotBounds();
      int placement = gobj.getLabelPlacement();
      double tx, ty;
      double midx, midy;
      final int XGAP = 2;
      final int YGAP = 2;

      switch(placement) {
      case GraphConstants.BOTTOM:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY();
         tx = midx - prefw / 2;
         ty = midy - prefh - YGAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         midx = box.getX();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx - prefw - XGAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         midx = box.getX() + box.getWidth();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx + XGAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         break;
      case GraphConstants.TOP:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight();
         tx = midx - prefw / 2;
         ty = midy + YGAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.CENTER:
      default: // auto
         midx = box.getX() + box.getWidth() / 2;
         tx = midx - Math.min(box.getWidth(), prefw) / 2;

         midy = box.getY() + box.getHeight() / 2;
         ty = midy - Math.min(box.getHeight(), prefh) / 2;

         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         vtext.setPlacement(placement);
         break;
      }

      vtext.setPosition(new Point2D.Double(Math.max(tx, vbounds.getX()), ty));

      // if wider than area, reduce the width and use ...
      if(prefw > box.getWidth() && elem.isLabelInside()) {
         prefw = Math.max(10, box.getWidth());
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
      if(shape == null) {
         return null;
      }

      return getScreenTransform().createTransformedShape(shape).getBounds2D();
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
      if(this.shape == null) {
         return new Shape[0];
      }

      Rectangle2D shape = getScreenTransform().createTransformedShape(this.shape).getBounds2D();
      return new Shape[] { shape };
   }

   @Override
   public void overlay(VisualObject vo0) {
      ElementVO vo = (ElementVO) vo0;
      RelationGeometry overObj = (RelationGeometry) vo.getGeometry();
      RelationGeometry gobj = (RelationGeometry) getGeometry();
      Rectangle2D over = vo.getShapes()[0].getBounds2D();

      double overH = over.getHeight();
      double overW = over.getWidth();
      double w = overW;
      double h = overH;

      // if a node is not unique, the size may be meaningless so just use the same size
      // as all for simple highlight.
      if(!gobj.isMultiParent()) {
         double mySize = gobj.getSize(0);
         double overSize = overObj.getSize(0);
         // equation:
         // w * h / (overW * overH) = mySize / overSize
         // w / h = overW / overH
         h = overSize != 0 ? Math.sqrt(mySize * overH * overH / overSize) : overH;
         w = overW * h / overH;
      }

      shape = new Rectangle2D.Double(over.getX(), over.getY() + overH - h, w, h);
   }

   @Override
   public RelationVO clone() {
      RelationVO obj = (RelationVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private Shape shape;
   private VOText vtext;

   private static final Color DEFAULT_LINE_COLOR = new Color(0xafafad);
   private static final Logger LOG = LoggerFactory.getLogger(RelationVO.class);
}

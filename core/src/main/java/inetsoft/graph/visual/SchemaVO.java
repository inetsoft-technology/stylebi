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
package inetsoft.graph.visual;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.schema.SchemaPainter;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Map;

/**
 * This visual object represents a stock or a candle in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SchemaVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    */
   public SchemaVO(Geometry gobj, Coordinate coord, SchemaPainter painter) {
      super(gobj);

      this.painter = painter;

      SchemaGeometry geom = (SchemaGeometry) gobj;
      GraphElement elem = geom.getElement();
      Object label = geom.getText(0);

      // don't center if dodge asymmetric, this is useful for stacked points
      // on a 1d coord (p.173)
      if(isRect1(coord) && (elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
         AffineTransform trans = AffineTransform.getTranslateInstance(coord.getMaxWidth() / 2, 0);
         painter.transformScreen(trans);
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
      painter.transformShape(coord, getHints());
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      SchemaPainter painter = (SchemaPainter) this.painter.clone();

      g = (Graphics2D) g.create();
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

      painter.transformScreen(getScreenTransform());
      painter.paint(g);
      g.dispose();
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
    * Init text size and position.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext == null) {
         return;
      }

      vgraph.removeVisual(vtext);
      vgraph.addVisual(vtext);

      ElementGeometry geom = (ElementGeometry) getGeometry();
      int placement = geom.getLabelPlacement();
      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = vtext.getPreferredHeight();
      Rectangle2D box = getPainterBounds();
      double lx, ly;
      Point2D rpt;

      switch(placement) {
      case GraphConstants.BOTTOM:
         lx = box.getX() + box.getWidth() / 2 - prefw / 2;
         ly = box.getY() - TEXT_GAP - prefh;
         rpt = new Point2D.Double(box.getX() + box.getWidth() / 2, box.getY());
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         lx = box.getX() - prefw - TEXT_GAP;
         ly = box.getY() + box.getHeight() / 2 - prefh / 2;
         rpt = new Point2D.Double(box.getX(), box.getY() + box.getHeight() / 2);
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         lx = box.getX() + box.getWidth() + TEXT_GAP;
         ly = box.getY() + box.getHeight() / 2 - prefh / 2;
         rpt = new Point2D.Double(box.getX() + box.getWidth(),
                                  box.getY() + box.getHeight() / 2);
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         break;
      case GraphConstants.CENTER:
         lx = box.getX() + box.getWidth() / 2 - prefw / 2;
         ly = box.getY() + box.getHeight() / 2 - prefh / 2;
         rpt = new Point2D.Double(box.getX() + box.getWidth() / 2,
				  box.getY() + box.getHeight() / 2);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.AUTO:
         placement = GraphConstants.TOP;
      case GraphConstants.TOP:
      default:
         lx = box.getX() + box.getWidth() / 2 - prefw / 2;
         ly = box.getY() + box.getHeight() + TEXT_GAP;
         rpt = new Point2D.Double(box.getX() + box.getWidth() / 2,
				  box.getY() + box.getHeight());
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      }

      vtext.setPosition(new Point2D.Double(lx, ly));
      vtext.setSize(new DimensionD(prefw, prefh));

      Point2D offset = vtext.getRotationOffset(rpt, placement);

      vtext.setOffset(offset);
      vtext.setPlacement(placement);

      if(GTool.getRotation(vgraph.getCoordinate().getCoordTransform()) == 0) {
         vtext.setCollisionModifier(VLabel.MOVE_UP);
      }
      else {
         vtext.setCollisionModifier(VLabel.MOVE_RIGHT);
      }
   }

   /**
    * Get the painter bounds.
    * @return the painter's bounds.
    */
   private Rectangle2D getPainterBounds() {
      Rectangle2D box = null;
      SchemaPainter painter = (SchemaPainter) this.painter.clone();

      painter.transformScreen(getScreenTransform());

      for(int i = 0; i < painter.getShapeCount(); i++) {
         Rectangle2D b1 = painter.getShape(i).getBounds2D();

         if(b1.getWidth() == 0 && b1.getHeight() == 0) {
            continue;
         }

         // account for the space taken up by line
         b1 = new Rectangle2D.Double(b1.getX() - 1, b1.getY() - 1,
                                     b1.getWidth() + 2, b1.getHeight() + 2);

         if(!isValidBounds(b1)) {
            continue;
         }

         if(box == null) {
            box = b1;
         }
         else {
            box = box.createUnion(b1);
         }
      }

      return (box == null) ? new Rectangle2D.Double() : box;
   }

   /**
    * Check if the bounds is valid.
    */
   private boolean isValidBounds(Rectangle2D box) {
      return box != null && !Double.valueOf(box.getX()).isNaN() &&
         !Double.valueOf(box.getY()).isNaN() &&
         !Double.valueOf(box.getWidth()).isNaN() &&
         !Double.valueOf(box.getHeight()).isNaN();
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      return getPainterBounds();
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return MIN_WIDTH;
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return PREFERRED_WIDTH;
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      SchemaPainter painter = (SchemaPainter) this.painter.clone();
      painter.transformScreen(getScreenTransform());

      Shape[] shapes = new Shape[painter.getShapeCount()];

      for(int i = 0; i < shapes.length; i++) {
         shapes[i] = painter.getShape(i);
      }

      return shapes;
   }

   /**
    * Check if this element needs to dodge from overlapping.
    */
   @Override
   public boolean requiresDodge() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();
      int mask = GraphElement.MOVE_DODGE | GraphElement.MOVE_CENTER;

      return (elem.getCollisionModifier() & mask) != 0;
   }

   /**
    * Check if dodging is required.
    */
   @Override
   public boolean requiresDodge(ElementVO vo) {
      if(vo == this) {
         return false;
      }

      ElementGeometry geom = (ElementGeometry) getGeometry();
      ElementGeometry geom2 = (ElementGeometry) vo.getGeometry();
      GraphElement elem = geom.getElement();
      GraphElement elem2 = geom2.getElement();

      // only dodge for vo from same element type, e.g. interval and interval
      if(!getClass().equals(vo.getClass())) {
         return false;
      }

      if(!CoreTool.equals(elem.getHint("overlay"), elem2.getHint("overlay"))) {
         return false;
      }

      Rectangle2D b = getBounds();
      Rectangle2D b2 = (Rectangle2D) vo.getBounds();

      if(b.getY() > b2.getMaxY() || b2.getY() > b.getMaxY()) {
         return false;
      }

      double x1 = b.getX() + b.getWidth() / 2;
      double x2 = b2.getX() + b2.getWidth() / 2;

      return Math.abs(x1 - x2) < 2;
   }

   /**
    * Move the visual objects to avoid overlapping.
    * @param comap collision map, from the first visual object to a list (List)
    * of overlapping visual objects.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public void dodge(Map<ElementVO, List<ElementVO>> comap, Coordinate coord) {
      List<ElementVO> vos = comap.get(this);

      if(vos.size() == 0) {
         return;
      }

      GraphElement elem = ((ElementGeometry) getGeometry()).getElement();

      if((elem.getCollisionModifier() & GraphElement.MOVE_DODGE) != 0) {
         Rectangle2D box = getBounds();
         double move = box.getWidth() + 1;

         for(int i = 0; i < vos.size(); i++) {
            SchemaVO point = (SchemaVO) vos.get(i);
            double pw = point.getBounds().getWidth();
            AffineTransform trans = AffineTransform.getTranslateInstance(move,0);

            point.painter.transformScreen(trans);
            move += pw + 1;
         }
      }

      // center points
      if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
         Rectangle2D box1 = getBounds();
         Rectangle2D box2 = ((SchemaVO) vos.get(vos.size() - 1)).getBounds();
         double x1 = box1.getX() + box1.getWidth() / 2;
         double x2 = box2.getX() + box2.getWidth() / 2;
         double shift = -Math.abs((x2 - x1) / 2);
         AffineTransform trans = AffineTransform.getTranslateInstance(shift, 0);

         for(int i = 0; i < vos.size(); i++) {
            SchemaVO point = (SchemaVO) vos.get(i);

            point.painter.transformScreen(trans);
         }

         painter.transformScreen(trans);
      }
   }

   @Override
   public SchemaVO clone() {
      SchemaVO obj = (SchemaVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private static final int MIN_WIDTH = 5;
   private static final int PREFERRED_WIDTH = 20;
   private SchemaPainter painter;
   private VOText vtext;
}

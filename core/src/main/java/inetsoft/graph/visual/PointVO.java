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
package inetsoft.graph.visual;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;

/**
 * This visual object represents a point in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class PointVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param gobj geometry object.
    * @param coord the coordinate the visual object is plotted on.
    */
   public PointVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      PointGeometry obj = (PointGeometry) gobj;
      PointElement elem = (PointElement) obj.getElement();

      loc = coord.getPosition(obj.getTuple());

      // don't center if dodge asymmetric, this is useful for stacked points
      // on a 1d coord (p.173)
      if(isRect1(coord) &&
         (elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0)
      {
         if(((RectCoord) coord).getXScale() == null) {
            loc = new Point2D.Double(coord.getMaxWidth() / 2, loc.getY());
         }
         else {
            loc = new Point2D.Double(loc.getX(), coord.getMaxHeight() / 2);
         }
      }

      Object label = obj.getText(0);
      GShape shp = getShape();
      double size = obj.getSize(0);

      // if shape is not filled, make sure the edge is not covered by axis. (58343)
      if(shp != null && !shp.isFill()) {
         setZIndex(GDefaults.VO_OUTLINE_Z_INDEX);
      }
      // set to save z-index as line so when both line and point are drawn for a series,
      // the point should be placed on top (otherwise line goes through point.
      else {
         setZIndex(GDefaults.VO_Z_INDEX + 2);
      }

      radius = (short) (size + shp.getMinSize() / 2);

      // overlaid shape shouldn't draw label
      if(label != null && elem.getHint("overlaid") == null) {
         vtext = new VOText(label, this, getMeasureName(), coord.getDataSet(),
                            obj.getSubRowIndex(), obj);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
      }

      if(vtext != null) {
         // applying sizing to label for text cloud
         if(elem.isWordCloud()) {
            int fsize = vtext.getTextSpec().getFont(label).getSize();
            boolean staticSize = elem.getSizeFrame().getField() == null;
            double nsize = staticSize ? fsize + size * elem.getFontScale() - 1 :
               Math.round(fsize * 0.5 + size * elem.getFontScale());

            TextSpec spec = vtext.getTextSpec().clone();

            if(fsize != nsize) {
               spec.setFont(spec.getFont().deriveFont((float) nsize));
               vtext.setTextSpec(spec);
            }
         }
         // this is for display text in a table like layout
         else if(shp == GShape.NIL && !elem.isContour()) {
            vtext.setCollisionModifier(VLabel.MOVE_NONE);
         }

         if(shp == GShape.NIL) {
            ColorFrame cframe = elem.getColorFrame();

            // if only text is displayed for a point, apply the color of the
            // point on text instead of using the text static color
            if(!(cframe == null || cframe instanceof StaticColorFrame)) {
               TextSpec spec = vtext.getTextSpec().clone();

               spec.setColor(GTool.getColor(obj.getColor(0), getAlphaHint()));
               vtext.setTextSpec(spec);
            }
         }
      }

      if(shp == GShape.NIL) {
         radius = 0;
      }
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      PointElement elem = (PointElement) gobj.getElement();

      paint(g, gobj, loc, radius, getShape(), getScreenTransform(), getAlphaHint(),
            elem.getOutlineColor());
   }

   static void paint(Graphics2D g, ElementGeometry gobj, Point2D loc, double radius,
                     GShape shp, AffineTransform screenTrans, double alpha, Color outline)
   {
      GraphElement elem = gobj.getElement();
      Color color = gobj.getColor(0);
      GLine line = gobj.getLine(0);
      Color borderColor = elem.getBorderColor();
      Point2D pos = screenTrans.transform(loc, null);
      boolean applyEffect = "true".equals(elem.getHint(GraphElement.HINT_SHINE));
      // optimization, only create new graphics for pdf, more efficient for pdf and less
      // efficient for svg
      boolean createG = g instanceof PDFDevice;
      Graphics2D g2 = createG ? (Graphics2D) g.create() : g;

      if(outline != null) {
         Shape shape = getShape(shp, pos, radius, elem.getSizeFrame() != null);
         g.setStroke(new BasicStroke(2));
         g.setColor(outline);
         g.draw(shape);
         g.setStroke(new BasicStroke(1));
      }

      if(shp.isFill()) {
         if(alpha != 1) {
            color = GTool.getColor(color, alpha);
         }
      }

      Stroke ostroke = g.getStroke();
      Paint opaint = g.getPaint();
      g2.setColor(color);

      if(applyEffect && shp.isFill()) {
         applyEffect(g2, pos.getX(), pos.getY(), radius, alpha);
      }

      if(line != null) {
         g2.setStroke(line.getStroke());
      }

      Color olineColor = shp.getLineColor();
      int olineStyle = shp.getLineStyle();

      // only force border if borderColor is set (not if line is set), this
      // is to keep backward compatibility since we used line style without
      // forcing borders to be drawn before.
      if(borderColor != null) {
         if(!shp.isOutline()) {
            shp = shp.create(true, shp.isFill());
         }

         shp.setLineColor(borderColor);
      }

      if(line != null) {
         shp.setLineStyle(line.getStyle());
      }

      callShape(shp, elem.getSizeFrame() != null, s -> {
         s.paint(g2, pos.getX(), pos.getY(), radius);
         return true;
      });

      shp.setLineColor(olineColor);
      shp.setLineStyle(olineStyle);

      if(createG) {
         g2.dispose();
      }
      else {
         g.setStroke(ostroke);
         g.setPaint(opaint);
      }
   }

   private static Shape getShape(GShape shp, Point2D pos, double radius, boolean sizeDefined) {
      return callShape(shp, sizeDefined, s ->
         s.getShape(pos.getX() - radius, pos.getY() - radius, radius * 2, radius * 2));
   }

   private static <R> R callShape(GShape shp, boolean sizeDefined, Function<GShape, R> func) {
      // if size frame is not defined, never apply size to image shape.
      boolean ignoreSize = !sizeDefined && shp instanceof GShape.ImageShape &&
         ((GShape.ImageShape) shp).isApplySize();

      if(ignoreSize) {
         ((GShape.ImageShape) shp).setApplySize(false);
      }

      try {
         return func.apply(shp);
      }
      finally {
         if(ignoreSize) {
            ((GShape.ImageShape) shp).setApplySize(true);
         }
      }
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      PointGeometry gobj = (PointGeometry) getGeometry();

      if(vtext == null) {
         return;
      }

      PointElement elem = (PointElement) ((ElementGeometry) getGeometry()).getElement();

      // make sure word cloud is not drawn in the neighboring area
      if(elem.isWordCloud()) {
         String key = "__cached_textClip" + CoreTool.arrayToString(gobj.getTuple());
         Rectangle2D pbounds = (Rectangle2D) elem.getHint(key);

         if(pbounds == null) {
            double mw = vgraph.getCoordinate().getMaxWidth();
            double mh = vgraph.getCoordinate().getMaxHeight();
            pbounds = new Rectangle2D.Float((float) (loc.getX() - mw / 2),
                                            (float) (loc.getY() - mh / 2),
                                            (float) mw, (float) mh);
            pbounds = vgraph.getCoordinate().getCoordTransform()
               .createTransformedShape(pbounds).getBounds2D();
            pbounds = getScreenTransform().createTransformedShape(pbounds).getBounds2D();
            elem.setHint(key, pbounds);
         }

         vtext.setClipBounds(pbounds);
         vtext.setMaxSize(new Dimension((int) pbounds.getWidth(), (int) pbounds.getHeight()));
      }

      lvgraph = layoutText(vgraph, lvgraph, gobj, vtext, loc, radius, getShape(),
                           getScreenTransform());
   }

   static VGraph layoutText(VGraph vgraph, VGraph lvgraph, ElementGeometry gobj,
                            VOText vtext, Point2D loc, double radius, GShape shp,
                            AffineTransform screenTrans)
   {
      // performance optimization

      // last vgraph is null, just add it
      if(lvgraph == null) {
         vgraph.addVisual(vtext);
      }
      // if last vgraph is the same, we need not to remove then add it
      else if(lvgraph != vgraph) {
         vgraph.removeVisual(vtext);
         vgraph.addVisual(vtext);
      }

      lvgraph = vgraph;

      int placement = gobj.getLabelPlacement();
      double size = gobj.getSize(0);
      GraphElement elem = gobj.getElement();
      double r = radius;

      Point2D pt = screenTrans.transform(loc, null);
      Shape shape = getShape(shp, pt, r, elem.getSizeFrame() != null);
      double prefw = getVOTextWidth(vtext, vgraph, gobj);
      double prefh = vtext.getPreferredHeight();
      Point2D rpt = pt;
      double gap = (shape == null) ? 0 : TEXT_GAP;

      vtext.setSize(new DimensionD(prefw, prefh));
      r = (shape == null) ? 0 : shape.getBounds().getHeight() / 2;

      double tx, ty;

      switch(placement) {
      case GraphConstants.BOTTOM:
         tx = pt.getX() - prefw / 2;
         ty = pt.getY() - r - gap - prefh;
         rpt = new Point2D.Double(pt.getX(), pt.getY() - r);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         tx = pt.getX() - prefw - r - gap;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX() - r, pt.getY());
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         tx = pt.getX() + r + gap;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX() + r, pt.getY());
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         break;
      case GraphConstants.CENTER:
         tx = pt.getX() - prefw / 2;
         ty = pt.getY() - prefh / 2;
         rpt = new Point2D.Double(pt.getX(), pt.getY());
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.AUTO:
      case GraphConstants.TOP:
      default:
         tx = pt.getX() - prefw / 2;

         // if nil shape, center text at the y point location by default.
         // x is controlled by placement
         if(shape == null && placement == GraphConstants.AUTO) {
            ty = pt.getY() - prefh / 2;
            placement = GraphConstants.CENTER;
         }
         else {
            ty = pt.getY() + r + gap;
            // auto means top in point, should set for rotation offset
            placement = GraphConstants.TOP;
         }

         rpt = new Point2D.Double(pt.getX(), pt.getY() + r);
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      }

      vtext.setPosition(new Point2D.Double(tx, ty));

      Point2D offset = vtext.getRotationOffset(rpt, placement);
      vtext.setOffset(offset);
      vtext.setPlacement(placement);

      return lvgraph;
   }

   /**
    * apply the the point effect for point chart.
    * @param g - the awt graphics
    * @param x - the x coord
    * @param y - the y coord
    * @param size - The point size
    */
   private static void applyEffect(Graphics2D g, double x, double y, double size, double alpha) {
      double s = size;
      Color color = g.getColor();
      Color gradColor = color.brighter();

      color = GTool.getColor(color, alpha);
      gradColor = GTool.getColor(gradColor, alpha);

      try {
         GradientPaint paint = new GradientPaint((float) (x + s),
						 (float) (y - s), color,
                                                 (float) (x - s),
                                                 (float) (y + s), gradColor);
         g.setPaint(paint);
      }
      catch(Exception e) {
         // do nothing
      }
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public void transform(Coordinate coord) {
      loc = (Point2D) coord.transformShape(loc);
   }

   /**
    * Get the point position.
    */
   public Point2D getPosition() {
      return loc;
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      GraphElement elem = ((ElementGeometry) getGeometry()).getElement();
      Point2D pt = getScreenTransform().transform(loc, null);
      return callShape(getShape(), elem.getSizeFrame() != null, s -> s.getBounds(pt, radius));
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      return radius;
   }

   /**
    * Move the visual objects to avoid overlapping.
    * @param comap collision map, from the first visual object to a list (List)
    * of overlapping visual objects.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public void dodge(Map<ElementVO, List<ElementVO>> comap, Coordinate coord) {
      PointElement elem = (PointElement) ((ElementGeometry) getGeometry()).getElement();
      List<ElementVO> vos = comap.get(this);

      if(vos.size() == 0) {
         return;
      }

      if((elem.getCollisionModifier() & GraphElement.MOVE_JITTER) != 0) {
         jitter(vos, coord);
         return;
      }

      if((elem.getCollisionModifier() & GraphElement.MOVE_DODGE) == 0 &&
         (elem.getCollisionModifier() & GraphElement.MOVE_STACK) == 0)
      {
         return;
      }

      // !!! dodge is called in createVisual, before the fit(), so the position
      // is in logic space. Since point has fixed (unscaled) size, the position
      // set in dodge needs to take into consideration of the scaling factor.
      // But at this point we don't even know the actual output size of the
      // graph. We use a ratio of 3 as an approximation, and re-adjust the
      // locations in layoutCompleted when the precise size is known.
      double wratio = 3;
      double hratio = 3;
      Rectangle2D box = getBounds();
      double x = box.getX() + (box.getWidth() + 1) * wratio / 2;
      double y = box.getY() + (box.getHeight() + 1) * hratio / 2;

      dodged = new ArrayList(); // for layoutCompleted
      dodged.add(this);

      for(int i = 0; i < vos.size(); i++) {
         PointVO point = (PointVO) vos.get(i);

         dodged.add(point);

         if((elem.getCollisionModifier() & GraphElement.MOVE_STACK) != 0) {
            double ph = (point.getBounds().getHeight() + 1) * hratio;

            point.loc = new Point2D.Double(point.loc.getX(), y + ph / 2);
            y += ph;
         }
         else { // dodge
            double pw = (point.getBounds().getWidth() + 1) * wratio;

            point.loc = new Point2D.Double(x + pw / 2, point.loc.getY());
            x += pw * wratio;
         }
      }

      // center points, we always center on x (same as interval)
      if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
         double shiftx = 0;

         double x1 = loc.getX();
         double x2 = dodged.get(dodged.size() - 1).loc.getX();
         shiftx = (x2 - x1) / 2;

         for(PointVO point : dodged) {
            point.loc = new Point2D.Double(point.loc.getX() - shiftx,
                                           point.loc.getY());
         }
      }
   }

   /**
    * Jitter overlapped points.
    */
   private void jitter(List<ElementVO> vos, Coordinate coord) {
      List<PointVO> list = new ArrayList<>();
      int MAX_DIST = Math.min(Math.max(80, vos.size() / 2), vos.size() * 8);
      MAX_DIST = Math.min(MAX_DIST, 250);
      Random rand = new Random(vos.size());

      list.add(this);

      for(Object vo : vos) {
         list.add((PointVO) vo);
      }

      for(PointVO point : list) {
         double angle = rand.nextDouble() * 2 * Math.PI;
         double dist = rand.nextDouble() * MAX_DIST;
         double x = point.loc.getX() + Math.cos(angle) * dist;
         double y = point.loc.getY() + Math.sin(angle) * dist;

         point.loc = new Point2D.Double(x, y);
      }
   }

   /**
    * Check if this element needs to dodge from overlapping.
    */
   @Override
   public boolean requiresDodge() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      PointElement elem = (PointElement) gobj.getElement();
      int collision = gobj.getElement().getCollisionModifier();

      // text layout processed separately for word cloud
      return !elem.isWordCloud() &&
         ((collision & GraphElement.MOVE_JITTER) != 0 ||
          (collision & GraphElement.MOVE_DODGE) != 0 ||
          (collision & GraphElement.MOVE_STACK) != 0);
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

      double x = b2.getX();
      double y = b2.getY();
      double w = Math.max(0, b2.getWidth() - 0.5);
      double h = Math.max(0, b2.getHeight() - 0.5);
      x += x >= b.getX() ? 0.5 : -0.5;
      y += y >= b.getY() ? 0.5 : -0.5;

      return b.intersects(x, y, w, h);
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      double textw = vtext == null ? 0 : vtext.getPreferredWidth();
      return Math.max(textw, radius * 2 + 2);
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
      Point2D pos = getScreenTransform().transform(loc, null);
      GShape shp = getShape();
      ElementGeometry gobj = (ElementGeometry) getGeometry();

      Shape point = getShape(shp, pos, radius, gobj.getElement().getSizeFrame() != null);

      // NIL returns null for shape
      return (point != null) ? new Shape[] {point} : new Shape[0];
   }

   /**
    * Get the shape for this point. Use default if shape frame not set.
    */
   private GShape getShape() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GShape shp = gobj.getShape(0);

      if(shp == null) {
         shp = GShape.CIRCLE;
      }

      return shp;
   }

   /**
    * Re-adjust point location if this has been dodged with other points.
    */
   @Override
   public void layoutCompleted(Coordinate coord) {
      loc = new Point2D.Float((float) loc.getX(), (float) loc.getY()); // optimization

      if(dodged == null || dodged.size() == 0 || !(coord instanceof RectCoord)) {
         return;
      }

      PointGeometry obj = (PointGeometry) getGeometry();
      GraphElement elem = obj.getElement();
      boolean hor = GTool.isHorizontal(coord.getCoordTransform());

      if((elem.getCollisionModifier() & GraphElement.MOVE_STACK) != 0) {
         hor = !hor;
      }

      // see comments in dodge() for why we need to adjust the position here

      Rectangle2D bounds = getBounds();
      Point2D lastp = dodged.get(dodged.size() - 1).loc;
      double x1 = hor ? bounds.getX() : bounds.getY();
      double w1 = hor ? bounds.getWidth() : bounds.getHeight();

      for(int i = 1; i < dodged.size(); i++) {
         Rectangle2D bounds2 = dodged.get(i).getBounds();
         double w2 = hor ? bounds2.getWidth() : bounds2.getHeight();
         Point2D loc2 = hor
            ? new Point2D.Float((float) (x1 + w1 + w2 / 2),
                                (float) (bounds2.getY() + bounds2.getHeight() / 2))
            : new Point2D.Float((float) (bounds2.getX() + bounds2.getWidth() / 2),
                                (float) (x1 + w1 + w2 / 2));

         try {
            dodged.get(i).loc = getScreenTransform().inverseTransform(loc2, null);
         }
         catch(Exception ex) {
            LOG.debug("Failed to apply transformation: " + loc2, ex);
         }

         x1 += w1;
         w1 = w2;
      }

      // re-center if adjusted
      if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0 && hor) {
         Point2D lastp2 = dodged.get(dodged.size() - 1).loc;
         double shiftx = (lastp2.getX() - lastp.getX()) / 2;

         for(PointVO point : dodged) {
            point.loc = new Point2D.Float((float) (point.loc.getX() - shiftx),
                                          (float) point.loc.getY());
         }
      }

      dodged = null; // no-longer needed
   }

   @Override
   public void flip(VOText votext, double by) {
      Rectangle2D voBounds = getBounds();
      Rectangle2D bounds = votext.getBounds();

      double y = bounds.getY() - (bounds.getHeight() + voBounds.getHeight() + TEXT_GAP) * by;
      votext.setPosition(new Point2D.Double(bounds.getX(), y));
      votext.setPlacement(GraphConstants.BOTTOM);
   }

   @Override
   public PointVO clone() {
      PointVO obj = (PointVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private Point2D loc;
   private VOText vtext;
   private VGraph lvgraph;
   private short radius;
   // list of vos dodged with this point
   private List<PointVO> dodged;

   private static final Logger LOG = LoggerFactory.getLogger(PointVO.class);
}

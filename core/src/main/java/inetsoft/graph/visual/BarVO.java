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
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.util.CoreTool;
import net.jafama.FastMath;

import java.awt.*;
import java.awt.geom.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.Array;
import java.util.List;
import java.util.*;

import static inetsoft.graph.coord.Coordinate.GHEIGHT;

/**
 * This visual object represents a bar in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class BarVO extends ElementVO {
   /**
    * Create a visual object at 0,0 location.
    * @param coord the coordinate the visual object is plotted on.
    */
   public BarVO(Geometry gobj, Coordinate coord) {
      super(gobj);
      this.coord = coord;

      IntervalGeometry geom = (IntervalGeometry) gobj;
      IntervalElement elem = (IntervalElement) geom.getElement();
      double interval = geom.getInterval();
      Point2D loc = coord.getPosition(geom.getTuple());
      double size = geom.getSize(0);
      GShape gshape = getGShape();
      double height = coord.getIntervalSize(interval);
      double width = getBarSize(coord, size);
      Object label = geom.getText(0);

      if(height == 0 && interval == 0) {
         height = elem.getZeroHeight();
      }

      // if shape is not filled, make sure the edge is not covered by axis. (58343)
      if(gshape != null && !gshape.isFill()) {
         setZIndex(GDefaults.VO_OUTLINE_Z_INDEX);
      }

      has_border = geom.getTexture(0) != null || geom.getLine(0) != null ||
         elem.getBorderColor() != null;
      negative = height < 0;

      // one scale is null?
      if(isRect1(coord)) {
         loc = new Point2D.Double(coord.getMaxWidth() / 2, loc.getY());
      }

      shape = gshape.getShape(loc.getX(), loc.getY(), width, height);

      // leave shape at tick position if no move (for histogram)
      if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
         shape = GTool.centerShape(shape, true, width, height);
      }

      geomShape = shape;

      if(label != null) {
         Object stackLabel = null;

         if(label.getClass().isArray()) {
            int len = Array.getLength(label);

            if(len == 2 && elem.isStack() && !isPolar() &&
               // don't move label to the side for funnel.
               elem.getLabelPlacement() != GraphConstants.CENTER)
            {
               stackLabel = Array.get(label, 1);
               label = Array.get(label, 0);
            }
            else if(len == 1) {
               label = Array.get(label, 0);
            }
         }

         if(label != null) {
            if(isPolar() && elem.getLabelPlacement() == GraphConstants.CENTER) {
               vtext = new CircularVOText(label, this, getMeasureName(), coord.getDataSet(),
                                     geom.getSubRowIndex(), geom);
            }
            else if(isPolar()) {
               vtext = new ArcVOText(label, this, getMeasureName(), coord.getDataSet(),
                                     geom.getSubRowIndex(), geom);
            }
            else {
               vtext = new VOText(label, this, getMeasureName(), coord.getDataSet(),
                                  geom.getSubRowIndex(), geom);
            }

            vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }

         if(stackLabel != null) {
            stackText = new VOText(stackLabel, this, getMeasureName(), coord.getDataSet(),
                                   geom.getSubRowIndex(), geom);

            stackText.setValueText(true);
            stackText.setStacked(true);
            stackText.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            stackText.setAlignmentY(GraphConstants.TOP_ALIGNMENT);
         }

         if(elem.getTextFrame() instanceof ValueTextFrame &&
            // if text is already combined, don't apply the message format again. (61335)
            !((ValueTextFrame) elem.getTextFrame()).isTextCombined() &&
            label != null && !label.getClass().isArray())
         {
            ValueTextFrame frame = (ValueTextFrame) elem.getTextFrame();
            vtext.setTextSpec(frame.applyTextFormat(vtext.getTextSpec()));

            if(stackText != null) {
               stackText.setTextSpec(frame.applyValueFormat(stackText.getTextSpec()));
            }
         }
      }
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    * @param coord the visual object's coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      Object over = overShape == null ? null : coord.transformShape(overShape);
      shape = (Shape) explode(coord.transformShape(shape), this, over);
      Arc2D arc = getOuterArc(shape);

      if(arc != null && arc.getAngleStart() == 0 && Math.abs(arc.getAngleExtent()) == 360) {
         if(vtext instanceof ArcVOText) {
            VGraph vgraph = coord.getVGraph();
            IntervalGeometry gobj = (IntervalGeometry) getGeometry();

            vgraph.removeVisual(vtext);
            vtext = new VOText(vtext.getLabel(), vtext.getElementVO(), vtext.getMeasureName(),
                               coord.getDataSet(), gobj.getSubRowIndex(), gobj);
            vgraph.addVisual(vtext);
         }
      }
   }

   /**
    * Get the shape of the bar.
    */
   protected Shape getPath() {
      return getPath0(false);
   }

   /**
    * Get the bar shape.
    * @param paint true if for painting.
    */
   Shape getPath0(boolean paint) {
      CachedShape cachedShape = this.cachedShape.get();
      Shape path0 = cachedShape != null ? cachedShape.path : null;
      AffineTransform trans0 = cachedShape != null ? cachedShape.trans : null;

      if(path0 == null || !getScreenTransform().equals(trans0)) {
         path0 = shape;
         trans0 = (AffineTransform) getScreenTransform().clone();
         Rectangle2D box = path0.getBounds2D();

         // reserve 1 pt between bars
         if(paint && box.getWidth() > 3 && getOuterArc(path0) == null) {
            AffineTransform trans = new AffineTransform();
            double cx = box.getX() + box.getWidth() / 2;
            double one = 1 * GTool.getScaleFactor(trans0, 0);

            trans.translate(cx, box.getY());
            trans.scale((box.getWidth() - one) / box.getWidth(), 1);
            trans.translate(-cx, -box.getY());

            path0 = trans.createTransformedShape(path0);
            trans0.concatenate(trans);
         }

         path0 = transformShape(path0, getScreenTransform());
         this.cachedShape = new SoftReference<>(new CachedShape(trans0, path0));
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
      Shape path = getPath0(true);
      Rectangle2D box = path.getBounds2D();
      boolean horizontal = GTool.isHorizontal(getScreenTransform());
      boolean fraction = horizontal && (box.getWidth() < 4.5 || box.getHeight() < 1) ||
         !horizontal && (box.getHeight() < 4.5 || box.getWidth() < 1);
      // optimization, only create new graphics for pdf, more efficient for pdf and less
      // efficient for svg
      boolean createG = g instanceof PDFDevice;

      if(createG) {
         g = (Graphics2D) g.create();
      }

      // fraction (<1) shape may not show up without antialiasing.
      // also if the bar is too narrow, no anti-alias would cause the bars to
      // appear to be different size
      if(getOuterArc(path) != null || fraction) {
         g.setRenderingHint(GHints.CURVE, "true");
      }

      Stroke ostroke = g.getStroke();
      Paint opaint = g.getPaint();

      paintBar(g, path, color, shape, fill, line, borderColor);

      if(createG) {
         g.dispose();
      }
      else {
         g.setStroke(ostroke);
         g.setPaint(opaint);
      }
   }

   /**
    * Paint the bar shape.
    */
   protected void paintBar(Graphics2D g2, Shape path, Color color,
                           GShape shape, GTexture fill, GLine line, Color lineColor)
   {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      IntervalElement elem = (IntervalElement) gobj.getElement();
      boolean textured = fill != null;
      boolean applyEffect = "true".equals(elem.getHint(GraphElement.HINT_SHINE));
      Rectangle2D bounds = path.getBounds2D();
      boolean smallInterval = elem.getBaseVar(0) != null &&
         (bounds.getHeight() < 1 && bounds.getHeight() > 0 ||
            bounds.getWidth() < 1 && bounds.getWidth() > 0);

      color = applyAlpha(color);
      g2.setColor(color);

      if(line != null) {
         g2.setStroke(line.getStroke());
      }

      if(!textured) {
         shape.paint(g2, path);
      }
      else {
         fill.paint(g2, path);
      }

      if(applyEffect && fill == null) {
         applyEffect(g2, path, color);
      }

      if(line != null && shape.isFill()) {
         paintLine(g2, path, line, lineColor != null ? lineColor : color.darker());
      }
      // force a border if texture is used otherwise the shape looks incomplete and distorted.
      // if interval is very small, force it to show or it would appear missing.
      else if(textured || smallInterval || lineColor != null) {
         paintLine(g2, path, new GLine(1), lineColor != null ? lineColor : color);
      }
   }

   /**
    * Paint a line around the shape.
    */
   static void paintLine(Graphics2D g2, Shape path, GLine line, Color color) {
      g2 = (Graphics2D) g2.create();
      g2.setColor(color != null ? color : g2.getColor().darker());

      if(line != null) {
         g2.setStroke(line.getStroke());
      }

      g2.draw(path);
      g2.dispose();
   }

   /**
    * Apply the gradual effect.
    */
   private void applyEffect(Graphics2D g2d, Shape path, Color fill) {
      Arc2D arc = getOuterArc(path);
      Shape clip = g2d.getClip();

      if(arc != null) {
         Ellipse2D ellipse = new Ellipse2D.Double(arc.getX(), arc.getY(),
            arc.getWidth(), arc.getHeight());
         Rectangle2D rect = ellipse.getBounds2D();
         double x = rect.getX();
         double y = rect.getY();
         double width = rect.getWidth();
         double height = rect.getHeight();

         g2d.clip(path);

         // clear -> partial transparent white
         Color col1 = new Color(255, 255, 255, 0);
         Color col2 = new Color(255, 255, 255, 180);

         // top left gradient
         GradientPaint paint1 = new GradientPaint(
            (float) (x + width), (float) y, col2, (float) (x + 2 * width / 3),
            (float) (y + height / 3), col1);

         g2d.setPaint(paint1);
         g2d.fill(path);

         // bottom right gradient
         GradientPaint paint2 = new GradientPaint(
            (float) x, (float) (y + height), col2, (float) (x + width / 3),
            (float) (y + 2 * height / 3), col1);

         g2d.setPaint(paint2);
         g2d.fill(path);
      }
      else {
         Rectangle2D rect = path.getBounds2D();
         double x = rect.getX();
         double y = rect.getY();
         double width = rect.getWidth();
         double height = Math.max(0, rect.getHeight());

         // @by larryl, jdk1.6 draw a little shiny line for height == 0
         if(height > 0) {
            // clear -> partial transparent white
            Color col1 = new Color(255, 255, 255, 0);
            Color col2 = new Color(255, 255, 255, 120);

            g2d.clip(path);
            fillGradient(g2d, x, y, width, height, col1, col2);
         }
      }

      g2d.setClip(clip);
   }

   /**
    * Create a gradient paint including the transformation.
    */
   private void fillGradient(Graphics2D g, double x, double y, double width,
                             double height, Color c1, Color c2)
   {
      double x1, y1, x2, y2;

      if(GTool.isHorizontal(getScreenTransform())) {
         y1 = y + height / 2;
         y2 = y + height / 2;
         x1 = x;
         x2 = x + width / 2;

         GradientPaint paint = new GradientPaint((float) x1 - 1, (float) y1, c2,
                                                 (float) x2, (float) y2, c1);
         g.setPaint(paint);
         // g is clipped by shape. we enlarge the area to make sure the entire
         // shape is covered by paint
         g.fill(new Rectangle2D.Double(x - 1, y - 1, width / 2 + 3, height + 3));
      }
      else {
         x1 = x + width / 2;
         x2 = x + width / 2;
         y1 = y + height;
         y2 = y + height / 2;

         GradientPaint paint = new GradientPaint((float) x1, (float) y1 + 1, c2,
                                                 (float) x2, (float) y2, c1);
         g.setPaint(paint);
         // see above
         g.fill(new Rectangle2D.Double(x - 1, y + height / 2 - 1, width + 3,
                                       height / 2 + 3));
      }
   }

   /**
    * Get the visual object's text.
    * @return the VLabel of this visual object.
    */
   @Override
   public VOText[] getVOTexts() {
      return (stackText != null)
         ? new VOText[] {vtext, stackText} : new VOText[] {vtext};
   }

   /**
    * Layout text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      if(vtext != null) {
         vgraph.removeVisual(vtext);
         vgraph.addVisual(vtext);
      }

      if(stackText != null) {
         vgraph.removeVisual(stackText);
         vgraph.addVisual(stackText);
      }

      Shape shape = getPath();
      Arc2D arc = getOuterArc(shape);
      ElementGeometry gobj = (ElementGeometry) getGeometry();

      if(arc != null) {
         if(vtext != null) {
            layoutTextArc(vgraph, arc, shape);
         }
      }
      else {
         int placement = gobj.getLabelPlacement();

         if(placement == GraphConstants.AUTO) {
            placement = getAutoPlacement(false);
         }

         if(vtext != null) {
            layoutText0(vgraph, placement, shape.getBounds2D(), negative, vtext);
         }

         if(stackText != null) {
            placement = getAutoPlacement(true);
            layoutText0(vgraph, placement, shape.getBounds2D(), negative, stackText);
         }
      }
   }

   /**
    * Layout label on an arc.
    */
   private void layoutTextArc(VGraph vgraph, Arc2D arc, Shape slice) {
      double w = arc.getWidth();
      double h = arc.getHeight();
      double cx = arc.getX() + w / 2;
      double cy = arc.getY() + h / 2;
      double wr = (w / 2);
      double hr = (h / 2);
      double angle = -Math.toRadians(arc.getAngleStart() + arc.getAngleExtent() / 2);

      Rectangle2D bounds = vgraph.getPlotBounds();
      double plotw = bounds.getWidth();
      double ploth = bounds.getHeight();
      double plotRadius = ((PolarCoord) coord).getRadius();
      boolean moveable = ((PolarCoord) coord).isLabelMoveable();

      double textw = PolarUtil.getPieLabelWidth((int) plotw, plotRadius, angle, vtext, moveable);
      double texth = PolarUtil.getPieLabelHeight((int) ploth, plotRadius, angle, vtext);
      int placement = ((ElementGeometry) getGeometry()).getElement().getLabelPlacement();
      boolean autoCenter = placement == GraphConstants.AUTO ||
         placement == GraphConstants.CENTER ||
         placement == GraphConstants.CENTER_FILL;

      if(vtext instanceof CircularVOText) {
         double r = Math.min(wr, hr);
         Arc2D inner = slice instanceof Donut ? ((Donut) slice).getInnerArc() : null;
         double innerR = inner != null ? Math.min(inner.getWidth(), inner.getHeight()) / 2 : r / 2;
         ((CircularVOText) vtext).setCircle(cx, cy, r, innerR,
                                            Math.toRadians(arc.getAngleStart()),
                                            Math.toRadians(arc.getAngleExtent()));
      }
      // center label, changed to VOText in transform().
      // only place label in middle for center/auto placement, or the label of outer
      // pie would collide with middle label in donut chart. (57561)
      else if(autoCenter && arc.getAngleStart() == 0 && arc.getAngleExtent() == 360) {
         if(placement == GraphConstants.CENTER_FILL) {
            vtext.setFont(PolarUtil.fillCircle(Math.min(wr, hr), vtext.getText(),
                                               vtext.getFont(), 24));
            vtext.invalidate();
         }

         double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
         double prefh = vtext.getPreferredHeight();
         vtext.setPosition(new Point2D.Double(cx - prefw / 2, cy - prefh / 2));
         vtext.setSize(new DimensionD(prefw, prefh));
         vtext.setCollisionModifier(VLabel.MOVE_NONE);
      }
      else {
         if(!(vtext instanceof ArcVOText)) {
            vgraph.removeVisual(vtext);
            vtext = new ArcVOText(vtext.getLabel(), vtext.getElementVO(),
                                  vtext.getMeasureName(), coord.getDataSet(),
                                  getSubRowIndex(), getGeometry());
            vgraph.addVisual(vtext);
         }

         vtext.setSize(new Dimension((int) textw, (int) texth));

         ArcVOText vtext2 = (ArcVOText) vtext;
         vtext2.setArc(cx, cy, wr, hr, 0);
         vtext2.layout(angle);
         vtext2.setExtent(Math.toRadians(arc.getAngleExtent()));
      }
   }

   /**
    * Layout text around a rectangle.
    */
   void layoutText0(VGraph vgraph, int placement, Rectangle2D box,
                    boolean negative, VOText vtext)
   {
      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = vtext.getPreferredHeight();
      double tx, ty;
      double midx, midy;
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();

      switch(placement) {
      case GraphConstants.BOTTOM:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY();
         tx = midx - prefw / 2;
         ty = midy - prefh - TEXT_GAP;
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
      case GraphConstants.CENTER:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight() / 2;
         tx = midx - prefw / 2;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);

         if(GTool.isHorizontal(getScreenTransform())) {
            vtext.setCollisionModifier(VLabel.MOVE_UP);
         }
         else {
            vtext.setCollisionModifier(VLabel.MOVE_RIGHT);
         }

         // text in middle of bar, support auto text color
         if(elem.isAutoTextColor()) {
            vtext.setAutoBackground(gobj.getColor(0));
         }
         break;
      case GraphConstants.TOP:
      default:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight();
         tx = midx - prefw / 2;
         ty = midy + TEXT_GAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         vtext.setCollisionModifier(VLabel.MOVE_UP);
         break;
      }

      vtext.setPosition(new Point2D.Double(tx, ty));
      vtext.setSize(new DimensionD(prefw, prefh));
      vtext.setPlacement(placement);

      Point2D pt = new Point2D.Double(midx, midy);
      Point2D offset = vtext.getRotationOffset(pt, placement);
      vtext.setOffset(offset);
   }

   /**
    * Get the auto label placement according to transformation.
    */
   private int getAutoPlacement(boolean stack) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      IntervalElement elem = (IntervalElement) gobj.getElement();
      boolean horizontal = GTool.isHorizontal(getScreenTransform());

      // don't place label in middle of waterfall bars.
      if(elem.isVisualStacked() == null || elem.isVisualStacked()) {
         if(elem.isStack() && !(elem.getTextFrame() instanceof StackTextFrame || stack)) {
            return GraphConstants.CENTER;
         }
      }

      if(horizontal) {
         return negative ? GraphConstants.BOTTOM : GraphConstants.TOP;
      }

      return negative ? GraphConstants.LEFT : GraphConstants.RIGHT;
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      Rectangle2D box = getPath().getBounds2D();

      // if borders exists, there is 0.5 pt (half of a line) outside of the area
      if(has_border && !box.isEmpty()) {
         box = new Rectangle2D.Double(box.getX() - 0.5, box.getY() - 0.5,
                                      box.getWidth() + 1, box.getHeight() + 1);
      }

      return box;
   }

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      if(!has_border) {
         return 0;
      }

      return 0.5;
   }

   /**
    * Get max width.
    */
   private double getMaxWidth(List vos) {
      double max = 0;

      for(int i = 0; i < vos.size(); i++) {
         BarVO vo = (BarVO) vos.get(i);
         max = Math.max(vo.getGeomShape().getBounds2D().getWidth(), max);
      }

      return max;
   }

   /**
    * Move the visual objects to avoid overlapping.
    * @param comap collision map, from the first visual object to a list (List)
    * of overlapping visual objects.
    * @param coord the coordinate the visual object is plotted on.
    */
   @Override
   public void dodge(Map<ElementVO,List<ElementVO>> comap, Coordinate coord) {
      GraphElement elem = ((ElementGeometry) getGeometry()).getElement();
      Rectangle2D rect = shape.getBounds2D();
      List<ElementVO> vos = comap.get(this);
      // item in stackbars is a list of all stacked bars for a var/element
      // stackbars.size() is the number of stacks
      List<List<ElementVO>> stacks = getStackBars(vos);
      double factor = 1;

      // for funnel
      if((elem.getCollisionModifier() & GraphElement.MOVE_MIDDLE) != 0) {
         for(List<ElementVO> stack : stacks) {
            double totalh = stack.stream().mapToDouble(a -> a.getBounds().getHeight()).sum();
            this.cachedShape.clear();

            for(ElementVO elementVO : stack) {
               BarVO bar = (BarVO) elementVO;
               bar.shape = GTool.move(bar.shape, 0, (GHEIGHT - totalh) / 2);
               bar.cachedShape.clear();
            }
         }

         return;
      }

      // get max total width of all bars that will be dodged and calculate the
      // scale factor so the width are reduced to prepare for dodging
      if((elem.getCollisionModifier() & GraphElement.MOVE_DODGE) != 0) {
         Double factorObj = (Double) elem.getHint("_dodge_factor_");

         if(factorObj != null) {
            factor = factorObj;
         }
         else {
            double maxbar = getMaxBarSize(coord);

            for(Visualizable visual : comap.keySet()) {
               if(!(visual instanceof BarVO)) {
                  continue;
               }

               BarVO vo = (BarVO) visual;
               double totalw = 0;

               List<ElementVO> overlapped = comap.get(vo);
               List<List<ElementVO>> stacks2 = vo.getStackBars(overlapped);

               for(int j = 0; j < stacks2.size(); j++) {
                  totalw += getMaxWidth(stacks2.get(j));
               }

               double factor2 = Math.min(1, maxbar / totalw);
               factor = Math.min(factor, factor2);
            }

            // optimization, remember it to avoid calculating it for every
            // stack, which results in n^2 iterations
            elem.setHint("_dodge_factor_", factor);
         }
      }

      double cx = rect.getX() + rect.getWidth() / 2;
      double x0 = rect.getX();
      double totalw2 = 0;

      // shift to the right to unwind the bar
      for(int i = 0; i < stacks.size(); i++) {
         List<ElementVO> stack = stacks.get(i);
         double barw = 0;

         for(int j = 0; j < stack.size(); j++) {
            BarVO bar = (BarVO) stack.get(j);
            Rectangle2D rect2 = shape.getBounds2D();
            Shape shape2 = bar.shape;

            shape2 = GTool.scale(shape2, factor, 1);
            rect2 = shape2.getBounds2D();
            shape2 = GTool.move(shape2, x0 - rect2.getX(), 0);
            bar.setShape(shape2);
            barw = Math.max(barw, rect2.getWidth());
         }

         // center bar
         for(int j = 0; j < stack.size(); j++) {
            BarVO bar = (BarVO) stack.get(j);
            Rectangle2D rect2 = bar.shape.getBounds2D();
            bar.shape = GTool.move(bar.shape, (barw - rect2.getWidth()) / 2, 0);
         }

         x0 += barw;
         totalw2 += barw;
      }

      // center if symmetric
      if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
         x0 = cx - totalw2 / 2; // left most after centered
         double offset = x0 - rect.getX();

         for(int i = 0; i < stacks.size(); i++) {
            List<ElementVO> stack = stacks.get(i);

            for(int j = 0; j < stack.size(); j++) {
               BarVO bar = (BarVO) stack.get(j);
               bar.setShape(GTool.move(bar.shape, offset, 0));
            }
         }
      }
   }

   /**
    * Check if this element needs to dodge from overlapping.
    */
   @Override
   public boolean requiresDodge() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();
      int mask = GraphElement.MOVE_DODGE | GraphElement.MOVE_MIDDLE;

      // do not dodge if dodge is not required, e.g. pie
      return (elem.getCollisionModifier() & mask) != 0 && !isPolar();
   }

   /**
    * Check if dodging is required.
    */
   @Override
   public boolean requiresDodge(ElementVO vo) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GraphElement elem = gobj.getElement();

      // dodge() needs to be called on self for middle (funnel).
      if(vo == this) {
         return (elem.getCollisionModifier() & GraphElement.MOVE_MIDDLE) != 0;
      }

      // only dodge for vo from same element type, e.g. interval and interval
      if(!getClass().equals(vo.getClass())) {
         return false;
      }

      // optimization, since overlapping is not impacted by scaling, we could
      // use the untransformed shape safely here
      Rectangle2D b = shape.getBounds2D();
      Rectangle2D b2 = ((BarVO) vo).shape.getBounds2D();

      if(!intersects(b.getX(), b.getWidth(), b2.getX(), b2.getWidth())) {
         return false;
      }

      if(!CoreTool.equals(getHint("overlay"), vo.getHint("overlay"))) {
         return false;
      }

      return true;
   }

   /**
    * Check if bars intersect.
    */
   private boolean intersects(double x, double w, double x2, double w2) {
      double adj = 0.5;

      if(has_border) {
         adj += 1;
      }

      return x == x2 || x < x2 && x + w > x2 + adj ||
         x2 < x && x2 + w2 > x + adj;
   }

   /**
    * Split the bar vos into stacks.
    */
   private List<List<ElementVO>> getStackBars(List<ElementVO> vos) {
      List<List<ElementVO>> stacks = new ArrayList();
      List<ElementVO> stack = new ArrayList();
      GraphElement elem = ((ElementGeometry) getGeometry()).getElement();

      stacks.add(stack);
      stack.add(this);

      for(ElementVO vo : vos) {
         GraphElement elem2 = ((ElementGeometry) vo.getGeometry()).getElement();
         // check if this bar is in a new group
         boolean newgroup = elem != elem2 || !elem.isStack();

         if(newgroup) {
            stacks.add(stack = new ArrayList());
         }

         stack.add(vo);
         elem = elem2;
      }

      return stacks;
   }

   /**
    * Get min width of this vo.
    * @return min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      double textw = vtext == null ? 0 : vtext.getMinWidth();
      return Math.max(BAR_MIN_WIDTH, textw);
   }

   /**
    * Get preferred width of this vo.
    * @return preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      double textw = vtext == null ? 0 : vtext.getPreferredWidth();
      return Math.max(BAR_PREFERRED_WIDTH, textw);
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      return new Shape[] {getPath()};
   }

   /**
    * Get the bar shape.
    */
   public Shape getShape() {
      return shape;
   }

   /**
    * Get geometry shape that is not processed by dodge and other transform.
    */
   private Shape getGeomShape() {
      return geomShape;
   }

   /**
    * Set shape.
    */
   private void setShape(Shape shape) {
      this.shape = shape;
      cachedShape.clear(); // clear cached path
   }

   /**
    * Get the gshape for this bar.
    */
   private GShape getGShape() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      GShape shape = gobj.getShape(0);

      return (shape == null || shape == GShape.NIL) ? GShape.FILLED_SQUARE : shape;
   }

   /**
    * Size factor for bar is the maximum width of the bar after transformation.
    */
   @Override
   public double getSizeFactor() {
      Point2D p1 = new Point2D.Double(0, 0);
      Point2D p2 = new Point2D.Double(coord.getMaxWidth(), 0);

      p1 = getScreenTransform().transform(p1, null);
      p2 = getScreenTransform().transform(p2, null);

      return p1.distance(p2);
   }

   /**
    * Scale the shape to the specified size factor.
    */
   @Override
   public void scaleToSizeFactor(double factor) {
      Arc2D arc = getOuterArc(shape);

      // if this is a circular shape, its side should not be scaled since they
      // are already uniform as the size of the plot space (polar).
      if(arc != null) {
         return;
      }

      double myfactor = getSizeFactor();

      if(myfactor == factor) {
         return;
      }

      double angle = GTool.getRotation(getScreenTransform());
      AffineTransform trans = AffineTransform.getRotateInstance(-angle);
      Shape path2 = transformShape(getPath(), trans);
      Rectangle2D box = path2.getBounds2D();

      trans = new AffineTransform();
      trans.translate(box.getX(), box.getY() + box.getHeight() / 2);
      trans.scale(factor / myfactor, 1);
      trans.translate(-box.getX(), -box.getY() - box.getHeight() / 2);
      path2 = transformShape(path2, trans);

      trans = AffineTransform.getRotateInstance(angle);

      CachedShape cachedShape = this.cachedShape.get();

      if(cachedShape != null) {
         cachedShape.path = transformShape(path2, trans);
      }
   }

   /**
    * Calculate the bar size.
    */
   protected double getBarSize(Coordinate coord, double size) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      SizeFrame frame = gobj.getVisualModel().getSizeFrame();
      double maxbar = coord.getMaxWidth();

      if(frame == null) {
         return isPolar() ? maxbar : Math.max(1, maxbar / 2);
      }

      double largest = frame.getLargest();
      double smallest = frame.getSmallest();
      double max = frame.getMax();

      if(largest == smallest) {
         return Math.max(1, maxbar / 2);
      }

      double minwidth = maxbar * smallest / max;
      double w = maxbar - minwidth;

      return minwidth + w * size / max;
   }

   /**
    * Calculate the bar size.
    */
   private double getMaxBarSize(Coordinate coord) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      SizeFrame frame = gobj.getVisualModel().getSizeFrame();

      if(frame instanceof StaticSizeFrame) {
         return getBarSize(coord, ((StaticSizeFrame) frame).getSize());
      }

      return coord.getMaxWidth();
   }

   /**
    * Make the visual object overlay another visual object.
    * @param vo the specified visual object to be overlaid.
    */
   @Override
   public void overlay(VisualObject vo) {
      Rectangle2D bounds0 = ((ElementVO) vo).getShapes()[0].getBounds2D();
      Rectangle2D bounds = shape.getBounds2D();
      // make the overlay vo thiner than the base vo
      double width = Math.min(bounds0.getWidth() * getOverlayRatio(), bounds.getWidth());
      double x = isPolar() ? bounds0.getX() : bounds0.getX() + (bounds0.getWidth() - width) / 2;
      double y = bounds0.getY();

      if(negative) {
         y = bounds0.getY() + bounds0.getHeight() - bounds.getHeight();
      }

      double height = bounds.getHeight();

      // for polar, the effect looks strange if upper-arc covers other parts
      if(isPolar() && height > bounds0.getHeight()) {
         height = bounds0.getHeight();
      }

      Shape nshape = null;

      // for polar, maintain rectangle for PolarCoord to transform properly
      if(shape instanceof Rectangle2D) {
         nshape = new Rectangle2D.Double(x, y, width, height);
      }
      else {
         // move to x,y and scale to width
         AffineTransform trans = new AffineTransform();
         trans.translate(x + width / 2, y);
         trans.scale(width / bounds.getWidth(), 1);
         trans.translate(-bounds.getX() - bounds.getWidth() / 2, -bounds.getY());
         nshape = transformShape(shape, trans);
      }

      overShape = ((BarVO) vo).shape;
      cachedShape.clear();
      setShape(nshape);
   }

   /**
    * Get the width ratio for overlay.
    */
   double getOverlayRatio() {
      return 0.7;
   }

   /**
    * Check if is polar coord.
    */
   private boolean isPolar() {
      return coord instanceof PolarCoord;
   }

   /**
    * Get the outer (surrounding) arc of the shape.
    */
   static Arc2D getOuterArc(Shape shape) {
      if(shape instanceof Arc2D) {
         return (Arc2D) shape;
      }
      else if(shape instanceof Donut) {
         return ((Donut) shape).getOuterArc();
      }
      else if(shape instanceof Ellipse2D) {
         Ellipse2D oval = (Ellipse2D) shape;
         return new Arc2D.Double(oval.getX(), oval.getY(), oval.getWidth(), oval.getHeight(),
                                 0, 360, Arc2D.OPEN);
      }

      return null;
   }

   /**
    * Apply transformation on shape.
    */
   static Shape transformShape(Shape shape, AffineTransform trans) {
      Arc2D arc = getOuterArc(shape);

      if(arc != null) {
         Ellipse2D outer = new Ellipse2D.Double(
            arc.getX(), arc.getY(), arc.getWidth(), arc.getHeight());
         Rectangle2D rect = getTransformedBounds(outer, trans);
         double start = arc.getAngleStart();
         double extent = arc.getAngleExtent();
         double end = arc.getAngleStart() + extent;
         double mid = getAngle(start + extent / 2, trans);

         start = getAngle(start, trans);
         end = getAngle(end, trans);
         extent = end - start;

         // if the mid is not between start and end, use the other side of arc
         if(Math.abs((start + end) / 2 - mid) > 0.01) {
            extent = (extent > 0) ? extent - 360 : extent + 360;
         }

         if(shape instanceof Arc2D) {
            return new Arc2D.Double(rect.getX(), rect.getY(), rect.getWidth(),
                                    rect.getHeight(), start, extent, arc.getArcType());
         }
         else if(shape instanceof Ellipse2D) {
            return new Ellipse2D.Double(rect.getX(), rect.getY(), rect.getWidth(),
                                        rect.getHeight());
         }
         else {
            Arc2D iarc = ((Donut) shape).getInnerArc();
            Ellipse2D inner = new Ellipse2D.Double(
               iarc.getX(), iarc.getY(), iarc.getWidth(), iarc.getHeight());
            Rectangle2D irect = getTransformedBounds(inner, trans);

            return new Donut(rect.getX(), rect.getY(), rect.getWidth(),
                             rect.getHeight(), irect.getWidth(),
                             irect.getHeight(), start, extent);
         }
      }

      return trans.createTransformedShape(shape);
   }

   /**
    * Get the transformed bounds of an arc. The getBounds2D on Path2D doesn't
    * return a correct bounds. It may be larger than the actual bounds if the
    * rotation is not a multiple of 90. This method convert the transformed
    * shape to Area, which returns the correct bounds.
    */
   private static Rectangle2D getTransformedBounds(Shape outer, AffineTransform trans) {
      Shape shape = trans.createTransformedShape(outer);
      return (new Area(shape)).getBounds2D();
   }

   /**
    * The the angle after transformation, in degrees.
    */
   private static double getAngle(double v, AffineTransform trans) {
      // Since the graphics space is flipped in graph, the angle actually means
      // the opposite of the y, we change the sign before transform and after
      // transform to match the graphics

      v = -v * Math.PI / 180;

      Point2D pt1 = new Point2D.Double(0, 0);
      Point2D pt2 = new Point2D.Double(100 * FastMath.cos(v), 100 * FastMath.sin(v));

      pt1 = trans.transform(pt1, null);
      pt2 = trans.transform(pt2, null);

      return -GTool.getAngle(pt1, pt2) * 180 / Math.PI;
   }

   /**
    * Explode a pie slice.
    */
   static Object explode(Object shape, ElementVO elem, Object overShape) {
      if(!"true".equals(elem.getHint(GraphElement.HINT_EXPLODED))) {
         return shape;
      }

      final int explodedRatio = 20;
      Arc2D arc = getOuterArc((Shape) shape);
      Arc2D arc2 = null;

      if(arc == null) {
         return shape;
      }
      else if(shape instanceof Donut) {
         arc2 = ((Donut) shape).getInnerArc();
      }

      double extent = -arc.getAngleExtent();
      double overext = Double.NaN;

      if(overShape instanceof Shape) {
         Arc2D overarc = getOuterArc((Shape) overShape);

         if(overarc != null) {
            overext = -overarc.getAngleExtent();
         }
      }

      extent = Math.min(360, extent);

      if(extent == 360.0 || overext == 360.0) {
         return shape;
      }

      double x = arc.getX();
      double y = arc.getY();
      double w = arc.getWidth();
      double h = arc.getHeight();
      double start = -arc.getAngleStart();
      // use base shape to calculate extent, then the overlaid
      // shape could also align with its base shape when explode it
      double aextent = Double.isNaN(overext) ? extent : overext;
      double angle = Math.toRadians(start + aextent / 2);
      int type = arc.getArcType();
      double yratio = h / w;
      double r = w / 2;
      double radius = r / explodedRatio;
      r -= radius;

      if(shape instanceof Arc2D) {
         return new Arc2D.Double(-r + radius * FastMath.cos(angle),
                                 (-r + radius * FastMath.sin(angle)) * yratio,
                                 2 * r, 2 * r * yratio,
                                 -start, -extent, type);
      }
      else if(shape instanceof Donut) {
         double r2 = Math.max(0, arc2.getWidth() / 2 - radius);

         return new Donut(-r + radius * FastMath.cos(angle),
                          (-r + radius * FastMath.sin(angle)) * yratio,
                          2 * r, 2 * r * yratio,
                          2 * r2, 2 * r2 * yratio, -start, -extent);
      }

      return shape;
   }

   @Override
   public BarVO clone() {
      BarVO obj = (BarVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private static class CachedShape {
      public CachedShape(AffineTransform trans, Shape shape) {
         this.trans = trans;
         this.path = shape;
      }

      AffineTransform trans; // transform as of last path0
      Shape path; // transformed shape ready for drawing
   }

   private static final int BAR_MIN_WIDTH = 10;
   private static final int BAR_PREFERRED_WIDTH = 16;

   protected Shape shape; // the shape to draw for this vo
   private Shape overShape;
   private Shape geomShape;
   private boolean negative = false;
   private VOText vtext;
   private VOText stackText;
   private Coordinate coord;
   private boolean has_border = false;
   // cached value
   private SoftReference<CachedShape> cachedShape = new SoftReference<>(null);
}

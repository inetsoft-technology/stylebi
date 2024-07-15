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
import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.graph.treemap.Rect;
import inetsoft.graph.treeviz.tree.circlemap.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Visualizable object for a treemap area.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public class TreemapVO extends ElementVO {
   public TreemapVO(Geometry gobj, Coordinate coord) {
      super(gobj);

      TreemapGeometry obj = (TreemapGeometry) gobj;
      TreemapElement elem = (TreemapElement) obj.getElement();

      // make sure border is not covered
      setZIndex(GDefaults.VO_Z_INDEX + obj.getLevel());

      switch(elem.getMapType()) {
      case TREEMAP:
         Rect rect = obj.getMapItem().getBounds();
         this.shape = new Rectangle2D.Double(rect.x, rect.y, rect.w, rect.h);
         break;
      case CIRCLE:
         Circle circle = (Circle) obj.getMapItem().getShape();
         setZIndex(GDefaults.VO_Z_INDEX + elem.getDimCount() - obj.getLevel());

         if(circle != null) {
            this.shape = new Ellipse2D.Double(circle.cx - circle.radius + Coordinate.GWIDTH / 2,
                  circle.cy - circle.radius + Coordinate.GHEIGHT / 2,
                  circle.radius * 2, circle.radius * 2);
         }
         break;
      case SUNBURST:
      case ICICLE:
         this.shape = (Shape) obj.getMapItem().getShape();
         break;
      }

      int level = obj.getLevel();
      int k = elem.getTreeDimCount() - level - 1;

      // currently we don't add title outside of area so we only show text on leaf blocks.
      if((hasTextLabel(k) || !elem.getMapType().isContainment()) &&
         obj.getTupleIndex() >= 0 &&
         (elem.getHint("overlaid") == null && elem.getHint("overlay") == null ||
         // overlaid leaf shape shouldn't draw label. label on overlay shape
          elem.getHint("overlaid") != null && !obj.isLeaf() ||
          // overlay (brushed) data only show leaf inside the 'all' container (see paint()),
          // so only add label if its not leaf
          elem.getHint("overlay") != null && obj.isLeaf()))
      {
         String currDim = null;

         if(k >= 0) {
            CurrentTextFrame.setCurrentField(currDim = elem.getTreeDim(k));
         }

         Object label = hasTextLabel(k) ? obj.getTreeDimText(k) : null;

         if(label != null) {
            if(elem.getMapType() == TreemapElement.Type.SUNBURST) {
               vtext = new CircularVOText(label, this, currDim,
                                          coord.getDataSet(), obj.getSubRowIndex(), gobj);
            }
            else {
               vtext = new VOText(label, this, currDim,
                                  coord.getDataSet(), obj.getSubRowIndex(), gobj);
            }

            if(elem.isAutoTextColor()) {
               vtext.setAutoBackground(obj.getColor(0));
            }

            TextFrame textFrame = elem.getTextFrame();

            if(textFrame instanceof CurrentTextFrame && vtext.getTextSpec() != null) {
               vtext.getTextSpec().setFormat(((CurrentTextFrame) textFrame).getFormat());
            }

            vtext.setCollisionModifier(VLabel.MOVE_NONE);
            vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtext.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }

         CurrentTextFrame.setCurrentField(null);
      }
   }

   /**
    * Return the dimensions up to the current level.
    */
   public String[] getCurrentTreeDims() {
      TreemapGeometry obj = (TreemapGeometry) getGeometry();
      TreemapElement elem = (TreemapElement) obj.getElement();
      int level = obj.getLevel();
      return IntStream.range(0, elem.getTreeDimCount() - level)
         .mapToObj(i -> elem.getTreeDim(i))
         .toArray(String[]::new);
   }

   // check if this object should have text label
   private boolean hasTextLabel(int k) {
      TreemapGeometry obj = (TreemapGeometry) getGeometry();
      TreemapElement elem = (TreemapElement) obj.getElement();

      if(elem.getMapType() == TreemapElement.Type.TREEMAP) {
         return obj.isLeaf();
      }

      TextFrame textFrame = elem.getTextFrame();

      // if text frame is defined for a tree dim, only show label for vo
      // of that dimension
      if(textFrame != null) {
         String field = textFrame.getField();

         for(int i = 0; i < elem.getTreeDimCount(); i++) {
            if(Objects.equals(field, elem.getTreeDim(i))) {
               return k == i;
            }
         }

         // circle packing show label on leaf by default
         // we have logic in layoutText to place the container label at top. but that
         // can overlap with bubbles so it's not used for now until we find a
         // better solution
         if(field == null && elem.getMapType() == TreemapElement.Type.CIRCLE) {
            return obj.isLeaf();
         }
      }

      return true;
   }

   /**
    * Paint the visual object on the graphics.
    */
   @Override
   public void paint(Graphics2D g) {
      TreemapGeometry gobj = (TreemapGeometry) getGeometry();
      TreemapElement elem = (TreemapElement) gobj.getElement();

      // overlay (brushed) data only show leaf inside the 'all' container
      if(elem.getHint("overlay") != null && !gobj.isLeaf() || this.shape == null) {
         return;
      }

      Shape shape = getScreenTransform().createTransformedShape(this.shape);
      Color color = gobj.getColor(0);
      GLine line = gobj.getLine(0);
      GTexture texture = gobj.getTexture(0);
      Graphics2D g2 = (Graphics2D) g.create();
      int level = gobj.getLevel();
      int nestingLevel = elem.getTreeDimCount() - level;
      Color borderColor = elem.getBorderColor(nestingLevel);
      boolean colorBound = elem.getColorFrame() instanceof CategoricalColorFrame;

      if(borderColor == null) {
         borderColor = elem.getBorderColor();
      }

      if(elem.getMapType() == TreemapElement.Type.SUNBURST ||
         elem.getMapType() == TreemapElement.Type.CIRCLE)
      {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      }

      color = applyAlpha(color);

      // background only filled for leaf (circle packing, treemap) if leaf is not drawn inside
      // of a container.
      if(gobj.isLeaf() || !elem.getMapType().isContainment()) {
         g2.setColor(color);

         if(texture != null) {
            texture.paint(g2, shape);
         }
         else {
            g2.fill(shape);
         }
      }
      else if(elem.getBackground(nestingLevel) != null) {
         g2.setColor(elem.getBackground(nestingLevel));
         g2.fill(shape);
      }

      // default to draw line if color map not assigend (all areas drawn in same color)
      if(line != null || borderColor != null || !colorBound || level >= 0) {
         if(borderColor != null) {
            g2.setColor(borderColor);
         }
         else if(elem.getMapType() == TreemapElement.Type.CIRCLE) {
            g2.setColor(Color.LIGHT_GRAY);
         }
         else {
            g2.setColor(GDefaults.DEFAULT_LINE_COLOR.brighter());
         }

         if(line != null) {
            g2.setStroke(line.getStroke());
         }
         else if(elem.getMapType() == TreemapElement.Type.TREEMAP) {
            g2.setStroke(new BasicStroke(getBorderWidth(level)));
         }

         g2.draw(shape);
      }

      g2.dispose();
   }

   // get the treemap area border width
   private int getBorderWidth(int level) {
      return Math.min(level + 1, 5);
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

      TreemapGeometry gobj = (TreemapGeometry) getGeometry();
      TreemapElement elem = (TreemapElement) gobj.getElement();
      TreemapElement.Type mapType = elem.getMapType();

      // sunburst places text along the curve
      if(mapType == TreemapElement.Type.SUNBURST) {
         Donut donut = (Donut) shape;
         Arc2D outer = donut.getOuterArc();
         Arc2D inner = donut.getInnerArc();
         Rectangle2D obox = getScreenTransform().createTransformedShape(outer.getFrame())
            .getBounds2D();
         Rectangle2D ibox = getScreenTransform().createTransformedShape(inner.getFrame())
            .getBounds2D();
         Double angle = outer.getAngleStart();
         Double extent = outer.getAngleExtent();

         CircularVOText vtext2 = (CircularVOText) vtext;
         vtext2.setCircle(obox.getCenterX(), obox.getCenterY(),
                          obox.getWidth() / 2, ibox.getWidth() / 2,
                          Math.toRadians(angle), Math.toRadians(extent));

         // if the text can almost fit, allow it to show
         if(Math.abs(extent) < vtext2.getPreferredAngle() * 0.9) {
            //vtext.setZIndex(-1); change to draw radial instead of hide. (58318)
            elem.setRadialLabel(gobj.getLevel(), true);
         }

         return;
      }

      Rectangle2D box = getScreenTransform().createTransformedShape(shape).getBounds2D();
      // clear max size so preferred sizes are from text and not limited by maxSize.
      vtext.setMaxSize(null);

      double prefw = getVOTextWidth(vtext, vgraph, (ElementGeometry) getGeometry());
      double prefh = vtext.getPreferredHeight();
      vtext.setMaxSize(new DimensionD(box.getWidth(), box.getHeight()));

      // circle always places text in the middle
      if(mapType == TreemapElement.Type.CIRCLE) {
         // put in middle
         if(gobj.isLeaf()) {
            double y = Math.min(prefh / 2, box.getHeight() / 4);
            double x = PolarUtil.calcXonEllipse(box.getWidth() / 2, box.getHeight() / 2, y);

            if(x < 10 && x < prefw / 2) {
               vtext.setZIndex(-1);
            }

            x = Math.min(x, prefw / 2);
            vtext.setPosition(new Point2D.Double(box.getCenterX() - x, box.getCenterY() - y));
            vtext.setSize(new DimensionD(x * 2, y * 2));
         }
         // put on top
         // currently unused
         else {
            double x = prefw / 2;
            double y = PolarUtil.calcXonEllipse(box.getHeight() / 2, box.getWidth() / 2, x);

            if(y - prefh < box.getHeight() / 4) {
               vtext.setZIndex(-1);
            }

            if(containerSpec == null) {
               containerSpec = vtext.getTextSpec().clone();
               containerSpec.setBackground(new Color(255, 255, 255, 100));
            }

            vtext.setAutoBackground(null);
            vtext.setTextSpec(containerSpec);
            vtext.setPosition(new Point2D.Double(box.getCenterX() - x,
                                                 box.getCenterY() + y - prefh));
            vtext.setSize(new DimensionD(prefw, prefh));
         }

         return;
      }

      Rectangle2D vbounds = vgraph.getPlotBounds();
      int placement = gobj.getLabelPlacement();
      double tx, ty;
      double midx, midy;
      final int XGAP = 4;
      final int YGAP = 2;

      if(mapType == TreemapElement.Type.TREEMAP) {
         final int borderW = getBorderWidth(elem.getTreeDimCount() - 1) - YGAP;
         box = new Rectangle2D.Double(box.getX() + borderW, box.getY() + borderW,
                                      box.getWidth() - borderW * 2,
                                      box.getHeight() - borderW * 2);
      }

      switch(placement) {
      case GraphConstants.BOTTOM:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY();
         tx = midx - prefw / 2;
         ty = midy + YGAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.LEFT:
         midx = box.getX();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx + XGAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.RIGHT_ALIGNMENT);
         break;
      case GraphConstants.RIGHT:
         midx = box.getX() + box.getWidth();
         midy = box.getY() + box.getHeight() / 2;
         tx = midx - prefw - XGAP;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         break;
      case GraphConstants.TOP:
         midx = box.getX() + box.getWidth() / 2;
         midy = box.getY() + box.getHeight();
         tx = midx - prefw / 2;
         ty = midy - prefh - YGAP;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      case GraphConstants.CENTER:
         Point2D cp = new Point2D.Double(box.getCenterX(), box.getCenterY());

         midx = cp.getX();
         midy = cp.getY();
         tx = midx - prefw / 2;
         ty = midy - prefh / 2;
         vtext.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         break;
      default: // auto
         int labelAlign = mapType.getLabelAlign();

         if((labelAlign & GraphConstants.LEFT_ALIGNMENT) != 0) {
            midx = box.getX();
            tx = midx + XGAP;
         }
         else if((labelAlign & GraphConstants.CENTER_ALIGNMENT) != 0) {
            midx = box.getX() + box.getWidth() / 2;
            tx = midx - prefw / 2;
         }
         else {
            midx = box.getX() + box.getWidth();
            tx = midx - prefw - XGAP;
         }

         if((labelAlign & GraphConstants.TOP_ALIGNMENT) != 0) {
            midy = box.getY() + box.getHeight();
            ty = midy - prefh - YGAP;
         }
         else if((labelAlign & GraphConstants.MIDDLE_ALIGNMENT) != 0) {
            midy = box.getY() + box.getHeight() / 2;
            ty = midy - prefh / 2;
         }
         else {
            midy = box.getY();
            ty = midy + YGAP;
         }

         vtext.setAlignmentX(GraphConstants.LEFT_ALIGNMENT);
         vtext.setPlacement(placement);
         break;
      }

      vtext.setPosition(new Point2D.Double(Math.max(tx, vbounds.getX()), ty));

      // if wider than area, reduce the width and use ...
      if(prefw > box.getWidth()) {
         prefw = Math.max(30, box.getWidth());
      }

      // hide label that doesn't fit in area
      if(prefw > box.getWidth() || prefh > box.getHeight()) {
         vtext.setZIndex(-1);
      }
      else {
         vtext.setSize(new DimensionD(prefw, prefh));
      }

      Point2D pt = new Point2D.Double(midx, midy);
      Point2D offset = vtext.getRotationOffset(pt, placement);
      vtext.setOffset(offset);
   }

   @Override
   public void postLayoutText() {
      if(vtext instanceof CircularVOText) {
         TreemapGeometry obj = (TreemapGeometry) getGeometry();
         TreemapElement elem = (TreemapElement) obj.getElement();

         // radial flag may be changed in layoutText().
         ((CircularVOText) vtext).setRadial(elem.isRadialLabel(obj.getLevel()));
      }
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
      TreemapGeometry obj = (TreemapGeometry) getGeometry();
      TreemapElement elem = (TreemapElement) obj.getElement();

      if(this.shape == null) {
         return new Shape[0];
      }

      switch(elem.getMapType()) {
      case CIRCLE:
         Rectangle2D rect = getScreenTransform().createTransformedShape(this.shape).getBounds2D();
         return new Shape[] { new Ellipse2D.Double(rect.getX(), rect.getY(), rect.getWidth(),
                                                   rect.getHeight()) };
      case SUNBURST:
         return new Shape[] { BarVO.transformShape(this.shape, getScreenTransform()) };
      default:
         Rectangle2D shape = getScreenTransform().createTransformedShape(this.shape).getBounds2D();
         return new Shape[] { shape };
      }
   }

   // called if "overlay" is defined
   @Override
   public void overlay(VisualObject vo0) {
      ElementVO vo = (ElementVO) vo0;
      TreemapGeometry overObj = (TreemapGeometry) vo.getGeometry();
      TreemapGeometry gobj = (TreemapGeometry) getGeometry();
      TreemapElement overElem = (TreemapElement) overObj.getElement();
      TreemapElement elem = (TreemapElement) gobj.getElement();
      Rectangle2D over = vo.getShapes()[0].getBounds2D();

      if(!gobj.isLeaf() || !overObj.isLeaf()) {
         this.shape = vo.getShapes()[0];
         return;
      }

      double mySize = gobj.getSize(0);
      double overSize = overObj.getSize(0);
      double overW = over.getWidth();
      double overH = over.getHeight();

      mySize = getRawValue(mySize, elem.getSizeFrame());
      overSize = getRawValue(overSize, overElem.getSizeFrame());

      // overlay (brushing), place at the top left corner of the underlying shape
      if(elem.getMapType() == TreemapElement.Type.SUNBURST) {
         Donut donut = (Donut) vo.getShapes()[0];
         Arc2D inner = donut.getInnerArc();
         Arc2D outer = donut.getOuterArc();
         double oextent = outer.getAngleExtent();
         double nextent = overSize == 0 ? oextent : outer.getAngleExtent() * mySize / overSize;

         shape = new Donut(outer.getFrame().getX(), outer.getFrame().getY(),
               outer.getFrame().getWidth(), outer.getFrame().getHeight(),
               inner.getFrame().getWidth(), inner.getFrame().getHeight(),
               outer.getAngleStart() + (oextent - nextent) / 2, nextent);
      }
      else {
         // equation:
         // w * h / (overW * overH) = mySize / overSize
         // w / h = overW / overH
         double h = overSize != 0 ? Math.sqrt(mySize * overH * overH / overSize) : overH;
         double w = overW * h / overH;

         if(elem.getMapType() == TreemapElement.Type.CIRCLE) {
            shape = new Ellipse2D.Double(over.getX() + overW / 2 - w / 2,
                  over.getY() + overH / 2 - h / 2, w, h);
         }
         else {
            shape = new Rectangle2D.Double(over.getX(), over.getY() + overH - h, w, h);
         }
      }
   }

   // for TreemapSizeFrame, the smallest/largest may defined on the value range.
   // so instead of using the actual size, we calculate the raw (orginal) value
   // which reflects the proportional size of overlay on the original shape.
   private static double getRawValue(double mySize, SizeFrame mySizeFrame) {
      if(mySizeFrame == null || mySizeFrame.getScale() == null) {
         return mySize;
      }

      double smallest = mySizeFrame.getSmallest();
      double largest = mySizeFrame.getLargest();
      double ratio = (mySize - smallest) / (largest - smallest);
      double min = mySizeFrame.getScale().getMin();
      double max = mySizeFrame.getScale().getMax();
      return ratio * (max - min) + min;
   }

   @Override
   public TreemapVO clone() {
      TreemapVO obj = (TreemapVO) super.clone();
      obj.vtext = cloneVOText(vtext);
      return obj;
   }

   private Shape shape;
   private VOText vtext;
   private TextSpec containerSpec;

   private static final Logger LOG = LoggerFactory.getLogger(TreemapVO.class);
}

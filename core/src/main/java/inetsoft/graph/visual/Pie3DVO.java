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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.PolarCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.*;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.*;
import inetsoft.util.CoreTool;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/**
 * This visual object represents a 3d pie in a graphic output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class Pie3DVO extends ElementVO {
   /**
    * Create the visual objects.
    * @param coord the coordinate the visual object is plotted on.
    */
   public Pie3DVO(Geometry gobj, Coordinate coord, int[] subridxs) {
      super(gobj);

      this.coord = coord;

      Pie3DGeometry obj = (Pie3DGeometry) gobj;
      GraphElement elem = obj.getElement();
      SizeFrame sizes = obj.getVisualModel().getSizeFrame();
      int[] rows = subridxs;

      shapes = new Shape[obj.getTupleCount()];
      heightRect = new Rectangle(0, 0, (int) Coordinate.GWIDTH, (int) Coordinate.GHEIGHT / 2);
      vtexts = new ArcVOText[shapes.length];

      for(int i = 0; i < shapes.length; i++) {
         double interval = obj.getInterval(i);
         double width = coord.getMaxWidth();
         double height = coord.getIntervalSize(interval);
         Point2D loc = coord.getPosition(obj.getTuple(i));
         double size = obj.getSize(rows[i]);
         Object label = obj.getText(rows[i]);

         size = getPieSize(width, size, sizes);

         if(height < 0) {
            height = -height;
            loc = new Point2D.Double(loc.getX(), loc.getY() - height);
         }

         shapes[i] = new Rectangle2D.Double(loc.getX(), loc.getY(), size,height);

         if((elem.getCollisionModifier() & GraphElement.MOVE_CENTER) != 0) {
            shapes[i] = GTool.centerShape(shapes[i], true);
         }

         if(label != null) {
            vtexts[i] = new ArcVOText(label, this, getMeasureName(), coord.getDataSet(),
                                      obj.getSubRowIndexes()[i], obj);
            vtexts[i].setColIndex(getColIndex());
            vtexts[i].setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
            vtexts[i].setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         }
      }

      this.overlay = elem.getHint("overlay") != null;
   }

   /**
    * Calculate the 3d pie size.
    */
   private double getPieSize(double maxWidth, double size, SizeFrame frame) {
      if(frame == null) {
         return maxWidth;
      }

      double max = frame.getLargest();
      double min = frame.getSmallest();

      if(min == max) {
         return Math.max(1, maxWidth / 2);
      }

      double minwidth = maxWidth / 5;
      double w = Math.max(0, maxWidth - minwidth);
      return minwidth + w * size / frame.getMax();
   }

   /**
    * Paint the visual object on the graphics.
    * @param g the graphics context to use for painting.
    */
   @Override
   public void paint(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);

      ElementGeometry gobj = (ElementGeometry) getGeometry();
      Shape h2 = getScreenTransform().createTransformedShape(heightRect);
      double height = h2.getBounds().getHeight() / MULTIPLE;

      if(!overlay) {
         paintBottom(g, gobj, height);
      }

      paintTop(g, gobj);
   }

   /**
    * Paint the bottom 3d effect, include the bottom and the profile.
    * @param g the graphics context to use for painting.
    * @param gobj current visual object's geometry.
    * @param height the 3D pie Height.
    */
   private void paintBottom(Graphics2D g, ElementGeometry gobj, double height) {
      GraphElement elem = gobj.getElement();
      Color borderColor = elem.getBorderColor();
      Vector<Object[]> sides = new Vector();
      int[] rows = getSubRowIndexes();

      for(int i = 0; i < transformedShapes.size(); i++) {
         Color color = gobj.getColor(rows[i]);
         GTexture texture = gobj.getTexture(rows[i]);
         Shape shape = (Shape) transformedShapes.get(i);
         Arc2D arc = BarVO.getOuterArc(getPath(shape));
         Object[] sSideObj = null;
         Object[] eSideObj = null;
         Object[] fSideObj = null;

         color = applyAlpha(color);

         // 1. paint bottom.
         g.setColor(color);

         if(arc.getAngleExtent() == 0) {
            continue;
         }

         Arc2D arcb = new Arc2D.Double(arc.getX(), arc.getY() - height,
                                       arc.getWidth(), arc.getHeight(),
                                       arc.getAngleStart(), arc.getAngleExtent(),
                                       Arc2D.PIE);

         if(texture == null) {
            g.fill(arcb);
         }
         else {
            GTexture texture1 = new GTexture(texture.getLineGap(), Math.PI / 2, 1);
            texture1.paint(g, arcb);
         }

            // always draw border around texture
         if(borderColor != null || texture != null) {
            if(borderColor != null) {
               g.setColor(borderColor);
            }

            g.draw(arcb);
         }

         Color darkColor = color.darker();
         // 2. add the start and end side.
         Point2D[] startSide = new Point2D[4];
         Point2D[] endSide = new Point2D[4];

         startSide[0] = arc.getStartPoint();
         startSide[1] = new Point2D.Double(arcb.getStartPoint().getX(),
                                           arcb.getStartPoint().getY());
         startSide[2] = new Point2D.Double(arcb.getCenterX(), arcb.getCenterY());
         startSide[3] = new Point2D.Double(arc.getCenterX(), arc.getCenterY());

         endSide[0] = arc.getEndPoint();
         endSide[1] = new Point2D.Double(arcb.getEndPoint().getX(),
                                         arcb.getEndPoint().getY());
         endSide[2] = new Point2D.Double(arcb.getCenterX(), arcb.getCenterY());
         endSide[3] = new Point2D.Double(arc.getCenterX(), arc.getCenterY());

         GeneralPath sideShp = new GeneralPath();

         for(int j = 0; j < startSide.length; j++) {
            if(j == 0) {
               sideShp.moveTo((float) startSide[j].getX(),
                              (float) startSide[j].getY());
            }
            else {
               sideShp.lineTo((float) startSide[j].getX(),
                              (float) startSide[j].getY());
            }
         }

         sideShp.closePath();
         sSideObj = new Object[] {sideShp, darkColor, "dark", texture};
         sideShp = new GeneralPath();

         for(int j = 0; j < endSide.length; j++) {
            if(j == 0) {
               sideShp.moveTo((float) endSide[j].getX(),
                              (float) endSide[j].getY());
            }
            else {
               sideShp.lineTo((float) endSide[j].getX(),
                              (float) endSide[j].getY());
            }
         }

         sideShp.closePath();
         eSideObj = new Object[] {sideShp, darkColor, "dark", texture};

         // 3. add front outer side
         double startAngle = arc.getAngleStart();
         double endAngle = startAngle + arc.getAngleExtent();
         double frontAngle = 0;
         Object[] side = null;

         if(arc.getAngleExtent() == 360) {
            startAngle = 180;
            endAngle = 360;
         }

         // if a part of the front side can not be seen.
         // yes add to sides2, on add to sides
         // slices that overlap the 180 extent need to be drawn
         // from negative side here
         // i.e. a slice that starts at 90 degrees and ends at 270 degrees
         if(Math.abs(startAngle) <= 180) {
            if(Math.abs(endAngle) > 180) {
               Arc2D arc1 = new Arc2D.Double(arc.getFrame(), 0.0, 0.0,
                                             Arc2D.PIE);

               arc1.setAngleStart(endAngle);
               arc1.setAngleExtent(-endAngle - 180);

               Point2D[] point = new Point2D[4];
               point[0] = arc1.getEndPoint();
               point[1] = new Point2D.Double(arc1.getEndPoint().getX(),
                          arc1.getEndPoint().getY() - height);
               point[2] = new Point2D.Double(arc1.getStartPoint().getX(),
                          arc1.getStartPoint().getY() - height);
               point[3] = arc1.getStartPoint();
               sideShp = new GeneralPath();

               for(int j = 0; j < point.length; j++) {
                  if(j == 0) {
                     sideShp.moveTo((float) point[j].getX(),
                                    (float) point[j].getY());
                  }
                  else {
                     sideShp.lineTo((float) point[j].getX(),
                                    (float) point[j].getY());
                  }
               }

               sideShp.closePath();
               fSideObj = new Object[] {sideShp, color, "light", texture};
               frontAngle = arc1.getAngleStart();
            }
         }
         else {
            Point2D[] pts = new Point2D[4];
            pts[0] = arc.getEndPoint();
            pts[1] = new Point2D.Double(arcb.getEndPoint().getX(),
                                        arcb.getEndPoint().getY());
            pts[2] = new Point2D.Double(arcb.getStartPoint().getX(),
                                        arcb.getStartPoint().getY());
            pts[3] = arc.getStartPoint();
            sideShp = new GeneralPath();

            for(int j = 0; j < pts.length; j++) {
               if(j == 0) {
                  sideShp.moveTo((float) pts[j].getX(), (float) pts[j].getY());
               }
               else {
                  sideShp.lineTo((float) pts[j].getX(), (float) pts[j].getY());
               }
            }

            sideShp.closePath();
            Color sideC = Math.abs(endAngle) <= 180 ? darkColor : color;

            fSideObj = new Object[] {sideShp, sideC, "light", texture};
            frontAngle = getAngleQuadrant(startAngle) ==
                         getAngleQuadrant(endAngle) ? startAngle : endAngle;
         }

         // 4. add side to different quadrant.
         if(getAngleQuadrant(startAngle) == getAngleQuadrant(endAngle)) {
            if(getAngleQuadrant(startAngle) == 2 ||
               getAngleQuadrant(startAngle) == 3)
            {
               addToSides(new Object[] {sSideObj, eSideObj, fSideObj},
                          new double[] {startAngle, endAngle, frontAngle});
            }
            else {
               addToSides(new Object[] {fSideObj, sSideObj, eSideObj},
                          new double[] {frontAngle, startAngle, endAngle});
            }
         }
         else {
            if(getAngleQuadrant(endAngle) != 4) {
               addToSides(new Object[] {sSideObj, eSideObj, fSideObj},
                          new double[] {startAngle, endAngle, frontAngle});
            }
            else {
               addToSides(new Object[] {sSideObj, fSideObj, eSideObj},
                          new double[] {startAngle, frontAngle, endAngle});
            }
         }
      }

      // 5. add sides1 - sides4 to sides
      for(int i = sides1.size() - 1; i >= 0; i--) {
         sides.add(sides1.get(i));
      }

      for(int i = 0; i < sides2.size(); i++) {
         sides.add(sides2.get(i));
      }

      for(int i = 0; i < sides3.size(); i++) {
         sides.add(sides3.get(i));
      }

      for(int i = sides4.size() - 1; i >= 0; i--) {
         sides.add(sides4.get(i));
      }

      // 6. fill all the sides
      for(int i = 0; i < sides.size(); i++) {
         Object[] side = sides.get(i);
         GTexture texture = (GTexture) side[3];
         Shape shp = (Shape) side[0];
         Color clr = (Color) side[1];
         clr = applyAlpha(clr);

         if(texture == null) {
            g.setColor(clr);
            g.fill(shp);
         }
         else {
            texture = new GTexture(texture.getLineGap(), Math.PI / 2, 1);
            g.setColor(clr);
            texture.paint(g, shp);
         }
      }
   }

   /**
    * Paint the bottom 3d effect, include the bottom and the profile.
    * @param g the graphics context to use for painting.
    * @param gobj current visual object's geometry.
    */
   private void paintTop(Graphics2D g, ElementGeometry gobj) {
      GraphElement elem = gobj.getElement();
      vlabels = new VLabel[transformedShapes.size()];
      int[] rows = getSubRowIndexes();

      for(int i = 0; i < transformedShapes.size(); i++) {
         Color color = gobj.getColor(rows[i]);
         GTexture texture = gobj.getTexture(rows[i]);
         GLine line = gobj.getLine(rows[i]);
         Color borderColor = elem.getBorderColor();
         Shape shape = (Shape) transformedShapes.get(i);
         Arc2D path = BarVO.getOuterArc(getPath(shape));
         color = applyAlpha(color);

         g.setColor(color);

         if(texture == null) {
            g.fill(path);
            applyEffect(g, path, color);
         }
         else {
            texture.paint(g, path);

            // always draw border around texture
            if(line == null) {
               line = new GLine(1);
            }
         }

         if(line != null || borderColor != null) {
            Stroke ostroke = g.getStroke();
            g.setColor(borderColor != null ? borderColor : color.darker());

            if(line != null) {
               g.setStroke(line.getStroke(0.5));
            }

            double start = Math.abs(path.getAngleStart());
            double extent = Math.abs(path.getAngleExtent());

            // avoid drawing edge at the arc angles if it's a complete pie
            if(start == 0 && extent == 360) {
               g.draw(new Ellipse2D.Double(path.getX(), path.getY(),
                                           path.getWidth(), path.getHeight()));
            }
            else {
               g.draw(path);
            }

            g.setStroke(ostroke);
         }
      }
   }

   /**
    * Apply the gradual effect.
    */
   private void applyEffect(Graphics2D g, Shape path, Color fill) {
      Arc2D arc = BarVO.getOuterArc(path);
      Ellipse2D ellipse = new Ellipse2D.Double(arc.getX(), arc.getY(),
         arc.getWidth(), arc.getHeight());
      Rectangle rect = ellipse.getBounds();
      int x = rect.x;
      int y = rect.y;
      int width = rect.width;
      int height = rect.height;

      // clear -> partial transparent white
      Color col1 = new Color(255, 255, 255, 0);
      Color col2 = new Color(255, 255, 255, 180);

      // top left gradient
      GradientPaint paint1 =
         new GradientPaint(x + width, y, col2,
         x + 2 * width / 3, y + height / 3, col1);
      g.setPaint(paint1);
      g.fill(path);

      // bottom right gradient
      GradientPaint paint2 = new GradientPaint(x, y + height, col2,
                                               x + width / 3,
                                               y + 2 * height / 3, col1);
      g.setPaint(paint2);
      g.fill(path);
   }

   /**
    * Get the shape of the bar.
    */
   private Shape getPath(Shape shape) {
      return BarVO.transformShape(shape, getScreenTransform());
   }

   /**
    * Layout the text labels.
    * @param vgraph the vgraph to hold the text labels.
    */
   @Override
   public void layoutText(VGraph vgraph) {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      Rectangle2D bounds = vgraph.getPlotBounds();
      double plotw = bounds.getWidth();
      double ploth = bounds.getHeight();
      double plotRadius = ((PolarCoord) coord).getRadius();
      boolean moveable = ((PolarCoord) coord).isLabelMoveable();
      Shape h2 = getScreenTransform().createTransformedShape(heightRect);
      double height = h2.getBounds().getHeight() / MULTIPLE;

      for(int i = 0; i < transformedShapes.size(); i++) {
         if(i >= vtexts.length) {
            break;
         }

         if(vtexts[i] == null) {
            continue;
         }

         vgraph.removeVisual(vtexts[i]);
         vgraph.addVisual(vtexts[i]);

         ArcVOText vtext = (ArcVOText) vtexts[i];
         Shape path = getPath((Shape) transformedShapes.get(i));
         Arc2D arc = BarVO.getOuterArc(path);
         double angle = -(arc.getAngleStart() + arc.getAngleExtent() / 2) * Math.PI / 180;
         double w = arc.getWidth();
         double h = arc.getHeight();
         double cx = arc.getX() + w / 2;
         double cy = arc.getY() + h / 2;
         double wr = w / 2;
         double hr = h / 2;

         double textw = PolarUtil.get3DPieLabelWidth(plotw, plotRadius, angle, vtext, moveable);
         double texth = PolarUtil.get3DPieLabelHeight(ploth, plotRadius, angle, vtext);

         vtext.setSize(new DimensionD(textw, texth));

         vtext.setArc(cx, cy, wr, hr, height);
         vtext.layout(angle);
         vtext.setExtent(Math.toRadians(arc.getAngleExtent()));
      }
   }

   /**
    * Transform the logic coordinate to chart coordinate.
    */
   @Override
   public void transform(Coordinate coord) {
      transformedShapes = new ArrayList();
      trans0 = null;

      for(int i = 0; i < shapes.length; i++) {
         Object overShape = overShapes.get(shapes[i]);
         Object shp = BarVO.explode(coord.transformShape(shapes[i]), this,
            overShape == null ? null : coord.transformShape(overShape));
         transformedShapes.add(shp);
      }
   }

   /**
    * Get the bounding box of the visual object in the graphics output.
    */
   @Override
   public Rectangle2D getBounds() {
      ElementGeometry gobj = (ElementGeometry) getGeometry();
      Rectangle2D rect = null;

      for(int i = 0; i < transformedShapes.size(); i++) {
         Shape shape = (Shape) transformedShapes.get(i);

         if(rect == null || rect.isEmpty()) {
            rect = getPath(shape).getBounds2D();
         }
         else {
            rect.createUnion(getPath(shape).getBounds2D());
         }
      }

      return rect;
   }

   /**
    * Get min width of this vo.
    */
   @Override
   protected double getMinWidth0() {
      return MIN_WIDTH;
   }

   /**
    * Get preferred width of this vo.
    */
   @Override
   protected double getPreferredWidth0() {
      return PREFERRED_WIDTH;
   }

   /**
    * Get the visual object's texts.
    */
   @Override
   public VOText[] getVOTexts() {
      return vtexts;
   }

   /**
    * Set sub row indexs.
    */
   @Override
   public void setSubRowIndexes(int[] subridxs) {
      super.setSubRowIndexes(subridxs);

      if(!overlay) {
         initShapeMap();
      }
   }

   /**
    * Get linkable shapes.
    */
   @Override
   public Shape[] getShapes() {
      if(shapes0 == null || !getScreenTransform().equals(trans0)) {
         trans0 = (AffineTransform) getScreenTransform().clone();
         shapes0 = new Shape[transformedShapes.size()];

         for(int i = 0; i < transformedShapes.size(); i++) {
            Shape shape = (Shape) transformedShapes.get(i);
            shapes0[i] = getPath(shape);
         }
      }

      return shapes0;
   }

   /**
    * Set the col index.
    */
   @Override
   public void setColIndex(int cidx) {
      super.setColIndex(cidx);

      if(vtexts != null) {
         for(int i = 0; i < vtexts.length; i++) {
            if(vtexts[i] != null) {
               vtexts[i].setColIndex(cidx);
            }
         }
      }
   }

   /**
    * Make the visual object overlay another visual object.
    * @param vo the specified visual object to be overlaid.
    */
   @Override
   public void overlay(VisualObject vo) {
      DataSet dset = coord.getDataSet();
      Map<String, Shape> map = ((Pie3DVO) vo).shapeMap;
      overShapes.clear();
      int[] subridxs = getSubRowIndexes();

      for(int i = 0; i < subridxs.length; i++) {
         Shape shape0 = map.get(createKey(subridxs[i]));

         // sometimes we can not find the vo to be overlaid, ignore it
         if(shape0 == null) {
            continue;
         }

         Rectangle2D bounds0 = shape0.getBounds2D();
         Rectangle2D bounds = shapes[i].getBounds2D();
         double x = bounds0.getX() +
            (bounds0.getWidth() - bounds.getWidth()) / 2;
         shapes[i] = new Rectangle2D.Double(x, bounds0.getY(),
            bounds.getWidth(), bounds.getHeight());
         overShapes.put(shapes[i], shape0);
      }
   }

   /**
    * Create the key of the element visual object.
    */
   private String createKey(int ridx) {
      DataSet data = coord.getDataSet();
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < data.getColCount(); i++) {
         String dim = data.getHeader(i);

         if(!data.isMeasure(dim)) {
            if(sb.length() > 0) {
               sb.append(',');
            }

            Object obj = data.getData(dim, ridx);
            sb.append(CoreTool.toString(obj));
         }
      }

      return sb.toString();
   }

   /**
    * Init shape map.
    */
   private void initShapeMap() {
      shapeMap = new HashMap();
      int[] subridxs = getSubRowIndexes();

      for(int i = 0; i < subridxs.length; i++) {
         shapeMap.put(createKey(subridxs[i]), shapes[i]);
      }
   }

   /**
    * Get the angle quadrant. Starting at 1, counter-clockwise.
    */
   private int getAngleQuadrant(double angle) {
      if(angle >= -90) {
         return 1;
      }
      else if(angle >= -180 && angle < -90) {
         return 2;
      }
      else if(angle >= -270 && angle < -180) {
         return 3;
      }

      return 4;
   }

   /**
    * Add the side array in 4 quadrant.
    */
   private void addToSides(Object[] sides, double[] angles) {
      for(int i = 0; i < sides.length; i++) {
         Object[] side = (Object[]) sides[i];

         if(side == null) {
            continue;
         }

         switch(getAngleQuadrant(angles[i])) {
         case 1:
            sides1.add(side);
            break;
         case 2:
            sides2.add(side);
            break;
         case 3:
            sides3.add(side);
            break;
         case 4:
            sides4.add(side);
            break;
         }
      }
   }

   @Override
   public Pie3DVO clone() {
      Pie3DVO obj = (Pie3DVO) super.clone();
      obj.vtexts = cloneVOTexts(vtexts);
      return obj;
   }

   private static final double MIN_WIDTH = 20;
   private static final double PREFERRED_WIDTH = 20;
   private static final int MULTIPLE = 12;
   private Shape[] shapes; // the line points
   private Map overShapes = new HashMap(); // over shapes
   private ArrayList transformedShapes;
   private VLabel[] vlabels;
   private VOText[] vtexts;
   private Rectangle heightRect;
   private Map<String, Shape> shapeMap;
   private boolean overlay;
   private Vector<Object[]> sides1 = new Vector();
   private Vector<Object[]> sides2 = new Vector();
   private Vector<Object[]> sides3 = new Vector();
   private Vector<Object[]> sides4 = new Vector();
   private Coordinate coord;
   private Shape[] shapes0; // cached transformed shapes
   private transient AffineTransform trans0; // cached transform
}

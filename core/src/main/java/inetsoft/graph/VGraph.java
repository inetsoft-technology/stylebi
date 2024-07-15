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
package inetsoft.graph;

import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.form.GraphForm;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.*;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.*;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * VGraph is the final output of a EGraph. It contains plotting information and
 * can be used to produce the output on a graphics. It can also be used to
 * query visual object positions and sizes for adding interactions or links.
 * <br>
 * A VGraph can be produced from a EGraph and a dataset using a Plotter.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class VGraph extends BoundedContainer {
   /**
    * Create a VGraph.
    * @param coord the coordinate of this graph.
    */
   public VGraph(Coordinate coord) {
      super();

      this.coord = coord;
      coord.setVGraph(this);
      tops = new AxisSizeList[0];
      bottoms = new AxisSizeList[0];
      lefts = new AxisSizeList[0];
      rights = new AxisSizeList[0];
      topStrategy = new AxisSizeStrategy(ICoordinate.TOP_AXIS);
      bottomStrategy = new AxisSizeStrategy(ICoordinate.BOTTOM_AXIS);
      leftStrategy = new AxisSizeStrategy(ICoordinate.LEFT_AXIS);
      rightStrategy = new AxisSizeStrategy(ICoordinate.RIGHT_AXIS);
      pwidth = new PlotWidth();
      pheight = new PlotHeight();
   }

   /**
    * Paint the top-level VGraph. This can be called on the top-level VGraph to
    * paint the entire graph. The layout() method must be called before paint()
    * is called.
    * @param g the graphics in screen coordinate (0 at top);
    * @param legend true if include legends.
    */
   public void paintGraph(Graphics2D g, boolean legend) {
      final GraphPaintContextImpl ctx = new GraphPaintContextImpl.Builder()
         .paintLegends(legend)
         .build();
      paintGraph(g, ctx);
   }

   /**
    * Paint the top-level VGraph. This can be called on the top-level VGraph to
    * paint the entire graph. The layout() method must be called before paint()
    * is called.
    *
    * @param g the graphics in screen coordinate (0 at top);
    * @param ctx the graph paint context
    */
   public void paintGraph(Graphics2D g, GraphPaintContext ctx) {
      Rectangle2D bounds = getBounds();
      double y = bounds.getY();
      double h = bounds.getHeight();

      // same as layout()
      h--;

      Graphics2D g2 = (Graphics2D) g.create();
      // the vo's y coordinate is in chart (0 at bottom) coordinate, we need to
      // transform the coordinate so the drawing uses the same coordinate
      g2.translate(0, h + 2 * y);
      g2.transform(GDefaults.FLIPY);
      paint(g2, ctx);
      g2.dispose();
   }

   /**
    * Create visuals for this visual graph, including visual object, title, axises.
    * @hidden
    * @param ggraph geometry graph this visual graph based on.
    */
   public void createVisuals(GGraph ggraph) {
      EGraph egraph = ggraph.getEGraph();
      this.graph = egraph;

      // create visual objects
      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         if(isCancelled()) {
            return;
         }

         Visualizable vobj = ggraph.getGeometry(i).createVisual(coord);
         addVisual(vobj);
      }

      for(int i = 0; i < egraph.getFormCount(); i++) {
         GraphForm form = egraph.getForm(i);

         if(!form.isVisible(coord)) {
            continue;
         }

         Visualizable[] vobjs = form.createVisuals(coord);

         for(int j = 0; j < vobjs.length; j++) {
            addVisual(vobjs[j]);
         }
      }

      if(isCancelled()) {
         return;
      }

      dodge(); // dodge and overlay

      if(isCancelled()) {
         return;
      }

      coord.transform(this);
      coord.createAxis(this);
      adjustAxes(coord);

      if(coord.getParentCoordinate() == null) {
         createTitle();
      }

      if(isCancelled()) {
         return;
      }

      createLegends(egraph);

      if(isCancelled()) {
         return;
      }

      tops = createAxisSizeList(false, topStrategy);
      bottoms = createAxisSizeList(false, bottomStrategy);
      lefts = createAxisSizeList(true, leftStrategy);
      rights = createAxisSizeList(true, rightStrategy);
   }

   private void adjustAxes(Coordinate coord) {
      if(coord instanceof FacetCoord) {
         Coordinate[][] arr = ((FacetCoord) coord).getExpandedInnerCoords();

         for(int r = 1; r < arr.length; r++) {
            for(int c = 0; c < arr[r].length; c++) {
               Coordinate inner = arr[r][c];
               Coordinate above = arr[r - 1][c];

               // if the top axis has 0 as min and the axis below has 0 as max,
               // only show one 0 to make it look nicer. (59229)
               if(inner instanceof RectCoord && above instanceof RectCoord) {
                  RectCoord rect = (RectCoord) inner;
                  Scale left = rect.getScaleAt(ICoordinate.LEFT_AXIS);

                  Scale leftAbove = ((RectCoord) above).getScaleAt(ICoordinate.LEFT_AXIS);

                  if(left instanceof LinearScale && left.getMin() == 0 &&
                     leftAbove instanceof LinearScale && leftAbove.getMax() == 0)
                  {
                     DefaultAxis aboveAxis = ((RectCoord) above).getAxisAt(ICoordinate.LEFT_AXIS);

                     if(aboveAxis != null) {
                        aboveAxis.setMaxTickVisible(false);
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Get the unit width. Unit width is used to determine the preferred width
    * of a nested (facet) graph.
    * @hidden
    */
   public double getUnitWidth() {
      return pwidth.getUnit();
   }

   /**
    * Get the unit height. Unit height is used to determine the preferred height
    * of a nested (facet) graph.
    * @hidden
    */
   public double getUnitHeight() {
      return pheight.getUnit();
   }

   /**
    * Create legends in the plot.
    */
   private void createLegends(EGraph graph) {
      this.legendLayout = graph.getLegendLayout();
      this.legendPreferredSize = graph.getLegendPreferredSize();

      if(legendLayout == GraphConstants.NONE || coord.getParentCoordinate() != null) {
         return;
      }

      VisualFrame[] arr = graph.getVisualFrames();

      if(arr.length > 0) {
         vlegends = new LegendGroup(arr, graph);
         vlegends.setLayoutSize(coord.getLayoutSize());
      }
   }

   /**
    * Add an axis object to this graph.
    */
   public void addAxis(Axis axis) {
      axises.add(axis);
   }

   /**
    * Check if array2D contains array.
    * @param array the array.
    * @param array2D the array2d.
    * @return true if array2D contains array.
    */
   private boolean contains(String[] array, String[][] array2D) {
      for(int i = 0; i < array2D.length; i++) {
         if(equals(array, array2D[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if string array1 equsls string array2.
    */
   private boolean equals(String[] arr1, String[] arr2) {
      if(arr1 == null || arr2 == null || arr1.length != arr2.length) {
         return false;
      }

      for(int i = 0; i < arr1.length; i++) {
         if(!arr1[i].equals(arr2[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Set the axis object at specified position.
    */
   public void setAxis(int idx, Axis axis) {
      axises.set(idx, axis);
   }

   /**
    * Remove the axis object at the specified position.
    */
   public void removeAxis(int idx) {
      axises.remove(idx);
   }

   /**
    * Get the specified axis object.
    */
   public Axis getAxis(int idx) {
      return axises.get(idx);
   }

   /**
    * Get the number of axis objects defined in contained in this graph.
    */
   public int getAxisCount() {
      return axises.size();
   }

   /**
    * Create x and y title.
    */
   private void createTitle() {
      TitleSpec xTitleSpec = graph.getXTitleSpec();
      TitleSpec yTitleSpec = graph.getYTitleSpec();
      TitleSpec x2TitleSpec = graph.getX2TitleSpec();
      TitleSpec y2TitleSpec = graph.getY2TitleSpec();

      if(xTitleSpec != null && xTitleSpec.getLabel() != null) {
         xTitle = new VLabel(xTitleSpec.getLabel(), xTitleSpec.getTextSpec());
         xTitle.setInsets(VLabel.getTransformedInsets(
            new Insets(xTitleSpec.getLabelGap(), 0, 0, 0),
            xTitleSpec.getTextSpec().getRotation()));
      }

      if(x2TitleSpec != null && x2TitleSpec.getLabel() != null) {
         x2Title = new VLabel(x2TitleSpec.getLabel(), x2TitleSpec.getTextSpec());
         x2Title.setInsets(VLabel.getTransformedInsets(
            new Insets(0, 0, x2TitleSpec.getLabelGap(), 0),
            x2TitleSpec.getTextSpec().getRotation()));
      }

      if(yTitleSpec != null && yTitleSpec.getLabel() != null) {
         yTitle = new VLabel(yTitleSpec.getLabel(), yTitleSpec.getTextSpec());
         yTitle.setInsets(VLabel.getTransformedInsets(
            new Insets(0, 0, 0, yTitleSpec.getLabelGap()),
            yTitleSpec.getTextSpec().getRotation()));
      }

      if(y2TitleSpec != null && y2TitleSpec.getLabel() != null) {
         y2Title = new VLabel(y2TitleSpec.getLabel(), y2TitleSpec.getTextSpec());
         y2Title.setInsets(VLabel.getTransformedInsets(
            new Insets(0, y2TitleSpec.getLabelGap(), 0, 0),
            y2TitleSpec.getTextSpec().getRotation()));
      }
   }

   /**
    * Move the visual objects to avoid overlapping.
    */
   private void dodge() {
      BitSet moved = new BitSet();
      Map<ElementVO,List<ElementVO>> map = new Object2ObjectOpenHashMap<>();
      boolean dodgeRequired = false;

      for(int i = 0; i < getVisualCount(); i++) {
         Visualizable visual = getVisual(i);

         if(moved.get(i)) {
            continue;
         }

         if(!(visual instanceof ElementVO)) {
            continue;
         }

         ElementVO vo = (ElementVO) visual;

         if(!vo.requiresDodge()) {
            continue;
         }

         List<ElementVO> overlapped = new ArrayList<>();

         for(int j = i + 1; j < getVisualCount(); j++) {
            if(moved.get(j)) {
               continue;
            }

            Visualizable visual2 = getVisual(j);

            if(!(visual2 instanceof ElementVO)) {
               continue;
            }

            ElementVO v2 = (ElementVO) visual2;

            if(!(v2.getGeometry() instanceof ElementGeometry)) {
               continue;
            }

            if(vo.requiresDodge(v2)) {
               overlapped.add(v2);
               moved.set(j);
            }
         }

         dodgeRequired = dodgeRequired || overlapped.size() > 0 || vo.requiresDodge(vo);
         map.put(vo, overlapped);
      }

      if(dodgeRequired) {
         // dodge overlappled visual objects
         for(ElementVO vo : map.keySet()) {
            vo.dodge(map, coord);
         }
      }

      // overlay visual object requires overlay
      for(int i = 0; i < getVisualCount(); i++) {
         Visualizable visual = getVisual(i);

         if(!(visual instanceof ElementVO)) {
            continue;
         }

         ElementVO vo = (ElementVO) visual;

         if(!(vo.getGeometry() instanceof ElementGeometry)) {
            continue;
         }

         ElementGeometry gobj = (ElementGeometry) vo.getGeometry();
         GraphElement elem = gobj.getElement();

         if(!requiresOverlay(elem)) {
            continue;
         }

         ElementVO vo2 = getOverlayVO(vo);

         if(vo2 != null) {
            vo.overlay(vo2);
         }
      }

      sort3DBar();
   }

   /**
    * If chart style is 3D Bar, the order should be x ascending if
    * non-stacked, and y ascending if stacked.
    */
   private void sort3DBar() {
      List<Visualizable> vos = new ArrayList<>();
      List<Integer> idxs = new ArrayList<>();

      for(int i = 0; i < getVisualCount(); i++) {
         Visualizable visual = getVisual(i);

         if(visual instanceof Bar3DVO) {
            vos.add(visual);
            idxs.add(Integer.valueOf(i));
         }
      }

      if(vos.size() > 0) {
         Collections.sort(vos, (obj1, obj2) -> {
            ElementGeometry geom = (ElementGeometry)
               ((ElementVO) obj1).getGeometry();
            GraphElement elem = geom.getElement();
            Rectangle2D b1 = obj1.getBounds();
            Rectangle2D b2 = obj2.getBounds();
            boolean stack = elem.isStack();
            double c1 = Math.round(b1.getX() + b1.getWidth() / 2);
            double c2 = Math.round(b2.getX() + b2.getWidth() / 2);

            if(stack && c1 == c2) {
               // @by davidd 2009-01-16 if is a stacked Bar3D then compare
               // y. Overlays should have the same location as the overlaid
               // element and compare should return 0.
               c1 = b1.getY();
               c2 = b2.getY();
            }

            return Double.compare(c1, c2);
         });

         for(int i = 0; i < idxs.size(); i++) {
            setVisual(idxs.get(i), vos.get(i));
         }
      }
   }

   /**
    * Get the visual object to be overlaid.
    */
   private ElementVO getOverlayVO(ElementVO vo) {
      ElementGeometry g = (ElementGeometry) vo.getGeometry();
      GraphElement elem = g.getElement();
      String key = createKey(vo);

      for(int i = 0; i < getVisualCount(); i++) {
         if(!(getVisual(i) instanceof ElementVO)) {
            continue;
         }

         ElementVO vo2 = (ElementVO) getVisual(i);

         if(!(vo2.getGeometry() instanceof ElementGeometry)) {
            continue;
         }

         ElementGeometry g2 = (ElementGeometry) vo2.getGeometry();
         GraphElement elem2 = g2.getElement();

         if(!elem2.getClass().equals(elem.getClass())) {
            continue;
         }

         if(vo2.getHint("overlay") != null) {
            continue;
         }

         if(vo.getHint("overlay") != elem2) {
            continue;
         }

         String key2 = createKey(vo2);

         if(key2.equals(key)) {
            return vo2;
         }
      }

      return null;
   }

   /**
    * Create the key of the element visual object.
    */
   private String createKey(ElementVO vo) {
      ElementGeometry g = (ElementGeometry) vo.getGeometry();
      DataSet data = coord.getDataSet();
      return g.getOverlayId(vo, data);
   }

   /**
    * Check if overlay is required.
    */
   private boolean requiresOverlay(GraphElement elem) {
      if(!elem.supportsOverlay()) {
         return false;
      }

      // do not dodge overlay element
      return elem.getHint("overlay") != null;
   }

   /**
    * Layout the graph to prepare for painting. This method must be called once
    * before the paintGraph() method is called.
    */
   public void layout(int x, int y, int w, int h) {
      setBounds(x, y, w, h);
      // java drawRect(0, 0, w, h) actually draws outside of the bounds
      w--; h--;

      layout0(x, y, w, h);
   }

   /**
    * Layout visual graph.
    */
   private void layout0(double x, double y, double w, double h) {
      // title shouldn't take up all space
      if(xTitle != null) {
         xTitle.setMaxSize(new DimensionD(w / 3, h / 3));
      }

      if(x2Title != null) {
         x2Title.setMaxSize(new DimensionD(w / 3, h / 3));
      }

      if(yTitle != null) {
         yTitle.setMaxSize(new DimensionD(w / 3, h / 3));
      }

      if(y2Title != null) {
         y2Title.setMaxSize(new DimensionD(w / 3, h / 3));
      }

      // legends
      double vlegendsw;
      double vlegendsh;

      if(vlegends != null) {
         Dimension2D asize = new DimensionD(w, h);

         if(legendLayout == GraphConstants.LEFT ||
            legendLayout == GraphConstants.RIGHT)
         {
            asize = new DimensionD(w / 2, h);
         }
         else if(legendLayout == GraphConstants.TOP ||
                 legendLayout == GraphConstants.BOTTOM)
         {
            asize = new DimensionD(w, h / 2);
         }

         vlegends.setLayoutSize(asize);
      }

      if(vlegends == null || legendLayout == GraphConstants.NONE) {
         vlegendsw = 0;
         vlegendsh = 0;
      }
      else if(legendLayout == GraphConstants.IN_PLACE) {
         vlegendsw = 0;
         vlegendsh = 0;

         double legendx = x;
         int count = vlegends.getLegendCount();
         count = count > 0 ? count : 1;
         double maxLegendWidth = (w - count * GAP) / count;

         for(int i = 0; i < vlegends.getLegendCount(); i++) {
            // make sure the legend will not out of the content bound
            Legend legend = vlegends.getLegend(i);

            double legendw = legend.getPreferredWidth();
            double legendh = legend.getPreferredHeight();
            legendw = Math.min(legendw, maxLegendWidth);
            legendh = Math.min(legendh, h);
            double legendy = y + h - legendh;

            legend.setBounds(legendx, legendy, legendw, legendh);
            legend.layout();
            legendx = legendx + legendw + GAP;
         }
      }
      else {
         double legendsSize = legendPreferredSize;
         double legendPercent;
         double legendW = w;
         double legendH = h;

         if(legendLayout == GraphConstants.TOP ||
            legendLayout == GraphConstants.BOTTOM)
         {
            if(legendsSize > 0) {
               // is proportion
               legendPercent = legendsSize <= 1 ? legendsSize : legendsSize / h;
            }
            else {
               legendPercent = vlegends.getPreferredHeight() / legendH;
            }

            vlegendsw = legendW;
            legendPercent = Math.min(legendPercent, 0.5);
            vlegendsh = legendPercent * legendH;
            vlegendsh = Math.max(vlegendsh, vlegends.getMinHeight());
         }
         else {
            if(legendsSize > 0) {
               legendPercent = legendsSize <= 1 ? legendsSize : legendsSize / w;
            }
            else {
               legendPercent = vlegends.getPreferredWidth() / legendW;
            }

            vlegendsh = legendH;
            legendPercent = Math.min(legendPercent, 0.5);
            vlegendsw = legendPercent * legendW;
            vlegendsw = Math.max(vlegendsw, vlegends.getMinWidth());
         }

         boolean affectx = legendLayout == GraphConstants.RIGHT;
         boolean affecty = legendLayout != GraphConstants.BOTTOM;
         double vlegendsx = affectx ? x + w - vlegendsw : x;
         double vlegendsy = affecty ? y + h - vlegendsh : y;

         vlegends.setBounds(vlegendsx, vlegendsy, vlegendsw, vlegendsh);
         vlegends.layout();
      }

      // content
      boolean affectx = vlegends != null && legendLayout == GraphConstants.LEFT;
      boolean affecty = vlegends != null && legendLayout == GraphConstants.BOTTOM;
      boolean affectw = vlegends != null &&
         (legendLayout == GraphConstants.LEFT ||
          legendLayout == GraphConstants.RIGHT);
      boolean affecth = vlegends != null &&
         (legendLayout == GraphConstants.TOP ||
          legendLayout == GraphConstants.BOTTOM);
      double contentx = affectx ? x + vlegendsw + GAP : x;
      double contenty = affecty ? y + vlegendsh + GAP : y;
      double contentw = affectw ? w - vlegendsw - GAP : w;
      double contenth = affecth ? h - vlegendsh - GAP : h;

      contentBounds = new Rectangle2D.Double(contentx, contenty, contentw,
                                             contenth);

      // used to set axis label max size when calculating preferred size
      coord.setCoordBounds(new Rectangle2D.Double(contentx, contenty,
                                                  contentw, contenth));

      SizeManager wmanager = SizeManager.width(
         contentw, new ILayout[] {yTitle, y2Title, coord}, coord);
      SizeManager hmanager = SizeManager.height(
         contenth, new ILayout[] {xTitle, x2Title, coord}, coord);

      // coord
      int xtitleh = xTitle == null ? 0 : (int) hmanager.getSize(xTitle);
      int ytitlew = yTitle == null ? 0 : (int) wmanager.getSize(yTitle);
      int x2titleh = x2Title == null ? 0 : (int) hmanager.getSize(x2Title);
      int y2titlew = y2Title == null ? 0 : (int) wmanager.getSize(y2Title);
      int coordx = (int) contentx + ytitlew;
      int coordy = (int) contenty + xtitleh;
      int coordw = (int) contentw - ytitlew - y2titlew;
      int coordh = (int) contenth - xtitleh - x2titleh;

      coord.setCoordBounds(new Rectangle2D.Double(coordx, coordy, coordw, coordh));

      // fit coord
      fitCoord(coordx, coordy, coordw, coordh);

      // x title
      if(xTitle != null) {
         setXTitleBounds(xTitle, plotx, contenty, plotw, xtitleh);
      }

      if(x2Title != null) {
         setXTitleBounds(x2Title, plotx, coordy + coordh + 1, plotw, x2titleh);
      }

      // y title
      if(yTitle != null) {
         setYTitleBounds(yTitle, contentx, ploty, ytitlew, ploth);
      }

      if(y2Title != null) {
         setYTitleBounds(y2Title, coordx + coordw + 1, ploty, y2titlew, ploth);
      }

      // layout the legend position exactly after coordinate is ready
      if(legendLayout == GraphConstants.IN_PLACE && vlegends != null) {
         // to apply LegendDescriptor, first to calculate all legneds place,
         // then to apply specifical legend to its setting place
         for(int i = 0; i < vlegends.getLegendCount(); i++) {
            Legend legend = vlegends.getLegend(i);
            VisualFrame legendFrame = legend.getVisualFrame();

            if(legendFrame == null) {
               continue;
            }

            LegendSpec spec = legendFrame.getLegendSpec();
            // pos is proportion, also not need to change to absolute
            // point, GTool.transformFixedPosition will maintain it
            Point2D epos = spec.getPlotPosition();
            boolean plotRatio = epos != null;
            Point2D pos = plotRatio ? epos : spec.getPosition();
            Dimension2D size = spec.getPreferredSize();

            if(size != null) {
               // proportion size
               if(size.getWidth() <= 1 && size.getHeight() <= 1) {
                  size = new DimensionD(w * size.getWidth(),
                                        h * size.getHeight());
               }

               double lw = Math.min(size.getWidth(), w);
               double lh = Math.min(size.getHeight(), h);
               legend.setSize(size = new DimensionD(lw, lh));
            }

            if(pos != null) {
               pos = GTool.transformFixedPosition(coord, pos, plotRatio);
               Dimension2D sz = legend.getSize();
               double px = pos.getX();
               double py = pos.getY() - (plotRatio || spec.isTopY() ? sz.getHeight() : 0);
               double rx = x + w - sz.getWidth();
               double ry = y + h - sz.getHeight();

               rx = Math.min(rx, px);
               rx = Math.max(rx, x);
               ry = Math.min(ry, py);
               ry = Math.max(ry, y);
               pos = new Point2D.Double(rx, ry);
               double lw = Math.min(sz.getWidth(), x + w - rx);
               double lh = Math.min(sz.getHeight(), y + h - ry);
               legend.setPosition(pos);
               legend.setSize(new Dimension((int) lw, (int) lh));
            }
            // this state, should reset the y for the legend
            else if(size != null) {
               Point2D legendpos = legend.getPosition();
               double legendy = y + h - legend.getSize().getHeight();
               legend.setPosition(new Point2D.Double(legendpos.getX(), legendy));
            }

            legend.layout();
         }
      }

      coord.layoutCompleted();
      this.layoutCompleted();
   }

   /**
    * Setup x title.
    */
   private void setXTitleBounds(VLabel xTitle, double x, double y,
                                double w, double h)
   {
      if(xTitle == null) {
         return;
      }

      // 2.5d title width include depth which is outside of graph area
      w = Math.min(w, getBounds().getWidth() - x);

      xTitle.setBounds(x, y, w, h);
      xTitle.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
      xTitle.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);

      Point2D pt0 = xTitle.getPosition();
      Point2D pt = new Point2D.Double(x + w / 2, pt0.getY());
      Point2D offset = xTitle.getRotationOffset(pt, GraphConstants.TOP);
      xTitle.setOffset(offset);

      // center xtitle
      if(offset.getX() != 0 || offset.getY() != 0) {
         xTitle.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
         xTitle.alignOffset(x, y, w, h);
      }
   }

   /**
    * Setup y title.
    */
   private void setYTitleBounds(VLabel yTitle, double x, double y,
                                double w, double h)
   {
      if(yTitle == null) {
         return;
      }

      yTitle.setBounds(x, y, w, h);
      yTitle.setAlignmentX(GraphConstants.CENTER_ALIGNMENT);
      yTitle.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);

      Point2D pt = new Point2D.Double(x, y + h / 2);
      Point2D offset = yTitle.getRotationOffset(pt, GraphConstants.RIGHT);
      yTitle.setOffset(offset);

      // center ytitle
      if(offset.getX() != 0 || offset.getY() != 0) {
         yTitle.setAlignmentY(GraphConstants.MIDDLE_ALIGNMENT);
         yTitle.alignOffset(x, y, w, h);
      }
   }

   /**
    * Fit the coord.
    */
   private void fitCoord(int coordx, int coordy, int coordw, int coordh) {
      AxisSizeList[][] all = {lefts, rights, tops, bottoms};

      // need to recalcuate axis size with the real coord size since
      // the label preferred size may depend on the coord size
      for(AxisSizeList[] sizes : all) {
         for(AxisSizeList size : sizes) {
            size.invalidate();
         }
      }

      // create size manager for width and height
      calculateAxisSizes(lefts, rights, pwidth, coordw);
      calculateAxisSizes(tops, bottoms, pheight, coordh);

      // now size is defined, we may continue the fit process
      coord.fit(coordx, coordy, coordw, coordh);

      // clear tops, bottoms, lefts and rights to release memory
      tops = new AxisSizeList[0];
      bottoms = new AxisSizeList[0];
      lefts = new AxisSizeList[0];
      rights = new AxisSizeList[0];

      if(coord instanceof FacetCoord) {
         GTool.scaleToSizeFactor(this, GTool.getMinSizeFactor(this));
      }
   }

   /**
    * Calculate the plot and axis sizes.
    */
   private void calculateAxisSizes(AxisSizeList[] lefts, AxisSizeList[] rights,
                                   ISize plotw, double coordw)
   {
      // create size manager for width
      List<ISize> wlist = new ArrayList<>();
      wlist.addAll(Arrays.asList(lefts));
      wlist.addAll(Arrays.asList(rights));
      wlist.add(plotw);

      ISize[] warr = wlist.toArray(new ISize[0]);
      SizeManager wmanager = SizeManager.size(coordw, warr, plotw);

      // set size to left axis
      for(AxisSizeList left : lefts) {
         double val = wmanager.getSize(left);
         left.setSize(val);
      }

      // set size to right axis
      for(AxisSizeList right : rights) {
         double val = wmanager.getSize(right);
         right.setSize(val);
      }

      // set size to plot
      double val = wmanager.getSize(plotw);
      plotw.setSize(val);
   }

   /**
    * Paint all visual objects in the container. Graphics is already
    * transformed to be at the top-left corner of the container.
    * The paintGraph method should normally be called to paint a graph on
    * the graphics output.
    *
    * @param g the graphics in graph coordinate (0 at bottom);
    * @param ctx the graph paint context
    */
   public void paint(Graphics2D g, GraphPaintContext ctx) {
      if(ctx.paintTitles()) {
         if(xTitle != null && xTitle.getZIndex() >= 0) {
            xTitle.paint(g);
         }

         if(x2Title != null && x2Title.getZIndex() >= 0) {
            x2Title.paint(g);
         }

         if(yTitle != null && yTitle.getZIndex() >= 0) {
            yTitle.paint(g);
         }

         if(y2Title != null && y2Title.getZIndex() >= 0) {
            y2Title.paint(g);
         }
      }

      paintPlotBackground(g, coord.getPlotSpec());

      // sort visuals
      List<Visualizable> all = new ObjectArrayList<>(visuals);
      all.addAll(axises);
      paintVisualizables(g, all, ctx);

      // paint legends
      if(vlegends != null && ctx.paintLegends()) {
         vlegends.paint(g);
      }
   }

   /**
    * Get the background created by BackGroundPainter.
    */
   public Image getOrCreatePlotBackground() throws IOException {
      PlotSpec spec = coord.getPlotSpec();

      if(spec.getBackgroundPainter() != null) {
         return spec.getBackgroundPainter().getImage(this);
      }

      return getPlotBackground();
   }

   /**
    * Paint the plot background.
    */
   private void paintPlotBackground(Graphics2D g, PlotSpec spec) {
      if(spec == null) {
         return;
      }

      Image img = spec.getBackgroundImage();
      BackgroundPainter bgPainter = spec.getBackgroundPainter();
      double plotx = this.plotx;
      double ploty = this.ploty;
      double plotw = this.plotw;
      double ploth = this.ploth;

      if(img instanceof ObjectWrapper) {
         img = (Image) ((ObjectWrapper) img).unwrap();
      }

      if(bgPainter != null) {
         try {
            bgPainter.paint(g, this);
         }
         catch(IOException ex) {
            LOG.warn("Failed to draw chart background: " + bgPainter, ex);
         }
      }
      else if(img != null) {
         CoreTool.waitForImage(img);

         double xmin = spec.getXMin();
         double xmax = spec.getXMax();
         double ymin = spec.getYMin();
         double ymax = spec.getYMax();
         int iw = img.getWidth(null);
         int ih = img.getHeight(null);

         if(spec.getAlpha() < 1) {
            img = applyAlpha(img, spec.getAlpha());
         }

         // if the image is mapped to scale position, truncate the image to the
         // corresponding position as plotted on the graph
         if(!Double.isNaN(xmin) && !Double.isNaN(xmax) &&
            !Double.isNaN(ymin) && !Double.isNaN(ymax) &&
            coord instanceof RectCoord)
         {
            DefaultAxis yaxis = ((RectCoord) coord).getAxisAt(
               ICoordinate.LEFT_AXIS);
            DefaultAxis xaxis = ((RectCoord) coord).getAxisAt(
               ICoordinate.BOTTOM_AXIS);

            if(xaxis != null && yaxis != null) {
               Scale xscale = xaxis.getScale();
               Scale yscale = yaxis.getScale();
               double xmin0 = xscale.getMin();
               double xmax0 = xscale.getMax();
               double ymin0 = yscale.getMin();
               double ymax0 = yscale.getMax();

               double subw = (xmax0 - xmin0) * iw / (xmax - xmin);
               double subh = (ymax0 - ymin0) * ih / (ymax - ymin);
               double subx = (xmin - xmin0) * subw / (xmax0 - xmin0);
               double suby = (ymin - ymin0) * subh / (ymax0 - ymin0);

               // change to java coord
               suby = subh - (suby + ih);

               Point2D x0 = new Point2D.Double(0, 0);
               Point2D x2 = new Point2D.Double(xaxis.getLength(), 0);
               Point2D y0 = new Point2D.Double(0, 0);
               Point2D y2 = new Point2D.Double(yaxis.getLength(), 0);

               // use the real plot (subtract the re-scaled space) for plot
               // bg image to map image position to scale
               x0 = xaxis.getScreenTransform().transform(x0, x0);
               x2 = xaxis.getScreenTransform().transform(x2, x2);
               y0 = yaxis.getScreenTransform().transform(y0, y0);
               y2 = yaxis.getScreenTransform().transform(y2, y2);
               plotx = x0.getX();
               plotw = x2.getX() - x0.getX();
               ploty = y0.getY();
               ploth = y2.getY() - y0.getY();

               if(subw * subh > Integer.MAX_VALUE) {
                  throw new RuntimeException("Image size is too large. " +
                     "Try calling setScaleOption(0) on all scales.");
               }

               Image nimg = new BufferedImage((int) subw, (int) subh,
                                              BufferedImage.TYPE_INT_ARGB);
               Graphics2D g2 = (Graphics2D) nimg.getGraphics();

               g2.drawImage(img, (int) subx, (int) suby, null);
               g2.dispose();
               img = nimg;
               iw = (int) subw;
               ih = (int) subh;
            }
         }

         if(spec.isLockAspect()) {
            double ratio = iw / (double) ih;
            double ratio0 = plotw / ploth;
            double pw = plotw;
            double ph = ploth;

            if(ratio > ratio0) {
               pw = ratio * ploth;
            }
            else if(ratio < ratio0) {
               ph = plotw / ratio;
            }

            g = (Graphics2D) g.create();
            g.clip(getPlotBounds());

            GraphTool.drawImage(g, img, plotx, ploty, pw, ph);
            g.dispose();
         }
         else {
            GraphTool.drawImage(g, img, plotx, ploty, plotw, ploth);
         }
      }
      else if(spec.getBackground() != null) {
         Color clr = spec.getBackground();

         if(spec.getAlpha() < 1) {
            clr = GTool.applyAlpha(clr, spec.getAlpha());
         }

         g.setColor(clr);
         g.fill(getPlotBounds());
      }

      double dep = getDepth(coord);
      Rectangle clip = g.getClipBounds();

      if(spec.getXBandColor() != null) {
         int units = GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, true);

         // if facet is not nested on this direction, apply banding to the inner coord.
         if(units == 1 && coord instanceof FacetCoord) {
            Coordinate[][] inners = ((FacetCoord) coord).getExpandedInnerCoords();

            if(inners.length > 0 && inners[0].length == 1) {
               units = GTool.getUnitCount(inners[0][0], Coordinate.BOTTOM_AXIS, true);
            }
         }

         double bandw = Math.max((plotw - dep) * spec.getXBandSize() / units, 10);

         g.setColor(spec.getXBandColor());

         for(double x = bandw + dep; x < plotw + dep; x += bandw * 2) {
            double w = x + bandw > plotw ? plotw - x : bandw;

            // check clipping explicitly since svg graphics would paint without
            // checking for clipping and resulting in performance problem
            if(clip != null && (plotx + x > clip.getMaxX() || plotx + x + w < clip.getMinX())) {
               continue;
            }

            g.fill(new Rectangle2D.Double(plotx + x, ploty + dep, w, ploth - dep));
         }
      }

      if(spec.getYBandColor() != null) {
         int units = GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, true);

         // if facet is not nested on this direction, apply banding to the inner coord.
         if(units == 1 && coord instanceof FacetCoord) {
            Coordinate[][] inners = ((FacetCoord) coord).getExpandedInnerCoords();

            if(inners.length == 1) {
               units = GTool.getUnitCount(inners[0][0], Coordinate.LEFT_AXIS, true);
            }
         }

         double bandw = Math.max((ploth - dep) * spec.getYBandSize() / units, 10);

         g.setColor(spec.getYBandColor());

         for(double y = bandw + dep; y < ploth + dep; y += bandw * 2) {
            double h = y + bandw > ploth ? ploth - y : bandw;

            if(clip != null && (ploty + y > clip.getMaxY() || ploty + y + h < clip.getMinY())) {
               continue;
            }

            g.fill(new Rectangle2D.Double(plotx + dep, ploty + y, plotw - dep, h));
         }
      }
   }

   /**
    * Get the depth of coordinate.
    */
   private double getDepth(Coordinate coord) {
      if(coord instanceof RectCoord) {
         return ((RectCoord) coord).getDepth();
      }
      else if(coord instanceof FacetCoord) {
         return ((FacetCoord) coord).getDepth();
      }

      return 0;
   }

   /**
    * Set the alpha in the image.
    */
   private Image applyAlpha(Image img0, double alpha) {
      BufferedImage img = CoreTool.getBufferedImage(img0);
      BufferedImage img2 = new BufferedImage(img.getWidth(), img.getHeight(),
                                             BufferedImage.TYPE_INT_ARGB);
      int width = img.getWidth();
      int[] imgData = new int[width];
      int mask = ((int) (alpha * 255)) << (6 * 4);

      for(int y = 0; y < img.getHeight(); y++) {
         img.getRGB(0, y, width, 1, imgData, 0, 1);

         for(int x = 0; x < width; x++) {
            int color = imgData[x] & 0x00FFFFFF;
            color |= mask;
            imgData[x] = color;
         }

         img2.setRGB(0, y, width, 1, imgData, 0, 1);
      }

      return img2;
   }

   @Override
   protected void paintVisualizables(Graphics2D g, List<Visualizable> visuals,
                                     GraphPaintContext ctx)
   {
      ArrayList<Visualizable> sorted = new ArrayList<>(visuals);
      Collections.sort(sorted);
      Rectangle2D pbounds = getPlotBounds();
      Rectangle clipBounds = g.getClipBounds();
      final Shape oclip = g.getClip();
      boolean clipped = false;

      for(int i = 0; i < sorted.size(); i++) {
         Visualizable visual = sorted.get(i);
         Rectangle2D visualBounds = visual.getBounds();

         if(visual.getZIndex() < 0 || !insideClip(clipBounds, visualBounds, visual) ||
            !ctx.paintVisual(visual))
         {
            continue;
         }

         Graphable elem = visual.getGraphable();

         if(elem == null && visual instanceof VOText) {
            elem = ((VOText) visual).getGraphElement();
         }

         // if element is inPlot, it may still be outside of plot area. for example, point
         // may be bigger than the plot area, so it can't be moved 'inside'. we force to
         // clip in this case. it should be ok since inPlot should never be drawn outside of
         // the plot area anyway.

         // if in-plot is false, the points may be drawn outside of vgraph bounds. in case
         // of facet, a point could be drawn in a completely different sub-graph if the
         // min/max is explicitly set.
         /*
         boolean clip = elem != null && (elem.isInPlot() ||
            "true".equals(elem.getHint(GraphElement.HINT_CLIP)));
         */

         // see the above comments. since it doesn't make sense to not clip regardless of
         // whether element is in-plot or not, we only ignore clipping if explicitly disabled.
         // currently we don't set the hint to false in our cod, so it would only be false
         // if set through script.
         // don't clip Form unless explicitly specified since it can be ouside of plot.
         boolean clip = elem instanceof GraphElement &&
            !"false".equals(elem.getHint(GraphElement.HINT_CLIP)) ||
            elem != null && "true".equals(elem.getHint(GraphElement.HINT_CLIP));

         if(visual instanceof ElementVO) {
            boolean inPlot = elem != null && elem.isInPlot();

            if(!inPlot && clip) {
               Rectangle2D ebounds = visual.getBounds();

               // @by billh, fix customer bug bug1296849302682
               // vo is invisible? set its votext as invisible
               if(ebounds != null && !ebounds.intersects(pbounds.getX(), pbounds.getY(),
                                                         pbounds.getWidth(), pbounds.getHeight()))
               {
                  VOText vtext = ((ElementVO) visual).getVOText();

                  if(vtext != null) {
                     vtext.setZIndex(-1);
                  }
               }
            }
         }

         // elem clipping requirement changed
         if(clipped != clip) {
            // not yet clipped, clip plot bounds
            if(clip) {
               // add one to not clip out border line. (57050)
               g.clip(new Rectangle2D.Double(pbounds.getX(), pbounds.getY(),
                                             pbounds.getWidth() + 1, pbounds.getHeight() + 1));
            }
            // no clipping needed, restore
            else {
               g.setClip(oclip);
            }

            clipped = clip;
         }

         // don't clip the graphics here since some coord may not be rescaled
         // and the vo may be drawn partly outside of the plot area.
         if(visual instanceof GraphVO) {
            ((GraphVO) visual).paint(g, ctx);
         }
         else {
            visual.paint(g);
         }
      }

      g.setClip(oclip);
   }

   /**
    * Add visual and its children to the list.
    */
   private void addVisual(List<Visualizable> visuals, Visualizable visual) {
      if(visual.getZIndex() < 0) {
         return;
      }

      if(visual instanceof GraphVO) {
         GraphVO graphvo = (GraphVO) visual;
         VGraph vgraph = graphvo.getVGraph();

         for(int i = 0; i < vgraph.getVisualCount(); i++) {
            addVisual(visuals, vgraph.getVisual(i));
         }

         for(int i = 0; i < vgraph.getAxisCount(); i++) {
            addVisual(visuals, vgraph.getAxis(i));
         }
      }
      else {
         visuals.add(visual);
      }
   }

   /**
    * Get the x axis title.
    */
   public VLabel getXTitle() {
      return xTitle;
   }

   /**
    * Get the secondary x axis title.
    */
   public VLabel getX2Title() {
      return x2Title;
   }

   /**
    * Get the y axis title.
    */
   public VLabel getYTitle() {
      return yTitle;
   }

   /**
    * Get the secondary y axis title.
    */
   public VLabel getY2Title() {
      return y2Title;
   }

   /**
    * Set plot bounds. The plot bounds is the area between axes, excluding
    * title, axis, and legend.
    */
   public void setPlotBounds(Rectangle2D rect) {
      this.plotx = rect.getX();
      this.ploty = rect.getY();
      this.plotw = rect.getWidth();
      this.ploth = rect.getHeight();
   }

   /**
    * Get the minimum width.
    */
   @Override
   protected double getMinWidth0() {
      return getContentMinWidth() + getVLegendsMinWidth();
   }

   /**
    * Get visual legends min width.
    */
   private double getVLegendsMinWidth() {
      boolean contains = vlegends != null &&
         (legendLayout == GraphConstants.RIGHT ||
          legendLayout == GraphConstants.LEFT);
      return contains ? vlegends.getMinWidth() : 0;
   }

   /**
    * Get the minimum height.
    */
   @Override
   protected double getMinHeight0() {
      return getContentMinHeight() + getVLegendsMinHeight();
   }

   /**
    * Get content minimum height.
    */
   private double getContentMinHeight() {
      double xTitleH = xTitle == null ? 0 : xTitle.getMinHeight();
      double x2TitleH = x2Title == null ? 0 : x2Title.getMinHeight();
      double xAxis1H = getBottomAxisMinHeight();
      double plotH = coord.getUnitMinHeight() *
         GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
      double xAxis2H = getTopAxisMinHeight();

      return xTitleH + xAxis1H + xAxis2H + x2TitleH +
         Math.min(plotH, getMaxPlotHeight());
   }

   /**
    * Get content minimum width.
    */
   private double getContentMinWidth() {
      double yTitleW = yTitle == null ? 0 : yTitle.getMinWidth();
      double y2TitleW = y2Title == null ? 0 : y2Title.getMinWidth();
      double yAxis1W = getLeftAxisMinWidth();
      double plotW = coord.getUnitMinWidth() *
         GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
      double yAxis2W = getRightAxisMinWidth();

      return yTitleW + yAxis1W + yAxis2W + y2TitleW +
         Math.min(plotW, getMaxPlotWidth());
   }

   /**
    * Get visual lengends min height.
    */
   private double getVLegendsMinHeight() {
      boolean contains = vlegends != null &&
         (legendLayout == GraphConstants.TOP ||
          legendLayout == GraphConstants.BOTTOM);
      return contains ? vlegends.getMinHeight() : 0;
   }

   /**
    * Get graph preferred width.
    */
   @Override
   protected double getPreferredWidth0() {
      return getPreferredSize().getWidth();
   }

   /**
    * Get preferred size.
    */
   private DimensionD getPreferredSize() {
      String mstr = GTool.getProperty("graph.maxarea", GDefaults.GRAPH_MAX_AREA + "");
      double maxArea = Double.parseDouble(mstr);
      double pw = getContentPreferredWidth() + getVLegendsPreferredWidth();
      double ph = getContentPreferredHeight() + getVLegendsPreferredHeight();

      // avoid too large size
      if(pw * ph > maxArea) {
         double maxSize = maxArea / 1000;

         if(pw < ph) {
            pw = pw > maxSize ? maxSize : pw;
            ph = maxArea / pw;
         }
         else {
            ph = ph > maxSize ? maxSize : ph;
            pw = maxArea / ph;
         }

         String msg =
            GTool.getString("viewer.viewsheet.chart.reduceWidthHeight", pw, ph) +
            ": " + GTool.getGraphId(coord);
         //CoreTool.addUserMessage(msg);
         // showing a warning to end user is more distractive than
         // helpful. in any case, when the max is reached, the graph
         // is generally so over crowed that any truncation is like
         // likely noticeable or significant
         LOG.warn(msg);
      }

      return new DimensionD(pw, ph);
   }

   /**
    * Get content preferred width.
    */
   private double getContentPreferredWidth() {
      double yTitleWidth = yTitle == null ? 0 : yTitle.getPreferredWidth();
      double yAxis1Width = getLeftAxisPreferredWidth();
      double plotWidth = coord.getUnitPreferredWidth() *
         GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
      double yAxis2Width = getRightAxisPreferredWidth();

      return yTitleWidth + yAxis1Width + yAxis2Width +
         Math.min(plotWidth, getMaxPlotWidth());
   }

   /**
    * Get the min plot width.
    */
   public double getMinPlotWidth() {
      return Math.max(pwidth.getMinSize(), 0);
   }

   /**
    * Get the min plot height.
    */
   public double getMinPlotHeight() {
      return Math.max(pheight.getMinSize(), 0);
   }

   /**
    * Get visual legends preferred width.
    */
   private double getVLegendsPreferredWidth() {
      boolean contains = vlegends != null &&
         (legendLayout == GraphConstants.RIGHT ||
          legendLayout == GraphConstants.LEFT);
      return contains ? vlegends.getPreferredWidth() : 0;
   }

   /**
    * Get graph preferred height.
    */
   @Override
   protected double getPreferredHeight0() {
      return getPreferredSize().getHeight();
   }

   /**
    * Get content preferred height.
    */
   private double getContentPreferredHeight() {
      double yTitleHeight = yTitle == null ? 0 : yTitle.getMinHeight();
      double yAxis1Height = getBottomAxisPreferredHeight();
      double plotHeight = coord.getUnitPreferredHeight() *
         GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
      double yAxis2Height = getTopAxisPreferredHeight();

      return yTitleHeight + yAxis1Height + yAxis2Height +
         Math.min(plotHeight, getMaxPlotHeight());
   }

   /**
    * Get visual legends preferred height.
    */
   private double getVLegendsPreferredHeight() {
      boolean contains = vlegends != null &&
         (legendLayout == GraphConstants.TOP ||
          legendLayout == GraphConstants.BOTTOM);
      return contains ? vlegends.getPreferredHeight() : 0;
   }

   /**
    * Get the maximum plot width.
    */
   private double getMaxPlotWidth() {
      return getMaxPlotSize(true);
   }

   /**
    * Get the maximum plot height.
    */
   private double getMaxPlotHeight() {
      return getMaxPlotSize(false);
   }

   /**
    * Get the maximum plot width/height.
    */
   private double getMaxPlotSize(boolean width) {
      double max = Double.MAX_VALUE;

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);
         String hint = width ? GraphElement.HINT_MAX_WIDTH :
            GraphElement.HINT_MAX_HEIGHT;
         Object w = elem.getHint(hint);

         if(w != null) {
            max = Math.min(max, Double.parseDouble(w + ""));
         }
      }

      return max;
   }

   /**
    * Get all axes at specified position, e.g. Coordinate.TOP_AXIS.
    * @hidden
    */
   public DefaultAxis[] getAxesAt(int axis) {
      return coord.getAxesAt(axis);
   }

   /**
    * Get the legends in this graph.
    * @hidden
    */
   public LegendGroup getLegendGroup() {
      return vlegends;
   }

   /**
    * Get the plot bounds.
    */
   public Rectangle2D getPlotBounds() {
      return new Rectangle2D.Double(plotx, ploty, plotw, ploth);
   }

   /**
    * Get the content bounds. Content area includes plot, axis, and title, but
    * not the legends.
    */
   public Rectangle2D getContentBounds() {
      return contentBounds;
   }

   /**
    * Get the coordinate.
    */
   public Coordinate getCoordinate() {
      return coord;
   }

   /**
    * Get top axis min height.
    */
   private double getTopAxisMinHeight() {
      return getMinTotal(tops);
   }

   /**
    * Get top axis preferred height.
    */
   private double getTopAxisPreferredHeight() {
      return getPreferredTotal(tops);
   }

   /**
    * Get bottom axis min height.
    */
   private double getBottomAxisMinHeight() {
      return getMinTotal(bottoms);
   }

   /**
    * Get bottom axis preferred height.
    */
   private double getBottomAxisPreferredHeight() {
      return getPreferredTotal(bottoms);
   }

   /**
    * Get left axis min width.
    */
   private double getLeftAxisMinWidth() {
      return getMinTotal(lefts);
   }

   /**
    * Get left axis preferred width.
    */
   private double getLeftAxisPreferredWidth() {
      return getPreferredTotal(lefts);
   }

   /**
    * Get right axis min width.
    */
   private double getRightAxisMinWidth() {
      return getMinTotal(rights);
   }

   /**
    * Get right axis min width.
    */
   private double getRightAxisPreferredWidth() {
      return getPreferredTotal(rights);
   }

   /**
    * Get the min total.
    */
   private double getMinTotal(AxisSizeList[] arr) {
      double val = 0;

      for(int i = 0; i < arr.length; i++) {
         val += arr[i].getMinSize();
      }

      return val;
   }

   /**
    * Get the preferred total.
    */
   private double getPreferredTotal(AxisSizeList[] arr) {
      double val = 0;

      for(int i = 0; i < arr.length; i++) {
         val += arr[i].getPreferredSize();
      }

      return val;
   }

   /**
    * Create the axis size list.
    */
   private AxisSizeList[] createAxisSizeList(boolean vertical,
                                             AxisSizeStrategy strategy) {
      Map map = new OrderedMap();
      createAxisMap(map, vertical, strategy);

      AxisSizeList[] arr = new AxisSizeList[map.size()];
      Iterator values = map.values().iterator();
      int counter = 0;

      while(values.hasNext()) {
         arr[counter] = (AxisSizeList) values.next();
         counter++;
      }

      return arr;
   }

   /**
    * Create the axis size map.
    */
   private void createAxisMap(Map map, boolean vertical,
                              AxisSizeStrategy strategy) {
      if(coord == null) {
         return;
      }

      if(coord instanceof RectCoord) {
         Object key = ((RectCoord) coord).createKey(vertical);
         AxisSizeList list = new AxisSizeList(strategy);
         list.add(coord);

         if(list.getCount() > 0) {
            map.put(key, list);
         }
      }
      else if(coord instanceof FacetCoord) {
         ((FacetCoord) coord).createAxisMap(vertical, map, strategy);
      }
   }

   /**
    * Get the element graph.
    */
   public EGraph getEGraph() {
      return graph;
   }

   /**
    * Cancel any processing on the chart.
    */
   public void cancel() {
      this.cancelled = true;
      List visuals = new ArrayList(this.visuals);

      for(Object vobj : visuals) {
         if(vobj instanceof VGraph) {
            ((VGraph) vobj).cancel();
         }

         if(vobj instanceof GraphVO) {
            ((GraphVO) vobj).getVGraph().cancel();
         }
      }
   }

   /**
    * Check if the graph is cancelled.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      VGraph vgraph = (VGraph) super.clone();

      // remove orphaned votext
      for(int i = vgraph.getVisualCount() - 1; i >= 0; i--) {
         if(vgraph.getVisual(i) instanceof VOText) {
            vgraph.removeVisual(i);
         }
      }

      return vgraph;
   }

   /**
    * Get the plot background image. This is set by BackgroundPainter.
    */
   public Image getPlotBackground() {
      return plotBackground;
   }

   /**
    * Set the plot background image.
    */
   public void setPlotBackground(Image plotBackground) {
      this.plotBackground = plotBackground;
   }

   /**
    * Plot height.
    */
   private class PlotHeight implements ISize {
      @Override
      public double getPreferredSize() {
         return coord.getUnitPreferredHeight() *
            GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
      }

      @Override
      public double getMinSize() {
         return coord.getUnitMinHeight() *
            GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
      }

      @Override
      public void setSize(double val) {
         this.val = val;
      }

      @Override
      public double getSize() {
         return val;
      }

      public double getUnit() {
         int count = GTool.getUnitCount(coord, Coordinate.LEFT_AXIS, false);
         return count == 0 ? val : val / count;
      }

      private double val;
   }

   /**
    * Plot width.
    */
   private class PlotWidth implements ISize {
      @Override
      public double getPreferredSize() {
         return coord.getUnitPreferredWidth() *
            GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
      }

      @Override
      public double getMinSize() {
         return coord.getUnitMinWidth() *
            GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
      }

      @Override
      public void setSize(double val) {
         this.val = val;
      }

      @Override
      public double getSize() {
         return val;
      }

      public double getUnit() {
         int count = GTool.getUnitCount(coord, Coordinate.BOTTOM_AXIS, false);
         return count == 0 ? val : val / count;
      }

      private double val;
   }

   private static final double GAP = 2;

   private EGraph graph;
   private List<Axis> axises = new ArrayList<>(4);
   private Coordinate coord;
   private VLabel xTitle;
   private VLabel yTitle;
   private VLabel x2Title;
   private VLabel y2Title;
   private LegendGroup vlegends;
   private double plotw;
   private double ploth;
   private double plotx;
   private double ploty;
   private Rectangle2D contentBounds;
   private int legendLayout = GraphConstants.RIGHT;
   private double legendPreferredSize = 0;

   private transient PlotWidth pwidth;
   private transient PlotHeight pheight;
   private transient AxisSizeStrategy topStrategy;
   private transient AxisSizeStrategy bottomStrategy;
   private transient AxisSizeStrategy leftStrategy;
   private transient AxisSizeStrategy rightStrategy;
   private transient AxisSizeList[] tops;
   private transient AxisSizeList[] bottoms;
   private transient AxisSizeList[] lefts;
   private transient AxisSizeList[] rights;
   private transient boolean cancelled;
   private transient Image plotBackground;

   private static final Logger LOG = LoggerFactory.getLogger(VGraph.class);
}

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
package inetsoft.report.internal;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.geo.service.WebMapService;
import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.composition.graph.*;
import inetsoft.report.composition.region.ChartArea;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.profile.ProfileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterGraphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Painter class for painting a chart.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ChartPainter implements LinkedShapePainter, Cloneable {
   /**
    * Create a new instance of ChartPainter.
    */
   protected ChartPainter() {
      super();
   }

   /**
    * Create a new instance of ChartPainter.
    * @param data the chart data set.
    * @param desc the chart descriptor.
    * @param info the chart info.
    * @param chart the chart element.
    */
   public ChartPainter(DataSet data, ChartDescriptor desc, ChartInfo info, ChartElement chart) {
      this.data = data;
      this.descriptor = desc;
      this.info = info;
      this.chart = chart;
      font_ = chart.getFont();
      fg_ = chart.getForeground();
      bg_ = chart.getBackground();
   }

   /**
    * Create a new instance of ChartPainter.
    * @param egraph the chart element graph.
    * @param data the chart data set.
    * @param chart the chart element.
    */
   public ChartPainter(EGraph egraph, DataSet data, ChartElement chart) {
      this.egraph = egraph;
      this.scriptChart = egraph != null;
      this.chart = chart;

      if(chart != null) {
         info = chart.getChartInfo();

         if(info != null) {
            info = (ChartInfo) info.clone();
         }

         descriptor = chart.getChartDescriptor();

         if(descriptor != null) {
            descriptor = (ChartDescriptor) descriptor.clone();
         }
      }

      this.data = data;
      font_ = chart.getFont();
      fg_ = chart.getForeground();
      bg_ = chart.getBackground();
   }

   /**
    * Get the chart info.
    */
   public ChartInfo getChartInfo() {
      return info;
   }

   /**
    * Get the chart descriptor.
    */
   public ChartDescriptor getChartDescriptor() {
      return descriptor;
   }

   /**
    * Get the chart data set.
    */
   public DataSet getChartDataSet() {
      return data;
   }

   /**
    * Get the chart area.
    */
   public ChartArea getChartArea() {
      return area;
   }

   /**
    * Check if contains egraph.
    */
   public boolean containsEgraph() {
      return egraph != null;
   }

   /**
    * Get the egraph.
    */
   public EGraph getEGraph() {
      return getEGraph(null);
   }

   /**
    * Get the egraph.
    */
   private synchronized EGraph getEGraph(Dimension size) {
      int sourceType = XSourceInfo.NONE;

      if(info == null && egraph == null && chart instanceof ChartElementDef) {
         info = chart.getChartInfo();

         if(info != null) {
            info = (ChartInfo) info.clone();
         }

         egraph = ((ChartElementDef) chart).egraph;
         descriptor = chart.getChartDescriptor();

         if(descriptor != null) {
            descriptor = (ChartDescriptor) descriptor.clone();
         }
      }

      if(egraph != null) {
         return egraph;
      }

      ReportSheet report = chart instanceof ChartElementDef ? chart.getReport() : null;

      if(chart instanceof ChartElementDef) {
         ChartElementDef celem = (ChartElementDef) chart;
         String dataError = celem.getDataError();

         if(dataError != null) {
            throw new RuntimeException(dataError);
         }
      }

      VariableTable params = report == null ? new VariableTable() : report.getVariableTable();
      GraphGenerator gen = GraphGenerator.getGenerator(
         info, descriptor, null, null, data.clone(true), params, sourceType, size);

      egraph = gen.createEGraph();
      data = gen.getData(); // update data from generator

      generated = true;
      return egraph;
   }

   /**
    * Get the tooltip for the specified data point.
    * <p>
    * If row/col is less than zero, we will find all the available rows/cols
    * to get the shapes.
    * @param row the row of the data point.
    * @param col the column of the data point.
    * @return the tooltip string.
    */
   public String getToolTip(int row, int col) {
      if(tooltips == null || row < 0 || col < 0) {
         return null;
      }

      return tooltips.get(new Point(row, col));
   }

   /**
    * Get the shapes for the specified data point.
    * <p>
    * If row/col is less than zero, we will find all the available rows/cols
    * to get the shapes.
    * @param row the row of the data point.
    * @param col the column of the data point.
    * @return an array of Shape objects.
    */
   @Override
   public Shape[] getShapes(int row, int col) {
      if(shapes == null) {
         return new Shape[0];
      }

      // is single point, just return the shapes gotten from shapes
      if(row >= 0 && col >= 0) {
         return getShapes0(row, col);
      }

      // is multiple points
      java.util.List<Shape> results = new ArrayList<>();
      java.util.List<Integer> rows = new ArrayList<>();
      java.util.List<Integer> cols = new ArrayList<>();

      // row >= 0, is single row
      if(row >= 0) {
         rows.add(row);
      }
      // row < 0, should find all the available rows
      else {
         for(int i = 0; i < getDatasetCount(); i++) {
            rows.add(i);
         }
      }

      // col >= 0, is single col
      if(col >= 0) {
         cols.add(col);
      }
      // col < 0, should find all the available cols
      else {
         for(int i = 0; i < getDatasetSize(); i++) {
            cols.add(i);
         }
      }

      // collect all the available shapes
      for(Integer row0 : rows) {
         for(Integer col0 : cols) {
            Shape[] shapes0 = getShapes0(row0, col0);
            results.addAll(Arrays.asList(shapes0));
         }
      }

      return results.toArray(new Shape[0]);
   }

   /**
    * Get the shapes for the specified data point.
    * @param row the row of the data point.
    * @param col the column of the data point.
    * @return an array of Shape objects.
    */
   public Shape[] getShapes0(int row, int col) {
      Point point = new Point(row, col);
      Set<Shape> list = this.shapes.get(point);

      if(list == null) {
         if(col >= 0) {
            point = new Point(row, -1);
            list = this.shapes.get(point);
         }
      }

      if(list == null) {
         return new Shape[0];
      }

      Shape[] result = new Shape[list.size()];

      list.toArray(result);
      return result;
   }

   /**
    * Get the location of the data point that contains the specified screen
    * coordinates.
    * @param x the X-coordinate.
    * @param y the Y-coordinate.
    * @return a Point, where x is the row and y is the column of the data point
    *         or <code>null</code> if the specified coordinates are not
    *         contained in any data point.
    */
   public Point locate(int x, int y) {
      if(shapes == null) {
         return null;
      }

      Point result = null;
      Iterator<Point> it = shapes.keySet().iterator();

      while(it.hasNext() && result == null) {
         Point pt = it.next();
         Set<Shape> list = this.shapes.get(pt);

         for(Shape shape : list) {
            if(shape != null && shape.contains(x, y)) {
               result = pt;
               break;
            }
         }
      }

      return result == null ? null : new Point(result.x, result.y);
   }

   /**
    * Gets the shapes that define the outline of the graphical element that
    * represents the data value at the specified indexes.
    * @param dataset the index of the dataset.
    * @param index the index of the data value in the dataset.
    * @return the shapes used to represent the data value, or <code>null</code>
    *         if shapes exist for the specified data point.
    */
   @Override
   public Object getData(int dataset, int index) {
      Object result = null;

      if(data != null) {
         result = data.getData(index, dataset); // index is column number
      }

      return result;
   }

   /**
    * Gets the data object bound at the specified indexes.
    * @return the data value at the specified location.
    */
   @Override
   public int getDatasetSize() {
      int result = -1;

      if(data != null) {
         result = data.getColCount();
      }

      return result;
   }

   /**
    * Gets the number of datasets that this painter renders.
    * @return the number of datasets bound to this painter or <code>-1</code> if
    *         this painter is not bound.
    */
   @Override
   public int getDatasetCount() {
      int result = -1;

      if(data != null) {
         result = data.getRowCount();
      }

      return result;
   }

   /**
    * Determine if this painter can be scaled.
    * @return <code>true</code>.
    */
   @Override
   public boolean isScalable() {
      return true;
   }

   /**
    * Get the chart font.
    * @return the chart font.
    */
   public Font getFont() {
      return (chart != null) ? (font_ = chart.getFont()) : font_;
   }

   /**
    * Get the chart foreground color.
    * @return the chart foreground color.
    */
   public Color getForeground() {
      return (chart != null) ? (fg_ = chart.getForeground()) : fg_;
   }

   /**
    * Get the chart background color.
    * @return the chart background color.
    */
   public Color getBackground() {
      return (chart != null) ? (bg_ = chart.getBackground()) : bg_;
   }

   /**
    * Get the preferred size of this chart. It's scaled to 2:1 based on the
    * available page width.
    * @return a Dimension object containing the perferred size.
    */
   @Override
   public Dimension getPreferredSize() {
      int w = -1000;
      int h = -500;

      try {
         // tree/network doesn't rescale to fit space, so the preferred size should be the real size.
         if(info instanceof RelationChartInfo && chart == null) {
            // only set if valid binding exists. (57429, 57533)
            if(data != null && data.getColCount() > 0 && data.getRowCount() > 0 &&
               ((RelationChartInfo) info).getRTSourceField() != null &&
               ((RelationChartInfo) info).getRTTargetField() != null)
            {
               VGraph vgraph = getVGraph();

               if(vgraph == null) {
                  vgraph = getVGraph(1000, 500, true);
               }

               h = (int) vgraph.getPreferredHeight();
            }
         }
      }
      catch(Exception ex) {
         // Maybe relation chart will throw an exception when getting real size
         // because of permission control, catch and ignore it.
      }

      return new Dimension(-1000, h);
   }

   /**
    * Create a copy of this object.
    * @return the created copy.
    */
   @Override
   public ChartPainter clone() {
      try {
         ChartPainter cp = (ChartPainter) super.clone();
         return cp;
      }
      catch(Exception exc) {
         LOG.error("Failed to clone chart painter", exc);
      }

      return null;
   }

   /**
    * Paint this chart on a graphics context at the specified location.
    * @param g the graphics context.
    * @param x the X-coordinate of the upper-left corner of the drawing area.
    * @param y the Y-coordinate of the upper-left corner of the drawing area.
    * @param w the width of the drawing area.
    * @param h the height of the drawing area.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      paint(g, x, y, w, h, true);
   }

   /**
    * Paint this chart on a graphics context at the specified location.
    * @param g the graphics context.
    * @param x the X-coordinate of the upper-left corner of the drawing area.
    * @param y the Y-coordinate of the upper-left corner of the drawing area.
    * @param w the width of the drawing area.
    * @param h the height of the drawing area.
    * @param resetShapes true to regenerate shapes, false otherwise.
    */
   public void paint(Graphics g, int x, int y, int w, int h, boolean resetShapes) {
      Insets borders = chart == null ? null : chart.getBorders();

      // paint borders
      if(borders != null && (borders.top != 0 || borders.left != 0 ||
         borders.bottom != 0 || borders.right != 0))
      {
         Color ocolor = g.getColor();
         Color color = chart == null ? null :
         ((ChartElementDef) chart).getBorderColor();
         g.setColor(color == null ?  getForeground() : color);
         int top = (borders.top != -1) ? borders.top : 0;
         int left = (borders.left != -1) ? borders.left : 0;
         int bottom = (borders.bottom != -1) ? borders.bottom : 0;
         int right = (borders.right != -1) ? borders.right : 0;
         Common.drawRect(g, x, y, w, h, top, left, bottom, right);
         g.setColor(ocolor);

         float topw = (float) Math.ceil(Common.getLineWidth(top));
         float leftw = (float) Math.ceil(Common.getLineWidth(left));
         float bottomw = (float) Math.ceil(Common.getLineWidth(bottom));
         float rightw = (float) Math.ceil(Common.getLineWidth(right));
         x += leftw;
         y += topw;
         w -= leftw + rightw + 1;
         h -= topw + bottomw + 1;
      }

      w = w >= 0 ? w : 1;
      h = h >= 0 ? h : 1;

      // @by larryl, make sure the image is a Graphics2D otherwise it may
      // throw exception later
      try {
         Graphics2D g2 = Common.getGraphics2D(g);

         // shift 1/2 line width so the top axis or facet grid won't be cut off.
         g2 = (Graphics2D) g2.create(x, y, w + 1, h + 1);
         g2.translate(0.5, 0.5);

         if(SreeEnv.getProperty("image.antialias").equals("true")) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         }

         // @by larryl, the chart font is the default font for all text
         if(getFont() != null) {
            g2.setFont(getFont());
         }

         paint(g2, w, h, resetShapes);
         g2.dispose();
      }
      catch(RuntimeException ex) {
         // if it's debug model, throw the exception, otherwise ignore it
         if(chart != null && "true".equals(chart.getProperty("chart.debug"))) {
            throw ex;
         }

         paintImage(g, x, y, w, h, resetShapes);
      }
   }

   /**
    * Paint this chart on a graphics context at the specified location.
    * @param g the graphics context.
    * @param x the X-coordinate of the upper-left corner of the drawing area.
    * @param y the Y-coordinate of the upper-left corner of the drawing area.
    * @param w the width of the drawing area.
    * @param h the height of the drawing area.
    * @param resetShapes true to regenerate shapes, false otherwise.
    */
   private void paintImage(Graphics g, int x, int y, int w, int h,
            boolean resetShapes)
   {
      try {
         Image img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         Graphics g2 = img.getGraphics();
         g2.setColor(Color.black);

         if(SreeEnv.getProperty("image.antialias").equals("true")) {
            ((Graphics2D) g2).setRenderingHint(
               RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         }

         // @by larryl, the chart font is the default font for all text
         if(getFont() != null) {
            g2.setFont(getFont());
         }

         paint(g2, w, h, resetShapes);
         g2.dispose();
         g.drawImage(img, x, y, null);
      }
      catch(Exception ex) {
         String msg = Catalog.getCatalog().getString("viewer.chart.invalid");
         g.setColor(getForeground());
         g.setFont(getFont());
         FontMetrics fm = g.getFontMetrics();
         g.drawString(msg, x + (w - fm.stringWidth(msg)) / 2, y + (h - fm.getAscent()) / 2);
         LOG.error("Failed to paint chart", ex);
      }
   }

   /**
    * Always paint to 0,0 coordinate because the shapes need to be relative
    * to the top left corner.
    * @param w the width of the drawing area.
    * @param h the height of the drawing area.
    * @param resetShapes true to regenerate shapes, false otherwise.
    */
   private void paint(Graphics g, final int w, final int h, boolean resetShapes) {
      try {
         Map<Point, Set<Shape>> oldShapes = shapes;
         paint0(g, w, h);

         if(!resetShapes) {
            shapes = oldShapes;
         }
      }
      catch(MessageException ex) {
         throw ex;
      }
   }

   /**
    * Always paint to 0,0 coordinate because the shapes need to be relative
    * to the top left corner.
    * @param w the width of the drawing area.
    * @param h the height of the drawing area.
    */
   private void paint0(Graphics g, final int w, final int h) {
      ReportSheet report = null;

      // set the default formats for chart
      if(chart instanceof ChartElementDef) {
         report = chart.getReport();

         if(report != null && descriptor != null) {
            descriptor.setFormats(report.getFormats());
         }
      }

      VGraph vgraph = getVGraph(w, h, true);

      // copy the facet setting from the runtime (set by GraphGenerator)
      if(info != null && chart != null && chart instanceof ChartElementDef) {
         chart.getChartInfo().setFacet(info.isFacet());
      }

      if(vgraph == null) {
         return;
      }

      if(g instanceof PDFDevice || g instanceof PrinterGraphics ||
         // exporter uses high resolution image so should not copy from buffered image. (57643)
         GTool.isPDF() || GTool.isPNG() ||
         SVGSupport.getInstance().isSVGGraphics(g))
      {
         g = Common.getGraphics2D(g.create());
         vgraph.paintGraph((Graphics2D) g, true);
         GraphUtil.drawNoDataLabel(g, vgraph, info);
         g.dispose();
      }
      else {
         // higher resolution image so lines at fration (e.g. 18.5) is not lost. (56683)
         final int res = Integer.parseInt(SreeEnv.getProperty("chart.image.resolution", "2"));
         // @by larryl, drawing on the screen and on to image may have different
         // rounding behavior. We should always use image so the code doesn't
         // have to deal with both.
         // need extra pixel to accommodate border. (59666)
         BufferedImage img = new BufferedImage(w * res, (h + 1) * res,
                                               BufferedImage.TYPE_INT_ARGB);
         Graphics2D ig = (Graphics2D) img.getGraphics();

         ig.scale(res, res);

         try {
            // for Feature #26586, add ui processing time record.

            ProfileUtils.addExecutionBreakDownRecord(report,
               ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
                  vgraph.paintGraph((Graphics2D) args[0], true);
               }, ig);

            //vgraph.paintGraph(ig, true);
         }
         catch(Exception ex) {
            LOG.error("Failed to paint chart graph", ex);
         }

         GraphUtil.drawNoDataLabel(ig, vgraph, info);
         ig.dispose();
         g.drawImage(img, 0, 0, w, h, null);
      }

      // @by larryl, optimization, avoid creating chart area if not necessary
      if(!(g instanceof PrintGraphics)) {
         area = new ChartArea(vgraph, vgraph.getEGraph(), data, null, info, chart.getID());
         shapes = area.getShapes();
         tooltips = area.getTooltips();
      }
   }

   /**
    * Check if the specified script controls EGraph.
    */
   private boolean containsEGraphScript(String script) {
      if(script == null || script.length() == 0) {
         return false;
      }

      script = script.toLowerCase();
      return script.contains("egraph");
   }

   /**
    * Get the visual graph.
    */
   public synchronized VGraph getVGraph(int w, int h, boolean reused) {
      // if is printlayoutmode, then this chart element is converted by
      // a vs chart to support vs printlayout, then paint the chart element
      // by the setted vgraph which is from the vs chart to provide a safer
      // convertion between vs chart and report chart.
      if(chart != null) {
         return vgraph;
      }

      VGraph vgraph = reused ? getVGraph() : null;

      if(vgraph != null) {
         Dimension2D size = vgraph.getSize();

         if(size.getWidth() != w || size.getHeight() != h) {
            vgraph = null;

            // network chart RelationElement.mxLayout() depends on graph size. (57416)
            if(GraphTypes.isRelation(info.getChartType())) {
               egraph = null;
            }
         }
      }

      if(vgraph == null || vgraph.isCancelled()) {
         if(egraph == null) {
            getEGraph(new Dimension(w, h));
         }

         if(generated) {
            if(egraph != null) {
               LegendsDescriptor legends =
                  descriptor == null ? null : descriptor.getLegendsDescriptor();
               GraphUtil.fixLegendsRatio(info, legends, w, h);

               // maintain frame position size here
               if(legends != null) {
                  for(int i = 0; i < egraph.getElementCount(); i++) {
                     GraphElement elem = egraph.getElement(i);
                     VisualFrame[] visuals = elem.getVisualFrames();

                     for(int k = 0; k < visuals.length; k++) {
                        VisualFrame frame = visuals[k];

                        if(frame != null) {
                           String type = (frame instanceof ColorFrame)
                              ? ChartArea.COLOR_LEGEND :
                              (frame instanceof SizeFrame) ? ChartArea.SIZE_LEGEND
                                 : ChartArea.SHAPE_LEGEND;
                           LegendDescriptor legend = GraphUtil.getLegendDescriptor(
                              info, legends, frame.getField(),
                              GraphUtil.getTargetFields(frame, egraph), type,
                              GraphUtil.isNodeAestheticFrame(frame, elem));
                           GraphUtil.setPositionSize(frame, legend, legends);
                        }
                     }
                  }
               }
            }

            generated = false;
         }

         ReportSheet report = chart instanceof ChartElementDef ? chart.getReport() : null;

         EGraph egraph0 = (EGraph) egraph.clone();

         if(egraph0.getCoordinate() != null) {
            egraph0.getCoordinate().setLayoutSize(new Dimension(w, h));
         }

         if(chart != null) {
            ChartDescriptor desc = chart.getChartDescriptor();
            ChartInfo cinfo = chart.getChartInfo();

            // preferred size should conform to web map, same as ChartElementDef.processPreferredSize.
            if(desc != null && desc.getPlotDescriptor().isWebMap() && cinfo instanceof MapInfo) {
               WebMapService mapService = WebMapService.getWebMapService(null);

               if(mapService != null) {
                  int maxSize = mapService.getMaxSize();
                  w = Math.min(maxSize, w);
                  h = Math.min(maxSize, h);
               }
            }
         }

         vgraph = (plotter = Plotter.getPlotter(egraph0)).plotAndLayout(data.clone(false),
                                                                        0, 0, w, h);

         if(vgraph == null) {
            return null;
         }

         // underline for hyperlink text, the report could be null after swapped
         if(report == null || true) {
            GraphUtil.processHyperlink(info, vgraph, data);
         }

         vgraphContainer = new VGraphContainer(this, vgraph);
      }

      return vgraph;
   }

   /**
    * Get the visual graph.
    */
   public VGraph getVGraph() {
      return (vgraphContainer != null) ? vgraphContainer.getVGraph() : null;
   }

   @Override
   public void finalize() {
      // don't cancel VSDataSet when gc garbage collection the ChartPainter since it may be shared by cloned ChartPainter.
      cancel(false);
   }

   public void cancel() {
      cancel(true);
   }

   public void cancel(boolean cancelData) {
      Plotter plotter = this.plotter;
      DataSet data = this.data;

      if(plotter != null) {
         plotter.cancel();
      }

      // don't cancel VGraph since it may be shared by cloned ChartPainter.

      if(cancelData && data instanceof VSDataSet) {
         TableLens table = ((VSDataSet) data).getTable();

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
         }
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      // chart is transient, so we need serialize info and descriptor
      if(info == null && chart instanceof ChartElementDef) {
         info = chart.getChartInfo();
         descriptor = chart.getChartDescriptor();
      }

      stream.defaultWriteObject();
   }

   /**
    * VGraphContainer contains one weak referenced VGraph.
    */
   private static class VGraphContainer {
      public VGraphContainer(ChartPainter painter, VGraph vgraph) {
         this.painter = painter;
         this.vgraphRef = vgraph == null ? null : new WeakReference<>(vgraph);
         this.size = vgraph == null ? null : vgraph.getSize();
      }

      public VGraph getVGraph() {
         if(vgraphRef == null) {
            return null;
         }

         VGraph vgraph = vgraphRef.get();

         if(vgraph == null && size != null) {
            vgraph = painter.getVGraph((int) size.getWidth(), (int) size.getHeight(), false);
            vgraphRef = new WeakReference<>(vgraph);
         }

         return vgraph;
      }

      private final ChartPainter painter;
      private final Dimension2D size;
      private WeakReference<VGraph> vgraphRef;
   }

  /**
   * Set vgraph for the chart element. This is for vs converted report, and
   * paint the chart using the set vgraph to provide a safer conversion
   * between vs chart and report chart.
   */
   public void setVGraph(VGraph vgraph) {
      this.vgraph = vgraph;
   }

   // for vs converted report, paint the chart by the set vgraph.
   private transient VGraph vgraph = null;
   private transient Plotter plotter = null;
   boolean scriptChart;
   private ChartDescriptor descriptor = null;
   private ChartInfo info = null;
   private DataSet data = null;
   private Font font_;
   private Color fg_, bg_;
   transient ChartElement chart;
   private EGraph egraph;
   private transient VGraphContainer vgraphContainer;
   private transient ChartArea area = null;
   private transient Map<Point, Set<Shape>> shapes = null;
   private transient Map<Point, String> tooltips = null;
   private transient boolean generated = false;

   private static final Logger LOG = LoggerFactory.getLogger(ChartPainter.class);
}

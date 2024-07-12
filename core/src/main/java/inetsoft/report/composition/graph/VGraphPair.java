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
package inetsoft.report.composition.graph;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.*;
import inetsoft.graph.element.*;
import inetsoft.graph.geometry.ElementGeometry;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.form.*;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.internal.DimensionD;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.PointVO;
import inetsoft.graph.visual.PolygonVO;
import inetsoft.report.*;
import inetsoft.report.composition.execution.DataMap;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.region.*;
import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.gui.viewsheet.VSChart;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.script.viewsheet.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.BrushingColor;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.css.CSSParameter;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.log.LogLevel;
import inetsoft.util.profile.ProfileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VGraphPair, contains two visual graphs, one for real size and
 * one for expanded size.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class VGraphPair {
   /**
    * Constructor.
    */
   public VGraphPair() throws Exception {
      super();
   }

   /**
    * Get the content size of the chart.
    */
   public Dimension getContentSize() {
      return size;
   }

   /**
    * Cancel the graph pair.
    */
   public void cancel() {
      synchronized(completed) {
         if(completed.get()) {
            return;
         }

         cancelled = true;
         completed.notifyAll();
      }

      VGraph vgraph = this.vgraph;
      VGraph evgraph = this.evgraph;
      Plotter plotter1 = this.plotter1;
      Plotter plotter2 = this.plotter2;

      if(vgraph != null) {
         vgraph.cancel();
      }

      if(evgraph != null) {
         evgraph.cancel();
      }

      if(plotter1 != null) {
         plotter1.cancel();
      }

      if(plotter2 != null) {
         plotter2.cancel();
      }
      DataSet data = this.data;

      if(data instanceof DataSetFilter) {
         data = ((DataSetFilter) data).getRootDataSet();
      }

      if(data instanceof VSDataSet) {
         TableLens table = ((VSDataSet) data).getTable();

         while(!(table instanceof CancellableTableLens) && table instanceof TableFilter) {
            table = ((TableFilter) table).getTable();
         }

         if(table instanceof CancellableTableLens) {
            ((CancellableTableLens) table).cancel();
         }
      }
   }

   /**
    * Check if processing has been cancelled.
    */
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Check if this VGraphPair has completed process.
    */
   public boolean isCompleted() {
      return completed.get();
   }

   /**
    * Check if graph was plotted. The graph may not be plotted if data is null (for whatever
    * reason), or if the graph definition is empty.
    */
   public boolean isPlotted() {
      return vgraph != null;
   }

   /**
    * Intialize visual graphs.
    */
   public void initGraph(ViewsheetSandbox box, String cname, Dimension maxsize,
                         boolean export, double scaleFont)
      throws Exception
   {
      initGraph(box, cname, maxsize, export, scaleFont, false);
   }

   /**
    * Intialize visual graphs.
    */
   public void initGraph(ViewsheetSandbox box, String cname, Dimension maxsize,
                         boolean export, double scaleFont, boolean forceExpand)
      throws Exception
   {
      // for Feature #26586, add ui processing time record when init the graph.

      ProfileUtils.addExecutionBreakDownRecord(box.getID(),
                                               ExecutionBreakDownRecord.UI_PROCESSING_CYCLE, args -> {
            try {
               initGraph0(box, cname, maxsize, export, scaleFont, forceExpand);
            }
            catch(MessageException messageException) {
               throw messageException;
            }
            catch(Exception e) {
               if(LOG.isDebugEnabled()) {
                  LOG.debug("init graph error: cancel={}", this.cancelled, e);
               }
               else {
                  LOG.warn("init graph error: cancel={}, {}", this.cancelled, e.getMessage());
               }

               throw e;
            }
            finally {
               synchronized(completed) {
                  completed.set(true);
                  completed.notifyAll();
               }
            }
         });

      //initGraph0(box, cname, maxsize, export, scaleFont);
   }

   /**
    * Set the size of this graph.
    */
   public void initSize(ViewsheetSandbox box, String cname, Dimension maxsize, boolean export) {
      Viewsheet vs = box.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(cname);

      // the chart could have been remove by the time get-chart-areas is processed
      if(chart != null) {
         this.size = export && maxsize != null ? maxsize : VSUtil.getContentSize(chart, maxsize);
      }
   }

   /**
    * Intialize visual graphs.
    */
   private void initGraph0(ViewsheetSandbox box, String cname, Dimension maxsize,
                           boolean export, double scaleFont, boolean forceExpand)
      throws Exception
   {
      DataSet data = (DataSet) box.getData(cname);

      if(data != null) {
         LOG.debug("Chart {} finished processing: {} row(s)", cname, data.getRowCount() + 1);
      }

      this.vbox = box;

      if(cancelled) {
         return;
      }

      DataSet adata = (DataSet) box.getData(cname, true, DataMap.ZOOM);

      if(cancelled) {
         return;
      }

      Viewsheet vs = box.getViewsheet();
      ChartVSAssembly chart = (ChartVSAssembly) vs.getAssembly(cname);
      int index = cname.lastIndexOf(".");

      if(index >= 0) {
         box = box.getSandbox(cname.substring(0, index));
         cname = cname.substring(index + 1);
         vs = box.getViewsheet();
      }

      if(chart == null) {
         // a chart may have been removed (in wizard) before the image is generated.
         // don't throw an exception since the chart will be refreshed with the new
         // recommended chart.
         //throw new RuntimeException("Chart not found: " + cname);
         return;
      }

      if(cancelled) {
         return;
      }

      initSize(box, cname, maxsize, export);
      final ChartVSAssemblyInfo ainfo = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      fixChartFormat(ainfo);
      this.vsrc = GraphUtil.getVisualSource(ainfo);
      VariableTable vars = box.getAllVariables();
      this.topPadding = ainfo.getPadding().top;
      this.leftPadding = ainfo.getPadding().left;
      this.bottomPadding = ainfo.getPadding().bottom;
      this.rightPadding = ainfo.getPadding().right;

      // create vgraph
      cinfo = ainfo.getVSChartInfo();

      ((AbstractChartInfo) cinfo).fixTimeSeries();

      ChartDescriptor cdesc = ainfo.getChartDescriptor();
      odesc = (ChartDescriptor) cdesc.clone();

      if(cinfo instanceof VSChartInfo) {
         ((VSChartInfo) cinfo).setLinkVarTable(box.getAllVariables());
         ((VSChartInfo) cinfo).setLinkSelections(box.getSelections());
         cinfo.setChartDescriptor(odesc);
      }

      // write sco file for debug
      /*
      if("true".equals(SreeEnv.getProperty("graph.dump"))) {
         String dir = Tool.getCacheDirectory();
         File file = new File(Tool.convertUserFileName(dir), "graph.sco");
         SCOHelper helper = SCOHelper.createWHelper(file);
         LOG.debug("write sco file to: " + file.getAbsolutePath());
         helper.write(ainfo, adata, data, vars);
      }
      */

      if(cancelled) {
         return;
      }

      Creator creator = null;
      Creator creator2 = null;
      DataSet vdata = null;
      VSChartInfo info = chart.getVSChartInfo();

      if(data != null) {
         vdata = getVisualDataSet(box, ainfo);

         if(info.isNeedResetShape()) {
            GraphUtil.fixVisualFrame(info.getShapeField(),
               ChartConstants.AESTHETIC_SHAPE, info.getRTChartType(), info);
            info.setNeedResetShape(false);
         }

         if(cancelled) {
            return;
         }

         // dataset may be changed in script, don't modified the cached copy
         if(data instanceof VSDataSet) {
            data = (DataSet) data.clone();
         }
         else if(data instanceof ExpandableDataSet) {
            data = (DataSet) data.clone();
         }
      }

      synchronized(info) {
         try {
            creator = new Creator(ainfo, vbox, data, adata, vdata, vars, this.size);
            creator2 = new Creator(ainfo, vbox, data, adata, vdata, vars, this.size);
         }
         catch(Exception ex) {
            if(cancelled) {
               return;
            }

            throw ex;
         }
      }

      EGraph egraph2 = null;
      String script = ainfo.getScript();

      if(!Tool.isEmptyString(script) && ainfo.isScriptEnabled()) {
         ViewsheetScope scope = vbox.getScope();
         double osize = 0;
         vbox.lockWrite();

         try {
            // execute on egraph
            scope.resetChartScriptable(chart);
            ChartVSAScriptable cscriptable = (ChartVSAScriptable) scope.getVSAScriptable(cname);

            // delay the creation of graph so changes to the binding will
            // be reflected in the graph
            cscriptable.setGraphCreator(this.creator = creator);

            // since we are executing the script here, clear out runtime info
            // so changes don't accummulate
            if(ainfo.getRTChartDescriptor() != null && ainfo.getChartDescriptor() != null) {
               ainfo.setRTChartDescriptor((ChartDescriptor) ainfo.getChartDescriptor().clone());
            }

            executeScript(scope, script, cname, true);
            creator.setScriptExecuted(true);

            EGraph oegraph = null;
            DataSet nset = null;
            boolean isChanged = false;

            try {
               egraph = creator.getGraph();
               oegraph = creator.getCreatedGraph();
               this.data = creator.getGraphDataSet();
               VSChartInfo oinfo = creator.getChartInfo();
               osize = creator.getLegendSize();

               // support script created dataset
               // when put the dataset script, we will convert the value to
               // dataset, so here it must be dataset when get dataset
               nset = creator.getDataSet();

               // initialize flag - changed by script in structure or not
               cscript = isChangedByScript0(nset, this.data, egraph, oegraph);
               isChanged = needGenerateNewGraph(ainfo, oinfo, cdesc);
            }
            catch(Exception ex) {
               // if the script changes binding (e.g. setColorField),
               // the dataset may not contain the columns for the new binding,
               // and causes a 'column not found' exception, we should
               // continue and get a new dataset and re-generate
               // the egraph in the next block
               LOG.debug("The binding has changed, the graph will be regenerated", ex);
               isChanged = true;
            }

            // descriptor change, create graph
            try {
               if(!cscript && isChanged) {
                  box.resetDataMap(cname);
                  data = (DataSet) box.getData(cname);
                  adata = (DataSet) box.getData(cname, true, DataMap.ZOOM);
                  vdata = getVisualDataSet(box, ainfo);
                  VSChartInfo tinfo = ainfo.getVSChartInfo();

                  if(tinfo.isNeedResetShape()) {
                     GraphUtil.fixVisualFrame(tinfo.getShapeField(),
                                              ChartConstants.AESTHETIC_SHAPE,
                                              tinfo.getRTChartType(), tinfo);
                     tinfo.setNeedResetShape(false);
                  }

                  creator = new Creator(ainfo, vbox, data, adata, vdata, vars, this.size);
                  cscriptable.setGraphCreator(this.creator = creator);
                  egraph = creator.getGraph();
                  this.data = creator.getGraphDataSet();
               }
            }
            catch(Exception ex) {
               throw ex;
            }

            // don't accummulate script changes
            if(!cscript && isChanged && !cscriptable.hasHyperlink()) {
               script = "";
            }

            if(!"".equals(script)) {
               // clear one of chart's alert message
               Tool.getUserMessage();
            }

            // execute on egraph2
            cscriptable.setGraphCreator(this.creator = creator2);
            executeScript(scope, script, ainfo.getName(), false);

            // descriptor change, create graph
            try {
               if(!cscript && isChanged) {
                  creator2 = new Creator(ainfo, vbox, data, adata, vdata, vars, this.size);
                  cscriptable.setGraphCreator(this.creator = creator2);
                  egraph2 = creator2.getGraph();
                  this.data = creator2.getGraphDataSet();
               }
            }
            catch(Exception ex) {
               throw ex;
            }

            // script created graph
            if(egraph2 == null) {
               egraph2 = creator2.getGraph();
            }

            if(!cscript && isChanged) {
               // do nothing
            }
            else if(nset != null) {
               this.data = nset;
            }

            // for script chart, if multi-element is defined but no color frame,
            // here create one default color frame for better user experience
            if(cscript) {
               ColorFrame color = GraphUtil.getColorFrame(egraph);

               if(color == null) {
                  color = GraphGenerator.createCombinedColorFrame(egraph);

                  if(color != null) {
                     GraphUtil.setColorFrame(egraph, color);
                  }

                  color = GraphGenerator.createCombinedColorFrame(egraph2);

                  if(color != null) {
                     GraphUtil.setColorFrame(egraph2, color);
                  }
               }
            }

            sortFrameValues(egraph, this.data);
            sortFrameValues(egraph2, this.data);

            // repopulate the properties changed by script
            info = chart.getVSChartInfo();
            info.setFacet(egraph.getCoordinate() instanceof FacetCoord);

            // if the legend layout is changed, we set the preferred size from
            // the descriptor if the size has not been touched by the script.
            // this is required if the preferred width/height is set and then
            // the legend is moved to a different side.
            if(osize == egraph.getLegendPreferredSize()) {
               LegendsDescriptor legends = cdesc.getLegendsDescriptor();

               GraphUtil.setLegendSize(egraph, legends);
               GraphUtil.setLegendSize(egraph2, legends);
            }
         }
         catch(MessageException messageException) {
            throw messageException;
         }
         catch(Exception ex) {
            String linemsg = Catalog.getCatalog().getString("Script failed") + " " +
               ex.getMessage();
            throw new MessageException(linemsg, ex);
         }
         finally {
            vbox.unlockWrite();
         }
      }
      else {
         try {
            egraph = creator.getGraph();
            this.data = creator.getGraphDataSet();
            egraph2 = creator2.getGraph();
         }
         catch(Exception e) {
            if(cancelled) {
               LOG.warn("graph is canceled:", e);
               return;
            }

            throw e;
         }
      }

      // GDebug.dumpGraph(egraph, this.data);
      // GDebug.printDataSet(this.data);
      if(scaleFont != 1) {
         scaleTextSpecs(egraph, scaleFont);
         scaleTextSpecs(egraph2, scaleFont);
      }
      else {
         double sf = 1.0;

         // scale by scalar of which ever axis has become the smallest proportionally to the
         // original size, but do not scale upwards in size
         if(ainfo != null && ainfo.getLayoutSize() != null && ainfo.getPixelSize() != null) {
            // avoid scaling unless extremely small
            sf = ainfo.getLayoutSize().getHeight() * 2.5 / ainfo.getPixelSize().getHeight();
            sf = Math.min(1.0f, sf);
         }

         if(sf < 1) {
            scaleTextSpecs(egraph, sf);
            scaleTextSpecs(egraph2, sf);
         }
      }

      if(this.data == null) {
         LOG.debug("Data is null in VGraphPair: {}", cname);
         return;
      }

      if(!GraphTypeUtil.supportShapeFrame(egraph)) {
         LOG.debug("Chart does not support shape frame.");
      }

      // empty chart, don't plot
      if(egraph.getElementCount() == 0) {
         ainfo.setNoData(true);
         return;
      }

      if(cancelled) {
         return;
      }

      // get size
      width = ewidth = (int) size.getWidth();
      height = eheight = (int) size.getHeight();
      int width2 = width - leftPadding - rightPadding;
      int height2 = height - topPadding - bottomPadding;
      boolean evgraphNeeded = false;

      // make sure the evgraph legend has same size as vgraph
      if(egraph != null && egraph.getCoordinate() != null) {
         egraph.getCoordinate().setLayoutSize(new DimensionD(width2, height2));
      }

      if(evgraph != null && evgraph.getCoordinate() != null) {
         egraph2.getCoordinate().setLayoutSize(new DimensionD(width2, height2));
      }

      try {
         plotter1 = Plotter.getPlotter(egraph);
         vgraph = plotter1.plotAndLayout(this.data, 0, 0, width2, height2);
         plotter1 = null;

         if(cancelled) {
            return;
         }

         DateComparisonUtil.checkGraphValidity(ainfo, vgraph);
         final boolean scrollable = GraphUtil.isScrollable(vgraph, info);
         evgraph = null;
         evgraphNeeded = scrollable && !forceExpand;
      }
      catch(MessageException messageException) {
         throw messageException;
      }
      catch(Throwable ex) {
         if(!isCancelled()) {
            LOG.error("Failed to generate chart: " + ex, ex);
         }

         throw new MessageException(ex, LogLevel.DEBUG, false);
      }

      if(cancelled) {
         return;
      }

      // underline for hyperlink text
      if(box.getMode() == Viewsheet.SHEET_RUNTIME_MODE) {
         GraphUtil.processHyperlink(chart.getVSChartInfo(), getExpandedVGraph(), data);
      }

      if(cancelled) {
         return;
      }

      final Rectangle2D graphBounds = vgraph.getBounds();
      final double graphWidth = graphBounds.getWidth();
      final double graphHeight = graphBounds.getHeight();
      final Rectangle2D plotBounds = vgraph.getPlotBounds();
      final double plotWidth = plotBounds.getWidth();
      final double plotHeight = plotBounds.getHeight();
      final double sideW = graphWidth - plotWidth;
      final double sideH = graphHeight - plotHeight;
      double minPlotWidth = Math.min(vgraph.getMinPlotWidth(), 1000000);
      double minPlotHeight = Math.min(vgraph.getMinPlotHeight(), 1000000);

      // for wordcloud, the ratio is the multiple of the graph plot size.
      if(GraphTypeUtil.isWordCloud(info) || GraphTypeUtil.isDotPlot(info)) {
         minPlotWidth = plotWidth;
         minPlotHeight = plotHeight;
      }

      double initialWidthRatio = plotWidth / minPlotWidth;
      double initialHeightRatio = plotHeight / minPlotHeight;
      double effectiveWidthRatio = initialWidthRatio;
      double effectiveHeightRatio = initialHeightRatio;

      // remove the label after the getMinPlotWidth/Height have been called so the
      // label size is included (same as export) (47827).
      // do this before the isVScrollable/isHScrollable so the scrollable is same
      // as before 13.3 (47874).
      if(!export && evgraphNeeded) {
         // if evgraph exists, axis is not used on the gui, so we can remove the labels to
         // reduce memory usage
         for(Axis axis : vgraph.getCoordinate().getAxes(true)) {
            axis.removeAllLabels();
         }
      }

      double widthRatio = info.getUnitWidthRatio();
      double heightRatio = info.getUnitHeightRatio();
      boolean scrollable = GraphUtil.isScrollable(vgraph, info);
      boolean vScrollable = GraphUtil.isVScrollable(vgraph, info);
      boolean hScrollable = GraphUtil.isHScrollable(vgraph, info);
      final boolean singlePoint = isSinglePoint(egraph);
      final boolean invertedGraph = info.isInvertedGraph();

      // tree/network scaling is applied by setting width/height ratio (in GraphGenerator).
      // we get that ratio directly instead of computing it from the current sizes.
      if(vgraph.getEGraph().getElementCount() > 0 &&
         vgraph.getEGraph().getElement(0) instanceof RelationElement)
      {
         RelationElement elem = (RelationElement) vgraph.getEGraph().getElement(0);
         effectiveWidthRatio = elem.getWidthRatio();
         effectiveHeightRatio = elem.getHeightRatio();
         initialWidthRatio = 1;
         initialHeightRatio = 1;
      }

      info.setInitialHeightRatio(initialHeightRatio);
      info.setInitialWidthRatio(initialWidthRatio);
      info.setEffectiveWidthRatio(effectiveWidthRatio);
      info.setEffectiveHeightRatio(effectiveHeightRatio);

      if(scrollable) {
         // limit the size of a faceted chart to 1/3rd of the size of the actual chart
         if(info.isWidthResized()) {
            // resize ratio is applied to vertex width for relation graph, which would
            // change the preferred/min width for its calculation, so it should not be
            // scaled again.
            if(!(info instanceof RelationChartInfo)) {
               minPlotWidth *= widthRatio;
            }
         }
         else if(singlePoint && invertedGraph) {
            minPlotWidth /= 3;
            widthRatio /= 3;
            hScrollable = true;
         }

         if(info.isHeightResized()) {
            // see above
            if(!(info instanceof RelationChartInfo)) {
               minPlotHeight *= heightRatio;
            }
         }
         else if(singlePoint && !invertedGraph) {
            minPlotHeight /= 3;
            heightRatio /= 3;
            vScrollable = true;
         }
         else if(cdesc.isSparkline()) {
            minPlotHeight /= 5;
            heightRatio /= 5;
         }

         // if min size is smaller than the actual size then use the actual size
         if(minPlotWidth < plotWidth) {
            ewidth = (int) (plotWidth + sideW);
         }
         else if(hScrollable) {
            ewidth = (int) (minPlotWidth + sideW);
            info.setEffectiveWidthRatio(widthRatio);
         }
         else {
            ewidth = ewidth - leftPadding - rightPadding;
         }

         if(minPlotHeight < plotHeight) {
            eheight = (int) (plotHeight + sideH);
         }
         else if(vScrollable) {
            eheight = (int) (minPlotHeight + sideH);
            info.setEffectiveHeightRatio(heightRatio);
         }
         else {
            eheight = eheight - topPadding - bottomPadding;
         }
      }
      else {
         ewidth = ewidth - leftPadding - rightPadding;
         eheight = eheight - topPadding - bottomPadding;
      }

      if(evgraphNeeded) {
         createEVGraph(egraph2, ewidth, eheight);

         if(evgraph.getCoordinate().requiresReplot()) {
            createEVGraph(egraph2, ewidth, eheight);
         }

         if(box.getMode() == Viewsheet.SHEET_RUNTIME_MODE) {
            GraphUtil.processHyperlink(chart.getVSChartInfo(), getExpandedVGraph(), data);
         }
      }

      //GDebug.snap(evgraph != null ? evgraph : vgraph, "/tmp/graph.png");

      if(cancelled) {
         return;
      }

      //==========================================================================
      // if graph is scrollable,
      //   vgraph - contains title, legend, but no vo or axes
      //   evgraph - contains no title
      // otherwise, evgraph is null and vgraph contains everything
      //==========================================================================

      // @by arrowz to fix bug1254216371337
      // If there are multiple cities in one state or country, multiple polygon
      // vos are created for one state or country.In brush source map some are
      // gray, some are red. Red ones should be always on the top of gray ones.
      info = chart.getVSChartInfo();
      ConditionList bconds = chart.getBrushConditionList(null, true);

      if(info instanceof VSMapInfo && bconds != null && !bconds.isEmpty()) {
         int cnt = getExpandedVGraph().getVisualCount();
         List<Visualizable> povos = new Vector<>(); // polygon
         List<Visualizable> ptvos = new Vector<>(); // point

         for(int i = cnt - 1; i >= 0; i--) {
            Visualizable visual = getExpandedVGraph().getVisual(i);

            if(visual instanceof PolygonVO) {
               PolygonVO vo = (PolygonVO) visual;
               Color color = ((ElementGeometry) vo.getGeometry()).getColor(0);

               if(color.equals(brushHLColor)) {
                  getExpandedVGraph().removeVisual(visual);
                  povos.add(visual);
               }
            }
            else if(visual instanceof PointVO) {
               getExpandedVGraph().removeVisual(visual);
               ptvos.add(visual);
            }
         }

         for(int i = povos.size() - 1; i >= 0; i--) {
            getExpandedVGraph().addVisual(povos.get(i));
         }

         for(int i = ptvos.size() - 1; i >= 0; i--) {
            getExpandedVGraph().addVisual(ptvos.get(i));
         }
      }

      // clear the runtime infos, when the graph has been created
      // @by larryl, clearing runtime information after each graph init is problematic
      // since the graph pair may be requested multiple times (for chart model and
      // change image). it seems we should make sure the runtime info is up-to-date
      // through other means. this feels like a hack at some point to make sure
      // runtime info is updated on each graph generation.
      // clearRuntimeInfos(ainfo);
   }

   private void createEVGraph(EGraph egraph2, int ewidth, int eheight) {
      plotter2 = Plotter.getPlotter(egraph2);
      evgraph = plotter2.plot(this.data);
      plotter2 = null;

      LegendGroup vlegends = vgraph.getLegendGroup();
      LegendGroup evlegends = evgraph.getLegendGroup();

      // use the legend size of vgraph layout in evgraph to force the evgraph legend
      // to match vgraph.
      if(vlegends != null && evlegends != null) {
         for(int i = 0; i < vlegends.getLegendCount(); i++) {
            Legend vlegend = vlegends.getLegend(i);
            Rectangle2D vbounds = vlegend.getBounds();
            DimensionD psize = new DimensionD(vbounds.getWidth(), vbounds.getHeight());
            Legend evlegend = evlegends.getLegend(i);
            LegendSpec spec = evlegend.getVisualFrame().getLegendSpec();
            spec.setPreferredSize(psize);
         }
      }

      // For facet charts the expanded graph will be created even though there's no scrolling.
      // If that's the case then the scaled size will be smaller than the design size so we
      // will want to use the design size instead.
      evgraph.layout(0, 0, ewidth, eheight);
   }

   /**
    * Wait for init to complete (or cancelled).
    */
   public void waitInit() {
      synchronized(completed) {
         while(!completed.get() && !cancelled) {
            try {
               completed.wait(2000);
            }
            catch(InterruptedException e) {
            }
         }
      }
   }

   // Execute script and report error.
   private void executeScript(ViewsheetScope scope, String script, String cname,
                              boolean ignoreError)
   {
      try {
         scope.execute(script, cname);
      }
      catch(Throwable ex) {
         if(!ignoreError) {
            LOG.debug("Script failed", ex);

            String linemsg = Catalog.getCatalog().getString("Script failed") + ex.getMessage();
            Tool.addUserMessage(linemsg);
            // allow chart to be rendered when script fails
         }
      }
   }

   /**
    * Scale all the text spec font size with scale ratio.
    */
   private void scaleTextSpecs(EGraph graph, double scaleFont) {
      for(int i = 0; i < graph.getElementCount(); i++) {
         scaleGraphElementFont(graph.getElement(i), scaleFont);
      }

      scaleTextSpecFont(graph.getXTitleSpec().getTextSpec(), scaleFont);
      scaleTextSpecFont(graph.getYTitleSpec().getTextSpec(), scaleFont);
      scaleTextSpecFont(graph.getX2TitleSpec().getTextSpec(), scaleFont);
      scaleTextSpecFont(graph.getY2TitleSpec().getTextSpec(), scaleFont);

      Collection<Scale> scales = GraphUtil.getAllScales(graph.getCoordinate());

      for(Scale scale : scales) {
         AxisSpec aspec = scale.getAxisSpec();
         scaleTextSpecFont(aspec.getTextSpec(), scaleFont);
         aspec.setAxisSize(aspec.getAxisSize() * scaleFont);
      }

      VisualFrame[] frames = graph.getVisualFrames();

      for(VisualFrame frame : frames) {
         LegendSpec legendSpec = frame.getLegendSpec();

         if(legendSpec != null) {
            scaleTextSpecFont(legendSpec.getTextSpec(), scaleFont);
            scaleTextSpecFont(legendSpec.getTitleTextSpec(), scaleFont);
         }
      }

      for(int i = 0; i < graph.getFormCount(); i++) {
         GraphForm form = graph.getForm(i);

         if(form instanceof LabelForm) {
            scaleTextSpecFont(((LabelForm) form).getTextSpec(), scaleFont);
         }
         else if(form instanceof TargetForm) {
            scaleTextSpecFont(((TargetForm) form).getTextSpec(), scaleFont);
         }
      }
   }

   private void scaleGraphElementFont(GraphElement elem, double scaleFont) {
      if(elem == null) {
         return;
      }

      scaleTextSpecFont(elem.getTextSpec(), scaleFont);

      if(elem instanceof TreemapElement) {
         TreemapElement telem = (TreemapElement) elem;

         for(int j = 0; j < telem.getTreeDimCount(); j++) {
            String dim = telem.getTreeDim(j);
            scaleTextSpecFont(telem.getLabelTextSpec(dim), scaleFont);
         }
      }
   }

   /**
    * Scale the text spect font size with the scale ratio.
    */
   private void scaleTextSpecFont(TextSpec spec, double scaleFont) {
      Font font = spec.getFont();
      float scaledSize = (float) (font.getSize() * scaleFont);

      if(font.getSize() < 18 && scaledSize > 18) {
         scaledSize = 18;
      }

      if(font.getSize() >= 10 && scaledSize < 10) {
         scaledSize = 10;
      }

      font = font.deriveFont(scaledSize);
      spec.setFont(font);
   }

   /**
    * Copies any user or css defined format settings of the given Chart to
    * the default format of its sub-components. Also, sets parent css
    * parameter in CSSTextFormat.
    */
   private void fixChartFormat(ChartVSAssemblyInfo info) {
      if(info == null) {
         return;
      }

      info.updateChartTypeCssAttribute();
      FormatInfo formatInfo = info.getFormatInfo();
      VSCompositeFormat objFmt = formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);

      if(objFmt == null) {
         return;
      }

      ArrayList<CSSParameter> parentParams = info.getCssParentParameters();
      ChartDescriptor desc = info.getChartDescriptor();

      if(desc != null) {
         LegendsDescriptor legendsDesc = desc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat(true);
            copyDefaultFormat(legendsDesc.getTitleTextFormat().getDefaultFormat(), objFmt);
            CSSTextFormat legendTitle = legendsDesc.getTitleTextFormat().getCSSFormat();
            legendTitle.setParentCSSParams(parentParams);
            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat(true);
               copyDefaultFormat(colorDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               colorDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat(true);
               copyDefaultFormat(shapeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               shapeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat(true);
               copyDefaultFormat(sizeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               sizeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         TitlesDescriptor titlesDesc = desc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat(true);
               copyDefaultFormat(xDesc.getTextFormat().getDefaultFormat(), objFmt);
               xDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat(true);
               copyDefaultFormat(xDesc2.getTextFormat().getDefaultFormat(), objFmt);
               xDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat(true);
               copyDefaultFormat(yDesc.getTextFormat().getDefaultFormat(), objFmt);
               yDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat(true);
               copyDefaultFormat(yDesc2.getTextFormat().getDefaultFormat(), objFmt);
               yDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         PlotDescriptor plotDesc = desc.getPlotDescriptor();

         if(plotDesc != null) {
            plotDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.getErrorFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.initDefaultFormat(true);
            copyDefaultFormat(plotDesc.getTextFormat().getDefaultFormat(), objFmt);
         }

         for(int i = 0; i < desc.getTargetCount(); i++) {
            GraphTarget graphTarget = desc.getTarget(i);
            graphTarget.initDefaultFormat(true);
            copyDefaultFormat(graphTarget.getTextFormat().getDefaultFormat(), objFmt);
            graphTarget.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
         }
      }

      ChartDescriptor rdesc = info.getRTChartDescriptor();

      if(rdesc != null) {
         LegendsDescriptor legendsDesc = rdesc.getLegendsDescriptor();

         if(legendsDesc != null) {
            legendsDesc.initDefaultFormat(true);
            copyDefaultFormat(legendsDesc.getTitleTextFormat().getDefaultFormat(), objFmt);
            CSSTextFormat legendTitle = legendsDesc.getTitleTextFormat().getCSSFormat();
            legendTitle.setParentCSSParams(parentParams);
            LegendDescriptor colorDesc = legendsDesc.getColorLegendDescriptor();

            if(colorDesc != null) {
               colorDesc.initDefaultFormat(true);
               copyDefaultFormat(colorDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               colorDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor shapeDesc = legendsDesc.getShapeLegendDescriptor();

            if(shapeDesc != null) {
               shapeDesc.initDefaultFormat(true);
               copyDefaultFormat(shapeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               shapeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            LegendDescriptor sizeDesc = legendsDesc.getSizeLegendDescriptor();

            if(sizeDesc != null) {
               sizeDesc.initDefaultFormat(true);
               copyDefaultFormat(sizeDesc.getContentTextFormat().getDefaultFormat(), objFmt);
               sizeDesc.getContentTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         TitlesDescriptor titlesDesc = rdesc.getTitlesDescriptor();

         if(titlesDesc != null) {
            TitleDescriptor xDesc = titlesDesc.getXTitleDescriptor();

            if(xDesc != null) {
               xDesc.initDefaultFormat(true);
               copyDefaultFormat(xDesc.getTextFormat().getDefaultFormat(), objFmt);
               xDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor xDesc2 = titlesDesc.getX2TitleDescriptor();

            if(xDesc2 != null) {
               xDesc2.initDefaultFormat(true);
               copyDefaultFormat(xDesc2.getTextFormat().getDefaultFormat(), objFmt);
               xDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc = titlesDesc.getYTitleDescriptor();

            if(yDesc != null) {
               yDesc.initDefaultFormat(true);
               copyDefaultFormat(yDesc.getTextFormat().getDefaultFormat(), objFmt);
               yDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }

            TitleDescriptor yDesc2 = titlesDesc.getY2TitleDescriptor();

            if(yDesc2 != null) {
               yDesc2.initDefaultFormat(true);
               copyDefaultFormat(yDesc2.getTextFormat().getDefaultFormat(), objFmt);
               yDesc2.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         PlotDescriptor plotDesc = rdesc.getPlotDescriptor();

         if(plotDesc != null) {
            plotDesc.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.getErrorFormat().getCSSFormat().setParentCSSParams(parentParams);
            plotDesc.initDefaultFormat(true);
            copyDefaultFormat(plotDesc.getTextFormat().getDefaultFormat(), objFmt);
         }

         for(int i = 0; i < rdesc.getTargetCount(); i++) {
            GraphTarget graphTarget = rdesc.getTarget(i);
            graphTarget.initDefaultFormat(true);
            copyDefaultFormat(graphTarget.getTextFormat().getDefaultFormat(), objFmt);
            graphTarget.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
         }
      }

      VSChartInfo chartInfo = info.getVSChartInfo();

      if(chartInfo instanceof RadarChartInfo) {
         AxisDescriptor axisDesc = ((RadarChartInfo) chartInfo).getLabelAxisDescriptor();

         if(axisDesc != null) {
            axisDesc.initDefaultFormat(true);
            copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(), objFmt);
            axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);

            for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
               CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

               if(colFmt != null) {
                  copyDefaultFormat(colFmt.getDefaultFormat(), objFmt);
                  colFmt.getCSSFormat().setParentCSSParams(parentParams);
               }
            }
         }
      }

      if(chartInfo != null) {
         ChartRef[][] nrefs = {chartInfo.getXFields(), chartInfo.getYFields(),
            chartInfo.getGroupFields(), chartInfo.getBindingRefs(true)};

         for(ChartRef[] refs : nrefs) {
            int index = 0;

            for(ChartRef ref : refs) {
               ref.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
               AxisDescriptor axisDesc = ref.getAxisDescriptor();

               if(axisDesc != null) {
                  axisDesc.initDefaultFormat(true);
                  copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(),
                                    objFmt);
                  axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);

                  for(String col : axisDesc.getColumnLabelTextFormatColumns()) {
                     CompositeTextFormat colFmt = axisDesc.getColumnLabelTextFormat(col);

                     if(colFmt != null) {
                        if(StyleFont.isDefaultFont(colFmt.getDefaultFormat().getFont())) {
                           colFmt.getDefaultFormat().setFont(axisDesc.getAxisLabelTextFormat()
                                                                .getDefaultFormat().getFont());
                        }

                        initDefaultFormat(colFmt);
                        copyDefaultFormat(colFmt.getDefaultFormat(), objFmt);
                        colFmt.getCSSFormat().setParentCSSParams(parentParams);
                     }
                  }
               }

               if(ref instanceof VSChartAggregateRef) {
                  VSChartAggregateRef aggr = (VSChartAggregateRef) ref;
                  aggr.initDefaultFormat(true);
                  ColorFrame colorFrame = aggr.getColorFrame();

                  if(aggr.getTextField() != null &&
                     aggr.getTextField().getDataRef() instanceof ChartRef)
                  {
                     ChartRef textfield = (ChartRef) aggr.getTextField().getDataRef();
                     initDefaultFormat(textfield);
                     copyDefaultFormat(textfield.getTextFormat().getDefaultFormat(), objFmt);
                  }

                  if(colorFrame instanceof StaticColorFrame) {
                     StaticColorFrame staticFrame = (StaticColorFrame) colorFrame;
                     staticFrame.setIndex(index);
                     staticFrame.setParentParams(parentParams);
                     index++;
                  }
               }

               copyDefaultFormat(ref.getTextFormat().getDefaultFormat(), objFmt);
            }
         }

         VSDataRef[] vsDataRefs = chartInfo.getFields();

         if(chartInfo instanceof VSMapInfo) {
            vsDataRefs = (VSDataRef[]) ArrayUtils.addAll(vsDataRefs, ((VSMapInfo) chartInfo).getRTGeoFields());
         }

         for(VSDataRef ref : vsDataRefs) {
            if(ref instanceof VSChartDimensionRef) {
               VSChartDimensionRef chartDimRef = (VSChartDimensionRef) ref;
               chartDimRef.initDefaultFormat(true);
               copyDefaultFormat(chartDimRef.getTextFormat().getDefaultFormat(), objFmt);
               chartDimRef.getTextFormat().getCSSFormat().setParentCSSParams(parentParams);
            }
         }

         for(boolean runtime: new boolean[] { false, true }) {
            for(AestheticRef ref : chartInfo.getAestheticRefs(runtime)) {
               ref.getLegendDescriptor().initDefaultFormat(true);
               copyDefaultFormat(ref.getLegendDescriptor().getContentTextFormat()
                                 .getDefaultFormat(), objFmt);

               if(ref.getVisualFrame() instanceof CategoricalColorFrame) {
                  ((CategoricalColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
               }
               else if(ref.getVisualFrame() instanceof GradientColorFrame) {
                  ((GradientColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
               }
               else if(ref.getVisualFrame() instanceof HSLColorFrame) {
                  ((HSLColorFrame) ref.getVisualFrame()).setParentParams(parentParams);
               }
            }
         }

         Arrays.stream(chartInfo.getRuntimeDateComparisonRefs())
            .filter(ref -> ref instanceof ChartAggregateRef)
               .forEach(ref -> {
                  AestheticRef[] arefs = {
                     ((ChartAggregateRef) ref).getColorField(),
                     ((ChartAggregateRef) ref).getShapeField(),
                     ((ChartAggregateRef) ref).getSizeField(),
                  };

                  for(AestheticRef aref : arefs) {
                     if(aref != null) {
                        CompositeTextFormat fmt = aref.getLegendDescriptor().getContentTextFormat();
                        initDefaultFormat(fmt);
                        copyDefaultFormat(fmt.getDefaultFormat(), objFmt);
                     }
                  }
               });

         AxisDescriptor axisDesc = chartInfo.getAxisDescriptor();
         AxisDescriptor axisDesc2 = chartInfo.getAxisDescriptor2();

         copyDefaultFormat(objFmt, parentParams, axisDesc);
         copyDefaultFormat(objFmt, parentParams, axisDesc2);

         if(chartInfo.getTextField() != null && chartInfo.getTextField().getDataRef() instanceof ChartRef) {
            ChartRef ref = (ChartRef) chartInfo.getTextField().getDataRef();

            if(ref != null) {
               initDefaultFormat(ref);
               copyDefaultFormat(ref.getTextFormat().getDefaultFormat(), objFmt);
            }
         }
      }

      CSSChartStyles.apply(info.getChartDescriptor(), info.getVSChartInfo(), null, parentParams);
   }

   private void copyDefaultFormat(VSCompositeFormat objFmt, ArrayList<CSSParameter> parentParams,
                                  AxisDescriptor axisDesc)
   {
      if(axisDesc != null) {
         axisDesc.initDefaultFormat(true);
         copyDefaultFormat(axisDesc.getAxisLabelTextFormat().getDefaultFormat(), objFmt);
         axisDesc.getAxisLabelTextFormat().getCSSFormat().setParentCSSParams(parentParams);
      }
   }

   private void initDefaultFormat(ChartRef ref) {
      if(ref instanceof VSChartDimensionRef) {
         ((VSChartDimensionRef) ref).initDefaultFormat(true);
      }
      else if(ref instanceof VSChartAggregateRef) {
         ((VSChartAggregateRef) ref).initDefaultFormat(true);
      }
   }

   private void initDefaultFormat(CompositeTextFormat format) {
      if(format == null) {
         return;
      }

      TextFormat deffmt = format.getDefaultFormat();
      deffmt.setColor(GDefaults.DEFAULT_TEXT_COLOR);
   }

   /**
    * Copies font and color settings from Chart's VSCompositeFormat to the
    * specified TextFormat.
    */
   private void copyDefaultFormat(TextFormat tfmt, VSCompositeFormat cfmt) {
      VSCSSFormat cssFormat = cfmt.getCSSFormat();
      VSFormat userFormat = cfmt.getUserDefinedFormat();

      if(cssFormat.isForegroundValueDefined() || userFormat.isForegroundValueDefined()) {
         tfmt.setColor(cfmt.getForeground());
      }

      if(cssFormat.isFontValueDefined() || userFormat.isFontValueDefined()) {
         tfmt.setFont(cfmt.getFont());
      }
   }

   /**
    * Sort the categorical scale values if the comparator is changed by script.
    */
   private void sortFrameValues(EGraph egraph, DataSet data) {
      if(data != null) {
         VisualFrame[] frames = egraph.getVisualFrames();

         for(VisualFrame frame : frames) {
            if(frame.getField() != null) {
               Comparator comp = data.getComparator(frame.getField());

               if(comp != null && !(comp instanceof DefaultComparer)) {
                  VSFrameVisitor.syncCategoricalFrame(frame, data);
               }
            }
         }
      }
   }

   /**
    * Get visual data set.
    */
   private DataSet getVisualDataSet(ViewsheetSandbox box,
                                    ChartVSAssemblyInfo ainfo) throws Exception
   {
      /*
      boolean consistent = "true".equals(
         SreeEnv.getProperty("graph.visual.consistency.viewsheet"));

      if(!consistent) {
         return null;
      }

      VSChartInfo cinfo = ainfo.getVSChartInfo();

      if(cinfo != null &&
         cinfo.getRTFields(false, true, true, false).length <= 0)
      {
         return null;
      }

      return (DataSet) box.getData(ainfo.getAbsoluteName(), true,
                                   DataMap.NO_FILTER);
      */
      return null;
   }

   /**
    * Check if chart is changed by script.
    */
   private boolean needGenerateNewGraph(ChartVSAssemblyInfo ainfo,
      VSChartInfo oinfo, ChartDescriptor desc)
   {
      ChartDescriptor rdesc = ainfo.getRTChartDescriptor();
      VSChartInfo ninfo = ainfo.getVSChartInfo();

      if(ninfo == null) {
         return false;
      }

      if(!ninfo.equalsContent(oinfo)) {
         return true;
      }

      if(ninfo.getRTChartType() != oinfo.getRTChartType()) {
         return true;
      }

      if(rdesc == null) {
         return false;
      }

      if(!rdesc.equalsContent(desc)) {
         return true;
      }

      if(ninfo.getRTAxisDescriptor() != null &&
         !ninfo.getRTAxisDescriptor().equalsContent(oinfo.getAxisDescriptor()))
      {
         return true;
      }

      if(ninfo.getRTAxisDescriptor2() != null &&
         !ninfo.getRTAxisDescriptor2().equalsContent(oinfo.getAxisDescriptor2()))
      {
         return true;
      }

      ChartRef[][] nrefs = {ninfo.getRTXFields(), ninfo.getRTYFields()};
      ChartRef[][] orefs = {oinfo.getRTXFields(), oinfo.getRTYFields()};

      // if axis descriptors changed in script and not included in egraph
      for(int i = 0; i < nrefs.length; i++) {
         for(int j = 0; j < nrefs[i].length; j++) {
            VSChartRef nref = (VSChartRef) nrefs[i][j];
            VSChartRef oref = (VSChartRef) orefs[i][j];

            // ChartScriptable creates RTAxisDescriptor so if the
            // original ref has a null rt axis descriptor, we
            // shouldn't treat it as changed by script
            if(oref.getRTAxisDescriptor() != null &&
               !nref.getRTAxisDescriptor().
               equalsContent(oref.getRTAxisDescriptor()))
            {
               return true;
            }
         }
      }

      return isMapInfoChanged(oinfo, ninfo);
   }

   /**
    * Check whethere map info is changed.
    */
   private boolean isMapInfoChanged(VSChartInfo oinfo, VSChartInfo ninfo) {
      if(!(ninfo instanceof VSMapInfo) || !(oinfo instanceof VSMapInfo)) {
         return false;
      }

      VSMapInfo mapInfo0 = (VSMapInfo) oinfo;
      VSMapInfo mapInfo1 = (VSMapInfo) ninfo;
      ChartRef[] refs0 = mapInfo0.getRTGeoFields();
      ChartRef[] refs1 = mapInfo1.getRTGeoFields();

      if(refs0.length != refs1.length) {
         return true;
      }

      for(int i = 0; i < refs0.length; i++) {
         if(!refs0[i].equals(refs1[i])) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if chart is changed by script in structure or not.
    */
   private boolean isChangedByScript0(DataSet ndata, DataSet data,
                                      EGraph ngraph, EGraph graph) {
      if(ndata != null && ndata != data) {
         isStructureChanged = true;
         return true;
      }

      if(ngraph == graph) {
         return false;
      }

      if(graph == null || ngraph == null) {
         isStructureChanged = true;
         return true;
      }

      isStructureChanged = isStructureChanged(ngraph, graph);
      int cnt = graph.getElementCount();

      if(cnt != ngraph.getElementCount()) {
         return true;
      }

      for(int i = 0; i < cnt; i++) {
         GraphElement elem = graph.getElement(i);
         GraphElement nelem = ngraph.getElement(i);

         if(!elem.equalsContent(nelem)) {
            return true;
         }
      }

      cnt = graph.getFormCount();

      if(cnt != ngraph.getFormCount()) {
         return true;
      }

      for(int i = 0; i < cnt; i++) {
         GraphForm form = graph.getForm(i);
         GraphForm nform = ngraph.getForm(i);

         if(!form.equalsContent(nform)) {
            return true;
         }
      }

      return isCoordChanged(ngraph, graph);
   }

   /**
    * Check if the ngraph's binding structure is changed or not, to determine
    * if we should support interactions such as brush\showDetail etc
    */
   private boolean isStructureChanged(EGraph ngraph, EGraph graph) {
      String[] ndims = collectDims(ngraph);
      String[] dims = collectDims(graph);

      // The key point for interactions are the dims, which will be used
      // to generate filter, so if dims is changed, structure changed
      if(!Arrays.equals(ndims, dims)) {
         return true;
      }

      return false;
   }

   /**
    * Check if the coords of two EGraph is the same
    */
   private boolean isCoordChanged(EGraph ngraph, EGraph graph) {
      Coordinate coord = graph.getCoordinate();
      Coordinate ncoord = ngraph.getCoordinate();

      if(coord == ncoord) {
         return false;
      }
      else if(coord == null || ncoord == null) {
         return true;
      }

      return !ncoord.equalsContent(coord);
   }

   /**
    * Collect the dims used in a EGraph
    */
   private String[] collectDims(EGraph graph) {
      Vector<String> vec = new Vector<>();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         for(int j = 0; j < elem.getDimCount(); j++) {
            String dim = elem.getDim(j);

            if(dim != null && !vec.contains(dim)) {
               vec.add(dim);
            }
         }
      }

      VisualFrame[] frames = graph.getVisualFrames();

      for(int i = 0; i < frames.length; i++) {
         VisualFrame frame = frames[i];

         if(frame != null && frame instanceof CategoricalFrame) {
            String field = frame.getField();

            if(field != null && !vec.contains(field)) {
               vec.add(field);
            }
         }
      }

      String[] dims = new String[0];
      dims = vec.toArray(dims);
      Arrays.sort(dims);

      return dims;
   }

   /**
    * Get data set.
    */
   public DataSet getData() {
      return data;
   }

   /**
    * Get the image for plot.
    */
   public BufferedImage getPlotImage(int row, int col) {
      final VGraph vgraph = getExpandedVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, getRealSizeVGraph(), cinfo);
      return getFlipYSubimage(vgraph, gbounds.getPlotBounds(), row, col, true,
         getEVGraphContext(true));
   }

   /**
    * Get the (map) plot background image.
    */
   public BufferedImage getPlotBackgroundImage() {
      try {
         return (BufferedImage) getExpandedVGraph().getOrCreatePlotBackground();
      }
      catch(IOException e) {
         LOG.warn("Failed to load plot background image", e);
         return null;
      }
   }

   /**
    * Get the image for top x axis.
    */
   public BufferedImage getTopXImage(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds  = egbounds.getAxisBounds(Coordinate.TOP_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds  = gbounds.getAxisBounds(Coordinate.TOP_AXIS);

      // @by arrowz to fix bug1224820846406. If top x axis's expanded height is
      // larger than real height, axis will be out of chart, so real height is
      // used.
      if(bounds != null && ebounds.getHeight() > bounds.getHeight()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
            ebounds.getWidth(), bounds.getHeight());
      }

      return getFlipYSubimage(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for bottom x axis.
    */
   public BufferedImage getBottomXImage(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds = egbounds.getAxisBounds(Coordinate.BOTTOM_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.BOTTOM_AXIS);

      if(bounds != null && ebounds.getHeight() > bounds.getHeight()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
            ebounds.getWidth(), bounds.getHeight());
      }

      return getFlipYSubimage(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for left y axis.
    */
   public BufferedImage getLeftYImage(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds = egbounds.getAxisBounds(Coordinate.LEFT_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.LEFT_AXIS);

      if(bounds != null && ebounds.getWidth() > bounds.getWidth()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
            bounds.getWidth(), ebounds.getHeight());
      }

      return getFlipYSubimage(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for right y axis.
    */
   public BufferedImage getRightYImage(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.RIGHT_AXIS);

      if(bounds == null || bounds.getWidth() < 0 || bounds.getHeight() < 0) {
         return null;
      }

      return getFlipYSubimage(evgraph, bounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for x title.
    */
   public BufferedImage getXTitleImage(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubimage(vgraph, gbounds.getXTitleBounds(), row, col, false,
         getEVGraphContext(true));
   }

   /**
    * Get the image for x2 title.
    */
   public BufferedImage getX2TitleImage(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubimage(vgraph, gbounds.getX2TitleBounds(), row, col, false,
         getEVGraphContext(true));
   }

   /**
    * Get the image for y title.
    */
   public BufferedImage getYTitleImage(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubimage(vgraph, gbounds.getYTitleBounds(), row, col, false,
         getEVGraphContext(true));
   }

   /**
    * Get the image for 2nd y title.
    */
   public BufferedImage getY2TitleImage(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubimage(vgraph, gbounds.getY2TitleBounds(), row, col, false,
         getEVGraphContext(true));
   }

   /**
    * Get the image for facet corner.
    */
   public BufferedImage getFacetTLImage() {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      final GraphPaintContext ctx = new GraphPaintContextImpl.Builder()
         .paintLegends(false)
         .paintVOVisuals(evgraph == null)
         .build();
      return getFlipYSubimage(vgraph, gbounds.getFacetTLBounds(), 0, 0, false, ctx);
   }

   /**
    * Get the image for facet corner.
    */
   public BufferedImage getFacetTRImage() {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      final GraphPaintContext ctx = new GraphPaintContextImpl.Builder()
         .paintLegends(false)
         .paintVOVisuals(evgraph == null)
         .build();
      return getFlipYSubimage(vgraph, gbounds.getFacetTRBounds(), 0, 0, false, ctx);
   }

   /**
    * Get the image for facet corner.
    */
   public BufferedImage getFacetBLImage() {
      final VGraph vgraph = getRealSizeVGraph();

      // use evgraph to avoid the label being clipped at the bottom-left
      // corner when it's rotated 45 degrees
      final GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      // @by stephenwebster, For Bug #1148
      // This is sort of a stop-gap solution.  There is logic above to
      // satisfy a requirement to not cut the first label when it is
      // rotated.  However, when scrolling it doesn't really work.
      // For now, when scrolling, ignore this requirement, cut the subbounds
      // based on the evgraph.
      return getFlipYSubimage(evgraph, gbounds.getFacetBLBounds(), 0, 0, false,
                              getEVGraphContext(true));
   }

   /**
    * Get the image for facet corner.
    */
   public BufferedImage getFacetBRImage() {
      final VGraph vgraph = getRealSizeVGraph();
      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      // don't hide label, otherwise the right label at bottom x axis
      // may be clipped when there is a 2nd y axis on the right
      boolean noScroll = evgraph == null || vgraph.getBounds().equals(evgraph.getBounds());
      return getFlipYSubimage(evgraph, gbounds.getFacetBRBounds(), 0, 0, false,
                              getEVGraphContext(noScroll));
   }

   /**
    * Get the image for the specified legend.
    * @param idx legend index.
    */
   public BufferedImage getLegendTitleImage(int idx, int row, int col) {
      return getLegendImage(idx, true, row, col);
   }

   /**
    * Get the image for the specified legend.
    * @param idx legend index.
    */
   public BufferedImage getLegendContentImage(int idx, int row, int col) {
      return getLegendImage(idx, false, row, col);
   }

   /**
    * Get the image for the specified legend.
    * @param isTitle true if paint title.
    */
   private BufferedImage getLegendImage(int idx, boolean isTitle, int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      // use the real size legend to paint
      final Legend legend = vgraph.getLegendGroup() == null ?
         null : vgraph.getLegendGroup().getLegend(idx);

      if(legend == null) {
         return null;
      }

      Rectangle2D bounds = isTitle ? legend.getTitleBounds() :
                                     legend.getContentPreferredBounds();

      if(bounds == null) {
         return null;
      }

      Rectangle2D fbounds = getFlipYBounds(vgraph, bounds);
      int biw = (int) (fbounds.getX() + fbounds.getWidth());
      int bih = (int) (fbounds.getY() + fbounds.getHeight());

      if(biw <= 0 || bih <= 0) {
         return null;
      }

      BufferedImage bi = new BufferedImage(biw, bih,
                                           BufferedImage.TYPE_INT_ARGB);
      double height = vgraph.getSize().getHeight();
      Graphics2D g = (Graphics2D) bi.getGraphics();
      LegendSpec spec = legend.getVisualFrame().getLegendSpec();

      // background for legend is drawn in gui instead of in image. force it to
      // not draw in legend so semi-transparent colors aren't on top of each other
      legend.setPaintBackground(false);

      if(spec.getBackground() != null) {
         g.setColor(spec.getBackground());
      }
      else {
         g.setColor(new Color(255, 255, 255, 0));
      }

      g.fill(new Rectangle2D.Double(0, 0, biw, bih));

      g.translate(0, height);
      g.transform(GDefaults.FLIPY);

      if(isTitle) {
         legend.paintTitle(g);
      }
      else {
         legend.paintContent(g);
      }

      g.dispose();
      legend.setPaintBackground(true);

      return getSubimage(bi, vgraph, fbounds, row, col);
   }

   /**
    * Get filp y sub image of a BufferedImage.
    * @param bounds the sub image bounds.
    */
   private BufferedImage getFlipYSubimage(VGraph graph, Rectangle2D bounds,
                                          int row, int col, boolean restrict, GraphPaintContext ctx)
   {
      return getSubimage(graph, getFlipYBounds(graph, bounds), row, col, restrict, ctx);
   }

   /**
    * Get sub image of a graph.
    * @param bounds the sub image bounds.
    */
   private BufferedImage getSubimage(VGraph graph, Rectangle2D bounds,
                                     int row, int col, boolean restrict, GraphPaintContext ctx)
   {
      final Rectangle subox = getSubBounds(graph, bounds, row, col, restrict);

      if(subox == null) {
         return null;
      }

      int w = (int) subox.getWidth();
      int h = (int) subox.getHeight();
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D) img.getGraphics();

      g.clipRect(0, 0, w + 1, h + 1);
      g.translate(-subox.getX(), -subox.getY());
      graph.paintGraph(g, ctx);
      g.dispose();

      return img;
   }

   /**
    * Get sub image of a BufferedImage.
    * @param bi the source buffered image.
    * @param bounds the sub image bounds.
    */
   private BufferedImage getSubimage(BufferedImage bi, VGraph graph,
                                     Rectangle2D bounds, int row, int col)
   {
      Rectangle box = getSubBounds(graph, bounds, row, col, false);

      if(box == null) {
         return null;
      }

      return bi.getSubimage(box.x, box.y, box.width, box.height);
   }

   /**
    * Get image.
    */
   public synchronized void paintChart(Graphics2D g, boolean matchLayout) {
      VGraph graph = matchLayout ? vgraph : getExpandedVGraph();
      paint(g, graph, matchLayout);
   }

   /**
    * Get the image for plot.
    */
   public Graphics2D getPlotGraphic(int row, int col) {
      final VGraph vgraph = getExpandedVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, getRealSizeVGraph(), cinfo);
      return getFlipYSubGraphic(vgraph, gbounds.getPlotBounds(), row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for top x axis.
    */
   public Graphics2D getTopXGraphic(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds  = egbounds.getAxisBounds(Coordinate.TOP_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds  = gbounds.getAxisBounds(Coordinate.TOP_AXIS);

      // @by arrowz to fix bug1224820846406. If top x axis's expanded height is
      // larger than real height, axis will be out of chart, so real height is
      // used.
      if(bounds != null && ebounds.getHeight() > bounds.getHeight()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
                                          ebounds.getWidth(), bounds.getHeight());
      }

      // single pixel line may be missing in browser for unknow reasion, just use image
      if(ebounds.getHeight() < 3) {
         return null;
      }

      return getFlipYSubGraphic(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for bottom x axis.
    */
   public Graphics2D getBottomXGraphic(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds = egbounds.getAxisBounds(Coordinate.BOTTOM_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, getRealSizeVGraph(), cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.BOTTOM_AXIS);

      if(bounds != null && ebounds.getHeight() > bounds.getHeight()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
                                          ebounds.getWidth(), bounds.getHeight());
      }

      // single pixel line may be missing in browser for unknown reason, just use image
      if(ebounds.getHeight() < 3) {
         return null;
      }

      return getFlipYSubGraphic(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for left y axis.
    */
   public Graphics2D getLeftYGraphic(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds egbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D ebounds = egbounds.getAxisBounds(Coordinate.LEFT_AXIS);

      if(ebounds == null || ebounds.getWidth() < 0 || ebounds.getHeight() < 0) {
         return null;
      }

      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.LEFT_AXIS);

      if(bounds != null && ebounds.getWidth() > bounds.getWidth()) {
         ebounds = new Rectangle2D.Double(ebounds.getX(), ebounds.getY(),
                                          bounds.getWidth(), ebounds.getHeight());
      }

      return getFlipYSubGraphic(evgraph, ebounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for right y axis.
    */
   public Graphics2D getRightYGraphic(int row, int col) {
      final VGraph evgraph = getExpandedVGraph();
      final VGraph vgraph = getRealSizeVGraph();
      GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getAxisBounds(Coordinate.RIGHT_AXIS);

      if(bounds == null || bounds.getWidth() < 0 || bounds.getHeight() < 0) {
         return null;
      }

      return getFlipYSubGraphic(evgraph, bounds, row, col, true, getEVGraphContext(true));
   }

   /**
    * Get the image for x title.
    */
   public Graphics2D getXTitleGraphic(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubGraphic(vgraph, gbounds.getXTitleBounds(), row, col, false,
         getVGraphContext());
   }

   /**
    * Get the image for x2 title.
    */
   public Graphics2D getX2TitleGraphic(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      Rectangle2D bounds = gbounds.getX2TitleBounds();

      // see comments in x2 axis
      if(bounds.getHeight() < 3) {
         return null;
      }

      return getFlipYSubGraphic(vgraph, bounds, row, col, false, getVGraphContext());
   }

   /**
    * Get the image for y title.
    */
   public Graphics2D getYTitleGraphic(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubGraphic(vgraph, gbounds.getYTitleBounds(), row, col, false,
         getVGraphContext());
   }

   /**
    * Get the image for 2nd y title.
    */
   public Graphics2D getY2TitleGraphic(int row, int col) {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      return getFlipYSubGraphic(vgraph, gbounds.getY2TitleBounds(), row, col, false,
         getVGraphContext());
   }

   /**
    * Get the image for facet corner.
    */
   public Graphics2D getFacetTLGraphic() {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      final GraphPaintContext ctx = new GraphPaintContextImpl.Builder()
         .paintLegends(false)
         .paintVOVisuals(evgraph == null)
         .build();
      final Rectangle2D facetTLBounds = gbounds.getFacetTLBounds();
      return getFlipYSubGraphic(vgraph, facetTLBounds, 0, 0, false, ctx);
   }

   /**
    * Get the image for facet corner.
    */
   public Graphics2D getFacetTRGraphic() {
      final VGraph vgraph = getRealSizeVGraph();

      GraphBounds gbounds = new GraphBounds(vgraph, vgraph, cinfo);
      final GraphPaintContext ctx = new GraphPaintContextImpl.Builder()
         .paintLegends(false)
         .paintVOVisuals(evgraph == null)
         .build();
      return getFlipYSubGraphic(vgraph, gbounds.getFacetTRBounds(), 0, 0, false, ctx);
   }

   /**
    * Get the image for facet corner.
    */
   public Graphics2D getFacetBLGraphic() {
      final VGraph vgraph = getRealSizeVGraph();

      // use evgraph to avoid the label being clipped at the bottom-left
      // corner when it's rotated 45 degrees
      final GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      // @by stephenwebster, For Bug #1148
      // This is sort of a stop-gap solution.  There is logic above to
      // satisfy a requirement to not cut the first label when it is
      // rotated.  However, when scrolling it doesn't really work.
      // For now, when scrolling, ignore this requirement, cut the subbounds
      // based on the evgraph.
      return getFlipYSubGraphic(evgraph, gbounds.getFacetBLBounds(), 0, 0, false,
                                getEVGraphContext(true));
   }

   /**
    * Get the image for facet corner.
    */
   public Graphics2D getFacetBRGraphic() {
      final VGraph vgraph = getRealSizeVGraph();
      final GraphBounds gbounds = new GraphBounds(evgraph, vgraph, cinfo);
      // don't hide label, otherwise the right label at bottom x axis
      // may be clipped when there is a 2nd y axis on the right.
      // changed to use evgraph instead of vgraph to make sure the rightmost label
      // is not clipped. (52070)
      // if scroll, don't display partial label on the right edge or it will be
      // 'detached' from the axis when scrolled. (52868)
      boolean noScroll = evgraph == null || vgraph.getBounds().equals(evgraph.getBounds());
      return getFlipYSubGraphic(evgraph, gbounds.getFacetBRBounds(), 0, 0, false,
                                getEVGraphContext(noScroll));
   }

   /**
    * Get the image for the specified legend.
    * @param idx legend index.
    */
   public Graphics2D getLegendTitleGraphic(int idx, int row, int col) {
      return getLegendGraphic(idx, true, row, col);
   }

   /**
    * Get the image for the specified legend.
    * @param idx legend index.
    */
   public Graphics2D getLegendContentGraphic(int idx, int row, int col) {
      return getLegendGraphic(idx, false, row, col);
   }

   /**
    * Get the image for the specified legend.
    * @param isTitle true if paint title.
    */
   private Graphics2D getLegendGraphic(int idx, boolean isTitle, int row, int col) {
      final VGraph vgraph = this.vgraph;
      // use the real size legend to paint
      final Legend legend = vgraph.getLegendGroup() == null ?
         null : vgraph.getLegendGroup().getLegend(idx);

      if(legend == null) {
         return null;
      }

      Rectangle2D bounds = isTitle ? legend.getTitleBounds() :
         legend.getContentPreferredBounds();

      if(bounds == null) {
         return null;
      }

      Rectangle2D fbounds = getFlipYBounds(vgraph, bounds);
      int biw = (int) (fbounds.getX() + fbounds.getWidth());
      int bih = (int) (fbounds.getY() + fbounds.getHeight());

      if(biw <= 0 || bih <= 0) {
         return null;
      }

      double height = vgraph.getSize().getHeight();
      final Graphics2D g = SVGSupport.getInstance().createSVGGraphics();
      LegendSpec spec = legend.getVisualFrame().getLegendSpec();

      SVGSupport.getInstance().setCanvasSize(
         g, new Dimension((int) bounds.getWidth(), (int) Math.min(bounds.getHeight(), 1024)));
      // background for legend is drawn in gui instead of in image. force it to
      // not draw in legend so semi-transparent colors aren't on top of each other
      legend.setPaintBackground(false);

      if(spec.getBackground() != null) {
         g.setColor(spec.getBackground());
      }
      else {
         g.setColor(new Color(255, 255, 255, 0));
      }

      g.fill(new Rectangle2D.Double(0, 0, biw, bih));
      g.translate(0, height);

      // position the legend to the top-left corner of the graphic. with SVG clipping is rendered
      // on the frontend so even though we clip at the correct position, the actual position of the
      // legend is 2x farther than it should be. Here we undo that positioning so the legend lines
      // up with the container
      g.translate(-bounds.getX(), -fbounds.getY());
      g.transform(GDefaults.FLIPY);

      final Rectangle box = getSubBounds(vgraph, fbounds, row, col, false);

      if(box != null) {
         g.translate(-(box.x - bounds.getX()), box.y - fbounds.getY());
      }

      if(isTitle) {
         legend.paintTitle(g);
      }
      else {
         legend.paintContent(g);
      }

      g.dispose();
      legend.setPaintBackground(true);
      return g;
   }

   /**
    * Get filp y sub image of a BufferedImage.
    * @param bounds the sub image bounds.
    */
   private Graphics2D getFlipYSubGraphic(VGraph graph, Rectangle2D bounds,
                                         int row, int col, boolean restrict, GraphPaintContext ctx)
   {
      return getSubGraphic(graph, getFlipYBounds(graph, bounds), row, col, restrict, ctx);
   }

   /**
    * Get sub image of a graph.
    * @param bounds the sub image bounds.
    */
   private Graphics2D getSubGraphic(VGraph graph, Rectangle2D bounds,
                                    int row, int col, boolean restrict, GraphPaintContext ctx)
   {
      Rectangle subox = getSubBounds(graph, bounds, row, col, restrict);

      if(subox == null || subox.isEmpty()) {
         return null;
      }

      // need to add 1 (0.5) to avoid clipping half of a line off in case the line is
      // at the edge of the image. for example, a vertical line would have a width of 1px.
      // if drawing at 0, half of the line will be clipped.
      int w = (int) subox.getWidth() + 1;
      int h = (int) subox.getHeight() + 1;
      SVGSupport svgSupport = SVGSupport.getInstance();
      final Graphics2D g = svgSupport.createSVGGraphics();
      svgSupport.setCanvasSize(g, new Dimension(w, h));
      g.clipRect(0, 0, w, h);
      // shift by 0.5 to avoid clipping half of line. see above.
      g.translate(-subox.getX() + 0.5, -subox.getY() + 0.5);
      graph.paintGraph(g, ctx);
      g.dispose();

      return g;
   }

   /**
    * Get the bounds of the sub-image.
    */
   private Rectangle getSubBounds(VGraph graph, Rectangle2D bounds,
                                  int row, int col, boolean restrict)
   {
      int x = (int) (bounds.getX() + col * ChartArea.MAX_IMAGE_SIZE);
      int y = (int) (bounds.getY() + row * ChartArea.MAX_IMAGE_SIZE);
      int width = (int) calculateSize(bounds.getWidth(), col);
      int height = (int) calculateSize(bounds.getHeight(), row);

      // fix error here
      if(restrict) {
         int iwidth = (graph == getExpandedVGraph()) ? ewidth : width;
         int iheight = (graph == getExpandedVGraph()) ? eheight : height;

         x = Math.min(x, iwidth - 1);
         x = Math.max(0, x);
         y = Math.min(y, iheight - 1);
         y = Math.max(0, y);
         width = width + x > iwidth ? iwidth - x : width;
         height = height + y > iheight ? iheight - y : height;
      }

      if(width <= 0 || height <= 0 || x < 0 || y < 0) {
         return null;
      }

      return new Rectangle(x, y, width, height);
   }

   /**
    * Caculate the real index image width or height.
    */
   private double calculateSize(double size, int index) {
      int mindex = (int) Math.ceil(size / ChartArea.MAX_IMAGE_SIZE);

      if(index < mindex - 1) {
         return ChartArea.MAX_IMAGE_SIZE;
      }
      else {
         return size - index * ChartArea.MAX_IMAGE_SIZE;
      }
   }

   /**
    * Get filp y bounds.
    * @param bounds the sub image bounds.
    */
   private Rectangle2D getFlipYBounds(VGraph graph, Rectangle2D bounds) {
      if(graph == null) {
         return new Rectangle2D.Double(0, 0, 0, 0);
      }

      double height = graph.getSize().getHeight();
      Point2D posi = graph.getPosition();
      double w = Math.ceil(bounds.getWidth());
      double h = Math.ceil(bounds.getHeight());
      double x = bounds.getX() + posi.getX();
      double y = (int) (posi.getY() + height - (bounds.getY() + h));

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Get expanded visual graph.
    * @return expanded visual graph.
    */
   public VGraph getExpandedVGraph() {
      return evgraph == null ? getRealSizeVGraph() : evgraph;
   }

   private GraphPaintContext getEVGraphContext(boolean axes) {
      if(evgraph != null) {
         return new GraphPaintContextImpl.Builder()
            .paintLegends(false)
            .paintTitles(false)
            .paintAxes(axes)
            .build();
      }
      else {
         return getVGraphContext();
      }
   }

   private GraphPaintContext getVGraphContext() {
      return new GraphPaintContextImpl.Builder()
         .paintLegends(false)
         .paintAxes(evgraph == null)
         .paintVOVisuals(evgraph == null)
         .build();
   }

   /**
    * Get real size visual graph.
    * @return real size visual graph.
    */
   public VGraph getRealSizeVGraph() {
      return vgraph;
   }

   /**
    * Get element graph.
    * @return element graph.
    */
   public EGraph getEGraph() {
      return egraph;
   }

   /**
    * Get image.
    */
   public synchronized BufferedImage getImage(boolean matchLayout, int dpi) {
      Rectangle2D bounds = matchLayout ? vgraph.getBounds()
         : getExpandedVGraph().getBounds();
      double w = bounds.getWidth();
      double h = bounds.getHeight();
      double scale = dpi / 72.0;
      BufferedImage img = new BufferedImage((int) (w * scale), (int) (h * scale),
                                            BufferedImage.TYPE_INT_ARGB);
      VGraph graph = matchLayout ? vgraph : getExpandedVGraph();
      Graphics2D g = (Graphics2D) img.getGraphics();

      g.scale(scale, scale);
      paint(g, graph, matchLayout);
      g.dispose();

      return img;
   }

   /**
    * Get a piece image of the chart.
    * @param match identify if is to match the layout.
    * @param start the start point of the piece image.
    * @param size the size of the piece image.
    * @return the piece image.
    */
   public synchronized BufferedImage getImage(
      boolean match, Point start, Dimension size, VSChart chart) {
      VGraph graph = match ? vgraph : getExpandedVGraph();
      Rectangle2D sbounds = new Rectangle2D.Double(start.x, start.y,
                                                   size.width, size.height);
      int w = (int) sbounds.getWidth();
      int h = (int) sbounds.getHeight();
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = (Graphics2D) img.getGraphics();

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getAssemblyInfo();
      int titleHeight = 0;

      if(info.isTitleVisible()) {
         titleHeight = info.getTitleHeight();
      }

      g.clipRect(0, 0, w + 1, h + 1);
      g.translate(-sbounds.getX(), -sbounds.getY());

      // draw borders and background
      chart.paint(g);

      // translate by padding and title height
      g.translate(info.getPadding().left, info.getPadding().top + titleHeight);

      // draw the graph
      paint(g, graph, match);
      g.dispose();

      return img;
   }

   /**
    * Paint the graph.
    */
   private void paint(Graphics2D g, VGraph graph, boolean matchLayout) {
      final GraphPaintContextImpl.Builder builder = new GraphPaintContextImpl.Builder()
         .paintLegends(false);

      if(!matchLayout && evgraph == null && GraphUtil.isScrollable(vgraph, getChartInfo())) {
         builder.paintAxes(false).paintVOVisuals(false);
      }

      g.setClip(getContentClipBounds(graph));
      graph.paintGraph(g, builder.build());
      g.setClip(null);

      LegendGroup legends = graph.getLegendGroup();
      double height = graph.getSize().getHeight();

      g.translate(0, height);
      g.transform(GDefaults.FLIPY);

      for(int i = 0; legends != null && i < legends.getLegendCount(); i++) {
         legends.getLegend(i).paint(g);
      }

      g.dispose();
   }

   /**
    * Get content clip bounds.
    */
   private Rectangle2D getContentClipBounds(VGraph graph) {
      int legendLayout = graph.getEGraph().getLegendLayout();
      Rectangle2D cbounds = graph.getContentBounds();
      double x = cbounds.getX();
      double y = cbounds.getY();
      double w = cbounds.getWidth();
      double h = cbounds.getHeight();

      if(legendLayout == GraphConstants.TOP) {
         // the graph.getBounds().getHeight() need minus 1,
         // because the height minus 1 when VGraph layout
         y = graph.getBounds().getHeight() - 1 - h;
      }
      else if(legendLayout == GraphConstants.BOTTOM) {
         y = 0;
      }

      return new Rectangle2D.Double(x, y, w + 1, h + 1);
   }

   /**
    * Check if the chart is produced with a fake value (no measure binding)
    * where only a single point/text is displayed per row. Don't treat as single point
    * if size frame is assigned a measure or dimension since the size of the point
    * will vary like regular point charts.
    */
   private boolean isSinglePoint(EGraph egraph) {
      for(int i = 0; i < egraph.getElementCount(); i++) {
         GraphElement elem = egraph.getElement(i);

         // assume word cloud has multiple labels
         if(elem instanceof PointElement && ((PointElement) elem).isWordCloud()) {
            continue;
         }

         if("true".equals(egraph.getElement(i).getHint("_fake_")) &&
            elem.getSizeFrame() instanceof StaticSizeFrame)
         {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the chart is changed by script (element, data, etc).
    */
   public boolean isChangedByScript() {
      // fix customer bug1365805534414
      return isStructureChanged && !"true".equals(
         SreeEnv.getProperty("graph.script.action.support", "false"));
   }

   /**
    * Check if the chart is changed by script.
    */
   public boolean isChangedByScript0() {
      return cscript;
   }

   /**
    * Check if this is the same descriptor used for this VGraphPair.
    */
   public boolean isSameDescriptor(ChartDescriptor desc) {
      return desc.equalsContent(odesc);
   }

   public GraphCreator getGraphCreator() {
      return creator;
   }

   private static class Creator extends GraphCreator {
      public Creator(ChartVSAssemblyInfo ainfo, ViewsheetSandbox vbox, DataSet data,
                     DataSet adata, DataSet vdata, VariableTable vars, Dimension size)
      {
         this.ainfo = ainfo;
         this.vbox = vbox;
         this.data = data;
         this.adata = adata;
         this.vdata = vdata;
         this.vars = vars;
         this.size = size;
         oinfo = ainfo.getVSChartInfo().clone();
      }

      @Override
      public EGraph createGraph() {
         EGraph graph = null;

         if(data == null) {
            graphdata = new DefaultDataSet(new Object[][] {
                        {"Dim", "M"}, {"D", 0}});
            graph = new EGraph();
         }
         else {
            addScriptables();

            // @by ChrisSpagnoli bug1422843580389 2015-2-3
            // @by ChrisSpagnoli bug1422602265526 2015-2-5
            if(data instanceof AbstractDataSet && ainfo != null) {
               VSChartInfo cinfo = ainfo.getVSChartInfo();

               if(cinfo != null) {
                  if(cinfo.canProjectForward(DateComparisonUtil.isDateComparisonDefined(ainfo))) {
                     ChartDescriptor desc = ainfo.getRTChartDescriptor() == null ?
                        ainfo.getChartDescriptor() : ainfo.getRTChartDescriptor();

                     if(desc != null) {
                        PlotDescriptor plotdesc = desc.getPlotDescriptor();

                        if(plotdesc != null && plotdesc.getTrendline() != 0) {
                           ChartRef primaryDim = cinfo.getPrimaryDimension(true);
                           ((AbstractDataSet) data).setRowsProjectedForward(
                              plotdesc.getProjectTrendLineForward());
                           ((AbstractDataSet) data).setProjectColumn(
                              primaryDim != null ? primaryDim.getFullName() : null);
                        }
                        else {
                           ((AbstractDataSet) data).setRowsProjectedForward(0);
                        }
                     }
                  }
                  // @by ChrisSpagnoli bug1427783978948 2015-4-1
                  else {
                     ((AbstractDataSet) data).setRowsProjectedForward(0);
                  }
               }
            }

            Assembly boundTable = vbox.getViewsheet()
                    .getBaseWorksheet().getAssembly(ainfo.getTableName() + "_O");
            int sourceType = XSourceInfo.NONE;

            if(boundTable instanceof BoundTableAssembly) {
               sourceType = ((BoundTableAssembly) boundTable).getSourceInfo().getType();
            }

            GraphGenerator gen = GraphGenerator.getGenerator(ainfo, adata, data, vars, vdata,
                                                             sourceType, size);

            graph = gen.createEGraph();
            graphdata = gen.getData();
         }

         // if the EGraph is generated, we take a snapshot of the ChartInfo
         // so if the info is modified by script after graph is created, we
         // will re-generate the graph later
         if(!isScriptExecuted()) {
            oinfo = (VSChartInfo) ainfo.getVSChartInfo().clone();
         }

         legendSize = graph.getLegendPreferredSize();
         return graph;
      }

      @Override
      public DataSet getGraphDataSet() {
         if(graphdata == null) {
            getGraph();
         }

         return (graphdata != null) ? graphdata : data;
      }

      /**
       * Get the chart info used at the time of creating the EGraph.
       */
      public VSChartInfo getChartInfo() {
         return oinfo;
      }

      public DataSet getData() {
         return data;
      }

      public double getLegendSize() {
         return legendSize;
      }

      private void addScriptables() {
         if(vbox == null || vars == null || ainfo == null) {
            return;
         }

         vars.put("querySandbox", vbox.getConditionAssetQuerySandbox(
                     ainfo.getViewsheet()));
      }

      private ViewsheetSandbox vbox;
      private ChartVSAssemblyInfo ainfo;
      private VSChartInfo oinfo;
      private DataSet graphdata;
      private DataSet data;
      private DataSet adata;
      private DataSet vdata;
      private VariableTable vars;
      private double legendSize;
      private Dimension size;
   }

   public ChartInfo getChartInfo() {
      return cinfo;
   }

   private int leftPadding = 0;
   private int rightPadding = 0;
   private int topPadding = 0;
   private int bottomPadding = 0;
   private EGraph egraph; // element graph
   private VGraph vgraph; // real size vgraph
   private VGraph evgraph; // expanded size vgraph
   private int width, ewidth, height, eheight; // vgraph/evgraph sizes
   private Dimension size; // content size
   private DataSet data; // data set
   private boolean cancelled = false;
   private final AtomicBoolean completed = new AtomicBoolean(false);
   private boolean cscript = false;
   private boolean isStructureChanged = false;
   private ViewsheetSandbox vbox;
   private String vsrc;
   private ChartDescriptor odesc; // descriptor used for initGraph
   private Color brushHLColor = BrushingColor.getHighlightColor();
   private ChartInfo cinfo;
   private Plotter plotter1, plotter2;
   private GraphCreator creator;

   private static final Logger LOG = LoggerFactory.getLogger(VGraphPair.class);
}

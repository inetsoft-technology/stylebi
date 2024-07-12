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
package inetsoft.web.admin.monitoring;

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.scale.Scale;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticSizeFrameWrapper;
import inetsoft.util.Catalog;
import inetsoft.util.graphics.SVGSupport;

import java.awt.*;
import java.sql.Time;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;

/**
 * MonitorUtil contains utility methods.
 *
 * @author InetSoft Technology Corp
 * @version 11.3
 */
public class MonitorUtil {
   public static int compareUser(int cnt1, int cnt2) {
      if(cnt1 > cnt2) {
         return -1;
      }
      else if(cnt1 < cnt2) {
         return 1;
      }

      return 0;
   }

   public static String getDisplayUser(String user, String task) {
      if(task == null) {
         return user;
      }

      String taskLabel = Catalog.getCatalog().getString("Task");
      String taskDesc = taskLabel + ": " + task;

      return user == null ? taskDesc : user + "(" + taskDesc + ")";
   }

   /**
    * Create a monitor image.
    *
    * @param datum  the datum of the image to be created.
    * @param width  the width of the image.
    * @param height the height of the image.
    * @param type   the chart type.
    * @param format the format of the y-axis.
    * @param max    the maximum value of the y-axis.
    */
   public static Graphics2D createMonitorImage(Object[][] datum, int width, int height,
                                                  int type, String format, String title, long max)
   {
      if(datum == null || datum.length == 0) {
         return null;
      }

      boolean isDarkEM = CustomThemesManager.getManager().isEMDarkTheme();
      DataSet data = new DefaultDataSet(datum);

      // hide the titles
      TitleDescriptor xdesc = new TitleDescriptor();
      TitleDescriptor ydesc = new TitleDescriptor();
      ydesc.setTitleValue(title);
      ydesc.getTextFormat().setRotation(90);
      xdesc.setVisible(false);
      ChartDescriptor desc = new ChartDescriptor();
      desc.setPreferredSize(new Dimension(width, height));
      desc.getTitlesDescriptor().setYTitleDescriptor(ydesc);
      desc.getTitlesDescriptor().setXTitleDescriptor(xdesc);

      if(isDarkEM) {
         desc.getPlotDescriptor().setYGridColor(new Color(0x424242).darker());
      }

      LegendsDescriptor ldesc = desc.getLegendsDescriptor();
      ldesc.getColorLegendDescriptor().setVisible(false);
      ldesc.getShapeLegendDescriptor().setVisible(false);
      ldesc.getSizeLegendDescriptor().setVisible(false);

      ChartInfo info = new DefaultVSChartInfo();
      info.setSeparatedGraph(false);
      info.setMultiStyles(true);
      info.getAxisDescriptor().setMinimum(0);

      if(max > 0) {
         info.getAxisDescriptor().setMaximum(max);
      }

      // set the format of the y axis, the cpu image is % and the memory image
      // is M
      if("percent".equals(format)) {
         XFormatInfo xfinfo = new XFormatInfo();
         xfinfo.setFormat(XConstants.PERCENT_FORMAT);
         info.getAxisDescriptor().getAxisLabelTextFormat().setFormat(xfinfo);
      }
      else if("million".equals(format)) {
         XFormatInfo xfinfo = new XFormatInfo();
         xfinfo.setFormat(XConstants.DECIMAL_FORMAT);
         xfinfo.setFormatSpec("#,###M");
         info.getAxisDescriptor().getAxisLabelTextFormat().setFormat(xfinfo);
      }

      Integer rotate = 0;
      BaseField field1 = new BaseField((String) datum[0][0]);
      ChartDimensionRef dref = new VSChartDimensionRef(field1);
      dref.getAxisDescriptor().getAxisLabelTextFormat().setRotation(rotate);
      dref.setTimeSeries(true);
      info.addXField(dref);

      int sourceType = field1.getSourceType();

      // this is a re-arranged Soft palette to make the colors more stand out.
      Color[] soft = new Color[] {
         new Color(0x5a9bd4), new Color(0xf15a60), new Color(0x7ac36a), new Color(0x737373),
         new Color(0xfaa75b), new Color(0x9e67ab), new Color(0xce7058), new Color(0xd77fb4)};

      for(int i = 1; i < datum[0].length; i++) {
         BaseField field2 = new BaseField((String) datum[0][i]);
         VSChartAggregateRef aref = new VSChartAggregateRef();
         aref.setDataRef(field2);
         aref.setRTChartType(type);
         aref.setColorFrame(new StaticColorFrame(soft[(i - 1) % soft.length]));
         info.addYField(aref);
      }

      SizeFrameWrapper frame = info.getSizeFrameWrapper();

      if(frame instanceof StaticSizeFrameWrapper) {
         StaticSizeFrameWrapper wrapper = (StaticSizeFrameWrapper) frame;
         wrapper.setSize(1);
         wrapper.setChanged(true);
      }

      info.updateChartType(true);

      GraphGenerator gen = GraphGenerator.getGenerator(info, desc, null, null, data,
                                                       new VariableTable(), sourceType, null);
      EGraph egraph = gen.createEGraph();
      fixFormatSpec(egraph);

      // workaround to keep the points on the chart connected
      for(int i = 0; i < egraph.getElementCount(); i++) {
         GraphElement element = egraph.getElement(i);

         if(element instanceof LineElement) {
            ((LineElement) element).setIgnoreNull(true);
         }
      }

      data = gen.getData();
      VGraph vgraph = Plotter.getPlotter((EGraph) egraph.clone()).plotAndLayout(data, 0, 0, width, height);

      SVGSupport svgSupport = SVGSupport.getInstance();
      Graphics2D g = svgSupport.createSVGGraphics();
      svgSupport.setCanvasSize(g, new Dimension(width, height));

      if(isDarkEM) {
         g.setColor(new Color(0x424242));
      }
      else {
         g.setColor(Color.WHITE);
      }

      g.fillRect(0, 0, width, height);

      vgraph.paintGraph(g, true);
      g.dispose();

      return g;
   }

   /**
    * Merge the arrays.
    */
   public static Object[][] mergeGridData(Object[][] mData, Object[][] sData) {
      if(mData == null && sData == null) {
         return new Object[][]{};
      }

      // one of the two arrays is null
      if(!(mData != null && sData != null)) {
         return mData != null ? mData : sData;
      }

      if(sData.length > 1 && mData.length > 1) {
         int mDataPos = 1;
         int sDataPos = 1;
         int length = mData.length + sData.length - 1;
         int sublen = mData[0].length + sData[0].length - 1;
         List<Object[]> data = new ArrayList<>();

         data.add(mergeGridData0(mData[0], sData[0], sublen));

         for(int i = 0; i < length; i++) {
            if(!(mData[mDataPos][0] instanceof Time && sData[sDataPos][0] instanceof Time)) {
               break;
            }

            Time mTime = (Time)mData[mDataPos][0];
            Time sTime = (Time)sData[sDataPos][0];

            //Supplementary vacancies when merge data
            if(mTime.before(sTime)) {
               if(mDataPos < mData.length - 1) {
                  data.add(mergeGridData0(mData[mDataPos], null, sublen));
                  mDataPos ++;
               }
               else if(mDataPos >= mData.length - 1) {
                  data.add(mergeGridData0(null, sData[sDataPos], sublen));
                  sDataPos ++;
               }
            }
            else if(mTime.after(sTime)) {
               if(sDataPos < sData.length - 1) {
                  data.add(mergeGridData0(null, sData[sDataPos], sublen));
                  sDataPos ++;
               }
               else if(sDataPos >= sData.length - 1) {
                  data.add(mergeGridData0(mData[mDataPos], null, sublen));
                  mDataPos ++;
               }
            }
            else if(mTime.equals(sTime)) {
               data.add(mergeGridData0(mData[mDataPos], sData[sDataPos], sublen));

               if(mDataPos < mData.length - 1) {
                  mDataPos ++;
               }

               if(sDataPos < sData.length - 1) {
                  sDataPos ++;
               }
            }

            if(sDataPos >= sData.length - 1 && mDataPos >= mData.length - 1 &&
               data.size() >= mData.length && data.size() >= sData.length)
            {
               break;
            }
         }

         return data.toArray(new Object[0][]);
      }

      return mData;
   }

   private static Object[] mergeGridData0(Object[] mData, Object[] sData, int len) {
      Object[] data = new Object[len];

      if(mData != null && sData != null) {
         System.arraycopy(mData, 0, data, 0, mData.length);
         //avoid to add the header row twice.
         System.arraycopy(sData, 1, data, mData.length, sData.length - 1);
      }
      else if(mData != null && sData == null) {
         System.arraycopy(mData, 0, data, 0, mData.length);
      }
      else if(mData == null && sData != null) {
         data[0] = sData[0];
         System.arraycopy(sData, 1, data, len - (sData.length - 1), sData.length - 1);
      }

      return data;
   }

   private static void fixFormatSpec(EGraph egraph) {
      boolean isDarkEM = CustomThemesManager.getManager().isEMDarkTheme();
      Color fgColor = isDarkEM ? Color.lightGray : GDefaults.DEFAULT_TEXT_COLOR;
      RectCoord coord = (RectCoord) egraph.getCoordinate();
      Scale[] scales = coord.getScales();

      for(Scale scale : scales) {
         AxisSpec spec = scale.getAxisSpec();
         Format oldFormat = spec.getTextSpec().getFormat();
         spec.setTruncate(false);
         TextSpec tSpec = new TextSpec();
         tSpec.setColor(fgColor);
         spec.setTextSpec(tSpec);
         spec.getTextSpec().setFormat(oldFormat);

         if(isDarkEM) {
            spec.setLineColor(new Color(0x424242).darker());
         }
      }

      TextSpec yTitleTextSpec = new TextSpec();
      TextSpec y2TitleTextSpec = new TextSpec();
      yTitleTextSpec.setColor(fgColor);
      yTitleTextSpec.setRotation(90);
      y2TitleTextSpec.setColor(fgColor);
      y2TitleTextSpec.setRotation(90);
      egraph.getXTitleSpec().setTextSpec(new TextSpec());
      egraph.getX2TitleSpec().setTextSpec(new TextSpec());
      egraph.getYTitleSpec().setTextSpec(yTitleTextSpec);
      egraph.getY2TitleSpec().setTextSpec(y2TitleTextSpec);
   }
}

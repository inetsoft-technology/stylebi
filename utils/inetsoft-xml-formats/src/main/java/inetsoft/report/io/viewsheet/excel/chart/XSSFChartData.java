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
package inetsoft.report.io.viewsheet.excel.chart;

import inetsoft.report.StyleFont;
import inetsoft.report.io.excel.PoiExcelUtil;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xddf.usermodel.text.XDDFRunProperties;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.main.*;

import java.awt.*;
import java.util.Iterator;
import java.util.Set;

public abstract class XSSFChartData extends XDDFChartData {
   protected XSSFChartData(XDDFChart chart) {
      this(chart, -1);
   }

   protected XSSFChartData(XDDFChart chart, int style) {
      super(chart);
      this.chartStyle = style;
   }

   @Override
   public final AbstractXSSFChartSerie0 addSeries(
      XDDFDataSource<?> category, XDDFNumericalDataSource<? extends Number> values)
   {
      return null; // unused method
   }

   /**
    * @param category serie category data.
    * @param values   serie val data.
    * @param sinfo    serie info.
    */
   public abstract AbstractXSSFChartSerie0 addSeries(XDDFDataSource<?> category,
                                                     XDDFNumericalDataSource<? extends Number> values,
                                                     SerieInfo sinfo);

   /**
    * @param id       the serie id.
    * @param order    the serie order.
    * @param sinfo    the serie info.
    * @param category the categories datasource
    * @param values   the values datasource
    */
   public abstract AbstractXSSFChartSerie0 addSeries(int id, int order, SerieInfo sinfo,
                                                     XDDFDataSource<?> category,
                                                     XDDFNumericalDataSource<? extends Number> values);

   /**
    * Create and fill a chart with the added datas.
    *
    * @param category the category datasource
    * @param values   the values datasource
    */
   public abstract void fillChart(XDDFChartAxis category, XDDFValueAxis values);

   protected XSSFChart getChart() {
      return (XSSFChart) parent;
   }

   @Override
   protected final void removeCTSeries(int n) {
      // no-op, unused
   }

   @Override
   public final void setVaryColors(Boolean varyColors) {
      // no-op, unused
   }

   public abstract class AbstractXSSFChartSerie0 extends XDDFChartData.Series
   {
      protected AbstractXSSFChartSerie0(XDDFDataSource<?> category,
                                        XDDFNumericalDataSource<? extends Number> values)
      {
         super(category, values);
      }

      @Override
      public void setShowLeaderLines(boolean showLeaderLines) {
         // no-op
      }

      @Override
      public XDDFShapeProperties getShapeProperties() {
         return null; // no-op
      }

      @Override
      public void setShapeProperties(XDDFShapeProperties properties) {
         // no-op
      }

      @Override
      protected final void setIndex(long index) {
         // no-op
      }

      @Override
      protected final void setOrder(long order) {
         // no-op
      }

      /**
       * Get serie default shape properties. For stack charts.
       */
      protected CTShapeProperties getDefaultShapeProperties() {
         if(serieInfo == null || serieInfo.getDefaultDataPointInfo() == null) {
            return null;
         }

         DataPointInfo dinfo = serieInfo.getDefaultDataPointInfo();

         CTShapeProperties spPr = CTShapeProperties.Factory.newInstance();
         Color fillColor = dinfo.getFillColor();
         double alpha = serieInfo.getAlpha();

         if(dinfo.getPattFillPrst() != null) {
            spPr.setPattFill(getPattFill(fillColor, dinfo.getPattFillPrst()));
         }
         else if(serieInfo.isGlossyEffect() && !serieInfo.isSparkline()) {
            spPr.setGradFill(XSSFChartUtil.getGradFill(fillColor));
         }
         else if(fillColor != null) {
            spPr.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
         }
         else {
            spPr.addNewNoFill();
         }

         spPr.setLn(getLineProperties(dinfo));

         return spPr;
      }

      private CTPatternFillProperties getPattFill(Color color,
                                                  STPresetPatternVal.Enum prst)
      {
         CTPatternFillProperties pattFill =
            CTPatternFillProperties.Factory.newInstance();
         pattFill.setPrst(prst);
         pattFill.setFgClr(XSSFChartUtil.getCTColor(color));
         pattFill.setBgClr(XSSFChartUtil.getCTColor(Color.white));

         return pattFill;
      }

      /**
       * Get serie default marker. Just for line chart.
       */
      protected CTMarker getDefaultMarker() {
         if(serieInfo == null || serieInfo.getDefaultDataPointInfo() == null ||
            serieInfo.getDefaultDataPointInfo().getMarkerInfo() == null)
         {
            return null;
         }

         MarkerInfo minfo = serieInfo.getDefaultDataPointInfo().getMarkerInfo();
         CTMarker marker = CTMarker.Factory.newInstance();
         marker.addNewSymbol().setVal(minfo.getMarkerStyle());

         if(minfo.getSize() > 0) {
            marker.addNewSize().setVal((short) minfo.getSize());
         }

         CTShapeProperties mspPr = marker.addNewSpPr();
         Color fillColor = serieInfo.getDefaultDataPointInfo().getFillColor();
         double alpha = serieInfo.getAlpha();

         if(minfo.isFillShape() && fillColor != null){
            mspPr.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
         }
         else {
            mspPr.addNewNoFill();
         }

         CTLineProperties ln = mspPr.addNewLn();
         ln.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));

         return marker;
      }

      /**
       * Get data labels properties.
       */
      protected CTDLbls getDataLabelsProperties() {
         if(serieInfo == null || !serieInfo.isShowValue()) {
            return null;
         }

         CTDLbls dLbls = CTDLbls.Factory.newInstance();

         if(GraphTypes.isLine(serieInfo.getStyle()) ||
            GraphTypes.isPoint(serieInfo.getStyle()))
         {
            dLbls.addNewDLblPos().setVal(STDLblPos.T);
         }

         dLbls.addNewShowVal().setVal(true);
         dLbls.addNewShowLegendKey().setVal(false);
         dLbls.addNewShowCatName().setVal(false);
         dLbls.addNewShowSerName().setVal(false);
         dLbls.addNewShowPercent().setVal(false);
         dLbls.addNewShowBubbleSize().setVal(false);
         dLbls.addNewShowLeaderLines().setVal(false);

         Color fillColor = serieInfo.getDataLabelFillColor();

         if(fillColor != null) {
            CTShapeProperties spPr = dLbls.addNewSpPr();
            spPr.setSolidFill(XSSFChartUtil.getSolidFill(fillColor));
         }

         Font font = serieInfo.getDataLabelFont();

         if(font != null) {
            CTTextBody txPr = dLbls.addNewTxPr();
            CTTextBodyProperties bodyPr = txPr.addNewBodyPr();
            CTTextListStyle lstStyle = txPr.addNewLstStyle();
            CTTextParagraph p = txPr.addNewP();
            CTTextParagraphProperties pPr = p.addNewPPr();
            CTTextCharacterProperties defRPr = pPr.addNewDefRPr();
            final XDDFRunProperties xddfDefRPr = new XDDFRunProperties(pPr.addNewDefRPr());

            int style = font.getStyle();
            xddfDefRPr.setFontSize(XSSFChartUtil.getFontSize(font));
            defRPr.setI((style & StyleFont.ITALIC) != 0);
            defRPr.setB((style & StyleFont.BOLD) != 0);
            xddfDefRPr.setUnderline(XSSFChartUtil.getFontUnderlineType(font));
            xddfDefRPr.setCapitals(XSSFChartUtil.getFontCapsType(font));
            xddfDefRPr.setStrikeThrough(XSSFChartUtil.getFontStrikeType(font));

            CTTextFont cs = defRPr.addNewCs(); // ComplexScriptFont
            CTTextFont ea = defRPr.addNewEa(); // EastAisanFont
            CTTextFont latin = defRPr.addNewLatin(); // LatinFont
            cs.setTypeface(font.getName());
            ea.setTypeface(font.getName());
            latin.setTypeface(font.getName());

            if(serieInfo.getDataLabelColor() != null) {
               defRPr.setSolidFill(XSSFChartUtil.getSolidFill(
                  serieInfo.getDataLabelColor()));
            }
         }

         if(serieInfo.getDataLabelFormat() != null) {
            CTNumFmt numFmt = dLbls.addNewNumFmt();
            numFmt.setFormatCode(PoiExcelUtil.analyze(
               serieInfo.getDataLabelFormat()));
            //Fixed bug#24430 that excel v2010 can't apply numFmt.
            numFmt.setSourceLinked(false);
         }

         return dLbls;
      }

      /**
       * Get data points properties.
       */
      protected CTDPt[] getDataPointsProperties() {
         Set keyset = serieInfo.getDataPointMapKeySet();

         if(keyset == null || keyset.size() == 0) {
            return null;
         }

         CTDPt[] dPts = new CTDPt[keyset.size()];
         Iterator it = keyset.iterator();
         DataPointInfo dinfo = null;
         int i = 0;

         while(it.hasNext()) {
            int id = (Integer) it.next();
            dinfo = serieInfo.getDataPointInfo(id);

            CTDPt dPt = CTDPt.Factory.newInstance();
            CTUnsignedInt idx = dPt.addNewIdx();
            idx.setVal(id);

            CTShapeProperties spPr = dPt.addNewSpPr();
            Color fillColor = dinfo.getFillColor();
            double alpha = serieInfo.getAlpha();

            if(dinfo.getPattFillPrst() != null) {
               spPr.setPattFill(getPattFill(fillColor, dinfo.getPattFillPrst()));
            }
            else if(serieInfo.isGlossyEffect() && !serieInfo.isSparkline()) {
               spPr.setGradFill(XSSFChartUtil.getGradFill(fillColor));
            }
            else if(fillColor != null) {
               spPr.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
            }
            else {
               spPr.addNewNoFill();
            }

            spPr.setLn(getLineProperties(dinfo));

            if(dinfo.getMarkerInfo() != null) {
               MarkerInfo minfo = dinfo.getMarkerInfo();

               CTMarker marker = dPt.addNewMarker();
               marker.addNewSymbol().setVal(minfo.getMarkerStyle());

               if(minfo.getSize() > 0) {
                  marker.addNewSize().setVal((short) minfo.getSize());
               }

               CTShapeProperties mspPr = marker.addNewSpPr();

               if(minfo.isFillShape() && fillColor != null){
                  mspPr.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
               }
               else {
                  mspPr.addNewNoFill();
               }

               CTLineProperties ln = mspPr.addNewLn();
               ln.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
            }

            dPts[i++] = dPt;
         }

         return dPts;
      }

      private CTLineProperties getLineProperties(DataPointInfo dinfo) {
         CTLineProperties ln = CTLineProperties.Factory.newInstance();

         if(dinfo == null || dinfo.getLineInfo() == null) {
            CTNoFillProperties nofill = ln.addNewNoFill();
            return ln;
         }

         LineInfo lnInfo = dinfo.getLineInfo();
         ln.setW(lnInfo.getLineWidth());
         ln.setCmpd(lnInfo.getLineCompound());
         ln.addNewPrstDash().setVal(lnInfo.getLinePrstDash());
         Color fillColor = GraphTypes.isPie(serieInfo.getStyle()) &&
            !serieInfo.isExploded() ? Color.gray : dinfo.getFillColor();
         double alpha = serieInfo.getAlpha();

         if(fillColor != null) {
            ln.setSolidFill(XSSFChartUtil.getSolidFill(fillColor, alpha));
         }
         else {
            CTNoFillProperties nofill = ln.addNewNoFill();
         }

         return ln;
      }

      protected int id;
      protected int order;
      protected SerieInfo serieInfo;
   }

   protected final int chartStyle;
}

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

import inetsoft.report.StyleConstants;
import inetsoft.report.StyleFont;
import inetsoft.report.io.excel.PoiExcelUtil;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.text.*;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBoolean;
import org.openxmlformats.schemas.drawingml.x2006.chart.*;
import org.openxmlformats.schemas.drawingml.x2006.main.*;

import java.awt.*;
import java.text.Format;
import java.util.Hashtable;
/**
 * Package private class with utility methods.
 *
 * @author Roman Kashitsyn
 */
public class XSSFChartUtil {

   private XSSFChartUtil() {}

   /**
    * Builds CTAxDataSource object content from POI ChartDataSource.
    * @param ctAxDataSource OOXML data source to build.
    * @param dataSource POI data source to use.
    */
   public static void buildAxDataSource(CTAxDataSource ctAxDataSource,
                                        XDDFDataSource<?> dataSource)
   {
      buildAxDataSource(ctAxDataSource, dataSource, false);
   }

   /**
    * Builds CTAxDataSource object content from POI ChartDataSource.
    * @param ctAxDataSource OOXML data source to build.
    * @param dataSource POI data source to use.
    * @param forceNumber force to create numeric datasource.
    */
   public static void buildAxDataSource(CTAxDataSource ctAxDataSource,
                                        XDDFDataSource<?> dataSource,
                                        boolean forceNumber)
   {
      if(dataSource.isNumeric() || forceNumber) {
         if(dataSource.isReference()) {
            buildNumRef(ctAxDataSource.addNewNumRef(), dataSource);
         }
         else {
            buildNumLit(ctAxDataSource.addNewNumLit(), dataSource);
         }
      }
      else {
         if(dataSource.isReference()) {
            buildStrRef(ctAxDataSource.addNewStrRef(), dataSource);
         }
         else {
            buildStrLit(ctAxDataSource.addNewStrLit(), dataSource);
         }
      }
   }

   /**
    * Builds CTNumDataSource object content from POI ChartDataSource.
    * @param ctNumDataSource OOXML data source to build.
    * @param dataSource POI data source to use.
    */
   public static void buildNumDataSource(CTNumDataSource ctNumDataSource,
                                         XDDFNumericalDataSource<? extends Number> dataSource)
   {
      if(dataSource.isReference()) {
         buildNumRef(ctNumDataSource.addNewNumRef(), dataSource);
      }
      else {
         buildNumLit(ctNumDataSource.addNewNumLit(), dataSource);
      }
   }

   private static void buildNumRef(CTNumRef ctNumRef,
                                   XDDFDataSource<?> dataSource)
   {
      ctNumRef.setF(dataSource.getFormula());
      CTNumData cache = ctNumRef.addNewNumCache();
      fillNumCache(cache, dataSource);
   }

   private static void buildNumLit(CTNumData ctNumData,
                                   XDDFDataSource<?> dataSource)
   {
      fillNumCache(ctNumData, dataSource);
   }

   private static void buildStrRef(CTStrRef ctStrRef,
                                   XDDFDataSource<?> dataSource)
   {
      ctStrRef.setF(dataSource.getFormula());
      CTStrData cache = ctStrRef.addNewStrCache();
      fillStringCache(cache, dataSource);
   }

   private static void buildStrLit(CTStrData ctStrData,
                                   XDDFDataSource<?> dataSource)
   {
      fillStringCache(ctStrData, dataSource);
   }

   private static void fillStringCache(CTStrData cache,
                                       XDDFDataSource<?> dataSource)
   {
      int numOfPoints = dataSource.getPointCount();
      cache.addNewPtCount().setVal(numOfPoints);

      for(int i = 0; i < numOfPoints; ++i) {
         Object value = dataSource.getPointAt(i);

         if(value != null) {
            CTStrVal ctStrVal = cache.addNewPt();
            ctStrVal.setIdx(i);
            ctStrVal.setV(value.toString());
         }
      }
   }

   private static void fillNumCache(CTNumData cache,
                                    XDDFDataSource<?> dataSource)
   {
      int numOfPoints = dataSource.getPointCount();
      cache.addNewPtCount().setVal(numOfPoints);

      for(int i = 0; i < numOfPoints; ++i) {
         Object obj = dataSource.getPointAt(i);
         Number value = null;

         if(obj instanceof Number) {
            value = (Number) obj;
         }
         else {
            try {
               value = Double.valueOf(obj + "");
            }
            catch(Exception ex) {
               value = 0.0;
            }
         }

         if(value != null && !Double.isNaN((Double) value)) {
            CTNumVal ctNumVal = cache.addNewPt();
            ctNumVal.setIdx(i);
            ctNumVal.setV(value.toString());
         }
      }
   }

   /**
    * Get the rgb byte[].
    */
   public static byte[] getRGBArray(Color c) {
      if(c == null) {
         return new byte[0];
      }

      return (new XSSFColor(c, null)).getRGB();
   }

   /**
    * Create CTPresetLineDashProperties for target border style.
    */
   public static CTPresetLineDashProperties getBorderStyle(int style) {
      if(style == StyleConstants.NO_BORDER) {
         return null;
      }

      int idx = getLineIndex(style);
      CTPresetLineDashProperties prstDash =
         CTPresetLineDashProperties.Factory.newInstance();
      prstDash.setVal(lineStyles[idx]);

      return prstDash;
   }

   /**
    * Get preset line value in excel.
    */
   public static int getLineWidth(int style) {
      return lineWidth[getLineIndex(style)];
   }

   /**
    * Get preset line value in excel.
    */
   public static STPresetLineDashVal.Enum getPresetLineValue(int style) {
      return lineStyles[getLineIndex(style)];
   }

   /**
    * Get compound line in excel.
    */
   public static STCompoundLine.Enum getLineCompound(int style) {
      return lineCmpds[getLineIndex(style)];
   }

   /**
    * Get line index in the static array StyleLines.
    */
   private static int getLineIndex(int style) {
      int idx = -1;

      for(int i = 0; i < StyleLines.length; i++) {
         if(StyleLines[i] == style) {
            idx = i;
            break;
         }
      }

      // if use not set no border, and match failure, use thin line as default
      if(idx == -1 && style != StyleConstants.NO_BORDER) {
         idx = 2;
      }

      return idx;
   }

   /**
    * Get font size in excel.
    */
   public static double getFontSize(Font font) {
      return font.getSize() * getFontRate(font);
   }

   /**
    * Get the STTextCapsType of the target font.
    */
   public static CapsType getFontCapsType(Font font) {
      int style = font.getStyle();

      if((style & StyleFont.SMALLCAPS) != 0) {
         return CapsType.SMALL;
      }
      else if((style & StyleFont.ALLCAPS) != 0) {
         return CapsType.ALL;
      }

      return CapsType.NONE;
   }

   /**
    * Get the STTextStrikeType of the target font.
    */
   public static StrikeType getFontStrikeType(Font font) {
      int style = font.getStyle();

      if((style & StyleFont.STRIKETHROUGH) != 0) {
         return StrikeType.SINGLE_STRIKE;
      }

      return StrikeType.NO_STRIKE;
   }

   /**
    * Get the STTextUnderlineType of the target font.
    */
   public static UnderlineType getFontUnderlineType(Font font) {
      int style = font.getStyle();

      if((style & StyleFont.UNDERLINE) != 0) {
         return UnderlineType.SINGLE;
      }

      return UnderlineType.NONE;
   }

   /**
    * Get font rate between our product and excel.
    */
   private static double getFontRate(Font font) {
      Double rate = (Double) fontrates.get(font.getName().toLowerCase());

      if(rate != null) {
         return rate.doubleValue();
      }

      return OTHER_FONT_RATE;
   }

   /**
    * Set font text properties.
    */
   public static void addFontProperties(XDDFRunProperties rPr, Font font) {
      if(font == null) {
         return;
      }

      final int style = font.getStyle();
      rPr.setFontSize(XSSFChartUtil.getFontSize(font));
      rPr.setItalic((style & StyleFont.ITALIC) != 0);
      rPr.setBold((style & StyleFont.BOLD) != 0);
      rPr.setUnderline(XSSFChartUtil.getFontUnderlineType(font));
      rPr.setCapitals(XSSFChartUtil.getFontCapsType(font));
      rPr.setStrikeThrough(XSSFChartUtil.getFontStrikeType(font));

      final XDDFFont[] fonts = {
         new XDDFFont(FontGroup.COMPLEX_SCRIPT, font.getName(), null, null, null),
         new XDDFFont(FontGroup.EAST_ASIAN, font.getName(), null, null, null),
         new XDDFFont(FontGroup.LATIN, font.getName(), null, null, null),
      };

      rPr.setFonts(fonts);
   }

   /**
    * Create a CTSolidColorFillProperties with target color.
    */
   public static CTSolidColorFillProperties getSolidFill(Color color) {
      return getSolidFill(color, -1);
   }

   /**
    * Create a CTSolidColorFillProperties with target color.
    */
   public static CTSolidColorFillProperties getSolidFill(Color color,
                                                         double alpha)
   {
      CTSolidColorFillProperties solidFill =
         CTSolidColorFillProperties.Factory.newInstance();

      if(color == null) {
         return solidFill;
      }

      CTSRgbColor srgbClr = solidFill.addNewSrgbClr();
      srgbClr.setVal(getRGBArray(color));

      if(alpha == -1) {
         srgbClr.addNewAlpha().setVal(getAlpha(color));
      }
      else {
         srgbClr.addNewAlpha().setVal((int) (alpha * 100000));
      }

      return solidFill;
   }

   /**
    * Get CTColor in excel of the target color.
    */
   public static CTColor getCTColor(Color color) {
      CTColor clr = CTColor.Factory.newInstance();
      CTSRgbColor srgbClr = clr.addNewSrgbClr();

      color = color == null ? Color.black : color;
      srgbClr.setVal(getRGBArray(color));
      srgbClr.addNewAlpha().setVal(getAlpha(color));

      return clr;
   }

   /**
    * Get excel alpha value of the target color.
    */
   private static int getAlpha(Color color) {
      int alpha = 0;

      if(color.getAlpha() > 0) {
         alpha = (int) (((double) color.getAlpha() / (double) 255) * 100);
      }

      return alpha * 1000;
   }

   /**
    * Create varyColors, here we alawy set varycolors to true,
    * then we can set different color for different data point.
    */
   public static CTBoolean getVaryColor() {
      CTBoolean varyColors = CTBoolean.Factory.newInstance();
      varyColors.setVal(true);

      return varyColors;
   }

   public static CTHoleSize getHoleSize() {
      return getHoleSize((short) 50);
   }

   /**
    * Create holeSize, here we set what percent of the outer radius,
    * the inner radius will be, i.e. the radius of the hole for a donut chart
    */
   public static CTHoleSize getHoleSize(Short radiusPct) {
      CTHoleSize holeSize = CTHoleSize.Factory.newInstance();
      holeSize.setVal(radiusPct);

      return holeSize;
   }

   /**
    * Create corresponding chartdata.
    * @param style the chartstyle.
    * @param isXYScate if xyscate chart.
    */
   public static XSSFChartData createChartData(XSSFChart chart, int style, boolean isXYScate) {
      if(style == GraphTypes.CHART_BAR ||
         style == GraphTypes.CHART_BAR_STACK ||
         style == GraphTypes.CHART_3D_BAR ||
         style == GraphTypes.CHART_3D_BAR_STACK ||
         style == GraphTypes.CHART_WATERFALL ||
         style == GraphTypes.CHART_PARETO)
      {
         return new XSSFBarChartData(chart, style);
      }

      if(style == GraphTypes.CHART_LINE ||
         style == GraphTypes.CHART_LINE_STACK ||
         style == GraphTypes.CHART_STOCK ||
         style == GraphTypes.CHART_CANDLE ||
         (style == GraphTypes.CHART_POINT ||
         style == GraphTypes.CHART_POINT_STACK) && !isXYScate)
      {
         return new XSSFLineChartData(chart, style);
      }

      if(style == GraphTypes.CHART_AREA ||
         style == GraphTypes.CHART_AREA_STACK)
      {
         return new XSSFAreaChartData(chart, style);
      }

      if((style == GraphTypes.CHART_POINT ||
         style == GraphTypes.CHART_POINT_STACK) && isXYScate)
      {
         return new XSSFScatterChartData(chart, style);
      }

      if(style == GraphTypes.CHART_PIE ||
         style == GraphTypes.CHART_DONUT ||
         style == GraphTypes.CHART_3D_PIE)
      {
         return new XSSFPieChartData(chart, style);
      }

      if(style == GraphTypes.CHART_RADAR ||
         style == GraphTypes.CHART_FILL_RADAR)
      {
         return new XSSFRadarChartData(chart, style);
      }

      throw new IllegalArgumentException("Unhandled chart type: " + style);
   }

   /**
    * Get markerinfo.
    * @param shapeStyle the marker shape style.
    */
   public static STMarkerStyle.Enum getMarkerStyle(int shapeStyle) {
      STMarkerStyle.Enum mstyle = STMarkerStyle.NONE;

      switch(shapeStyle) {
      case StyleConstants.CIRCLE:
         mstyle = STMarkerStyle.CIRCLE;
         break;
      case StyleConstants.FILLED_CIRCLE:
         mstyle = STMarkerStyle.CIRCLE;
         break;
      case StyleConstants.TRIANGLE:
         mstyle = STMarkerStyle.TRIANGLE;
         break;
      case StyleConstants.FILLED_TRIANGLE:
         mstyle = STMarkerStyle.TRIANGLE;
         break;
      case StyleConstants.SQUARE:
         mstyle = STMarkerStyle.SQUARE;
         break;
      case StyleConstants.FILLED_SQUARE:
         mstyle = STMarkerStyle.SQUARE;
         break;
      case StyleConstants.CROSS:
         mstyle = STMarkerStyle.PLUS;
         break;
      case StyleConstants.STAR:
         mstyle = STMarkerStyle.STAR;
         break;
      case StyleConstants.DIAMOND:
         mstyle = STMarkerStyle.DIAMOND;
         break;
      case StyleConstants.FILLED_DIAMOND:
         mstyle = STMarkerStyle.DIAMOND;
         break;
      case StyleConstants.X:
         mstyle = STMarkerStyle.X;
         break;
      default:
         mstyle = STMarkerStyle.CIRCLE;
         break;
      }

      return mstyle;
   }

   /**
    * Add value range property.
    */
   public static void addValueRange(CTValAx valAx, double min, double max,
                                    double major, double minor,
                                    boolean logarithmicScale)
   {
      CTScaling scaling = valAx.addNewScaling();
      scaling.addNewOrientation().setVal(STOrientation.MIN_MAX);

      if(min != 0) {
         scaling.addNewMin().setVal(min);
      }

      if(max != 0) {
         scaling.addNewMax().setVal(max);
      }

      if(logarithmicScale) {
         scaling.addNewLogBase().setVal(10);
      }

      if(major != 0) {
         valAx.addNewMajorUnit().setVal(major);
      }

      if(minor != 0) {
         valAx.addNewMinorUnit().setVal(minor);
      }
   }

   /**
    * Init shape property.
    * @param sppr the CTShapeProperties.
    * @param hideAxisLine the whether axis line is hide.
    * @param color the line color.
    * @param style the line style.
    */
   public static void initShapeProperty(CTShapeProperties sppr,
      boolean hideAxisLine, Color color, int style, Color bg)
   {
      CTLineProperties in = getChartLine(color, style, hideAxisLine);
      sppr.setLn(in);

      if(bg != null) {
         CTSolidColorFillProperties solidFill = getSolidFill(bg);
         sppr.setSolidFill(solidFill);
      }

      sppr.addNewEffectLst();
   }

   /**
    * Init text property.
    * @param txpr the CTTextBody.
    * @param font the text font.
    * @param col the text color.
    * @param rot the text rotation.
    */
   public static void initTextProperty(CTTextBody txpr, Font font,
                                       Color col, double rot)
   {
      txpr.addNewBodyPr().setRot(getRotation(rot));
      CTTextParagraph p = txpr.addNewP();
      CTTextParagraphProperties ppr = p.addNewPPr();
      CTTextCharacterProperties defRPr = ppr.addNewDefRPr();
      addFontProperties(new XDDFRunProperties(defRPr), font);
      CTSolidColorFillProperties solidFill = getSolidFill(col);
      defRPr.setSolidFill(solidFill);
   }

   /**
    * Init grid lines property.
    * @param gridlines the chart grid lines.
    * @param color the grid line color.
    * @param style the grid line style.
    */
   public static void initGridlineProperty(CTChartLines gridlines, Color color,
                                           int style)
   {
      CTShapeProperties sppr = gridlines.addNewSpPr();
      initShapeProperty(sppr, false, color, style, null);
   }

   /**
    * Get Chart line.
    */
   public static CTLineProperties getChartLine(Color color, int style,
                                               boolean hideLine)
   {
      CTLineProperties in = CTLineProperties.Factory.newInstance();

      if(hideLine || style == StyleConstants.NONE) {
         in.addNewNoFill();
      }
      else {
         in.setSolidFill(getSolidFill(color));
         in.setPrstDash(getBorderStyle(style));
      }

      return in;
   }

   /**
    * Get axis label rotation.
    */
   public static int getRotation(double rotation) {
      if(rotation == 0) {
         return 0;
      }

      return (int) (-rotation * ROTATION_FACTER);
   }

   /**
    * Get CTNumFmt.
    * @param format the text format.
    */
   public static CTNumFmt getNumberFormat(Format format) {
      CTNumFmt nFormat = CTNumFmt.Factory.newInstance();
      nFormat.setFormatCode(PoiExcelUtil.analyze(format));
      nFormat.setSourceLinked(false);

      return nFormat;
   }

   /**
    * Property ct trend line.
    * @param ctTreandLine the CTTrendline.
    * @param trendline the CTTrendline type.
    * @param trendLineColor the CTTrendline color.
    * @param trendLineStyle the CTTrendline style.
    */
   public static void propertyTrendLine(CTTrendline ctTreandLine,
      int trendline, Color trendLineColor, int trendLineStyle)
   {
      CTShapeProperties sppr = ctTreandLine.addNewSpPr();
      initShapeProperty(sppr, false, trendLineColor, trendLineStyle, null);
      CTTrendlineType treadLineType = ctTreandLine.addNewTrendlineType();
      treadLineType.setVal(getTrendLineType(trendline));

      if(trendline == 2) {
         ctTreandLine.addNewOrder().setVal((short) 2);
      }

      if(trendline == 3) {
         ctTreandLine.addNewOrder().setVal((short) 3);
      }

      ctTreandLine.addNewDispRSqr().setVal(false);
      ctTreandLine.addNewDispEq().setVal(false);
   }

   /**
    * Get trand line type.
    */
   private static STTrendlineType.Enum getTrendLineType(int trendline) {
      STTrendlineType.Enum trendLinetype = STTrendlineType.LINEAR;

      switch(trendline) {
      case 1:
         trendLinetype = STTrendlineType.LINEAR;
         break;
      case 2:
      case 3:
         trendLinetype = STTrendlineType.POLY;
         break;
      case 4:
         trendLinetype = STTrendlineType.EXP;
         break;
      case 5:
         trendLinetype = STTrendlineType.LOG;
         break;
      case 6:
         trendLinetype = STTrendlineType.POWER ;
         break;
      }

      return trendLinetype;
   }

   /**
    * Get mapping the prst val of the target texture shape.
    */
   public static STPresetPatternVal.Enum getPrst(int textureID) {
      textureID = textureID > prstVals.length ? 0 : textureID;
      return prstVals[textureID];
   }

   /**
    * Get CTGradientFillProperties.
    * @param color the fill color.
    */
   public static CTGradientFillProperties getGradFill(Color color) {
      CTGradientFillProperties gradFill =
         CTGradientFillProperties.Factory.newInstance();
      CTGradientStopList gslist = gradFill.addNewGsLst();
      CTGradientStop gs = gslist.addNewGs();
      gs.setPos(0);
      gs.addNewSrgbClr().setVal(getRGBArray(color));
      CTGradientStop gs1 = gslist.addNewGs();
      gs1.setPos(14000);
      gs1.addNewSrgbClr().setVal(getRGBArray(new Color(0, 0, 0)));
      CTGradientStop gs2 = gslist.addNewGs();
      gs2.setPos(100000);
      gs2.addNewSrgbClr().setVal(getRGBArray(color));

      return gradFill;
   }

   private static int[] StyleLines = {
      StyleConstants.THIN_THIN_LINE, StyleConstants.ULTRA_THIN_LINE,
      StyleConstants.THIN_LINE, StyleConstants.MEDIUM_LINE,
      StyleConstants.THICK_LINE, StyleConstants.DOUBLE_LINE,
      StyleConstants.DOT_LINE, StyleConstants.DASH_LINE,
      StyleConstants.MEDIUM_DASH, StyleConstants.LARGE_DASH
   };

   private static STPresetLineDashVal.Enum[] lineStyles = {
      STPresetLineDashVal.SOLID, STPresetLineDashVal.SOLID,
      STPresetLineDashVal.SOLID, STPresetLineDashVal.SOLID,
      STPresetLineDashVal.SOLID, STPresetLineDashVal.SOLID,
      STPresetLineDashVal.SYS_DOT, STPresetLineDashVal.SYS_DASH,
      STPresetLineDashVal.DASH, STPresetLineDashVal.LG_DASH
   };

   private static STCompoundLine.Enum[] lineCmpds = {
      STCompoundLine.SNG, STCompoundLine.SNG,
      STCompoundLine.SNG, STCompoundLine.SNG,
      STCompoundLine.SNG, STCompoundLine.DBL,
      STCompoundLine.SNG, STCompoundLine.SNG,
      STCompoundLine.SNG, STCompoundLine.SNG
   };

   private static int[] lineWidth = {0, 0, 0, 1, 2, 2, 1, 1, 1, 1};

   private static Hashtable fontrates = new Hashtable(); {
      fontrates.put("dialog", DEFAULT_FONT_RATE);
      fontrates.put("comic sans ms", DEFAULT_FONT_RATE);
      fontrates.put("arial", ARIAL_FONT_RATE);
   }

   // the mapping prst val of the texture shapes.
   private static STPresetPatternVal.Enum[] prstVals = {
      STPresetPatternVal.LT_VERT, STPresetPatternVal.SM_CHECK,
      STPresetPatternVal.DASH_HORZ, STPresetPatternVal.DK_DN_DIAG,
      STPresetPatternVal.UP_DIAG, STPresetPatternVal.DIAG_CROSS,
      STPresetPatternVal.LT_UP_DIAG, STPresetPatternVal.DASH_DN_DIAG,
      STPresetPatternVal.DN_DIAG, STPresetPatternVal.NAR_VERT,
      STPresetPatternVal.DASH_VERT, STPresetPatternVal.NAR_HORZ,
      STPresetPatternVal.LT_DN_DIAG, STPresetPatternVal.PCT_90,
      STPresetPatternVal.DK_UP_DIAG, STPresetPatternVal.WD_UP_DIAG,
      STPresetPatternVal.OPEN_DMND, STPresetPatternVal.DK_DN_DIAG,
      STPresetPatternVal.LT_HORZ, STPresetPatternVal.DASH_UP_DIAG
   };

   public static final int DEFAULT_COLORIDX = 0x0000;
   public static Double DEFAULT_FONT_RATE = 0.88;
   private static Double OTHER_FONT_RATE = 0.85;
   private static Double ARIAL_FONT_RATE = 0.80;
   private static int ROTATION_FACTER = 60000;
}

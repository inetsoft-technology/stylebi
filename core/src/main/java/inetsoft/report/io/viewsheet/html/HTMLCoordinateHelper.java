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
package inetsoft.report.io.viewsheet.html;

import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Common;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.SpanMap;
import inetsoft.report.io.viewsheet.CoordinateHelper;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Coordinate helper used when exporting as PNG.
 *
 * @since 12.1
 */
public class HTMLCoordinateHelper extends CoordinateHelper {
   /**
    * Once the width and height of the PNG are known, set up the buffered image.
    * @param width  of the png
    * @param height of the png
    */
   protected void init(int width, int height) {
      SVGSupport.getInstance(); // make sure the PNG writer codec is registered
      bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = bufferedImage.createGraphics();
      graphics.setRenderingHint(Common.EXPORT_GRAPHICS, Common.VALUE_EXPORT_GRAPHICS_ON);
      setGraphics(graphics);
   }

   /**
    * Final preparation for viewsheet export. Called once per viewsheet.
    */
   public void processViewsheet(Writer writer) throws Exception {
      init(100, 100);

      VSAssemblyInfo vinfo = (VSAssemblyInfo)vs.getInfo();
      VSCompositeFormat fmt = vinfo.getFormat();
      fmt.getCSSFormat().setCSSType("Viewsheet");
      writer.write("<head><meta charset='UTF-8'></head>");
      writer.write("<body style='");
      writer.write(getCSSStyles(null, fmt));
      writer.write("'>");
   }

   /**
    * Gets the graphics context.
    * @return the graphics.
    */
   Graphics2D getGraphics() {
      return svgGraphics;
   }

   /**
    * Sets the graphics context.
    * @return the graphics.
    */
   void setGraphics(Graphics2D graphics) {
      svgGraphics = graphics;
   }

   /**
    * Writes the PNG image.
    */
   public void write(Writer writer) throws IOException {
      writer.write("</body>");
   }

   /**
    * draw the image.
    * @param image the specified Image.
    * @param bounds the specified Bounds.
    */
   public void drawImage(Image image, Rectangle2D bounds) {
      Shape shape = svgGraphics.getClip();
      svgGraphics.setClip(bounds);
      Common.drawImage(svgGraphics, image, (float) bounds.getX(),
                       (float) bounds.getY(), (float) bounds.getWidth(),
                       (float) bounds.getHeight(), null);
      svgGraphics.setClip(shape);
   }

   /**
    * write the image.
    */
   public void writeImage(BufferedImage img, Writer writer, String id, Rectangle2D bounds,
                          Hyperlink.Ref ref, VSCompositeFormat format, boolean writeBg,
                          String alpha, int zindex)
      throws Exception
   {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(img, "png", bos);
      byte[] data = bos.toByteArray();
      String str = Base64.getEncoder().encodeToString(data);
      StringBuffer buf = new StringBuffer();
      buf.append("<img id='" + id + "' style='");
      buf.append("z-index:");
      buf.append(zindex);
      buf.append(";");

      if(writeBg) {
         final Color background = format.getBackground();

         if(background != null) {
            buf.append("background:");
            buf.append(VSCSSUtil.getBackgroundRGBA(background));
            buf.append(";");
         }

         if(format.getRoundCorner() > 0) {
            buf.append("border-radius:");
            buf.append(format.getRoundCorner());
            buf.append("px;");
         }
      }

      if(bounds != null) {
         buf.append("position:absolute;left:");
         buf.append(bounds.getX());
         buf.append("px;top:");
         buf.append(bounds.getY());
         buf.append("px;width:");
         buf.append(bounds.getWidth());
         buf.append("px;height:");
         buf.append(bounds.getHeight());
         buf.append("px;");
      }

      if(alpha != null) {
         buf.append("opacity:");
         buf.append(Integer.parseInt(alpha) / 100.0);
      }

      if(ref != null && ref.getLinkType() == Hyperlink.WEB_LINK) {
         String link = ExcelVSUtil.getURL(ref);
         buf.append("text-decoration:underline' onclick='window.open(\"" + link + "\");");
      }

      buf.append("' src='data:image/png;base64," + str + "'/>");
      writer.write(buf.toString());
   }

   public String getImage(BufferedImage img, Point pos, String style) throws Exception {
      StringBuffer buffer = new StringBuffer();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(img, "png", bos);
      byte[] data = bos.toByteArray();
      String str = Base64.getEncoder().encodeToString(data);
      buffer.append("<img");

      if(pos != null) {
         buffer.append(" style='position:absolute;left:" + pos.x + ";top:" + pos.y + "'");
      }

      if(style != null) {
         buffer.append(" style='" + style + "'");
      }

      buffer.append(" src='data:image/png;base64," + str + "'/>");
      return buffer.toString();
   }

   public void writeText(Writer writer, Rectangle2D bounds, VSCompositeFormat format,
                         String dispText, Hyperlink.Ref ref, boolean autoSize, Insets padding,
                         boolean shadow, int zIndex)
   {
      StringBuffer buf = new StringBuffer();
      buf.append("<div style='position:absolute;overflow:hidden;z-index:");
      buf.append(zIndex);
      buf.append(";");

      if(bounds != null) {
         buf.append("left:");
         buf.append(bounds.getX());
         buf.append(";top:");
         buf.append(bounds.getY());
         buf.append(";width:");
         buf.append(bounds.getWidth());
         buf.append("px;");

         if(!autoSize) {
            buf.append("height:");
            buf.append(bounds.getHeight());
            buf.append("px;");
         }
      }

      buf.append("'>");
      buf.append("<div style='width:100%;height:100%;table-layout:");
      buf.append(autoSize ? "auto;" : "fixed;");
      buf.append(getCommonStyleString(format) + ";");
      buf.append(getBorderString(format, false));
      buf.append(";display:");
      buf.append(autoSize ? "block;" : "table;");
      buf.append(getPaddingString(padding));

      if(ref != null && ref.getLinkType() == Hyperlink.WEB_LINK) {
         String link = ExcelVSUtil.getURL(ref);
         buf.append("text-decoration:underline' onclick='window.open(\"" + link + "\");");
      }

      if(autoSize) {
         buf.append("word-break:break-all;");
      }

      buf.append("'>");
      buf.append("<div style='display:");
      buf.append(autoSize ? "block;" : "table-cell;");

      if(format.isWrapping()) {
         buf.append("word-wrap:break-word;");
      }

      //append alignment
      String vAlign = VSCSSUtil.getvAlign(format);
      String hAlign = VSCSSUtil.gethAlign(format);
      buf.append("vertical-align:");
      buf.append(autoSize ? "top" : vAlign);
      buf.append(";text-align:");
      buf.append(autoSize ? "left" : hAlign);

      if(shadow) {
         buf.append(";text-shadow:3px 3px 5px #777777;");
      }

      buf.append("'>");

      if(dispText != null) {
         buf.append(dispText.replaceAll("\n", "<br>"));
      }

      buf.append("</div></div></div>");

      try {
         writer.write(buf.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write text: " + dispText, e);
      }
   }

   /**
    * Return css style string for padding.
    */
   protected String getPaddingString(Insets padding) {
      if(padding == null) {
         return "";
      }

      StringBuffer buf = new StringBuffer();
      buf.append("padding-left:");
      buf.append(padding.left);
      buf.append("px;padding-right:");
      buf.append(padding.right);
      buf.append("px;padding-top:");
      buf.append(padding.right);
      buf.append("px;padding-bottom:");
      buf.append(padding.bottom);
      buf.append("px;");

      return buf.toString();
   }

   /**
    * Return css style string for layout.
    */
   protected String getLayoutString(Rectangle2D bounds, VSCompositeFormat format) {
      if(bounds == null || format == null) {
         return "";
      }

      StringBuffer buffer = new StringBuffer();
      buffer.append("position:absolute;left:");
      buffer.append(bounds.getX());
      buffer.append("px;top:");
      buffer.append(bounds.getY());
      buffer.append("px;width:");
      buffer.append(bounds.getWidth());
      buffer.append("px;height:");
      buffer.append(bounds.getHeight());
      buffer.append("px;");

      return buffer.toString();
   }

   /**
    * Return css style string for border.
    */
   protected String getBorderString(VSCompositeFormat format, boolean object) {
      String top = VSCSSUtil.getBorder(format, "top");
      String bottom = VSCSSUtil.getBorder(format, "bottom");
      String left = VSCSSUtil.getBorder(format, "left");
      String right = VSCSSUtil.getBorder(format, "right");

      StringBuffer buffer = new StringBuffer();
      buffer.append(";border-top:");
      buffer.append(top);
      buffer.append(";border-left:");
      buffer.append(left);
      buffer.append(";border-bottom:");
      buffer.append(bottom);
      buffer.append(";border-right:");
      buffer.append(right);
      buffer.append(";");

      if(!object) {
         buffer.append("box-sizing:border-box;");
      }

      if(format.getRoundCornerValue() != 0) {
         buffer.append(";border-radius:");
         buffer.append(format.getRoundCornerValue());
         buffer.append("px;");
      }

      return buffer.toString();
   }

   /**
    * Return css style string for alignment.
    */
   protected String getAlignmentString(VSCompositeFormat format) {
      String vAlign = VSCSSUtil.getvAlign(format);
      String hAlign = VSCSSUtil.gethAlign(format);

      StringBuffer buffer = new StringBuffer();
      buffer.append("display: flex;justify-content:");
      buffer.append(VSCSSUtil.getFlexAlignment(hAlign));
      buffer.append(";align-items:");
      buffer.append(VSCSSUtil.getFlexAlignment(vAlign));

      return buffer.toString();
   }

   /**
    * Return css style string for common format, like font, color, background etc.
    */
   protected String getCommonStyleString(VSCompositeFormat format) {
      StringBuffer styles = new StringBuffer("");
      styles.append("font:");
      styles.append(VSCSSUtil.getFont(format));
      styles.append(";color:");
      styles.append(VSCSSUtil.getForeground(format));
      styles.append(";background:");
      styles.append(VSCSSUtil.getBackgroundRGBA(format));
      String decoration = VSCSSUtil.getDecoration(format);

      if(decoration != null) {
         styles.append(";text-decoration: " + decoration);
      }

      return styles.toString();
   }

   public String getCSSStyles(Rectangle2D bounds, VSCompositeFormat format) {
      return getCSSStyles(bounds, format, false);
   }

   /**
    * Return the css style string.
    * @param  bounds the bounds of the target component.
    * @param  format the format of the target component.
    * @param  object true if the whole object format, else not.
    */
   public String getCSSStyles(Rectangle2D bounds, VSCompositeFormat format, boolean object) {
      StringBuffer styles = new StringBuffer("");
      styles.append(getLayoutString(bounds, format) + ";");
      styles.append(getCommonStyleString(format) + ";");
      styles.append(getBorderString(format, object) + ";");

      if(bounds == null) {
         // inner content align.
         styles.append(getAlignmentString(format));
      }

      return styles.toString();
   }

   public void writeTextInput(Writer writer, Rectangle2D bounds, TextInputVSAssembly assembly,
                              VSCompositeFormat format, Object value, int zIndex)
   {
      StringBuffer buf = new StringBuffer();

      buf.append("<textarea style='");
      buf.append(getCSSStyles(bounds, format));
      buf.append(";z-index:");
      buf.append(zIndex);
      buf.append("'>");

      if(value != null) {
         buf.append(value == null ? "" : getDataString(assembly.getDataType(), value));
      }

      buf.append("</textarea>");

      try {
         writer.write(buf.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write textinput: " + value, e);
      }
   }

   public void writeButton(Writer writer, Rectangle2D bounds, VSCompositeFormat fmt,
                           String value, int zIndex)
   {
      try {
         writer.write("<input type='button' value='" + value + "' style='");
         writer.write(getCSSStyles(bounds, fmt));
         writer.write(";z-index:");
         writer.write(String.valueOf(zIndex));
         writer.write("'/>");
      }
      catch(Exception e) {
         LOG.error("Failed to write button: " + value, e);
      }
   }

   public void writeSpinner(Writer writer, Rectangle2D bounds, VSCompositeFormat fmt, Object value,
                            int zIndex, Dimension size)
   {
      try {
         writer.write("<input type='number' value='");
         writer.write(Tool.toString(value));
         writer.write("' style='");
         writer.write(getCSSStyles(bounds, fmt));
         writer.write(";z-index:");
         writer.write(String.valueOf(zIndex));
         writer.write(";text-align:");
         writer.write(VSCSSUtil.gethAlign(fmt));
         writer.write(";padding:");
         writer.write(getSpinnerInnerTextPadding(fmt, size));
         writer.write("'/>");
      }
      catch(Exception e) {
         LOG.error("Failed to write spinner: " + value, e);
      }
   }

   public void writeComboBox(Writer writer, ComboBoxVSAssemblyInfo info) {
      VSCompositeFormat fmt = info.getFormat();
      Rectangle2D bounds = getBounds(info);
      Object[] values = info.getValues();
      Object[] labels = info.getLabels();
      Object value = info.getSelectedObject();
      StringBuffer options = new StringBuffer("");

      if(info.isCalendar()) {
         String dataType = info.getDataType();
         dataType = "timeInstance".equals(dataType) ? "dateTime" : dataType;
         options.append("<input type='" + dataType + "' style='");
         options.append(getCSSStyles(bounds, fmt));
         options.append(";z-index:");
         options.append(info.getZIndex());
         options.append("' value='");
         options.append((values[0] == null ? "" : getDataString(dataType, values[0])) + "'>");
      }
      else {
         options.append("<select style='");
         options.append(getCSSStyles(bounds, fmt));
         options.append(";z-index:");
         options.append(info.getZIndex());
         options.append("'>");
         boolean found = false;

         for(int i = 0; i < values.length; i++) {
            Object valueStr = values[i] == null ? "" : values[i];
            options.append("<option value='" + valueStr + "' ");

            if(Tool.equals(values[i], value)) {
               options.append("selected='selected'");
               found = true;
            }

            Object label = labels[i] == null ? "" : labels[i];
            options.append(">" + labels[i] + "</option>");
         }

         if(!found) {
            Object valueStr = value == null ? "" : value;
            options.append(
               "<option value='" + valueStr + "' selected='selected'>" + valueStr + "</option>");
         }

         options.append("</select>");
      }

      try {
         writer.write(options.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write combox: " + value, e);
      }
   }

   private Object getDataString(String dataType, Object value) {
      if(XSchema.isDateType(dataType)) {
         return Tool.getDataString(value, dataType);
      }

      return value;
   }

   public void writeList(Writer writer, ListInputVSAssemblyInfo info, boolean isRadio) {
      VSCompositeFormat fmt = info.getFormat();
      Rectangle2D bounds = getBounds(info);
      boolean isNullBorder = isNullBorder(info);
      int titleH = getTitleH(info);
      StringBuffer buf = new StringBuffer("");

      if(isNullBorder) {
         buf.append("<div style='");
         buf.append(getLayoutString(bounds, fmt));
         buf.append(";border:1px solid #e0e0e0;z-index:1;'></div>");
      }

      buf.append("<div style='");
      buf.append(getCSSStyles(bounds, fmt));
      buf.append(";z-index:");
      buf.append(info.getZIndex());
      buf.append("'>");

      if(info instanceof TitledVSAssemblyInfo && ((TitledVSAssemblyInfo) info).isTitleVisible()) {
         buf.append(getTitle0(info, isNullBorder));
      }

      buf.append("<div style='position:absolute;width:100%;height:");
      buf.append((bounds.getHeight() - titleH));
      buf.append(";overflow:hidden;'>");
      appendCells(buf, info, isRadio);
      buf.append("</div></div>");

      try {
         writer.write(buf.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write radiobutton: ", e);
      }
   }

   private boolean isNullBorder(VSAssemblyInfo info) {
      VSCompositeFormat fmt = info.getFormat();
      Insets insets = fmt.getBorders();
      boolean noWholeBorder = insets == null;

      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat titleFormat = info.getFormatInfo().getFormat(titlepath, false);
      Insets tinsert = titleFormat.getBorders();
      boolean noTitleBorder = tinsert == null ||
              tinsert.top == 0 && tinsert.left == 0 && tinsert.right == 0 && tinsert.bottom == 0;

      if(noWholeBorder && noTitleBorder) {
         return true;
      }

      return false;
   }

   private void appendCells(StringBuffer buf, ListInputVSAssemblyInfo info, boolean isRadio) {
      Object[] values = info.getValues();
      String[] labels = info.getLabels();
      VSCompositeFormat[] formats = info.getFormats();
      Object[] value = info.getSelectedObjects();
      int rowCount = getRowCount(info);
      int colCount = getColCount(info, rowCount);
      VSCompositeFormat fmt = getDetailFormat(info);

      Dimension cellSize = getCellSize(info);
      int count = 0;
      buf.append("<table style='border-collapse:collapse'>");

      for(int i = 0; i < rowCount && count < values.length; i++) {
         buf.append("<tr>");

            for(int j = 0; j < colCount && count < values.length; j++) {
               VSCompositeFormat cellFormat = fmt;

               if(formats != null && count < formats.length) {
                  cellFormat = formats[count];
               }

               String cellStyle = getCSSStyles(null, cellFormat);

               if("center".equals(VSCSSUtil.gethAlign(fmt)) && colCount > 0) {
                  cellStyle += ";justify-content:flex-start;";
                  int paddingLeft =
                     ((int) Math.max(0, cellSize.width - getMaxLabelWidth(labels, fmt) - 13)) / 2;
                  cellStyle += ";padding-left:" + paddingLeft + "px";
               }

               buf.append("<td>");
               buf.append(" <div style='width:" + cellSize.width + "px;height:" + cellSize.height
                  + "px;" + cellStyle + "'>");
               buf.append("  <label style='display:flex;align-items:center;white-space:nowrap;overflow:hidden;'>");
               buf.append("   <input type=" + (isRadio ? "'radio'" : "'checkbox'"));

               if(isSelected(value, values[count])) {
                  buf.append(" checked");
               }

               buf.append(">" + labels[count]);
               buf.append("  </label>");
               buf.append(" </div>");
               buf.append("</td>");

               count++;
            }

         buf.append("</tr>");
      }

      buf.append("</table>");
   }

   private int getDataTopCSS(int index, Dimension size, int dataColCount) {
      int dataHeight = size.height;
      int row = (int)Math.floor(index / dataColCount);

      return dataHeight * row;
   }

   private int getDataLeftCSS(int index, Dimension size, int dataColCount) {
      int dataWidth = size.width;
      int position = (index + 1) % dataColCount;

      if(position == 0) {
         return dataWidth * (dataColCount - 1);
      }
      else {
         return dataWidth * (position - 1);
      }
   }

   private boolean isSelected(Object[] svalue, Object value) {
      if(value == null) {
         return svalue == null || svalue.length == 0;
      }

      for(int i = 0; i < svalue.length; i++) {
         if(value.equals(svalue[i]) || Objects.equals(Tool.toString(value), svalue[i])) {
            return true;
         }
      }

      return false;
   }

   private int getTitleH(VSAssemblyInfo info) {
      if(!(info instanceof TitledVSAssemblyInfo)) {
         return 0;
      }

      TitledVSAssemblyInfo tinfo = (TitledVSAssemblyInfo) info;

      return !tinfo.isTitleVisible() ? 0 : tinfo.getTitleHeight();
   }

   private VSCompositeFormat getDetailFormat(VSAssemblyInfo info) {
      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat detailFormat = fmtInfo.getFormat(datapath, false);

      return detailFormat;
   }

   private Dimension getCellSize(ListInputVSAssemblyInfo cinfo) {
      Dimension containerSize = vs.getPixelSize(cinfo);
      // subtract 2 pixels added by <td>
      int cellHeight = cinfo.getCellHeight() - 2;
      int rowCount = getRowCount(cinfo);
      int dataColCount = getColCount(cinfo, rowCount);

      return new Dimension(dataColCount == 0 ? 0 : containerSize.width / dataColCount,
                           cellHeight);
   }

   /**
    * Return the avaiable row count according to the assembly content height.
    */
   private int getRowCount(ListInputVSAssemblyInfo cinfo) {
      Dimension containerSize = vs.getPixelSize(cinfo);
      int contentHeight = containerSize.height;

      if(cinfo instanceof TitledVSAssemblyInfo) {
         int titleHeight = ((TitledVSAssemblyInfo)cinfo).getTitleHeight();
         contentHeight = containerSize.height - titleHeight;
      }

      int valueLength = cinfo.getValues().length;
      int cellHeight = cinfo.getCellHeight();
      int dataRowCount = (int) Math.floor(contentHeight / cellHeight);
      return contentHeight != 0 ? Math.max(1, dataRowCount) : dataRowCount;
   }

   /**
    * Return the avaiable column count for the list assembly.
    * @param  cinfo    the assembly info.
    * @param  rowCount the avaiable row count for the list assembly.
    */
   private int getColCount(ListInputVSAssemblyInfo cinfo, int rowCount) {
      int valueLength = cinfo.getValues().length;
      int cellHeight = cinfo.getCellHeight();
      int dataColCount = rowCount >= valueLength ? 1 :
         (int) Math.ceil((double) valueLength / rowCount);

      return dataColCount;
   }

   /**
    * Return the max label width according to the font.
    */
   private float getMaxLabelWidth(String[] labels, VSCompositeFormat fmt) {
      if(labels == null || labels.length == 0) {
         return 0;
      }

      Font font = fmt.getFont();
      float maxWidth = 0;

      for(String label : labels) {
         float lw = Common.stringWidth(label, font);
         maxWidth = Math.max(maxWidth, lw);
      }

      return maxWidth;
   }

   public void writeCurrentSelection(Writer writer, CurrentSelectionVSAssemblyInfo info, Viewsheet vs) {
      Rectangle2D bounds = getBounds(info);
      VSCompositeFormat format = info.getFormat();

      try {
         writer.write("<div style='");
         writer.write(getCSSStyles(bounds, format));
         writer.write(";z-index:");
         writer.write(String.valueOf(info.getZIndex()));
         writer.write("'>");

         if(info.isTitleVisible()) {
            writer.write(getTitle(info));
         }

         writer.write("</div>");
         StringBuffer slist = new StringBuffer();
         writeSelections(slist, info, vs);
         writer.write(slist.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write selection container: " + info.getName(), e);
      }
   }

   /**
    * Write the current selections.
    */
   private void writeSelections(StringBuffer slist, CurrentSelectionVSAssemblyInfo info, Viewsheet exportVS) {
      Point position = info.getViewsheet().getPixelPosition(info);
      Dimension size = info.getPixelSize();
      Rectangle2D cbounds = createBounds(position, size);
      size = new Dimension(size.width, AssetUtil.defh);
      Point startPos = info.getViewsheet().getPixelPosition(info);
      int titleH = info.isTitleVisible() ? info.getTitleHeight() : 0;

      if(info.isTitleVisible()) {
         startPos = new Point(startPos.x, startPos.y + info.getTitleHeight());
      }

      position = new Point(position.x, position.y + titleH);

      if(info.isShowCurrentSelection()) {
         String[] titles = info.getOutSelectionTitles();
         String[] values = info.getOutSelectionValues();

         Rectangle2D tbounds = createBounds(startPos, size);
         double currentY = tbounds.getY();
         double theight = tbounds.getHeight();
         VSCompositeFormat format = info.getFormat() == null ?
            new VSCompositeFormat() : (VSCompositeFormat) info.getFormat().clone();

         for(int i = 0; i < titles.length; i++) {
            Rectangle2D bounds = new Rectangle2D.Double(cbounds.getX(), currentY,
               cbounds.getWidth(), theight);

            if(bounds.getY() + bounds.getHeight() / 2 >
               cbounds.getY() + cbounds.getHeight())
            {
               break;
            }

            double maxH = cbounds.getHeight() + cbounds.getY() - bounds.getY();
            maxH = Math.min(maxH, bounds.getHeight());
            bounds.setFrame(bounds.getX(), bounds.getY(), bounds.getWidth(), maxH);
            String title = titles[i];
            String value = values[i] == null ? "none" : values[i];
            writeOutTitle(slist, title, value, bounds, format, info.getTitleRatio(), info.getZIndex());
            currentY += theight;
         }
      }

      adjustChildAssemblyPosition(position, info, exportVS);
   }

   private void adjustChildAssemblyPosition(Point position, CurrentSelectionVSAssemblyInfo info, Viewsheet exportVS) {
      Viewsheet vs = info.getViewsheet();
      String[] values = info.getOutSelectionValues();
      int y = position.y + (info.isShowCurrentSelection() ? values.length * AssetUtil.defh : 0);

      for(String child: info.getAssemblies()) {
         Assembly cobj = vs.getAssembly(child);

         if(cobj != null) {
            Point pos = cobj.getPixelOffset();
            Dimension csize = cobj.getPixelSize();
            Object cinfo = cobj.getInfo();
            int ypos = y;

            if(info.isEmbedded() &&
               info.getViewsheet().getInfo() instanceof ViewsheetVSAssemblyInfo)
            {
               ViewsheetVSAssemblyInfo vsInfo =
                  (ViewsheetVSAssemblyInfo) info.getViewsheet().getInfo();
               Point vsPos = exportVS.getPixelPosition(vsInfo);
               Rectangle vsBounds = vsInfo.getAssemblyBounds();

               if(vsBounds != null) {
                  ypos += vsBounds.y;
               }

               if(vsPos != null) {
                  ypos -= vsPos.y;
               }
            }

            cobj.setPixelOffset(new Point(pos.x, ypos));

            if(cinfo instanceof SelectionListVSAssemblyInfo &&
               ((SelectionListVSAssemblyInfo)cinfo).getShowType() ==
                  SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
            {
               y += info.getTitleHeight();
            }
            else {
               y += csize.height;
            }
         }
      }
   }

   /**
    * Write out selection title.
    */
   private void writeOutTitle(StringBuffer slist, String title, String value, Rectangle2D bounds,
                              VSCompositeFormat format, double titleRatio, int zIndex)
   {
      String vAlign = VSCSSUtil.getvAlign(format);
      String hAlign = VSCSSUtil.gethAlign(format);

      if(Double.isNaN(titleRatio)) {
         titleRatio = 0.5;
      }

      String ratio1 = (int) (titleRatio * 100) + "%";
      String ratio2 = (int) ((1 - titleRatio) * 100) + "%";

      slist.append("<div style='");
      slist.append(getCSSStyles(bounds, format));
      slist.append(";z-index:");
      slist.append(zIndex);
      slist.append("'>");
      slist.append(" <div style='width:100%;height:100%;display:flex;justify-content:");
      slist.append(VSCSSUtil.getFlexAlignment(hAlign));
      slist.append(";align-items:");
      slist.append(VSCSSUtil.getFlexAlignment(vAlign));
      slist.append("'>");
      slist.append("  <div style='display:inline-block;overflow:hidden;width:" + ratio1 +
                      ";white-space:nowrap'>");
      slist.append(title);
      slist.append("  </div>");
      slist.append("  <div style='display:inline-block;overflow:hidden;width:" + ratio2 +
         ";white-space:nowrap'>");
      slist.append(value);
      slist.append("  </div>");
      slist.append(" </div>");
      slist.append("</div>");
   }

   public String getTitle(VSAssemblyInfo info) {
      return getTitle0(info, false);
   }

   private String getTitle0(VSAssemblyInfo info, boolean hasPadding) {
      if(!(info instanceof TitledVSAssemblyInfo)) {
         return "";
      }

      TitledVSAssemblyInfo tinfo = (TitledVSAssemblyInfo)info;
      StringBuffer titleStr = new StringBuffer();
      int titleH = !tinfo.isTitleVisible() ? 0 : tinfo.getTitleHeight();

      if(titleH == 0) {
         return "";
      }

      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat titleFormat = fmtInfo.getFormat(titlepath, false);
      String title = tinfo.getTitle();
      boolean floating = isFloating(titleFormat, info.getFormat());
      boolean wrapping = titleFormat.isWrapping();
      Insets padding = tinfo.getTitlePadding();

      titleStr.append(
         "<div style='overflow:hidden;box-sizing: border-box;width:100%;height:" + titleH + "px;");
      titleStr.append(getCSSStyles(null, titleFormat));
      titleStr.append(";text-align:");
      titleStr.append(VSCSSUtil.gethAlign(titleFormat));

      //If title and assembly do not have borders, using default borders.
      if(hasPadding && padding == null) {
         titleStr.append(";margin-top:-6px;");
         titleStr.append("'>");
         titleStr.append("<span style='margin-left:5px;z-index:2;padding-left:2px;padding-right:2px;");
         titleStr.append("width:");
         titleStr.append(floating && !wrapping ? "auto;" : "100%;");

         if(titleFormat.isBackgroundDefined() && titleFormat.getBackground() != null) {
            titleStr.append("background:");
            titleStr.append(VSCSSUtil.getBackgroundRGBA(titleFormat));
            titleStr.append(";");
         }
         else {
            titleStr.append("background:white;");
         }

         if(titleFormat.isWrapping()) {
            titleStr.append("word-wrap:break-word;");
         }

         titleStr.append("'>");

         if(title != null) {
            titleStr.append(title);
         }

         titleStr.append("</span></div>");
      }
      else {
         titleStr.append("'>");
         titleStr.append("<span style='width:");
         titleStr.append(floating && !wrapping ? "auto;" : "100%;");

         if(titleFormat.isWrapping()) {
            titleStr.append("word-wrap:break-word;");
         }

         if(padding != null) {
            titleStr.append(getPaddingString(padding));
         }

         titleStr.append("'>");

         if(title != null) {
            titleStr.append(title);
         }

         titleStr.append("</span></div>");
      }

      return titleStr.toString();
   }

   private boolean isFloating(VSCompositeFormat titleFormat, VSCompositeFormat objectFormat) {
      return (titleFormat.getBorders() == null || titleFormat.getBorders().bottom == 0 &&
         titleFormat.getBorders().top == 0 && titleFormat.getBorders().right == 0 &&
         titleFormat.getBorders().left == 0) && (objectFormat.getBorders() == null ||
         objectFormat.getBorders().bottom == 0 && objectFormat.getBorders().top == 0 &&
         objectFormat.getBorders().right == 0 && objectFormat.getBorders().left == 0);
   }

   public void writeTimeSlider(Writer writer, TimeSliderVSAssembly assembly,
                               BufferedImage img, int zIndex)
   {
      TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo)assembly.getInfo();
      VSCompositeFormat format = info.getFormat();
      Rectangle2D abounds = getBounds(assembly, CoordinateHelper.ALL);
      Rectangle2D titleBounds = getBounds(assembly, CoordinateHelper.TITLE);
      int titleH = !info.isTitleVisible() ? 0 : info.getTitleHeight();

      try {
         StringBuffer slist = new StringBuffer();
         slist.append("<div style='");
         slist.append(getCSSStyles(abounds, format));
         slist.append(";z-index:");
         slist.append(zIndex);
         slist.append(";overflow:auto'>");

         if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
            CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo)
               assembly.getContainer().getInfo();
            String containerTitle = assembly.getDisplayValue(true, true);
            appendContainerTitle(slist, info, titleH, cinfo.getTitleRatio(), containerTitle);
         }
         else {
            slist.append(getTitle(info));
         }

         Rectangle2D bounds0 = getBounds(assembly, CoordinateHelper.DATA);

         double h = 0;

         if(!info.isTitleVisible()) {
            h = titleBounds.getHeight();
         }

         Rectangle2D bounds = getBounds(assembly, CoordinateHelper.DATA);
         bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY() - h, bounds.getWidth(),
            (getAssemblySize(assembly, null).height - info.getTitleHeight()) * getScale());

         if(!info.isHidden()) {
            drawImage(img, bounds);
            slist.append(getImage(img, null, "width:100%;height:" + bounds.getHeight() + "px;"));
         }

         slist.append("</div>");
         writer.write(slist.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write selection container: " + info.getName(), e);
      }
   }

   public void appendContainerTitle(StringBuffer slist, SelectionVSAssemblyInfo info, int titleH,
      double titleRatio, String containerTitle)
   {
      if(titleH == 0) {
         return;
      }

      TitledVSAssemblyInfo tinfo = (TitledVSAssemblyInfo) info;
      FormatInfo fmtInfo = info.getFormatInfo();
      TableDataPath titlepath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat titleFormat = fmtInfo.getFormat(titlepath, false);
      String title = tinfo.getTitle();
      double labelW = titleRatio * 100;
      labelW = Double.isNaN(labelW) ? 50 : labelW;
      Insets padding = tinfo.getTitlePadding();

      slist.append("<div style='width:100%;height:" + titleH + "px;");
      slist.append(getCSSStyles(null, titleFormat));
      slist.append("'>");
      slist.append("<div style='display:inline-block;overflow:hidden;white-space:nowrap;");

      if(containerTitle != null) {
         slist.append("width:" + labelW + "%");
      }

      slist.append("'>");

      if(title != null) {
         slist.append("<span style='display:block;");

         if(padding != null) {
            slist.append(getPaddingString(padding));
         }

         slist.append("'>");
         slist.append(title);
         slist.append("</span>");
      }

      slist.append("</div>");

      if(containerTitle != null) {
         slist.append("<div style='display:inline-block;overflow:hidden;width:" + (100 - labelW) +
                         "%;white-space:nowrap'>");
         slist.append("<span style='display:block;");

         if(padding != null) {
            slist.append(getPaddingString(padding));
         }

         slist.append("'>");
         slist.append(containerTitle);
         slist.append("</span>");
         slist.append("</div>");
      }

      slist.append("</div>");
   }

   public Object getCellValue(Object value) {
      if(value instanceof Double) {
         return ((Double) value).doubleValue();
      }

      if(value instanceof Float) {
         return ((Float) value).floatValue();
      }

      if(value instanceof Integer) {
         return ((Integer) value).intValue();
      }

      if(value instanceof Long) {
         return ((Long) value).longValue();
      }

      if(value instanceof Date) {
         long time = ((Date) value).getTime();
         Calendar calendar = CoreTool.calendar.get();
         calendar.set(1900, 0, 1);

         // excel doesn't support the date time earlier than 1900-01-1
         if(time > calendar.getTimeInMillis()) {
            return new Date(time);
         }
      }

      if(value instanceof Boolean) {
         return ((Boolean) value).booleanValue();
      }

      if(value instanceof Number) {
         return ((Number) value).toString();
      }

      return value;
   }

   public String getTableCellURL(VSTableLens lens, int r, int c) {
      ColumnIndexMap columnIndexMap = columnIndexMaps.computeIfAbsent(
         lens.hashCode(), k -> ColumnIndexMap.createColumnIndexMap(lens));

      Hyperlink.Ref ref = ExportUtil.getTableCellHyperLink(lens, r, c, columnIndexMap);

      if(ref == null) {
         return null;
      }

      if(ref.getLinkType() != Hyperlink.WEB_LINK) {
         return null;
      }

      return ExcelVSUtil.getURL(ref);
   }

   public Object getTableCellObject(VSTableLens lens, int row, int col, String fmt) {
      SpanMap spanMap = lens.getSpanMap(0, row + 1);
      Rectangle span = spanMap.get(row, col);

      // beginning of span cell may be hidden, should find the top-left of the span cell. (62502)
      if(span != null) {
         col += span.x;
      }

      return ExportUtil.getObject(lens, row, col, fmt, false);
   }

   public String getCellFormat(VSTableLens lens, int row, int col) {
      return ExportUtil.getCellFormat(lens, row, col, true);
   }

   public BufferedImage getPresenter(PresenterPainter painter, VSCompositeFormat fmt, int w,
      int h)
   {
      Rectangle2D bounds = new Rectangle2D.Float(0, 0, w, h);
      BufferedImage bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = bufferedImage.createGraphics();
      g.setRenderingHint(Common.EXPORT_GRAPHICS, Common.VALUE_EXPORT_GRAPHICS_ON);
      Shape shape = g.getClip();
      g.setClip(bounds);

      ExportUtil.printPresenter(painter, w, h, fmt, g, bounds);
      g.setClip(shape);

      return bufferedImage;
   }

   /**
    * For Spinner, should use padding to control the inner text's vertical position.
    */
   private String getSpinnerInnerTextPadding(VSCompositeFormat fmt, Dimension size) {
      if("middle".equals(VSCSSUtil.getvAlign(fmt))) {
         return "";
      }

      Font font = fmt.getFont() != null ? fmt.getFont() : Util.DEFAULT_FONT;
      int padding = (int) size.getHeight() - font.getSize() - 3;

      return padding <= 0 ? "" : "top".equals(VSCSSUtil.getvAlign(fmt)) ?
         "0px 0px " + padding + "px" : padding + "px 0px 0px";
   }

   private BufferedImage bufferedImage;
   private Graphics2D svgGraphics;
   private Map<Integer, ColumnIndexMap> columnIndexMaps = new HashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(HTMLCoordinateHelper.class);
}

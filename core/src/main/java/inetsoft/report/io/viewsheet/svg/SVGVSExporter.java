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
package inetsoft.report.io.viewsheet.svg;

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.internal.Common;
import inetsoft.report.io.rtf.RichText;
import inetsoft.report.io.viewsheet.*;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.XPortalHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Viewsheet exporter that creates SVG images.
 *
 * @since 12.1
 */
public class SVGVSExporter extends AbstractVSExporter {
   /**
    * Creates a new instance of <tt>SVGVSExporter</tt>.
    *
    * @param output the output stream to which the image will be written.
    */
   public SVGVSExporter(OutputStream output) {
      this.output = output;
      this.helper = new SVGCoordinateHelper();

      try {
         this.theme = new FlexTheme(PortalThemesManager.getColorTheme());
      }
      catch(Exception e) {
         LOG.error("Failed to set viewsheet theme", e);
      }
   }

   /**
    * Creates a new instance of <tt>SVGVSExporter</tt>.
    *
    * @param output the output stream to which the image will be written.
    */
   public SVGVSExporter(OutputStream output, SVGCoordinateHelper helper) {
      this(output);
      this.helper = helper;
   }

   @Override
   protected void writeCalendar(CalendarVSAssembly assembly) {
      if(assembly.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         writeCalendarTitle(assembly);
         return;
      }

      writePicture(assembly);
   }

   private void writeCalendarTitle(CalendarVSAssembly assembly) {
      try {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo)
            assembly.getVSAssemblyInfo();
         VSCompositeFormat format = getCalendarTitleFormat(info);
         String txt = info.getDisplayValue();
         Dimension size = info.getPixelSize();

         // calculate the area the String will cover
         Dimension pixsize = new Dimension((int) info.getPixelSize().getWidth(), size.height);
         format.getUserDefinedFormat().setWrapping(true);
         Rectangle2D bounds = helper.getBounds(info.getPixelOffset(), pixsize);
         helper.drawTextBox(bounds, bounds, format, txt, true, info.getTitlePadding());
      }
      catch(Exception e) {
         LOG.error("Failed to write calendar title: " + assembly.getAbsoluteName(), e);
      }
   }

   @Override
   protected boolean supportChartSlices() {
      return false;
   }

   @Override
   protected void writeSliceChart(ChartVSAssembly assembly, DataSet data,
                                  VGraphPair pair, boolean match, boolean imgOnly)
   {
      VGraph graph = pair.getExpandedVGraph();
      // pdf do not need to slice chart
      writeChart(assembly, graph, data, imgOnly);
   }

   @Override
   protected void writeChart(ChartVSAssembly originalAsm, ChartVSAssembly asm,
                             VGraph graph, DataSet data, BufferedImage img,
                             boolean firstTime, boolean imgOnly)
   {
      // do nothing, pdf paint image out directory
   }

   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                             DataSet data, boolean imgOnly)
   {
      Graphics2D g2 = Common.getGraphics2D(helper.getGraphics().create());
      VSChart chart = new VSChart(viewsheet);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getVSAssemblyInfo();
      chart.setViewsheet(info.getViewsheet());
      chart.setTheme(theme);
      chart.setAssemblyInfo(info);
      Rectangle2D bounds = helper.getBounds(chartAsm.getVSAssemblyInfo());

      if(!isMatchLayout()) {
         Rectangle2D rect = vgraph.getBounds();
         double w = rect.getWidth() + info.getPadding().left + info.getPadding().right;
         double h = rect.getHeight() + info.getPadding().top + info.getPadding().bottom;

         if(info.isTitleVisible()) {
            h += info.getTitleHeight();
         }

         chart.setPixelSize(new Dimension((int) w, (int) h));
         rect = new Rectangle2D.Double(bounds.getX(), bounds.getY(), (float) w, (float) h);
         g2.setClip(rect);
      }
      else {
         g2.setClip(bounds);
      }

      writeChart(g2, chart, vgraph, bounds);
      g2.dispose();
   }

   @Override
   protected void writeCheckBox(CheckBoxVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeCylinder(CylinderVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeGauge(GaugeVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeImageAssembly(ImageVSAssembly assembly,
                                     XPortalHelper phelper)
   {
      ImageVSAssemblyInfo info =
         (ImageVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Rectangle2D bounds = helper.getBounds(info);

      try {
         if(info.getImage() == null) {
            helper.getGraphics().setClip(helper.getGraphics().getClip());
            return;
         }

         VSImage image = new VSImage(info.getViewsheet());
         image.setTheme(theme);
         image.setAssemblyInfo(info);
         Image rimg = VSUtil.getVSImage(info.getRawImage(), info.getImage(),
                                        assembly.getViewsheet(),
                                        image.getContentWidth(),
                                        image.getContentHeight(),
                                        image.getAssemblyInfo().getFormat(),
                                        phelper);
         image.setRawImage(rimg);
         BufferedImage img = (BufferedImage) image.getImage(true);
         helper.drawImage(img, bounds, info.getImageAlpha());
      }
      catch(Exception ex) {
         LOG.error("Failed to write image: " + assembly.getAbsoluteName(), ex);
      }
   }

   @Override
   protected void writeSubmit(SubmitVSAssembly assembly) {
      helper.getGraphics().setClip(helper.getGraphics().getClip());
   }

   @Override
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeSelectionList(SelectionListVSAssembly assembly) {
      new SVGSelectionListHelper(helper).write(assembly);
   }

   @Override
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      new SVGSelectionTreeHelper(helper).write(assembly);
   }

   @Override
   protected void writeSlider(SliderVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeSpinner(SpinnerVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      SVGTableHelper thelper = new SVGTableHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
   }

   @Override
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      SVGCrosstabHelper thelper = new SVGCrosstabHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
   }

   @Override
   protected void writeCalcTable(CalcTableVSAssembly assembly,
                                 VSTableLens lens)
   {
      SVGCrosstabHelper thelper = new SVGCrosstabHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
   }

   @Override
   protected void writeTextInput(TextInputVSAssembly assembly) {
      Object value = assembly.getSelectedObject() != null ?
         assembly.getSelectedObject() : null;
      writeText(assembly, value == null ? "" : Tool.getDataString(value, assembly.getDataType()));
   }

   @Override
   protected void writeText(VSAssembly assembly, String txt) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         VSCompositeFormat fmt = info.getFormat();

         if(assembly instanceof TextVSAssembly &&
            fmt != null && fmt.getPresenter() != null)
         {
            writePicture(assembly);
            return;
         }

         boolean shadow = false;

         if(assembly instanceof TextVSAssembly) {
            shadow = ((TextVSAssemblyInfo) info).getShadowValue();
         }

         Rectangle2D bounds = helper.getBounds(info);
         helper.drawTextBox(bounds, bounds, getTextFormat(info), txt, null, info.getPadding(), shadow);
      }
   }

   @Override
   protected void writeText(String text, Point pos, Dimension size,
                            VSCompositeFormat format) {
      Rectangle2D bounds = helper.getBounds(pos, size);
      helper.drawTextBox(bounds, format, text);
   }

   @Override
   protected void writeThermometer(ThermometerVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void expandTable(TableDataVSAssembly obj, VSTableLens table,
                              boolean exprow)
   {
      if(table == null) {
         return;
      }

      if(!exprow) {
         super.expandTable(obj, table, false);
         return;
      }

      table.moreRows(Integer.MAX_VALUE);

      int n = getExpandTableHeight(obj, table);
      // @by stephenwebster, For bug1409232608443, comment out setting a minimum of 500
      // This appears to be an artificial value to prevent the table from being too
      // large.  Added logic to the PDFCoordinateHelper createPage method to
      // restrict the size of the PDF according to the PDF specification.
      // n = Math.min(n, 500);
      Dimension size = (Dimension) obj.getPixelSize().clone();
      int more = Math.max(0, n - size.height);
      Dimension nsize = new Dimension(size.width, Math.max(size.height, n));
      obj.setPixelSize(nsize);
      insertRowCol(obj, size, more, 0);
   }

   @Override
   protected void writeTimeSlider(TimeSliderVSAssembly assembly) {
      TimeSliderVSAssemblyInfo info =
         (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         FormatInfo finfo = info.getFormatInfo();
         VSCompositeFormat format = new VSCompositeFormat();

         if(finfo != null) {
            format = finfo.getFormat(new TableDataPath(-1, TableDataPath.TITLE), false);
         }

         format.getUserDefinedFormat().setBackground(
            ExportUtil.getBackGroundColor(format, info.getFormat()));
         Rectangle2D bounds = helper.getBounds(assembly, CoordinateHelper.TITLE);

         if(bounds == null) {
            return;
         }

         if(info.isTitleVisible()) {
            CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo)
               assembly.getContainer().getInfo();
            format = format.clone();
            format.getDefaultFormat().setBorders(new Insets(StyleConstants.NO_BORDER,
               StyleConstants.THIN_LINE, StyleConstants.THIN_LINE, StyleConstants.NO_BORDER));
            format.getDefaultFormat().setBorderColors(new BorderColors(null,
               VSAssemblyInfo.DEFAULT_BORDER_COLOR, VSAssemblyInfo.DEFAULT_BORDER_COLOR, null));
            Insets padding = new Insets(0, 5, 0, 0);

            if(info.getTitlePadding() != null) {
               padding = info.getTitlePadding();
            }

            helper.drawTextBox(
               bounds, format, ExportUtil.getTextInTitleCell(
               Tool.localize(info.getTitle()),
               assembly.getDisplayValue(true, true), (int) bounds.getWidth(),
               format.getFont(), cinfo.getTitleRatio()), true, padding);
         }

         if(assembly.getPixelSize().height > AssetUtil.defh) {
            double h = 0;

            if(!info.isTitleVisible()) {
               h = helper.getBounds(assembly, CoordinateHelper.TITLE)
                  .getHeight();
            }

            Rectangle2D abounds = helper.getBounds(assembly, CoordinateHelper.ALL);
            bounds = helper.getBounds(assembly, CoordinateHelper.DATA);
            bounds = new Rectangle2D.Double(
               bounds.getX(), bounds.getY() - h, bounds.getWidth(),
               (CoordinateHelper.getAssemblySize(assembly, null).height - info.getTitleHeight()) *
               helper.getScale());

            if(bounds.getY() + bounds.getHeight() > abounds.getY() + abounds.getHeight()) {
               return;
            }

            helper.drawImage(getImage(assembly), bounds);
         }
      }
      else {
         writePicture(assembly);
      }
   }

   @Override
   protected void writeVSTab(TabVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeWarningText(Assembly[] assemblies, String warning,
                                   VSCompositeFormat format) {
      Point textPos = new Point(0, 0);

      for(Assembly assembly : assemblies) {
         VSAssembly vsAssembly = (VSAssembly) assembly;
         prepareAssembly(vsAssembly);
         Rectangle2D bounds = helper.getBounds(vsAssembly.getVSAssemblyInfo());
         textPos.x = Math.max((int) (bounds.getX() + bounds.getWidth()),
                              textPos.x);
         textPos.y = Math.max((int) (bounds.getY() + bounds.getHeight()),
                              textPos.y);
      }

      Dimension size = new Dimension(270, 20);
      textPos.x -= Math.min(size.width, textPos.x);

      Font styleFont = format == null || format.getFont() == null ?
         VSFontHelper.getDefaultFont() : format.getFont();
      FontMetrics fontMetrics = Common.getFontMetrics(styleFont);

      textPos.y += Math.max(size.height, fontMetrics.getHeight());
      Rectangle2D bounds = new Rectangle2D.Double(textPos.x, textPos.y,
                                                  size.width, size.height);

      if(warning != null && !warning.equals("")) {
         Font font0 = helper.getGraphics().getFont();
         helper.getGraphics().setFont(VSFontHelper.getDefaultFont());
         helper.drawTextBox(bounds, format, warning);
         helper.getGraphics().setFont(font0);
      }
   }

   @Override
   protected void writeShape(ShapeVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void prepareAssembly(VSAssembly assembly) {
      super.prepareAssembly(assembly);
      helper.setViewsheet(assembly.getViewsheet());
   }

   @Override
   protected boolean needRegionLens() {
      return true;
   }

   @Override
   protected int getRegionRowCount(TableDataVSAssembly table, TableLens data) {
      int rows = super.getRegionRowCount(table, data);
      final int maxrows = 500;

      // avoid very tall png.
      if(rows > maxrows) {
         rows = maxrows;

         if(LOG.isDebugEnabled()) {
            LOG.debug("Table " + table + " data is truncated to " + maxrows + " rows.");
         }
      }

      return rows;
   }

   /**
    * Write picture.
    * @param assembly the specified VSAssembly.
    */
   private void writePicture(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         Rectangle2D bounds = helper.getBounds(info);
         VSCompositeFormat fmt = info.getFormat();

         if(fmt.getRoundCorner() > 0) {
            bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY(),
               bounds.getWidth() + 1, bounds.getHeight() + 1);
         }

         helper.drawImage(getImage(assembly), bounds);
      }
   }

   @Override
   protected void writeGroupContainer(GroupContainerVSAssembly assembly, XPortalHelper phelper) {
      GroupContainerVSAssemblyInfo info =
         (GroupContainerVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Rectangle2D bounds = helper.getBounds(info);

      try {
         String path = info.getBackgroundImage();

         if(path == null) {
            writePicture(assembly);
            return;
         }

         VSGroupContainer container = new VSGroupContainer(info.getViewsheet());
         container.setTheme(theme);
         container.setAssemblyInfo(info);
         Image rimg = VSUtil.getVSImage(null, path, assembly.getViewsheet(),
                                        container.getContentWidth(),
                                        container.getContentHeight(),
                                        container.getAssemblyInfo().getFormat(),
                                        phelper);

         container.setRawImage(rimg);

         if(info.isTile()) {
            int iw = rimg.getWidth(null);
            int ih = rimg.getHeight(null);

            for(int y = 0; y < bounds.getHeight(); y += ih) {
               for(int x = 0; x < bounds.getWidth(); x += iw) {
                  helper.drawImage(rimg, new Rectangle2D.Double(bounds.getX() + x,
                                                                bounds.getY() + y, iw, ih));
               }
            }
         }
         else {
            helper.drawImage(container.getImage(true), bounds);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to write group container: " + assembly.getAbsoluteName(), ex);
      }
   }

   @Override
   protected void writeAnnotation(AnnotationVSAssembly assembly) {
      super.writeAnnotation(assembly);

      AnnotationVSAssemblyInfo info =
         (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Assembly base = AnnotationVSUtil.getBaseAssembly(viewsheet, info.getAbsoluteName());

      if(isMatchLayout() && ExportUtil.annotationIsOuterTable(base, info, helper)) {
         return;
      }

      writeAnnotation0(info);
   }

   public void writeAnnotation0(VSAssemblyInfo ainfo) {
      if(!(ainfo instanceof AnnotationVSAssemblyInfo)) {
         return;
      }

      AnnotationVSAssemblyInfo info = (AnnotationVSAssemblyInfo) ainfo;
      String line = info.getLine();

      if(line != null) {
         AnnotationLineVSAssembly lineAss =
            (AnnotationLineVSAssembly) viewsheet.getAssembly(line);

         if(lineAss != null && "show".equals(((VSAssemblyInfo)
            lineAss.getInfo()).getVisibleValue()))
         {
            writeShape(lineAss);
         }
      }

      String rectangle = info.getRectangle();

      if(rectangle != null) {
         AnnotationRectangleVSAssembly recAss =
            (AnnotationRectangleVSAssembly) viewsheet.getAssembly(rectangle);

         if(recAss != null) {
            writeShape(recAss);

            AnnotationRectangleVSAssemblyInfo recInfo =
               (AnnotationRectangleVSAssemblyInfo) recAss.getVSAssemblyInfo();

            if(recInfo != null) {
               Rectangle2D bounds = helper.getBounds(recInfo);
               java.util.List list = AnnotationVSUtil.getAnnotationContent(
                  viewsheet, recInfo, bounds);

               String content = "";

               for(Object text : list) {
                  if(!content.isEmpty()) {
                     content += "\n";
                  }

                  content += ((RichText) text).getContent();
               }

               boolean containsCJK = false;

               for(int i = 0; i < content.length(); i++) {
                  char c = content.charAt(i);

                  if(Common.isInCJKCharacters(c)) {
                     containsCJK = true;
                     break;
                  }
               }

               VSCompositeFormat format = getTextFormat(recInfo);

               //surround content in html tags so that swing will render it
               content = AnnotationVSUtil.getAnnotationHTMLContent(viewsheet, recInfo, bounds);
               content = "<html>" + content + "</html>";
               int textAlign = AnnotationVSUtil.getAnnotationTextAlignment(recInfo.getContent());

               // if contains CJK Characters, use "SimSun" font as the default
               // font
               if(containsCJK) {
                  Font f = new Font("SimSun", Font.PLAIN, 11);
                  format.getUserDefinedFormat().setFont(f);
               }

               format.getUserDefinedFormat().setWrapping(true);
               bounds = new Rectangle2D.Double(bounds.getX() + 2,
                                               bounds.getY() + 2, bounds.getWidth() - 4,
                                               bounds.getHeight() - 4);

               Rectangle rect = bounds.getBounds();
               JLabel jLabel = new JLabel();
               jLabel.setBounds(rect);
               jLabel.setHorizontalAlignment(ExportUtil.getHorizontalAlignment(textAlign));
               jLabel.setVerticalAlignment(ExportUtil.getVerticalAlignment(format.getAlignment()));

               //set text last as setting format after text might cause html not to render
               jLabel.setText(content);

               //translate graphics object because you can only paint at the origin
               final Graphics2D gfx = helper.getGraphics();
               gfx.translate(rect.getX(), rect.getY());
               jLabel.paint(gfx);
               gfx.translate(-rect.getX(), -(rect.getY()));
            }
         }
      }
   }

   @Override
   public void prepareSheet(Viewsheet vsheet, String sheet,
                            ViewsheetSandbox box) throws Exception
   {
      super.prepareSheet(vsheet, sheet, box);

      if(isAllHidden(viewsheet, box)) {
         Dimension size = vsheet.getPreferredSize();
         helper.getGraphics().
            setClip(new Rectangle(0, 0, size.width, size.height));
      }

      helper.setViewsheet(vsheet);
      helper.processViewsheet(vsheet);
   }

   /**
    * Check if all the assemblies are hidden.
    */
   private boolean isAllHidden(Viewsheet viewsheet, ViewsheetSandbox box) {
      Assembly[] assemblies = viewsheet.getAssemblies();

      for(Assembly assembly : assemblies) {
         if(!assembly.isVisible()) {
            continue;
         }

         if(assembly instanceof ChartVSAssembly) {
            try {
               String name = assembly.getAbsoluteName();
               VGraphPair pair = box.getVGraphPair(name);
               DataSet data = pair.getData();

               if(data != null && data.getRowCount() != 0 &&
                  data.getColCount() != 0) {
                  return false;
               }
            }
            catch(Exception ex) {
               LOG.debug("Failed to determine if chart is hidden: {}",
                         assembly.getAbsoluteName(), ex);
            }
         }
         else {
            return false;
         }
      }

      return true;
   }

   @Override
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
      new SVGCurrentSelectionHelper(helper).write(assembly);
   }

   @Override
   public void write() throws IOException {
      helper.write(output);
      output.close();
   }

   @Override
   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // no-op
   }

   /**
    * Determines whether current cell background will overlay object background.
    *
    * @param fmt the specify cell format.
    * @param parentFmt parent assembly format.
    *
    * @return <tt>true</tt> if the background is needed.
    */
   static boolean isBackgroundNeed(VSCompositeFormat fmt,
                                   VSCompositeFormat parentFmt)
   {
      return !(fmt == null || parentFmt == null) &&
         !Tool.equals(fmt.getBackground(), parentFmt.getBackground());
   }

   private OutputStream output;
   private SVGCoordinateHelper helper;

   private static final Logger LOG =
      LoggerFactory.getLogger(SVGVSExporter.class);
}

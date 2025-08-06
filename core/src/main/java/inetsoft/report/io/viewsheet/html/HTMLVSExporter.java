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

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.gui.viewsheet.VSGroupContainer;
import inetsoft.report.gui.viewsheet.VSImage;
import inetsoft.report.internal.table.TableFormat;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.Format;

/**
 * Viewsheet exporter that creates HTML elements.
 *
 * @since 13.1
 */
public class HTMLVSExporter extends AbstractVSExporter {
   /**
    * Creates a new instance of <tt>HTMLVSExporter</tt>.
    *
    * @param output the output stream to which the image will be written.
    */
   public HTMLVSExporter(OutputStream output) {
      try {
         writer = new PrintWriter(new OutputStreamWriter(output, "UTF8"));
      }
      catch(Exception ex) {
      }

      this.helper = new HTMLCoordinateHelper();
   }

   @Override
   public int getFileFormatType() {
      return FileFormatInfo.EXPORT_TYPE_HTML;
   }

   public void write() throws IOException {
      helper.write(writer);
      writer.close();
   }

   @Override
   public void prepareSheet(Viewsheet vsheet, String sheet, ViewsheetSandbox box)
      throws Exception
   {
      super.prepareSheet(vsheet, sheet, box);
      helper.setViewsheet(vsheet);
      helper.processViewsheet(writer);
   }

   protected void prepareAssembly(VSAssembly assembly) {
      super.prepareAssembly(assembly);
      helper.setViewsheet(assembly.getViewsheet());
   }

   /**
    * Check need use the region table lens or not.
    */
   protected boolean needRegionLens() {
      return false;
   }

   /**
    * Write image assembly.
    * @param assembly the ImageVSAssembly to be written.
    */
   protected void writeImageAssembly(ImageVSAssembly assembly, XPortalHelper phelper) {
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
         Image rimg = VSUtil.getVSImage(info,
                                        assembly.getViewsheet(),
                                        image.getContentWidth(),
                                        image.getContentHeight(),
                                        image.getAssemblyInfo().getFormat(),
                                        phelper);
         image.setRawImage(rimg);
         BufferedImage img = (BufferedImage) image.getImage(true);
         helper.drawImage(img, bounds);
         helper.writeImage(img, writer, assembly.getName(), bounds, info.getHyperlinkRef(),
                           info.getFormat(), true, info.getImageAlpha(), info.getZIndex());
      }
      catch(Exception ex) {
         LOG.error("Failed to write image: " + assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write picture.
    * @param assembly the specified VSAssembly.
    */
   private void writePicture(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         Rectangle2D bounds = helper.getBounds(info);
         BufferedImage img = getImage(assembly);
         boolean ignoreBackground = assembly instanceof ShapeVSAssembly ||
            assembly instanceof GaugeVSAssembly || assembly instanceof TabVSAssembly;

         try {
            helper.drawImage(img, bounds);
            helper.writeImage(img, writer, info.getAbsoluteName(), bounds, info.getHyperlinkRef(),
                              info.getFormat(), !ignoreBackground, null, info.getZIndex());
         }
         catch(Exception ex) {
            LOG.error("Failed to write image: " + assembly.getAbsoluteName(), ex);
         }
      }
   }

   /**
    * Write Textinput assembly.
    * @param assembly the specified TextInputVSAssembly.
    */
   protected void writeTextInput(TextInputVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         VSCompositeFormat fmt = info.getFormat();
         Object value = assembly.getSelectedObject() != null ?
            assembly.getSelectedObject() : null;
         Rectangle2D bounds = helper.getBounds(info);
         helper.writeTextInput(writer, bounds, assembly, getTextFormat(info), value, info.getZIndex());
      }
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   protected void writeText(VSAssembly assembly, String txt) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         VSCompositeFormat fmt = info.getFormat();
         boolean autoSize = false;

         if(assembly instanceof TextVSAssembly) {
            if(fmt != null && fmt.getPresenter() != null) {
               writePicture(assembly);
               return;
            }

            autoSize = ((TextVSAssemblyInfo) info).isAutoSize();
         }

         boolean shadow = false;

         if(assembly instanceof TextVSAssembly) {
            shadow = ((TextVSAssemblyInfo) info).getShadowValue();
         }

         Rectangle2D bounds = helper.getBounds(info);
         Hyperlink.Ref ref = info.getHyperlinkRef();
         helper.writeText(writer, bounds, getTextFormat(info), txt, ref, autoSize,
                          info.getPadding(), shadow, info.getZIndex());
      }
   }

   /**
    * Write text with format.
    * @param text the specified text.
    * @param pos the specified position.
    * @param size the specified text size.
    * @param format the specified text format.
    */
   protected void writeText(String text, Point pos, Dimension size, VSCompositeFormat format) {
   }

   /**
    * Write a range slider.
    */
   protected void writeTimeSlider(TimeSliderVSAssembly assembly) {
      TimeSliderVSAssemblyInfo info =
         (TimeSliderVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         helper.writeTimeSlider(
            writer, assembly, !info.isHidden() ? getImage(assembly) : null, info.getZIndex());
      }
      else {
         writePicture(assembly);
      }
   }

   /**
    * Write a calendar.
    */
   protected void writeCalendar(CalendarVSAssembly assembly) {
      if(assembly.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         writeCalendarTitle(assembly);
         return;
      }

      writePicture(assembly);
   }

   private void writeCalendarTitle(CalendarVSAssembly assembly) {
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(!info.isTitleVisible()) {
         return;
      }

      VSCompositeFormat format = getCalendarTitleFormat(info);
      String txt = info.getDisplayValue();
      Rectangle2D bounds = helper.getBounds(info);
      helper.writeText(writer, bounds, format, txt, null, false, null, false, info.getZIndex());
   }

   /**
    * Write gauge assembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeGauge(GaugeVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write thermometer assembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeThermometer(ThermometerVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write cylinder assembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeCylinder(CylinderVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write SlidingScale assembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write RadioButton VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         RadioButtonVSAssemblyInfo cinfo = (RadioButtonVSAssemblyInfo)info;
         helper.writeList(writer, cinfo, true);
      }
   }

   /**
    * Write CheckBox VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   protected void writeCheckBox(CheckBoxVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         CheckBoxVSAssemblyInfo cinfo = (CheckBoxVSAssemblyInfo) info;
         helper.writeList(writer, cinfo, false);
      }
   }

   /**
    * Write slider assembly.
    * @param assembly the SliderVSAssembly.
    */
   protected void writeSlider(SliderVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write spinner assembly.
    * @param assembly the spinnerVSAssembly.
    */
   protected void writeSpinner(SpinnerVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         VSCompositeFormat fmt = info.getFormat().clone();
         Object txt = ((SpinnerVSAssemblyInfo) info).getSelectedObject();
         Rectangle2D bounds = helper.getBounds(info);
         /*
         helper.writeSpinner(writer, bounds, getTextFormat(info), txt,
            info.getZIndex(), info.getPixelSize());
          */

         fmt.getDefaultFormat().setBorders(new Insets(1, 1, 1, 1));
         fmt.getDefaultFormat().setBorderColors(
            new BorderColors(Color.gray, Color.gray, Color.gray, Color.gray));
         Format fmt2 = TableFormat.getFormat(fmt.getFormat(), fmt.getFormatExtent(),
                                             ThreadContext.getLocale());
         String label = fmt2 != null ? fmt2.format(txt) : Tool.toString(txt);
         helper.writeText(writer, bounds, getTextFormat(info), label, null, false,
                          info.getPadding(), false, info.getZIndex());
      }
   }

   /**
    * Write comboBox assembly.
    * @param assembly the specified ComboBoxVSAssembly.
    */
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo)info;
         helper.writeComboBox(writer, cinfo);
      }
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   protected void writeSelectionList(SelectionListVSAssembly assembly) {
      HTMLSelectionListHelper thelper = new HTMLSelectionListHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.write(writer, assembly);
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      HTMLSelectionTreeHelper thelper = new HTMLSelectionTreeHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.write(writer, assembly);
   }

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      HTMLTableHelper thelper = new HTMLTableHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.write(writer, assembly, lens);
   }

   /**
    * Write crosstab assembly.
    * @param assembly the specified CrosstabVSAssembly.
    */
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      HTMLCrosstabHelper thelper = new HTMLCrosstabHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.write(writer, assembly, lens);
   }

   /**
    * Write calctable assembly.
    * @param assembly the specified CalcTableVSAssembly.
    */
   protected void writeCalcTable(CalcTableVSAssembly assembly, VSTableLens lens) {
      HTMLCrosstabHelper thelper = new HTMLCrosstabHelper(
         helper, assembly.getViewsheet(), assembly);
      thelper.write(writer, assembly, lens);
   }

   /**
    * Write chart assembly.
    * @param chartAsm the specified ChartVSAssembly.
    * @param vgraph the graph object
    * @param data the dataset
    */
   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph, DataSet data,
                             boolean imgOnly)
   {
      try {
         ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getInfo();
         Rectangle2D bounds = helper.getBounds(info);
         final BufferedImage chartImage = getChartImage(chartAsm, vgraph);
         helper.writeImage(chartImage, writer, info.getAbsoluteName(), bounds,
            info.getHyperlinkRef(), info.getFormat(), true, null, info.getZIndex());
      }
      catch(Exception e) {
         LOG.error("Failed to write chart: " + chartAsm.getAbsoluteName(), e);
      }
   }

   /**
    * @param chartAsm the chart assembly.
    * @param vgraph the chart's vgraph.
    *
    * @return the chart image.
    */
   private BufferedImage getChartImage(ChartVSAssembly chartAsm, VGraph vgraph) {
      final int scale = 2;
      final Dimension size = chartAsm.getPixelSize();
      final BufferedImage img =
         new BufferedImage(size.width * scale, size.height * scale, BufferedImage.TYPE_4BYTE_ABGR);
      final Graphics2D g2 = ((Graphics2D) img.getGraphics());

      writeChart(g2, chartAsm, vgraph, scale);
      g2.dispose();

      return img;
   }

   @Override
   protected boolean supportChartSlices() {
      return false;
   }

   protected void writeChart(ChartVSAssembly originalAsm, ChartVSAssembly asm, VGraph graph,
      DataSet data, BufferedImage img, boolean firstTime, boolean imgOnly)
   {
   }

   /**
    * Write tab assembly.
    * @param assembly the specified TabVSAssembly.
    */
   protected void writeVSTab(TabVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write group container assembly.
    * @param assembly the specified GroupContainerVSAssembly.
    */
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

         BufferedImage img = (BufferedImage) container.getImage(true);
         helper.drawImage(img, bounds);
         helper.writeImage(img, writer, assembly.getName(), bounds, info.getHyperlinkRef(),
                           info.getFormat(), true, info.getImageAlpha(), info.getZIndex());
      }
      catch(Exception ex) {
         LOG.error("Failed to write group container: " +
                      assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write shape assembly.
    * @param assembly the specified ShapeVSAssembly.
    */
   protected void writeShape(ShapeVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write current selection assembly.
    * @param assembly the specified CurrentSelectionVSAssembly.
    */
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
      CurrentSelectionVSAssemblyInfo info = (CurrentSelectionVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Viewsheet vs = getViewsheet();
      helper.writeCurrentSelection(writer, info, vs);
   }

   /**
    * Write submit assembly.
    * @param assembly the specified SubmitVSAssembly.
    */
   protected void writeSubmit(SubmitVSAssembly assembly) {
      // same as others export type. it is unnecessray to export submit assembly
//      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
//
//      if(info != null) {
//         VSCompositeFormat fmt = info.getFormat();
//         String txt = ((SubmitVSAssemblyInfo) info).getLabelName();
//         Rectangle2D bounds = helper.getBounds(info);
//         helper.writeButton(writer, bounds, getTextFormat(info), txt, info.getZIndex());
//      }
   }

   /**
    * Write the warning text.
    */
   protected void writeWarningText(Assembly[] assemblies, String warning, VSCompositeFormat format) {
   }

   /**
    * Write annotation assembly.
    * @param assembly the specified AnnotationVSAssembly.
    */
   protected void writeAnnotation(AnnotationVSAssembly assembly) {
      super.writeAnnotation(assembly);
      AnnotationVSAssemblyInfo info = (AnnotationVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Assembly base = AnnotationVSUtil.getBaseAssembly(viewsheet, info.getAbsoluteName());

      if(ExportUtil.annotationIsOuterTable(base, info, helper)) {
         return;
      }

      writeAnnotation0(info);
   }

   /**
    * Write annotation assembly.
    * @param ainfo the specified AnnotationVSAssemblyInfo.
    */
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
               VSCompositeFormat format = getTextFormat(recInfo);
               format.getUserDefinedFormat().setWrapping(true);
               Rectangle2D bounds = helper.getBounds(recInfo);
               bounds = new Rectangle2D.Double(bounds.getX() + 2,
                  bounds.getY() + 2, bounds.getWidth() - 4,
                  bounds.getHeight() - 4);
               String content =
                  AnnotationVSUtil.getAnnotationHTMLContent(viewsheet, recInfo, bounds);
               writeAnnotationContent(bounds, format, content, recInfo.getZIndex());
            }
         }
      }
   }

   private void writeAnnotationContent(Rectangle2D bounds, VSCompositeFormat format,
                                       String dispText, int zIndex)
   {
      StringBuffer buf = new StringBuffer();
      buf.append("<div " + "style='position:absolute;overflow:auto;font-size:13px;z-index:");
      buf.append(zIndex);
      buf.append(";");

      if(bounds != null) {
         buf.append("left:");
         buf.append(bounds.getX());
         buf.append(";top:");
         buf.append(bounds.getY());
         buf.append(";width:");
         buf.append(bounds.getWidth());
         buf.append("px;height:");
         buf.append(bounds.getHeight());
         buf.append("px;");
      }

      buf.append("'>");

      if(dispText != null) {
         buf.append("<p style='margin:0;display:flex;flex-direction:column;overflow-wrap:break-word;white-space:pre-wrap;'>");
         buf.append(dispText.replaceAll("<p>", "<p style='margin:0px'>"));
         buf.append("</p>");
      }

      buf.append("</div>");

      try {
         writer.write(buf.toString());
      }
      catch(Exception e) {
         LOG.error("Failed to write annotation content: " + dispText, e);
      }
   }

   @Override
   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // no-op
   }

   /**
    * Check if matches layout.
    * @return true
    */
   @Override
   public boolean isMatchLayout() {
      return true;
   }

   private PrintWriter writer;
   private HTMLCoordinateHelper helper;
   private static final Logger LOG = LoggerFactory.getLogger(HTMLVSExporter.class);
}

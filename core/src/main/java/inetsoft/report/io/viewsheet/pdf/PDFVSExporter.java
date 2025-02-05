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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.gui.viewsheet.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.io.viewsheet.*;
import inetsoft.report.io.viewsheet.excel.ExcelVSUtil;
import inetsoft.report.pdf.PDF3Generator;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.util.*;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * The class is used to export pdf.
 *
 * @version 10.1, 13/01/2009
 * @author InetSoft Technology Corp
 */
public class PDFVSExporter extends AbstractVSExporter {
   /**
    * Constructor.
    * @param stream the specified OutputStream.
    */
   public PDFVSExporter(OutputStream stream) {
      helper = new PDFCoordinateHelper(stream);
      generator = PDF3Generator.getPDFGenerator(stream);
      genLink = SreeEnv.getProperty("pdf.generate.links").equalsIgnoreCase("true");
      Objects.requireNonNull(generator).getPrinter().
         setBackground(helper.getPrinter().getBackground());
   }

   @Override
   public int getFileFormatType() {
      return FileFormatInfo.EXPORT_TYPE_PDF;
   }

   /**
    * Export one single viewsheet to document.
    * @param box the viewsheet sandbox.
    * @param sheet the sheet name.
    * @param index the sheet index, Current View is 0, bookmark1 is 1, etc.
    */
   @Override
   public void export(ViewsheetSandbox box, String sheet, int index, XPortalHelper helper)
         throws Exception
   {
      Viewsheet viewsheet = box.getViewsheet();
      LayoutInfo layoutinfo = viewsheet.getLayoutInfo();
      PrintLayout playout = layoutinfo.getPrintLayout();

      if(playout != null) {
         if(playout.isEmpty()) {
            LOG.warn("Empty print layout ignored.");
         }
         else {
            try {
               executeViewDynamicValues(viewsheet, box);
            }
            catch(Exception ex) {
               LOG.error("Failed to execute dynamic values", ex);
            }

            viewsheet.updateCSSFormat("pdf", null, box);

            VsToReportConverter converter = new VsToReportConverter(box);
            ReportSheet report = converter.generateReport();

            // Build a list of report sheets instead of generating a printlayout
            // pdf here so we can build a CompositeSheet later; see write()
            reportList.add(report);

            return;
         }
      }

      super.export(box, sheet, index, helper);
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCalendar(CalendarVSAssembly assembly) {
      if(assembly.getShowType() == CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE) {
         writeCalendarTitle(assembly);
         return;
      }

      writePicture(assembly);
   }

   /**
    * Write the single calendar title cell.
    */
   private void writeCalendarTitle(CalendarVSAssembly assembly) {
      try {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo)
            assembly.getVSAssemblyInfo();
         VSCompositeFormat format = getCalendarTitleFormat(info);
         String txt = info.getDisplayValue();
         Dimension size = info.getPixelSize();

         // calculate the area the String will cover
         Dimension pixsize = calculateSizeInPixel(info);
         Font styleFont = format.getFont() == null ?
            VSFontHelper.getDefaultFont() : format.getFont();
         FontMetrics fontMetrics = Common.getFontMetrics(styleFont);
         int totalLength = fontMetrics.stringWidth(txt) == 0 ?
            (int) pixsize.getWidth() : fontMetrics.stringWidth(txt);
         double height = Math.ceil((totalLength / pixsize.getWidth()) * AssetUtil.defh);
         height = Math.max(height, size.getHeight());
         pixsize = new Dimension((int) info.getPixelSize().getWidth(), (int) height);
         format.getUserDefinedFormat().setWrapping(true);
         Rectangle2D bounds =
            helper.createBounds(info.getViewsheet().getPixelPosition(info), pixsize);
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

   /**
    * Write slice chart.
    */
   @Override
   protected void writeSliceChart(ChartVSAssembly assembly, DataSet data,
                                  VGraphPair pair, boolean match, boolean imgOnly)
   {
      VGraph graph = pair.getExpandedVGraph();
      // pdf do not need to slice chart
      writeChart(assembly, graph, data, imgOnly);
   }

   /**
    * Write the slice chart.
    * @param originalAsm the original chart vs assembly.
    * @param asm the slice chart vs assembly.
    * @param graph the vgraph to paint chart.
    * @param data the data set.
    * @param img the buffered image of the slice chart.
    * @param firstTime the first to write a slice chart of the whole chart.
    */
   @Override
   protected void writeChart(ChartVSAssembly originalAsm, ChartVSAssembly asm,
                             VGraph graph, DataSet data, BufferedImage img, boolean firstTime,
                             boolean imgOnly)
   {
      // do nothing, pdf paint image out directory
   }

   @Override
   protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph,
                             DataSet data, boolean imgOnly)
   {
      VSChart chart = new VSChart(viewsheet);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chartAsm.getVSAssemblyInfo();
      chart.setViewsheet(info.getViewsheet());
      chart.setTheme(theme);
      chart.setAssemblyInfo(info);
      Rectangle2D bounds = helper.getBounds(chartAsm.getVSAssemblyInfo());
      Graphics2D g2 = Common.getGraphics2D(helper.getPrinter().create());
      g2.setClip(bounds);

      // avoid huge pdf when number of points is large (46101).
      if(data.getRowCount() <= 10000) {
         writeChart(g2, chart, vgraph, bounds);
      }
      else {
         int w = (int) bounds.getWidth() * 4;
         int h = (int) bounds.getHeight() * 4;
         BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g3 = (Graphics2D) img.getGraphics();
         writeChart(g3, chart, vgraph, new Rectangle2D.Double(0, 0, w, h), 4);
         g3.dispose();
         g2.drawImage(img, (int) bounds.getX(), (int) bounds.getY(), (int) bounds.getWidth(),
                      (int) bounds.getHeight(), null);
      }

      g2.dispose();

      final int theight = getTitleHeight(info);
      final Insets2D border = getBorderOffset(info.getFormat());

      if(genLink) {
         Insets padding = info.getPadding();
         Rectangle2D bounds2 = new Rectangle2D.Double(
            bounds.getX() + padding.left + border.left,
            bounds.getY() + padding.top + border.top + theight,
            bounds.getWidth(), bounds.getHeight());
         processHyperlink(vgraph, data, info, bounds2);
      }
   }

   /**
    * Set the hyperlink into the helper.
    */
   @Override
   protected void setHyperlink(VGraph vgraph, Rectangle2D bounds, Hyperlink.Ref hyperlink,
                               Shape shape)
   {
      if(hyperlink == null || hyperlink.getLinkType() != Hyperlink.WEB_LINK) {
         return;
      }

      float pH = helper.getPrinter().getPageSize().height * 72;
      LinkArea area = new LinkArea(shape,
         GTool.getFlipYTransform(vgraph), bounds.getX(),
         bounds.getY(), pH);
      helper.setLinks(area, hyperlink);
   }

   /**
    * Write CheckBox VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCheckBox(CheckBoxVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write comboBox assembly.
    * @param assembly the specified ComboBoxVSAssembly.
    */
   @Override
   protected void writeComboBox(ComboBoxVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write cylinder assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeCylinder(CylinderVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write gauge assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeGauge(GaugeVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write image assembly.
    * @param assembly the ImageVSAssembly to be written.
    */
   @Override
   protected void writeImageAssembly(ImageVSAssembly assembly,
                                     XPortalHelper phelper)
   {
      ImageVSAssemblyInfo info =
         (ImageVSAssemblyInfo) assembly.getVSAssemblyInfo();
      Rectangle2D bounds = helper.getBounds(info);

      try {
         String path = info.getImage();

         if(info.getImage() == null) {
            helper.getPrinter().setClip(helper.getPrinter().getClip());
            return;
         }

         boolean isSVG = path.toLowerCase().endsWith(".svg");

         if(isSVG && (path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE) ||
                      path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE) ||
                      path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE)))
         {
            try {
               Viewsheet vs = getViewsheet();
               byte[] svg = null;

               if(path.startsWith(ImageVSAssemblyInfo.UPLOADED_IMAGE)) {
                  svg = vs.getUploadedImageBytes(
                     path.substring(ImageVSAssemblyInfo.UPLOADED_IMAGE.length()));
               }
               else if(path.startsWith(ImageVSAssemblyInfo.SKIN_IMAGE)) {
                  svg = vs.getUploadedImageBytes(
                     path.substring(ImageVSAssemblyInfo.SKIN_IMAGE.length()));
               }
               else if(path.startsWith(ImageVSAssemblyInfo.SERVER_IMAGE)) {
                  svg = vs.getUploadedImageBytes(
                     path.substring(ImageVSAssemblyInfo.SERVER_IMAGE.length()));
               }

               if(svg != null) {
                  double x = bounds.getX();
                  double y = bounds.getY();
                  double width = bounds.getWidth();
                  double height = bounds.getHeight();

                  Graphics g = helper.getPrinter();
                  Document doc =
                     SVGSupport.getInstance().createSVGDocument(new ByteArrayInputStream(svg));
                  Graphics2D g2 = (Graphics2D) g.create();

                  MetaImage.printSVG(g2, x, y, width, height, doc);
                  g2.dispose();
               }
            }
            catch(Exception ex) {
               LOG.debug("Failed to create temp file: " + ex, ex);
            }
         }
         else {
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
      }
      catch(Exception ex) {
         LOG.error("Failed to write image: " + assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write submit assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSubmit(SubmitVSAssembly assembly) {
      // it is unnecessray to export submit assembly
      helper.getPrinter().setClip(helper.getPrinter().getClip());
   }

   /**
    * Write RadioButton VSAssembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeRadioButton(RadioButtonVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   @Override
   protected void writeSelectionList(SelectionListVSAssembly assembly) {
      new PDFSelectionListHelper(helper).write(assembly);
   }

   /**
    * Write selection list assembly.
    * @param assembly the specified SelectionListVSAssembly.
    */
   @Override
   protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {
      new PDFSelectionTreeHelper(helper).write(assembly);
   }

   /**
    * Write slider assembly.
    * @param assembly the SliderVSAssembly.
    */
   @Override
   protected void writeSlider(SliderVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write SlidingScale assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write spinner assembly.
    * @param assembly the spinnerVSAssembly.
    */
   @Override
   protected void writeSpinner(SpinnerVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Write table assembly.
    * @param assembly the specified TableVSAssembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
      PDFTableHelper thelper = new PDFTableHelper(helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
      setMaxRows(thelper.getMaxRows());
   }

   /**
    * Write crosstab assembly.
    * @param assembly the specified CrosstabVSAssembly.
    * @param lens the specified VSTableLens.
    */
   @Override
   protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {
      PDFCrosstabHelper thelper = new PDFCrosstabHelper(helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
   }

   /**
    * Write calctable assembly.
    * @param assembly the specified CalcTableVSAssembly.
    */
   @Override
   protected void writeCalcTable(CalcTableVSAssembly assembly,
                                 VSTableLens lens)
   {
      PDFCrosstabHelper thelper = new PDFCrosstabHelper(helper, assembly.getViewsheet(), assembly);
      thelper.setExporter(this);
      thelper.write(assembly, lens);
   }

   /**
    * Write textinput with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeTextInput(TextInputVSAssembly assembly) {
      Object value = assembly.getSelectedObject();
      writeText(assembly, value == null ? "" : Tool.getDataString(value, assembly.getDataType()));
   }

   /**
    * Write text with format.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeText(VSAssembly assembly, String txt) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         VSCompositeFormat fmt = info.getFormat();

         if(assembly instanceof TextVSAssembly && fmt != null && fmt.getPresenter() != null) {
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

   /**
    * Write text with format.
    * @param text the specified text.
    * @param pos the specified position.
    * @param size the specified text size.
    * @param format the specified text format.
    */
   @Override
   protected void writeText(String text, Point pos, Dimension size,
                            VSCompositeFormat format) {
      Rectangle2D bounds = helper.getBounds(pos, size);
      helper.drawTextBox(bounds, format, text);
   }

   /**
    * Write thermometer assembly.
    * @param assembly the specified VSAssembly.
    */
   @Override
   protected void writeThermometer(ThermometerVSAssembly assembly) {
      writePicture(assembly);
   }

   /**
    * Expand the table data assembly to show data without scrolling.
    * @param table the specified table.
    * @param exprow true to expand row, false to expand column.
    */
   @Override
   protected void expandTable(TableDataVSAssembly obj, VSTableLens table, boolean exprow) {
      if(table == null) {
         return;
      }

      if(!exprow) {
         super.expandTable(obj, table, exprow);
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

   /**
    * Write a range slider.
    */
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
            format = finfo.getFormat(
               new TableDataPath(-1, TableDataPath.TITLE), false);
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
            helper.drawTextBox(bounds, bounds, format,
               ExportUtil.getTextInTitleCell(Tool.localize(info.getTitle()),
                  assembly.getDisplayValue(true, true), (int) bounds.getWidth(),
                                             format.getFont(), cinfo.getTitleRatio()), true,
                                             info.getTitlePadding());
         }

         if(assembly.getPixelSize().height > info.getTitleHeight()) {
            double h = 0;

            if(!info.isTitleVisible()) {
               h = helper.getBounds(assembly, CoordinateHelper.TITLE).getHeight();
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

   /**
    * Write VSTab assembly.
    * @param assembly the VSTabAssembly.
    */
   @Override
   protected void writeVSTab(TabVSAssembly assembly) {
      writePicture(assembly);
   }

   @Override
   protected void writeAdditionalTipMessage() {
      PDFPrinter printer = this.helper.getPrinter();

      if(printer.isOutOfMaxPageSize()) {
         double y = printer.getPageSize().height * PDFPrinter.RESOLUTION - 30;
         double width = printer.getPageSize().width * PDFPrinter.RESOLUTION;
         Rectangle2D bounds = new Rectangle2D.Double(0, y, width, 30);
         VSCompositeFormat format = new VSCompositeFormat();
         format.getUserDefinedFormat().setForeground(Color.RED);
         Font font0 = printer.getFont();
         printer.setFont(VSFontHelper.getDefaultFont());
         helper.drawTextBox(bounds, format,
                            Catalog.getCatalog().getString("vs.export.pdftable.outOfLength"));
         printer.setFont(font0);
      }
   }

   /**
    * implements the AbstractVSExporter to writeText warning.
    * @param assemblies the Assembly array.
    * @param warning the String of warning.
    * @param format the VSCompositeFormat.
    */
   @Override
   protected void writeWarningText(Assembly[] assemblies, String warning,
                                   VSCompositeFormat format) {
      Point textPos = new Point(0, 0);

      for(Assembly value : assemblies) {
         VSAssembly assembly = (VSAssembly) value;
         prepareAssembly(assembly, true);
         Rectangle2D bounds = helper.getBounds(assembly.getVSAssemblyInfo());
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
         Font font0 = helper.getPrinter().getFont();
         helper.getPrinter().setFont(VSFontHelper.getDefaultFont());
         helper.drawTextBox(bounds, format, warning);
         helper.getPrinter().setFont(font0);
      }
   }

   /**
    * Write shape assembly.
    * @param assembly the specified ShapeVSAssembly.
    */
   @Override
   protected void writeShape(ShapeVSAssembly assembly) {
      writePicture(assembly);
   }

   private void writeHyperlink(Hyperlink.Ref link, Object linkBounds,
                               double pgH, int page)
   {
      String hlink = Util.createURL(link);
      hlink = ExcelVSUtil.fixLink(hlink, link.getLinkType());

      int linkid = 0;
      String linkobj = null;

      if(link.getLinkType() == Hyperlink.WEB_LINK) {
         if(linkBounds instanceof Rectangle2D) {
            Rectangle2D bounds = (Rectangle2D) linkBounds;
            double llx = (bounds).getX();
            double lly = pgH - bounds.getY() - bounds.getHeight();
            double urx = llx + bounds.getWidth();
            double ury = lly + bounds.getHeight();
            linkobj = "<<\n/A << /S /URI /URI (" + hlink +
            ")>>\n/Type /Annot\n/Subtype /Link\n/Rect [ " + llx + " " + lly +
            " " + urx + " " + ury + " ]\n/Border [ 0 0 0 ]\n/H /I\n>>";
         }
         else if(linkBounds instanceof LinkArea) {
            LinkArea area = (LinkArea) linkBounds;
            linkobj = "<<\n/A << /S /URI /URI (" + hlink +
               ")>>\n/Type /Annot\n/Subtype /Link\n" + area.getRectArray() +
               // this adds a lot of text but doesn't seem to do anything since the
               // hit area is defined by /Rect instead of /QuadPoints. there is also
               // no reference of using QuadPoints in /Annot Link type.
               //"\n" + area.getQuadPointsArray() +
               "\n/Border [ 0 0 0 ]\n/H /I\n>>";
         }

         linkid = helper.getPrinter().getNextObjectID();
      }

      if(linkobj != null) {
         helper.getPrinter().addAnnotation(linkid, page);
         helper.getPrinter().addObject(linkid, linkobj);
      }
   }

   @Override
   protected void prepareAssembly(VSAssembly assembly) {
      prepareAssembly(assembly, false);
   }

   /**
    * This method is called before writing the specified assembly.
    */
   protected void prepareAssembly(VSAssembly assembly, boolean warning) {
      super.prepareAssembly(assembly);

      helper.setViewsheet(assembly.getViewsheet());
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      Rectangle2D bounds = helper.getBounds(info);

      if(!warning && genLink) {
         try {
            Hyperlink.Ref ref = info.getHyperlinkRef();

            if(ref != null) {
               helper.setLinks(bounds, ref);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to prepare assembly: " + assembly.getAbsoluteName(), ex);
         }
      }
   }

   /**
    * Write picture.
    * @param assembly the specified VSAssembly.
    */
   private void writePicture(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info != null) {
         helper.drawImage(getImage(assembly), helper.getBounds(info));
      }
   }

   /**
    * Write VSGroupContainer assembly.
    * @param assembly the VSGroupContainerAssembly.
    */
   @Override
   protected void writeGroupContainer(GroupContainerVSAssembly assembly,
                                      XPortalHelper phelper) {
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
         LOG.error("Failed to write group container: " +
            assembly.getAbsoluteName(), ex);
      }
   }

   /**
    * Write annotation assembly.
    * @param assembly the specified AnnotationVSAssembly.
    */
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
               Rectangle2D bounds = helper.getBounds(recInfo);
               VSCompositeFormat format = getTextFormat(recInfo);

               //surround content in html tags so that swing will render it
               String content =
                  AnnotationVSUtil.getAnnotationHTMLContent(viewsheet, recInfo, bounds);
               content = "<html>" + content + "</html>";
               boolean containsCJK = false;

               for(int i = 0; i < content.length(); i++) {
                  char c = content.charAt(i);

                  if(Common.isInCJKCharacters(c)) {
                     containsCJK = true;
                     break;
                  }
               }

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
               Graphics gfx = helper.getPrinter().create((int) rect.getX(), (int) rect.getY(),
                                                         (int) rect.getWidth(),
                                                         (int) rect.getHeight());
               jLabel.paint(gfx);
               gfx.dispose();
            }
         }
      }
   }

   /**
    * Specify the size and cell size of sheet.
    * @param vsheet the Viewsheet to be exported.
    * @param sheet the specified sheet name.
    * @param box the specified viewsheet sandbox.
    */
   @Override
   public void prepareSheet(Viewsheet vsheet, String sheet,
                            ViewsheetSandbox box) throws Exception
   {
      super.prepareSheet(vsheet, sheet, box);
      Dimension size = viewsheet.getPreferredSize(false, true);

      if(isAllHidden(viewsheet, box)) {
         helper.getPrinter().
            setClip(new Rectangle(0, 0, size.width, size.height));
      }

      helper.setViewsheet(vsheet);
      helper.createPage(size);

      final TableDataPath dataPath = new TableDataPath(-1, TableDataPath.OBJECT);
      final VSCompositeFormat format = viewsheet.getFormatInfo().getFormat(dataPath);

      if(format != null) {
         final Color background = format.getBackground();
         final PDFPrinter printer = helper.getPrinter();

         if(printer != null && (background != null || printer.getBackground() == null)) {
            final Color originalColor = printer.getColor();
            printer.setColor(background != null ? background : Color.WHITE);
            printer.fillRect(0, 0, size.width, size.height);
            printer.setColor(originalColor);
         }
      }
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

   /**
    * Write given VSCurrentSelection to worksheet.
    * @param assembly the specified VSCurrentSelection.
    */
   @Override
   protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {
      new PDFCurrentSelectionHelper(helper).write(assembly);
   }

   /**
    * Write the in-mem document (workbook or show) to OutputStream.
    */
   @Override
   public void write() throws IOException {
      if(!reportList.isEmpty()) {
         // Generate closes the output stream so we need to build a CompositeSheet
         // from the currentview, bookmarks, etc, then call generate only once
         generator.setPrintLayoutMode(true);
         generator.setPrintOnOpen(isPrintOnOpen());
         ReportSheet[] reportSheets = reportList.toArray(new ReportSheet[0]);
         CompositeSheet compositeSheet = new CompositeSheet(reportSheets);
         generator.generate(compositeSheet);
      }
      else {
         LicenseManager licenseManager = LicenseManager.getInstance();

         if(licenseManager.isElasticLicense() && licenseManager.getElasticRemainingHours() == 0) {
            Size pageSize = helper.getPrinter().getPageSize();
            Dimension pageDimension = new Dimension((int) pageSize.width * 72,
               (int) pageSize.height * 72);
            Util.drawWatermark(helper.getPrinter(), pageDimension);
         }
      }

      helper.write();
      float pageH = helper.getPrinter().getPageSize().height * 72;

      if(genLink) {
         for(int i = 0; i <= helper.getPage(); i++) {
            LinkedHashMap<Object, Hyperlink.Ref> links = helper.getLinks(i);

            if(links != null) {
               for(Object bound : links.keySet()) {
                  Hyperlink.Ref hlink = links.get(bound);

                  if(hlink.getLinkType() == Hyperlink.WEB_LINK) {
                     writeHyperlink(hlink, bound, pageH, i);
                  }
               }
            }
         }
      }

      helper.getPrinter().close();
   }

   /**
    * Sets whether the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @param printOnOpen <tt>true</tt> to print when opened.
    */
   public void setPrintOnOpen(boolean printOnOpen) {
      helper.setPrintOnOpen(printOnOpen);
   }

   /**
    * Determines if the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @return <tt>true</tt> to print when opened.
    */
   public boolean isPrintOnOpen() {
      return helper.isPrintOnOpen();
   }

   @Override
   protected void writeChartBackgroundShape(ChartVSAssembly chart, ChartVSAssemblyInfo info,
                                            Point pos, Dimension size)
   {
      // no-op
   }

   /**
    * Determines whether current cell background will overlay object background.
    * @param fmt the specify cell format.
    * @param parentFmt parent assembly format.
    */
   static boolean isBackgroundNeed(VSCompositeFormat fmt,
                                   VSCompositeFormat parentFmt)
   {
      if(fmt == null || parentFmt == null) {
         return false;
      }

      return !Tool.equals(fmt.getBackground(), parentFmt.getBackground());
   }

   private PDFCoordinateHelper helper;
   private PDF3Generator generator;
   private boolean genLink;
   private ArrayList<ReportSheet> reportList = new ArrayList<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(PDFVSExporter.class);
}

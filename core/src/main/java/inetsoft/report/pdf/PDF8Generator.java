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
package inetsoft.report.pdf;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.OutputStream;
import java.util.List;
import java.util.*;

/**
 * Specialization of <tt>PDF4Generator</tt> that supports features in version
 * 1.7 of the PDF specification.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class PDF8Generator extends PDF4Generator {
   /**
    * Creates a new instance of <tt>PDF8Generator</tt>.
    */
   public PDF8Generator() {
      super();
   }

   /**
    * Creates a new instance of <tt>PDF8Generator</tt>.
    *
    * @param out the output stream to which the PDF will be written.
    */
   public PDF8Generator(OutputStream out) {
      super(out);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public PDF3Printer getPrinter() {
      if(printer == null) {
         printer = new PDF8Printer(getOutput());
      }

      return printer;
   }

   @Override
   void print(StylePage page, Paintable pt, int index, Graphics g,
              List<Integer> linkIds, LinkContext linkContext, int pgIndex)
   {
      if((g instanceof PDFPrinter) && ((PDFPrinter) g).isAccessible()) {
         PDFPrinter pdf = (PDFPrinter) g;
         ReportElement element = pt.getElement();

         if(pdf.getReportLocale() == null && element instanceof BaseElement)
         {
            ReportSheet report = ((BaseElement) element).getReport();

            if(report != null) {
               pdf.setReportLocale(report.getLocale());
            }
         }

         // @by davidd 11.5 bug1383073211622, Set PDFPrinter header/body/footer
         // knowledge, used to properly order PDF Tags
         if(isAccessible() && element instanceof BaseElement) {
            ReportSheet report = ((BaseElement) element).getReport();
         
            if(report != null) {
               if(report.getAllHeaderElements().contains(element)) {
                  pdf.setStructurePart(PDFPrinter.StructurePart.HEADER);
               }
               else if(report.getAllFooterElements().contains(element)) {
                  pdf.setStructurePart(PDFPrinter.StructurePart.FOOTER);
               }
               else {
                  pdf.setStructurePart(PDFPrinter.StructurePart.BODY);
               }
            }
         }

         if(pt instanceof TextPaintable) {
            printText(
               page, (TextPaintable) pt, index, pdf,
               linkIds.isEmpty() ? null : linkIds.get(0));
         }
         else if(pt instanceof TablePaintable) {
            printTable((TablePaintable) pt, pdf, pgIndex, linkContext);
         }
         else if(pt instanceof ChartPainterPaintable) {
            printChart((ChartPainterPaintable) pt, pdf);
         }
         else if(pt instanceof ImagePainterPaintable) {
            printImage((ImagePainterPaintable) pt, pdf);
         }
         else if(pt instanceof TabPaintable) {
            printTab((TabPaintable) pt, pdf);
         }
         else {
            if(!NON_ARTIFACT.contains(pt.getClass())) {
               LOG.debug(
                  "Excluding element from structure tree (artifact): " +
                  pt.getClass().getSimpleName());
               pdf.startArtifact();
            }

            pt.paint(pdf);

            if(pdf.isAccessible() && !NON_ARTIFACT.contains(pt.getClass())) {
               pdf.endArtifact();
            }
         }
      }
      else {
         super.print(page, pt, index, g, linkIds, linkContext, pgIndex);
      }
   }

   @Override
   List<Integer> processHyperlinks(Paintable pt, LinkContext context,
                                   int pgIndex, boolean genLink)
   {
      List<Integer> linkIds;

      if(isAccessible() && (pt instanceof TablePaintable ||
         pt instanceof ChartPainterPaintable))
      {
         if(pt instanceof TablePaintable) {
            context.lastNode = null;
         }

         linkIds = new ArrayList<>();
      }
      else {
         linkIds = super.processHyperlinks(pt, context, pgIndex, genLink);
      }

      return linkIds;
   }

   private void printText(StylePage page, TextPaintable pt, int index,
                          PDFPrinter pdf, Integer linkId)
   {
      boolean last;
      boolean heading = false;

      if(index == 0 || pt.isFirstPaintable()) {
         pdf.startParagraph(linkId);
      }

      if(index == page.getPaintableCount() - 1) {
         last = true;
      }
      else {
         Paintable pt2 = page.getPaintable(index + 1);
         last = pt2.getElement() != null && pt.getElement() != null &&
            !pt2.getElement().getID().equals(pt.getElement().getID());
      }

      pt.paint(pdf);

      if(last) {
         if(heading) {
            pdf.endHeading();
         }
         else {
            pdf.endParagraph();
         }
      }
   }

   private void printTable(TablePaintable pt, PDFPrinter pdf, int pgIndex,
                           LinkContext linkContext)
   {
      ActionListener listener =
         new PDFTableListener(pt, pdf, pgIndex, linkContext);

      if(pt.listener == null) {
         pt.listener = listener;
      }
      else {
         pt.listener = AWTEventMulticaster.add(pt.listener, listener);
      }

      pdf.startTable();
      pt.paint(pdf);
      pdf.endTable();

      if(pt.listener instanceof AWTEventMulticaster) {
         AWTEventMulticaster.remove(pt.listener, listener);
      }
      else {
         pt.listener = null;
      }
   }

   private void printChart(ChartPainterPaintable pt, PDFPrinter pdf) {
      String alt = null;

      if(pt.getElement() != null) {
         alt = pt.getElement().getID();
      }

      pdf.startFigure(alt);
      pt.paint(pdf);
      pdf.endFigure();
   }

   private void printImage(ImagePainterPaintable pt, PDFPrinter pdf) {
      String alt = null;

      if(pt.getElement() instanceof ImageElementDef) {
         alt = ((ImageElementDef) pt.getElement()).getToolTip();
      }

      pdf.startFigure(alt);
      pt.paint(pdf);
      pdf.endFigure();
   }

   private void printTab(TabPaintable pt, PDFPrinter pdf) {
      if(pt.getFill() != StyleConstants.NO_BORDER || pt.getTabMarker() != null)
      {
         pdf.startArtifact();
      }

      pt.paint(pdf);

      if(pt.getFill() != StyleConstants.NO_BORDER || pt.getTabMarker() != null)
      {
         pdf.endArtifact();
      }
   }

   private PDF8Printer printer = null;
   private static final Logger LOG =
      LoggerFactory.getLogger(PDF8Generator.class);

   @SuppressWarnings("unchecked")
   private static final Set<Class<?>> NON_ARTIFACT =
      new HashSet<>(Arrays.asList(
         GridPaintable.class, SpacePaintable.class, TextPainterPaintable.class
      ));

   private static final class PDFTableListener implements ActionListener {
      public PDFTableListener(TablePaintable paintable, PDFPrinter pdf,
                              int pageIndex, LinkContext context)
      {
         this.paintable = paintable;
         this.pdf = pdf;
         this.pageIndex = pageIndex;
         this.context = context;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         TablePaintable.ObjectInfo cellInfo =
            (TablePaintable.ObjectInfo) e.getSource();

         int row = cellInfo.row;
         int col = cellInfo.col;

         String cmd = e.getActionCommand();
         final int headerR = paintable.getTable().getHeaderRowCount();
         final int headerC = paintable.getTable().getHeaderColCount();

         if("start".equals(cmd) || "startText".equals(cmd)) {
            if(row > lastRow) {
               pdf.startTableRow();
               lastRow = row;
            }

            Rectangle reg = paintable.getTableRegion();
            Integer linkId = null;

            if(row < headerR || row >= reg.y) {
               if(col < headerC || col >= reg.x) {
                  Hyperlink.Ref hlink = paintable.getHyperlink(row, col);

                  if(hlink != null &&
                     (hlink.getLinkType() == Hyperlink.WEB_LINK ||
                        hlink.getLink().startsWith("#"))) {
                     Rectangle ptRect = paintable.
                        getPrintBounds(row, col, false).getRectangle();

                     linkId = pdf.addLink(
                        hlink, ptRect, context.pgH, context.actions, pageIndex,
                        false);
                  }
               }
            }

            if(row < headerR || col < headerC) {
               int hrow = col < headerC ? row : -1;
               int hcol = row < headerR ? col : -1;
               pdf.startTableHeader(linkId, hrow, hcol);
            }
            else {
               pdf.startTableCell(linkId, row, col);
            }
         }
         else if("end".equals(cmd) || "endText".equals(cmd)) {
            if(row < headerR || col < headerC) {
               pdf.endTableHeader();
            }
            else {
               pdf.endTableCell();
            }
         }
         else if("startBorders".equals(cmd)) {
            pdf.startArtifact();
         }
         else if("endBorders".equals(cmd)) {
            pdf.endArtifact();
         }
      }

      private final TablePaintable paintable;
      private final PDFPrinter pdf;
      private final LinkContext context;
      private final int pageIndex;
      private int lastRow = -1;
   }
}

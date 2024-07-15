/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.pdf;

import inetsoft.graph.internal.GTool;
import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.paging.SwappedEnumeration;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.internal.VsToReportConverter;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * PDF3Generator is a PDF generator that supports generation of Table Of
 * Contents bookmarks in PDF. This class should not be created directly.
 * Call getPDFGenerator() method to get an instance of this class.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class PDF3Generator extends inetsoft.report.io.AbstractGenerator {
   /**
    * Get an instance of a PDF generator.
    */
   public static PDF3Generator getPDFGenerator(OutputStream output) {
      try {
         PDF3Generator gen = new PDF8Generator();
         gen.setOutput(output);
         PDF3Printer pdf = gen.getPrinter();
         String prop = SreeEnv.getProperty("pdf.font.mapping");

         if(prop != null) {
            String[] pairs = Tool.split(prop, ';');

            for(String mapping : pairs) {
               String[] pair = Tool.split(mapping, ':');

               if(pair.length == 2 && pair[0].length() > 0 &&
                  pair[1].length() > 0) {
                  pdf.putFontName(pair[0], pair[1]);
               }
            }
         }

         pdf.setCompressText(SreeEnv.getProperty("pdf.compress.text").
                             equalsIgnoreCase("true"));
         pdf.setCompressImage(SreeEnv.getProperty("pdf.compress.image").
                              equalsIgnoreCase("true"));
         pdf.setAsciiOnly(SreeEnv.getProperty("pdf.output.ascii").
                          equalsIgnoreCase("true"));
         pdf.setMapSymbols(SreeEnv.getProperty("pdf.map.symbols").
                           equalsIgnoreCase("true"));
         pdf.setOpenBookmark(SreeEnv.getProperty("pdf.open.bookmark").
                             equalsIgnoreCase("true"));
         pdf.setOpenThumbnail(SreeEnv.getProperty("pdf.open.thumbnail").
                              equalsIgnoreCase("true"));
         pdf.setEmbedFont(SreeEnv.getProperty("pdf.embed.font").
                          equalsIgnoreCase("true"));
         pdf.setAccessible(
            "true".equalsIgnoreCase(
            SreeEnv.getProperty("accessibility.enabled")));

         String property = SreeEnv.getProperty("pdf.embed.full.fonts");

         if(property != null) {
            pdf.setFullyEmbeddedFonts(Tool.split(property, ','));
         }

         if(pdf instanceof PDF4Printer) {
            ((PDF4Printer) pdf).setEmbedCMap(
               "true".equalsIgnoreCase(SreeEnv.getProperty("pdf.embed.cmap")));
         }

         return gen;
      }
      catch(Throwable e) {
         LOG.error("Failed to initialize PDF generator", e);
      }

      return null;
   }

   /**
    * Create a generator. The setOutput() method must be called before
    * generating the PDF.
    */
   public PDF3Generator() {
   }

   /**
    * Create a generator to the specified output.
    */
   public PDF3Generator(OutputStream out) {
      setOutput(out);
   }

   /**
    * Prepare pdf docuemnt info from style page.
    */
   private static DocumentInfo getDocumentInfo(StylePage pg) {
      DocumentInfo pdfInfo = null;

      boolean hasInfo = (pg.getProperty("report.title") != null) ||
         (pg.getProperty("report.subject") != null) ||
         (pg.getProperty("report.author") != null) ||
         (pg.getProperty("report.keywords") != null) ||
         (pg.getProperty("report.comments") != null) ||
         (pg.getProperty("report.created") != null) ||
         (pg.getProperty("report.modified") != null) ||
         (pg.getProperty("report.modifiedUser") != null);

      if(hasInfo) {
         SimpleDateFormat sdf = Tool.createDateFormat("yyyy-MM-dd HH:mm:ss");

         Object prop = null;
         pdfInfo = new DocumentInfo();

         try {
            if((prop = pg.getProperty("report.title")) != null) {
               pdfInfo.setTitle((String) prop);
            }

            if((prop = pg.getProperty("report.subject")) != null) {
               pdfInfo.setSubject((String) prop);
            }

            if((prop = pg.getProperty("report.author")) != null) {
               pdfInfo.setAuthor((String) prop);
            }

            if((prop = pg.getProperty("report.keywords")) != null) {
               pdfInfo.setKeywords((String) prop);
            }

            if((prop = pg.getProperty("report.comments")) != null) {
               pdfInfo.setComments((String) prop);
            }

            if((prop = pg.getProperty("report.created")) != null) {
               pdfInfo.setCreationDate(sdf.parse((String) prop));
            }

            if((prop = pg.getProperty("report.modified")) != null) {
               pdfInfo.setModDate(sdf.parse((String) prop));
            }

            if((prop = pg.getProperty("report.modifiedUser")) != null) {
               pdfInfo.setModUser((String) prop);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to initialize document info for page", e);
         }
      }

      return pdfInfo;
   }

   /**
    * Prepare pdf docuemnt info from report sheet.
    */
   private static DocumentInfo getDocumentInfo(ReportSheet report) {
      DocumentInfo pdfInfo = null;

      boolean hasInfo = (report.getProperty("report.title") != null) ||
         (report.getProperty("report.subject") != null) ||
         (report.getProperty("report.author") != null) ||
         (report.getProperty("report.keywords") != null) ||
         (report.getProperty("report.comments") != null) ||
         (report.getProperty("report.created") != null) ||
         (report.getProperty("report.modified") != null) ||
         (report.getProperty("report.modifiedUser") != null);

      if(hasInfo) {
         SimpleDateFormat sdf = Tool.createDateFormat("yyyy-MM-dd HH:mm:ss");

         String prop = null;
         pdfInfo = new DocumentInfo();

         try {
            if((prop = report.getProperty("report.title")) != null) {
               pdfInfo.setTitle(prop);
            }

            if((prop = report.getProperty("report.subject")) != null) {
               pdfInfo.setSubject(prop);
            }

            if((prop = report.getProperty("report.author")) != null) {
               pdfInfo.setAuthor(prop);
            }

            if((prop = report.getProperty("report.keywords")) != null) {
               pdfInfo.setKeywords(prop);
            }

            if((prop = report.getProperty("report.comments")) != null) {
               pdfInfo.setComments(prop);
            }

            if((prop = report.getProperty("report.created")) != null) {
               pdfInfo.setCreationDate(sdf.parse(prop));
            }

            if((prop = report.getProperty("report.modified")) != null) {
               pdfInfo.setModDate(sdf.parse(prop));
            }

            if((prop = report.getProperty("report.modifiedUser")) != null) {
               pdfInfo.setModUser(prop);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to initialize document info for report", e);
         }
      }

      return pdfInfo;
   }

   /**
    * Write a collection of pages to text.
    */
   @Override
   public void generate(ReportSheet sheet, Enumeration pages) throws IOException {
      if(pages != null) {
         generate(pages);
      }
      else {
         generate(sheet);
      }
   }

   /**
    * Generate PDF file.
    * @param pages report pages, StylePage.
    */
   public void generate(Vector pages) {
      generate(pages.elements());
   }

   /**
    * Generate PDF file.
    * @param report report object.
    */
   @Override
   public void generate(ReportSheet report) {
      int orient = report.getOrientation();
      Size size = report.getPageSize();

      if(size != null) {
         setPageSize((orient == StyleConstants.LANDSCAPE) ?
                     size.rotate() : size);
      }

      if(docInfo == null) {
         docInfo = getDocumentInfo(report);
      }

      if(encryptInfo == null) {
         preparePDFEncryptInfo(report);
      }

      Enumeration pages = ReportGenerator.generate(report, getPageDimension());
      generate(pages);

      // @by jasons the HP-UX jvm has a GC bug where the finalize method
      // of the enumeration may not be called, so explicitly call dispose
      // when we're done
      if(pages instanceof SwappedEnumeration) {
         ((SwappedEnumeration) pages).dispose();
      }
   }

   /**
    * Generate PDF file from a collection of StylePage objects.
    */
   @Override
   public void generate(Enumeration pages) {
      LinkContext linkContext = new LinkContext();
      linkContext.root = new Node(null);
      linkContext.root.setID(getPrinter().getNextObjectID());
      linkContext.currnode = linkContext.root;
      linkContext.currlevel = 0;
      linkContext.targets = new HashMap<>();
      linkContext.actions = new HashMap<>();
      linkContext.pgH = getPageDimension().height;
      boolean genLink =
         "true".equalsIgnoreCase(SreeEnv.getProperty("pdf.generate.links"));

      cancelled = false;

      try {
         GTool.setIsPDF(true);

         for(int pgIndex = 0; pages.hasMoreElements() && !cancelled; pgIndex++) {
            fireProgressEvent(pgIndex);
            StylePage pg = (StylePage) pages.nextElement();
            Dimension size = pg.getPageDimension();

            if(pg != null && printlayoutmode) {
               pg.setProperty("printlayoutmode" , printlayoutmode + "");
               VsToReportConverter.sortPaintableByZIndex(pg);
            }

            if(pgIndex == 0) {
               setPageSize(new Size(size.width / 72.0, size.height / 72.0));

               if(docInfo == null) {
                  docInfo = getDocumentInfo(pg);
               }

               if(encryptInfo == null) {
                  preparePDFEncryptInfo(pg);
               }

               if(docInfo != null) {
                  getPrinter().setDocumentInfo(docInfo);
               }

               if(encryptInfo != null) {
                  getPrinter().setEncryptInfo(encryptInfo);
               }
            }

            Graphics g = getGraphics();
            int orient = (size.width > size.height) ?
               StyleConstants.LANDSCAPE : StyleConstants.PORTRAIT;

            ((CustomGraphics) g).setOrientation(orient);
            linkContext.pgH = size.height;

            Common.startPage(g, pg);
            pg.paintBg(g, 1.0, 1.0);

            linkContext.lastNode = null;
            linkContext.lastElem = null;

            Rectangle rect = g.getClipBounds();
            int count = pg.getPaintableCount();

            for(int ptIndex = 0; ptIndex < count; ptIndex++) {
               Paintable pt = pg.getPaintable(ptIndex);

               if(pt == null) {
                  continue;
               }

               List<Integer> linkIds = processHyperlinks(pt, linkContext, pgIndex, genLink);

               if(rect == null || pt.getBounds() == null || rect.intersects(pt.getBounds())) {
                  print(pg, pt, ptIndex, g, linkIds, linkContext, pgIndex);
               }
            }

            g.dispose();
            pg.clearCache();
         }

         for(Integer actionid : linkContext.actions.keySet()) {
            String target = linkContext.actions.get(actionid);
            String dest = linkContext.targets.get(target);
            String actionobj;

            if(dest != null) {
               actionobj = "<<\n/S /GoTo\n" + dest + "\n>>";
            }
            else {
               // target doesn't exist, go to the first page (it's safe)
               actionobj = "<<\n/S /GoTo\n/D [" + getPrinter().getPageID(0) +
                  " /FitH " + linkContext.pgH + "]\n>>";
            }

            getPrinter().addObject(actionid, actionobj);
         }

         addBookmark(linkContext.root);

         getPrinter().flush();
         getPrinter().setOutlines(linkContext.root.getID() + " 0 R");
         getPrinter().close();
      }
      finally {
         GTool.setIsPDF(false);
      }
   }

   /**
    * Processes any hyperlinks to be added for the specified paintable.
    *
    * @param pt      the paintable.
    * @param context the current link context.
    * @param pgIndex the page index.
    *
    * @return the list of link annotation identifiers.
    */
   List<Integer> processHyperlinks(Paintable pt, LinkContext context,
                                   int pgIndex, boolean genLink)
   {
      List<Integer> linkIds = new ArrayList<>();

      if(!genLink) {
         return linkIds;
      }

      ReportElement ec = pt.getElement();

      // @by larryl, if a heading elements wraps into multiple lines,
      // only add one entry to the toc
      if(context.lastNode != null && context.lastElem == ec &&
         pt instanceof TextPaintable)
      {
         context.lastNode.setLabel(context.lastNode.getLabel() + " " +
            ((TextPaintable) pt).getText());
      }
      else if(ec instanceof TextContext && pt instanceof TextPaintable) {
         int level = ((TextContext) ec).getLevel();

         if(level >= 0) {
            Node node = new Node(((TextPaintable) pt).getText());

            context.lastNode = node;
            node.setID(getPrinter().getNextObjectID());
            node.setPageID(getPrinter().getPageID(pgIndex));
            node.setPageY(context.pgH - pt.getBounds().y);

            if(level > context.currlevel) {
               context.currnode.addChild(node);
            }
            else if(level == context.currlevel) {
               context.currnode = context.currnode.getParent();

               if(context.currnode == null) {
                  context.currnode = context.root;
               }

               context.currnode.addChild(node);
            }
            else { // level < currlevel
               for(int k = 0; k < context.currlevel - level + 1 &&
                  context.currnode != null; k++)
               {
                  context.currnode = context.currnode.getParent();
               }

               if(context.currnode == null) {
                  context.currnode = context.root;
               }

               context.currnode.addChild(node);
            }

            context.currnode = node;
            context.currlevel = level;

            Hyperlink.Ref hlink;
            hlink = ((TextPaintable) pt).getHyperlink();

            if(hlink != null && (hlink.getLinkType() ==
               Hyperlink.WEB_LINK ||
               hlink.getLink().startsWith("#"))) {
               Integer linkId = getPrinter().addLink(
                  hlink, pt.getBounds(), context.pgH, context.actions, pgIndex,
                  false);

               if(linkId != null) {
                  linkIds.add(linkId);
               }
            }
         }
         else {
            context.lastNode = null;
         }
      }
      else if(pt instanceof TextPaintable ||
         pt instanceof PainterPaintable)
      {
         context.lastNode = null;
         Hyperlink.Ref hlink;

         if(pt instanceof TextPaintable) {
            hlink = ((TextPaintable) pt).getHyperlink();
         }
         else {
            hlink = ((PainterPaintable) pt).getHyperlink();
         }

         if(hlink != null && (hlink.getLinkType() ==
            Hyperlink.WEB_LINK|| hlink.getLink().startsWith("#"))) {
            Integer linkId = getPrinter().addLink(
               hlink, pt.getBounds(), context.pgH, context.actions, pgIndex,
               false);

            if(linkId != null) {
               linkIds.add(linkId);
            }
         }
      }
      else if(pt instanceof TablePaintable) {
         context.lastNode = null;
         TablePaintable pt2 = (TablePaintable) pt;
         Rectangle reg = pt2.getTableRegion();
         int headerR = pt2.getTable().getHeaderRowCount();
         int headerC = pt2.getTable().getHeaderColCount();

         for(int row = 0; row < reg.y + reg.height; row++) {
            if(row >= headerR && row < reg.y) {
               row = reg.y - 1; // optimization
               continue;
            }

            for(int col = 0; col < reg.x + reg.width; col++) {
               if(col >= headerC && col < reg.x) {
                  col = reg.x - 1; // optimization;
                  continue;
               }

               Hyperlink.Ref hlink = pt2.getHyperlink(row, col);
               Rectangle ptRect = null;

               if(hlink != null && (hlink.getLinkType() == Hyperlink.WEB_LINK ||
                  hlink.getLink().startsWith("#")))
               {
                  ptRect = ((TablePaintable) pt).getPrintBounds(
                     row, col, false).getRectangle();
                  Integer linkId = getPrinter().addLink(
                     hlink, ptRect, context.pgH, context.actions, pgIndex,
                     false);

                  if(linkId != null) {
                     linkIds.add(linkId);
                     continue;
                  }
               }

               Hyperlink.Ref[] hlinks = pt2.getDrillHyperlinks(row, col);

               for(int i = 0; i < hlinks.length; i++) {
                  if(hlinks[i] != null && (hlinks[i].getLinkType() ==
                     Hyperlink.WEB_LINK || hlinks[i].getLink().startsWith("#")))
                  {
                     String link = hlinks[i].getLink();

                     if(link.toLowerCase().startsWith("www.")) {
                        link = "http://" + link;
                     }

                     Hyperlink.Ref nlink = new Hyperlink.Ref(
                        link, Hyperlink.WEB_LINK);
                     nlink.setTargetFrame(hlinks[i].getTargetFrame());

                     if(ptRect == null) {
                        ptRect = ((TablePaintable) pt).getPrintBounds(
                           row, col, false).getRectangle();
                     }

                     Integer linkId = getPrinter().addLink(nlink, ptRect,
                        context.pgH, context.actions, pgIndex, false);

                     if(linkId != null) {
                        linkIds.add(linkId);
                        break;
                     }
                  }
               }
            }
         }
      }

      // set chart area paintables
      if(pt instanceof LinkedShapePainterPaintable) {
         LinkedShapePainterPaintable pt2 =
            (LinkedShapePainterPaintable) pt;
         Enumeration areas = pt2.getHyperlinkAreas();
         Rectangle base = pt2.getBounds();
         Map<Hyperlink.Ref, Rectangle> maps = new HashMap<>();

         while(areas.hasMoreElements()) {
            Shape shape = (Shape) areas.nextElement();
            Hyperlink.Ref hlink = pt2.getHyperlink(shape);

            if(hlink != null &&
               (hlink.getLinkType() == Hyperlink.WEB_LINK ||
                  hlink.getLink().startsWith("#"))) {
               Rectangle box = new Rectangle(shape.getBounds());

               box.x += base.x;
               box.y += base.y;
               maps.put(hlink, box);
            }
         }

         // fix bug1300957927646 sort the hyperlink area
         Object[] values = maps.values().toArray();

         Arrays.sort(values, new Comparator<Object>() {
            @Override
            public int compare(Object obj1, Object obj2) {
               Rectangle r1 = (Rectangle) obj1;
               Rectangle r2 = (Rectangle) obj2;

               if(r1.intersects(r2)) {
                  return r1.height > r2.height ? 1 : 0;
               }

               return r2.contains(r1) ? 1 : 0;
            }

            @Override
            public boolean equals(Object obj) {
               return true;
            }
         });

         for(Hyperlink.Ref hlink : maps.keySet()) {
            Integer linkId = getPrinter().addLink(
               hlink, maps.get(hlink), context.pgH, context.actions, pgIndex,
               false);

            if(linkId != null) {
               linkIds.add(linkId);
            }
         }
      }

      context.lastElem = ec;
      return linkIds;
   }

   /**
    * Prints an paintable to the PDF.
    *
    * @param page        the page containing the paintable.
    * @param pt          the paintable.
    * @param index       the index of the paintable on the page.
    * @param g           the PDF graphics context.
    * @param linkIds     the identifiers of the link annotations associated with
    *                    the paintable.
    * @param linkContext the link context.
    * @param pgIndex the index of the current printing page
    */
   void print(StylePage page, Paintable pt, int index, Graphics g,
              List<Integer> linkIds, LinkContext linkContext, int pgIndex)
   {
      pt.paint(g);
   }

   /**
    * Prepare the pdf encrypt info.
    */
   private void preparePDFEncryptInfo(ReportSheet report) {
      String prop;
      boolean hasInfo =
         (report.getProperty("pdf.password.owner") != null) ||
         (report.getProperty("pdf.password.user") != null) ||
         (report.getProperty("pdf.permission.print") != null) ||
         (report.getProperty("pdf.permission.copy") != null) ||
         (report.getProperty("pdf.permission.change") != null) ||
         (report.getProperty("pdf.permission.add") != null);

      if(hasInfo) {
         encryptInfo = new PDFEncryptInfo();

         try {
            if((prop =
                report.getProperty("pdf.password.owner")) != null) {
               encryptInfo.setOwnerPassword(prop);
            }

            if((prop =
                report.getProperty("pdf.password.user")) != null) {
               encryptInfo.setUserPassword(prop);
            }

            if((prop =
                report.getProperty("pdf.permission.print")) != null) {
               encryptInfo.setPrintPermission("true".equals(prop));
            }

            if((prop =
                report.getProperty("pdf.permission.copy")) != null) {
               encryptInfo.setCopyPermission("true".equals(prop));
            }

            if((prop =
                report.getProperty("pdf.permission.change")) != null) {
               encryptInfo.setChangePermission("true".equals(prop));
            }

            if((prop =
                report.getProperty("pdf.permission.add")) != null) {
               encryptInfo.setAddPermission("true".equals(prop));
            }
         }
         catch(Exception e) {
            LOG.error("Failed to initialize PDF encryption for report", e);
         }
      }
   }

   /**
    * Prepare the pdf encrypt info.
    */
   private void preparePDFEncryptInfo(StylePage pg) {
      Object prop;

      boolean hasInfo =
         (pg.getProperty("pdf.password.owner") != null) ||
         (pg.getProperty("pdf.password.user") != null) ||
         (pg.getProperty("pdf.permission.print") != null) ||
         (pg.getProperty("pdf.permission.copy") != null) ||
         (pg.getProperty("pdf.permission.change") != null) ||
         (pg.getProperty("pdf.permission.add") != null);

      if(hasInfo) {
         encryptInfo = new PDFEncryptInfo();

         try {
            if((prop =
               pg.getProperty("pdf.password.owner")) != null) {
               encryptInfo.setOwnerPassword((String)prop);
            }

            if((prop =
               pg.getProperty("pdf.password.user")) != null) {
               encryptInfo.setUserPassword((String)prop);
            }

            if((prop =
               pg.getProperty("pdf.permission.print")) != null) {
               encryptInfo.setPrintPermission("true".equals(prop));
            }

            if((prop =
               pg.getProperty("pdf.permission.copy")) != null) {
               encryptInfo.setCopyPermission("true".equals(prop));
            }

            if((prop =
               pg.getProperty("pdf.permission.change")) != null) {
               encryptInfo.setChangePermission("true".equals(prop));
            }

            if((prop =
               pg.getProperty("pdf.permission.add")) != null) {
               encryptInfo.setAddPermission("true".equals(prop));
            }
         }
         catch(Exception e) {
            LOG.error("Failed to initialize PDF encryption for page", e);
         }
      }
   }

   private void addBookmark(Node node) {
      getPrinter().addBookmark(node);
   }

   /**
    * Get the PDF3Printer used inside this generator.
    */
   public PDF3Printer getPrinter() {
      if(printer == null) {
         printer = new PDF3Printer(getOutput());
      }

      return printer;
   }

   /**
    * Get the graphics object for a new page.
    */
   protected Graphics getGraphics() {
      return getPrinter();
   }

   /**
    * Get the graphics object for a new page.
    */
   protected Dimension getPageDimension() {
      if(job == null) {
         job = getPrinter().getPrintJob();
      }

      return job.getPageDimension();
   }

   /**
    * Set the output page size.
    * @param pgsize page size in inches.
    */
   public void setPageSize(Size pgsize) {
      // @by billh, fix customer bug bug1290446180988. PDF size should not
      // exceed 200 inches. For more information, please refer to url:
      // http://www.amyuni.com/forum/viewtopic.php?f=14&t=2482
      if(pgsize.width >= MAX_SIZE || pgsize.height >= MAX_SIZE) {
         LOG.warn(
            "PDF Page size should not exceed " + MAX_SIZE + " inches!");
         pgsize = new Size(pgsize);
         pgsize.width = Math.min(pgsize.width, MAX_SIZE);
         pgsize.height = Math.min(pgsize.height, MAX_SIZE);
      }

      ((PDFDevice) getGraphics()).setPageSize(pgsize);
   }

   /**
    * Get the output page size.
    */
   public Size getPageSize() {
      return getPrinter().getPageSize();
   }

   /*
    * Sets the option of selective bookmarking. If set, heading elements
    * within subreports will not be written out as bookmarks
    */
   public void setSelectiveBookmarks(boolean selectiveBookmarks) {
      this.selectiveBookmarks = selectiveBookmarks;
   }

   /**
    * Indicates whether selective bookmarking is turned on
    */
   public boolean isSelectiveBookmarks() {
      return this.selectiveBookmarks;
   }

   /**
    * Sets whether the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @param printOnOpen <tt>true</tt> to print when opened.
    */
   public void setPrintOnOpen(boolean printOnOpen) {
      getPrinter().setPrintOnOpen(printOnOpen);
   }

   /**
    * Sets the print report ID when the generated PDF
    * is opened.
    *
    * @param id to print when opened.
    */
   public void setReportID(String id) {
      getPrinter().setReportID(id);
   }

   /**
    * Determines if the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @return <tt>true</tt> to print when opened.
    */
   public boolean isPrintOnOpen() {
      return getPrinter().isPrintOnOpen();
   }

   /**
    * Gets the flag that determines if an accessible PDF file is generated. By
    * default, this option is disabled.
    * <p>
    * Accessible PDF files will be larger and may take longer to generate.
    *
    * @return <tt>true</tt> to generate an accessible PDF; <tt>false</tt>
    *         otherwise.
    */
   public boolean isAccessible() {
      return getPrinter().isAccessible();
   }

   /**
    * Sets the flag that determines if an accessible PDF file is generated. By
    * default, this option is disabled.
    * <p>
    * Accessible PDF files will be larger and may take longer to generate.
    *
    * @param accessible <tt>true</tt> to generate an accessible PDF;
    *                   <tt>false</tt> otherwise.
    */
   public void setAccessible(boolean accessible) {
      getPrinter().setAccessible(accessible);
   }

   /**
    * Set printlayoutmode.
    * @param printlayoutmode if true the target exporting report is generated
    * for exporting a vs applied the printlayout.
    */
   public void setPrintLayoutMode(boolean printlayoutmode) {
      this.printlayoutmode = printlayoutmode;
   }

   private boolean printlayoutmode = false;
   private static final float MAX_SIZE = 200;
   private PDFEncryptInfo encryptInfo = null;
   private PDF3Printer printer = null;
   private PrintJob job = null;
   private boolean selectiveBookmarks = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(PDF3Generator.class);

   static final class LinkContext {
      Node root;
      Node currnode;
      Node lastNode;
      ReportElement lastElem;
      int currlevel;
      Map<Integer, String> actions;
      Map<String, String> targets;
      int pgH;
   }
}

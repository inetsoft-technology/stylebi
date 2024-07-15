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
package inetsoft.report;

import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.*;
import inetsoft.report.pdf.PDFDevice;
import inetsoft.report.pdf.PDFEncryptInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.*;
import java.awt.RenderingHints.Key;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.awt.print.PrinterJob;
import java.io.*;
import java.security.MessageDigest;
import java.text.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PDFPrinter can be used to generate a PDF file from a report. This PDF
 * printer simulates an actual printer, and generates a very accurately
 * formatted PDF document.
 * <P>
 * This class provides a simple implementation. For full featured PDF
 * generation, use inetsoft.report.pdf.PDF3Generator.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class PDFPrinter extends Graphics2D implements PDFDevice {
   /**
    * Page height in points.
    */
   protected int pageheight = 792;
   /**
    * Page width in points.
    */
   protected int pagewidth = 612;
   /**
    * Page resolution.
    */
   public static final int RESOLUTION = 72;
   /**
    * hexadecimal digits
    */
   static final char[] hd = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                             'A', 'B', 'C', 'D', 'E', 'F'};
   /**
    * number of chars in a full row of pixel data
    */
   static final int charsPerRow = 12 * 6;
   /**
    * Output stream where pdf goes
    */
   CountWriter os;
   /**
    * The current color
    */
   Color clr = Color.black;
   /**
    * The background color of the current widget.
    * It's up to the client software to set this correctly!
    */
   Color backClr = Color.white;
   Rectangle default_cliprect = new Rectangle(0, 0, pagewidth, pageheight);
   protected boolean outOfMaxPageSize = false;

   public boolean isOutOfMaxPageSize() {
      return outOfMaxPageSize;
   }

   public void setOutOfMaxPageSize(boolean outOfMaxPageSize) {
      this.outOfMaxPageSize = outOfMaxPageSize;
   }

   /**
    * Create an empty PDFPrinter. The setOutput() and startDoc() must
    * be called before the PDFPrinter can be used.
    */
   public PDFPrinter() {
      clipping = new Rectangle2D.Double(default_cliprect.x, default_cliprect.y,
         default_cliprect.width, default_cliprect.height);

      try {
         pg = new PDFWriter(pgBuf);
         others = new CountWriter(othersBuf);
      }
      catch(Exception e) {
         LOG.error("Failed to initialize PDF printer", e);
      }
   }

   /**
    * Construct a PDFPrinter graphics content. The pdf output is
    * sent to the specified file.
    * @param outf output file.
    */
   public PDFPrinter(File outf) throws IOException {
      this(new FileOutputStream(outf));
   }

   /**
    * Constructs a new PDFPrinter Object. Unlike regular Graphics objects,
    * PDFPrinter contexts can be created directly.
    * @param o Output stream for PostScript output
    * @see #create
    */
   public PDFPrinter(OutputStream o) {
      this();
      setOutput(o);
      startDoc();
      GTool.setIsPDF(true);
   }

   /**
    * Set the output stream for the PDF output.
    */
   @Override
   public void setOutput(OutputStream o) {
      try {
         os = new CountWriter(o);
      }
      catch(IOException e) {
         LOG.error("Failed to set output stream", e);
      }

      started = false;
   }

   /**
    * Set whether to compress the text object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   @Override
   public void setCompressText(boolean comp) {
      compressText = comp;
   }

   /**
    * Check if compression is on.
    * @return true if text objects are compressed.
    */
   @Override
   public boolean isCompressText() {
      return compressText;
   }

   /**
    * Set whether the output should only contain 7 bits ascii code only.
    * It defaults to false.
    * @param ascii output ascii only.
    */
   @Override
   public void setAsciiOnly(boolean ascii) {
      this.ascii = ascii;
   }

   /**
    * Check if the output is ascii only.
    * @return true if ascii only.
    */
   @Override
   public boolean isAsciiOnly() {
      return ascii;
   }

   /**
    * Set whether to compress the image object and streams in the PDF.
    * Currently only Zip compression is supported, as the consequence
    * the output is only compatible with Acrobat 3.0 and later versions.
    * By default this is true.
    * @param comp compression option.
    */
   @Override
   public void setCompressImage(boolean comp) {
      compressImg = comp;
   }

   /**
    * Check if compression is on.
    * @return true if image objects are compressed.
    */
   @Override
   public boolean isCompressImage() {
      return compressImg;
   }

   /**
    * Set whether to map unicode characters for greek and math symbols to
    * symbol font characters.
    */
   @Override
   public void setMapSymbols(boolean map) {
      mapSymbol = map;
   }

   /**
    * Check if symbol mapping is enabled.
    */
   @Override
   public boolean isMapSymbols() {
      return mapSymbol;
   }

   /**
    * Set the "openBookmark" flag (which indicates whether bookmarks should
    * be immediately displayed when the PDF file is opened).
    */
   public void setOpenBookmark(boolean openBookmark) {
      this.openBookmark = openBookmark;
   }

   /**
    * Get the value of the "openBookmark" flag.
    */
   public boolean isOpenBookmark() {
      return this.openBookmark;
   }

   /**
    * Set the "openThumbnail" flag (which indicates whether thumbnails should
    * be immediately displayed when the PDF file is opened).  This option and
    * the "openBookmark" option are mutually exclusive, with "setOpenBookmark()"
    * having priority if both are called.
    */
   public void setOpenThumbnail(boolean openThumbnail) {
      this.openThumbnail = openThumbnail;
   }

   /**
    * Get the value of the "openThumbnail" flag.
    */
   public boolean isOpenThumbnail() {
      return this.openThumbnail;
   }

   /**
    * Sets report ID when the generated PDF is opened.
    *
    * @param id when opened.
    * @hidden
    */
   public void setReportID(String id) {
      this.reportID = id;
   }

   /**
    * Sets the "PrintScaling" flag. This determines if the PDF viewer uses the
    * application defaults for print scaling or none.
    *
    * @param printScaling <code>true</code> to set print scaling to the
    *                     application default; <code>false</code> for none.
    */
   public void setPrintScaling(boolean printScaling) {
      this.printScaling = printScaling;
   }

   /**
    * Gets the "PrintScaling" flag. This determines if the PDF viewer uses the
    * application defaults for print scaling or none.
    *
    * @return  <code>true</code> to set print scaling to the application
    *          default; <code>false</code> for none.
    */
   public boolean isPrintScaling() {
      return printScaling;
   }

   /**
    * Sets whether the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @param printOnOpen <tt>true</tt> to print when opened.
    * @hidden
    */
   public void setPrintOnOpen(boolean printOnOpen) {
      this.printOnOpen = printOnOpen;
   }

   /**
    * Determines if the print dialog should be displayed when the generated PDF
    * is opened.
    *
    * @return <tt>true</tt> to print when opened.
    * @hidden
    */
   public boolean isPrintOnOpen() {
      return printOnOpen;
   }

   /**
    * Gets the flag that determines if an accessible PDF file is generated. By
    * default, this option is disabled.
    * <p>
    * Accessible PDF files will be larger and may take longer to generate.
    *
    * @return <tt>true</tt> to generate an accessible PDF; <tt>false</tt>
    *         otherwise.
    *
    * @since 11.4
    */
   public boolean isAccessible() {
      return accessible;
   }

   /**
    * Sets the flag that determines if an accessible PDF file is generated. By
    * default, this option is disabled.
    * <p>
    * Accessible PDF files will be larger and may take longer to generate.
    *
    * @param accessible <tt>true</tt> to generate an accessible PDF;
    *                   <tt>false</tt> otherwise.
    *
    * @since 11.4
    */
   public void setAccessible(boolean accessible) {
      this.accessible = accessible;
   }

   /**
    * Gets the locale of the report being rendered.
    *
    * @return the report locale.
    *
    * @since 11.4
    */
   public Locale getReportLocale() {
      return reportLocale;
   }

   /**
    * Sets the locale of the report being rendered.
    *
    * @param reportLocale the report locale.
    *
    * @since 11.4
    */
   public void setReportLocale(Locale reportLocale) {
      this.reportLocale = reportLocale;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startParagraph(Integer linkId) {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         StructureNode element = new StructureNode();
         element.parent = structureParent;
         element.mcid = mcid++;
         element.part = structurePart;

         if(linkId != null) {
            element.type = StructureType.Link;
            element.reference = linkId;
            structureTree.kids.add(element);
            pg.print("/Link ");
         }
         else {
            element.type = StructureType.P;
            pg.print("/P ");
         }

         pg.println("<</MCID " + element.mcid + ">>BDC");
         structureParent.kids.add(element);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endParagraph() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startHeading(int level) {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         StructureNode element = new StructureNode();
         element.mcid = mcid++;
         element.parent = structureParent;
         element.type = StructureType.valueOf("H" + level);
         element.part = structurePart;
         pg.println(
            "/" + element.type.name() + " <</MCID " + element.mcid + ">>BDC");
         structureParent.kids.add(element);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endHeading() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startFigure(String altText) {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         StructureNode element = new StructureNode();
         element.mcid = mcid++;
         element.parent = structureParent;
         element.type = StructureType.Figure;
         element.altText = altText;
         element.part = structurePart;

         pg.println("/Figure <</MCID " + element.mcid + ">>BDC");

         structureParent.kids.add(element);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endFigure() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startTable() {
      if(isAccessible()) {
         StructureNode table = new StructureNode();
         table.parent = structureParent;
         table.type = StructureType.Table;
         table.part = structurePart;
         table.kids = new ArrayList<>();
         structureParent.kids.add(table);
         structureParent = table;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endTable() {
      if(isAccessible()) {
         StructureNode parent = structureParent;

         while(parent.type != StructureType.Part) {
            parent = parent.parent;
         }

         structureParent = parent;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startTableRow() {
      if(isAccessible()) {
         checkTextObj(false);

         StructureNode parent = structureParent;

         if(parent.type == StructureType.TR) {
            parent = parent.parent;
         }

         StructureNode row = new StructureNode();
         row.parent = parent;
         row.type = StructureType.TR;
         row.part = structurePart;
         row.kids = new ArrayList<>();
         parent.kids.add(row);

         structureParent = row;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startTableHeader(Integer linkId, int row, int col) {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         StructureNode element = new StructureNode();
         element.mcid = mcid++;
         element.parent = structureParent;
         element.type = StructureType.TH;
         element.part = structurePart;

         if(row >= 0 || col >= 0) {
            String scope;

            if(row >= 0 && col >= 0) {
               scope = "/Both";
            }
            else if(row >= 0) {
               scope = "/Row";
            }
            else {
               scope = "/Column";
            }

            element.setAttribute("/O", "/Table");
            element.setAttribute("/Scope", scope);
         }

         if(linkId != null) {
            element.reference = linkId;
            structureTree.kids.add(element);
         }

         pg.println("/TH <</MCID " + element.mcid + ">>BDC");
         structureParent.kids.add(element);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endTableHeader() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startTableCell(Integer linkId, int row, int col) {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         StructureNode element = new StructureNode();
         element.mcid = mcid++;
         element.parent = structureParent;
         element.type = StructureType.TD;
         element.part = structurePart;

         if(linkId != null) {
            element.reference = linkId;
            structureTree.kids.add(element);
         }

         pg.println("/TD <</MCID " + element.mcid + ">>BDC");
         structureParent.kids.add(element);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endTableCell() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void startArtifact() {
      if(isAccessible()) {
         startPage();
         checkTextObj(false);

         pg.println("/Artifact");
         pg.println("BMC");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void endArtifact() {
      if(isAccessible()) {
         checkTextObj(false);
         pg.println("EMC");
      }
   }

   /**
    * Set the document info.
    */
   public void setDocumentInfo(DocumentInfo info) {
      this.info = info;
   }

   /**
    * Get the document info.
    */
   public DocumentInfo getDocumentInfo() {
      return info;
   }

   /**
    * Set the PDF document encryption infomation.
    */
   public void setEncryptInfo(PDFEncryptInfo info) {
      this.encrypt = info;
   }

   /**
    * Get the PDF document encryption infomation.
    */
   public PDFEncryptInfo getEncryptInfo() {
      return encrypt;
   }

   /**
    * Check if the PDF document should be encrypted.
    */
   public boolean isEncrypted() {
      return (encrypt != null && encrypt.isEncrypted() && isRC4Available());
   }

   /**
    * Get the current page size.
    */
   @Override
   public Size getPageSize() {
      return new Size(pagewidth / (double) RESOLUTION,
         pageheight / (double) RESOLUTION);
   }

   /**
    * Set the page size in inches.
    * @param width page width.
    * @param height page height.
    */
   public void setPageSize(double width, double height) {
      setPageSize((int) (RESOLUTION * width), (int) (RESOLUTION * height));
   }

   /**
    * Set the page size using exact dimensions in pixels.
    * @param width page width.
    * @param height page height.
    */
   public void setPageSize(int width, int height) {
      pagewidth = width;
      pageheight = height;
      default_cliprect = new Rectangle(0, 0, pagewidth, pageheight);
      // this is necessary otherwise the first page may not be correct
      // because getClipBounds may be called before startPage()
      clipping = new Rectangle2D.Double(default_cliprect.x, default_cliprect.y,
                                        default_cliprect.width, default_cliprect.height);
   }

   /**
    * Set the page size in inches. Common paper sizes are defined
    * as constants in StyleConstants.
    * @param size Size object in inches.
    */
   @Override
   public void setPageSize(Size size) {
      setPageSize(size.width, size.height);
   }

   /**
    * Set page orientation.
    * @param orient orientation, StyleConstants.PORTRAIT or
    * StyleConstants.LANDSCAPE.
    */
   @Override
   public void setOrientation(int orient) {
      if(orient == StyleConstants.PORTRAIT && pagewidth > pageheight ||
         orient == StyleConstants.LANDSCAPE && pagewidth < pageheight)
      {
         int h = pageheight;

         pageheight = pagewidth;
         pagewidth = h;
         default_cliprect = new Rectangle(0, 0, pagewidth, pageheight);
         clipping = new Rectangle2D.Double(default_cliprect.x,
            default_cliprect.y, default_cliprect.width,
            default_cliprect.height);
      }
   }

   /**
    * Get the pdf font name corresponding to the Java font name.
    * @param font Java font.
    * @return pdf font name.
    */
   @Override
   public String getFontName(Font font) {
      lock.lock();

      try {
         if(font.equals(lastFn)) {
            return lastPDFFn;
         }
      }
      finally {
         lock.unlock();
      }

      String lastName = pdfFnCache.get(font);

      if(lastName != null) {
         lock.lock();

         try {
            lastFn = font;
            return lastPDFFn = lastName;
         }
         finally {
            lock.unlock();
         }
      }

      String javaName0 = font.getName();
      String javaName = javaName0.toLowerCase();
      String fn = fontmap.get(javaName);

      // if a font (e.g. Verdana) is defined as the font to map to, other font (e.g. Arial) will
      // be mapped to (Verdana) and used in pdf. however, when Verdana is used, it will be
      // mapped to a base14 font (Courier) in pdf, which is unreasonable. this logic would
      // imply an implicit mapping (Verdana -> Verdana). (59712)
      if(fn == null && (fontmap.values().contains(javaName0) ||
                        fontmap.values().contains(javaName)))
      {
         fn = javaName;
      }

      if(isBase14Font(javaName)) {
         fn = font.getName();
      }
      else if(fn == null) {
         int dot = javaName.indexOf('.');

         if(dot > 0) {
            fn = fontmap.get(javaName.substring(0, dot));
         }
      }

      // roboto is a web font and won't be found as a truetype, should not
      // change it to Courier. (50846)
      // @by jasonshobe, this actually does nothing with the default font family ("Roboto"),
      // because javaName has toLowerCase applied to it.
      if(javaName.equals(SreeEnv.getProperty("default.font.family"))) {
         fn = javaName;
      }

      String psFontName = (fn == null) ? "Courier" : fn;
      int javaStyle = font.getStyle();

      if((javaStyle & Font.BOLD) != 0 && (javaStyle & Font.ITALIC) != 0) {
         psFontName += oblique.contains(psFontName) ?
            "-BoldOblique" :
            "-BoldItalic";
      }
      else if((javaStyle & Font.BOLD) != 0) {
         psFontName += "-Bold";
      }
      else if((javaStyle & Font.ITALIC) != 0) {
         psFontName += oblique.contains(psFontName) ? "-Oblique" : "-Italic";
      }
      else if(psFontName.equals("Times")) {
         psFontName += "-Roman";
      }

      if(pdfFnCache.size() > 50) {
         pdfFnCache.clear();
      }

      pdfFnCache.put(font, psFontName);

      lock.lock();

      try {
         lastPDFFn = psFontName;
         lastFn = font;
      }
      finally {
         lock.unlock();
      }

      return psFontName;
   }

   /**
    * Add the mapping for the pdf font name corresponding to the
    * Java font name.
    * @param javaName Java font name.
    * @param psFontName mapped font name.
    */
   @Override
   public void putFontName(String javaName, String psFontName) {
      fontmap.put(javaName.toLowerCase(), psFontName);
   }

   /**
    * Get the wide string.
    */
   protected String getTextString(Object obj, boolean wide) {
      if(obj == null) {
         return "()";
      }

      String[] lines = Tool.split(obj.toString(), '\n');
      byte[] bs = getTextBytes(lines, wide);
      return getTextHexString(bs);
   }

   /**
    * Get the wide string.
    */
   protected String getTextString(Object obj) {
      return getTextString(obj, false);
   }

   /**
    * Get the text string representation. Use Hex String.
    */
   protected String getTextString(int onum, int gnum, Object obj) {
      if(obj == null) {
         return "()";
      }

      containsWideString = isWideCharString(obj.toString(), false);
      String[] lines = Tool.split(obj.toString(), '\n');
      byte[] bs = getTextBytes(lines, false);
      containsWideString = false;

      return getTextHexString(encrypt(onum, gnum, bs));
   }


   /**
    * Get the Hex string representation of a byte array.
    */
   private String getTextHexString(byte[] bytes) {
      return "<" + toHex(bytes) + ">";
   }

   /**
    * Get the unicode string byte array with leading FE FF.
    */
   private byte[] getTextBytes(String[] lines, boolean wide) {
      byte[] out = new byte[] {};

      if(containsWideString) {
         byte[] mark = new byte[] {(byte) 0xFE, (byte) 0xFF};
         out = paddingByteArray(out, out.length + mark.length, mark);
      }

      for(int i = 0; i < lines.length; i++) {
         byte[] bytes = getTextBytes(lines[i], i < lines.length -1,
                                     wide);
         out = paddingByteArray(out, out.length + bytes.length, bytes);
      }

      return out;
   }

   /**
    * Get the unicode byte array.
    */
   private byte[] getTextBytes(String str, boolean newline, boolean wide) {
      byte[] out = new byte[] {};
      byte[] newlbyte = new byte[] {(byte) 0x0D};

      if(containsWideString || wide) {
         newlbyte = new byte[] {(byte) 0x00, (byte) 0x0D};
         byte[] bytes = new byte[str.length() * 2];

         for(int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            bytes[i * 2] = (byte) ((c & 0xFF00) >> 8);
            bytes[i * 2 + 1] = (byte) (c & 0x00FF);
         }

         out = paddingByteArray(out, out.length + bytes.length, bytes);
      }
      else {
         out = str.getBytes();
      }

      if(newline) {
         out = paddingByteArray(out, out.length + newlbyte.length, newlbyte);
      }

      return out;
   }

   /**
    * Updates the set of characters used for the current font to include the
    * characters of the specified string.
    *
    * @param s the string to add.
    */
   protected void updateCharacterSet(String s) {
      // NO-OP
   }

   /**
    * Check if a string contains unicode.
    */
   protected final boolean isWideCharString(String str, boolean font) {
      if(font) {
         Boolean wide = wfonts.get(str);

         if(wide != null) {
            return wide;
         }
      }

      int len = str.length();

      for(int i = 0; i < len; i++) {
         char c = str.charAt(i);

         if(c > 255) {
            if(font) {
               wfonts.put(str, true);
            }

            return true;
         }
      }

      if(font) {
         wfonts.put(str, false);
      }

      return false;
   }

   /**
    * Write the AP object of a field.
    */
   private void writeAP(int apId, Rectangle box, String fn, String zadb,
                        int rotation, String str) {
      int sizeId = getNextObjectID(); // ap size

      others.markObject(apId);
      others.println(apId + " 0 obj");
      others.println("<<");
      others.println("/Length " + sizeId + " 0 R");
      others.println("/Subtype /Form");
      others.println("/BBox [ 0 0 " + box.width + " " + box.height + "]");

      if(rotation == 90) {
         others.println("/Matrix [ 0 1 -1 0 0 0 ]");
      }
      else if(rotation == 270) {
         others.println("/Matrix [ 0 -1 1 0 0 0 ]");
      }

      others.println("/Resources << /ProcSet [ /PDF /Text ]");
      others.print("/Font << /" + fn + " " + fnObj.get(fn));

      if(zadb != null) {
         others.print(" /" + zadb + " " + fnObj.get(zadb));
      }

      others.println(" >>");
      others.println(">>");
      others.println(">>");

      others.println("stream");
      int osize = others.getOffset();

      if(isEncrypted()) {
         try {
            byte[] coded = encrypt(apId, 0, str.getBytes());
            others.write(coded);
         }
         catch(Exception e) {}
      }
      else {
         others.println(str);
      }

      int objlen = others.getOffset() - osize;
      others.println("endstream");
      others.println("endobj");
      others.markObject(sizeId);
      others.println(sizeId + " 0 obj");
      others.println(objlen + "");
      others.println("endobj");
   }

   /**
    * Get the current output size.
    * @return size in bytes.
    */
   public int getOutputSize() {
      return os.getOffset();
   }

   /**
    * Get the printjob object associated with this object, which contains
    * the page size and resolution information.
    * @return print job object.
    */
   @Override
   public PrintJob getPrintJob() {
      if(job == null) {
         job = new Printer();
      }

      return job;
   }

   /**
    * Printer class implements the PrintJob to supply the page info.
    */
   class Printer extends PrintJob implements Serializable {
      /**
       * Gets a Graphics object that will draw to the next page.
       * The page is sent to the printer when the graphics
       * object is disposed.  This graphics object will also implement
       * the PrintGraphics interface.
       * @see PrintGraphics
       */
      @Override
      public Graphics getGraphics() {
         return PDFPrinter.this;
      }

      /**
       * Returns the dimensions of the page in pixels.
       * The resolution of the page is chosen so that it
       * is similar to the screen resolution.
       */
      @Override
      public Dimension getPageDimension() {
         return new Dimension(pagewidth, pageheight);
      }

      /**
       * Returns the resolution of the page in pixels per inch.
       * Note that this doesn't have to correspond to the physical
       * resolution of the printer.
       */
      @Override
      public int getPageResolution() {
         return RESOLUTION;
      }

      /**
       * Returns true if the last page will be printed first.
       */
      @Override
      public boolean lastPageFirst() {
         return false;
      }

      /**
       * Ends the print job and does any necessary cleanup.
       */
      @Override
      public void end() {
         close();
      }
   }

   /**
    * Creates a new PDFPrinter Object that is a copy of the original
    * PDFPrinter Object. The Graphics object MUST be disposed explicitly
    * after its use. The Graphics object MUST be disposed before the
    * original PDFPrinter graphics object can be used.
    */
   @Override
   public Graphics create() {
      try {
         startPage();
         debug(pg, "%create");
         PDFPrinter ps = (PDFPrinter) clone();
         return ps;
      }
      catch(Exception e) {
         LOG.error("Failed to create PDF graphics", e);
      }

      return null;
   }

   /**
    * Creates a new Graphics Object with the specified parameters,
    * based on the original
    * Graphics Object.
    * This method translates the specified parameters, x and y, to
    * the proper origin coordinates and then clips the Graphics Object to the
    * area.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the area
    * @param height the height of the area
    * @see #translate
    */
   @Override
   public Graphics create(int x, int y, int width, int height) {
      PDFPrinter g = (PDFPrinter) create();

      g.ptrans.translate(x, y);
      g.checkTextObj(false);
      // don't use the translate() or clipRect() methods otherwise
      // the translate and clipping will be reversed on next call
      g.emit("1 0 0 1 " + x + " " + -y + " cm");
      g.emitClip(0, 0, width, height);
      g.clipping = new Rectangle2D.Double(0, 0, width, height);
      g.transformRect((Rectangle2D.Double) g.clipping, false);

      return g;
   }

   /**
    * Gets the current color.
    */
   @Override
   public Color getColor() {
      return (clr == null) ? Color.black : clr;
   }

   /**
    * Set the background color.
    */
   @Override
   public void setBackground(Color c) {
      backClr = c;
   }

   /**
    * Get the background color.
    */
   @Override
   public Color getBackground() {
      return backClr;
   }

   /**
    * Sets the current color to the specified color. All subsequent graphics
    * operations will use this specified color.
    * @param c the color to be set
    */
   @Override
   public void setColor(Color c) {
      startPage();
      debug(pg, "%setColor");
      // optimize setColor
      this.brush = c;

      if(c == null) {
         return;
      }

      if(c.equals(clr) && alpha == compAlpha) {
         return;
      }

      float alpha = c.getAlpha() / 255f;

      if(composite != null && (composite instanceof AlphaComposite) &&
        ((AlphaComposite) composite).getRule() == AlphaComposite.SRC_OVER)
      {
         alpha *= compAlpha;
      }

      updateAlpha(alpha);
      this.alpha = compAlpha;
      clr = c;
      pg.println(getColorCommand(clr));
      setStroke(getStroke());
   }

   /**
    * Update alpha in the graphics state.
    */
   private void updateAlpha(float alpha) {
      int tpos = alpHolder.getAlphaIndex(alpha);
      int ppos = alpHolder.getPageAlphaIndex(alpha);

      if(tpos == -1) {
         alpHolder.add(alpha);
         tpos = alpHolder.getAlphaIndex(alpha);
      }
      else if(ppos == -1 && alpha != 1.0f) {
         alpHolder.addDefinedAlpha(alpha);
      }

      emit("BX /GS" + (tpos + 1) + " gs EX");
   }

   /**
    * Sets the default paint mode to overwrite the destination with the
    * current color. PostScript has only paint mode.
    */
   @Override
   public void setPaintMode() {
   }

   /**
    * Sets the paint mode to alternate between the current color
    * and the new specified color. PostScript does not support XOR mode.
    * @param c1 the second color
    * Note: setXORMode not supported by PostScript
    */
   @Override
   public void setXORMode(Color c1) {
      LOG.warn("XOR Mode Not supported");
   }

   /**
    * Gets the current font.
    * @see #setFont
    */
   @Override
   public Font getFont() {
      return font;
   }

   /**
    * Sets the font for all subsequent text-drawing operations.
    * @param font the specified font
    */
   @Override
   public void setFont(Font font) {
      startPage();

      // ignore null font
      if(font == null) {
         return;
      }

      if(font.equals(this.font) && font.getClass().equals(this.font.getClass())) {
         return;
      }

      this.ofont = font;
      this.font = fixFont(font);
      fm = null;
      afm = null;

      if(isTextObj()) {
         emitFont(this.font);
      }
      else {
         psFontName = null;
      }
   }

   private Font fixFont(Font font) {
      if(fontRatio == 1.0) {
         return font;
      }

      if(font instanceof StyleFont) {
         StyleFont sf = (StyleFont) font;
         sf = new StyleFont(sf.getName(), sf.getStyle(),
                         (int) (sf.getSize() * fontRatio),
                         sf.getUnderlineStyle(),
                         sf.getStrikelineStyle());
         return sf;
      }
      else if(font != null) {
         font = font.deriveFont((float) (font.getSize2D() * fontRatio));
      }

      return font;
   }

   /**
    * Send the set font command to output.
    */
   protected String emitFont(Font font) {
      startPage();
      debug(pg, "%setFont");

      psFontName = getFontName(font);
      String psFontNameTmp = getPSFontNameWithInsetx(psFontName, insetx);
      String fn = fontFn.get(psFontNameTmp);
      boolean found = fn != null;

      if(!found) {
         fn = "F" + getNextFontIndex();
         String fo = fontObj.get(psFontNameTmp);

         if(fo == null) {
            int encodingId = 0;

            if(isInsetxNeeded(psFontName, insetx)) {
               encodingId = writeEncoding(insetx);
            }

            int fontId = getNextObjectID();

            fo = fontId + " 0 R";
            fontObj.put(psFontNameTmp, fo);
            fnObj.put(fn, fo);

            others.markObject(fontId);
            others.println(fontId + " 0 obj");
            others.println("<<");
            others.println("/Type /Font");
            others.println("/Subtype /Type1");
            others.println("/Name /" + fn);
            others.println("/BaseFont /" + getBaseFontName(psFontName, font));

            // @by larryl, ZapfDingbats does not use windows encoding, the
            // default encoding would work
            if(!psFontName.equals("ZapfDingbats")) {
               String encoding = psFontName.equals("Symbol") ?
                  "/PDFDocEncoding" :
                  ((insetx < 0) ? "/WinAnsiEncoding" : (encodingId + " 0 R"));

               others.println("/Encoding " + encoding);
            }

            others.println(">>");
            others.println("endobj");
         }

         fnList.add("/" + fn + " " + fo + " ");
      }

      builder.setLength(0);
      builder.append('/').append(fn).append(' ').append(font.getSize())
         .append(" Tf\n");
      pg.print(builder.toString());

      if(!found) {
         fontFn.put(psFontNameTmp, fn);
      }

      return fn;
   }

   /**
    * Get Base font name, if font name is wide char string, just use the font
    * name with US locale.
    */
   public String getBaseFontName(String fontname, Font font) {
      return font != null ? isWideCharString(fontname, true) ?
         font.getFontName(Locale.US) : fontname : fontname;
   }

   /**
    * Gets the current font metrics.
    * @see #getFont
    */
   @Override
   public FontMetrics getFontMetrics() {
      return (fm == null) ? (fm = getFontMetrics(getFont())) : fm;
   }

   /**
    * Gets the current font metrics for the specified font.
    * @param f the specified font
    * @see #getFont
    * @see #getFontMetrics
    */
   @Override
   public FontMetrics getFontMetrics(Font f) {
      return Common.getFontMetrics(f);
   }

   /**
    * Returns the bounding rectangle of the current clipping area.
    * The coordinates in the rectangle are relative to the coordinate
    * system origin of this graphics context.
    * @return      the bounding rectangle of the current clipping area.
    */
   @Override
   public Rectangle getClipBounds() {
      final Shape s = getClip();
      final Rectangle2D b = s.getBounds2D();
      return new Rectangle((int) b.getX(), (int) b.getY(), (int) b.getWidth(), (int) b.getHeight());
   }

   /**
    * Gets the current clipping area.
    * @return      a <code>Shape</code> object representing the
    *                      current clipping area.
    */
   @Override
   public Shape getClip() {
      Rectangle2D.Double out = new Rectangle2D.Double();
      out.setRect(clipping);
      transformRect(out, true);
      double x = out.getX();
      double y = out.getY();
      double w = out.getWidth();
      double h = out.getHeight();

      if(w < 0) {
         x += w;
         w = -w;
      }

      if(h < 0) {
         y += h;
         h = -h;
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   /**
    * Clips to a rectangle. The resulting clipping area is the
    * intersection of the current clipping area and the specified
    * rectangle. Graphic operations have no effect outside of the
    * clipping area.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #getClipRect
    */
   @Override
   public void clipRect(int x, int y, int width, int height) {
      clipRect((double) x, (double) y, (double) width, (double) height);
   }

   public void clipRect(double x, double y, double width, double height) {
      startPage();
      debug(pg, "%clipRect");

      Rectangle2D.Double rect = new Rectangle2D.Double(x, y, width, height);
      Rectangle2D.Double clip = new Rectangle2D.Double();
      Rectangle2D.Double out = new Rectangle2D.Double();

      out.setRect(clipping);
      transformRect(out, true);
      Rectangle2D res = out.createIntersection(rect);
      clip.setRect(res);
      transformRect(clip, false);

      checkTextObj(false);
      gsave(4);

      // @by billh, fix bug bug1286938273739
      clipping = clip;
      emitClip(x, y, width, height);
   }

   /**
    * Sets the current clip to the rectangle specified by the given
    * coordinates.
    * Rendering operations have no effect outside of the clipping area.
    * @param       x the <i>x</i> coordinate of the new clip rectangle.
    * @param       y the <i>y</i> coordinate of the new clip rectangle.
    * @param       width the width of the new clip rectangle.
    * @param       height the height of the new clip rectangle.
    */
   @Override
   public void setClip(int x, int y, int width, int height) {
      setClip((double) x, (double) y, (double) width, (double) height);
   }

   public void setClip(double x, double y, double width, double height) {
      startPage();
      debug(pg, "%setClip");

      checkTextObj(false);
      grestore(4);

      Rectangle2D.Double rect = new Rectangle2D.Double(x, y, width, height);
      transformRect(rect, false);
      gsave(4);

      // @by billh, fix bug bug1286938273739
      clipping = rect;
      // re-set transformation
      emitcm(trans);
      emitClip(x, y, width, height);
   }

   protected void emitClip(double x, double y, double width, double height) {
      y = transformY(y);
      x = transformX(x);

      builder.setLength(0);
      builder.append(toString(x)).append(' ').append(toString(y)).append(' ').
         append(toString(width)).append(' ').append(toString(-height)).
         append(" re\n").append("W* n\n");
      pg.print(builder.toString());
   }

   /**
    * Set the clip if the shape is not rectangle.
    */
   private void setPathClip(Shape s) {
      startPage();
      debug(pg, "%setPathClip");
      checkTextObj(false);
      grestore(4);
      gsave(4);
      PathIterator iter = s.getPathIterator(new AffineTransform());

      if(iter.isDone()) {
         return;
      }

      emitcm(trans);

      float[] moveto = {0, 0};

      for(; !iter.isDone(); iter.next()) {
         float[] coords = new float[6];
         int type = iter.currentSegment(coords);

         switch(type) {
         case PathIterator.SEG_MOVETO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " m");
            moveto = coords;
            break;
         case PathIterator.SEG_LINETO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " l");
            break;
         case PathIterator.SEG_QUADTO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            coords[2] = (float) transformX(coords[2]);
            coords[3] = (float) transformY(coords[3]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " " +
               toString(coords[2]) + " " + toString(coords[3]) + " v");
            break;
         case PathIterator.SEG_CUBICTO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            coords[2] = (float) transformX(coords[2]);
            coords[3] = (float) transformY(coords[3]);
            coords[4] = (float) transformX(coords[4]);
            coords[5] = (float) transformY(coords[5]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " " +
               toString(coords[2]) + " " + toString(coords[3]) + " " +
               toString(coords[4]) + " " + toString(coords[5]) + " c");
            break;
         case PathIterator.SEG_CLOSE:
            pg.println(toString(moveto[0]) + " " + toString(moveto[1]) + " l");
            break;
         }
      }

      pg.println("W n");
   }

   /**
    * Copies an area of the screen.
    * @param x the x-coordinate of the source
    * @param y the y-coordinate of the source
    * @param width the width
    * @param height the height
    * @param dx the horizontal distance
    * @param dy the vertical distance
    * Note: copyArea not supported by PostScript
    */
   @Override
   public void copyArea(int x, int y, int width, int height, int dx, int dy) {
      throw new RuntimeException("copyArea not supported");
   }

   /**
    * Draws a line between the coordinates (x1,y1) and (x2,y2). The line is
    * drawn below and to the left of the logical coordinates.
    * @param x1 the first point's x coordinate
    * @param y1 the first point's y coordinate
    * @param x2 the second point's x coordinate
    * @param y2 the second point's y coordinate
    */
   @Override
   public void drawLine(int x1, int y1, int x2, int y2) {
      drawLine((double) x1, (double) y1, (double) x2, (double) y2);
   }

   public void drawLine(double x1, double y1, double x2, double y2) {
      startPage();
      debug(pg, "%drawLine");

      //@by mikec.
      //draw line should not adjust the y or x value automatically,
      //the invoker method should set y or x to the middle of the line
      //instead of any corner, the pdf reader should be able to take
      //care of the adjustment of the line width.
      //This is consistent with the draw method.

      // draw dot
      if(x1 == x2 && y1 == y2) {
         x2 += 0.5;
         y1 = y2 = y1 + 0.5; // this is consistent with the case where y1==y2
      }

      y1 = transformY(y1);
      y2 = transformY(y2);
      x1 = transformX(x1);
      x2 = transformX(x2);
      checkTextObj(false);

      builder.setLength(0);
      builder.append(toString(x1)).append(' ').append(toString(y1))
         .append(" m\n").append(toString(x2)).append(' ').append(toString(y2))
         .append(" l\n").append("S\n");
      pg.print(builder.toString());
   }

   /**
    * Draws a sequence of connected lines defined by
    * arrays of <i>x</i> and <i>y</i> coordinates.
    * Each pair of (<i>x</i>,&nbsp;<i>y</i>) coordinates defines a point.
    * The figure is not closed if the first point
    * differs from the last point.
    * @param       xPoints an array of <i>x</i> points
    * @param       yPoints an array of <i>y</i> points
    * @param       nPoints the total number of points
    */
   @Override
   public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
      float[] xs = new float[nPoints];
      float[] ys = new float[nPoints];

      for(int i = 0; i < nPoints; i++) {
         xs[i] = xPoints[i];
         ys[i] = yPoints[i];
      }

      drawPolyline(xs, ys, nPoints);
   }

   /**
    * Draws a sequence of connected lines defined by
    * arrays of <i>x</i> and <i>y</i> coordinates.
    * Each pair of (<i>x</i>,&nbsp;<i>y</i>) coordinates defines a point.
    * The figure is not closed if the first point
    * differs from the last point.
    * @param       xPoints an array of <i>x</i> points
    * @param       yPoints an array of <i>y</i> points
    * @param       nPoints the total number of points
    */
   public void drawPolyline(float[] xPoints, float[] yPoints, int nPoints) {
      startPage();
      debug(pg, "%drawPolyline");

      checkTextObj(false);

      for(int i = 0; i < nPoints; i++) {
         double y1 = transformY(yPoints[i]);
         double x1 = transformX(xPoints[i]);

         pg.print(toString(x1) + " " + toString(y1));

         if(i == 0) {
            pg.println(" m");
         }
         else {
            pg.println(" l");
         }
      }

      pg.println("S");
   }

   /**
    * Draw rectangle.
    */
   private void doRect(double x, double y, double width, double height,
                       boolean fill) {
      startPage();
      debug(pg, "%doRect");

      y = transformY(y);
      x = transformX(x);

      checkTextObj(false);

      if(fill) {
         pg.println(toString(x) + " " + toString(y) + " " + toString(width) +
            " -" + toString(height) + " re");
         pg.println("f*");
      }
      else {
         pg.println(toString(x) + " " + toString(y) + " m");
         pg.println(toString(x + width) + " " + toString(y) + " l");
         pg.println(toString(x + width) + " " + toString(y - height) + " l");
         pg.println(toString(x) + " " + toString(y - height) + " l");
         pg.println(toString(x) + " " + toString(y) + " l");
         pg.println("s");
      }
   }

   /**
    * Fills the specified rectangle with the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #drawRect
    * @see #clearRect
    */
   @Override
   public void fillRect(int x, int y, int width, int height) {
      fillRect((double) x, (double) y, (double) width, (double) height);
   }

   public void fillRect(double x, double y, double width, double height) {
      debug(pg, "%fillRect");
      doRect(x, y, width, height, true);
   }

   /**
    * Draws the outline of the specified rectangle using the current color.
    * Use drawRect(x, y, width-1, height-1) to draw the outline inside the
    * specified rectangle.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #fillRect
    * @see #clearRect
    */
   @Override
   public void drawRect(int x, int y, int width, int height) {
      drawRect((double) x, (double) y, (double) width, (double) height);
   }

   public void drawRect(double x, double y, double width, double height) {
      debug(pg, "%drawRect");
      doRect(x, y, width, height, false);
   }

   /**
    * Clears the specified rectangle by filling it with the current
    * background color of the current drawing surface.
    * Which drawing surface it selects depends on how the graphics context
    * was created.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #fillRect
    * @see #drawRect
    */
   @Override
   public void clearRect(int x, int y, int width, int height) {
      clearRect((double) x, (double) y, (double) width, (double) height);
   }

   public void clearRect(double x, double y, double width, double height) {
      startPage();
      debug(pg, "%clearRect");
      checkTextObj(false);
      Color c = getColor();

      setColor(backClr);
      doRect(x, y, width, height, true);
      setColor(c);
   }

   private void doRoundRect(double x, double y, double width, double height,
                            double aw, double ah, boolean fill) {
      startPage();

      // if the corner all more than 1/2 of w/h, it mean that the round
      // rectangle is a circle shape
      if(aw >= width / 2 && ah >= height / 2) {
         doArc(x, y, width, height, 0, 360, fill);

         return;
      }

      debug(pg, "%doRoundRect");
      // java paint round rectangle, the corner height means:
      //    top corner height + bottom corner height
      aw = aw / 2;
      ah = ah / 2;
      boolean b2 = true;
      y = transformY(y);
      x = transformX(x);
      double x2 = x + width, y2 = y - height;

      // @by larryl, the round corner can not take more than 1/2 of w/h
      aw = Math.min(aw, width / 2);
      ah = Math.min(ah, height / 2);

      checkTextObj(false);

      // starts from the top-left curve, clockwise
      pg.println(toString(x) + " " + toString(y - ah) + " m");

      pg.println(transform(x, y-ah, x, y, x + aw, y, b2) + " " +
                 toString(x + aw) + " " + toString(y) + " c");

      pg.println(toString(x2 - aw) + " " + toString(y) + " l");

      pg.println(transform(x2 - aw, y, x2, y, x2, y - ah, b2) + " " +
                 toString(x2) + " " + toString(y - ah) + " c");

      pg.println(toString(x2) + " " + toString(y2 + ah) + " l");

      pg.println(transform(x2, y2 + ah, x2, y2, x2 - aw, y2, b2) + " " +
                 toString(x2 - aw) + " " + toString(y2) + " c");

      pg.println(toString(x + aw) + " " + toString(y2) + " l");

      pg.println(transform(x + aw, y2, x, y2, x, y2 + ah, b2) + " " +
                 toString(x) + " " + toString(y2 + ah) + " c");

      pg.println(toString(x) + " " + toString(y - ah) + " l");

      if(fill) {
         pg.println("f*");
      }
      else {
         pg.println("S");
      }
   }

   /**
    * Get Bezier curve's control point(s), transform Quadratic Bezier curve to
    * Cubic Bezier curve, this can more match java RoundRectangle2D..
    * @param x0, y0 the start point.
    * @param x1, y1 the control point.
    * @param x2, y2 the end point.
    * @param b2 identical need convert Quadratic Bezier curve to
    *  Cubic Bezier curve or not, here always true.
    * @see <a href="http://en.wikipedia.org/wiki/B%C3%A9zier_curve">Bezier curve</a>
    */
   private String transform(double x0, double y0, double x1, double y1,
                            double x2, double y2, boolean b2)
   {
      if(b2) {
         x2 = 2 / 3.0 * x1 + 1 / 3.0 * x2;
         y2 = 2 / 3.0 * y1 + 1 / 3.0 * y2;
         x1 = 1 / 3.0 * x0 + 2/ 3.0 * x1;
         y1 = 1 / 3.0 * y0 + 2/ 3.0 * y1;
      }
      else {
         x2 = x1;
         y2 = y1;
      }

      return toString(x1) + " " + toString(y1) + " " +
             toString(x2) + " " + toString(y2);
   }

   /**
    * Draws an outlined rounded corner rectangle using the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param arcWidth the diameter of the arc
    * @param arcHeight the radius of the arc
    * @see #fillRoundRect
    */
   @Override
   public void drawRoundRect(int x, int y, int width, int height,
                             int arcWidth, int arcHeight) {
      drawRoundRect((double) x, (double) y, (double) width, (double) height,
         (double) arcWidth, (double) arcHeight);
   }

   public void drawRoundRect(double x, double y, double width, double height,
                             double arcWidth, double arcHeight) {
      debug(pg, "%drawRoundRect");
      doRoundRect(x, y, width, height, arcWidth, arcHeight, false);
   }

   /**
    * Draws a rounded rectangle filled in with the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param arcWidth the diameter of the arc
    * @param arcHeight the radius of the arc
    * @see #drawRoundRect
    */
   @Override
   public void fillRoundRect(int x, int y, int width, int height,
                             int arcWidth, int arcHeight) {
      fillRoundRect((double) x, (double) y, (double) width, (double) height,
         (double) arcWidth, (double) arcHeight);
   }

   public void fillRoundRect(double x, double y, double width, double height,
                             double arcWidth, double arcHeight) {
      debug(pg, "%fillRoundRect");
      doRoundRect(x, y, width, height, arcWidth, arcHeight, true);
   }

   /**
    * Draws a highlighted 3-D rectangle.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param raised a boolean that states whether the rectangle is raised
    * or not.
    */
   @Override
   public void draw3DRect(int x, int y, int width, int height, boolean raised) {
      draw3DRect((double) x, (double) y, (double) width, (double) height,
         raised);
   }

   public void draw3DRect(double x, double y, double width, double height,
                          boolean raised) {
      startPage();
      debug(pg, "%draw3DRect");
      Color c = getColor();
      Color brighter = c.brighter();
      Color darker = c.darker();

      setColor(raised ? brighter : darker);
      drawLine(x, y, x, y + height);
      drawLine(x + 1, y, x + width - 1, y);
      setColor(raised ? darker : brighter);
      drawLine(x + 1, y + height, x + width, y + height);
      drawLine(x + width, y, x + width, y + height);
      setColor(c);
   }

   /**
    * Paints a highlighted 3-D rectangle using the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param raised a boolean that states whether the rectangle is raised
    * or not.
    */
   @Override
   public void fill3DRect(int x, int y, int width, int height, boolean raised) {
      fill3DRect((double) x, (double) y, (double) width, (double) height,
         raised);
   }

   public void fill3DRect(double x, double y, double width, double height,
                          boolean raised) {
      startPage();
      debug(pg, "%fill3DRect");
      Color c = getColor();
      Color brighter = c.brighter();
      Color darker = c.darker();

      if(!raised) {
         setColor(darker);
      }

      fillRect(x + 1, y + 1, width - 2, height - 2);
      setColor(raised ? brighter : darker);
      drawLine(x, y, x, y + height - 1);
      drawLine(x + 1, y, x + width - 2, y);
      setColor(raised ? darker : brighter);
      drawLine(x + 1, y + height - 1, x + width - 1, y + height - 1);
      drawLine(x + width - 1, y, x + width - 1, y + height - 1);
      setColor(c);
   }

   /**
    * Draws an oval inside the specified rectangle using the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #fillOval
    */
   @Override
   public void drawOval(int x, int y, int width, int height) {
      drawOval((double) x, (double) y, (double) width, (double) height);
   }

   public void drawOval(double x, double y, double width, double height) {
      debug(pg, "%drawOval");
      doArc(x, y, width, height, 0, 360, false);
   }

   /**
    * Fills an oval inside the specified rectangle using the current color.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @see #drawOval
    */
   @Override
   public void fillOval(int x, int y, int width, int height) {
      fillOval((double) x, (double) y, (double) width, (double) height);
   }

   public void fillOval(double x, double y, double width, double height) {
      debug(pg, "%fillOval");
      doArc(x, y, width, height, 0, 360, true);
   }

   private void doArc(double x, double y, double width, double height,
                      double startAngle, double arcAngle, boolean fill)
   {
      doArc(x, y, width, height, startAngle, arcAngle, fill, Arc2D.OPEN);
   }

   private void doArc(double x, double y, double width, double height,
      double startAngle, double arcAngle, boolean fill, int type)
   {
      arcAngle = Double.parseDouble(Tool.toString(arcAngle, 6));

      if(arcAngle == 0.0 || width <= 0 || height <= 0) {
         return;
      }

      startPage();
      debug(pg, "%doArc");

      if(arcAngle == 0) {
         return;
      }
      else if(arcAngle < 0) {
         startAngle += arcAngle;
         arcAngle = -arcAngle;
      }

      y = transformY(y);
      x = transformX(x);

      checkTextObj(false);
      double endAngle = startAngle + arcAngle;
      double inc = Math.max(0.1, Math.min(3, arcAngle / 4));
      List<FitCurves.Point2> pv = new ArrayList<>();
      debug(pg, "%doArc_begin");

      for(double a = startAngle; a < endAngle; a += inc) {
         final FitCurves.Point2 p = getArcPoint(x, y, width, height, a);
         pv.add(p);
         debug(pg, "%point at " + p);
      }

      FitCurves.Point2 p = getArcPoint(x, y, width, height, endAngle);
      pv.add(p);
      debug(pg, "%e point at " + p);
      debug(pg, "%doArc_end");

      // fit curves to points
      FitCurves.Point2[] parr = pv.toArray(new FitCurves.Point2[pv.size()]);
      FitCurves algo = new FitCurves(parr, 2.0);
      // draw curves
      List<?> curves = algo.getCurves();
      builder.setLength(0);

      if(isDrawArcBounds(type, arcAngle, fill)) {
         builder.append(toString(x + width / 2)).append(' ').
            append(toString(y - height / 2)).append(" m\n");
      }

      int len = curves.size();

      for(int i = 0; i < len; i++) {
         FitCurves.Point2[] bp = (FitCurves.Point2[]) curves.get(i);

         if(fill) {
            builder.append(toString(x + width / 2)).append(' ').
               append(toString(y - height / 2)).append(" m\n");
         }

         builder.append(toString(bp[0].x)).append(' ').append(toString(bp[0].y))
            .append(fill || (isDrawArcBounds(type, arcAngle, fill) && i == 0) ?
            " l\n" : " m\n");

         builder.append(toString(bp[1].x)).append(' ').append(toString(bp[1].y))
            .append(' ').append(toString(bp[2].x)).append(' ')
            .append(toString(bp[2].y)).append(' ').append(toString(bp[3].x))
            .append(' ').append(toString(bp[3].y)).append(" c\n");
      }

      if(isDrawArcBounds(type, arcAngle, fill)) {
         builder.append(toString(x + width / 2)).append(' ').
            append(toString(y - height / 2)).append(" l\n");
      }

      if(fill) {
         builder.append("f*\n");
      }
      else {
         builder.append("S\n");
      }

      pg.print(builder.toString());
   }

   /**
    * Check if the bound of the arc should be drawn.
    */
   private boolean isDrawArcBounds(int type, double arcAngle, boolean fill) {
      return type == Arc2D.PIE && arcAngle % 360 != 0 && !fill;
   }

   /**
    * Draws an arc bounded by the specified rectangle from startAngle to
    * endAngle. 0 degrees is at the 3-o'clock position.Positive arc
    * angles indicate counter-clockwise rotations, negative arc angles are
    * drawn clockwise.
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param startAngle the beginning angle
    * @param arcAngle the angle of the arc (relative to startAngle).
    * @see #fillArc
    */
   @Override
   public void drawArc(int x, int y, int width, int height,
                       int startAngle, int arcAngle) {
      drawArc((double) x, (double) y, (double) width, (double) height,
         (double) startAngle, (double) arcAngle, Arc2D.OPEN);
   }

   public void drawArc(double x, double y, double width, double height,
                       double startAngle, double arcAngle, int arcType) {
      debug(pg, "%drawArc");
      doArc(x, y, width, height, startAngle, arcAngle, false, arcType);
   }

   /**
    * Fills an arc using the current color. This generates a pie shape.
    *
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the arc
    * @param height the height of the arc
    * @param startAngle the beginning angle
    * @param arcAngle the angle of the arc (relative to startAngle).
    * @see #drawArc
    */
   @Override
   public void fillArc(int x, int y, int width, int height,
                       int startAngle, int arcAngle) {
      fillArc((double) x, (double) y, (double) width, (double) height,
         (double) startAngle, (double) arcAngle);
   }

   public void fillArc(double x, double y, double width, double height,
                       double startAngle, double arcAngle) {
      debug(pg, "%fillArc");
      doArc(x, y, width, height, startAngle, arcAngle, true);
   }

   private void doPoly(int[] xPoints, int[] yPoints, int nPoints, boolean fill) {
      startPage();

      if(nPoints < 2) {
         return;
      }

      checkTextObj(false);
      double[] newYPoints = new double[nPoints];
      double[] newXPoints = new double[nPoints];

      for(int i = 0; i < nPoints; i++) {
         newYPoints[i] = transformY(yPoints[i]);
      }

      for(int i = 0; i < nPoints; i++) {
         newXPoints[i] = transformX(xPoints[i]);
      }

      pg.print(toString(newXPoints[0]) + " " + toString(newYPoints[0]) + " m ");

      for(int i = 1; i < nPoints; i++) {
         pg.println(toString(newXPoints[i]) + " " + toString(newYPoints[i]) +
            " l");
      }

      pg.print(toString(newXPoints[0]) + " " + toString(newYPoints[0]) + " l ");

      if(fill) {
         pg.println("f*");
      }
      else {
         pg.println("S");
      }
   }

   /**
    * Draws a polygon defined by an array of x points and y points.
    * @param xPoints an array of x points
    * @param yPoints an array of y points
    * @param nPoints the total number of points
    * @see #fillPolygon
    */
   @Override
   public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
      debug(pg, "%drawPoly");
      doPoly(xPoints, yPoints, nPoints, false);
   }

   /**
    * Draws a polygon defined by the specified point.
    * @param p the specified polygon
    * @see #fillPolygon
    */
   @Override
   public void drawPolygon(Polygon p) {
      debug(pg, "%drawPoly");
      doPoly(p.xpoints, p.ypoints, p.npoints, false);
   }

   /**
    * Fills a polygon with the current color.
    * @param xPoints an array of x points
    * @param yPoints an array of y points
    * @param nPoints the total number of points
    * @see #drawPolygon
    */
   @Override
   public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
      debug(pg, "%fillPoly");
      doPoly(xPoints, yPoints, nPoints, true);
   }

   /**
    * Fills the specified polygon with the current color.
    * @param p the polygon
    * @see #drawPolygon
    */
   @Override
   public void fillPolygon(Polygon p) {
      debug(pg, "%fillPoly");
      doPoly(p.xpoints, p.ypoints, p.npoints, true);
   }

   /**
    * Draw a path.
    */
   private void drawPath(PathIterator path) {
      debug(pg, "%drawPath");
      doPath(path, false);
   }

   /**
    * Fill a path.
    */
   private void fillPath(PathIterator path) {
      debug(pg, "%fillPath");
      doPath(path, true);
   }

   /**
    * Draw or fill a path.
    * @param iter general path.
    * @param fill true to fill in the path.
    */
   private void doPath(PathIterator iter, boolean fill) {
      float[] moveto = {0, 0};

      for(; !iter.isDone(); iter.next()) {
         float[] coords = new float[6];
         int type = iter.currentSegment(coords);

         switch(type) {
         case PathIterator.SEG_MOVETO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " m");
            moveto = coords;
            break;
         case PathIterator.SEG_LINETO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " l");
            break;
         case PathIterator.SEG_QUADTO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            coords[2] = (float) transformX(coords[2]);
            coords[3] = (float) transformY(coords[3]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " " +
                       toString(coords[2]) + " " + toString(coords[3]) + " v");
            break;
         case PathIterator.SEG_CUBICTO:
            coords[0] = (float) transformX(coords[0]);
            coords[1] = (float) transformY(coords[1]);
            coords[2] = (float) transformX(coords[2]);
            coords[3] = (float) transformY(coords[3]);
            coords[4] = (float) transformX(coords[4]);
            coords[5] = (float) transformY(coords[5]);
            pg.println(toString(coords[0]) + " " + toString(coords[1]) + " " +
                       toString(coords[2]) + " " + toString(coords[3]) + " " +
                       toString(coords[4]) + " " + toString(coords[5]) + " c");
            break;
         case PathIterator.SEG_CLOSE:
            pg.println(toString(moveto[0]) + " " + toString(moveto[1]) + " l");
            break;
         default:
            LOG.error("Unknown path type: {}", type);
            break;
         }
      }

      if(fill) {
         pg.println(" f");
      }
      else {
         pg.println(" S");
      }
   }

   /**
    * Draws the specified String using the current font and color.
    * The x,y position is the starting point of the baseline of the String.
    * @param str the String to be drawn
    * @param sx the x coordinate
    * @param sy the y coordinate
    */
   @Override
   public void drawString(String str, int sx, int sy) {
      drawString(str, (double) sx, (double) sy);
   }

   @Override
   public void drawString(String str, float sx, float sy) {
      drawString(str, (double) sx, (double) sy);
   }

   /**
    * Draw string with double coordinate values.
    */
   public char[] drawString(String str, double sx, double sy) {
      if(str == null) {
         return null;
      }

      // @by stephenwebster, Fix bug1423849021134
      // @see http://en.wikipedia.org/wiki/Unicode_equivalence
      // @see http://www.unicode.org/reports/tr15/tr15-23.html
      // Use Unicode Normalization normal form "fully composed", so only the
      // composed form of a sequence of characters is needed in the PDF.
      // @TODO, this could replace any logic for the combining range
      // 0x300-0x320 below and may have some impact on the other forms of
      // combining we do for arabic and thai.
      str = Normalizer.normalize(str, Normalizer.Form.NFC);
      char[] oldChars = str.toCharArray();

      startPage();
      debug(pg, "%drawString");

      checkTextObj(true);

      double y = transformY(sy);
      double x = transformX(sx);

      builder.setLength(0);
      builder.append("1 0 0 1 ").
         append(toString(x)).append(' ').append(toString(y)).append(" Tm\n");
      pg.print(builder.toString());

      // handle bidi if necessary
      str = Common.processBidi(str);

      // @ by stephenwebster, For Bug #9779
      // @TODO review better solution here.
      // 1) bidirectional characters should not be included in the string,
      // otherwise it takes up space, there might be a better solution.
      // 2) Character spacing for mixed arabic and latin text is not well
      // supported.  The best way I could find in the interim is to break the
      // string into separate parts based on the direction of the text.
      str = str.replaceAll("[\u200e\u200f]", "");
      Bidi bidi = new Bidi(str, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);

      if(bidi.isMixed() &&
         "true".equals(SreeEnv.getProperty("mixed.bidi.enabled")))
      {
         char[] chars0 = str.toCharArray();
         StringBuilder buf;

         for(int i = 0; i < bidi.getRunCount(); i++) {
            int si = bidi.getRunStart(i);
            int ei = bidi.getRunLimit(i);

             buf = new StringBuilder();
             buf.append(chars0, si, ei - si);
             emitString(buf.toString());
         }
      }
      else {
         emitString(str);
      }

      // Bug #59512, get the char array before and after all the transformations, otherwise some
      // characters disappear in pdf when embed fonts is enabled
      char[] chars = str.toCharArray();
      char[] allChars = new char[oldChars.length + chars.length];
      System.arraycopy(oldChars, 0, allChars, 0, oldChars.length);
      System.arraycopy(chars, 0, allChars, oldChars.length, chars.length);
      return allChars;
   }

   /**
    * Emit String to PDF output
    * @param str The string to emit
    */
   private void emitString(String str) {
      char[] chars = str.toCharArray();
      int len = chars.length;

      if(len == 0) {
         return;
      }

      FontMetrics fm = Common.getFractionalFontMetrics(getFont());
      double awtW = Common.stringWidth(str, getFont(), fm);
      double psW = stringWidth(str);
      int combining = 0; // number of combining characters
      boolean hasArabic = false;
      boolean hasThai = false;

      for(int i = 0; i < len; i++) {
         char ch = chars[i];

         if(!hasArabic && isArabicCharacter(ch)) {
            hasArabic = true;
         }

         if(!hasThai && isThaiCharacter(ch)) {
            hasThai = true;
         }

         // @by yanie: bug1418941727663
         // Some Thai char will position above or below another char so it will
         // not occupy any width, remove the width of this type char
         if(ch >= 0x300 && ch <= 0x320 || isThaiWidth0Character(chars, i) ||
            // @by yanie: bug1429907180451
            // Arabic is similar to Thai, it has above/below chars, too.
            isArabicWidth0Character(chars, i))
         {
            combining++;
         }
      }

      // scale space proportional to the size differences
      double tc = (len > 1 + combining) ? (awtW - psW) / (len - 1 - combining) : 0;

      // @by larryl, limit the spacing so a long word does not get stretched
      // too much. this is not a complete solution, which requires the space
      // expansion to be done on a per word basis, and would be expensive
      if((getFont().getStyle() & StyleFont.UNDERLINE) != 0 ||
         (getFont().getStyle() & StyleFont.STRIKETHROUGH) != 0)
      {
         // allow spacing to be wider so the underline would match the width of chars
         tc = Math.min(tc, 1.5);
      }
      else {
         tc = Math.min(tc, 0.8);
      }

      // @by henry, keep enough word space to avoid words overlapping.
      // @by larryl, we should allow the spacing to be negative otherwise it
      // would lose the function of compressing the chars to fit into limited
      // space. The negative value should small enough so the chars don't
      // overlap
      // @by mikec, in case if we limit the tc, some text may be truncated,
      // let user can choose overlap it or truncate it.
      if(tc < -1 &&
         "true".equals(SreeEnv.getProperty("pdf.text.avoidoverlap")))
      {
         tc = -1;
      }

      if(hasArabic || hasThai || "true".equals(SreeEnv.getProperty("pdf.text.useDefaultCharacterSpacing"))) {
         tc = 0;
      }

      if(tc != 0) {
         pg.println(toString(tc) + " Tc");
      }

      emitTj(str);
   }

   /**
    * Output the Tj command.
    */
   protected void emitTj(String txt) {
      // perform symbol mapping
      if(mapSymbol) {
         char[] chars = txt.toCharArray();
         int len = chars.length;
         char[] map = new char[len + 1];
         int last = 0;
         boolean symbol = false;
         Font ofont = font;
         Font symbolFont = null;

         for(int i = 0; i < map.length; i++) {
            // the extra index after txt.length() is used to force the
            // if to be true at the end of the string
            map[i] = (i < len) ? SymbolMapper.map(chars[i]) :
               (symbol ? (char) 0 : 'a');

            // changed from regular -> symbol or wise versa
            if(symbol != (map[i] != 0)) {
               if(i > last) {
                  if(symbol) {
                     if(symbolFont == null) {
                        symbolFont = new Font("Symbol", font.getStyle(), font.getSize());
                     }

                     setFont(symbolFont);
                     pg.println("<" + toHex(map, last, i - last) + "> Tj");
                  }
                  else {
                     setFont(ofont);
                     emitTj0(txt.substring(last, i));
                  }

                  last = i;
               }

               symbol = map[i] != 0;
            }
         }
      }
      else {
         emitTj0(txt);
      }
   }

   /**
    * Emit the Tj command.
    */
   private void emitTj0(String txt) {
      String s = escapeString(txt);
      StringBuilder buffer = null;
      int range = -1;
      int lastRange = -99;
      char[] chars = s.toCharArray();
      int len = chars.length;

      for(int i = 0; i < len; i++) {
         char c = chars[i];
         range = -1;

         if(c == 0x20AC) { //euro
            c = 0x80;
         }
         else if(c == 0x200F) {
            // skip right to left mark
            continue;
         }

         boolean octal = false;

         if(c <= 127) {
            // do nothing
         }
         else if(c < 256) {  // for char 128-255
            octal = true;

            if(buffer == null) {
               buffer = new StringBuilder();
               buffer.append(chars, 0, i);
            }
         }
         else {
            if(buffer == null) {
               buffer = new StringBuilder();
               buffer.append(chars, 0, i);
            }

            boolean isWidth0Char = isThaiWidth0Character(chars, i) ||
               isArabicWidth0Character(chars, i);

            for(int k = 0; k < charRanges.length; k++) {
               boolean needWidth = needWidths[k];

               // @by yanie: bug1418941727663
               // Thai width0 char should use different char range since
               // their width will be exported as 0
               if(needWidth != isWidth0Char &&
                  c >= charRanges[k][0] && c <= charRanges[k][1])
               {
                  range = k;
                  break;
               }
            }

            if(range < 0) {
               range = newRange(c, isWidth0Char);
            }
         }

         // if this is the first range (-99), don't write out text regardless
         if(range != lastRange && lastRange >= -1 && buffer != null) {
            writeTj(buffer, lastRange,
                    (lastRange >= 0) ? charRanges[lastRange][0] - 1 : 0);
            buffer.setLength(0);
         }

         lastRange = range;

         if(octal) {
            buffer.append("\\");
            buffer.append(Integer.toOctalString(c));
         }
         else if(buffer != null) {
            buffer.append(c);
         }
      }

      if(buffer == null) {
         writeTj(s, len, range, (range >= 0) ? charRanges[range][0] - 1 : 0);
      }
      else if(buffer.length() > 0) {
         writeTj(buffer, range, (range >= 0) ? charRanges[range][0] - 1 : 0);
      }
   }

   /**
    * Create a new mapping for the unicode character. A range starting at the
    * character for the next 30 characters are created using the 'uni' naming
    * format as documented in
    * http://partners.adobe.com/public/developer/opentype/index_glyph.html
    */
   private static synchronized int newRange(char c, boolean isWidth0ThaiChar) {
      StringBuilder mapping = new StringBuilder("[ 1");

      // create mapping
      // @by yanie: the chars in initial charname are 31, and when use the
      // charranges, we are using >= startIndex and <= endIndex
      // so here in we should add 31 chars accordingly
      //for(int i = 0; i < 30; i++) {
      for(int i = 0; i <= 30; i++) {
         mapping.append("/uni" + toHex((char)(i + c)));
      }

      mapping.append("]");

      // add char range
      int[][] narr = new int[charRanges.length + 1][];

      System.arraycopy(charRanges, 0, narr, 0, charRanges.length);
      narr[narr.length - 1] = new int[] {c, (c + 30)};
      charRanges = narr;

      // add maping
      String[] sarr = new String[charname.length + 1];

      System.arraycopy(charname, 0, sarr, 0, charname.length);
      sarr[sarr.length - 1] = mapping.toString();
      charname = sarr;

      boolean[] warr = new boolean[needWidths.length + 1];
      System.arraycopy(needWidths, 0, warr, 0, needWidths.length);
      warr[warr.length - 1] = isWidth0ThaiChar ? false : true;
      needWidths = warr;

      return charRanges.length - 1;
   }

   private static String toHex(char c) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(Integer.toHexString((c >>> 12) & 0x0F));
      buffer.append(Integer.toHexString((c >>> 8) & 0x0F));
      buffer.append(Integer.toHexString((c >>> 4) & 0x0F));
      buffer.append(Integer.toHexString((c) & 0x0F));

      return buffer.toString().toUpperCase();
   }

   /**
    * Write the content out, and change the font if necessary
    */
   private void writeTj(String str, int len, int idx, int base) {
      if(len == 0) {
         return;
      }

      if(insetx != idx) {
         insetx = idx;
         Font ofont = font;
         font = this.ofont = null; // force the emit font to be called
         setFont(ofont);    // must add the difference of this set
      }

      // normal encoding
      if(idx < 0) {
         pg.println("(" + str + ") Tj");
      }
      else {
         // modified encoding, print offset to the base char in encoding
         builder.setLength(0);
         builder.append('(');

         for(int i = 0; i < len; i++) {
            builder.append('\\').append(Integer.toOctalString(str.charAt(i) - base));
            addGlyphNameSubstitution(str.charAt(i) - base);
         }

         builder.append(") Tj\n");
         pg.print(builder.toString());
      }
   }

   /**
    * Write the content out, and change the font if necessary
    */
   private void writeTj(StringBuilder buffer, int idx, int base) {
      int len = buffer.length();

      if(len == 0) {
         return;
      }

      if(insetx != idx) {
         insetx = idx;
         Font ofont = font;
         font = this.ofont = null; // force the emit font to be called
         setFont(ofont);    // must add the difference of this set
      }

      // normal encoding
      if(idx < 0) {
         pg.println("(" + buffer.toString() + ") Tj");
      }
      else {
         // modified encoding, print offset to the base char in encoding
         pg.print("(");

         for(int i = 0; i < len; i++) {
            pg.print("\\" + Integer.toOctalString(buffer.charAt(i) - base));
            addGlyphNameSubstitution(buffer.charAt(i) - base);
         }

         pg.println(") Tj");
      }
   }

   /*
   *   @by stephenwebster
   *   Used specifically when processing characters in the drawString/emitTj/writeTj
   *   process.  During which, it is possible that we replace certain characters in the
   *   encoding with the Differences instruction. @see writeEncoding
   *   The lookup is controlled by the global insetx and charname variables.
   *   When we process the character, if this substitution occurs we must keep track of the
   *   replacement character so that when the font is embedded, that character is included.
   *   PDF3Printer drawString method handles setting the current font.
    */
   public void addGlyphNameSubstitution(int location) {
      String glyphNames = charname[insetx];
      String [] names = glyphNames.substring(glyphNames.indexOf("1"),
                                             glyphNames.lastIndexOf("]")).split("/");

      HashSet<String> glyphsFontMap  = glyphNameSubstitutions.get(currentFontKey);

      if(glyphsFontMap == null) {
         glyphsFontMap = new HashSet<>();
         glyphNameSubstitutions.put(currentFontKey, glyphsFontMap);
      }

      glyphsFontMap.add(names[location]);
   }

   public HashSet<String> getGlyphNameSubstitutions(String fontKey) {
      return glyphNameSubstitutions.get(fontKey);
   }

   public void setCurrentFontKey(String fontKey)  {
      currentFontKey = fontKey;
   }

   /**
    * Check whether a character is an arabic character.
    */
   private boolean isArabicCharacter(char c) {
      if((c >= 0x600 && c <= 0x6FF) ||
         (c >= 0x750 && c <= 0x77F) ||
         (c >= 0xfb50 && c <= 0xfbc1) ||
         (c >= 0xfbd3 && c <= 0xfd3f) ||
         (c >= 0xfd50 && c <= 0xfd8f) ||
         (c >= 0xfd92 && c <= 0xfdc7) ||
         (c >= 0xfe70 && c <= 0xfefc) ||
         (c >= 0xFDF0 && c <= 0xFDFD))
      {
         return true;
      }

      return false;
   }

   /**
    * Check if a character is a Arabic character which may appear above or below
    * another character
    */
   private boolean isArabicAboveOrBelowCharacter(char c) {
      if(!isArabicCharacter(c)) {
         return false;
      }

      // following code are follow the rules at:
      // https://www.microsoft.com/typography/OpenTypeDev/arabic/intro.htm

      // DIAC1: Arabic Arabic above diacritics
      if(c == 0x64B || c == 0x64C || c == 0x64E || c == 0x64F ||
         c == 0x652 || c == 0x657 || c == 0x658 || c == 0x6E1)
      {
         return true;
      }

      // DIAC2: Arabic below diacritics
      if(c == 0x64D || c == 0x650 || c == 0x656) {
         return true;
      }

      // DIAC3: Arabic seat shadda
      if(c == 0x651) {
         return true;
      }

      // DIAC4: Arabic Qur'anic marks above
      if(c == 0x610 || c == 0x611 || c == 0x612 || c == 0x613 ||
         c == 0x614 || c == 0x659 || c == 0x6D6 || c == 0x6D7 ||
         c == 0x6D8 || c == 0x6D9 || c == 0x6DA || c == 0x6DB ||
         c == 0x6DC || c == 0x6DF || c == 0x6E0 || c == 0x6E2 ||
         c == 0x6E4 || c == 0x6E7 || c == 0x6E8 || c == 0x6EB ||
         c == 0x6EC)
      {
         return true;
      }

      // DIAC5: Arabic Qur'anic marks below
      if(c == 0x6E3 || c == 0x6EA || c == 0x6ED) {
         return true;
      }

      // DIAC6: Arabic superscript alef
      if(c == 0x670) {
         return true;
      }

      // DIAC7: Arabic madda
      if(c == 0x653) {
         return true;
      }

      // DIAC8: Arabic madda
      if(c == 0x654 || c == 0x655) {
         return true;
      }

      return false;
   }

   /**
    * Check if an Arabic character should be 0 width
    */
   private boolean isArabicWidth0Character(char[] chs, int idx) {
      if(chs == null || chs.length == 0 || idx < 0 || idx == chs.length - 1) {
         return false;
      }

      char ch = chs[idx];

      if(!isArabicCharacter(ch) || !isArabicAboveOrBelowCharacter(ch)) {
         return false;
      }

      // unlike English, Arabic is from right to left
      return isArabicCharacter(chs[idx + 1]);
   }

   /**
    * Check if a Thai character should be 0 width
    */
   private boolean isThaiWidth0Character(char[] chs, int idx) {
      char ch = chs[idx];

      if(idx == 0 || !isThaiCharacter(ch) || !isThaiUpperOrLowerCharacter(ch)) {
         return false;
      }

      // @by yanie: bug1418941727663
      // If the Thai char is a upper or lower character and its previous char
      // is also a Thai char, then its width should be 0
      int idx2 = idx - 1;

      while(idx2 >= 0) {
         ch = chs[idx2];

         if(!isThaiCharacter(ch)) {
            return false;
         }
         else if(!isThaiUpperOrLowerCharacter(ch)) {
            return true;
         }
         else {
            idx2 = idx2 - 1;
         }
      }

      return false;
   }

   /**
    * Check if a character is a Thai character which may appear in another
    * character's upper or lower position
    */
   private boolean isThaiUpperOrLowerCharacter(char c) {
      if(!isThaiCharacter(c)) {
         return false;
      }

      // @by yanie: according to MS spec for Thai
      // if Above1, yes
      if(c == 0xE31 || c == 0xE34 || c == 0xE35 || c == 0xE36 || c == 0xE37) {
         return true;
      }

      // if Above2, yes
      if(c == 0xE47 || c == 0xE4D) {
         return true;
      }

      // if Above3, yes
      if(c == 0xE48 || c == 0xE49 || c == 0xE4A || c == 0xE4B) {
         return true;
      }

      // if Above4, yes
      if(c == 0xE4C || c == 0xE4E) {
         return true;
      }

      // if Below1, yes
      if(c == 0xE38 || c == 0xE39) {
         return true;
      }

      // if Below2, yes
      if(c == 0xE3A) {
         return true;
      }

      return false;
   }

   /**
    * Check if a character is Thai character
    */
   private boolean isThaiCharacter(char c) {
      // according to unicode-standard-6.0
      return c >= 0xE00 && c <= 0xE7F;
   }

   /**
    * Return the string width.
    */
   protected float stringWidth(String str) {
      if(afm == null || (font != null &&
            afm instanceof AFontMetrics && ((AFontMetrics) afm).getSize() != font.getSize()))
      {
         afm = AFManager.getFontMetrics(getFontName(font), font.getSize());

         // make sure a fm is always found
         if(afm == null) {
            afm = Common.getFractionalFontMetrics(font);
         }
      }

      return afm.stringWidth(str);
   }

   /**
    * Draws the specified characters using the current font and color.
    * @param data the array of characters to be drawn
    * @param offset the start offset in the data
    * @param length the number of characters to be drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @see #drawString
    * @see #drawBytes
    */
   @Override
   public void drawChars(char[] data, int offset, int length, int x, int y) {
      debug(pg, "%drawChars");
      drawString(new String(data, offset, length), x, y);
   }

   /**
    * Draws the specified bytes using the current font and color.
    * @param data the data to be drawn
    * @param offset the start offset in the data
    * @param length the number of bytes that are drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @see #drawString
    * @see #drawChars
    */
   @Override
   public void drawBytes(byte[] data, int offset, int length, int x, int y) {
      debug(pg, "%drawBytes");
      drawString(new String(data, offset, length), x, y);
   }

   /**
    * Try to write the image as jpeg.
    */
   private boolean doJpeg(Image img, int x, int y, int width, int height,
                          Color bgcolor) {
      try {
         if(img instanceof MetaImage) {
            String path = ((MetaImage) img).getImageLocation().getPath().
               toLowerCase();

            if(height <= 0 || width <= 0) {
               height = img.getHeight(null);
               width = img.getWidth(null);
            }

            if(path.endsWith("jpg") || path.endsWith("jpeg")) {
               int imageId = emitImage((MetaImage) img, x, y,
                  new Dimension(width, height), bgcolor);

               // didn't draw
               if(imageId < 0) {
                  return false;
               }

               y = (int) transformY(y);
               x = (int) transformX(x);

               imgSet.add("/Im" + imageId + " " + imageId + " 0 R");
               gsave();
               pg.println(toString(width) + " 0 0 " + toString(height) + " " +
                  toString(x) + " " + toString(y - height) + " cm");
               pg.println("/Im" + imageId + " Do");
               grestore();

               return true;
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to encode JPEG image", ex);
      }

      return false;
   }

   /**
    * Draw image.
    */
   public boolean doImage(Image img, int x, int y, int width, int height,
                          ImageObserver observer, Color bgcolor)
   {
      if(!doJpeg(img, x, y, width, height, bgcolor)) {
         // This class fetches the pixels in its constructor.
         PixelConsumer pc = new PixelConsumer(img);
         return doImage(pc, x, y, width, height, observer, bgcolor);
      }

      return true;
   }

   /**
    * Draw image.
    */
   public boolean doImage(Image img, int x, int y, int width, int height,
                          int sx1, int sy1, int sx2, int sy2,
                          ImageObserver observer, Color bgcolor)
   {
      // This class fetches the pixels in its constructor.
      PixelConsumer pc = new PixelConsumer(img, sx1, sy1, sx2, sy2);

      return doImage(pc, x, y, width, height, observer, bgcolor);
   }

   /**
    * Draw image to pdf.
    */
   boolean doImage(PixelConsumer pc, int x, int y, int width,
                   int height, ImageObserver observer, Color bgcolor) {
      Dimension isize = new Dimension(width, height);
      int imageId = emitImage(pc, x, y, isize, observer, bgcolor, false);

      // didn't draw
      if(imageId < 0) {
         return false;
      }

      y = (int) transformY(y);
      x = (int) transformX(x);

      imgSet.add("/Im" + imageId + " " + imageId + " 0 R");
      gsave();
      pg.println(toString(isize.width) + " 0 0 -" + toString(isize.height) +
         " " + toString(x) + " " + toString(y) + " cm");
      pg.println("/Im" + imageId + " Do");
      grestore();

      return true;
   }

   /**
    * Create an image object.
    * @return image id.
    */
   private int emitImage(PixelConsumer pc, int x, int y, Dimension isize,
                         ImageObserver observer, Color bgcolor, boolean pattern)
   {
      startPage();
      debug(pg, "%doImage");

      y = (int) transformY(y);
      x = (int) transformX(x);

      ImageInfoCache iobj = imgmap.get(pc.getKey());
      int imageId = 1;

      if(iobj != null) {
         imageId = iobj.imageId;
         checkTextObj(false);
         if(isize.height == 0 || isize.width == 0) {
            isize.height = iobj.height;
            isize.width = iobj.width;
         }
      }
      else {
         // null image, ignore
         pc.produce(true);

         if(pc.width == 0 || pc.height == 0) {
            return -1;
         }

         checkTextObj(false);
         // compute image size. First of all, if width or height is 0,
         // image is 1:1.
         if(isize.height == 0 || isize.width == 0) {
            isize.height = pc.height;
            isize.width = pc.width;
         }

         imageId = getNextObjectID();

         // caching can't be too large, otherwise it may generate out of mem
         if(imgmap.size() > 10) {
            imgmap.clear();
         }

         imgmap.put(pc.getKey(), new ImageInfoCache(imageId, pc.height, pc.width));
      }

      if(iobj == null) {
         int sizeId = getNextObjectID();

         // temp space
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         final int transparent = 0xFFFEFFFF;
         boolean isMask = false;

         for(int i = pc.height - 1; i >= 0; i--) {
            for(int j = 0; j < pc.width; j++) {
               int n = pc.pix[j][i];

               // handle transparency
               if(pc.smask == null) {
                  if((n & 0xFF000000) == 0) {
                     if(bgcolor == null) {
                        isMask = true;
                        n = transparent;
                     }
                     else {
                        n = bgcolor.getRGB();
                     }
                  }
                  // @by larryl, if the real color is same as transparent,
                  // set to white
                  else if(n == transparent) {
                     n = 0xFFFFFF;
                  }
               }

               buf.write((byte) ((n & 0xFF0000) >> 16));
               buf.write((byte) ((n & 0xFF00) >> 8));
               buf.write((byte) (n & 0xFF));
            }
         }

         boolean jpeg = false;

         // use jpeg for large image to reduce size. (50143)
         if(pc.smask == null && !isMask && !pattern &&
            pc.width * pc.height > 1024 * 1024)
         {
            BufferedImage img = CoreTool.getBufferedImage(prepareJpeg(pc.getImage()));

            try {
               ByteArrayOutputStream buf2 = new ByteArrayOutputStream();
               ImageIO.write(img, "JPG", buf2);
               buf = buf2;
               jpeg = true;
            }
            catch(IOException e) {
               LOG.info("Failed to convert image to JPEG: " + e, e);
            }
         }

         others.markObject(imageId);
         others.println(imageId + " 0 obj");
         others.println("<<");
         others.println("/Type /XObject");
         others.println("/Subtype /Image");
         others.println("/Name /Im" + imageId);
         others.println("/Width " + pc.width);
         others.println("/Height " + pc.height);
         others.println("/BitsPerComponent 8");
         others.println("/ColorSpace /DeviceRGB");

         int smaskID = -1;

         if(pc.smask != null) {
            smaskID = getNextObjectID();
            others.println("/SMask " + smaskID + " 0 R");
         }
         else if(isMask) {
            // @by larryl, transparency color set to #FEFFFF
            others.println("/Mask [254 254 255 255 255 255]");
         }

         if(jpeg) {
            if(ascii) {
               others.println("/Filter [ /ASCII85Decode /DCTDecode ]");
            }
            else {
               others.println("/Filter [ /DCTDecode ]");
            }
         }
         else if(compressImg) {
            if(ascii) {
               others.println("/Filter [ /ASCII85Decode /FlateDecode ]");
            }
            else {
               others.println("/Filter [ /FlateDecode ]");
            }
         }
         else if(ascii) {
            others.println("/Filter /ASCII85Decode");
         }

         others.println("/Length " + sizeId + " 0 R");
         others.println(">>");

         int objlen = writeStream(buf, imageId, compressImg && !jpeg);
         others.println("endobj");
         others.markObject(sizeId);
         others.println(sizeId + " 0 obj");
         others.println(Integer.toString(objlen));
         others.println("endobj");

         if(smaskID != -1) {
            sizeId = getNextObjectID();
            others.markObject(smaskID);
            others.println(smaskID + " 0 obj");
            others.println("<<");
            others.println("/Subtype /Image");
            others.println("/ColorSpace /DeviceGray");

            if(compressImg) {
               if(ascii) {
                  others.print("/Filter [ /ASCII85Decode /FlateDecode ]");
               }
               else {
                  others.print("/Filter/FlateDecode");
               }
            }
            else if(ascii) {
               others.print("/Filter /ASCII85Decode");
            }

            others.print("/Type /XObject");
            others.print("/Length " + sizeId + " 0 R");
            others.print("/BitsPerComponent 8");
            others.print("/Width " + pc.width);
            others.print("/Height " + pc.height);
            others.println(">>");
            buf = new ByteArrayOutputStream();

            for(int i = pc.pixelh - 1; i >= 0; i--) {
               for(int j = 0; j < pc.pixelw; j++) {
                  buf.write(pc.smask[pc.pixelw * i + j]);
               }
            }

            objlen = writeStream(buf, imageId, compressImg);
            others.println("endobj");
            others.markObject(sizeId);
            others.println(sizeId + " 0 obj");
            others.println(Integer.toString(objlen));
            others.println("endobj");
         }
      }

      return imageId;
   }

   // if image contains transparency, ImageIO will write the 4 channels to jpeg,
   // which results in reader to interprets it as cmyk.
   private static BufferedImage prepareJpeg(Image img0) {
      BufferedImage img = CoreTool.getBufferedImage(img0);
      int width = img.getWidth();
      int height = img.getHeight();
      BufferedImage copy = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);

      Graphics2D g = copy.createGraphics();
      g.scale(1, -1);
      g.translate(0, -height);
      g.drawImage(img, 0, 0, null);
      g.dispose();

      return copy;
   }

   private int writeStream(ByteArrayOutputStream buf, int imageId, boolean compressImg) {
      others.println("stream");
      byte[] coded;

      if(compressImg) {
         if(ascii) {
            coded = Encoder.encodeAscii85(Encoder.deflate(buf.toByteArray()));
         }
         else {
            coded = Encoder.deflate(buf.toByteArray());
         }
      }
      else if(ascii) {
         coded = Encoder.encodeAscii85(buf.toByteArray());
      }
      else {
         coded = buf.toByteArray();
      }

      if(ascii) {
         ByteArrayOutputStream ascout = new ByteArrayOutputStream();

         for(int i = 0; i < coded.length; i += charsPerRow) {
            if(i > 0) {
               ascout.write(0xA);
            }

            ascout.write(coded, i,
                         Math.min(coded.length - i, charsPerRow));
         }

         ascout.write('~');
         ascout.write('>');
         coded = ascout.toByteArray();
      }

      coded = encrypt(imageId, 0, coded);

      try {
         others.write(coded);
      }
      catch(Exception e) {
         closed = true;
         LOG.error("Failed to write binary data", e);
         throw new RuntimeException("Write failed: " + e);
      }

      others.println("endstream");
      return coded.length;
   }

   /**
    * Create an image object from a jpeg.
    * @return image id.
    */
   protected int emitImage(MetaImage image, int x, int y, Dimension isize,
                           Color bgcolor) throws IOException {
      startPage();
      debug(pg, "%doImage");

      y = (int) transformY(y);
      x = (int) transformX(x);

      checkTextObj(false);

      Object key = image.getImageLocation();
      ImageInfoCache iobj = imgmap.get(key);
      int imageId = 1;

      if(iobj != null) {
         imageId = iobj.imageId;
      }
      else {
         imageId = getNextObjectID();
         imgmap.put(key, new ImageInfoCache(imageId, 0, 0));
      }

      if(iobj == null) {
         int sizeId = getNextObjectID();

         others.markObject(imageId);
         others.println(imageId + " 0 obj");
         others.println("<<");
         others.println("/Type /XObject");
         others.println("/Subtype /Image");
         others.println("/Name /Im" + imageId);
         others.println("/Width " + isize.width);
         others.println("/Height " + isize.height);
         others.println("/BitsPerComponent 8");
         others.println("/ColorSpace /DeviceRGB");

         if(ascii) {
            others.println("/Filter [ /ASCII85Decode /DCTDecode ]");
         }
         else {
            others.println("/Filter /DCTDecode");
         }

         others.println("/Length " + sizeId + " 0 R");
         others.println(">>");

         others.println("stream");
         int osize = others.getOffset();

         // temp space
         ByteArrayOutputStream buf = new ByteArrayOutputStream();
         byte[] coded = new byte[4096];

         if("true".equals(SreeEnv.getProperty("pdf.transcode.jpeg"))) {
            ImageReader ireader =
               ImageIO.getImageReadersByFormatName("jpeg").next();
            ImageWriter iwriter =
               ImageIO.getImageWritersByFormatName("jpeg").next();

            ImageWriteParam param = iwriter.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Float.parseFloat(
               SreeEnv.getProperty("pdf.jpeg.quality", "0.85")));
            param.setProgressiveMode(ImageWriteParam.MODE_DISABLED);

            try(ImageInputStream iinput =
                   new MemoryCacheImageInputStream(image.getInputStream());
                ImageOutputStream ioutput = new MemoryCacheImageOutputStream(buf))
            {
               ireader.setInput(iinput);
               iwriter.setOutput(ioutput);

               BufferedImage bimage = ireader.read(0);
               IIOImage oimage = new IIOImage(bimage, null, null);
               iwriter.write(null, oimage, param);
            }
         }
         else {
            try(InputStream input = new BufferedInputStream(image.getInputStream())) {
               int cnt;

               while((cnt = input.read(coded)) >= 0) {
                  buf.write(coded, 0, cnt);
               }
            }
         }

         if(ascii) {
            coded = Encoder.encodeAscii85(buf.toByteArray());
         }
         else {
            coded = buf.toByteArray();
         }

         coded = encrypt(imageId, 0, coded);

         try {
            if(ascii && !isEncrypted()) {
               for(int i = 0; i < coded.length; i += charsPerRow) {
                  if(i > 0) {
                     others.println("");
                  }

                  others.write(coded, i,
                     Math.min(coded.length - i, charsPerRow));
               }
            }
            else {
               others.write(coded);
            }
         }
         catch(Exception e) {
            closed = true;
            LOG.error("Failed to emit image", e);
            throw new RuntimeException("Write failed: " + e);
         }

         if(ascii && !isEncrypted()) {
            others.println("~>");
         }

         int objlen = others.getOffset() - osize;

         others.println("endstream");
         others.println("endobj");

         others.markObject(sizeId);
         others.println(sizeId + " 0 obj");
         others.println(Integer.toString(objlen));
         others.println("endobj");
      }

      return imageId;
   }

   /**
    * Draws the specified image at the specified coordinate (x, y). If the
    * image is incomplete the image observer will be notified later.
    * @param img the specified image to be drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @param observer notifies if the image is complete or not
    * @see Image
    * @see ImageObserver
    */
   @Override
   public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
      debug(pg, "%drawImage-1");
      return doImage(img, x, y, 0, 0, observer, null);
   }

   /**
    * Draws the specified image inside the specified rectangle. The image is
    * scaled if necessary. If the image is incomplete the image observer
    * will be notified later.
    * @param img the specified image to be drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param observer notifies if the image is complete or not
    * @see Image
    * @see ImageObserver
    */
   @Override
   public boolean drawImage(Image img, int x, int y, int width, int height,
                            ImageObserver observer) {
      debug(pg, "%drawImage-2");
      return doImage(img, x, y, width, height, observer, null);
   }

   /**
    * Draws the specified image at the specified coordinate (x, y). If the
    * image is incomplete the image observer will be notified later.
    * @param img the specified image to be drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @param bgcolor the background color
    * @param observer notifies if the image is complete or not
    * @see Image
    * @see ImageObserver
    */

   @Override
   public boolean drawImage(Image img, int x, int y, Color bgcolor,
                            ImageObserver observer) {
      debug(pg, "%drawImage-3");
      return doImage(img, x, y, 0, 0, observer, bgcolor);
   }

   /**
    * Draws the specified image inside the specified rectangle. The image is
    * scaled if necessary. If the image is incomplete the image observer
    * will be notified later.
    * @param img the specified image to be drawn
    * @param x the x coordinate
    * @param y the y coordinate
    * @param width the width of the rectangle
    * @param height the height of the rectangle
    * @param bgcolor the background color
    * @param observer notifies if the image is complete or not
    * @see Image
    * @see ImageObserver
    * NOTE: PDFPrinter ignores the background color.
    */
   @Override
   public boolean drawImage(Image img, int x, int y,
                            int width, int height, Color bgcolor,
                            ImageObserver observer) {
      debug(pg, "%drawImage-4");
      return doImage(img, x, y, width, height, observer, bgcolor);
   }

   /**
    * Draws as much of the specified area of the specified image as is
    * currently available, scaling it on the fly to fit inside the
    * specified area of the destination drawable surface. Transparent pixels
    * do not affect whatever pixels are already there.
    * <p>
    * This method returns immediately in all cases, even if the
    * image area to be drawn has not yet been scaled, dithered, and converted
    * for the current output device.
    * If the current output representation is not yet complete then
    * <code>drawImage</code> returns <code>false</code>. As more of
    * the image becomes available, the process that draws the image notifies
    * the specified image observer.
    * <p>
    * This method always uses the unscaled version of the image
    * to render the scaled rectangle and performs the required
    * scaling on the fly. It does not use a cached, scaled version
    * of the image for this operation. Scaling of the image from source
    * to destination is performed such that the first coordinate
    * of the source rectangle is mapped to the first coordinate of
    * the destination rectangle, and the second source coordinate is
    * mapped to the second destination coordinate. The subimage is
    * scaled and flipped as needed to preserve those mappings.
    * @param       img the specified image to be drawn
    * @param       dx1 the <i>x</i> coordinate of the first corner of the
    *                    destination rectangle.
    * @param       dy1 the <i>y</i> coordinate of the first corner of the
    *                    destination rectangle.
    * @param       dx2 the <i>x</i> coordinate of the second corner of the
    *                    destination rectangle.
    * @param       dy2 the <i>y</i> coordinate of the second corner of the
    *                    destination rectangle.
    * @param       sx1 the <i>x</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sy1 the <i>y</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sx2 the <i>x</i> coordinate of the second corner of the
    *                    source rectangle.
    * @param       sy2 the <i>y</i> coordinate of the second corner of the
    *                    source rectangle.
    * @param       observer object to be notified as more of the image is
    *                    scaled and converted.
    */
   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2,
                            ImageObserver observer) {
      debug(pg, "%drawImage-5");
      return doImage(img, dx1, dy1, dx2 - dx1 + 1, dy2 - dy1 + 1, sx1, sy1,
         sx2, sy2, observer, null);
   }

   /**
    * Draws as much of the specified area of the specified image as is
    * currently available, scaling it on the fly to fit inside the
    * specified area of the destination drawable surface.
    * <p>
    * Transparent pixels are drawn in the specified background color.
    * This operation is equivalent to filling a rectangle of the
    * width and height of the specified image with the given color and then
    * drawing the image on top of it, but possibly more efficient.
    * <p>
    * This method returns immediately in all cases, even if the
    * image area to be drawn has not yet been scaled, dithered, and converted
    * for the current output device.
    * If the current output representation is not yet complete then
    * <code>drawImage</code> returns <code>false</code>. As more of
    * the image becomes available, the process that draws the image notifies
    * the specified image observer.
    * <p>
    * This method always uses the unscaled version of the image
    * to render the scaled rectangle and performs the required
    * scaling on the fly. It does not use a cached, scaled version
    * of the image for this operation. Scaling of the image from source
    * to destination is performed such that the first coordinate
    * of the source rectangle is mapped to the first coordinate of
    * the destination rectangle, and the second source coordinate is
    * mapped to the second destination coordinate. The subimage is
    * scaled and flipped as needed to preserve those mappings.
    * @param       img the specified image to be drawn
    * @param       dx1 the <i>x</i> coordinate of the first corner of the
    *                    destination rectangle.
    * @param       dy1 the <i>y</i> coordinate of the first corner of the
    *                    destination rectangle.
    * @param       dx2 the <i>x</i> coordinate of the second corner of the
    *                    destination rectangle.
    * @param       dy2 the <i>y</i> coordinate of the second corner of the
    *                    destination rectangle.
    * @param       sx1 the <i>x</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sy1 the <i>y</i> coordinate of the first corner of the
    *                    source rectangle.
    * @param       sx2 the <i>x</i> coordinate of the second corner of the
    *                    source rectangle.
    * @param       sy2 the <i>y</i> coordinate of the second corner of the
    *                    source rectangle.
    * @param       bgcolor the background color to paint under the
    *                    non-opaque portions of the image.
    * @param       observer object to be notified as more of the image is
    *                    scaled and converted.
    */
   @Override
   public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                            int sx1, int sy1, int sx2, int sy2, Color bgcolor,
                            ImageObserver observer) {
      debug(pg, "%drawImage-6");
      return doImage(img, dx1, dy1, dx2 - dx1 + 1, dy2 - dy1 + 1, sx1, sy1,
         sx2, sy2, observer, bgcolor);
   }

   /**
    * Disposes of this graphics context. The Graphics context cannot be
    * used after being disposed of. This must be called EXPLICITLY
    * after a Graphics object is no longer used.
    */
   @Override
   public void dispose() {
      // don't dispose empty pages
      if(!inited || closed) {
         return;
      }

      checkTextObj(false);

      // make sure all saved context has being restored
      while(savelevel > 0) {
         grestore();
      }

      stroke = new BasicStroke();
      brush = null;

      // cloned subgraphics does not close a page
      if(cloned != null) {
         debug(pg, "%dispose-sub");
         inited = false;
         return;
      }

      debug(pg, "%dispose");
      pg.flush();
      byte[] data = pgBuf.toByteArray();

      int osize = os.getOffset();

      if(compressText) {
         if(!ascii) {
            data = Encoder.deflate(data);

            try {
               os.write(encrypt(contentId, 0, data));
            }
            catch(Exception e) {
               closed = true;
               LOG.error("Failed to flush compressed page buffer", e);
               throw new RuntimeException("Write failed: " + e);
            }
         }
         else {
            data = Encoder.encodeAscii85(Encoder.deflate(data));
            byte[] out = new byte[] {};
            byte[] ret = "\n".getBytes();

            for(int i = 0; i < data.length; i += charsPerRow) {
               if(i > 0) {
                  out = paddingByteArray(out, out.length + ret.length, ret);
               }

               byte[] d = new byte[Math.min(data.length - i, charsPerRow)];
               System.arraycopy(data, i, d, 0, d.length);
               out = paddingByteArray(out, out.length + d.length, d);
            }

            ret = "~>".getBytes();
            out = paddingByteArray(out, out.length + ret.length, ret);

            try {
               os.write(encrypt(contentId, 0, out));
            }
            catch(Exception e) {
               closed = true;
               LOG.error("Failed to flush compressed ASCII page buffer", e);
               throw new RuntimeException("Write failed: " + e);
            }
         }
      }
      else {
         try {
            os.write(encrypt(contentId, 0, data));
         }
         catch(Exception e) {
            closed = true;
            LOG.error("Failed to flush page buffer", e);
            throw new RuntimeException("Write failed: " + e);
         }
      }

      pgBuf.reset();

      int length = os.getOffset() - osize;

      resourceId = getNextObjectID();

      os.println("endstream");
      os.println("endobj");

      xrefs.put(lengthId, os.getOffset());
      os.println(lengthId + " 0 obj");
      os.println(length + "");
      os.println("endobj");

      ArrayList<Integer> alphaIds = new ArrayList<>();

      for(int i = 0; i < alpHolder.getPageAlphasCount(); i++) {
         int alpId = getNextObjectID();
         xrefs.put(alpId, os.getOffset());
         os.println(alpId + " 0 obj");
         os.println("<<");
         os.println("/Type /ExtGState");
         os.println("/ca " + alpHolder.getAlphaInPage(i));
         os.println("/CA " + alpHolder.getAlphaInPage(i));
         os.println(">>");
         os.println("endobj");
         alphaIds.add(alpId);

         if(alpHolder.getAlphaObjId(alpHolder.getAlphaInPage(i)) == -1) {
            alpHolder.putAlphaObjId(alpHolder.getAlphaInPage(i), alpId);
         }
      }

      int pageId = pageIds.get(pageIds.size() - 1);

      xrefs.put(pageId, os.getOffset());
      os.println(pageId + " 0 obj");
      os.println("<<");
      os.println("/Type /Page");
      os.println("/Parent " + pagesId + " 0 R");
      os.println("/Resources " + resourceId + " 0 R");
      os.println("/Contents " + contentId + " 0 R");
      os.println("/MediaBox [0 0 " + pagewidth + " " + pageheight + "]");
      os.println("/Group<</Type/Group/S/Transparency/CS/DeviceRGB>>");
      os.println("/Annots " + annotsIds.get(annotsIds.size() - 1) + " 0 R");

      if(isAccessible()) {
         for(int i = structureTree.kids.size() - 1; i >= 0; i--) {
            if(structureTree.kids.get(i).type == StructureType.Part) {
               os.println("/StructParents " + i);
               break;
            }
         }

         os.println("/Tabs /S");
      }

      os.println(">>");
      os.println("endobj");

      xrefs.put(resourceId, os.getOffset());
      os.println(resourceId + " 0 obj");
      os.println("<<");
      os.print("/ProcSet [/PDF");

      if(fnList.size() > 0) {
         os.print(" /Text");
      }

      if(imgSet.size() > 0) {
         os.print(" /ImageC");
      }

      os.println("]");
      os.println("/ExtGState <<");
      os.println("/GS1 " + egsId + " 0 R");

      int startIndex = alpHolder.getTotalAlphasCount() -
         alpHolder.getPageAlphasCount();

      for(int i = 0; i < alphaIds.size(); i++) {
         int objid = alphaIds.get(i).intValue();
         os.println("/GS" + (i + 1 + startIndex) + " " + objid + " 0 R");
      }

      for(int i = 0; i < alpHolder.getDefinedAlphasCount(); i++) {
         float alpha = alpHolder.getAlphaInDefined(i);
         int index = alpHolder.getAlphaIndex(alpha);
         int objid = alpHolder.getAlphaObjId(alpha);
         os.println("/GS" + (1 + index) + " " + objid + " 0 R");
      }

      alpHolder.clearPageAlphas();
      alpHolder.clearDefinedAlphas();
      os.println(">>");

      if(fnList.size() > 0) {
         os.println("/Font <<");

         for(int i = 0; i < fnList.size(); i++) {
            os.println(fnList.get(i));
         }

         os.println(">>");
      }

      if(imgSet.size() > 0) {
         os.println("/XObject <<");
         Iterator<String> iter = imgSet.iterator();

         while(iter.hasNext()) {
            os.println(iter.next());
         }

         os.println(">>");
         imgSet.clear();
      }

      writeAdditionalResources(os);

      os.println(">>");
      os.println("endobj");

      os.flush();

      writeOthers();
      inited = false;
   }

   /**
    * Close the pdf output stream. This MUST be called to complete the file.
    */
   @Override
   public void close() {
      if(!closed) {
         writeAnnotations();
         writeOthers();
         emitTrailer();
         // fix bug1305327896993 because the two 16-band data will lead to the
         // pdf file has a corrupted %%EOF marker, converting PDF with
         // StyleRerport to PCL will cause a warring side: "The file that it
         // does not conform to Adobe's published PDF specification."
         // os.write(0x1a); // ^Z EOF on Win32
         // os.write(0x04); // EOT
         os.flush();
         os.close();
         closed = true;
         imgmap.clear();
         GTool.setIsPDF(false);
      }
   }

   /**
    * Emit a PDF command to the output stream.
    */
   public void emit(String cmd) {
      pg.println(cmd);
   }

   /**
    * PDF save command.
    */
   private void gsave() {
      checkTextObj(false);
      pg.println("q");
      savelevel++;
      Info info = null;

      if(savelevel < g_infos.length) {
         info = g_infos[savelevel];

         if(info == null) {
            info = new Info();
            g_infos[savelevel] = info;
         }
      }
      else {
         Info[] og_infos = g_infos;
         int nsize = Math.max((int) (g_infos.length * 1.5), savelevel);
         g_infos = new Info[nsize];
         System.arraycopy(og_infos, 0, g_infos, 0, og_infos.length);
         info = new Info();
         g_infos[savelevel] = info;
      }

      info.clr = clr;
      info.alpha = alpha;
      info.font = (font == null) ? defFont : font;
      info.ofont = ofont;
      info.clipping = clipping;
      gs_info.push(info);
   }

   /**
    * PDF save. Save graphics context up-to the specified level.
    */
   public void gsave(int level) {
      if(savelevel > level) {
         LOG.warn("PDF save level overflowed.");
      }

      while(savelevel < level) {
         gsave();
      }
   }

   /**
    * PDF restore command.
    */
   private void grestore() {
      if(savelevel > 0) {
         Info info = gs_info.pop();
         clr = info.clr;
         font = info.font;
         ofont = info.ofont;
         alpha = info.alpha;
         clipping = info.clipping;
         checkTextObj(false);
         pg.println("Q");
         savelevel--;
      }
   }

   /**
    * To envelope the info for performace tune.
    */
   private static class Info {
      Color clr;
      Font font;
      Font ofont;
      float alpha;
      Rectangle2D clipping;
   }

   /**
    * Restore graphics context up-to the specified level.
    */
   public int grestore(int level) {
      int cnt = 0;

      while(savelevel >= level) {
         grestore();
         cnt++;
      }

      if(cnt > 0) {
         // re-set clipping, color, and font
         setStroke(stroke);
         setPaint(brush);
      }

      return cnt;
   }

   /**
    * Adds an annotation for a link.
    *
    * @param link       the target of the hyperlink.
    * @param linkBounds the bounds of the linked element.
    * @param pgH        the page height.
    * @param actions    map of action ids to internal targets.
    * @param page       the page index (zero-based).
    * @param flush      <tt>true</tt> to flush the object buffer.
    *
    * @return the ID of the link annotation or <tt>null</tt> if none was
    *         created.
    */
   public Integer addLink(Hyperlink.Ref link, Rectangle linkBounds,
                          double pgH, Map<Integer, String> actions,
                          int page, boolean flush)
   {
      String hlink = Util.createURL(link);
      double llx = linkBounds.x;
      double lly = pgH - linkBounds.y - linkBounds.height;
      double urx = llx + linkBounds.width;
      double ury = lly + linkBounds.height;
      int linkid = 0;
      String linkobj = null;

      if(hlink.startsWith("#")) {
         int actionid = getNextObjectID();

         actions.put(actionid, hlink.substring(1));
         linkobj = "<<\n/Type /Annot\n/Subtype /Link\n" + "/Rect [ " + llx +
            " " + lly + " " + urx + " " + ury + " ]\n/A " + actionid +
            " 0 R\n/Border [ 0 0 0 ]\n/H /I\n";
         linkid = getNextObjectID();

         if(isAccessible()) {
            linkobj += "/StructParent " + structureTree.kids.size() + "\n";

            if(link.getToolTip() != null) {
               linkobj += "/Contents (" + link.getToolTip() + ")";
            }
         }

         linkobj += ">>";
      }
      else if(link.getLinkType() == Hyperlink.WEB_LINK) {
         linkobj = "<<\n/A << /S /URI /URI (" + hlink +
            ")>>\n/Type /Annot\n/Subtype /Link\n/Rect [ " + llx + " " + lly +
            " " + urx + " " + ury + " ]\n/Border [ 0 0 0 ]\n/H /I\n";
         linkid = getNextObjectID();

         if(isAccessible()) {
            linkobj += "/StructParent " + structureTree.kids.size() + "\n";

            if(link.getToolTip() != null) {
               linkobj += "/Contents (" + link.getToolTip() + ")\n";
            }
         }

         linkobj += ">>";
      }

      if(linkobj != null) {
         addAnnotation(linkid, page);
         addObject(linkid, linkobj, false);
         return linkid;
      }

      return null;
   }

   /**
    * Add an annotation to the PDF.
    * @param id the annotation id
    * @param page the page the annotation is being added to
    */
   public void addAnnotation(int id, int page) {
      Integer annotsId = annotsIds.get(page);
      List<Integer> v = annots.get(annotsId);

      if(v == null) {
         v = new ArrayList<>();
      }

      v.add(id);
      annots.put(annotsId, v);
   }

   /**
    * Add an object to the PDF.
    */
   public void addObject(int id, String obj) {
      addObject(id, obj, true);
   }

   /**
    * Add an object to the PDF.
    */
   public void addObject(int id, String obj, boolean flush) {
      others.markObject(id);
      others.println(id + " 0 obj");
      others.println(encrypt(id, 0, obj, false));
      others.println("endobj");

      if(flush) {
         writeOthers(); // flush out others buffer
      }
   }

   /**
    * Write out the annotations.
    */
   protected void writeAnnotations() {
      for(Map.Entry<Integer, List<Integer>> e : annots.entrySet()) {
         Integer key = e.getKey();

         others.markObject(key);
         others.println(key + " 0 obj");
         others.println("[");

         for(Integer id : e.getValue()) {
            others.println(id + " 0 R\n");
         }

         others.println("]");
         others.println("endobj");
      }
   }

   /**
    * flush out the others buffer.
    */
   protected void writeOthers() {
      try {
         // @by billh, to append radio group infos behind radio infos is not
         // a good idea, which is not consistent with pdf1.3 specification,
         // but it seems that the logic works, and to append radio group infos
         // ahead of radio infos will break current elegant structure, so I
         // will let it be until new problem emerges
         Iterator<RadioButtonGroup> iterator = radiomap.values().iterator();

         while(iterator.hasNext()) {
            RadioButtonGroup rgroup = iterator.next();
            rgroup.writePDF(others, this);
         }

         radiomap.clear();

         others.flush();
         Map<Integer, Integer> objs = others.getObjectMarks();
         int offset = os.getOffset();

         for(Map.Entry<Integer, Integer> e : objs.entrySet()) {
            xrefs.put(e.getKey(), e.getValue() + offset);
         }

         os.write(othersBuf.toByteArray());
         others.reset();
         othersBuf.reset();
      }
      catch(Exception e) {
         // mark this so it will not try to write to the output stream
         // if the outputstream failed. If it does, it may write to the wrong
         // stream if an app server pools socket connections
         closed = true;
         throw new RuntimeException("Write failed: " + e);
      }
   }

   /**
    * This function is called to allow additional resources to be written
    * to the current page.
    */
   protected void writeAdditionalResources(PrintWriter os) {
      writePatternResources(os);
   }

   /**
    * Initialize file.
    */
   public void startDoc() {
      if(!started) {
         started = true;
         pmargin = ReportSheet.getPrinterMargin();
         alpHolder = new AlphaHolder();

         os.println("%PDF-" + getPDFVersion());
         // for chinese os
         //os.println("%????");

         egsId = getNextObjectID();
         xrefs.put(egsId, os.getOffset());
         os.println(egsId + " 0 obj");
         os.println("<<");
         os.println("/Type /ExtGState");
         os.println("/SA false");
         os.println("/OP false");
         os.println("/op false");
         os.println("/HT /Default");
         os.println("/ca 1.0");
         os.println("/CA 1.0");
         os.println(">>");
         os.println("endobj");
         alpHolder.putAlphaObjId(1.0f, egsId);
      }
   }

   /**
    * Write the encoding object
    */
   protected int writeEncoding(int idx) {
      int encodingId = getNextObjectID();

      others.markObject(encodingId);
      others.println(encodingId + " 0 obj");
      others.println("<<");
      others.println("/Type /Encoding");
      others.println("/BaseEncoding /WinAnsiEncoding");
      others.println("/Differences " + charname[idx]);
      others.println(">>");
      others.println("endobj");

      return encodingId;
   }

   /**
    * Check if in BT-ET block.
    */
   private boolean isTextObj() {
      // sub-graphics use the top level graphics context for BT-ET
      if(cloned != null) {
         return getRoot().textObj;
      }

      return textObj;
   }

   /**
    * Set the line width in points.
    */
   public void setLineWidth(float w) {
      checkTextObj(false);

      builder.setLength(0);
      builder.append(toString(w)).append(" w\n");
      pg.print(builder.toString());
   }

   /**
    * Make sure currently in the text segment (BT-ET).
    * @param txt true to make in a text segment or false to end text object.
    */
   public void checkTextObj(boolean txt) {
      // sub-graphics use the top level graphics context for BT-ET
      if(cloned != null) {
         PDFPrinter root = getRoot();

         if(root.textObj != txt) {
            root.textObj = txt;

            if(root.textObj) {
               pg.println("BT");

               if(psFontName == null) {
                  emitFont(font);
               }
            }
            else {
               pg.println("ET");
               psFontName = null;
            }
         }

         return;
      }

      if(textObj != txt) {
         startPage();
         textObj = txt;

         if(textObj) {
            pg.println("BT");

            if(psFontName == null) {
               emitFont(font);
            }
         }
         else {
            pg.println("ET");
            psFontName = null;
         }
      }
   }

   /**
    * Return the PDF version of the documents generated by this class.
    * Acrobat 3.0 is PDF 1.2, and Acrobat 4.0 is PDF 1.3.
    * @return PDF version number, e.g., "1.2".
    */
   public String getPDFVersion() {
      return "1.2";
   }

   /**
    * Returns a String object representing this Graphic's value.
    */
   @Override
   public String toString() {
      return getClass().getName() + "[font=" + getFont() + ",color=" +
         getColor() + "]";
   }

   /**
    * Flip Y coords so PDFPrinter looks like Java.
    */
   protected final double transformY(double y) {
      return -y;
   }

   /**
    * Translate Java coordinate to PDF coordinate.
    */
   protected final double transformX(double x) {
      return x;
   }

   /**
    * Encrypt an object string.
    */
   private String encrypt(int objnumber, int gennumber, String str,
                          boolean esc) {
      String s = new String(encrypt(objnumber, gennumber, str.getBytes()));
      return esc ? escapeString(s) : s;
   }

   /**
    * Encrypt an object string.
    */
   private byte[] encrypt(int objnumber, int gennumber, byte[] bytes) {
      if(isEncrypted()) {
         try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            byte[] obj = getReversedByteArray(objnumber);
            byte[] gen = getReversedByteArray(gennumber);

            obj = truncateByteArray(obj, 3);
            gen = truncateByteArray(gen, 2);

            byte[] key = getEncryptKey();

            if(key != null) {
               key = paddingByteArray(key, key.length + obj.length, obj);
               key = paddingByteArray(key, key.length + gen.length, gen);

               int len = key.length;
               key = md5.digest(key);

               key = truncateByteArray(key, len);

               SecretKeySpec secretKey = new SecretKeySpec(key, "RC4");

               Cipher cipher  = Cipher.getInstance("RC4");
               cipher.init(Cipher.ENCRYPT_MODE, secretKey);

               bytes = cipher.doFinal(bytes);
            }
         }
         catch(Throwable e) {
            LOG.warn("Failed to encrypt data", e);
         }
      }

      return bytes;
   }

   /**
    * Get the ecnrypted owner key.
    */
   private byte[] getOwnerKey() {
      byte[] ownerkey = keymaps.get("ownerkey");

      if(ownerkey == null && isEncrypted()) {
         try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            String pass = encrypt.getOwnerPassword();
            pass = (pass == null || pass.equals("")) ?
               encrypt.getUserPassword() : pass;

            byte[] padded = paddingByteArray(pass.getBytes(), 32, paddingArray);

            padded = md5.digest(padded);

            padded = truncateByteArray(padded, 5);

            pass = encrypt.getUserPassword();

            pass = (pass == null) ? "" : pass;

            byte[] padded2 = paddingByteArray(pass.getBytes(), 32, paddingArray);

            SecretKeySpec secretKey = new SecretKeySpec(padded, "RC4");
            Cipher cipher  = Cipher.getInstance("RC4");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            ownerkey = cipher.doFinal(padded2);

            keymaps.put("ownerkey", ownerkey);
         }
         catch(Throwable e) {
            LOG.warn("Failed to get owner key", e);
         }
      }

      return ownerkey;
   }

   /**
    * Get the encrypt key.
    */
   private byte[] getEncryptKey() {
      byte[] encryptkey = keymaps.get("encryptkey");

      if(encryptkey == null && isEncrypted() && getOwnerKey() != null) {
         try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            String key = encrypt.getUserPassword();
            key = (key == null) ? "" : key;

            byte[] padded = paddingByteArray(key.getBytes(), 32, paddingArray);

            md5.update(padded);
            md5.update(getOwnerKey());

            int permission = encrypt.getPermissionCode();

            md5.update((byte) (permission));
            md5.update((byte) (permission >>> 8));
            md5.update((byte) (permission >>> 16));
            md5.update((byte) (permission >>> 24));

            md5.update(getFileID());
            padded = md5.digest();

            encryptkey = truncateByteArray(padded, 5);

            keymaps.put("encryptkey", encryptkey);
         }
         catch(Throwable e) {
            LOG.warn("Failed to get encryption key", e);
         }
      }

      return encryptkey;
   }

   /**
    * Get the encrypted user key.
    */
   private byte[] getUserKey() {
      byte[] userkey = keymaps.get("userkey");

      if(userkey == null && isEncrypted()) {
         try {
            String key = "";
            byte[] padded = paddingByteArray(key.getBytes(), 32, paddingArray);
            byte[] keys = getEncryptKey();

            if(keys != null) {
               SecretKeySpec secretKey = new SecretKeySpec(keys, "RC4");
               Cipher cipher  = Cipher.getInstance("RC4");

               cipher.init(Cipher.ENCRYPT_MODE, secretKey);

               userkey = cipher.doFinal(padded);

               keymaps.put("userkey", userkey);
            }
         }
         catch(Throwable e) {
            LOG.warn("Failed to get user key", e);
         }
      }

      return userkey;
   }

   /**
    * Get the integer byte array, low byte first.
    */
   private byte[] getReversedByteArray(int i) {
      byte[] bytes = new byte[4];

      bytes[0] = (byte) (i & 0x000000FF);
      bytes[1] = (byte) ((i & 0x0000FF00) >>> 8);
      bytes[2] = (byte) ((i & 0x00FF0000) >>> 16);
      bytes[3] = (byte) ((i & 0xFF000000) >>> 24);

      return bytes;
   }

   /**
    * Get the file ID.
    */
   private byte[] getFileID() {
      byte[] fileID = keymaps.get("fileID");

      if(fileID == null) {
         fileID = new byte[16];
         Random random = new Random();

         random.nextBytes(fileID);

         keymaps.put("fileID", fileID);
      }

      return fileID;
   }

   /**
    * Padding or trim an byte array with the specified padding array.
    */
   private byte[] paddingByteArray(byte[] arr, int len, byte[] padding) {
      byte[] ret = null;

      if(arr.length == len) {
         ret = arr;
      }
      else {
         if(arr.length > len) {
            ret = truncateByteArray(arr, len);
         }
         else {
            ret = new byte[len];

            System.arraycopy(arr, 0, ret, 0, arr.length);

            if(padding == null) {
               Arrays.fill(ret, arr.length, ret.length - 1, (byte)0xFF);
            }
            else {
               System.arraycopy(padding, 0, ret, arr.length,
                                ret.length - arr.length);
            }
         }
      }

      return ret;
   }

   /**
    * Truncate an byte array to a given length.
    */
   private byte[] truncateByteArray(byte[] arr, int len) {
      byte[] ret = new byte[len];
      System.arraycopy(arr, 0, ret, 0, len);
      return ret;
   }

   /**
    * Print the trailer of the PDF file.
    */
   private void emitTrailer() {
      emitPages();

      String fileId = toHex(getFileID());

      int metadataId = emitMetadata();
      int encryptId = emitEncrypt();
      int infoId = emitInfo();
      int rootId = emitRoot(metadataId);

      if(isAccessible() && structureTree != null) {
         emitStructure();
      }

      int offset = os.getOffset();

      emitXRef();

      os.println("trailer");
      os.println("<<");
      os.println("/Size " + (xrefs.size() + 1));
      os.println("/Root " + rootId + " 0 R");

      if(isEncrypted() && getOwnerKey() != null && getUserKey() != null) {
         os.println("/Encrypt " + encryptId + " 0 R");
      }

      os.println("/ID [<" +  fileId + "> <" + fileId + ">]");
      os.println("/Info " + infoId + " 0 R");
      os.println(">>");

      os.println("startxref");
      os.println(offset + "");
      os.println("%%EOF");
   }

   /**
    * emit page id.
    */
   private void emitPages() {
      xrefs.put(pagesId, os.getOffset());
      os.println(pagesId + " 0 obj");
      os.println("<<");
      os.println("/Type /Pages");
      os.print("/Kids [");

      for(int i = 0; i < pageIds.size(); i++) {
         if(i > 0) {
            os.print(" ");
         }

         os.print(pageIds.get(i) + " 0 R");
      }

      os.println("]");
      os.println("/Count " + pageIds.size());
      os.println(">>");
      os.println("endobj");
   }

   private int emitMetadata() {
      int metadataId = -1;

      if(isAccessible()) {
         String title;

         if(info == null || info.getTitle() == null) {
            title = "Report";
         }
         else {
            title = Tool.encodeXML(info.getTitle());
         }

         String data = String.format(xmpMetadata, title);
         metadataId = getNextObjectID();

         xrefs.put(metadataId, os.getOffset());
         os.println(metadataId + " 0 obj");
         os.println("<<");
         os.println("/Length " + data.length());
         os.println("/Type /Metadata");
         os.println("/Subtype /XML");
         os.println(">>");
         os.println("stream");
         os.println(data);
         os.println("endstream");
         os.println("endobj");
      }

      return metadataId;
   }

   /**
    * emit encrypt info.
    */
   private int emitEncrypt() {
      int encryptId = -1;

      if(isEncrypted() && getOwnerKey() != null && getUserKey() != null) {
         encryptId = getNextObjectID();

         xrefs.put(encryptId, os.getOffset());
         os.println(encryptId + " 0 obj");
         os.println("<<");

         os.println("/Filter /Standard");
         os.println("/V 1");
         os.println("/R 2");
         os.println("/length 40");
         os.println("/P " + getEncryptInfo().getPermissionCode());
         os.println("/O <" + toHex(getOwnerKey()) + ">");
         os.println("/U <" + toHex(getUserKey()) + ">");

         os.println(">>");
         os.println("endobj");
      }

      return encryptId;
   }

   /**
    * Get the hex string of a byte array.
    */
   private String toHex(byte [] value) {
      StringBuilder buffer = new StringBuilder();

      for(int  i = 0; i < value.length; i++) {
         buffer.append(Integer.toHexString((value[i] >>> 4) & 0x0F));
         buffer.append(Integer.toHexString(value[i] & 0x0F));
      }

      return buffer.toString();
   }

   /**
    * Get the hex string of a byte array.
    */
   private String toHex(char [] value, int start, int size) {
      StringBuilder buffer = new StringBuilder();

      if(value != null && value.length >= start && size > 0) {
         for(int  i = start; i < value.length && i < (start + size); i++) {
            buffer.append(Integer.toHexString(value[i]));
         }
      }

      return buffer.toString();
   }

   /**
    * emit pdf info.
    */
   private int emitInfo() {
      int infoId = getNextObjectID();

      xrefs.put(infoId, os.getOffset());
      os.println(infoId + " 0 obj");
      os.println("<<");

      os.println("/Creator " + getTextString(infoId, 0, "Style Report"));

      os.println("/Producer " + getTextString(infoId, 0, "Style Report"));

      Date date = info != null && info.getCreationDate() != null ?
         info.getCreationDate() : new Date();

      os.println("/CreationDate " +
                 getTextString(infoId, 0, toString(date)));

      // encrypt
      if(info != null) {
         if(info.getAuthor() != null) {
            // @by larryl, support CJK
            os.println("/Author " +
                       getTextString(infoId, 0, info.getAuthor()));
         }

         // encrypt
         if(info.getModDate() != null) {
            os.println("/ModDate " +
                       getTextString(infoId, 0, toString(info.getModDate())));
         }

         if(info.getTitle() != null) {
            // @by larryl, support CJK
            os.println("/Title " +
                       getTextString(infoId, 0, info.getTitle()));
         }

         if(info.getSubject() != null) {
            os.println("/Subject " +
                       getTextString(infoId, 0, info.getSubject()));
         }

         if(info.getKeywords() != null) {
            os.println("/Keywords " +
                       getTextString(infoId, 0, info.getKeywords()));
         }
      }

      os.println(">>");
      os.println("endobj");

      return infoId;
   }

   /**
    * emit form.
    */
   private int emitForm() {
      int formId = -1;

      if(fieldIds.size() > 0) {
         formId = getNextObjectID();

         xrefs.put(formId, os.getOffset());
         os.println(formId + " 0 obj");
         os.print("<< /Fields [");

         for(int i = 0; i < fieldIds.size(); i++) {
            os.print(fieldIds.get(i) + " 0 R ");
         }

         os.println("]");
         os.println("/NeedAppearances true");
         os.println(">>");
         os.println("endobj");
      }

      return formId;
   }

   private int emitOpenAction() {
      int actionId = getNextObjectID();
      xrefs.put(actionId, os.getOffset());
      os.println(actionId + " 0 obj");
      os.println("<< /Type /Action");
      os.println("/S /JavaScript");

      // by stone, fix bug1334084590073 set 'bShrinkToFit = true' to change the
      // default print option to "Fit to printable area"
      os.print("/JS (this.print\\(" +
                  "{bUI:true, bSilent:false, bShrinkToFit:true}\\);");

      os.println("if\\(this.hostContainer && " +
            "this.hostContainer.postMessage\\) { var msg = " +
            "new Array\\(\\); msg[0] = \"releaseFocus\"; " +
            "this.hostContainer.postMessage\\(msg\\); })");
      os.println(">>");
      os.println("endobj");
      return actionId;
   }

   private int triggerDidPrintAction() {
      int actionId = getNextObjectID();
      xrefs.put(actionId, os.getOffset());
      String url = SUtil.getRepositoryUrl(null);

      os.println(actionId + " 0 obj");
      os.println("<< /Type /Action");
      os.println("/S /JavaScript");
      os.print("/JS ");

      os.print(url == null ? "()" : "(app.launchURL\\(\"" + url +
         "?op=triggerprintlistener&ID=" + Tool.encodeURL(reportID) +
         "&printer=\" +this.getPrintParams().printerName+\"\", false\\);)");

      os.println(">>");
      os.println("endobj");
      return actionId;
   }

   /**
    * emit root.
    */
   private int emitRoot(int metadataId) {
      int formId = emitForm();
      int openActionId = -1;
      int didPrintedId = -1;

      if(isPrintOnOpen()) {
         openActionId = emitOpenAction();

         if(reportID != null) {
            didPrintedId = triggerDidPrintAction();
         }
      }

      int rootId = getNextObjectID();

      xrefs.put(rootId, os.getOffset());
      os.println(rootId + " 0 obj");
      os.println("<<");
      os.println("/Type /Catalog");
      os.println("/Pages " + pagesId + " 0 R");

      if(isAccessible() && structureTree != null) {
         structureTree.id = getNextObjectID();
         os.println("/MarkInfo <<");
         os.println("/Marked true");
         os.println(">>");
         os.println("/StructTreeRoot " + structureTree.id + " 0 R");

         String lang = getNaturalLanguage();

         if(lang != null) {
            os.println(lang);
         }
      }

      // this is a plug for subclasses to add outlines (bookmarks);
      if(outlines != null) {
         os.println("/Outlines " + outlines);
      }

      if(formId > 0) {
         os.println("/AcroForm " + formId + " 0 R");
      }

      if(isOpenBookmark()) {
         os.println("/PageMode /UseOutlines");
      }
      else if(isOpenThumbnail()) {
         os.println("/PageMode /UseThumbs");
      }

      if(isPrintOnOpen()) {
         os.println("/OpenAction " + openActionId + " 0 R" +
            (reportID == null ? "" : "/AA<</DP " + didPrintedId + " 0 R>>"));
      }

      if(metadataId >= 0) {
         os.println("/Metadata " + metadataId + " 0 R");
      }

      os.print("/ViewerPreferences <</PrintScaling /");

      if(printScaling) {
         os.print("AppDefault");
      }
      else {
         os.print("None");
      }

      if(isAccessible()) {
         os.println();
         os.print("/DisplayDocTitle true");
      }

      os.println(">>");

      os.println(">>");
      os.println("endobj");

      return rootId;
   }

   /**
    * Writes the natural language attribute for the catalog dictionary.
    *
    * @return the natural language attribute or <tt>null</tt> if not supported
    *         in this version of the PDF specification.
    */
   protected String getNaturalLanguage() {
      return null;
   }

   private void processStructureNode(StructureNode node,
                                     List<List<Integer>> parentTree,
                                     int pageIndex)
   {
      xrefs.put(node.id, os.getOffset());

      os.println(node.id + " 0 obj");
      os.println("<<");

      if(node.altText != null) {
         os.println("/Alt (" + node.altText + ")");
      }

      os.print("/K");

      if(node.kids == null) {
         if(node.mcid != -1 && node.reference != -1) {
            os.print(" [");
         }

         if(node.mcid != -1) {
            os.print(" " + node.mcid);
         }

         if(node.reference != -1) {
            os.println(" <<");
            os.println("/Obj " + node.reference + " 0 R");
            os.println("/Type /OBJR");

            if(node.mcid == -1) {
               os.println("/Pg " + pageIds.get(pageIndex) + " 0 R");
            }

            os.print(">>");
         }

         if(node.mcid != -1 && node.reference != -1) {
            os.print(" ]");
         }

         os.println();
      }
      else {
         os.print(" [");

         StructurePart[] parts = StructurePart.values();

         // bug1383073211622, Order the page structure (Header, Body, Footer)
         for(int i = 0; i < parts.length; i++) {
            for(StructureNode kid : node.kids) {
               if(kid.part == parts[i]) {
                  kid.id = getNextObjectID();
                  os.print(" " + kid.id + " 0 R");
               }
            }
         }

         os.println(" ]");
      }

      os.println("/P " + node.parent.id + " 0 R");

      if(node.kids == null && node.mcid != -1) {
         os.println("/Pg " + pageIds.get(pageIndex) + " 0 R");
      }

      os.println("/S /" + node.type.name());

      if(node.attributes != null && !node.attributes.isEmpty()) {
         os.print("/A << ");

         for(Map.Entry<String, String> e : node.attributes.entrySet()) {
            os.println(e.getKey() + " " + e.getValue());
         }

         os.println(">>");
      }

      os.println(">>");
      os.println("endobj");

      if(node.kids == null) {
         parentTree.get(pageIndex).add(node.id);
      }
      else {
         for(StructureNode kid : node.kids) {
            if(kid.type == StructureType.Part) {
               pageIndex = pageIndex + 1;
            }

            processStructureNode(kid, parentTree, pageIndex);
         }
      }
   }

   private void emitStructure() {
      CachedByteArrayOutputStream elemBuffer = new CachedByteArrayOutputStream();
      CountWriter elemWriter;

      try {
         elemWriter = new CountWriter(elemBuffer);
      }
      catch(Exception exc) {
         throw new RuntimeException("Failed to create structure buffer", exc);
      }

      int parentTreeId = getNextObjectID();
      List<List<Integer>> parentTree = new ArrayList<>();

      StructureNode document = new StructureNode();
      document.parent = structureTree;
      document.id = getNextObjectID();
      document.part = structurePart;
      document.type = StructureType.Document;
      document.kids = new ArrayList<>();

      for(StructureNode node : structureTree.kids) {
         if(node.type == StructureType.Part) {
            node.parent = document;
            document.kids.add(node);
            parentTree.add(new ArrayList<>());
         }
      }

      xrefs.put(structureTree.id, os.getOffset());
      os.println(structureTree.id + " 0 obj");
      os.println("<<");
      os.println("/K [ " + document.id + " 0 R ]");
      os.println("/ParentTree " + parentTreeId + " 0 R");
      os.println("/ParentTreeNextKey " + structureTree.kids.size());
      os.println("/Type /StructTreeRoot");
      os.println(">>");
      os.println("endobj");

      processStructureNode(document, parentTree, -1);

      Map<Integer, StructureNode> parentTreeRoots = new LinkedHashMap<>();

      xrefs.put(parentTreeId, os.getOffset());

      os.println(parentTreeId + " 0 obj");
      os.println("<<");
      os.print("/Nums [");

      for(int i = 0; i < structureTree.kids.size(); i++) {
         StructureNode node = structureTree.kids.get(i);

         if(node.type == StructureType.Part) {
            int rootId = getNextObjectID();
            parentTreeRoots.put(rootId, node);
            os.print(" " + i + " " + rootId + " 0 R");
         }
         else {
            os.print(" " + i + " " + node.id + " 0 R");
         }
      }

      os.println(" ]");
      os.println(">>");
      os.println("endobj");

      int pageIndex = 0;

      for(Map.Entry<Integer, StructureNode> e : parentTreeRoots.entrySet()) {
         int rootId = e.getKey();
         StructureNode node = e.getValue();

         xrefs.put(rootId, os.getOffset());

         os.println(rootId + " 0 obj");
         os.print("[");

         for(Integer id : parentTree.get(pageIndex++)) {
            os.print(" " + id + " 0 R");
         }

         os.println(" ]");
         os.println("endobj");
      }
   }

   /**
    * emit cross reference.
    */
   private void emitXRef() {
      os.println("xref");
      os.println(0 + " " + (xrefs.size() + 1));
      os.println("0000000000 65535 f" + XREF_SPACE);

      for(int i = 0; i < xrefs.size(); i++) {
         String ofstr = "0000000000" + xrefs.get(i + 1);

         os.println(ofstr.substring(ofstr.length() - 10) + " 00000 n" +
            XREF_SPACE);
      }
   }

   /**
    * Get the next font index.
    */
   protected int getNextFontIndex() {
      return getRoot().fontIdx++;
   }

   /**
    * Get the next objectID.
    */
   public int getNextObjectID() {
      return getRoot().objId++;
   }

   /**
    * Get the next objectID.
    */
   public int getNextPatternID() {
      return getRoot().patternId++;
   }

   /**
    * Get the toplevel graphics.
    */
   private PDFPrinter getRoot() {
      PDFPrinter ptr = this;

      while(ptr.cloned != null) {
         ptr = ptr.cloned;
      }

      return ptr;
   }

   /**
    * Initialize a new page.
    */
   protected final void startPage() {
      if(!inited) {
         inited = true;

         if(isAccessible()) {
            if(structureTree == null) {
               structureTree = new StructureNode();
               structureTree.type = StructureType.StructTreeRoot;
               structureTree.kids = new ArrayList<>();
            }

            StructureNode part = new StructureNode();
            part.type = StructureType.Part;
            part.parent = structureTree;
            part.kids = new ArrayList<>();
            structureTree.kids.add(part);

            structureParent = part;
            mcid = 0;
         }

         clipping = new Rectangle2D.Double(default_cliprect.x,
            default_cliprect.y, default_cliprect.width,
            default_cliprect.height);
         savelevel = 0;
         gs_info.clear();
         textObj = false;

         contentId = getNextObjectID();
         lengthId = getNextObjectID();
         pageIds.add(getNextObjectID());
         xrefs.put(contentId, os.getOffset());
         os.println(contentId + " 0 obj");
         os.println("<<");
         os.println("/Length " + lengthId + " 0 R");

         if(compressText) {
            if(ascii) {
               os.println("/Filter [ /ASCII85Decode /FlateDecode ]");
            }
            else {
               os.println("/Filter [ /FlateDecode ]");
            }
         }

         os.println(">>");
         os.println("stream");

         clr = null;
         setColor(Color.black);
         font = ofont = null; // force setFont to apply
         setFont(defFont);
         alpha = 1;
         pg.println("0 g");
         pg.println("BX /GS1 gs EX");

         pg.println("1 0 0 1 " + toString(pmargin.left * 72) + " " +
            toString(pageheight - pmargin.top * 72) + " cm");

         // create annotation
         Integer annotsId = getNextObjectID();

         annotsIds.add(annotsId);
         annots.put(annotsId, new ArrayList<>());

         gsave();
      }
   }

   /**
    * Write pattern object.
    */
   public int writeGradientPaint(GradientPaint gpaint) {
      Color ocolor = getColor();
      Color c2 = gpaint.getColor2();
      boolean center = c2.getAlpha() != 0;

      Point2D p1 = center ? gpaint.getPoint1() : gpaint.getPoint2();
      Point2D p2 = center ? gpaint.getPoint2() : gpaint.getPoint1();

      int pid = getNextObjectID();
      int shadingId = getNextObjectID();
      int functionId = getNextObjectID();

      others.markObject(pid);
      others.println(pid + " 0 obj");
      others.println("<<");
      others.println("/Type /Pattern");
      others.println("/PatternType 2");
      others.println("/Shading " + shadingId + " 0 R");
      others.println("/Matrix " + getMatrixString());
      others.println(">>");
      others.println("endobj");

      others.markObject(shadingId);
      others.println(shadingId + " 0 obj");
      others.println("<<");
      others.println("/ColorSpace /DeviceRGB");

      others.println("/Coords [" + p1.getX() + " " + (-p1.getY()) + " " +
                     p2.getX() + " " + (-p2.getY()) + "]");

      others.println("/Function " + functionId + " 0 R");
      others.println("/Extend [true true]");
      others.println("/ShadingType 2");
      others.println(">>");
      others.println("endobj");

      others.markObject(functionId);
      others.println(functionId + " 0 obj");
      others.println("<<");
      others.println("/C0" + getColorArrayString(ocolor));
      others.println("/C1" + getColorArrayString(c2));
      others.println("/FunctionType 2");
      others.println("/N 1");
      others.println("/Domain[0 1]");
      others.println(">>");
      others.println("endobj");

      // @by stephenwebster, fix bug1400506883626
      // Depending on the direction of the gradient select the
      // appropriate color
      Color c = center ? gpaint.getColor2() : gpaint.getColor1();
      updateAlpha(c.getAlpha() / 255f);

      return pid;
   }

   private String getColorArrayString(Color c) {
      double r = c.getRed() / 255.0;
      double g = c.getGreen() / 255.0;
      double b = c.getBlue() / 255.0;

      return "[" + r + " " + g + " " + b + "]";
   }

   private String getMatrixString() {
      double x = pmargin.left * 72;
      double y = pageheight - pmargin.top * 72;

      double[] matrix = new double[6];

      AffineTransform pts = new AffineTransform(ptrans);
      pts.concatenate(trans);
      pts.getMatrix(matrix);

      matrix[4] += x;
      matrix[5] -= y;

      return "[" + toString(matrix[0]) + " " + toString(-matrix[1]) + " " +
         toString(-matrix[2]) + " " + toString(matrix[3]) + " " +
         toString(matrix[4]) + " " + toString(-matrix[5]) + "]";
   }

   /**
    * Write pattern object.
    */
   public int writePattern(Image img) {
      int pid = getNextObjectID();
      int resourceId = getNextObjectID();
      int sizeId = getNextObjectID();
      int imgW = img.getWidth(null);
      int imgH = img.getHeight(null);
      PixelConsumer pc = new PixelConsumer(img);
      int imageId = emitImage(pc, 0, 0, new Dimension(imgW, imgH), null,
         Color.white, true);

      others.markObject(pid);
      others.println(pid + " 0 obj");
      others.println("<<");
      others.println("/Type /Pattern");
      others.println("/PatternType 1");
      others.println("/Resources " + resourceId + " 0 R");
      others.println("/PaintType 1");
      others.println("/TilingType 1");
      others.println("/BBox [0 0 " + imgW + " " + imgH + "]");
      others.println("/XStep " + imgW);
      others.println("/YStep " + imgH);
      others.println("/Length " + sizeId + " 0 R");
      others.println(">>");
      others.println("stream");

      int osize = others.getOffset();

      StringBuilder buf = new StringBuilder();
      buf.append("q\n");
      buf.append(imgW + " 0 0 -" + imgH + " 0 " + imgH + " cm\n");
      buf.append("/Im" + imageId + " Do\n");
      buf.append("Q\n");
      String str = buf.toString();

      if(isEncrypted()) {
         try {
            byte[] coded = encrypt(pid, 0, str.getBytes());
            others.write(coded);
         }
         catch(Exception e) {}
      }
      else {
         others.print(str);
      }

      int objlen = others.getOffset() - osize;

      others.println("");
      others.println("endstream");
      others.println("endobj");

      others.markObject(sizeId);
      others.println(sizeId + " 0 obj");
      others.println(Integer.toString(objlen));
      others.println("endobj");

      others.markObject(resourceId);
      others.println(resourceId + " 0 obj");
      others.println("<<");
      others.println("/ProcSet [/PDF /ImageC]");
      others.println("/XObject <<");
      others.println("/Im" + imageId + " " + imageId + " 0 R");
      others.println(">>");
      others.println(">>");
      others.println("endobj");

      return pid;
   }

   /**
    * Check if a font is base14 font.
    */
   protected boolean isBase14Font(String fontin) {
      Object answer;

      // this will save a lot string comparison. Performance
      if((answer = base14map.get(fontin)) != null) {
         return ((Boolean) answer).booleanValue();
      }

      String font = fontin.toLowerCase();

      for(int i = 0; i < base14fonts.length; i++) {
         if(font.equals(base14fonts[i]) ||
            font.startsWith(base14fonts[i] + "-")) {
            base14map.put(fontin, Boolean.TRUE);
            return true;
         }
      }

      base14map.put(fontin, Boolean.FALSE);
      return false;
   }

   /**
    * Return the PS Font Name with insetx as suffix.
    */
   protected String getPSFontNameWithInsetx(String pfname, int ix) {
      return isInsetxNeeded(pfname, ix) ? ix + pfname : pfname;
   }

   protected boolean isInsetxNeeded(String pfname, int ix) {
      return !(ix == -1 || psFontName.equals("Symbol"));
   }

   /**
    * Calculate the point on the arc at the specified angle.
    */
   private FitCurves.Point2 getArcPoint(double x, double y, double w,
                                        double h, double angle) {
      double a = w / 2;
      double b = h / 2;

      // normalize
      while(angle < 0) {
         angle += 360;
      }

      while(angle > 360) {
         angle %= 360;
      }

      double oangle = angle;

      angle = angle * Math.PI / 180;

      // adjust for the tiltering, this is how java's Graphics draw arc
      double angle2 = Math.atan(Math.tan(angle) * b / a);

      angle = (oangle > 90 && oangle <= 270) ? (angle2 + Math.PI) : angle2;

      double t = Math.tan(angle);
      int sign = (Math.cos(angle) >= 0) ? 1 : -1;
      double xp = Math.sqrt((a * a * b * b) / (a * a * t * t + b * b)) * sign;
      double yp = t * xp;

      return new FitCurves.Point2(xp + x + a, yp + y - b);
   }

   private static final char[] digits = {'0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9'};
   private static final String pzero = "0";
   private static final String nzero = "-0.0";
   private final char[] buf = new char[65];

   /**
    * Get a 5 decimal point precision string to reduce size of pdf.
    * But the precision needs to be high enough so very precisely positioned lines
    * will not be messed up. (50846)
    */
   private final String toString(double dval) {
      long val = (long) dval;

      if(val == dval) {
         return Long.toString(val, 10);
      }

      val = (long) (dval * 1000000);
      boolean negative = val < 0;
      boolean ignored = true;
      int charPos = 64;
      int number = 0;

      if(negative) {
         val = -val;
      }

      while(val >= 10) {
         if(number++ == 6) {
            if(charPos == 64) {
               buf[charPos--] = '0';
            }

            buf[charPos--] = '.';
            ignored = false;
         }

         int idx = (int) ((val % 10));
         val = val / 10;

         if(idx != 0 || !ignored) {
            buf[charPos--] = digits[idx];
            ignored = false;
         }
      }

      if(number++ == 6) {
         if(charPos == 64) {
            buf[charPos--] = '0';
         }

         buf[charPos--] = '.';
         ignored = false;
      }

      int idx = (int) val;

      if(idx != 0 || !ignored) {
         buf[charPos--] = digits[idx];
         ignored = false;
      }

      if(number < 7) {
         if(!ignored) {
            while(number++ < 6) {
               buf[charPos--] = '0';
            }
         }
         else {
            buf[charPos--] = '0';
         }

         buf[charPos--] = '.';
         buf[charPos--] = '0';
      }

      if(negative) {
         buf[charPos] = '-';
      }
      else {
         charPos++;
      }

      return new String(buf, charPos, 65 - charPos);
   }

   private final FastDouble2String double2str = new FastDouble2String(50);
   private List<String> oblique = new ArrayList<>();

   /**
    * Font mapping table.
    */
   protected Map<String, String> fontmap = new HashMap<>();

   {
      fontmap.put("dialog", "Helvetica");
      fontmap.put("dialoginput", "Courier");
      fontmap.put("serif", "Times");
      fontmap.put("sansserif", "Helvetica");
      fontmap.put("monospaced", "Courier");
      fontmap.put("timesroman", "Times");
      fontmap.put("courier", "Courier");
      fontmap.put("helvetica", "Helvetica");

      oblique.add("Courier");
      oblique.add("Helvetica");
      oblique.add("Courier");
   }

   protected class ImageInfoCache {
      public ImageInfoCache(int imageIdIn, int heightIn, int widthIn) {
         imageId = imageIdIn;
         width = widthIn;
         height = heightIn;
      }

      int imageId;
      int width;
      int height;
   }

   protected final class PDFWriter extends Writer {
      public PDFWriter(OutputStream out) throws IOException {
         super();
         this.out = new BufferedWriter(new OutputStreamWriter(out, "utf-8"), 65535);
      }

      public void print(String s) {
         if(s == null) {
            s = "null";
         }

         try {
            out.write(s, 0, s.length());
         }
         catch(Exception ex) {
            // ignore it
         }
     }

      public void println(String s) {
         if(s == null) {
            s = "null";
         }

         try {
            out.write(s, 0, s.length());
            out.write(line, 0, 1);
         }
         catch(Exception ex) {
           // ignore it
         }
      }

      @Override
      public void close() {
         try {
            out.close();
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      @Override
      public void flush() {
         try {
            out.flush();
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      @Override
      public void write(char[] buf, int off, int len) {
         throw new RuntimeException("Unsupported method called!");
      }

      private final char[] line = new char[] {'\n'};
      private final Writer out;
   }

   // writer with offset and size counters
   protected final class CountWriter extends PrintWriter {
      public CountWriter(OutputStream out) throws IOException {
         super(new BufferedWriter(new OutputStreamWriter(out, "utf-8"), 65536));
         stream = out;
         line = new char[] {'\n'};
      }

      @Override
      public void print(String s) {
         if(s == null) {
            s = "null";
         }

         int len = s.length();
         super.write(s, 0, len);
         offset += len;
      }

      // we need to explicitly control the newline since pdf imposes some
      // hard size requirements
      @Override
      public void println(String s) {
         if(s == null) {
            s = "null";
         }

         // optimization
         int len = s.length();
         super.write(s, 0, len);
         offset += len;
         super.write(line, 0, 1);
         offset++;
      }

      @Override
      public void println() {
         super.write(line, 0, 1);
         offset++;
      }

      @Override
      public void write(int c) {
         offset++;

         try {
            flush();
            stream.write(c);
         }
         catch(Exception e) {
            closed = true;
            LOG.error("Failed to write byte to stream", e);
            throw new RuntimeException("Write failed: " + e);
         }
      }

      public void write(byte[] bs) throws IOException {
         write(bs, 0, bs.length);
      }

      public void write(byte[] bs, int off, int len) throws IOException {
         offset += len;
         flush();
         stream.write(bs, off, len);
      }

      public void reset() {
         offset = 0;
         objmarks.clear();
      }

      public int getOffset() {
         return offset;
      }

      public void markObject(int id) {
         objmarks.put(id, offset);
      }

      public Map<Integer, Integer> getObjectMarks() {
         return objmarks;
      }

      OutputStream stream;
      int offset = 0;
      char[] line;
      Map<Integer, Integer> objmarks = new HashMap<>();
   }

   /**
    * Radio button group stores info of a radio group.
    */
   protected class RadioButtonGroup {
      // object id
      public int id;
      // radio group name
      public String name;
      // selected son
      public String sson;
      // son object ids
      public List<Integer> sids = new ArrayList<>();

      /**
       * Get id reference.
       *
       * @param aid the specified id
       * @return the id reference
       */
      public String getIDRef(int aid) {
         return aid + " 0 R";
      }

      /**
       * Get parent String representation.
       *
       * @return the parent string representation
       */
      public String getParentString() {
         return "/Parent " + getIDRef(id);
      }

      /**
       * Write pdf representation.
       *
       * @param writer the specified count writer
       */
      public void writePDF(CountWriter writer, PDFPrinter ps) {
         writer.markObject(id);
         writer.println(id + " 0 obj");
         writer.println("<<");
         writer.println("/FT /Btn");
         writer.println("/Ff 49152");

         writer.println("/T " + getTextString(id, 0, name));

         writer.println("/V /" + (sson != null ? sson : "Off"));
         writer.print("/Kids [");

         for(int i = 0; i < sids.size(); i++) {
            if(i > 0) {
               writer.print(" ");
            }

            int sid = sids.get(i);
            writer.print(getIDRef(sid));
         }

         writer.println("]");
         writer.println(">>");
         writer.println("endobj");
      }
   }

   /**
    *  AlphaHolder holds alphas of colors.
    */
   private class AlphaHolder {
      /**
       * Create an AlphaHolder.
       */
      public AlphaHolder() {
         totalAlphas.add(1.0f);
      }

      /**
       * Add an alpha value to the alpha holder.
       */
      public void add(float alpha) {
         Float fl = alpha;

         if(totalAlphas.contains(fl)) {
            return;
         }

         pageAlphas.add(fl);
         totalAlphas.add(fl);
      }

      /**
       * Add a alpha that has already been defined in previous pages, so in the
       * current page this alpha will not be defined again.It is only be written
       * in the ExtGState directory in the current page.
       */
      public void addDefinedAlpha(float alpha) {
         Float fl = alpha;

         if(definedAlphas.contains(fl)) {
            return;
         }

         definedAlphas.add(fl);
      }

      /**
       * Get the position of the alpha in the list if the alpha does not exist
       * in the list -1 is returned.
       */
      public int getAlphaIndex(float alpha) {
         Float fl = alpha;
         return totalAlphas.indexOf(fl);
      }

      /**
       * Get the position of the alpha in the current page alpha list if the
       * alpha does not exist in the current page alpha list -1 is returned.
       */
      public int getPageAlphaIndex(float alpha) {
         Float fl = alpha;
         return pageAlphas.indexOf(fl);
      }

      /**
       * Get the number of the alphas in the current page.
       */
      public int getPageAlphasCount() {
         return pageAlphas.size();
      }

      /**
       * Get the number of the alphas defined from the first page to the current
       * page.
       */
      public int getTotalAlphasCount() {
         return totalAlphas.size();
      }

      /**
       * Get the number of defined alhpas in the current page.
       */
      public int getDefinedAlphasCount() {
         return definedAlphas.size();
      }

      /**
       * Get the ith alpha defined in a page.
       */
      public float getAlphaInPage(int i) {
         return pageAlphas.get(i);
      }

      /**
       * Get the ith alpha in the defined alphas.
       */
      public float getAlphaInDefined(int i) {
         return definedAlphas.get(i);
      }

      /**
       * Clear the alphas generated in a page.
       */
      public void clearPageAlphas() {
         pageAlphas = new ArrayList<>();
      }

      /**
       * Clear the defined alhpas in a page.
       */
      public void clearDefinedAlphas() {
         definedAlphas = new ArrayList<>();
      }

      /**
       * Put the alpha object id.
       */
      public void putAlphaObjId(float alpha, int objid) {
         alphaObject.put(alpha, objid);
      }

      /**
       * Get the alpha object id.
       */
      public int getAlphaObjId(float alpha) {
         Integer itg = alphaObject.get(alpha);

         if(itg == null) {
            return -1;
         }

         return itg;
      }

      private ArrayList<Float> totalAlphas = new ArrayList<>();
      private ArrayList<Float> pageAlphas = new ArrayList<>();
      // alphas that has already defined in previous pages
      private ArrayList<Float> definedAlphas = new ArrayList<>();
      private HashMap<Float, Integer> alphaObject = new HashMap<>();// <alpha, alpha object>
   }

   /**
    * Escape the string according to postscript rules.
    */
   private final String escapeString(String str) {
      StringBuilder buf = null;
      char[] chars = str.toCharArray();
      int len = chars.length;

      for(int i = 0; i < len; i++) {
         char c = chars[i];

         switch(c) {
         case '\n':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\n");
            break;
         case '\r':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\r");
            break;
         case '\t':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\t");
            break;
         case '\b':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\b");
            break;
         case '\f':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\f");
            break;
         case '\\':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\\\");
            break;
         case '(':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\(");
            break;
         case ')':
            if(buf == null) {
               builder.setLength(0);
               buf = builder;
               buf.append(chars, 0, i);
            }

            buf.append("\\)");
            break;
         default:
            if(buf != null) {
               buf.append(c);
            }

            break;
         }
      }

      return buf == null ? str : buf.toString();
   }

   /**
    * Split delimited string into array of words.
    */
   public static String[] splitWords(String str) {
      if(str == null || str.length() == 0) {
         return new String[] {
         };
      }

      List<String> v = new ArrayList<>();
      int pos;

      while((pos = str.indexOf(' ')) >= 0) {
         // space following space, find the end of spaces
         if(pos == 0) {
            for(pos++; pos < str.length() && str.charAt(pos) == ' '; pos++) {
            }

            pos--;
         }

         v.add(str.substring(0, pos));
         str = str.substring(pos + 1);
      }

      v.add(str);
      return v.toArray(new String[v.size()]);
   }

   /**
    * Return the pdf commands for setting color.
    */
   private String getColorCommand(Color clr) {
      StringBuilder buf = new StringBuilder();

      double r = clr.getRed() / 255.0;
      double g = clr.getGreen() / 255.0;
      double b = clr.getBlue() / 255.0;

      if(r == g && g == b) {
         buf.append(toString(r) + " g\n");
         buf.append(toString(r) + " G");
      }
      else {
         buf.append(toString(r) + " " + toString(g) + " " + toString(b) +
                    " rg\n");
         buf.append(toString(r) + " " + toString(g) + " " + toString(b) +
                    " RG");
      }

      return buf.toString();
   }

   static SimpleDateFormat dateFmt = Tool.createDateFormat("yyyyMMddHHmmss");
   static DecimalFormat d2Fmt = new DecimalFormat("00");

   private static String toString(Date date) {
      int diff = TimeZone.getDefault().getRawOffset() / 60000;
      String str = "D:" + dateFmt.format(date);

      if(diff == 0) {
         str += "Z";
      }
      else if(diff > 0) {
         str += "+";
      }
      else if(diff < 0) {
         str += '-';
      }

      diff = Math.abs(diff);
      str += d2Fmt.format(Integer.valueOf(diff / 60)) + "'";
      str += d2Fmt.format(Integer.valueOf(diff % 60)) + "'";

      return str;
   }

   // Graphics2D methods

   /**
    * Returns the <code>PrinterJob</code> that is controlling the
    * current rendering request.
    * @return the <code>PrinterJob</code> controlling the current
    * rendering request.
    */
   public PrinterJob getPrinterJob() {
      return job2;
   }

   /**
    * Set the printer job associated with this graphics.
    */
   public void setPrinterJob(PrinterJob job) {
      this.job2 = job;
   }

   /**
    * Draw a shape.
    */
   @Override
   public void draw(Shape s) {
      if(s instanceof Arc2D) {
         Arc2D arc = (Arc2D) s;

         drawArc(arc.getX(), arc.getY(), arc.getWidth(), arc.getHeight(),
            arc.getAngleStart(), arc.getAngleExtent(), ((Arc2D) s).getArcType());
      }
      else if(s instanceof Line2D) {
         Line2D line = (Line2D) s;

         drawLine(line.getX1(), line.getY1(), line.getX2(), line.getY2());
      }
      else if(s instanceof Rectangle2D) {
         Rectangle2D rect = (Rectangle2D) s;

         drawRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
      }
      else if(s instanceof RoundRectangle2D) {
         RoundRectangle2D rect = (RoundRectangle2D) s;

         drawRoundRect(rect.getX(), rect.getY(), rect.getWidth(),
            rect.getHeight(), rect.getArcWidth(), rect.getArcHeight());
      }
      else if(s instanceof Rectangle) {
         Rectangle rect = (Rectangle) s;

         drawRect(rect.x, rect.y, rect.width, rect.height);
      }
      else if(s instanceof Polygon) {
         Polygon poly = (Polygon) s;

         drawPolygon(poly.xpoints, poly.ypoints, poly.npoints);
      }
      else if(s instanceof PolylineShape) {
         PolylineShape s2 = (PolylineShape) s;

         drawPolyline(s2.getXPoints(), s2.getYPoints(), s2.getPointCount());
      }
      else {
         drawPath(s.getPathIterator(new AffineTransform()));
      }
   }

   /**
    * Draw image.
    */
   @Override
   public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
      gsave();
      emitcm(xform);
      drawImage(img, 0, 0, obs);
      grestore();
      return true;
   }

   /**
    * Draw image.
    */
   @Override
   public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
      throw new RuntimeException("drawImage(BufferedImage," +
         " BufferedImageOp, int, int) not supported by PDFPrinter");
   }

   /**
    * Draw image.
    */
   @Override
   public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
      gsave();
      emitcm(xform);
      drawImage((Image) img, 0, 0, null);
      grestore();
   }

   /**
    * Draw image.
    */
   @Override
   public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
      gsave();
      emitcm(xform);
      drawImage((Image) img, 0, 0, null);
      grestore();
   }

   /**
    * Draw string.
    */
   @Override
   public void drawString(AttributedCharacterIterator iterator, int x, int y) {
      throw new RuntimeException(
         "drawString(AttributedCharactorIterator, int, int) not supported");
   }

   /**
    * Draw string.
    */
   @Override
   public void drawString(AttributedCharacterIterator iterator,
                          float x, float y) {
      throw new RuntimeException(
         "drawString(AttributedCharactorIterator, float, float) not supported");
   }

   /**
    * Draw string.
    */
   @Override
   public void drawGlyphVector(GlyphVector g, float x, float y) {
      int num = g.getNumGlyphs();

      translate(x, y);

      for(int i = 0; i < num; i++) {
         Shape s = g.getGlyphOutline(i);
         fill(s);
      }

      translate(-x, -y);
   }

   /**
    * Fill shape.
    */
   @Override
   public void fill(Shape s) {
      if(s instanceof Arc2D) {
         Arc2D arc = (Arc2D) s;

         fillArc(arc.getX(), arc.getY(), arc.getWidth(), arc.getHeight(),
            arc.getAngleStart(), arc.getAngleExtent());
      }
      else if(s instanceof Ellipse2D) {
         Ellipse2D arc = (Ellipse2D) s;

         fillOval(arc.getX(), arc.getY(), arc.getWidth(), arc.getHeight());
      }
      else if(s instanceof Rectangle2D) {
         Rectangle2D rect = (Rectangle2D) s;

         fillRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
      }
      else if(s instanceof RoundRectangle2D) {
         RoundRectangle2D rect = (RoundRectangle2D) s;

         fillRoundRect(rect.getX(), rect.getY(), rect.getWidth(),
            rect.getHeight(), rect.getArcWidth(), rect.getArcHeight());
      }
      else if(s instanceof Rectangle) {
         Rectangle rect = (Rectangle) s;

         fillRect(rect.x, rect.y, rect.width, rect.height);
      }
      else if(s instanceof Polygon) {
         Polygon poly = (Polygon) s;

         fillPolygon(poly.xpoints, poly.ypoints, poly.npoints);
      }
      else {
         fillPath(s.getPathIterator(new AffineTransform()));
      }
   }

   /**
    * Set the brush of painting.
    */
   @Override
   public void setPaint(Paint paint) {
      if(brush != null && paint != null && brush.equals(paint)) {
         return;
      }

      this.brush = paint;

      if(brush instanceof TexturePaint) {
         TexturePaint tpaint = (TexturePaint) brush;
         String id = "P" + getNextPatternID();
         Image img = tpaint.getImage();
         int pid = writePattern(img);

         brushObjs.put(id, pid + " 0 R");
         emit("/CS1 cs");
         emit("/" + id + " scn");
         setColor(null);
         clr = null;
      }
      else if(brush instanceof Color) {
         // force setColor to be called
         setColor(null);
         setColor((Color) (this.brush = paint));
      }
      else if(brush instanceof GradientPaint) {
         GradientPaint gpaint = (GradientPaint) paint;
         Color c1 = gpaint.getColor1();
         Color c2 = gpaint.getColor2();

         if(getColor().getAlpha() == 255 || c1.getAlpha() == c2.getAlpha()) {
            String id = "Sh" + getNextPatternID();
            int pid = writeGradientPaint(gpaint);
            brushObjs.put(id, pid + " 0 R");
            emit("/CS1 cs");
            emit("/" + id + " scn");
            setColor(null);
         }
         else {
            setDefaultPaint(gpaint);
         }
      }
      else if(paint != null) {
         setDefaultPaint(paint);
      }
   }

   /**
    * Set the default paint.
    */
   private void setDefaultPaint(Paint paint) {
      try {
         int type = BufferedImage.TYPE_4BYTE_ABGR;
         BufferedImage img = new BufferedImage(pagewidth, pageheight, type);
         Graphics2D g = (Graphics2D) img.getGraphics();
         AffineTransform trans = new AffineTransform();
         trans.concatenate(ptrans);
         trans.concatenate(getTransform());
         g.transform(trans);
         AffineTransform inv = trans.createInverse();
         Shape fillRect =
            new Rectangle2D.Double(0, 0, img.getWidth(), img.getHeight());
         fillRect = inv.createTransformedShape(fillRect);
         g.setPaint(paint);
         g.fill(fillRect);
         g.dispose();
         String id = "P" + getNextPatternID();
         int pid = writePattern(img);
         brushObjs.put(id, pid + " 0 R");
         emit("/CS1 cs");
         emit("/" + id + " scn");
         setColor(null);
      }
      catch(Exception e) {
         LOG.warn("Failed to set default paint object", e);
      }
   }

   /**
    * Not supported.
    */
   @Override
   public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
      throw new RuntimeException(
         "hit(Rectangle, Shape, boolean) not supported by PDFPrinter");
   }

   /**
    * Set the painting pen.
    */
   @Override
   public void setStroke(Stroke s) {
      startPage();

      // ignore identical style
      if(stroke != null && s != null && stroke.equals(s)) {
         return;
      }

      this.stroke = s;

      if(stroke instanceof BasicStroke) {
         BasicStroke pen = (BasicStroke) stroke;
         setLineWidth(pen.getLineWidth());
         float[] dashs = pen.getDashArray();

         if(dashs == null || dashs.length == 0) {
            emit("[ ] 0 d");
         }
         else if(dashs.length == 1) {
            emit("[ " + dashs[0] + " ] 0 d");
         }
         else if(dashs.length == 2) {
            emit("[ " + dashs[0] + " " + dashs[1] + " ] 0 d");
         }

         switch(pen.getEndCap()) {
         case BasicStroke.CAP_BUTT:
            emit("0 J");
            break;
         case BasicStroke.CAP_ROUND:
            emit("1 J");
            break;
         case BasicStroke.CAP_SQUARE:
            emit("2 J");
            break;
         }

         switch(pen.getLineJoin()) {
         case BasicStroke.JOIN_MITER:
            emit("0 j");
            break;
         case BasicStroke.JOIN_ROUND:
            emit("1 j");
            break;
         case BasicStroke.JOIN_BEVEL:
            emit("2 j");
            break;
         }
      }
      else {
         throw new RuntimeException(
            "Only BasicStroke is supported by PDFPrinter");
      }
   }

   /**
    * Not supported.
    */
   @Override
   public void setRenderingHint(Key hintKey, Object hintValue) {
      try {
         hints.put(hintKey, hintValue);
      }
      catch(IllegalArgumentException illegalArg) {
         LOG.warn("An illegal argument was used for "
                 + hintKey + ", the hint will be removed", illegalArg);
         hints.remove(hintKey);
      }
   }

   /**
    * Not supported.
    */
   @Override
   public Object getRenderingHint(Key hintKey) {
      return hints.get(hintKey);
   }

   /**
    * Not supported.
    */
   @Override
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public void setRenderingHints(Map hints) {
      this.hints = new RenderingHints(hints);
   }

   /**
    * Not supported.
    */
   @Override
   public void addRenderingHints(Map<?, ?> hints) {
      this.hints.putAll(hints);
   }

   /**
    * Not supported.
    */
   @Override
   public RenderingHints getRenderingHints() {
      return hints;
   }

   @Override
   public void translate(int x, int y) {
      translate((double) x, (double) y);
   }

   @Override
   public void translate(double x, double y) {
      trans.translate(x, y);
      checkTextObj(false);
      gsave(4);

      if(Math.abs(x) < 0.0001) {
         x = 0;
      }

      if(Math.abs(y) < 0.0001) {
         y = 0;
      }

      emit("1 0 0 1 " + x + " " + -y + " cm");
   }

   /**
    * Not supported.
    */
   @Override
   public GraphicsConfiguration getDeviceConfiguration() {
      return null;
   }

  /**
   * Sets the Composite for the Graphics2D context. The Composite is used in all
   * drawing methods such as drawImage, drawString,draw, and fill. It specifies
   * how new pixels are to be combined with the existing pixels on the graphics
   * device during the rendering process.
   */
   @Override
   public void setComposite(Composite comp) {
      if(comp instanceof AlphaComposite) {
         AlphaComposite composite = (AlphaComposite) comp;

         if(composite.getRule() == AlphaComposite.SRC_OVER) {
            compAlpha = composite.getAlpha();
            this.composite = composite;

            if(clr != null) {
               setPaint(clr);
            }

            return;
         }
      }

      this.composite = comp;
      compAlpha = 1.0f;
   }

   @Override
   public void rotate(double theta) {
      trans.rotate(theta);

      theta = -theta; // PDF rotates counter-clock and java other way
      double cos = Math.cos(theta);
      double sin = Math.sin(theta);

      checkTextObj(false);
      gsave(4);

      emit(toString(cos) + " " + toString(sin) + " " + toString(-sin) + " " +
         toString(cos) + " 0 0 cm");
   }

   @Override
   public void rotate(double theta, double x, double y) {
      AffineTransform xt = new AffineTransform();

      xt.rotate(theta, x, y);
      transform(xt);
   }

   @Override
   public void scale(double sx, double sy) {
      trans.scale(sx, sy);
      checkTextObj(false);
      gsave(4);
      emit(sx + " 0 0 " + sy + " 0 0 cm");
   }

   @Override
   public void shear(double shx, double shy) {
      AffineTransform xt = new AffineTransform();

      xt.shear(shx, shy);
      transform(xt);
   }

   @Override
   public void transform(AffineTransform tx) {
      gsave(4);
      trans.concatenate(tx);
      emitcm(tx);
   }

   @Override
   public void setTransform(AffineTransform tx) {
      AffineTransform tran = null;

      try {
         gsave(4);
         tran = this.trans.createInverse();
         emitcm(tran);
         this.trans = new AffineTransform(tx);
         emitcm(tx);
      }
      catch(NoninvertibleTransformException e) {
         LOG.warn("Failed to set transformation, not invertable", e);

         grestore(4);
         gsave(4);
         this.trans = new AffineTransform(tx);
         emitcm(tx);
         emitClip();
      }
   }

   /**
    * Emit clip, it functions like setClip(getClip()).
    */
   private void emitClip() {
      if(clipping == null) {
         clipping = new Rectangle2D.Double(
            default_cliprect.x, default_cliprect.y,
            default_cliprect.width, default_cliprect.height);
      }

      Rectangle2D.Double out = new Rectangle2D.Double();
      out.setRect(clipping);
      transformRect(out, true);
      double w = out.getWidth();
      double h = out.getHeight();

      if(w < 0) {
         out.x += w;
         out.width = -out.width;
      }

      if(h < 0) {
         out.y += h;
         out.height = -out.height;
      }

      setClip(out);
   }

   @Override
   public AffineTransform getTransform() {
      return new AffineTransform(trans);
   }

   /**
    * Output a matrix command.
    */
   protected void emitcm(AffineTransform tx) {
      double[] matrix = new double[6];
      tx.getMatrix(matrix);
      checkTextObj(false);

      builder.setLength(0);
      builder.append(toString(matrix[0])).append(' ')
         .append(toString(-matrix[1])).append(' ').append(toString(-matrix[2]))
         .append(' ').append(toString(matrix[3])).append(' ')
         .append(toString(matrix[4])).append(' ').append(toString(-matrix[5])).
         append(" cm\n");
      pg.print(builder.toString());
   }

   @Override
   public Paint getPaint() {
      return brush;
   }

   @Override
   public Composite getComposite() {
      return composite;
   }

   @Override
   public Stroke getStroke() {
      return stroke;
   }

   @Override
   public boolean isSupported(int feature) {
      return true;
   }

   @Override
   public void setClip(Shape s) {
      if(s == null) {
         clipping = new Rectangle2D.Double(
            default_cliprect.x, default_cliprect.y,
            default_cliprect.width, default_cliprect.height);
         return;
      }

      if(s instanceof Rectangle2D) {
         Rectangle2D rect = (Rectangle2D) s;

         setClip(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
      }
      else if(s instanceof Rectangle) {
         Rectangle rect = (Rectangle) s;

         setClip(rect.x, rect.y, rect.width, rect.height);
      }
      else {
         setPathClip(s);
      }
   }

   @Override
   public void clip(Shape s) {
      if(s instanceof Rectangle2D) {
         Rectangle2D rect = (Rectangle2D) s;

         clipRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
      }
      else if(s instanceof Rectangle) {
         Rectangle rect = (Rectangle) s;

         clipRect(rect.x, rect.y, rect.width, rect.height);
      }
      else {
         setPathClip(s);
      }
   }

   @Override
   public FontRenderContext getFontRenderContext() {
      return fontContext;
   }

   protected final void transformRect(Rectangle2D.Double rect, boolean reverse)
   {
      double x = rect.getX();
      double y = rect.getY();
      Point2D p1 = new Point2D.Double(x, y);
      Point2D p2 =
         new Point2D.Double(x + rect.getWidth(), y + rect.getHeight());
      AffineTransform trans = new AffineTransform(this.trans);
      trans.preConcatenate(ptrans);

      if(reverse) {
         try {
            p1 = trans.inverseTransform(p1, null);
            p2 = trans.inverseTransform(p2, null);
         }
         catch(Exception e) {
            LOG.warn("Failed to transform rectangle", e);
         }
      }
      else {
         p1 = trans.transform(p1, null);
         p2 = trans.transform(p2, null);
      }

      rect.x = p1.getX();
      rect.y = p1.getY();
      rect.width = p2.getX() - rect.x;
      rect.height = p2.getY() - rect.y;
   }

   /**
    * Write the pattern resources.
    */
   protected void writePatternResources(PrintWriter os) {
      // patterns used on the current page is kept in patterns and cleared
      // after the current page ends so it doesn't accummulate across the
      // entire report
      if(brushObjs.size() > 0) {
         os.println("/Pattern <<");

         for(Map.Entry<String, String> e : brushObjs.entrySet()) {
            os.println("/" + e.getKey() + " " + e.getValue());
         }

         os.println(">>");
         String exp = isMac ? "/Pattern" : "[/Pattern /DeviceRGB]";
         os.println("/ColorSpace << /CS1 " + exp + ">>");

         brushObjs.clear();
      }
   }

   // end 2D

   /**
    * Write foreground.
    */
   private void writeForeground(int onum, int gnum, CountWriter others,
                                Color fgColor, String fn) {
      if(fgColor != null) {
         String s = "/" + fn + " " + font.getSize() + " Tf " +
            fgColor.getRed() / 255.0 + " " + fgColor.getGreen() / 255.0 +
            " " + fgColor.getBlue() / 255.0 + " rg";

         others.println("/DA " + getTextString(onum, gnum, s));
      }
   }

   /**
    * Write background.
    */
   private void writeBackground(int onum, int gnum, CountWriter others,
                                Color bgColor) {
      if(bgColor != null) {
         others.println("/MK<</BG[" + bgColor.getRed() / 255.0 + " " +
            bgColor.getGreen() / 255.0 + " " + bgColor.getBlue() / 255.0 +
            "]>>");
      }
   }

   /**
    * Check if current using font is CJK font.
    */
   protected boolean isCurrentCJKFont() {
      return false;
   }

   /**
    * Determines if an implementation of the RC4 cipher is available for use.
    *
    * For some reason, when using the IBM JVM with JIT enabled, a deadlock was
    * occuring when multiple threads were calling Cipher.getInstance() in the
    * isEncrypted() method. After factoring this out, making it thread-safe,
    * and only calling Cipher.getInstance() once, the problem no longer occurs.
    *
    * @return if an RC4 implementation was found.
    */
   private static synchronized boolean isRC4Available() {
      if(RC4_AVAILABLE == null) {
         try {
            Cipher.getInstance("RC4");
            RC4_AVAILABLE = Boolean.TRUE;
         }
         catch(Throwable e) {
            RC4_AVAILABLE = Boolean.FALSE;
            LOG.debug("RC4 algorithm not available", e);
         }
      }

      return RC4_AVAILABLE;
   }

   @Override
   public Object clone() {
      try {
         PDFPrinter ps = (PDFPrinter) super.clone();
         ps.patternId = this.patternId;
         ps.savelevel = 0;
         ps.gs_info = new Stack<>();
         ps.g_infos = new Info[10];
         ps.cloned = this;
         ps.inited = true;
         ps.stroke = (Stroke) cloneStroke(this);
         // we save two context, one for use by transform
         ps.gsave();
         ps.gsave();

         // graphics2D
         ps.ptrans = new AffineTransform(ptrans);
         ps.ptrans.concatenate(trans);
         ps.trans = new AffineTransform();

         return ps;
      }
      catch(CloneNotSupportedException e) {
         return this;
      }
   }

   /**
    * Clone the stroke.
    */
   private Object cloneStroke(PDFPrinter ps) {
      if(!(ps.stroke instanceof BasicStroke)) {
         return ps.stroke;
      }

      BasicStroke bs = (BasicStroke) ps.stroke;
      float[] dashs = bs.getDashArray();
      float[] ndashs = null;

      if(dashs != null) {
         ndashs = new float[dashs.length];
         System.arraycopy(dashs, 0, ndashs, 0, dashs.length);
      }

      return new BasicStroke(bs.getLineWidth(), bs.getEndCap(),
         bs.getLineJoin(), bs.getMiterLimit(), ndashs, bs.getDashPhase());
   }

   protected void debug(PDFWriter os, String m) {
      // os.println(m);
   }

   public void setMac(boolean isMac) {
      this.isMac = isMac;
   }

   /**
    * Set which page part is being processed.
    * @param structurePart the StructurePart of the page
    */
   public void setStructurePart(StructurePart structurePart) {
      this.structurePart = structurePart;
   }

   static final String DEF_RADIO_GROUP = "RadioGroup";
   static final Font defFont = new Font("Helvetica", Font.PLAIN, 10);

   private boolean isMac = false;
   private static String XREF_SPACE = " ";
   private static final String[] base14fonts = {
      "times", "helvetica", "courier", "symbol", "zapfdingbats"};
   private static Map<String, Boolean> base14map = new HashMap<>(5);
   private static Font lastFn = null;
   private static String lastPDFFn = null;
   private static Lock lock = new ReentrantLock();
   private static Map<Font, String> pdfFnCache = new HashMap<>();

   private CachedByteArrayOutputStream othersBuf = new CachedByteArrayOutputStream();
   private CachedByteArrayOutputStream pgBuf = new CachedByteArrayOutputStream();
   private final StringBuilder builder = new StringBuilder();

   private Font font; // current font
   private Font ofont = null; // current original font
   private double fontRatio = 1.0;

   {
      try {
         String prop = SreeEnv.getProperty("pdf.font.ratio");

         if(prop != null) {
            fontRatio = Double.parseDouble(prop);
         }
      }
      catch(Exception ex) {
         // ignore it
      }
   }

   protected FontMetrics fm = null; // cached font metrics
   protected FontMetrics afm = null; // cached afm font metrics
   protected PDFWriter pg = null;
   protected CountWriter others = null;
   protected String psFontName = null; // Times-Roman
   protected Map<String, String> fontFn = new HashMap<>(); // Helvetica -> F1
   protected List<String> fnList = new ArrayList<>(); // /F1 2 0 R
   protected Map<String, String> fontObj = new HashMap<>(); // Helvetica -> "8 0 R"
   protected Map<String, String> fnObj = new HashMap<>(); // F1 -> "8 0 R"
   protected List<Integer> pageIds = new ArrayList<>(); // page ids (2 0 obj)
   protected List<Integer> annotsIds = new ArrayList<>(); // annotation ids
   protected Map<Integer, List<Integer>> annots = new HashMap<>();
   protected String outlines = null; // outline root id
   private float alpha = 1;

   // graphics state stacks
   private Stack<Info> gs_info = new Stack<>();
   // share the info avoid create so many times
   private Info[] g_infos = new Info[10];

   private List<Integer> fieldIds = new ArrayList<>(); // form field ids
   private int objId = 2;     // obj id count
   private int fontIdx = 1;      // font index
   private boolean started = false; // initialized
   private int lengthId = 0;     // current page length
   private int pagesId = 1;      // pages obj id
   private int contentId = 0;    // content obj id
   private int resourceId = 0;      // resource obj id
   private int egsId = 0;     // global ExtGS id
   private Map<Integer, Integer> xrefs = new HashMap<>(); // object offsets
   private boolean inited = false;  // track if page initialization is done
   private Set<String> imgSet = new HashSet<>(); // /Im0 5 0 R
   private boolean textObj = false; // true if in BT-ET
   private PDFPrinter cloned = null; // parent graphics of this subgraphics
   private int savelevel = 0; // number of gsave that has not be grestore'd
   private boolean closed = false;
   private DocumentInfo info = null;
   private PDFEncryptInfo encrypt = null;
   private Map<String, byte[]> keymaps = new HashMap<>(); // save encrypt key infos
   private float compAlpha = 1;
   private Composite composite = null;
   private Margin pmargin = new Margin(); // PrinterMargin adjustment
   private Map<Object, ImageInfoCache> imgmap = new HashMap<>(); // PixelConsumer -> image id
   private Map<String, RadioButtonGroup> radiomap = new HashMap<>(); // group -> RadioButtonGroup
   private boolean containsWideString = false;
   private boolean compressText = true;
   private boolean compressImg = true;
   private boolean ascii = false; // ascii only, use ASCII85 encoder
   private boolean mapSymbol = false; // map greek and math symbols
   private boolean openBookmark = false;  // bookmarks displayed when PDF opened
   private boolean openThumbnail = false; // thumbs displayed when PDF opened -
   // not used if "openBookmark" is true
   private boolean printScaling = true; // PrintScaling flag
   private boolean printOnOpen = false; // show print dialog immediately
   private Printer job;
   private String reportID;

   private boolean accessible = false;
   private Locale reportLocale = null;
   private StructureNode structureTree = null;
   private StructureNode structureParent = null;
   private int mcid = 0;
   private StructurePart structurePart = StructurePart.NONE;

   protected int insetx = -1;  // 0 for set0, 1 for set1, 2 for set2, 3 for set3

   // Graphics2D
   private AffineTransform trans = new AffineTransform();
   // used by cloned gc to chain back to the original transformation
   private AffineTransform ptrans = new AffineTransform();
   private FontRenderContext fontContext = new FontRenderContext(
      new AffineTransform(), true, true);
   private Stroke stroke = new BasicStroke();
   private Rectangle2D clipping = null;
   private Paint brush = Color.black;
   private PrinterJob job2;
   // "P1" -> "4 0 R" pattern obj ID
   private Map<String, String> brushObjs = new HashMap<>();
   private Map<String, Boolean> wfonts = new HashMap<>();
   private int patternId = 1; // pattern object id
   private RenderingHints hints = new RenderingHints(
      Common.EXPORT_GRAPHICS, Common.VALUE_EXPORT_GRAPHICS_ON);
   private AlphaHolder alpHolder;
   private String currentFontKey;
   private HashMap<String,HashSet<String>> glyphNameSubstitutions = new HashMap<>();
   protected static String[] charname = {
      "[ 1/Amacron/amacron/Abreve/abreve/Aogonek/aogonek/Cacute/cacute" +
         "/Ccircumflex/ccircumflex/Cdot/cdot/Ccaron/ccaron/Dcaron/dcaron" +
         "/Eth/eth/Emacron/emacron/Ebreve/ebreve/Edot/edot/Eogonek/eogonek" +
         "/Ecaron/ecaron/Gcircumflex/gcircumflex/Gbreve]",
      "[ 1/gbreve/Gdot/gdot/Gcedilla/gcedilla/Hcircumflex/hcircumflex/Hbar" +
         "/hbar/Itilde/itilde/Imacron/imacron/Ibreve/ibreve/Iogonek/iogonek" +
         "/Idot/dotlessi/IJ/ij/Jcircumflex/jcircumflex/Kcedilla/kcedilla" +
         "/kgreenlandic/Lacute/lacute/Lcedilla/lcedilla/Lcaron]",
      "[ 1/lcaron/Ldot/ldot/Lslash/lslash/Nacute/nacute/Ncedilla/ncedilla" +
         "/Ncaron/ncaron/napostrophe/Eng/eng/Omacron/omacron/Obreve/obreve" +
         "/Ohungarumlaut/ohungarumlaut/OE/oe/Racute/racute/Rcedilla/rcedilla" +
         "/Rcaron/rcaron/Sacute/sacute/Scircumflex]",
      "[ 1/scircumflex/Scedilla/scedilla/Scaron/scaron/Tcedilla/tcedilla" +
         "/Tcaron/tcaron/Tbar/tbar/Utilde/utilde/Umacron/umacron/Ubreve/ubreve" +
         "/Uring/uring/Uhungarumlaut/uhungarumlaut/Uogonek/uogonek/Wcircumflex" +
         "/wcircumflex/Ycircumflex/ycircumflex/Ydieresis/Zacute/zacute/Zdot]",
      "[ 1/zdot/Zcaron/zcaron/longs]",
      "[ 1/tonos/dieresistonos/Alphatonos/anoteleia/Epsilontonos" +
         "/Etatonos/Iotatonos/x1/Omicrontonos/x1/Upsilontonos/Omegatonos" +
         "/iotadieresistonos/Alpha/Beta/Gamma/Delta/Epsilon/Zeta/Eta" +
         "/Theta/Iota/Kappa/Lambda/Mu/Nu/Xi/Omicron/Pi/Rho]",
      "[ 1/Sigma/Tau/Upsilon/Phi/Chi/Psi/Omega/Iotadieresis/Upsilondieresis" +
         "/alphatonos/epsilontonos/etatonos/iotatonos/upsilondieresistonos" +
         "/alpha/beta/gamma/delta/epsilon/zeta/eta/theta/iota/kappa/lambda" +
         "/mu/nu/xi/omicron/pi/rho]",
      "[ 1/sigma1/sigma/tau/upsilon/phi/chi/psi/omega/iotadieresis" +
         "/upsilondieresis/omicrontonos/upsilontonos/omegatonos]",
      "[ 1/afii10023/afii10051/afii10052/afii10053/afii10054/afii10055" +
         "/afii10056/afii10057/afii10058/afii10059/afii10060/afii10061" +
         "/x1/afii10062/afii10145/afii10017/afii10018/afii10019/afii10020" +
         "/afii10021/afii10022/afii10024/afii10025/afii10026/afii10027" +
         "/afii10028/afii10029/afii10030/afii10031/afii10032/afii10033]",
      "[ 1/afii10034/afii10035/afii10036/afii10037/afii10038/afii10039" +
         "/afii10040/afii10041/afii10042/afii10043/afii10044/afii10045" +
         "/afii10046/afii10047/afii10048/afii10049/afii10065/afii10066" +
         "/afii10067/afii10068/afii10069/afii10070/afii10072/afii10073" +
         "/afii10074/afii10075/afii10076/afii10077/afii10078/afii10079" +
         "/afii10080]",
      "[ 1/afii10081/afii10082/afii10083/afii10084/afii10085/afii10086" +
         "/afii10087/afii10088/afii10089/afii10090/afii10091/afii10092" +
         "/afii10093/afii10094/afii10095/afii10096/afii10097/x1" +
         "/afii10071/afii10099/afii10100/afii10101/afii10102/afii10103" +
         "/afii10104/afii10105/afii10106/afii10107/afii10108/afii10109]",
      "[ 1/afii10110/afii10193]",
      "[ 1/afii10050/afii10098]",    // 490, 491
      /* this is the adobe glyph list, we are using microsoft glyph list which
       seems to work with microsoft fonts
       "[ 1/gravecmb/acutecmb/circumflexcmb/tildecmb/macroncmd" +
       "/overlinecmb/brevecmb/dotaccentcmb/diaeresiscmb/hookcmb" +
       "/ringcmb/hungarumlautcmb/caroncmb/verticallineabovecmb" +
       "/dblverticallineabovecmb/dblgravecmb/candrabinducmb" +
       "/breveinvertedcmb/commaturnedabovecmb/commaabovecmb" +
       "/commareversedabovecmb/commaaboverightcmb/gravebelowcmb" +
       "/acutebelowcmb/lefttackbelowcmb/righttackbelowcmb" +
       "/leftangleabovecmb/horncmb/ringhalfleftbelowcmb/uptackbelowcmb" +
       "/downtackbelowcmb]", // combining characters
       */
      "[ 1/combininggraveaccent/combiningacuteaccent/circumflexcmb" +
         "/combiningtildeaccent/macroncmb" +
         "/overlinecmb/brevecmb/dotaccentcmb/dieresiscmb/combininghookabove" +
         "/ringcmb/hungarumlautcmb/caroncmb/verticallineabovecmb" +
         "/dblverticallineabovecmb/dblgravecmb/candrabinducmb" +
         "/breveinvertedcmb/commaturnedabovecmb/commaabovecmb" +
         "/commareversedabovecmb/commaaboverightcmb/gravebelowcmb" +
         "/acutebelowcmb/lefttackbelowcmb/righttackbelowcmb" +
         "/leftangleabovecmb/horncmb/ringhalfleftbelowcmb/uptackbelowcmb" +
         "/downtackbelowcmb]", // combining characters
      "[ 1/alef/bet/gimel/dalet/he/vav/zayin/het/tet/yod" +
         "/finalkaf/kaf/lamed/finalmem/mem/finalnun/nun/samekh" +
         "/ayin/finalpe/pe/finaltsadi/tsadi/qof/resh/shin/tav]", // hebrew
      "[ 1/afii57409/afii57410/afii57411/afii57412/afii57413" + // arabic
         "/afii57414/afii57415/afii57416/afii57417/afii57418" +
         "/afii57419/afii57420/afii57421/afii57422/afii57423" +
         "/afii57424/afii57425/afii57426/afii57427/afii57428" +
         "/afii57429/afii57430/afii57431/afii57432/afii57433/afii57434" +
         "/uni063B/uni063C/uni063D/uni063E/uni063F" +
         "/afii57440/afii57441/afii57442/afii57443/afii57444/afii57445" +
         "/afii57446/afii57470/afii57448/afii57449/afii57450" +
         "/afii57451/afii57452/afii57453/afii57454/afii57455" +
         "/afii57456/afii57457/afii57458]", // arabic
      "[ 1/endash/emdash/horizontalbar/dblverticalbar/underscoredbl/quoteleft" +
         "/quoteright/quotesinglbase/quotereversed/quotedblleft/quotedblright" +
         "/quotedblbase/unknown/dagger/daggerdbl/bullet/unknown2023" +
         "/onedotenleader/twodotleader/ellipsis]", // symbols
      "[ 1/Adotbelow/adotbelow/Ahookabove/ahookabove/Acircumflexacute" +
         "/acircumflexacute/Acircumflexgrave/acircumflexgrave" +
         "/Acircumflexhookabove/acircumflexhookabove/Acircumflextilde" +
         "/acircumflextilde/Acircumflexdotbelow/acircumflexdotbelow" +
         "/Abreveacute/abreveacute]", // acircum
      "[ 1/uniFBE8/uniFBE9]",
      "[ 1/uniFBFC/uniFBFD/uniFBFE/uniFBFF]",
      "[ 1/uniFE81/uniFE82]",
      "[ 1/uniFE8D/uniFE8E/uniFE8F/uniFE90/uniFE91/uniFE92/uniFE93/uniFE94" +
         "/uniFE95/uniFE96/uniFE97/uniFE98/uniFE99/uniFE9A/uniFE9B/uniFE9C" +
         "/uniFE9D/uniFE9E/uniFE9F/uniFEA0/uniFEA1/uniFEA2/uniFEA3/uniFEA4" +
         "/uniFEA5/uniFEA6/uniFEA7/uniFEA8/uniFEA9/uniFEAA/uniFEAB/uniFEAC" +
         "/uniFEAD/uniFEAE/uniFEAF/uniFEB0/uniFEB1/uniFEB2/uniFEB3/uniFEB4" +
         "/uniFEB5/uniFEB6/uniFEB7/uniFEB8/uniFEB9/uniFEBA/uniFEBB/uniFEBC" +
         "/uniFEBD/uniFEBE/uniFEBF/uniFEC0/uniFEC1/uniFEC2/uniFEC3/uniFEC4" +
         "/uniFEC5/uniFEC6/uniFEC7/uniFEC8/uniFEC9/uniFECA/uniFECB/uniFECC" +
         "/uniFECD/uniFECE/uniFECF/uniFED0/uniFED1/uniFED2/uniFED3/uniFED4" +
         "/uniFED5/uniFED6/uniFED7/uniFED8/uniFED9/uniFEDA/uniFEDB/uniFEDC" +
         "/uniFEDD/uniFEDE/uniFEDF/uniFEE0/uniFEE1/uniFEE2/uniFEE3/uniFEE4" +
         "/uniFEE5/uniFEE6/uniFEE7/uniFEE8/uniFEE9/uniFEEA/uniFEEB/uniFEEC" +
         "/uniFEED/uniFEEE/uniFEEF/uniFEF0/uniFEF1/uniFEF2/uniFEF3/uniFEF4" +
      "]"
   };

   // for checkbox checkmark
   private static final Font ZADB = new Font("ZapfDingbats", Font.PLAIN, 11);

   // inclusive on both ends
   protected static int[][] charRanges = {
      {256, 286}, {287, 317}, {318, 348}, {349, 379}, {380, 383},
      {900, 929}, {931, 961}, {962, 974}, {1025, 1055}, {1056, 1086},
      {1087, 1117}, {1118, 1119}, {1168, 1169}, {768, 798},
      {0x5D0, 0x5EA}, {0x621, 0x652}, {0x2013, 0x2026},
      {0x1EA0, 0x1EAF}, {0xFBE8, 0xFBE9}, {0xFBFC, 0xFBFF},
      {0xFE81, 0xFE82}, {0xFE8D, 0xFEF4}
   };

   // @by yanie: bug1418941727663
   // The Thai language is not a simple horizontal display language
   // Some char will display to the upper/lower place of another char
   // This type char don't need own widths for display
   // So seperate this type char and normal width char to different charRanges
   // and store the needWidth option accordingly
   protected static boolean[] needWidths = {
      true, true, true, true, true,
      true, true, true, true, true,
      true, true, true, true,
      true, true, true,
      true, true, true,
      true, true
   };

   // encrypt padding array.
   private static final byte[] paddingArray = new byte[] {
      (byte)0x28, (byte)0xBF, (byte)0x4E, (byte)0x5E,
      (byte)0x4E, (byte)0x75, (byte)0x8A, (byte)0x41,
      (byte)0x64, (byte)0x00, (byte)0x4E, (byte)0x56,
      (byte)0xFF, (byte)0xFA, (byte)0x01, (byte)0x08,
      (byte)0x2E, (byte)0x2E, (byte)0x00, (byte)0xB6,
      (byte)0xD0, (byte)0x68, (byte)0x3E, (byte)0x80,
      (byte)0x2F, (byte)0x0C, (byte)0xA9, (byte)0xFE,
      (byte)0x64, (byte)0x53, (byte)0x69, (byte)0x7A
   };

   private static final String xmpMetadata = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
      "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"\n" +
      "           x:xmptk=\"Adobe XMP Core 5.2-c001 63.139439, 2010/09/27-13:37:26        \">\n" +
      "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
      "    <rdf:Description rdf:about=\"\"\n" +
      "                     xmlns:pdfaExtension=\"http://www.aiim.org/pdfa/ns/extension/\"\n" +
      "                     xmlns:pdfaSchema=\"http://www.aiim.org/pdfa/ns/schema#\"\n" +
      "                     xmlns:pdfaProperty=\"http://www.aiim.org/pdfa/ns/property#\"\n" +
      "                     xmlns:pdfuaid=\"http://www.aiim.org/pdfua/ns/id/\">\n" +
      "      <pdfaExtension:schemas>\n" +
      "        <rdf:Bag>\n" +
      "          <rdf:li rdf:parseType=\"Resource\">\n" +
      "            <pdfaSchema:schema>PDF/UA Universal Accessibility Schema</pdfaSchema:schema>\n" +
      "            <pdfaSchema:namespaceURI>http://www.aiim.org/pdfua/ns/id/\n" +
      "            </pdfaSchema:namespaceURI>\n" +
      "            <pdfaSchema:prefix>pdfuaid</pdfaSchema:prefix>\n" +
      "            <pdfaSchema:property>\n" +
      "              <rdf:Seq>\n" +
      "                <rdf:li rdf:parseType=\"Resource\">\n" +
      "                  <pdfaProperty:name>part</pdfaProperty:name>\n" +
      "                  <pdfaProperty:valueType>Integer</pdfaProperty:valueType>\n" +
      "                  <pdfaProperty:category>internal</pdfaProperty:category>\n" +
      "                  <pdfaProperty:description>Indicates, which part of ISO 14289 standard is\n" +
      "                    followed\n" +
      "                  </pdfaProperty:description>\n" +
      "                </rdf:li>\n" +
      "              </rdf:Seq>\n" +
      "            </pdfaSchema:property>\n" +
      "          </rdf:li>\n" +
      "        </rdf:Bag>\n" +
      "      </pdfaExtension:schemas>\n" +
      "      <pdfuaid:part>1</pdfuaid:part>\n" +
      "    </rdf:Description>\n" +
      "    <rdf:Description rdf:about=\"\"\n" +
      "                     xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
      "      <dc:format>application/pdf</dc:format>\n" +
      "      <dc:title>\n" +
      "        <rdf:Alt>\n" +
      "          <rdf:li xml:lang=\"x-default\">%s</rdf:li>\n" +
      "        </rdf:Alt>\n" +
      "      </dc:title>\n" +
      "    </rdf:Description>\n" +
      "  </rdf:RDF>\n" +
      "</x:xmpmeta>\n" +
      "<?xpacket end=\"w\"?>";

   static AffineTransform psmatrix = new AffineTransform();
   static {
      // flip y axis
      psmatrix.scale(1, -1);
   }

   private static Boolean RC4_AVAILABLE = null;
   private static final Logger LOG = LoggerFactory.getLogger(PDFPrinter.class);
   public enum StructurePart { NONE, HEADER, BODY, FOOTER }

   private static final class StructureNode implements Comparable<StructureNode> {
      int id;
      int mcid = -1;
      StructurePart part = StructurePart.NONE;
      StructureType type;
      StructureNode parent;
      List<StructureNode> kids;
      Map<String, String> attributes;
      String altText;
      int reference = -1;

      @Override
      public int compareTo(StructureNode o) {
         // bug1383073211622, Order page sections (header then body then footer)
         return part.ordinal() > o.part.ordinal() ? 1 :
            (part.ordinal() < o.part.ordinal() ? -1 : id - o.id);
      }

      public void setAttribute(String name, String value) {
         if(attributes == null) {
            attributes = new LinkedHashMap<>();
         }

         attributes.put(name, value);
      }
   }

   private static enum StructureType {
      StructTreeRoot, Document, Part, P, H1, H2, H3, H4, H5, H6, Table, TR, TH,
      TD, Figure, Form, Link
   }
}

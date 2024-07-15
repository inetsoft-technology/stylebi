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

import inetsoft.report.PDFPrinter;
import inetsoft.report.internal.Common;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * PDF3Printer implements the full PDF file format, including font embedding.
 * It has the same API as the regular PDFPrinter. It should not be used
 * directly to generate a PDF. Use PDF3Generator.getPDFGenerator() and
 * generate a PDF from the generator.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class PDF3Printer extends PDFPrinter {
   /**
    * Create an empty PDF3Printer. The setOutput() and startDoc() must
    * be called before the PDF3Printer can be used.
    */
   public PDF3Printer() {
   }

   /**
    * Create a PDF3Printer to the specified output.
    */
   public PDF3Printer(OutputStream out) {
      super(out);
   }

   /**
    * Set whether to use base14 fonts only.
    * @param base14 true to use base14 fonts only.
    */
   public void setBase14Only(boolean base14) {
      this.base14 = base14;
   }

   /**
    * Check whether to use base14 fonts only.
    */
   public boolean isBase14Only() {
      return base14;
   }

   /**
    * Set whether to embed fonts in PDF.
    * @param embed true to embed fonts.
    */
   public void setEmbedFont(boolean embed) {
      this.embedFont = embed;
   }

   /**
    * Check whether to embed fonts in PDF.
    */
   public boolean isEmbedFont() {
      return embedFont;
   }

   /**
    * Sets the names of the fonts that should be fully embedded. All other fonts
    * will only have the subset of those characters used in the PDF embedded.
    *
    * @param fullyEmbeddedFonts the font names.
    */
   public void setFullyEmbeddedFonts(String[] fullyEmbeddedFonts) {
      if(fullyEmbeddedFonts == null) {
         this.fullyEmbeddedFonts = null;
      }
      else {
         this.fullyEmbeddedFonts = new HashSet<>(Arrays.asList(fullyEmbeddedFonts));
      }
   }

   /**
    * Gets the names of the fonts that should be fully embedded. All other fonts
    * will only have the subset of those characters used in the PDF embedded.
    *
    * @return the font names.
    */
   public String[] getFullyEmbeddedFonts() {
      String[] result;

      if(fullyEmbeddedFonts == null) {
         result = new String[0];
      }
      else {
         result = fullyEmbeddedFonts.toArray(
            new String[fullyEmbeddedFonts.size()]);
      }

      return result;
   }

   /**
    * Get the PDF font name.
    * @param font font object.
    */
   @Override
   public String getFontName(Font font) {
      String fname = Common.getFontName(font);
      String psname = getPSName(fname, font);

      if(!base14 && !isBase14Font(fname)) {
         if(fontMgr.exists(psname)) {
            return psname;
         }

         int comma = psname.indexOf(',');

         if(comma > 0) {
            psname = psname.replace(',', '-');

            if(fontMgr.exists(psname)) {
               return psname;
            }
         }

         if(fontMgr.exists(fname)) {
            return fname;
         }
      }

      return super.getFontName(font);
   }

   // in format 'font name,BoldItalic'
   protected String getPSName(String name, Font font) {
      String fontsub = "";

      if((font.getStyle() & Font.BOLD) != 0) {
         fontsub += "Bold";
      }

      if((font.getStyle() & Font.ITALIC) != 0) {
         fontsub += "Italic";
      }

      // pdf appends bold or italic to the end of font name
      return (fontsub.length() > 0) ? name += "," + fontsub : name;
   }

   /**
    * Add bookmark.
    */
   public void addBookmark(Node node) {
      int id = node.getID();

      others.markObject(id);
      others.println(id + " 0 obj");

      others.print("<<\n");

      if(node.getLabel() != null) {
         others.print("/Title " + getTextString(id, 0, node) + "\n");
      }

      String obj = "";

      if(node.getPageID() != null) {
         obj += "/Dest [" + node.getPageID() + " /FitH " + node.getPageY() +
            "]\n";
      }

      if(node.getParent() != null) {
         obj += "/Parent " + node.getParent().getID() + " 0 R\n";
      }

      Node next = node.getNext();

      if(next != null) {
         obj += "/Next " + next.getID() + " 0 R\n";
      }

      if(node.getChildCount() > 0) {
         Node child = node.getChild(0);

         obj += "/First " + child.getID() + " 0 R\n";
         child = node.getChild(node.getChildCount() - 1);
         obj += "/Last " + child.getID() + " 0 R\n";
         obj += "/Count " + node.getNodeCount();
      }

      obj += ">>\n";

      others.print(obj);
      others.println("endobj");
      writeOthers(); // flush out others buffer

      for(int i = 0; i < node.getChildCount(); i++) {
         addBookmark(node.getChild(i));
      }
   }

   /**
    * Send the set font command to output.
    */
   @Override
   protected String emitFont(Font font) {
      FontInfo finfo = null;

      if(font.equals(lastFn)) {
         psFontName = lastPSFn;
         finfo = lastFontInfo;
      }
      else {
         psFontName = getFontName(font);
         finfo = fontMgr.getFontInfo(psFontName);
      }

      lastFn = font;
      lastPSFn = psFontName;
      lastFontInfo = finfo;

      // @by larryl 2003-9-23, if contains special characters, proceed to embed
      if(isBase14Font(psFontName) && insetx < 0) {
         return super.emitFont(font);
      }

      startPage();
      debug(pg, "%setFont3");

      String psFontNameTmp = getPSFontNameWithInsetx(psFontName, insetx);
      String fn = fontFn.get(psFontNameTmp);

      if(fn == null) {
         fn = "F" + getNextFontIndex();

         String fo = fontObj.get(psFontNameTmp);

         if(fo == null) {
            if(finfo == null) {
               return super.emitFont(font);
            }

            int fontId = getNextObjectID();

            fo = fontId + " 0 R";
            fontObj.put(psFontNameTmp, fo);
            fnObj.put(fn, fo);
         }

         fnList.add("/" + fn + " " + fo + " ");
      }

      if(finfo != null) {
         Set<Integer> list = stringinsetx.computeIfAbsent(font, k -> new HashSet<>());
         list.add(insetx);
      }

      pg.println("/" + fn + " " + font.getSize() + " Tf");
      fontFn.put(psFontNameTmp, fn);

      return fn;
   }

   /**
    * Output Font Descriptor.
    */
   void emitFontDescriptor(int id, String fontname, Font font, FontInfo finfo,
         boolean embedFont) {
      Rectangle bbox = finfo.getFontBBox();
      boolean truetype = finfo instanceof TTFontInfo;
      boolean cff = truetype && ((TTFontInfo) finfo).isCFFont();

      others.markObject(id);
      others.println(id + " 0 obj");
      others.println("<<");
      others.println("/Type /FontDescriptor");
      others.println("/FontName /" + fixFontName(fontname, font));
      others.println("/Flags 34");
      others.println("/FontBBox [ " + bbox.x + " " + bbox.y + " " + bbox.width +
         " " + bbox.height + " ]");
      others.println("/StemV 73");
      others.println("/MissingWidth 400");
      others.println("/ItalicAngle " +
         (((font.getStyle() & Font.ITALIC) != 0) ? finfo.getItalicAngle() : 0));
      others.println("/CapHeight " + finfo.getCapHeight());
      others.println("/Ascent " + finfo.getAscent());
      others.println("/Descent " + finfo.getDescent());

      int ffId = 0;

      if(truetype && embedFont) {
         ffId = getNextObjectID();
         others.println((cff ? "/FontFile3 " : "/FontFile2 ") + ffId + " 0 R");
      }

      others.println(">>");
      others.println("endobj");

      if(truetype && embedFont) {
         embedFont(ffId, fontname, (TTFontInfo) finfo);
      }
   }

   /**
    * Fix the font name, if the font name is wide char string, fix it.
    */
   private String fixFontName(String fontname, Font font) {
      if(!isWideCharString(fontname, true)) {
         return fontname;
      }

      return font.getFontName(Locale.US);
   }

   /**
    * Embed a font file as the specified pdf object.
    */
   void embedFont(int id, String fontname, TTFontInfo finfo) {
      try {
         byte[] buf = getFontData(fontname, finfo);
         String[][] keys = null;

         if(finfo.isCFFont()) {
            keys = new String[][] { {"/Subtype", "/CIDFontType0C"}};
         }

         emitStream(id, buf, keys, true);
      }
      catch(Exception e) {
         LOG.warn("Failed to emit font: " + fontname, e);
      }
   }

   /**
    * Generate a stream object.
    */
   void emitStream(int id, byte[] buf, String[][] keys, boolean compress) {
      try {
         boolean ascii =
            "true".equals(SreeEnv.getProperty("pdf.output.ascii"));

         byte[] coded = buf;

         if(compress) {
            coded = Encoder.deflate(coded);

            if(ascii) {
               coded = Encoder.encodeAscii85(coded);
            }
         }

         others.markObject(id);
         others.println(id + " 0 obj");
         others.println("<<");

         if(compress) {
            others.print("/Filter [");

            if(ascii) {
               others.print(" /ASCII85Decode");
            }

            others.println(" /FlateDecode ]");
         }

         int lenId = getNextObjectID();

         others.println("/Length " + lenId + " 0 R");
         others.println("/Length1 " + buf.length);

         if(keys != null) {
            for(int i = 0; i < keys.length; i++) {
               others.println(keys[i][0] + " " + keys[i][1]);
            }
         }

         others.println(">>");
         others.println("stream");
         int osize = others.getOffset();

         //encrypt
         if(compress && ascii) {
            for(int i = 0; i < coded.length; i += 78) {
               if(i > 0) {
                  others.println("");
               }

               others.write(coded, i, Math.min(coded.length - i, 78));
            }

            others.println("~>");
         }
         else {
            others.write(coded);
         }

         int objlen = others.getOffset() - osize;

         others.println("endstream");
         others.println("endobj");
         others.markObject(lenId);
         others.println(lenId + " 0 obj");
         others.println(objlen + "");
         others.println("endobj");
      }
      catch(Exception e) {
         LOG.error("Failed to emit stream", e);
      }
   }

   /**
    * Get font file data.
    */
   byte[] getFontData(String fontname, TTFontInfo finfo) throws IOException {
      fontname = getRegularFontName(fontname);

      // embed partial font if default embedding is not on, which means
      // the embedding is trigger by special characters in the data
      if(!embedFont && insetx >= 0 && !finfo.isAdvancedTypographic()) {
         BitSet chars = new BitSet();

         // basic set
         for(int i = 0; i < 256; i++) {
            chars.set(i);
         }

         // special characters
         for(int i = 0; i < charRanges.length; i++) {
            for(int j = charRanges[i][0] + 1; j < charRanges[i][1]; j++) {
               chars.set(finfo.getGlyphIndex((char) j));
            }
         }

         return finfo.getFontData(chars);
      }

      if(fullyEmbeddedFonts == null || !fullyEmbeddedFonts.contains(fontname)) {
         BitSet ochars = strings.get(fontname);

         if(ochars != null) {
            BitSet chars = new BitSet();
            int i = ochars.nextSetBit(0);

            while(i >= 0) {
               chars.set(finfo.getGlyphIndex((char) i));
               i = ochars.nextSetBit(i + 1);
            }

            HashSet<String> glyphNameSubstitutions = getGlyphNameSubstitutions(fontname);

            if(glyphNameSubstitutions != null) {
               for(String glyphName : glyphNameSubstitutions) {
                  chars.set(finfo.getGlyphIndex(PDFTool.getGlyph(glyphName)));
               }
            }

            return finfo.getFontData(chars);
         }
      }

      return finfo.getFontData();
   }

   /**
    * Remove the pending style string from font name, such
    * as Bold, Italic.
    */
   protected String getRegularFontName(String fname) {
      int index = fname.indexOf(",");
      String result = fname;

      if(index > 0) {
         result = fname.substring(0, index);
      }

      return result;
   }

   /**
    * Return the string width.
    */
   @Override
   protected float stringWidth(String str) {
      // if ttf font known, use it
      return (lastFontInfo != null) ?
         lastFontInfo.stringWidth(str, getFont().getSize()) :
         super.stringWidth(str);
   }

   /**
    * Remove characters from string.
    */
   String strip(String str) {
      String val = strips.get(str);

      if(str == null || val != null) {
         return val;
      }

      StringBuilder buf = new StringBuilder();

      if(str != null) {
      	 int len = str.length();

         for(int i = 0; i < len; i++) {
            char c = str.charAt(i);

            if(c != ' ') {
               buf.append(c);
            }
         }
      }

      val = buf.toString();
      strips.put(str, val);
      return val;
   }

   /**
    * Get the page ID.
    */
   String getPageID(int idx) {
      return pageIds.get(idx) + " 0 R";
   }

   /**
    * Set the outline root.
    */
   void setOutlines(String outlines) {
      this.outlines = outlines;
   }

   /**
    * Flush out the contents in others buffer.
    */
   void flush() {
      writeOthers();
   }

   /**
    * Write the font widths list.
    */
   void writeWidths(FontInfo finfo, int idx) {
      int base = charRanges[idx][0];
      int len = charRanges[idx][1] - charRanges[idx][0] + 1;

      for(int i = 0; i < len; i++) {
         int val = base + i;

         // @by yanie: bug1418941727663
         // Some Thai char will appear on upper/lower case of another char,
         // This kind chars don't need width, so width is 0
         int w = needWidths[idx] ?
            (Arrays.binarySearch(hidin, val) >= 0 ? 0 : finfo.getWidth(val))
            : 0;

         others.print(" " + w);
      }
   }

   /**
    * Send the set font command to output.
    */
   protected void emitFont2(Font font) {
      psFontName = getFontName(font);
      FontInfo finfo = fontMgr.getFontInfo(psFontName);
      String fontname = getEmitFontName(finfo, font);
      boolean truetype = finfo instanceof TTFontInfo;
      int encodingId = 0;

      if(isInsetxNeeded(psFontName, insetx)) {
         encodingId = writeEncoding(insetx);
      }

      String psFontNameTmp = getPSFontNameWithInsetx(psFontName, insetx);
      String fn = fontFn.get(psFontNameTmp);
      String fo = fontObj.get(psFontNameTmp);
      int fontId = Integer.parseInt(fo.substring(0, fo.length() - 4));

      // @by jasons only emit font once
      if(others.getObjectMarks().containsKey(fontId)) {
         return;
      }

      others.markObject(fontId);
      others.println(fontId + " 0 obj");
      others.println("<<");
      others.println("/Type /Font");
      others.println("/Subtype /" + (truetype ? "TrueType" : "Type1"));
      others.println("/Name /" + fn);
      others.println("/BaseFont /" + getBaseFontName(fontname, font));
      String encoding = psFontName.equals("Symbol") ?
         "/PDFDocEncoding" :
         ((insetx == -1) ? "/WinAnsiEncoding" : (encodingId + " 0 R"));
      others.println("/Encoding " + encoding);

      int first = insetx >= 0 ? 1 : 0;
      others.println("/FirstChar " + first);
      others.println("/LastChar 255");
      others.print("/Widths [");

      if(insetx >= 0) {
         writeWidths(finfo, insetx);
         first = charRanges[insetx][1] - charRanges[insetx][0];
      }
      else {
         for(int i = 0; i < 32; i ++) {
            others.print(" " + finfo.getWidth(0));
         }

         first = 32;
      }

      for(int i = first; i <= 255; i++) {
         others.print(" " + finfo.getWidth(i));
      }

      others.println("]");
      boolean embed = embedFont;

      // if a special character, and font is one of base14,
      // force it to embed font otherwise the builtin font in acrobat
      // would not contain the char
      // helvetica is displayed using arial by acrobat
      if(truetype && !embed && insetx >= 0 &&
         !((TTFontInfo) finfo).isAdvancedTypographic() &&
         (fontname.startsWith("Arial") ||
          fontname.startsWith("Courier") ||
          fontname.startsWith("Time")))
      {
         embed = true;
      }

      String fontkey = fontname + "_" + embed;
      // a font may be output more than once if we are dealing with
      // special characters, make sure we don't embed it more than once
      Integer fdId = fdIds.get(fontkey);
      boolean emitFontDescriptor = false;

      if(fdId == null) {
         fdId = getNextObjectID();
         emitFontDescriptor = true;
      }

      others.println("/FontDescriptor " + fdId + " 0 R");
      others.println(">>");
      others.println("endobj");

      if(emitFontDescriptor) {
         emitFontDescriptor(fdId, fontname, font, finfo, embed);
         fdIds.put(fontkey, fdId);
      }
   }

   /**
    * Close the pdf output stream. This MUST be called to complete the file.
    */
   @Override
   public void close() {
      end();
      super.close();
   }

   /**
    * Write fonts.
    */
   private void end() {
      for(Map.Entry<Font, Set<Integer>> e : stringinsetx.entrySet()) {
         if(e.getValue() != null) {
            for(Integer n : e.getValue()) {
               insetx = n;
               emitFont2(e.getKey());
            }
         }
      }
   }

   /**
    * Draw string with double coordinate values.
    */
   @Override
   public char[] drawString(String str, double sx, double sy) {
      Font font = getFont();
      String fname = "";

      if(font != null && str != null && embedFont) {
         String pfname = getFontName(font);
         FontInfo finfo = fontMgr.getFontInfo(pfname);
         fname = finfo == null ? pfname :
            getRegularFontName(getEmitFontName(finfo, font));
         setCurrentFontKey(fname);
      }

      char[] chars = super.drawString(str, sx, sy);

      if(font != null && str != null && embedFont) {
         BitSet set  = strings.get(fname);

         if(set == null) {
            set = new BitSet();
            strings.put(fname, set);
         }

         int len = chars.length;

         for(int i = 0; i < len; i++) {
            set.set(chars[i]);
         }
      }

      return null;
   }

   /**
    * Get the font name used to emit font descriptor.
    */
   protected String getEmitFontName(FontInfo finfo, Font font) {
      boolean truetype = finfo instanceof TTFontInfo;
      String pname = finfo.getFontName();
      pname = truetype && isWideCharString(pname, true) ?
         ((TTFontInfo)finfo).getPSName() : pname;
      pname = (pname == null) ? finfo.getFontName() : pname;
      return getPSName(strip(pname), font);
   }

   private static int[] hidin = new int[] {2305, 2306, 2307, 2364, 2366, 2367,
      2368, 2369, 2370, 2371, 2372, 2373, 2374, 2375, 2376, 2377, 2378, 2380,
      2381, 2385, 2386, 2387, 2388};
   FontManager fontMgr = FontManager.getFontManager();
   private Map<String, Integer> fdIds = new HashMap<>(); // font name + embed -> id
   private boolean embedFont = false; // embed truetype font
   private Set<String> fullyEmbeddedFonts;
   private boolean base14 = false; // use base 14 fonts only
   private Font lastFn = null;  // non static for multi thread report gen.
   private String lastPSFn = null;
   private FontInfo lastFontInfo = null;
   private Map<String, BitSet> strings = new HashMap<>(); //fontname --> chars
   private Map<Font, Set<Integer>> stringinsetx = new HashMap<>();
   private Map<String, String> strips = new HashMap<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(PDF3Printer.class);
}

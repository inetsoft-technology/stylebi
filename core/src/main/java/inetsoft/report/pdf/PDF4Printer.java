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

import inetsoft.report.internal.Common;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * PDF3Printer implements the full PDF file format, including font embedding.
 * It has the same API as the regular PDFPrinter. It should not be used
 * directly to generate a PDF. Use PDF3Generator.getPDFGenerator() and
 * generate a PDF from the generator.
 * <p>
 * PDF4Printer generates PDF files confirming to the PDF 1.3 (Acrobat 4.0)
 * specification. It supports CJK fonts using the Acrobat 4.0
 * Asian language support pack.
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class PDF4Printer extends PDF3Printer {
   /**
    * Create an empty PDF4Printer. The setOutput() and startDoc() must
    * be called before the PDF4Printer can be used.
    */
   public PDF4Printer() {
   }

   /**
    * Create a PDF4Printer to the specified output.
    */
   public PDF4Printer(OutputStream out) {
      super(out);
   }

   /**
    * Set whether to embed cmaps in PDF.
    * @param embed true to embed cmaps.
    */
   public void setEmbedCMap(boolean embed) {
      this.embedCMap = embed;
   }

   /**
    * Check whether to embed cmaps in PDF.
    */
   public boolean isEmbedCMap() {
      return embedCMap;
   }

   /**
    * Get the PDF font name.
    * @param font font object.
    */
   @Override
   public String getFontName(Font font) {
      String pdf4font;

      if(font.equals(last4Fn)) {
         return lastPDF4Fn;
      }

      last4Fn = font;
      if((lastPDF4Fn = pdf4FnCache.get(font)) != null) {
         return lastPDF4Fn;
      }

      String name = Common.getFontName(font);
      String specname = name;

      if((font.getStyle() & Font.BOLD) != 0) {
         specname += "-bold";
      }
      else {
         specname += "-plain";
      }

      String name2 = fontmap.get(specname.toLowerCase());

      if(name2 == null) {
         name2 = fontmap.get(name.toLowerCase());
      }

      // CJK fonts
      if(name2 != null && fontMgr.exists(name2)) {
         pdf4font = name2;
      }
      else {
         pdf4font = super.getFontName(font);
      }

      if(pdf4FnCache.size() > 50) {
         pdf4FnCache.clear();
      }

      pdf4FnCache.put(font, pdf4font);
      lastPDF4Fn = pdf4font;

      return pdf4font;
   }

   /**
    * Return the string width.
    */
   @Override
   protected float stringWidth(String str) {
      if(lastCJK != null && lastFontInfo != null) {
         return lastFontInfo.stringWidth(str, getFont().getSize());
      }

      return super.stringWidth(str);
   }

   /**
    * Send the set font command to output.
    */
   @Override
   protected String emitFont(Font font) {
      String[] cjk = null;
      FontInfo finfo = null;

      if(font.equals(lastFn)) {
         psFontName = lastPSFn;
         cjk = lastCJK;
         finfo = lastFontInfo;
      }
      else {
         psFontName = getFontName(font);
         cjk = fontMgr.getCJKInfo(psFontName);
         finfo = fontMgr.getFontInfo(psFontName);

         lastFn = font;
         lastPSFn = psFontName;
         lastCJK = cjk;
         lastFontInfo = finfo;
      }

      if(cjk == null) {
         return super.emitFont(font);
      }

      startPage();
      debug(pg, "%emitFont4");

      String fontsub = "";

      if((font.getStyle() & Font.BOLD) != 0) {
         fontsub += "Bold";
      }

      if((font.getStyle() & Font.ITALIC) != 0) {
         fontsub += "Italic";
      }

      String fn = fontFn.get(psFontName + fontsub);

      if(fn == null) {
         fn = "F" + getNextFontIndex();

         String fo = fontObj.get(psFontName + fontsub);

         if(fo == null) {
            if(finfo == null) {
               return super.emitFont(font);
            }

            int fontId = getNextObjectID();

            fo = fontId + " 0 R";
            fontObj.put(psFontName + fontsub, fo);
            fnObj.put(fn, fo);

            boolean truetype = finfo instanceof TTFontInfo;
            boolean cff = truetype && ((TTFontInfo) finfo).isCFFont();
            // getFontName() returns non-ascii name in cjk fonts
            // we use getFullName() instead (getFontName() in regular fonts)
            String fontname = strip(finfo.getFullName());

            // pdf appends bold or italic to the end of font name
            if(fontsub.length() > 0) {
               fontname += "," + fontsub;
            }

            others.markObject(fontId);
            others.println(fontId + " 0 obj");
            others.println("<<");
            others.println("/Type /Font");
            others.println("/Subtype /Type0");
            others.println("/BaseFont /" + getBaseFontName(fontname, font));

            Integer cmapId = null;

            if(isEmbedCMap()) {
               cmapId = cmapmap.get(cjk[1]);

               if(cmapId == null) {
                  cmapId = getNextObjectID();
               }

               others.println("/Encoding " + cmapId + " 0 R");
            }
            else {
               others.println("/Encoding /" + cjk[1]);
            }

            int desId = getNextObjectID();

            others.println("/DescendantFonts [" + desId + " 0 R]");
            others.println(">>");
            others.println("endobj");

            others.markObject(desId);
            others.println(desId + " 0 obj");
            others.println("<<");
            others.println("/Type /Font");
            others.println("/Subtype /" +
               ((truetype && !cff) ? "CIDFontType2" : "CIDFontType0"));
            others.println("/BaseFont /" + getBaseFontName(fontname, font));
            others.println("/CIDSystemInfo <<");

            others.println(" /Registry " + getTextString(desId, 0, "Adobe"));

            others.println(" /Ordering " + getTextString(desId, 0, cjk[0]));

            others.println(" /Supplement 2>>");

            int fdId = getNextObjectID();

            others.println("/FontDescriptor " + fdId + " 0 R");
            others.println("/DW 1000"); // default width is 1000

            // font width is necessary to support proportional font
            short[] cidws = ((TTFontInfo) finfo).getCIDWidths();

            others.print("/W [ ");
            for(int i = 1; i < cidws.length;/* inc in body */) {
               int ei = i + 1;

               for(; ei < cidws.length && cidws[ei] == cidws[i]; ei++) {
               }

               // same width cid range
               if(ei > i + 1) {
                  // ignore default width
                  if(cidws[i] != 1000) {
                     others.print(i + " " + (ei - 1) + " " + cidws[i] + " ");
                  }

                  i = ei - 1;
               }
               else {
                  others.print(i + " [");
                  for(; i < cidws.length; i++) {
                     // found same width glyph
                     if(i < cidws.length - 1 && cidws[i] == cidws[i + 1]) {
                        break;
                     }

                     others.print(cidws[i] + " ");
                  }

                  others.print("] ");
               }
            }

            others.println("]");

            others.println(">>");
            others.println("endobj");

            emitFontDescriptor(fdId, fontname, font, finfo, isEmbedFont());

            if(cmapId != null && cmapmap.get(cjk[1]) == null) {
               try {
                  InputStream input = CMap.getCMapData(cjk[1]);

                  ByteArrayOutputStream out = new ByteArrayOutputStream();
                  byte[] buf = new byte[256];
                  int cnt;

                  while((cnt = input.read(buf)) >= 0) {
                     out.write(buf, 0, cnt);
                  }

                  String[][] keys = { {"/Type", "/CMap"},
                                      {"/CIDSystemInfo",
                                       "<<\n/Registry (Adobe)\n/Ordering (" +
                                       cjk[0] + ")\n/Supplement 2 >>"},
                                      {"/CMapName", "/" + cjk[1]},};

                  emitStream(cmapId, out.toByteArray(), keys, true);
                  cmapmap.put(cjk[1], cmapId);
               }
               catch(Exception e) {
                  LOG.warn("Failed to load CMap information from file:" + cjk[1], e);
               }
            }
         }

         fnList.add("/" + fn + " " + fo + " ");
      }

      pg.println("/" + fn + " " + font.getSize() + " Tf");
      fontFn.put(psFontName + fontsub, fn);

      return fn;
   }

   /**
    * Check if current using font is CJK font.
    */
   @Override
   protected boolean isCurrentCJKFont() {
      return (psFontName != null && lastCJK != null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void updateCharacterSet(String s) {
      if(isCurrentCJKFont() && isEmbedFont()) {
         // mark the used characters, used in creating partial font for
         // CJK font embedding
         BitSet cidset = getCIDSet(strip(psFontName));
         TTFontInfo finfo = (TTFontInfo)lastFontInfo;

         if(finfo != null) {
            CMap cmap = finfo.getCMap();

            for(int i = 0; i < s.length(); i++) {
               int cid = cmap.map(s.charAt(i));

               // negative index ignored
               if(cid > 0) {
                  cidset.set(cid);
               }
            }
         }
      }
   }

   /**
    * Output the Tj command.
    */
   @Override
   protected void emitTj(String txt) {
      if(isCurrentCJKFont()) {
         try {
            txt = Tool.replaceAll(txt, "\r", "");

            if(txt.length() > 0) {
               pg.print(getTextString(txt, true) + " Tj\n");
               updateCharacterSet(txt);
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to emit text: " + txt, e);
         }
      }
      else {
         super.emitTj(txt);
      }
   }

   /**
    * Close the pdf output stream. This MUST be called to complete the file.
    */
   @Override
   public void close() {
      // embed the pending Q
      for(int i = 0; i < fontQ.size(); i++) {
         Object[] params = fontQ.get(i);

         super.embedFont((Integer) params[0], (String) params[1], (TTFontInfo) params[2]);
      }

      super.close();
   }

   /**
    * Return the PDF version of the documents generated by this class.
    * Acrobat 3.0 is PDF 1.2, and Acrobat 4.0 is PDF 1.3.
    * @return PDF version number, e.g., "1.2".
    */
   @Override
   public String getPDFVersion() {
      return "1.3";
   }

   /**
    * Embed a font file as the specified pdf object.
    */
   @Override
   void embedFont(int id, String fontname, TTFontInfo finfo) {
      if(!finfo.isCJKFont()) {
         super.embedFont(id, fontname, finfo);
      }
      else {
         fontQ.add(new Object[] {id, fontname, finfo});
      }
   }

   /**
    * Get font file data.
    */
   /*
   byte[] getFontData(String fontname, TTFontInfo finfo)
      throws FileNotFoundException, IOException {
      // by charlesy, fix customer bug bug1329175547875, need to embed the
      // whole character set in PDF, or the contents may be not displayed
      // correctly sometimes.
      if(isEmbedFont() && containsArabicCombinCharacter()) {
         return finfo.getFontData();
      }

      fontname = getRegularFontName(fontname);

      if(!finfo.isCJKFont()) {
         return super.getFontData(fontname, finfo);
      }

      return finfo.getFontData(getCIDSet(fontname));
   }
   */

   /**
    * Get a CID set for the font.
    */
   BitSet getCIDSet(String fontname) {
      BitSet cidset = cidsets.get(fontname);

      if(cidset == null) {
         cidsets.put(fontname, cidset = new BitSet());
         cidset.set(0);
      }

      return cidset;
   }

   /**
    * Format font name.
    */
   @Override
   protected String getPSName(String name, Font font) {
      String fname = font.getFontName();

      if(specfonts.contains(fname)) {
         return font.getPSName();
      }

      return super.getPSName(name, font);
   }

   Map<String, BitSet> cidsets = new HashMap<>(); // font name -> BitSet
   Map<String, Integer> cmapmap = new HashMap<>(); // cmap name -> cmap obj id
   List<Object[]> fontQ = new ArrayList<>(); // font embedding Q {id, fontname, TTFontInfo}
   boolean embedCMap = false;
   String[] lastCJK = null;
   FontInfo lastFontInfo = null;
   Font lastFn = null;
   String lastPSFn = null;
   Font last4Fn = null;
   String lastPDF4Fn = null;
   Map<Font, String> pdf4FnCache = new HashMap<>();

   // fix bug1311272805919 simple fix this customer issue
   private static List<String> specfonts; // specified font name
   static {
      specfonts = new ArrayList<>();
      specfonts.add("Trade Gothic LT Bold Condensed No. 20 Oblique");
      specfonts.add("Trade Gothic LT Condensed No. 18");
      specfonts.add("Trade Gothic LT Bold Condensed No. 20");
      specfonts.add("Trade Gothic LT Condensed No. 18 Oblique");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(PDF4Printer.class);
}

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
package inetsoft.report.composition;

import inetsoft.uql.util.filereader.TextFileType;
import inetsoft.uql.util.filereader.TextUtil;
import inetsoft.util.Tool;
import org.mozilla.universalchardet.UniversalDetector;
import org.w3c.dom.Element;

import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CSVInfo provides available settings for import csv file.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class CSVInfo implements inetsoft.util.XMLSerializable {
   /**
    * Max check row count.
    */
   public static final int CHECK_ROW = 10;

   /**
    * Get a proper CSV info.
    */
   public static CSVInfo getCSVInfo(String[] lines) {
      boolean[] quotes = new boolean[] {true, false};
      char[] delims = new char[] {'\t', ';', ',', '|'};
      CSVInfo pinfo = null;
      int score = 0;

      for(int i = 0; i < quotes.length; i++) {
         for(int j = 0; j < delims.length; j++) {
            CSVInfo info = new CSVInfo(quotes[i], delims[j], null, true);
            int score2 = info.getScore(lines);

            if(score2 > score) {
               pinfo = info;
               score = score2;
            }
         }
      }

      return pinfo;
   }

   /**
    * Get the specified file's encode.
    * If encode is UTF-16BE or UTF-16LE, just return "Unicode",
    * if encode is GB2312 or GB18030, return "GBK" instead.
    */
   public static String getFileEncode(File file) throws IOException {
      FileInputStream fis = new FileInputStream(file);
      UniversalDetector detector = new UniversalDetector(null);
      byte[] buf = new byte[4096];
      int nread;

      while((nread = fis.read(buf)) > 0 && !detector.isDone()) {
         detector.handleData(buf, 0, nread);
      }

      detector.dataEnd();
      String encode = detector.getDetectedCharset();
      detector.reset();
      fis.close();

      if(encode == null) {
         encode = getSystemEncode();
      }
      else if(encode.startsWith("UTF-16")) {
         encode = "Unicode";
      }
      else if(encode.equals("GB2312") || encode.equals("GB18030")) {
         encode = "GBK";
      }

      return encode;
   }

   /**
    * Get system's encoding, default is "UTF-8".
    */
   public static String getSystemEncode() {
      return System.getProperty("file.encoding", "UTF-8");
   }

   /**
    * Constructor.
    */
   public CSVInfo() {
      this(true, ',', null, true);
   }

   /**
    * Constructor.
    */
   public CSVInfo(boolean quote, char delim, String encode, boolean header) {
      super();

      this.quote = quote;
      this.delim = delim;
      this.encode = encode;
      this.header = header;
      this.column = -1;
      this.unpivot = false;
   }

   /**
    * Get the matching score for using the quote and delimiter.
    * @param lines the specified String[].
    */
   private int getScore(String[] lines) {
      int score = 0;

      if(lines.length == 0) {
         return score;
      }

      for(int i = 0; i < lines.length && i < CHECK_ROW; i++) {
         int score2 = getLineScore(lines[i]);
         score = (score == 0) ? score2 : Math.min(score, score2);
      }

      header = isFirstRowAsHeader(lines[0]);
      encode = System.getProperty("file.encoding", "UTF-8");
      return score;
   }

   /**
    * Process the delimiter will fit two strings.
    * @param header the specified string.
    * @return <tt>true means<tt> first row as header, <tt>false<tt> otherwise.
    */
   private boolean isFirstRowAsHeader(String header) {
      String[] cols;

      if(quote) {
         cols = TextUtil.split(header, delim + "");
      }
      else {
         cols = header.split(delim + "");
      }

      Pattern nonDigital = Pattern.compile("\\D");

      for(int i = 0; i < cols.length; i++) {
         Matcher match = nonDigital.matcher(cols[i].trim());

         if(!match.find() && cols[i].trim().length() > 0) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the matching score for the line.
    * @param line the specified String.
    */
   private int getLineScore(String line) {
      if(line.length() == 0) {
         return 0;
      }

      boolean inquota = false;
      int count = 0;
      char c = '\uffff';

      for(int i = 0; i < line.length(); i++) {
         char lc = c;
         c = line.charAt(i);

         if(inquota) {
            if(c != '\"') {
               continue;
            }
            else {
               if(i < line.length() - 1 && line.charAt(i + 1) != delim) {
                  if(line.charAt(i + 1) == '\"') {
                     i++;
                     continue;
                  }
                  else {
                     return 0;
                  }
               }

               inquota = false;
               continue;
            }
         }
         else {
            if(c == '\"' && quote) {
               if(i > 0 && lc != delim) {
                  return 0;
               }

               inquota = true;
               continue;
            }
            else if(c == delim) {
               count++;
               continue;
            }
         }
      }

      if(inquota) {
         return 0;
      }

      count++;
      this.column = this.column == -1 ? count : this.column;

      if(this.column != count || this.column <= 1) {
         return column;
      }

      return this.column + 100;
   }

   /**
    * Write the xml segment to the destination writer.
    */
   @Override
   public final void writeXML(PrintWriter writer) {
      writer.print("<CSVInfo ");
      writeAttributes(writer);
      writer.println(">");
      writer.println("</CSVInfo>");
   }

   /**
    * Write attributes to a XML segment.
    */
   protected void writeAttributes(PrintWriter writer) {
      writer.print("encode=\"" + encode + "\" delimiter=\"" + Tool.escape("" + delim) + "\" ");
      writer.print("firstRowAsHeader=\"" + header + "\" ");
      writer.print("removeQuotationMark=\"" + quote + "\" ");
      writer.print("unpivot=\"" + unpivot + "\" ");
      writer.print("hcol=\"" + hcol + "\" ");

      if(type != null) {
         writer.print("type=\"" + type + "\" ");
      }

      if(sheet != null) {
         writer.print("sheet=\"" + Tool.encodeHTMLAttribute(sheet) + "\" ");
      }

      if(sheets != null) {
         writer.print("sheets=\"" + Tool.encodeHTMLAttribute(sheets) + "\" ");
      }
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
   }

   /**
    * Parse the attribute part.
    */
   protected void parseAttributes(Element tag) throws Exception {
      String val;

      if((val = Tool.getAttribute(tag, "encode")) != null) {
         encode = val;
      }

      if((val = Tool.getAttribute(tag, "delimiter")) != null) {
         if(val.equals("\\" + "t")) {
            delim = '\t';
         }
         else {
            delim = val.charAt(0);
         }
      }

      if((val = Tool.getAttribute(tag, "firstRowAsHeader")) != null) {
         header = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "removeQuotationMark")) != null) {
         quote = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "unpivot")) != null) {
         unpivot = "true".equals(val);
      }

      if((val = Tool.getAttribute(tag, "hcol")) != null) {
         hcol = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "type")) != null) {
         type = TextFileType.valueOf(val);
      }
      else {
         type = TextFileType.DELIMITED;
      }

      if((val = Tool.getAttribute(tag, "sheet")) != null) {
         sheet = val;
      }
   }

   /**
    * Set the associated encode.
    */
   public void setEncode(String encode) {
      this.encode = encode;
   }

   /**
    * Get the associated encode.
    */
   public String getEncode() {
      return encode;
   }

   /**
    * Set the associated delimiter.
    */
   public void setDelimiter(char delim) {
      this.delim = delim;
   }

   /**
    * Get the associated delimiter.
    */
   public char getDelimiter() {
      return delim;
   }

   /**
    * Set the first row as header or not.
    */
   public void setFirstRowAsHeader(boolean header) {
      this.header = header;
   }

   /**
    * Get the first row as header or not.
    */
   public boolean isFirstRowAsHeader() {
      return header;
   }

   /**
    * Set weather remove the associated quotation mark.
    */
   public void setRemoveQuotationMark(boolean quote) {
      this.quote = quote;
   }

   /**
    * Get is remove the quotation mark or not.
    */
   public boolean isRemoveQuotationMark() {
      return quote;
   }

   /**
    * Set whether to unpivot the table.
    */
   public void setUnpivot(boolean unpivot) {
      this.unpivot = unpivot;
   }

   /**
    * Check whether to unpivot the table.
    */
   public boolean isUnpivot() {
      return unpivot;
   }

   /**
    * Set the header column count.
    */
   public void setHeaderColCount(int cnt) {
      this.hcol = cnt;
   }

   /**
    * Get the header column count.
    */
   public int getHeaderColCount() {
      return hcol;
   }

   /**
    * Gets the type of the data file.
    *
    * @return the file type.
    */
   public final TextFileType getFileType() {
      return type;
   }

   /**
    * Sets the type of the data file.
    *
    * @param type the file type.
    */
   public final void setFileType(TextFileType type) {
      this.type = type;
   }

   /**
    * Gets all sheet names
    * @return the sheet names
    */
   public final String getSheets(){ return sheets;}

   /**
    * Set all sheet names.
    */
   public final void setSheets(String sheets) {
      this.sheets = sheets;
   }

   /**
    * Gets the name of the selected sheet.
    *
    * @return the sheet name.
    */
   public final String getSheet() {
      return sheet;
   }

   /**
    * Sets the name of the selected sheet.
    *
    * @param sheet the sheet name.
    */
   public final void setSheet(String sheet) {
      this.sheet = sheet;
   }

   /**
    * The indexes of columns which just need to keep as string type.
    * This was user option after parsing these columns failed.
    * @return
    */
   public List<Integer> getIgnoreTypeColumns() {
      return ignoreTypeColumns;
   }

   public void setIgnoreTypeColumns(List<Integer> ignoreTypeColumns) {
      this.ignoreTypeColumns = ignoreTypeColumns;
   }

   @Override
   public String toString() {
      return "CSVInfo{" +
         "column=" + column +
         ", quote=" + quote +
         ", delim=" + delim +
         ", encode='" + encode + '\'' +
         ", header=" + header +
         ", unpivot=" + unpivot +
         ", hcol=" + hcol +
         ", type=" + type +
         ", sheet='" + sheet + '\'' +
         '}';
   }

   private int column;
   private boolean quote;
   private char delim;
   private String encode;
   private boolean header;
   private boolean unpivot;
   private int hcol = 1; // header column count
   private TextFileType type;
   private String sheet;
   // all sheet names joined by "__^__", a trainsent value to set to web
   private String sheets;
   private List<Integer> ignoreTypeColumns;
}

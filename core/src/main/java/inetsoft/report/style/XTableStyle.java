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
package inetsoft.report.style;

import inetsoft.report.*;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XMLTokenStream;
import inetsoft.report.internal.table.TableTool;
import inetsoft.report.io.helper.ReportHelper;
import inetsoft.report.io.helper.TableStyleHelper;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXParseException;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * The XTableStyle class can be used together with the table style markup
 * files to create table style objects. It parses the table style markup
 * files to create an object conforming to the TableStyle API. The parsing
 * is very forgiving about the syntax of the file. However, it's strongly
 * recommended to use the correct syntax if the file is created by hand to
 * ensure future compatibility.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class XTableStyle extends TableStyle {
   /**
    * Create a XTableStyle from input stream.
    */
   public static XTableStyle getXTableStyle(TableLens table,
                                            InputStream input)
         throws IOException
   {
      try {
         Document doc = Tool.parseXML(input);
         NodeList nlist = doc.getElementsByTagName("table-style");

         if(nlist.getLength() > 0) {
            return getXTableStyle(table, (Element) nlist.item(0));
         }

         return null;
      }
      catch(Exception e) {
         throw new IOException("Failed to parse table style", e);
      }
   }

   /**
    * Create a XTableStyle from an element node.
    */
   public static XTableStyle getXTableStyle(TableLens table, Element tag)
      throws IOException
   {
      try {
         TableStyleHelper helper =
            (TableStyleHelper) ReportHelper.getReportHelper(tag,
               ReportHelper.MAX_VERSION, ReportHelper.TEMPLATE);
         return (XTableStyle) helper.read(tag, table);
      }
      catch(Exception e) {
         throw new IOException("Parsing error: " + e);
      }
   }

   /**
    * Create a transparent style. The style attributes can be changed
    * by explicitly setting them later.
    * @param table original table.
    */
   public XTableStyle(TableLens table) {
      setTable(table);
   }

   @Override
   public void setTable(TableLens table) {
      super.setTable(table);
      crosstab = Util.getCrosstab(table);
      headerRows = headerCols = null;
   }

   @Override
   public int getHeaderRowCount() {
      // optimization
      if(headerRows != null) {
         return headerRows;
      }

      return headerRows = super.getHeaderRowCount();
   }

   /**
    * Set the number of header rows.
    * @param headerRow number of header rows.
    */
   @Override
   public void setHeaderRowCount(int headerRow) {
      headerRows = headerRow;
   }

   @Override
   public int getHeaderColCount() {
      // optimization
      if(headerCols != null) {
         return headerCols;
      }

      return headerCols = super.getHeaderColCount();
   }

   /**
    * Create a style from a XML table style definition.
    * @param xmlstream XML table definition.
    */
   public XTableStyle(TableLens table, InputStream xmlstream) throws IOException{
      readFromStream(table, xmlstream);
   }

   /**
    * Create a style from a XML table style definition.
    * @param xmlfile XML table definition.
    */
   public XTableStyle(TableLens table, File xmlfile) throws IOException {
      FileInputStream input = new FileInputStream(xmlfile);

      readFromStream(table, input);
      input.close();
   }

   /**
    * Return the name of this style. The name can be defined in the
    * TABLE-STYLE element in XML.
    * @return style name or null if it's not defined.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Change the name of this style. If a name is not set in a XTableStyle,
    * the XML file name is used as the style name.
    * @param name style name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Return the id of this style.
    * @return style id.
    */
   @Override
   public String getID() {
      return id;
   }

   /**
    * Change the id of this style.
    * @param id style id.
    */
   public void setID(String id) {
      this.id = id;
   }

   /**
    * Get created time.
    * @return created time.
    */
   public long getCreated() {
      return created;
   }

   /**
    * Set created time.
    * @param created the specified created time.
    */
   public void setCreated(long created) {
      this.created = created;
   }

   /**
    * Get last modified.
    * @return last modified time.
    */
   public long getLastModified() {
      return modified;
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   public void setLastModified(long modified) {
      this.modified = modified;
   }

   /**
    * Get the created person.
    * @return the created person.
    */
   public String getCreatedBy() {
      return createdBy;
   }

   /**
    * Set the created person
    * @param createdBy the created person.
    */
   public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
   }

   /**
    * Get last modified person.
    * @return last modified person.
    */
   public String getLastModifiedBy() {
      return modifiedBy;
   }

   /**
    * Set last modified person.
    * @param modifiedBy the specified last modified person.
    */
   public void setLastModifiedBy(String modifiedBy) {
      this.modifiedBy = modifiedBy;
   }

   /**
    * Add or replace a style attribute setting. The name of the attribute
    * is the name of the element, appended by a '.', and the name of the
    * attribute in the element. For example, the foreground setting for
    * the header row is 'header-row.foreground'.
    * @param attr attribute name.
    * @param val attribute setting.
    */
   public void put(String attr, Object val) {
      int idx = attr.indexOf('.');

      if(idx < 0) {
         return;
      }

      String t_str = attr.substring(0, idx).toLowerCase();
      String a_str = attr.substring(idx + 1).toLowerCase();
      int t_idx = findIndex(t_str, elemlist);
      int a_idx = findIndex(a_str, attrlist);

      if(t_idx >= 0 && a_idx >= 0) {
         put(t_idx, a_idx, val);
      }
      else {
         throw new RuntimeException("Unknown attribute: " + attr);
      }
   }

   /**
    * Get the value for the attribute.
    */
   public Object get(String attr) {
      int idx = attr.indexOf('.');

      if(idx < 0) {
         return null;
      }

      String t_str = attr.substring(0, idx).toLowerCase();
      String a_str = attr.substring(idx + 1).toLowerCase();
      int t_idx = findIndex(t_str, elemlist);
      int a_idx = findIndex(a_str, attrlist);

      if(t_idx >= 0 && a_idx >= 0) {
         return get(t_idx, a_idx);
      }
      else {
         throw new RuntimeException("Unknown attribute: " + attr);
      }
   }

   /**
    * Get the number of specifications.
    */
   public int getSpecificationCount() {
      return speclist.size();
   }

   /**
    * Get specified specification.
    */
   public Specification getSpecification(int idx) {
      return speclist.get(idx);
   }

   /**
    * Remove a specification.
    */
   public void removeSpecification(int idx) {
      speclist.remove(idx);
   }

   /**
    * Clear a specification.
    */
   public void clearSpecification() {
      speclist.clear();
   }

   /**
    * Add a new specification.
    */
   public void addSpecification(Specification spec) {
      speclist.add(spec);
   }

   /**
    * Clear all attribute settings from the style.
    */
   public void clear() {
      for(int i = 0; i < attrmap.length; i++) {
         for(int j = 0; j < attrmap[i].length; j++) {
            attrmap[i][j] = null;
         }
      }

      speclist.clear();
   }

   /**
    * Import the attributes from a XML stream. The existing style attributes
    * are discarded.
    * @param xml XML input stream.
    */
   public void parse(XMLTokenStream xml) throws IOException {
      style = new Style(xml);
   }

   /**
    * Write the current style setting to a XML stream.
    * @param xml output stream.
    */
   public void export(OutputStream xml) {
      int[] elemidx = {t_top_border, t_bottom_border, t_left_border,
                       t_right_border, t_header_row, t_header_col, t_trailer_row,
                       t_trailer_col, t_body};
      int[] attridx = {a_border, a_color, a_bcolor, a_foreground, a_background,
                       a_font, a_alignment, a_row_border, a_rcolor, a_col_border, a_ccolor,
                       a_height, a_padding};

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(xml));

      writer.print("<table-style id=\"");
      writer.print(Tool.byteEncode(Encode.forHtmlAttribute(id)));
      writer.print("\" name=\"");
      writer.print(Tool.byteEncode(Encode.forHtmlAttribute(name)));

      if(createdBy != null) {
         writer.print("\" createdBy=\"");
         writer.print(Tool.byteEncode(Encode.forHtmlAttribute(createdBy)));
      }

      if(modifiedBy != null) {
         writer.print("\" modifiedBy=\"");
         writer.print(Tool.byteEncode(Encode.forHtmlAttribute(modifiedBy)));
      }

      if(created != 0) {
         writer.print("\" created=\"");
         writer.print(created);
      }

      if(modified != 0) {
         writer.print("\" modified=\"");
         writer.print(modified);
      }

      writer.println("\">");

      for(int i = 0; i < elemlist.length; i++) {
         writer.print("<" + elemlist[i] + " ");
         for(int j = 0; j < attrlist.length; j++) {
            Object val = get(elemidx[i], attridx[j]);

            if(val == null) {
               continue;
            }

            if(val instanceof Integer) {
               writer.print(" " + attrlist[j] + "=\"" +
                  ((Integer) val).intValue() + "\"");
            }
            else if(val instanceof Color) {
               writer.print(" " + attrlist[j] + "=\"" + ((Color) val).getRGB() +
                  "\"");
            }
            else if(val instanceof Font) {
               writer.print(" " + attrlist[j] + "=\"" +
                  StyleFont.toString((Font) val) + '"');
            }
            else {
               writer.print(" " + attrlist[j] + "=" + '"' + val + '"');
            }
         }

         writer.println(">");
         writer.println("</" + elemlist[i] + ">");
      }

      for(int i = 0; i < speclist.size(); i++) {
         Specification spec = (Specification) speclist.get(i);
         int[] range = spec.getRange();

         writer.print("<specification index=\"" + spec.getIndex() +
            "\" row=\"" + spec.isRow() + "\" type=\"" + spec.getType() +
            "\" repeat=\"" + spec.isRepeat() + "\" ");

         if(range != null && range.length == 2) {
            writer.print("range=\"" + range[0] + "," + range[1] + "\" ");
         }

         for(int j = 0; j < attrlist.length; j++) {
            Object val = spec.get(attridx[j]);

            if(val == null) {
               continue;
            }

            if(val instanceof Integer) {
               writer.print(" " + attrlist[j] + "=\"" +
                  ((Integer) val).intValue() + "\"");
            }
            else if(val instanceof Color) {
               writer.print(" " + attrlist[j] + "=\"" + ((Color) val).getRGB() +
                  "\"");
            }
            else if(val instanceof Font) {
               writer.print(" " + attrlist[j] + "=\"" +
                  StyleFont.toString((Font) val) + '"');
            }
            else {
               writer.print(" " + attrlist[j] + "=" + '"' + val + '"');
            }
         }

         writer.println("/>");
      }

      writer.println("</table-style>");
      writer.flush();
   }

   /**
    * Create a table style for the specified table.
    * @param table table lens.
    * @return the style lens to be used with the table.
    */
   @Override
   protected TableLens createStyle(TableLens table) {
      return new Style();
   }

   /**
    * Read the style from am xml stream.
    */
   private void readFromStream(TableLens table, InputStream input)
      throws IOException
   {
      try {
         Document doc = Tool.parseXML(input);
         NodeList nlist = doc.getElementsByTagName("Version");
         Element tag = null;

         if(nlist.getLength() > 0) {
            tag = (Element) nlist.item(0);
         }

         String version = tag == null ? null : Tool.getValue(tag);

         nlist = doc.getElementsByTagName("table-style");

         if(nlist.getLength() > 0) {
            tag = (Element) nlist.item(0);
            TableStyleHelper helper =
               (TableStyleHelper) ReportHelper.getReportHelper(tag, version,
                  ReportHelper.TEMPLATE);
            XTableStyle style = (XTableStyle) helper.read(tag, table);

            read(style);
         }
      }
      catch(SAXParseException e) {
         LOG.error(
            "Parsing error at line[" + e.getLineNumber() + "] Column[" +
               e.getColumnNumber() + "]");
         throw new IOException("Failed to parse table style", e);
      }
      catch(Exception e) {
         throw new IOException("Failed to parse table style", e);
      }
   }

   /**
    * Read properties from another XTableStyle.
    */
   private void read(XTableStyle style) {
      setTable(style.table);
      id = style.id;
      name = style.name;
      attrmap = style.attrmap.clone();

      speclist.clear();

      for(int i = 0; i < style.speclist.size(); i++) {
         speclist.add(style.getSpecification(i).clone(this));
      }

      headerRF = style.headerRF;
      headerCF = style.headerCF;
      tailRF = style.tailRF;
      tailCF = style.tailCF;
      widthF = style.widthF;
      heightF = style.heightF;
      rowBorderCF = style.rowBorderCF;
      colBorderCF = style.colBorderCF;
      rowBorderF = style.rowBorderF;
      colBorderF = style.colBorderF;
      insetsF = style.insetsF;
      spanF = style.spanF;
      alignF = style.alignF;
      fontF = style.fontF;
      wrapF = style.wrapF;
      foregroundF = style.foregroundF;
      backgroundF = style.backgroundF;
      presenterF = style.presenterF;
      firstRow = style.firstRow;
      firstCol = style.firstCol;
      lastRow = style.lastRow;
      lastCol = style.lastCol;
   }

   /**
    * Find a string in a list and return the index.
    */
   static int findIndex(String str, String[] list) {
      str = str.toLowerCase();

      for(int i = 0; i < list.length; i++) {
         if(list[i].equals(str)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Find a specification matching the row/column.
    */
   Specification findSpec(int r, int c, int a) {
      for(int i = 0; i < getSpecificationCount(); i++) {
         Specification spec = getSpecification(i);

         if(spec.get(a) != null && spec.getMatcher().match(r, c)) {
            return spec;
         }
      }

      return null;
   }

   /**
    * Get a style attribute setting.
    */
   Object get(int t, int a) {
      return attrmap[t][a];
   }

   /**
    * Get a style attribute from specifications.
    */
   Object get(int r, int c, int a) {
      Specification spec = findSpec(r, c, a);

      return (spec != null) ? spec.get(a) : null;
   }

   /**
    * Add or replace a style attribute setting.
    * @param val style value.
    */
   void put(int t, int a, Object val) {
      attrmap[t][a] = val;
   }

   /**
    * Style filter.
    */
   class Style extends Transparent {
      public Style() {
      }

      /**
       * Read the XML table style definition.
       */
      public Style(XMLTokenStream xml) throws IOException {
         Object tok;

         while((tok = xml.getToken()) != null) {
            if(tok instanceof XMLTokenStream.Tag) {
               XMLTokenStream.Tag tag = (XMLTokenStream.Tag) tok;

               if(tag.getName().equals("TABLE-STYLE")) {
                  name = Tool.byteDecode(tag.get("NAME"));
                  id = Tool.byteDecode(tag.get("ID"));
                  continue;
               }
               else if(tag.getName().equals("/TABLE-STYLE")) {
                  break;
               }

               String tname = tag.getName();
               Specification spec = tname.equals("SPECIFICATION") ?
                  new Specification() :
                  null;

               if(spec != null) {
                  String prop;

                  if((prop = tag.get("index")) != null) {
                     spec.setIndex(Integer.parseInt(prop));
                  }

                  if((prop = tag.get("type")) != null) {
                     spec.setType(Integer.parseInt(prop));
                  }

                  if((prop = tag.get("row")) != null) {
                     spec.setRow(prop.equalsIgnoreCase("true"));
                  }

                  if((prop = tag.get("repeat")) != null) {
                     spec.setRepeat(prop.equalsIgnoreCase("true"));
                  }

                  if((prop = tag.get("range")) != null) {
                     String[] sa = Tool.split(prop, ',');

                     if(sa.length == 2) {
                        spec.setRange(new int[] {Integer.parseInt(sa[0]),
                           Integer.parseInt(sa[1])});
                     }
                  }

                  speclist.add(spec);
               }

               Enumeration keys = tag.getAttributes();

               while(keys.hasMoreElements()) {
                  String attr = (String) keys.nextElement();
                  String val = tag.get(attr);

                  if(attr.equals("BORDER") || attr.equals("ROW-BORDER") ||
                     attr.equals("COL-BORDER"))
                  {
                     int line = StyleFont.decodeLineStyle(val);

                     if(line < 0) {
                        throw new IOException("Unknow border style: " + val);
                     }

                     if(spec != null) {
                        spec.put(attr, line);
                     }
                     else {
                        put(tname + "." + attr, line);
                     }
                  }
                  else if(attr.equals("COLOR") || attr.equals("BCOLOR") ||
                          attr.equals("RCOLOR") || attr.equals("CCOLOR") ||
                          attr.equals("FOREGROUND") ||
                          attr.equals("BACKGROUND")) {
                     try {
                        Color clr = new Color(Integer.decode(val).intValue());

                        if(spec != null) {
                           spec.put(attr, clr);
                        }
                        else {
                           put(tname + "." + attr, clr);
                        }
                     }
                     catch(Exception e) {
                        LOG.error("Invalid color format: " + val, e);
                        LOG.error(e.getMessage(), e);
                        throw new IOException(
                           "Invalid color format: " + val, e);
                     }
                  }
                  else if(attr.equals("FONT")) {
                     Font font = StyleFont.decode(val);

                     if(font == null) {
                        throw new IOException("Font format error: " + val);
                     }

                     if(spec != null) {
                        spec.put(attr, font);
                     }
                     else {
                        put(tname + "." + attr, font);
                     }
                  }
                  else if(attr.equals("ALIGNMENT")) {
                     int align = 0;

                     if(Character.isDigit(val.charAt(0))) {
                        align = Integer.decode(val).intValue();
                     }
                     else {
                        if(val.indexOf("H_LEFT") >= 0) {
                           align |= StyleConstants.H_LEFT;
                        }
                        else if(val.indexOf("H_CENTER") >= 0) {
                           align |= StyleConstants.H_CENTER;
                        }
                        else if(val.indexOf("H_RIGHT") >= 0) {
                           align |= StyleConstants.H_RIGHT;
                        }

                        if(val.indexOf("V_TOP") >= 0) {
                           align |= StyleConstants.V_TOP;
                        }
                        else if(val.indexOf("V_CENTER") >= 0) {
                           align |= StyleConstants.V_CENTER;
                        }
                        else if(val.indexOf("V_BOTTOM") >= 0) {
                           align |= StyleConstants.V_BOTTOM;
                        }
                     }

                     if(spec != null) {
                        spec.put(attr, align);
                     }
                     else {
                        put(tname + "." + attr, align);
                     }  // end of else
                  }  // end of else if
               }
               // end of while
            }
            // end of if
         }
         // end of while
      }  // end of method

      /**
       * Return the color for drawing the row border lines.
       * @param r row number.
       * @param c column number.
       * @return ruling color.
       */
      @Override
      public Color getRowBorderColor(int r, int c) {
         Object clr;

         if(r == -1 && (clr = get(t_top_border, a_color)) != null ||
            r == lastRow() && (clr = get(t_bottom_border, a_color)) != null ||
            (isHeaderRowFormat(r) &&
            (clr = get(t_header_row, a_bcolor)) != null) ||
            (isTrailerRowFormat(r + 1) && r == lastRow() - 1 &&
            (clr = get(t_trailer_row, a_bcolor)) != null) ||
            (isHeaderColFormat(c) &&
            (clr = get(t_header_col, a_rcolor)) != null) ||
            (isTrailerColFormat(c) &&
            (clr = get(t_trailer_col, a_rcolor)) != null) ||
            ((clr = get(r, c, a_rcolor)) != null) ||
            ((clr = get(t_body, a_rcolor)) != null))
         {
            return (Color) clr;
         }

         return super.getRowBorderColor(r, c);
      }

      /**
       * Return the color for drawing the column border lines.
       * @param r row number.
       * @param c column number.
       * @return ruling color.
       */
      @Override
      public Color getColBorderColor(int r, int c) {
         Object clr;

         if(c == -1 && (clr = get(t_left_border, a_color)) != null ||
            c == lastCol() && (clr = get(t_right_border, a_color)) != null ||
            (isHeaderRowFormat(r) &&
            (clr = get(t_header_row, a_ccolor)) != null) ||
            (isTrailerRowFormat(r) &&
            (clr = get(t_trailer_row, a_ccolor)) != null) ||
            (isHeaderColFormat(c) &&
            (clr = get(t_header_col, a_bcolor)) != null) ||
            (isTrailerColFormat(c + 1) && c == lastCol() - 1 &&
            (clr = get(t_trailer_col, a_bcolor)) != null) ||
            ((clr = get(r, c, a_ccolor)) != null) ||
            ((clr = get(t_body, a_ccolor)) != null))
         {
            return (Color) clr;
         }

         return super.getColBorderColor(r, c);
      }

      /**
       * Return the style for bottom border of the specified cell. The flag
       * must be one of the style options defined in the StyleConstants
       * class. If the row number is -1, it's checking the outside ruling
       * on the top.
       * @param r row number.
       * @param c column number.
       * @return ruling flag.
       */
      @Override
      public int getRowBorder(int r, int c) {
         Object v;

         if(r == -1 && (v = get(t_top_border, a_border)) != null ||
            r == lastRow() && (v = get(t_bottom_border, a_border)) != null ||
            (isHeaderRowFormat(r) &&
             (v = get(t_header_row, a_border)) != null) ||
            (isTrailerRowFormat(r + 1) && r == lastRow() - 1 &&
            (v = get(t_trailer_row, a_border)) != null) ||
            (isHeaderColFormat(c) &&
            (v = get(t_header_col, a_row_border)) != null) ||
            (isTrailerColFormat(c) &&
            (v = get(t_trailer_col, a_row_border)) != null) ||
            ((v = get(r, c, a_row_border)) != null) ||
            ((v = get(t_body, a_row_border)) != null))
         {
            return ((Integer) v).intValue();
         }

         return super.getRowBorder(r, c);
      }

      /**
       * Return the style for right border of the specified row. The flag
       * must be one of the style options defined in the StyleConstants
       * class. If the column number is -1, it's checking the outside ruling
       * on the left.
       * @param r row number.
       * @param c column number.
       * @return ruling flag.
       */
      @Override
      public int getColBorder(int r, int c) {
         Object v;

         if(c == -1 && (v = get(t_left_border, a_border)) != null ||
            c == lastCol() && (v = get(t_right_border, a_border)) != null ||
            (isHeaderRowFormat(r) &&
            (v = get(t_header_row, a_col_border)) != null) ||
            (isTrailerRowFormat(r) &&
            (v = get(t_trailer_row, a_col_border)) != null) ||
            (isHeaderColFormat(c) &&
             (v = get(t_header_col, a_border)) != null) ||
            (isTrailerColFormat(c + 1) && c == lastCol() - 1 &&
            (v = get(t_trailer_col, a_border)) != null) ||
            ((v = get(r, c, a_col_border)) != null) ||
            ((v = get(t_body, a_col_border)) != null))
         {
            return ((Integer) v).intValue();
         }

         return super.getColBorder(r, c);
      }

      /**
       * Return the per cell alignment.
       * @param r row number.
       * @param c column number.
       * @return cell alignment.
       */
      @Override
      public int getAlignment(int r, int c) {
         Object v;

         if((isHeaderRowFormat(r) &&
             (v = get(t_header_row, a_alignment)) != null) ||
            (isTrailerRowFormat(r) &&
             (v = get(t_trailer_row, a_alignment)) != null) ||
            (isHeaderColFormat(c) &&
             (v = get(t_header_col, a_alignment)) != null) ||
            (isTrailerColFormat(c) &&
             (v = get(t_trailer_col, a_alignment)) != null) ||
            ((v = get(r, c, a_alignment)) != null) ||
            ((v = get(t_body, a_alignment)) != null))
         {
            return ((Integer) v).intValue();
         }

         return super.getAlignment(r, c);
      }

      /**
       * Return the per cell font. Return null to use default font.
       * @param r row number.
       * @param c column number.
       * @return font for the specified cell.
       */
      @Override
      public Font getFont(int r, int c) {
         Object v;

         if((isHeaderRowFormat(r) && (v = get(t_header_row, a_font)) != null) ||
            (isTrailerRowFormat(r) &&
             (v = get(t_trailer_row, a_font)) != null) ||
            (isHeaderColFormat(c) && (v = get(t_header_col, a_font)) != null) ||
            (isTrailerColFormat(c) &&
             (v = get(t_trailer_col, a_font)) != null) ||
            ((v = get(r, c, a_font)) != null) ||
            ((v = get(t_body, a_font)) != null))
         {
            return (Font) v;
         }

         return super.getFont(r, c);
      }

      /**
       * Return the per cell foreground color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @return foreground color for the specified cell.
       */
      @Override
      public Color getForeground(int r, int c) {
         Object v;

         if((isHeaderRowFormat(r) &&
             (v = get(t_header_row, a_foreground)) != null) ||
            (isTrailerRowFormat(r) &&
            (v = get(t_trailer_row, a_foreground)) != null) ||
            (isHeaderColFormat(c) &&
            (v = get(t_header_col, a_foreground)) != null) ||
            (isTrailerColFormat(c) &&
            (v = get(t_trailer_col, a_foreground)) != null) ||
            ((v = get(r, c, a_foreground)) != null) ||
            ((v = get(t_body, a_foreground)) != null))
         {
            return (Color) v;
         }

         return super.getForeground(r, c);
      }

      /**
       * Return the per cell background color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @return background color for the specified cell.
       */
      @Override
      public Color getBackground(int r, int c) {
         Object v;

         if((isHeaderRowFormat(r) &&
             (v = get(t_header_row, a_background)) != null) ||
            (isTrailerRowFormat(r) &&
             (v = get(t_trailer_row, a_background)) != null) ||
            (isHeaderColFormat(c) &&
             (v = get(t_header_col, a_background)) != null) ||
            (isTrailerColFormat(c) &&
             (v = get(t_trailer_col, a_background)) != null) ||
            ((v = get(r, c, a_background)) != null) ||
            ((v = get(t_body, a_background)) != null))
         {
            return (Color) v;
         }

         return super.getBackground(r, c);
      }

      /**
       * Return the per cell background color. Return null to use default
       * color.
       * @param r row number.
       * @param c column number.
       * @param spanRow row index of the specified span
       * @return background color for the specified cell.
       */
      @Override
      public Color getBackground(int r, int c, int spanRow) {
         Object v;

         if((isHeaderRowFormat(r) &&
            (v = get(t_header_row, a_background)) != null) ||
            (isTrailerRowFormat(r) &&
               (v = get(t_trailer_row, a_background)) != null) ||
            (isHeaderColFormat(c) &&
               (v = get(t_header_col, a_background)) != null) ||
            (isTrailerColFormat(c) &&
               (v = get(t_trailer_col, a_background)) != null) ||
            ((v = get(spanRow > 0 ? spanRow : r, c, a_background)) != null) ||
            ((v = get(t_body, a_background)) != null))
         {
            return (Color) v;
         }

         return super.getBackground(r, c);
      }
   }

   /**
    * Return the per cell padding.
    *
    * @param r row number.
    * @param c column number.
    * @return padding for the specified cell.
    */
   @Override
   public Insets getInsets(int r, int c) {
      Object v;

      if((isHeaderRowFormat(r) &&
         (v = get(t_header_row, a_padding)) != null) ||
         (isTrailerRowFormat(r) &&
            (v = get(t_trailer_row, a_padding)) != null) ||
         (isHeaderColFormat(c) &&
            (v = get(t_header_col, a_padding)) != null) ||
         (isTrailerColFormat(c) &&
            (v = get(t_trailer_col, a_padding)) != null) ||
         ((v = get(r, c, a_padding)) != null) ||
         ((v = get(t_body, a_padding)) != null))
      {
         return (Insets) v;
      }

      return super.getInsets(r, c);
   }

   @Override
   public int getRowHeight(int r) {
      int baseRowHeight = super.getRowHeight(r);
      boolean vsLens = false;

      if(this instanceof CSSTableStyle) {
         CSSParameter sheetParam = ((CSSTableStyle) this).getSheetParam();
         vsLens = sheetParam != null && sheetParam.getCSSType().equals(CSSConstants.VIEWSHEET);
      }

      // only get the css height if there is no base height specified
      // otherwise there would be no way to override the css height in reports
      if(baseRowHeight == -1 || vsLens) {
         Object v;

         if((isHeaderRowFormat(r) &&
            (v = get(t_header_row, a_height)) != null) ||
            (isTrailerRowFormat(r) &&
               (v = get(t_trailer_row, a_height)) != null) ||
            ((v = get(r, -1, a_height)) != null) ||
            ((v = get(t_body, a_height)) != null))
         {
            return (int) v;
         }
      }

      return baseRowHeight;
   }

   /**
    * Make a copy of this table style.
    */
   @Override
   public XTableStyle clone() {
      TableLens tbl = getTable();

      if(tbl instanceof AbstractTableLens) {
         tbl = ((AbstractTableLens) tbl).clone();
      }

      XTableStyle nx = new XTableStyle(tbl);

      nx.id = id;
      nx.name = name;
      nx.created = created;
      nx.modified = modified;
      nx.createdBy = createdBy;
      nx.modifiedBy = modifiedBy;

      for(int i = 0; i < elemlist.length; i++) {
         for(int j = 0; j < attrlist.length; j++) {
            nx.attrmap[i][j] = cloneAttribute(attrmap[i][j]);
         }
      }

      for(int i = 0; i < speclist.size(); i++) {
         nx.speclist.add(getSpecification(i).clone(nx));
      }

      nx.headerRF = headerRF;
      nx.headerCF = headerCF;
      nx.tailRF = tailRF;
      nx.tailCF = tailCF;
      nx.widthF = widthF;
      nx.heightF = heightF;
      nx.rowBorderCF = rowBorderCF;
      nx.colBorderCF = colBorderCF;
      nx.rowBorderF = rowBorderF;
      nx.colBorderF = colBorderF;
      nx.insetsF = insetsF;
      nx.spanF = spanF;
      nx.alignF = alignF;
      nx.fontF = fontF;
      nx.wrapF = wrapF;
      nx.foregroundF = foregroundF;
      nx.backgroundF = backgroundF;
      nx.presenterF = presenterF;
      nx.firstRow = firstRow;
      nx.firstCol = firstCol;
      nx.lastRow = lastRow;
      nx.lastCol = lastCol;

      return nx;
   }

   /**
    * Deep clone attribute object.
    */
   Object cloneAttribute(Object obj) {
      if(obj instanceof Integer) {
         return obj;
      }
      else if(obj instanceof Color) {
         return new Color(((Color) obj).getRGB(), true);
      }
      else if(obj instanceof Font) {
         return new StyleFont((Font) obj);
      }
      else {
         return obj;
      }
   }

   static final int t_top_border = 0;
   static final int t_bottom_border = 1;
   static final int t_left_border = 2;
   static final int t_right_border = 3;
   static final int t_header_row = 4;
   static final int t_header_col = 5;
   static final int t_trailer_row = 6;
   static final int t_trailer_col = 7;
   static final int t_body = 8;
   static final int a_border = 0;
   static final int a_color = 1;
   static final int a_bcolor = 2;
   static final int a_foreground = 3;
   static final int a_background = 4;
   static final int a_font = 5;
   static final int a_alignment = 6;
   static final int a_row_border = 7;
   static final int a_rcolor = 8;
   static final int a_col_border = 9;
   static final int a_ccolor = 10;
   static final int a_height = 11;
   static final int a_padding = 12;
   static String[] elemlist = {"top-border", "bottom-border", "left-border",
                               "right-border", "header-row", "header-col",
                               "trailer-row", "trailer-col", "body"};
   static String[] attrlist = {"border", "color", "bcolor", "foreground",
                               "background", "font", "alignment", "row-border",
                               "rcolor", "col-border", "ccolor", "height", "padding"};

   /**
    * Table row/column specification.
    */
   public class Specification implements Serializable, Cloneable {
      /**
       * Specification for regular rows and columns.
       */
      public static final int REGULAR = 0;
      public static final int ROW_GROUP_TOTAL = 1;
      public static final int COL_GROUP_TOTAL = 2;

      public Specification() {
      }

      /**
       * Check if a cell is in the range.
       */
      public boolean match(int r, int c) {
         switch(type) {
         case ROW_GROUP_TOTAL:
               return matchRowGroup(r, c);
         case COL_GROUP_TOTAL:
               return matchColGroup(r, c);
         default:
            return matchRegular(r, c);
         }
      }

      /**
       * Match group header/footer.
       */
      public boolean matchRowGroup(int r, int c) {
         if(crosstab == null || r < 0 || c < 0) {
            return false;
         }

         int row = TableTool.getBaseRowIndex(table, crosstab, r);
         int col = TableTool.getBaseColIndex(table, crosstab, c);

         // if current speficication level is 1, the level can only get its total for row header
         // length. if level is large than row header count, it will not find its total cells.
         if(col < index || index >= crosstab.getRowHeaderCount()) {
            return false;
         }

         // if current row and current level is total cell. Then the whole row should paint total.
         if(!crosstab.isTotalCell(row, index)) {
            return false;
         }

         return true;
      }

      public boolean matchColGroup(int r, int c) {
         if(crosstab == null || r < 0 || c < 0) {
            return false;
         }

         int row = TableTool.getBaseRowIndex(table, crosstab, r);
         int col = TableTool.getBaseColIndex(table, crosstab, c);

         if(row < index || index >= crosstab.getColHeaderCount()) {
            return false;
         }

         if(!crosstab.isTotalCell(index, col)) {
            return false;
         }

         return true;
      }

      /**
       * Match regular row/column.
       */
      private boolean matchRegular(int r, int c) {
         int rc = isrow ? (r - getHeaderRowCount() + 1) : (c - getHeaderColCount() + 1);
         // -1 is last row or column
         int i2 = index;

         if(i2 < 0) {
            moreRows(Integer.MAX_VALUE);
            i2 += isrow ? getRowCount() : getColCount();
         }

         // @by larryl, if the pattern is for 1st row and repeat, we force
         // it to be 2 and adjust the rc to match. Otherwise all rows match
         if(i2 == 1 && repeat) {
            i2 = 2;
            rc++;
         }

         // make row/column
         if((repeat && rc > 0 && i2 > 0) ? (rc % i2) == 0 : rc == i2) {
            // change range
            if(range != null) {
               rc = isrow ? c : r;
               int start = range[0];
               int end = range[1];

               if(!isrow && (start < 0 || end < 0)) {
                  moreRows(Integer.MAX_VALUE);
               }

               // convert to last row/column
               if(start < 0) {
                  start += isrow ? getColCount() : getRowCount();
               }

               if(end < 0) {
                  end += isrow ? getColCount() : getRowCount();
               }

               return (rc >= start && rc <= end);
            }

            return true;
         }

         return false;
      }

      /**
       * Get the index, this is either a row or column number.
       */
      public int getIndex() {
         return index;
      }

      /**
       * Set the row or column index.
       */
      public void setIndex(int idx) {
         this.index = idx;
      }

      /**
       * Get the specification type.
       */
      public int getType() {
         return type;
      }

      /**
       * Set the specification type.
       */
      public void setType(int type) {
         this.type = type;
      }

      /**
       * Check if the index is a row or column.
       */
      public boolean isRow() {
         return isrow;
      }

      /**
       * Set the index type to row if true, column if false.
       */
      public void setRow(boolean isrow) {
         this.isrow = isrow;
      }

      /**
       * Check if the row/column spec should be repeated.
       */
      public boolean isRepeat() {
         return repeat;
      }

      /**
       * Set the repeat option.
       */
      public void setRepeat(boolean repeat) {
         this.repeat = repeat;
      }

      /**
       * Get the range to apply (columns or rows).
       */
      public int[] getRange() {
         return range;
      }

      /**
       * Set the range to apply.
       */
      public void setRange(int[] range) {
         this.range = range;
      }

      /**
       * Get the specified attribute.
       */
      public Object get(int a_idx) {
         return attr[a_idx];
      }

      /**
       * Get the specified attribute.
       */
      public Object get(String astr) {
         int i = findIndex(astr, attrlist);

         if(i < 0) {
            throw new RuntimeException("Unknown attribute: " + astr);
         }

         return get(i);
      }

      /**
       * Set an attribute.
       */
      public void put(int a_idx, Object val) {
         attr[a_idx] = val;
      }

      /**
       * Set an attribute.
       */
      public void put(String astr, Object val) {
         int i = findIndex(astr, attrlist);

         if(i >= 0) {
            put(i, val);
         }
         else {
            throw new RuntimeException("Unknown attribute: " + astr);
         }
      }

      public String toString() {
         switch(type) {
         case ROW_GROUP_TOTAL:
            return "Row Group Total " + index;
         case COL_GROUP_TOTAL:
            return "Column Group Total " + index;
         }

         String suffix = null;

         switch(index) {
         case 1:
            suffix = "st";
            break;
         case 2:
            suffix = "nd";
            break;
         case 3:
            suffix = "rd";
            break;
         default:
            suffix = "th";
            break;
         }

         return index + suffix + (repeat ? "*" : "") +
            (isrow ? " " + Catalog.getCatalog().getString("Row") :
                     " " + Catalog.getCatalog().getString("Column")) +
            ((range != null) ? (" [" + range[0] + ":" + range[1] + "]") : "");
      }

      public Specification clone(XTableStyle style) {
         try {
            Specification spec = style.new Specification();

            spec.index = index;
            spec.isrow = isrow;
            spec.repeat = repeat;
            spec.type = type;

            if(range != null) {
               spec.range = (int[]) range.clone();
            }

            spec.attr = new Object[attr.length];
            for(int i = 0; i < attr.length; i++) {
               spec.attr[i] = style.cloneAttribute(attr[i]);
            }

            return spec;
         }
         catch(Exception e) {
            LOG.error("Failed to copy table style", e);
         }

         return null;
      }

      private Specification getMatcher() {
         if(runtimeSpec == null) {
            if(type == REGULAR && isrow && repeat && range == null &&
               (index == 1 || index == 2))
            {
               runtimeSpec = new Specification2(this);
            }
            else {
               runtimeSpec = this;
            }
         }

         return runtimeSpec;
      }

      private int index; // starting row index or group level
      private int type = REGULAR; // regular row/column or group header/footer
      private boolean isrow = true; // true for row and false for column
      private boolean repeat = true; // only applicable to regular row/colum
      private int[] range = null; // [start, end]
      private Object[] attr = new Object[attrlist.length];
      private Specification runtimeSpec;
   }

   // optimization, for the most common alternate row pattern
   private class Specification2 extends Specification {
      public Specification2(Specification spec) {
         headers = (short) getHeaderRowCount();
         increment = (short) (-headers + 1);

         if(spec.index == 1) {
            increment += 1;
         }
      }

      @Override
      public boolean match(int r, int c) {
         return r >= headers && (r + increment) % 2 == 0;
      }

      private short headers = 0;
      private short increment = 0;
   }

   private Style style;
   private CrossTabFilter crosstab = null;
   private String name = "XTableStyle";
   private String id = "XTableStyle";
   private long created;
   private long modified;
   private String createdBy;
   private String modifiedBy;
   // matrix to store attributes [elem][attr]
   private Object[][] attrmap = new Object[elemlist.length][attrlist.length];
   private List<Specification> speclist = new ArrayList<>(); // Spec
   private Integer headerRows, headerCols;

   private static final Logger LOG =
      LoggerFactory.getLogger(XTableStyle.class);
}

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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.filter.*;
import inetsoft.report.internal.binding.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.*;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.table.XSwappableTable;
import inetsoft.uql.util.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.*;
import inetsoft.web.composer.model.BrowseDataModel;
import org.jnumbers.NumberParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Utility methods operating on the inetsoft.report packages.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Util implements inetsoft.report.StyleConstants {
   /**
    * Default font for report.
    */
   public static final Font DEFAULT_FONT = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, 0, 10);

   /**
    * Field distribution option.
    */
   public static final int H_SPACING = 0x100;
   /**
    * Field distribution option.
    */
   public static final int V_SPACING = 0x200;

   /**
    * Break a line.
    * @param line line text.
    * @param w width to fit.
    * @param font text font.
    * @param space white space.
    * @return index of the last character on the line (for substring(0, n)).
    * return -1 if the line does not need breaking.
    */
   public static int breakLine(String line, double w, Font font, boolean space) {
      FontMetrics fm = Common.getFractionalFontMetrics(font);
      int len = line.length();
      int hi = len - 1;

      // @by stephenwebster, For Bug #29559
      // It seems preferable to consider the case where the line actually fits
      // prior to the length check to avoid unnecessary processing.
      if(Common.stringWidth(line, 0, len, len, font, fm) <= w) {
         return -1;
      }
      // if it's a long line, try to find an end point that is significantly
      // shorter than the entire line to start binary search
      else if(len > 150) {
         float sw = 0;

         for(int i = 0, ei = 100; i < len; i = ei, ei += 50) {
            ei = Math.min(ei, len);
            sw += Common.stringWidth(line, i, ei, len, font, fm);

            if(sw > w) {
               hi = ei - 1;
               break;
            }
         }

         if(sw < w) {
            return -1;
         }
      }

      int mid = 0;
      int xW = fm.charWidth('X') + 1;
      double sw = w;

      for(int lo = 0; hi > lo;) {
         // in case of very long string
         mid = Math.min((lo + hi) / 2, lo + (int) (sw / xW + 1));

         // reached middle
         if(mid == lo || mid == hi) {
            mid = hi;
            break;
         }

         float w0 = Common.stringWidth(line, lo, mid + 1, len, font, fm);

         if(w0 > sw) {
            hi = mid;
         }
         // more space, try fit more characters
         else {
            lo = mid;
            sw -= w0;
         }
      }

      mid = Math.min(len, mid); // mid is exclusive
      boolean appended = false;

      while(Common.stringWidth(line, 0, mid, len, font, fm) < w) {
         mid++;
         appended = true;
      }

      if(appended && Common.stringWidth(line, 0, mid, len, font, fm) > w) {
         mid--;
      }

      // break at white space
      if(space) {
         boolean internal = (mid < len) && (mid >= 0);
         char mchar = internal ? line.charAt(mid) : ' ';

         if(internal && (mchar == ' ' || isCJKCharacter(mchar))) {
            return mid;
         }

         int leading = 0; // leading space

         while(leading < len && Character.isWhitespace(line.charAt(leading))) {
            leading++;
         }

         for(int i = mid - 1; i > leading; i--) {
            char c = line.charAt(i);

            if(Character.isWhitespace(c) || isCJKCharacter(c)) {
               return i;
            }
         }

         // @by mikec, if the character is greater than 255, it is mostly
         // an multiple byte character which in most case is a cjk character.
         // These kind of character support break in between each character.
         // If the next char is a punctuation mark, force a char to the next
         // line so it doesn't appear as the first char.
         // @by humming, greater than 255 is not a valid check cjk logic
         // see bug1285164778586
         // only check pre word is the cjk more reasonable
         if(mid - 1 > leading && isCJKCharacter(line.charAt(mid - 1))) {
            return (internal && isBreakSymbol(mchar)) ? mid - 1 : mid;
         }
      }

      return mid;
   }

   /**
    * Check whether a report must wait for the entire report to be processed.
    * For example, if a footer element in the report requires page number,
    * which looks like "{n}", the first page will only be ready until the whole
    * paging process is over.
    */
   public static boolean isBatchWaiting(ReportSheet report) {
      // @by billh, fix customer bug: bug1292510864138
      // support composite report and sub_report in page header/footer
      if(report instanceof CompositeSheet) {
         CompositeSheet creport = (CompositeSheet) report;
         int cnt = creport.getReportCount();
         boolean batch = false;

         for(int i = 0; i < cnt; i++) {
            ReportSheet rpt = creport.getReport(i);

            if(isBatchWaiting(rpt)) {
               batch = true;
               break;
            }
         }

         return batch;
      }

      Enumeration elems = ElementIterator.elements(report);
      Pattern pattern = Pattern.compile("\\{(n|N)\\}");

      while(elems.hasMoreElements()) {
         Object elem = elems.nextElement();

         if(!(elem instanceof TextBased)) {
            continue;
         }

         TextBased text = (TextBased) elem;
         TextLens lens = text.getTextLens();

         if(lens == null) {
            continue;
         }

         String content = lens.getText();

         if(content == null) {
            continue;
         }

         if(pattern.matcher(content).find()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the character is a break symbol.
    */
   public static boolean isBreakSymbol(char c) {
      return c == '.' || c == ',' || c == '\u3002';
   }

   /**
    * Merge two line styles.
    * @param s1 line style 1.
    * @param s2 line style 2.
    * @return merged line style.
    */
   public static int mergeLineStyle(int s1, int s2) {
      if(s1 == 0 || s2 == 0) {
         return s1 == 0 ? s2 : s1;
      }

      // double line does not merge well with other styles
      if((s1 & DOUBLE_MASK) != 0 || (s2 & DOUBLE_MASK) != 0) {
         int w1 = s1 & WIDTH_MASK;
         int w2 = s2 & WIDTH_MASK;

         // by taking the style with larger width, we give double line higher
         // priority than the 3D style lines
         if(w2 > w1) {
            return s2;
         }

         return s1;
      }

      int style = Math.max(s1 & WIDTH_MASK, s2 & WIDTH_MASK) |
         Math.max(s1 & FRACTION_WIDTH_MASK, s2 & FRACTION_WIDTH_MASK) |
         Math.min(s1 & DASH_MASK, s2 & DASH_MASK) |
         Math.max(s1 & SOLID_MASK, s2 & SOLID_MASK) |
         ((s1 | s2) & DOUBLE_MASK) | ((s1 | s2) & RAISED_MASK) |
         ((s1 | s2) & LOWERED_MASK);

      // @by larryl, if regular width is set, clear the fractional width
      if((style & FRACTION_WIDTH_MASK) != 0 && (style & WIDTH_MASK) != 0) {
         style = style & ~FRACTION_WIDTH_MASK;
      }

      return style;
   }

   /**
    * Merge two colors.
    */
   public static Color mergeColor(Color c1, Color c2) {
      if(c1 == null) {
         return c2;
      }
      else if(c2 == null) {
         return c1;
      }

      int rgb1 = c1.getRGB();
      int rgb2 = c2.getRGB();

      return new Color(Math.max(rgb1, rgb2));
   }

   /**
    * Find the specified element in the section.
    */
   public static ReportElement getElement(SectionElement elem, String id) {
      return getElement(elem.getSection(), id);
   }

   /**
    * Find an element in a section.
    */
   public static ReportElement getElement(SectionLens section, String id) {
      if(section == null) {
         return null;
      }

      ReportElement elem = null;
      SectionBand[] content = section.getSectionContent();

      elem = getSectionElement(content, id);

      if(elem != null) {
         return elem;
      }

      elem = getSectionElement(section.getSectionHeader(), id);

      if(elem != null) {
         return elem;
      }

      elem = getSectionElement(section.getSectionFooter(), id);
      return elem;
   }

   /**
    * Find an element in band or bands.
    */
   private static ReportElement getSectionElement(SectionBand[] bands, String id) {
      for(int i = 0; i < bands.length; i++) {
         ReportElement elem = bands[i].getElement(id);

         if(elem != null) {
            return elem;
         }

         // find the element in editRegionElements which are embedded in
         // this SectionElement.
         for(int j = 0; j < bands[i].getElementCount(); j++) {
            elem = bands[i].getElement(j);

            if(elem instanceof SectionElement) {
               ReportElement rc = getElement((SectionElement) elem, id);

               if(rc != null) {
                  return rc;
               }
            }
         }
      }

      return null;
   }

   public static String tableToString(XTable table) {
      return tableToString(table, Integer.MAX_VALUE);
   }

   /**
    * Print a table lens. For debugging only.
    */
   public static void printTable(XTable table) {
      printTable(table, Integer.MAX_VALUE);
   }

   public static String tableToString(XTable table, int max) {
      return tableToString(table, max, false);
   }

   /**
    * Print a table lens. For debugging only.
    */
   public static void printTable(XTable table, int max) {
      printTable(table, max, false);
   }

   /**
    * Print a table lens. For debugging only.
    */
   public static void printTable(XTable table, int max, boolean ocls) {
      printTable(table, max, ocls, System.err);
   }

   public static String tableToString(XTable table, int max, boolean ocls) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try(PrintStream stream = new PrintStream(buffer, false, Charset.defaultCharset().name())) {
         printTable(table, max, ocls, stream);
         stream.flush();
      }
      catch(Exception ignore) {
      }

      return new String(buffer.toByteArray(), Charset.defaultCharset());
   }

   /**
    * Print a table lens. For debugging only.
    */
   public static void printTable(XTable table, int max, boolean ocls, PrintStream out) {
      if(table == null) {
         out.println("null table");
         return;
      }

      for(int i = 0; i < max && table.moreRows(i); i++) {
         String lnum = i + "             ";
         out.print(lnum.substring(0, 8) + ": ");

         for(int j = 0; j < table.getColCount(); j++) {
            Object val = table.getObject(i, j);
            out.print("[" + j + "]");

            if(val == null) {
               val = "[null]";
            }

            if(i == 0) {
               String id = table.getColumnIdentifier(j);

               if(id != null && id.length() > 0 && !id.equals(val)) {
                  val = val + "{" + id + "}";
               }

               String header = getHeader(table, j).toString();
               Class<?> type = table.getColType(j);
               TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL,
                  Util.getDataType(type), new String[] {header});
               XMetaInfo info = table.getDescriptor().getXMetaInfo(path);

               if(info != null && !info.isEmpty()) {
                  val = val + "{" + info + "}";
               }
            }

            if(ocls && !(val instanceof String)) {
               val = val + "{" + (val == null ? "" : val.getClass().getName()) + "}";
            }

            out.print(val + "|");
         }

         out.println("^");
      }
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public static int getAppliedMaxRows(XTable table) {
      if(table instanceof XSwappableTable) {
         return ((XSwappableTable) table).getAppliedMaxRows();
      }
      else if(table instanceof MaxRowsTableLens) {
         return ((MaxRowsTableLens) table).getAppliedMaxRows();
      }
      else if(table instanceof XNodeTableLens) {
         return ((XNodeTableLens) table).getAppliedMaxRows();
      }
      else if(table instanceof RuntimeCalcTableLens) {
         return getAppliedMaxRows(
            ((RuntimeCalcTableLens) table).getCalcTableLens());
      }
      else if(table instanceof CalcTableLens) {
         return ((CalcTableLens) table).getAppliedMaxRows();
      }
      else if(table instanceof TableFilter) {
         XTable base = ((TableFilter) table).getTable();
         return getAppliedMaxRows(base);
      }
      else if(table instanceof BinaryTableFilter) {
         XTable lbase = ((BinaryTableFilter) table).getLeftTable();
         int lmax = getAppliedMaxRows(lbase);

         if(lmax > 0) {
            return lmax;
         }

         XTable rbase = ((BinaryTableFilter) table).getRightTable();
         return getAppliedMaxRows(rbase);
      }
      else if(table instanceof RowLimitableTable) {
         return ((RowLimitableTable) table).getAppliedMaxRows();
      }

      return 0;
   }

   public static HashMap getHintBasedMaxRow(XTable table) {
      if(table instanceof RuntimeCalcTableLens) {
         CalcTableLens calcTableLens = ((RuntimeCalcTableLens) table).getCalcTableLens();

         if(calcTableLens != null) {
            return getHintBasedMaxRow(calcTableLens.getScriptTable());
         }
         else {
            getHintBasedMaxRow(((RuntimeCalcTableLens) table).getTable());
         }
      }
      else if(table instanceof TableFilter) {
         XTable base = ((TableFilter) table).getTable();
         return getHintBasedMaxRow(base);
      }
      else if(table instanceof XNodeTableLens) {
         return ((XNodeTableLens) table).getMaxRowHintMap();
      }
      else if(table instanceof RealtimeTableMetaData.ColumnsTable) {
         return getHintBasedMaxRow(((RealtimeTableMetaData.ColumnsTable) table).getTable());
      }

      return null;
   }

   /**
    * Get the applied max rows.
    * @return the applied max rows.
    */
   public static boolean isTimeoutTable(XTable table) {
      if(table instanceof XSwappableTable) {
         return ((XSwappableTable) table).isTimeoutTable();
      }
      else if(table instanceof MaxRowsTableLens) {
         return ((MaxRowsTableLens) table).isTimeoutTable();
      }
      else if(table instanceof XNodeTableLens) {
         return ((XNodeTableLens) table).isTimeoutTable();
      }
      else if(table instanceof TableFilter) {
         XTable base = ((TableFilter) table).getTable();
         return isTimeoutTable(base);
      }
      else if(table instanceof BinaryTableFilter) {
         XTable lbase = ((BinaryTableFilter) table).getLeftTable();
         boolean lmax = isTimeoutTable(lbase);

         if(lmax) {
            return lmax;
         }

         XTable rbase = ((BinaryTableFilter) table).getRightTable();
         return isTimeoutTable(rbase);
      }

      return false;
   }

   /**
    * Print the table filters of a table. For debugging only.
    */
   public static void printTableFilters(XTable table) {
      printTableFilters0(table, 0);
   }

   /**
    * Print the table filters of a table. For debugging only.
    */
   public static void printTableFilters(XTable table, int maxRow) {
      printTableFilters0(table, 0, maxRow);
   }

   /**
    * Print the table filters of a table. For debugging only.
    */
   private static void printTableFilters0(XTable table, int level) {
      printTableFilters0(table, level, 2);
   }

   /**
    * Print the table filters of a table. For debugging only.
    */
   private static void printTableFilters0(XTable table, int level, int max) {
      if(table == null) {
         System.err.println("level: " + level + ", null");
         return;
      }

      if(table instanceof TableFilter) {
         XTable base = ((TableFilter) table).getTable();

         printTableFilters0(base, level + 1, max);
      }
      else if(table instanceof BinaryTableFilter) {
         XTable lbase = ((BinaryTableFilter) table).getLeftTable();
         XTable rbase = ((BinaryTableFilter) table).getRightTable();

         printTableFilters0(lbase, level + 1, max);
         printTableFilters0(rbase, level + 1, max);
      }
      else if(table instanceof ConcatTableLens) {
         for(TableLens tbl : ((ConcatTableLens) table).getTables()) {
            printTableFilters0(tbl, level + 1, max);
         }
      }

      int tab = 10 - level;

      for(int i = 0; i < tab; i++) {
         System.err.print(" ");
      }

      TableDataDescriptor desc = table instanceof TableLens ? table.getDescriptor() : null;

      System.err.println("level: " + level + ", " + table +
                         ", rc:" + table.getRowCount() + ", cc:" +
                         table.getColCount() + ", " + "hrc:" +
                         table.getHeaderRowCount() + ", desc: " + desc);
      printTable(table, max, false);
   }

   /**
    * Get crosstab filter.
    * @param table the specified table lens.
    * @reurn the found crosstab filter, null if not found.
    */
   public static CrossTabFilter getCrosstab(XTable table) {
      return (CrossTabFilter) getNestedTable(table, CrossTabFilter.class);
   }

   /**
    * Get crosstab filter.
    * @param table the specified table lens.
    * @reurn the found crosstab filter, null if not found.
    */
   public static CrossFilter getCrossFilter(XTable table) {
      return (CrossFilter) getNestedTable(table, CrossFilter.class);
   }

   /**
    * Get runtime calc table.
    * @param table the specified table lens.
    * @reurn the nested calc table.
    */
   public static TableLens getNestedTable(XTable table, Class cls) {
      while(table != null) {
         if(cls.isAssignableFrom(table.getClass())) {
            return (TableLens) table;
         }

         if(table instanceof TableFilter) {
            table = ((TableFilter) table).getTable();
         }
         else {
            return null;
         }
      }

      return null;
   }

   /**
    * List all nested table.
    */
   public static void listNestedTable(XTable lens, Class cls, java.util.List<XTable> arr) {
      if(lens == null) {
         return;
      }

      if(cls.isAssignableFrom(lens.getClass())) {
         arr.add(lens);
      }

      if(lens instanceof ConcatTableLens) {
         ConcatTableLens clens = (ConcatTableLens) lens;
         TableLens[] tables = clens.getTables();

         if(tables != null) {
            for(TableLens table : tables) {
               listNestedTable(table, cls, arr);
            }
         }
      }
      else if(lens instanceof BinaryTableFilter) {
         BinaryTableFilter blens = (BinaryTableFilter) lens;
         listNestedTable(blens.getLeftTable(), cls, arr);
         listNestedTable(blens.getRightTable(), cls, arr);
      }
      else if(lens instanceof TableFilter) {
         listNestedTable(((TableFilter) lens).getTable(), cls, arr);
      }
   }


   /**
    * Print license key installation instruction.
    */
   public static void showKeyInstruction() {
      String txt = "License key has not been properly installed.\n" +
         "Please follow the instructions to install it:\n\n" +
         "  1. Add the license key using the Enterprise Manager, or\n" +
         "  2. Add a property with the Inetsoft shell (inetsoftShell):\n" +
         "     inetsoft > :setup set-property '/path_to/sree_home' 'license.key' 'S000-000-ERX-000000000000000-000000000000'";
      LOG.error(txt);
   }

   /**
    * Find the column that matches the specified field.
    */
   public static int findColumn(XTable table, DataRef attribute) {
      return findColumn(table, getFieldHeader(attribute,
         false, table instanceof VSCubeTableLens), 0, true);
   }

   /**
    * Find the column that matches the specified column header.
    */
   public static int findColumn(XTable table, Object hdr) {
      return findColumn(table, hdr, 0, true);
   }

   /**
    * Find the column that matches the specified column header.
    */
   public static int findColumn(XTable table, Object hdr, int start, boolean fuzzy) {
      int result = -1;

      if(hdr == null) {
         return -1;
      }

      // @by billh, check if the header matches one column identifier
      for(int i = 0; i < table.getColCount(); i++) {
         String identifier = table.getColumnIdentifier(i);

         if(Tool.equals(identifier, hdr)) {
            return i;
         }
      }

      String tmpHdr = hdr.toString();

      if(tmpHdr != null && tmpHdr.indexOf("::") >= 0) {
         tmpHdr = tmpHdr.substring(tmpHdr.indexOf("::") + 2);
      }

      // @by larryl, check case where header row is hidden, we should check
      // if the column order is changed in filter, but currently the only
      // know use for hidden header row is in section binding, which does
      // not use column mapping. To handle column mapping requires a reverse
      // column index mapping to be built here and used on result
      while(table != null && table.getHeaderRowCount() == 0) {
         if(table instanceof TableFilter) {
            TableLens base = ((TableFilter) table).getTable();

            if(base.getColCount() == table.getColCount()) {
               table = base;
               continue;
            }
         }

         return -1;
      }

      if(tmpHdr != null && table != null && table.moreRows(0)) {
         result = findColumn(table, tmpHdr, start, table.getColCount(), fuzzy);
      }

      return result;
   }

   /**
    * Find the column that matches the specified field.
    */
   public static int findColumn(XTable table, DataRef ref, ColumnIndexMap columnIndexMap) {
      if(table instanceof TextSizeLimitTableLens) {
         table = ((TextSizeLimitTableLens) table).getTable();
      }

      String attribute = getFieldHeader(ref, false, table instanceof VSCubeTableLens);
      return findColumn(columnIndexMap, attribute, true);
   }

   /**
    * Find the column that matches the specified column header.
    */
   public static int findColumn(ColumnIndexMap columnIndexMap, Object hdr) {
      return findColumn(columnIndexMap, hdr, true);
   }

   /**
    * Find the column that matches the specified column header.
    */
   public static int findColumn(ColumnIndexMap columnIndexMap, Object hdr, boolean fuzzy) {
      if(hdr == null || columnIndexMap == null) {
         return -1;
      }

      int idx = columnIndexMap.getColIndexByIdentifier(hdr);

      if(idx >= 0) {
         return idx;
      }

      // some times a Field is passed as hdr and its string form needs to be checked. (49994)
      if(!(hdr instanceof String) && hdr.getClass().isPrimitive()) {
         return columnIndexMap.getColIndexByHeader(hdr);
      }

      String header = hdr.toString();

      if(header != null && header.indexOf("::") >= 0) {
         header = header.substring(header.indexOf("::") + 2);
      }

      idx = columnIndexMap.getColIndexByStrHeader(header);

      if(idx < 0) {
         idx = columnIndexMap.getColIndexByStrHeader(header, true);
      }

      if(idx < 0) {
         idx = columnIndexMap.getColIndexByFormatedHeader(header, true);
      }

      if(idx < 0) {
         // fix bug1074325540383, the header of LayoutTableLens in
         // design mode will append "[" and suffix "]", try to find again
         String header0 = "[" + header + "]";
         idx = columnIndexMap.getColIndexByStrHeader(header0);

         if(idx < 0) {
            idx = columnIndexMap.getColIndexByStrHeader(header0, true);
         }

         if(idx < 0) {
            idx = columnIndexMap.getColIndexByFormatedHeader(header0, true);
         }
      }

      if(idx != -1) {
         return idx;
      }

      if(fuzzy) {
         // @by mikec
         // ?? not sure why the old logic is here,
         // check header with dot and column object without dot?
         Set<Map.Entry<Object, Integer>> entrySet = columnIndexMap.getStrHeaderEntrySet();

         if(entrySet != null) {
            for(Map.Entry<Object, Integer> entry : entrySet) {
               Object key = entry.getKey();

               if(header.endsWith("." + key)) {
                  idx = columnIndexMap.getColIndexByStrHeader(key);
                  break;
               }

               // @by mikec
               // this logic is used to backward compatibility
               // check header without dot and current table name with.
               if(("" + key).endsWith("." + header)) {
                  idx = entry.getValue();
                  break;
               }
            }
         }
      }

      if(fuzzy && idx < 0) {
         Set<Map.Entry<Object, Integer>> entrySet = columnIndexMap.getIdentifier2EntrySet();

         if(entrySet != null) {
            for(Map.Entry<Object, Integer> entry : entrySet) {
               String identifier = entry.getKey() == null ? "" : entry.getKey().toString();

               if(identifier != null && header.startsWith("Column [") &&
                  (header.equals(identifier) || identifier.endsWith("." + header)))
               {
                  idx = entry.getValue();
                  break;
               }
            }
         }
      }

      if(idx != -1) {
         return idx;
      }

      if(header.startsWith("Column [")) {
         try {
            int begin = header.indexOf("Column [");
            String index = header.substring(begin + 8, header.indexOf(']', begin));
            int col = Integer.parseInt(index);
            int stop = -1;

            Set headerKeySet = columnIndexMap.getStrHeaderKeySet();
            stop = headerKeySet != null ? headerKeySet.size() : stop;

            if(stop == -1) {
               Set identifierKeySet = columnIndexMap.getIdentifierKeySet();
               stop = identifierKeySet != null ? identifierKeySet.size() : stop;
            }

            if(col >= 0 && col <= stop && col < columnIndexMap.getTable().getColCount()) {
               idx = col;
            }
         }
         catch(Throwable ignore) {
         }
      }

      return idx;
   }

   /**
    * Find the column that matches the specified column header.
    */
   private static int findColumn(XTable table, Object hdr, int start, int stop, boolean fuzzy) {
      int findex = -1; // fuzzy match index
      int findex2 = -1; // fuzzy match index
      int findex3 = -1; // fuzzy match index
      int findex4 = -1; // fuzzy match index

      for(int i = start; i < stop; i++) {
         Object val = table.getObject(0, i);
         val = (val == null) ? "" : val;

         if(hdr instanceof String) {
            String header = (String) hdr;

            if(header.equals(val.toString())) {
               return i;
            }
            else if(findex < 0 && header.equals(GeoRef.getBaseName(val.toString()))) {
               findex = i;
            }
            else if(findex < 0 && header.equalsIgnoreCase(val.toString())) {
               findex = i;
            }
            else if(findex < 0 && header.equalsIgnoreCase(Tool.toString(val))) {
               findex = i;
            }

            if(findex < 0) {
               // fix bug1074325540383, the header of LayoutTableLens in
               // design mode will append "[" and suffix "]", try to find again
               String header0 = "[" + header + "]";

               if(header0.equals(val.toString())) {
                  return i;
               }
               else if(findex < 0 && header0.equalsIgnoreCase(val.toString())) {
                  findex = i;
               }
               else if(findex < 0 && header0.equalsIgnoreCase(Tool.toString(val))) {
                  findex = i;
               }
            }

            if(fuzzy && findex < 0 && findex2 < 0) {
               // @by mikec
               // ?? not sure why the old logic is here,
               // check header with dot and column object without dot?
               if(header.endsWith("." + val)) {
                  findex2 = i;
               }

               // @by mikec
               // this logic is used to backward compatibility
               // check header without dot and current table name with.
               if(findex2 < 0 && ("" + val).endsWith("." + header)) {
                  findex2 = i;
               }
            }

            if(fuzzy && findex < 0 && findex2 < 0 && findex3 < 0) {
               String identifier = table.getColumnIdentifier(i);

               if(identifier != null && header.startsWith("Column [") &&
                  (header.equals(identifier) || identifier.endsWith("." + header)))
               {
                  findex3 = i;
               }
            }

            if(fuzzy && findex < 0 && findex2 < 0 && findex3 < 0 && findex4 < 0 &&
               header.startsWith("Column ["))
            {
               try {
                  int begin = header.indexOf("Column [");
                  String index = header.substring(begin + 8, header.indexOf(']', begin));
                  int col = Integer.parseInt(index);

                  if(col >= start && col < stop) {
                     findex4 = col;
                  }
               }
               catch(Throwable ex) {
               }
            }
         }
         else if(Tool.equals(hdr, val)) {
            return i;
         }
      }

      if(findex != -1) {
         return findex;
      }

      if(findex2 != -1) {
         return findex2;
      }

      if(findex3 != -1) {
         return findex3;
      }

      if(findex4 != -1) {
         return findex4;
      }

      return -1;
   }

   public static int findColumn(ColumnIndexMap columnIndexMap, ColumnInfo colInfo, TableLens lens) {
      if(columnIndexMap == null || colInfo == null || lens == null) {
         return -1;
      }

      int col = Util.findColumn(columnIndexMap, colInfo.getColumnRef());

      if(col < 0 && lens instanceof SubTableLens) {
         TableLens table = ((SubTableLens) lens).getTable();

         if(table instanceof FormatTableLens2) {
            Map<TableDataPath, TableFormat> formatMap = ((FormatTableLens2) table).getFormatMap();
            String header = colInfo.getHeader();
            TableFormat tableFormat = formatMap.get(new TableDataPath(header));

            if(tableFormat != null) {
               Format format = tableFormat.getFormat(Catalog.getCatalog().getLocale());
               col = Util.findColumn(columnIndexMap, format.format(header));
            }
         }
      }

      return col;
   }

   /**
    * Create a formula from the formula spec. The formula is either a
    * formula name defined in this class, or a name with parameter
    * value included in parenthesis.
    */
   public static Formula createFormula(XTable table, String formula) {
      if("Aggregate".equals(formula)) {
         return null;
      }

      String cls = formula;
      int idx = cls.indexOf('(');

      // secondary field
      if(idx > 0) {
         int eidx = cls.lastIndexOf(')');
         cls = cls.substring(0, idx) + cls.substring(eidx + 1);
      }

      // percentage type
      int perIdx = cls.indexOf('<');

      if(perIdx > 0) {
         cls = cls.substring(0, perIdx);
      }

      if(cls.indexOf('.') > 0) {
         // fully qualified name
      }
      else if(cls.endsWith("Formula")) {
         if(cls.startsWith("Sum")) {
            cls = "inetsoft.mv.formula." + cls;
         }
         else {
            cls = "inetsoft.mv.trans." + cls;
         }
      }
      else {
         cls = "inetsoft.report.filter." + cls + "Formula";
      }

      return createFormula(table, formula, cls);
   }

   /**
    * Create a formula from the formula spec. The formula is either a
    * formula name defined in this class, or a name with parameter
    * value included in parenthesis.
    */
   private static Formula createFormula(XTable table, String formula, String scls) {
      if(formula == null || formula.startsWith(SummaryAttr.NONE_FORMULA)) {
         return null;
      }

      Formula instance = null;
      String oFormula = formula;

      try {
         Class cls = Class.forName(scls);
         int idx = formula.indexOf('(');
         String pstr = null;

         // secondary field
         if(idx > 0) {
            int eidx = formula.lastIndexOf(')');
            pstr = formula.substring(idx + 1, eidx);
            formula = formula.substring(0, idx) + formula.substring(eidx + 1);
         }

         // percentage type
         int perIdx = formula.indexOf('<');

         if(perIdx > 0) {
            formula = formula.substring(0, perIdx);
         }

         // if has parameter, find constructor
         if(pstr != null) {
            int col = -1;

            if(table != null) {
               col = findColumn(table, pstr);
            }

            if(col < 0) {
               try {
                  col = Integer.parseInt(pstr);
               }
               catch(Exception e) {
               }
            }

            if(col >= 0) {
               Class[] params = {int.class};
               Constructor method = cls.getConstructor(params);

               instance = (Formula) method.newInstance(new Object[] { col });
            }
            else if(table == null) {
               throw new RuntimeException(
                  "Table required for formulas with base column: " + pstr);
            }
         }

         if(instance == null) {
            instance = (Formula) cls.newInstance();
         }

         // check percentage
         if(instance instanceof PercentageFormula) {
            perIdx = oFormula.indexOf('<');

            if(perIdx > 0) {
               int perEIdx = oFormula.lastIndexOf('>');
               String per = oFormula.substring(perIdx + 1, perEIdx);

               try {
                  int perInt = Integer.parseInt(per);
                  ((PercentageFormula) instance).setPercentageType(perInt);
               }
               catch(NumberFormatException inte) {
               }
            }
         }

         return instance;
      }
      catch(Exception e) {
         LOG.error("Failed to create formula", e);
      }

      return new SumFormula();
   }

   /**
    * Get an xtable header at a col index. If the header is not available,
    * "Column [col]" like "Column[3]" will be returned.
    * @param table the specified xtable
    * @param c the specified col
    * @return xtable header at the specified col index
    */
   public static Object getHeader(XTable table, int c) {
      return XUtil.getHeader(table, c);
   }

   /**
    * Get data type of a class.
    * @param c the specified class
    * @return the data type defined in <tt>XSchema</tt>
    */
   public static String getDataType(Class c) {
      return Tool.getDataType(c);
   }

   /**
    * Get data type of a col index in a table lens.
    * @param table the specified table lens
    * @param col the specified col index
    * @return the data type defined in <tt>XSchema</tt>
    */
   public static String getDataType(TableLens table, int col) {
      if(col == -1) {
         return XSchema.STRING;
      }
      else {
         Class c = (table == null) ? (new String("")).getClass() : table.getColType(col);
         return getDataType(c);
      }
   }

   /**
    * Get the style constants value its String representation. For example
    * getStyleConstantsFromString("THIN_LINE") returns the constant
    * StyleConstants.THIN_LINE.
    * @param str String representation of a Style Constant
    */
   public static int getStyleConstantsFromString(String str) {
      if(str.equals("H_LEFT")) {
         return StyleConstants.H_LEFT;
      }

      if(str.equals("H_CENTER")) {
         return StyleConstants.H_CENTER;
      }

      if(str.equals("H_RIGHT")) {
         return StyleConstants.H_RIGHT;
      }

      if(str.equals("V_BASELINE")) {
         return StyleConstants.V_BASELINE;
      }

      if(str.equals("V_TOP")) {
         return StyleConstants.V_TOP;
      }

      if(str.equals("V_CENTER")) {
         return StyleConstants.V_CENTER;
      }

      if(str.equals("V_BOTTOM")) {
         return StyleConstants.V_BOTTOM;
      }

      if(str.equals("V_SPACING")) {
         return V_SPACING;
      }

      if(str.equals("H_SPACING")) {
         return H_SPACING;
      }

      if(str.equals("THIN_LINE")) {
         return StyleConstants.THIN_LINE;
      }

      if(str.equals("THIN_THIN_LINE")) {
         return StyleConstants.THIN_THIN_LINE;
      }

      if(str.equals("DOUBLE_LINE")) {
         return StyleConstants.DOUBLE_LINE;
      }

      if(str.equals("MEDIUM_LINE")) {
         return StyleConstants.MEDIUM_LINE;
      }

      if(str.equals("THICK_LINE")) {
         return StyleConstants.THICK_LINE;
      }

      if(str.equals("RAISED_3D")) {
         return StyleConstants.RAISED_3D;
      }

      if(str.equals("LOWERED_3D")) {
         return StyleConstants.LOWERED_3D;
      }

      if(str.equals("DOUBLE_3D_RAISED")) {
         return StyleConstants.DOUBLE_3D_RAISED;
      }

      if(str.equals("DOUBLE_3D_LOWERED")) {
         return StyleConstants.DOUBLE_3D_LOWERED;
      }

      if(str.equals("DOT_LINE")) {
         return StyleConstants.DOT_LINE;
      }

      if(str.equals("DASH_LINE")) {
         return StyleConstants.DASH_LINE;
      }

      if(str.equals("MEDIUM_DASH")) {
         return StyleConstants.MEDIUM_DASH;
      }

      if(str.equals("LARGE_DASH")) {
         return StyleConstants.LARGE_DASH;
      }

      if(str.equals("ULTRA_THIN_LINE")) {
         return StyleConstants.ULTRA_THIN_LINE;
      }

      if(str.equals("NONE")) {
         return 0;
      }

      if(str.equals("TABLE_FIT_PAGE")) {
         return ReportSheet.TABLE_FIT_PAGE;
      }

      if(str.equals("TABLE_FIT_CONTENT")) {
         return ReportSheet.TABLE_FIT_CONTENT;
      }

      if(str.equals("TABLE_FIT_CONTENT_PAGE")) {
         return ReportSheet.TABLE_FIT_CONTENT_PAGE;
      }

      if(str.equals("TABLE_EQUAL_WIDTH")) {
         return ReportSheet.TABLE_EQUAL_WIDTH;
      }

      if(str.equals("TABLE_FIT_CONTENT_1PP")) {
         return ReportSheet.TABLE_FIT_CONTENT_1PP;
      }

      if(str.equals("CHART_BAR")) {
         return StyleConstants.CHART_BAR;
      }

      if(str.equals("CHART_PIE")) {
         return StyleConstants.CHART_PIE;
      }

      if(str.equals("CHART_DONUT")) {
         return StyleConstants.CHART_DONUT;
      }

      if(str.equals("CHART_SUNBURST")) {
         return StyleConstants.CHART_SUNBURST;
      }

      if(str.equals("CHART_TREEMAP")) {
         return StyleConstants.CHART_TREEMAP;
      }

      if(str.equals("CHART_CIRCLE_PACKING")) {
         return StyleConstants.CHART_CIRCLE_PACKING;
      }

      if(str.equals("CHART_ICICLE")) {
         return StyleConstants.CHART_ICICLE;
      }

      if(str.equals("CHART_PIE3D")) {
         return StyleConstants.CHART_3D_PIE;
      }

      if(str.equals("CHART_LINE")) {
         return StyleConstants.CHART_LINE;
      }

      if(str.equals("CHART_POINT")) {
         return StyleConstants.CHART_POINT;
      }

      if(str.equals("CHART_BAR3D3D")) {
         return StyleConstants.CHART_3D_BAR_3D;
      }

      if(str.equals("CHART_BAR3D")) {
         return StyleConstants.CHART_3D_BAR;
      }

      if(str.equals("CHART_STACKBAR")) {
         return StyleConstants.CHART_STACK_BAR;
      }

      if(str.equals("CHART_STACKBAR3D")) {
         return StyleConstants.CHART_3D_STACK_BAR;
      }

      if(str.equals("CHART_AREA")) {
         return StyleConstants.CHART_AREA;
      }

      if(str.equals("CHART_STOCK")) {
         return StyleConstants.CHART_STOCK;
      }

      if(str.equals("CHART_STICK")) {
         return StyleConstants.CHART_STICK;
      }

      if(str.equals("CHART_STACKAREA")) {
         return StyleConstants.CHART_STACK_AREA;
      }

      if(str.equals("CHART_XYSCATTER")) {
         return StyleConstants.CHART_SCATTER;
      }

      if(str.equals("CHART_XYLINE")) {
         return StyleConstants.CHART_XY_LINE;
      }

      if(str.equals("CHART_BUBBLE")) {
         return StyleConstants.CHART_BUBBLE;
      }

      if(str.equals("CHART_RADAR")) {
         return StyleConstants.CHART_RADAR;
      }

      if(str.equals("CHART_SURFACE")) {
         return StyleConstants.CHART_SURFACE;
      }

      if(str.equals("CHART_VOLUME")) {
         return StyleConstants.CHART_VOLUME;
      }

      if(str.equals("CHART_WATERFALL")) {
         return StyleConstants.CHART_WATERFALL;
      }

      if(str.equals("CHART_PARETO")) {
         return StyleConstants.CHART_PARETO;
      }

      if(str.equals("CHART_FILLED_RADAR")) {
         return StyleConstants.CHART_FILL_RADAR;
      }

      if(str.equals("CHART_CANDLE")) {
         return StyleConstants.CHART_CANDLE;
      }

      if(str.equals("CHART_BOXPLOT")) {
         return StyleConstants.CHART_BOXPLOT;
      }

      if(str.equals("CHART_MEKKO")) {
         return StyleConstants.CHART_MEKKO;
      }

      if(str.equals("CHART_CURVE")) {
         return StyleConstants.CHART_CURVE;
      }

      if(str.equals("CHART_INV_CURVE")) {
         return StyleConstants.CHART_INV_CURVE;
      }

      if(str.equals("CHART_INVERTED_BAR")) {
         return StyleConstants.CHART_INV_BAR;
      }

      if(str.equals("CHART_INVERTED_STACKBAR")) {
         return StyleConstants.CHART_INV_STACK_BAR;
      }

      if(str.equals("CHART_INVERTED_LINE")) {
         return StyleConstants.CHART_INV_LINE;
      }

      if(str.equals("CHART_INVERTED_POINT")) {
         return StyleConstants.CHART_INV_POINT;
      }

      if(str.equals("CHART_RIBBON")) {
         return StyleConstants.CHART_RIBBON;
      }

      // @by watsonn, for speedometer chart
      if(str.equals("CHART_SPEEDOMETER")) {
         return StyleConstants.CHART_SPEEDOMETER;
      }

      if(str.equals("NO_BORDER")) {
         return StyleConstants.NO_BORDER;
      }

      if(str.equals("Largest")) {
         return Integer.MAX_VALUE;
      }

      if(str.equals("Smallest")) {
         return Integer.MIN_VALUE;
      }
      else {
         int value = 0;

         try {
            value = Integer.parseInt(str);
         }
         catch(Exception e) {
            value = 0;
         }

         return value;
      }
   }

   /**
    * Get a name of a line style from a line style constant. For example,
    * getLineStyleName(StyleConstants.THIN_LINE) returns the string
    * "THIN_LINE"
    * @param val line style constant
    */
   public static String getLineStyleName(int val) {
      if(val == StyleConstants.NO_BORDER) {
         return "NO_BORDER";
      }
      else if(val == StyleConstants.ULTRA_THIN_LINE) {
         return "ULTRA_THIN_LINE";
      }
      else if(val == StyleConstants.THIN_THIN_LINE) {
         return "THIN_THIN_LINE";
      }
      else if(val == StyleConstants.THIN_LINE) {
         return "THIN_LINE";
      }
      else if(val == StyleConstants.MEDIUM_LINE) {
         return "MEDIUM_LINE";
      }
      else if(val == StyleConstants.THICK_LINE) {
         return "THICK_LINE";
      }
      else if(val == StyleConstants.DOUBLE_LINE) {
         return "DOUBLE_LINE";
      }
      else if(val == StyleConstants.RAISED_3D) {
         return "RAISED_3D";
      }
      else if(val == StyleConstants.LOWERED_3D) {
         return "LOWERED_3D";
      }
      else if(val == StyleConstants.DOUBLE_3D_RAISED) {
         return "DOUBLE_3D_RAISED";
      }
      else if(val == StyleConstants.DOUBLE_3D_LOWERED) {
         return "DOUBLE_3D_LOWERED";
      }
      else if(val == StyleConstants.DOT_LINE) {
         return "DOT_LINE";
      }
      else if(val == StyleConstants.DASH_LINE) {
         return "DASH_LINE";
      }
      else if(val == StyleConstants.MEDIUM_DASH) {
         return "MEDIUM_DASH";
      }
      else if(val == StyleConstants.LARGE_DASH) {
         return "LARGE_DASH";
      }
      else if(val == StyleConstants.NONE) {
         return "NONE";
      }

      return Integer.toString(val);
   }

   /**
    * Create an URL with parameters encoded in the url query string.
    */
   public static String createURL(Hyperlink.Ref link) {
      String url = link.getLink().trim();

      if(!url.startsWith("#") && link.getParameterCount() > 0) {
         if(url.indexOf('?') < 0) {
            url += '?';
         }
         else if(!url.endsWith("?")) {
            url += '&';
         }

         Enumeration keys = link.getParameterNames();
         boolean first = true;

         // @by stephenwebster, For Bug #9629
         // Custom URL Parameters added through script must also be url encoded.
         try {
            int queryStringStart = url.indexOf('?');

            if(queryStringStart >= 0 && !url.endsWith("?")) {
               String tmpURL = url;
               url = url.substring(0, queryStringStart + 1);
               String query = tmpURL.substring(queryStringStart + 1);
               String[] params = query.split("&");

               for(String param : params) {
                  String[] nameValue = param.split("=");

                  if(nameValue.length == 2) {
                     String name = nameValue[0];
                     String val = nameValue[1];
                     url += Tool.encodeWebURL(name) + "=" + Tool.encodeWebURL(val) + "&";
                  }
                  else {
                     url += Tool.encodeWebURL(param) + "&";
                  }
               }
            }
         }
         catch(Exception exc) {
            LOG.warn(
                        "URL Encoding of custom URL parameters failed, please " +
                        "make sure link is in a valid format: " + link.getLink(), exc);
            if(!url.endsWith("&")) {
               url += '&';
            }
         }

         while(keys.hasMoreElements()) {
            String name = (String) keys.nextElement();
            Object val = link.getParameter(name);
            String concatStr = "&";

            if(first) {
               first = false;
               concatStr = "";
            }

            // When adding parameters from selections, the parameter value can be an array
            if(Tool.getDataType(val).equals(Tool.ARRAY)) {
               for(Object pvalue : (Object[]) val) {
                  if("".equals(concatStr)) {
                     concatStr = "&";
                  }
                  else {
                     url += concatStr;
                  }

                  url += Tool.encodeWebURL(name) + "=" + Tool.encodeWebURL(pvalue.toString());
               }
            }
            else {
               url += concatStr + Tool.encodeWebURL(name) + "=" + Tool.encodeWebURL(val.toString());
            }
         }
      }

      if(link.getLinkType() == Hyperlink.WEB_LINK && !url.startsWith("http://") &&
         !url.startsWith("https://") && !url.startsWith("mailto:"))
      {
         url = "http://" + url;
      }

      return url;
   }

   /**
    * Get report page size.
    */
   public static Size getPageSize(ReportSheet report) {
      Size sz = report.getPageSize();

      if(sz == null) {
         sz = StyleConstants.DEFAULT_PAGE_SIZE;
      }

      if(report.isLandscape()) {
         sz = sz.rotate();
      }

      return sz;
   }

   /**
    * Verify a directory. If the directory does not exist, create a directory
    * in the user home directory instead and return the new path.
    * @param dir directory setting from the configuration.
    * @param name default directory name.
    */
   public static String verifyDirectory(String dir, String name) {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File file = fileSystemService.getFile(dir);

      try {
         if(mkdir(file)) {
            return dir;
         }
      }
      catch(Exception e) {
      }

      try {
         String home = SreeEnv.getProperty("user.home");
         File file2 = fileSystemService.getFile(home + File.separator + "sree", name);

         if(mkdir(file2)) {
            LOG.warn(
               "Directory [" + dir +
               "] does not exist, using default directory: " + file2);
            return file2.getPath();
         }
      }
      catch(Exception e) {
         LOG.error("Failed to verify directory " + dir + ", " + name, e);
      }

      return ".";
   }

   /**
    * Create a directory recursively.
    */
   private static boolean mkdir(File dir) {
      if(dir.exists()) {
         return true;
      }

      return mkdir(dir.getParent(), dir.getName());
   }

   /**
    * Create the parent and then the directory in the parent.
    */
   private static boolean mkdir(String parent, String name) {
      FileSystemService fileSystemService = FileSystemService.getInstance();

      if(parent == null) {
         return (fileSystemService.getFile(".", name)).mkdir();
      }

      if(mkdir(fileSystemService.getFile(Tool.convertUserFileName(parent)))) {
         return (fileSystemService.getFile(Tool.convertUserFileName(parent),
            Tool.convertUserFileName(name))).mkdir();
      }

      return false;
   }

   /**
    * Build a report condition list from any condition list.
    * <p>
    * In 6.0 report, DataRef is FieldAttributeRef or FormulaExpressionRef,
    * but when read 5.1(or less than) condition list, dataref in condition list
    * is AttributeRef or ExpressionRef. To avoid potential fault, the function
    * provide the ability to build a report condition list from any condition
    * list.
    *
    * @param olist the specified condition list
    * @return the newly built report condition list
    */
   public static ConditionList buildReportConditionList(ConditionList olist) {
      // as the old condition list is useless, just rewrite it for performance
      ConditionList nlist = olist;

      for(int i = nlist.getSize() - 1; i >= 0; i -= 2) {
         ConditionItem con = nlist.getConditionItem(i);

         if(con.getAttribute() != null) {
            if(ExpressionRef.class.equals(con.getAttribute().getClass())) {
               ExpressionRef oref = (ExpressionRef) con.getAttribute();
               FormulaField nref = new FormulaField(oref.getName());
               nref.setExpression(oref.getExpression());
               con.setAttribute(nref);
            }
            else if(AttributeRef.class.equals(con.getAttribute().getClass())) {
               AttributeRef oref = (AttributeRef) con.getAttribute();
               BaseField nref = new BaseField(oref);
               con.setAttribute(nref);
            }
         }
      }

      return nlist;
   }

   /**
    * Get duplicated header in table lens.
    *
    * @param name the specified header
    * @param dtimes the duplicated times of the specified header in table lens
    * @return a unique header represents the specified header with its
    * duplicated times
    */
   public static Object getDupHeader(Object name, int dtimes) {
      return dtimes <= 0 ? name : name + "." + dtimes;
   }

   /**
    * Get the column header from a field. It encapsulate the logic for using
    * qualified name.
    */
   public static String getFieldHeader(DataRef field) {
      return getFieldHeader(field, false);
   }

   /**
    * Get the column header from a field. It encapsulate the logic for using
    * qualified name.
    * @param force, to get the really source any way, because from 11.0, we
    * not support join source, so the source is really useless except for cal.
    */
   public static String getFieldHeader(DataRef field, boolean force) {
      return getFieldHeader(field, force, false);
   }

   /**
    * Get the column header from a field. It encapsulate the logic for using
    * qualified name.
    * @param force, to get the really source any way, because from 11.0, we
    * not support join source, so the source is really useless except for cal.
    * @param cube, if is the cube column.
    */
   public static String getFieldHeader(DataRef field, boolean force, boolean cube) {
      StringBuilder sb = new StringBuilder();

      if(cube) {
         String caption = null;

         if(field instanceof VSDimensionRef) {
            caption = ((VSDimensionRef) field).getCaption();
         }

         if(caption == null) {
            AttributeRef ref = getAttributeRef(field);
            caption = ref != null ? ref.getCaption() : null;
         }

         if(caption != null) {
            return caption;
         }
      }

      if(field instanceof BaseField && ((BaseField) field).getSource(force) != null) {
         sb.append(((BaseField) field).getSourceDescription(force));
         sb.append('.');
      }

      if(!(field instanceof DimensionRef) && !(field instanceof MeasureRef) &&
         field != null && field.getEntity() != null)
      {
         sb.append(field.getEntity());
         sb.append('.');
      }

      if(field instanceof ColumnRef && ((ColumnRef) field).isApplyingAlias() &&
         ((ColumnRef) field).getAlias() != null)
      {
         sb.append(((ColumnRef) field).getAlias());
      }
      else if(field != null) {
         sb.append(field.getAttribute());
      }

      return sb.toString();
   }

   private static AttributeRef getAttributeRef(DataRef field) {
      if(field instanceof DataRefWrapper) {
         return getAttributeRef(((DataRefWrapper) field).getDataRef());
      }

      if(field instanceof AttributeRef) {
         return (AttributeRef) field;
      }

      return null;
   }

   /**
    * Get the browsed data of a column in a table.
    * @param table the specified table.
    * @param column the name of the specified column.
    * @return the browsed data if any, <tt>null</tt> otherwise.
    */
   public static BrowseDataModel getBrowsedData(XTable table, String column) {
      if(table == null) {
         return null;
      }

      int col = findColumn(table, column);

      if(col < 0) {
         return null;
      }

      TableLens lens = (TableLens) table;
      lens = new ColumnMapFilter(lens, new int[] {col});
      lens = new DistinctTableLens(lens);
      lens = new SortFilter(lens, new int[] {0});

      lens.moreRows(Integer.MAX_VALUE);
      int count = lens.getRowCount() - lens.getHeaderRowCount() -
         lens.getTrailerRowCount();

      if(count <= 0) {
         return null;
      }

      Object[] data = new Object[count];

      for(int i = 0; i < count; i++) {
         data[i] = lens.getObject(lens.getHeaderRowCount() + i, 0);
      }

      return BrowseDataModel.builder().values(data).build();
   }

   /**
    * Get a table lens to get pure data.
    */
   public static TableLens getDataTable(TableFilter filter) {
      TableLens table = filter;

      while(table instanceof TableFilter) {
         if(table instanceof FormatTableLens) {
            return ((FormatTableLens) table).getTable();
         }

         table = ((TableFilter) table).getTable();
      }

      return filter;
   }

   /**
    * Merge parameters into hyperlink.
    */
   public static void mergeParameters(Hyperlink.Ref ref, VariableTable params) {
      if(ref == null || params == null || !ref.isSendReportParameters()) {
         return;
      }

      Enumeration keys = params.keys();

      while(keys.hasMoreElements()) {
         String name = (String) keys.nextElement();

         if(name.startsWith("__service_")) {
            continue;
         }

         if(ref.getParameter(name) != null) {
            continue;
         }

         if(params.isInternalParameter(name)) {
            continue;
         }

         addLinkParameter(ref, params, name);
      }
   }

   /**
    * Add hyperlink parameter.
    */
   public static void addLinkParameter(Hyperlink.Ref hlink, VariableTable vtable) {
      if(vtable == null) {
         return;
      }

      List<String> exists = new ArrayList<>();
      Enumeration<String> pnames = hlink.getParameterNames();
      Enumeration<String> vnames = vtable.keys();

      while(pnames.hasMoreElements()) {
         exists.add(pnames.nextElement());
      }

      while(vnames.hasMoreElements()) {
         String name = vnames.nextElement();

         // @by jasons, don't include _USER_, _ROLES_, __principal__ entries
         if(exists.contains(name) || VariableTable.isContextVariable(name)) {
            continue;
         }

         Util.addLinkParameter(hlink, vtable, name);
      }
   }

   /**
    * Add hyperlink parameter.
    */
   public static void addLinkParameter(Hyperlink.Ref ref, VariableTable params, String name) {
      List<String> variables = parseVariablesFromLink(new ArrayList<>(), ref.getLink());
      Object value = null;

      try {
         value = params.get(name);
      }
      catch(Exception ex) {
         // ignored
         return;
      }

      if(ref.getLinkType() == Hyperlink.WEB_LINK && variables.contains(name)) {
         value = Tool.encodeURL(String.valueOf(value));
         String var = "$(" + name + ")";
         String link = !Tool.isEmptyString(ref.getLink()) ? ref.getLink() : "";
         ref.setLink(link.replace(var, String.valueOf(value)));
      }
      else {
         ref.setParameter(name, value);
      }
   }

   /**
    * Parse variables from auto drill web-link.
    */
   public static List<String> parseVariablesFromLink(List<String> vars, String link) {
      if(Tool.isEmptyString(link)) {
         return vars;
      }

      if(link.contains("$(")) {
         String var = null;
         String remainder = null;
         String strs = link.substring(link.indexOf("$(") + 2);

         if(strs.contains(")")) {
            var = strs.substring(0, strs.indexOf(")"));
            remainder = strs.substring(strs.indexOf(")") + 1);
         }
         else {
            return vars;
         }

         if(remainder != null && remainder.length() > 0) {
            parseVariablesFromLink(vars, remainder);
         }

         vars.add(var);
      }

      return vars;
   }

   /**
    * Update the parameter link of the link ref.
    */
   public static void updateLink(Hyperlink.Ref ref) {
      if(ref == null) {
         return;
      }

      String link = ref.getLink();

      if(link == null) {
         return;
      }

      int idxF = link.indexOf('{', 0);
      int idxT = -1;
      Hashtable<Integer,Integer> idxTable = new Hashtable<>();

      while(idxF != -1) {
         idxT = link.indexOf('}', idxF);

         if(idxT != -1) {
            idxTable.put(idxT, idxF);
         }

         idxF = link.indexOf('{', idxF + 1);
      }

      if(idxTable.isEmpty()) {
         return;
      }

      Set<Integer> keySet = idxTable.keySet();
      Integer[] keys = keySet.toArray(new Integer[0]);
      Arrays.sort(keys);
      int offset = 0;

      for(int i = 0; i < keys.length; i++) {
         int from = idxTable.get(keys[i]) + offset;
         int to = keys[i] + offset;

         String param = link.substring(from, to + 1);
         int pLength = param.length();
         String pname = param.substring(1, pLength - 1);
         Object pvalue = ref.getParameter(pname);

         if(pvalue != null) {
            offset = offset + pvalue.toString().length() - pLength;
            link = link.substring(0, from) + Tool.toString(pvalue) + link.substring(to + 1);
         }
      }

      ref.setLink(link);
   }

   /**
    * Format the age to hh:mm:ss.
    */
   public static String formatAge(Date date, boolean addTime) {
      long dt1 = date.getTime();
      long dt2 = System.currentTimeMillis();
      long exhaustedTime = dt2 - dt1;

      int hour = (int) (exhaustedTime / 3600000); // 1 h equals 3600000 ms
      int min = (int) ((exhaustedTime - hour * 3600000) / 60000);
      int sec = (int) ((exhaustedTime - hour * 3600000 - min * 60000) / 1000);
      StringBuilder builder = new StringBuilder();

      builder.append(hour);
      builder.append(":");
      builder.append(formatTime(min));
      builder.append(":");
      builder.append(formatTime(sec));

      if(addTime) {
         builder.append("#time:");
         builder.append(exhaustedTime);
      }

      return builder.toString();
   }

   private static String formatTime(int val) {
      String str = Integer.toString(val);

      if(str.length() == 1) {
         return "0" + str;
      }
      else if(str.length() == 2) {
         return str;
      }
      else {
         return "00";
      }
   }

   /**
    * Get 'query timeout' property.
    *
    * @param elem the report element
    * @return 'query timeout' property
    */
   public static int getQueryTimeout(ReportElement elem) {
      int bindingTimeout = Integer.MAX_VALUE;
      int timeout = Integer.MAX_VALUE;

      timeout = getQueryRuntimeTimeout();
      timeout = Math.min(bindingTimeout, timeout);

      return (timeout == Integer.MAX_VALUE) ? 0 : timeout;
   }

   /**
    * Get the max rows of a query at runtime.
    */
   public static int getRuntimeMaxRows() {
      int max = getQueryRuntimeMaxrow(Integer.MAX_VALUE);
      return max == Integer.MAX_VALUE ? 0 : max;
   }

   /**
    * Get xtable labels.
    * @param table the specifield xtable.
    * @paam index the binding column index.
    * @return the labels of the table at the index.
    */
   public static String[] getXTableLabels(XTable table, int index) {
      ArrayList lbls = new ArrayList();
      table.moreRows(Integer.MAX_VALUE);

      for(int i = table.getHeaderRowCount(); i < table.getRowCount(); i++) {
         Object obj = table.getObject(i, index);
         String lbl = null;

         if(table instanceof TableLens) {
            Format fmt = ((TableLens) table).getDefaultFormat(i, index);

            try {
               lbl = fmt == null || obj == null ? null : fmt.format(obj);
            }
            catch(Exception ex) {
               LOG.warn("Failed to format " + obj + " with " + fmt, ex);
            }
         }

         lbl = lbl == null ? Tool.toString(obj) : lbl;
         lbls.add(lbl);
      }

      String[] arr = new String[lbls.size()];
      lbls.toArray(arr);
      return arr;
   }

   /**
    * Get column comparator from a table.
    * @param table thes pecified table lens.
    * @param ref the specified column data ref.
    * @return the column comparator.
    */
   public static Comparator getColumnComparator(XTable table, DataRef ref) {
      XTable table0 = table;

      while(table0 instanceof TableFilter) {
         if(table0 instanceof CubeTableFilter) {
            return ((CubeTableFilter) table0).getComparator(ref);
         }

         table0 = ((TableFilter) table0).getTable();
      }

      return null;
   }

   /**
    * Compare to headers.
    */
   private static boolean isSameColumn(String header0, String qheader) {
      // compare header directly
      if(Tool.equals(qheader, header0)) {
         return true;
      }

      // remove date range function to compare
      while(header0.indexOf('(') >= 0 && header0.lastIndexOf(')') >= 0) {
         header0 = header0.substring(header0.indexOf('(') + 1,
                                     header0.lastIndexOf(')'));
      }

      if(Tool.equals(qheader, header0)) {
         return true;
      }

      // remove digit suffix if any to compare
      int lidx = header0.lastIndexOf('.');

      if(lidx >= 0) {
         String suf = header0.substring(lidx + 1);
         boolean digit = suf.length() > 0;

         for(int j = 0; j < suf.length(); j++) {
            if(!Character.isDigit(suf.charAt(j))) {
               digit = false;
               break;
            }
         }

         if(digit) {
            header0 = header0.substring(0, lidx);
         }
      }

      if(Tool.equals(qheader, header0)) {
         return true;
      }

      // use last name of table header to compare
      lidx = header0.lastIndexOf('.');

      if(lidx >= 0) {
         header0 = header0.substring(lidx + 1);
      }

      if(Tool.equals(qheader, header0)) {
         return true;
      }

      // use last name in query header to compare
      lidx = qheader.lastIndexOf('.');

      if(lidx >= 0) {
         String qheader0 = qheader.substring(lidx + 1);

         if(Tool.equals(qheader0, header0)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Find identifier for drill sub query.
    * @param lens the table lens to find out really identifier.
    * @param iden the identifier for lens, use header instead of identifier
    *  may be not absolute correct.
    */
   public static String findIdentifierForSubQuery(TableLens lens, String iden) {
      // this function is serving for Util.findSubqueryVariable, the column
      // store in DrillSubQuery is the basest identifier, and if we just use a
      // table lens' column identifier to find sub query parameter, may be
      // cannot find it, because the identifier is not same forever with the
      // basest table in each table lens, so we should find the basest table
      // lens' column identifier here, and for crosstab, formula table,
      // dup header, date range header need specify check, fix bug1263807167813
      // this function is heavy, use the function should cache result
      if(iden == null) {
         return null;
      }

      // list all table(s)
      java.util.List<TableLens> filters = new ArrayList<>();
      filters.add(lens);

      while(lens instanceof TableFilter) {
         lens = ((TableFilter) lens).getTable();
         filters.add(lens);
      }

      int col = -1;
      TableLens table = null;

      // find the basest table which contains the column
      for(int i = filters.size() - 1; i >= 0; i--) {
         col = Util.findColumn2(filters.get(i), iden);

         if(col >= 0) {
            table = filters.get(i);
            break;
         }
      }

      String identifier = null;

      // find really identifier
      if(col >= 0 && table != null) {
         identifier = table.getColumnIdentifier(col);

         while(table instanceof TableFilter && col >= 0) {
            if(table.getColumnIdentifier(col) != null) {
               identifier = table.getColumnIdentifier(col);
            }

            col = ((TableFilter) table).getBaseColIndex(col);
            table = ((TableFilter) table).getTable();
         }
      }

      return identifier == null ? iden : identifier;
   }

   private static int findColumn2(TableLens lens, String header) {
      int col = findColumn(lens, header);

      while(col < 0) {
         // date range header?
         int idx1 = header.indexOf('(');
         int idx2 = header.lastIndexOf(')');
         // dup header?
         int dot = header.lastIndexOf('.');
         String end = dot >= 0 ? header.substring(dot + 1) : null;
         boolean dup = end != null;

         if(dup) {
            try {
               Integer.parseInt(end);
            }
            catch(Exception e) {
               dup = false;
            }
         }

         // just a test, not absolute correct
         if(dup) {
            header = header.substring(0, dot);
            col = Util.findColumn(lens, header);
         }
         else if(idx1 >= 0 && idx2 > idx1) {
            header = header.substring(idx1 + 1, idx2);
            col = Util.findColumn(lens, header);
         }
         else {
            break;
         }
      }

      return col;
   }

   /**
    * Get original header.
    */
   public static String findSubqueryVariable(DrillSubQuery query, String header) {
      // take care, see Util.findIdentifierForSubQuery
      if(query == null || header == null) {
         return null;
      }

      Iterator<String> it = query.getParameterNames();

      while(it.hasNext()) {
         String qvar = it.next();
         String qheader = query.getParameter(qvar);
         String header0 = header;

         if(isSameColumn(header0, qheader)) {
            return qvar;
         }
      }

      return null;
   }

   /**
    * Merge meta info by auto-created meta info and user defined meta info.
    */
   public static XMetaInfo mergeMetaInfo(XMetaInfo oinfo, XMetaInfo uinfo, int level) {
      if(uinfo == null) {
         return oinfo == null ? null : oinfo.clone();
      }

      String dtype = oinfo == null ? null : oinfo.getProperty("columnDataType");
      XFormatInfo finfo = null;

      if(XSchema.TIME.equals(dtype)) {
         SimpleDateFormat dfmt = XUtil.getDefaultDateFormat(level, dtype);
         finfo = dfmt == null ? null :
            new XFormatInfo(TableFormat.DATE_FORMAT, dfmt.toPattern());
      }

      // always use the auto created format
      XMetaInfo ninfo = uinfo.clone();

      if(oinfo != null && !oinfo.isXFormatInfoEmpty()) {
         ninfo.setXFormatInfo(oinfo.getXFormatInfo());
      }

      if(finfo != null) {
         ninfo.setXFormatInfo(finfo);
      }

      return ninfo;
   }

   /**
    * Check if a meta info's format info is replaceable.
    */
   public static boolean isXFormatInfoReplaceable(XMetaInfo info) {
      return info == null || info.isXFormatInfoEmpty() ||
         "true".equalsIgnoreCase(info.getProperty("autoCreatedFormat"));
   }

   /**
    * Remove date format for a aggregate column.
    */
   public static void removeIncompatibleMetaInfo(XMetaInfo info, Formula aggr) {
      if(aggr == null || info == null || info.isXFormatInfoEmpty()) {
         return;
      }

      if(aggr instanceof FirstFormula || aggr instanceof LastFormula ||
         aggr instanceof MaxFormula || aggr instanceof MinFormula ||
         aggr instanceof NthLargestFormula || aggr instanceof NthSmallestFormula ||
         aggr instanceof NthMostFrequentFormula || aggr instanceof PthPercentileFormula)
      {
         return;
      }

      // count has not relationship with original value (e.g. currency, date)
      if(aggr instanceof CountFormula || aggr instanceof DistinctCountFormula ||
         aggr instanceof CorrelationFormula)
      {
         info.setXFormatInfo(null);
         info.setXDrillInfo(null);
         return;
      }

      String fmt = info.getXFormatInfo().getFormat();

      // aggregate column, date format is meaningless
      if(TableFormat.DATE_FORMAT.equals(fmt)) {
         info.setXFormatInfo(null);
         info.setXDrillInfo(null);
      }
   }

   /**
    * Check the character is cjk character or not.
    * 1) CJK character
    * Code point range     Block name                          Release
    * U+3400..U+4DB5       CJK Unified Ideographs Extension A  3.0
    * U+4E00..U+9FA5       CJK Unified Ideographs              1.1
    * U+9FA6..U+9FBB       CJK Unified Ideographs              4.1
    * U+F900..U+FA2D       CJK Compatibility Ideographs        1.1
    * U+FA30..U+FA6A       CJK Compatibility Ideographs        3.2
    * U+FA70..U+FAD9       CJK Compatibility Ideographs        4.1
    * U+20000..U+2A6D6     CJK Unified Ideographs Extension B  3.1
    * U+2F800..U+2FA1D     CJK Compatibility Supplement        3.1
    * 2) FF00-FFEF
    * 3) 2E80-2EFF
    * 4) 3000-303F
    * 5) 31C0-31EF
    */
   private static boolean isCJKCharacter(char key) {
      if(key < 0x2e80) {
         return false;
      }

      return key <= 0x2eff || key >= 0x3000 && key <= 0x303f ||
         key >= 0x31c0 && key <= 0x31ef || key >= 0x3400 && key <= 0x4db5 ||
         key >= 0x4e00 && key <= 0x9fbb || key >= 0xf900 && key <= 0xfa2d ||
         key >= 0xfa30 && key <= 0xfa6a || key >= 0xfa70 && key <= 0xfad9 ||
         key >= 0xff00 && key <= 0xffef || key >= 0x20000 && key <= 0x2a6d6 ||
         key >= 0x2f800 && key <= 0x2fa1d;
   }

   public static Class getColType(XTable lens, int col, Class defaultType, int rows) {
      return getColType(lens, col, defaultType, rows, false);
   }

   /**
    * Gets the type of the specified column.
    *
    * @param col         the column index.
    * @param defaultType the default column type. This defaults to
    *                    java.lang.String if <tt>null</tt>.
    * @param rows maximum number of rows to check. 0 to check all.
    * @param checkNumberType If the values for the given col are all numbers but of different
    *                        type then if this is set to <tt>true</tt> then the column type will
    *                        be set to a number type that includes all the numbers.
    *
    * @return the column type.
    */
   public static Class getColType(XTable lens, int col, Class defaultType, int rows,
                                  boolean checkNumberType)
   {
      Object obj = null;
      Class otype = null;
      boolean all = rows == 0;
      boolean useDType = false;
      int r = 0;

      if(all) {
         rows = 2000;
      }

      int trailer = lens.getTrailerRowCount();

      // fix bug1318257874696, only try limited times
      for(r = lens.getHeaderRowCount(); lens.moreRows(r) && r < rows; r++) {
         Object test = lens.getObject(r, col);

         if(!(test instanceof CalcTableLens.Formula) && test != null && !"".equals(test)) {
            if(otype == null) {
               obj = test;
               otype = obj.getClass();
            }
            else if(test.getClass() != otype) {
               // ignore grand total label
               if(!lens.moreRows(r + trailer)) {
                  break;
               }

               if(checkNumberType && Tool.isNumberClass(otype) &&
                  Tool.isNumberClass(test.getClass()))
               {
                  if(needEnlargeNumberType(otype, test.getClass())) {
                     otype = test.getClass();
                     obj = test;
                  }
               }
               else {
                  useDType = true;
                  obj = test;
                  break;
               }
            }
         }
      }

      if(all && obj == null) {
         final int MAX = 5000000;

         for(r = Math.max(r, 100); lens.moreRows(r) && r < MAX; r = (int) (r * 1.1)) {
            Object test = lens.getObject(r, col);

            if(test != null && !"".equals(test)) {
               if(!lens.moreRows(r + trailer)) {
                  break;
               }

               if(otype == null) {
                  obj = test;
                  otype = obj.getClass();
               }
               else if(test.getClass() != otype) {
                  if(checkNumberType && Tool.isNumberClass(otype) &&
                     Tool.isNumberClass(test.getClass()))
                  {
                     if(needEnlargeNumberType(otype, test.getClass())) {
                        otype = test.getClass();
                        obj = test;
                     }
                  }
                  else {
                     useDType = true;
                     obj = test;
                     break;
                  }
               }
            }
         }
      }

      return obj == null || useDType ?
         (defaultType == null ? String.class : defaultType) : obj.getClass();
   }

   /**
    * Check if the target value is an abbreviation for "Not Applicable"
    */
   public static boolean isNotApplicableValue(Object value) {
      String str = value instanceof String ? (String) value : null;
      return "N/A".equalsIgnoreCase(str) || "NA".equalsIgnoreCase(str);
   }

   public static boolean isSQLite(XDataSource dataSource) {
      if(!(dataSource instanceof JDBCDataSource)) {
         return false;
      }

      String driver = ((JDBCDataSource) dataSource).getDriver();
      return "org.sqlite.JDBC".equals(driver);
   }

   /**
    * Check need enlarge the number type to avoid lost precision.
    */
   public static boolean needEnlargeNumberType(Class fcls, Class cls) {
      return getCompareNumber(cls) > getCompareNumber(fcls);
   }

   /**
    * Get number type number for compare.
    */
   private static int getCompareNumber(Class cls) {
      if(Tool.isNumberClass(cls)) {
         if(Byte.class.isAssignableFrom(cls)) {
            return 1;
         }
         else if(Short.class.isAssignableFrom(cls)) {
            return 2;
         }
         else if(Integer.class.isAssignableFrom(cls)) {
            return 3;
         }
         else if(Long.class.isAssignableFrom(cls)) {
            return 4;
         }
         else if(Float.class.isAssignableFrom(cls)) {
            return 5;
         }
         else if(Double.class.isAssignableFrom(cls)) {
            return 6;
         }
      }

      return -1;
   }

   /**
    * Localize the text format.
    * @param spec the string in text format.
    * @return the localized string.
    */
   public static String localizeTextFormat(String spec) {
      Catalog userCatalog = Catalog.getCatalog(null, Catalog.REPORT);
      return localizeTextID(spec, userCatalog);
   }

   /**
    * Localize the text format.
    */
   private static String localizeTextID(String str, Catalog catalog) {
      int start = str.indexOf('{');
      int end = str.indexOf('}');

      if(start < 0 || end < 0) {
         return str;
      }

      String textID = str.substring(start + 1, end);
      Pattern pattern = Pattern.compile("[0-9]*");
      boolean isNumber = pattern.matcher(textID).matches();
      boolean isParameters = textID.indexOf(',') >= 0;
      boolean isIDExisted = catalog.getIDString(textID) != null;
      String localizeText = isNumber || isParameters ? "{" + textID + "}" :
         isIDExisted ? catalog.getString(textID) : "";

      return str.substring(0, start) + localizeText +
         (end < str.length() ? localizeTextID(str.substring(end + 1), catalog) :
         "");
   }

   public static String getObjectFullPath(int type, String path, Principal principal) {
      return getObjectFullPath(type, path, principal, null);
   }

   /**
    * get fullPath of object on Repository.
    */
   public static String getObjectFullPath(int type, String path, Principal principal, IdentityID owner) {
      final Catalog catalog = Catalog.getCatalog(principal);
      String rootPath = null;

      switch(type) {
      case RepositoryEntry.DATA_SOURCE_FOLDER:
         rootPath = catalog.getString("Data Source");

         if("/".equals(path)) {
            return rootPath;
         }

         break;
      case RepositoryEntry.AUTO_SAVE_VS:
         if(path == null) {
            return "";
         }

         String[] strings = Tool.split(path, '^');

         if(strings.length < 3) {
            return path;
         }

         return "Recycle Bin/Auto Saved Files/" + strings[2] + "/Dashboard/" + strings[3];
      case RepositoryEntry.AUTO_SAVE_WS:
         if(path == null) {
            return "";
         }

         String[] strs = Tool.split(path, '^');

         if(strs.length < 3) {
            return path;
         }

         return "Recycle Bin/Auto Saved Files/" +  strs[2] + "/Data Worksheet/" + strs[3];
      case RepositoryEntry.DATA_SOURCE:
      case RepositoryEntry.DATA_SOURCE | RepositoryEntry.FOLDER:
         rootPath = catalog.getString("Data Source");
         break;
      case RepositoryEntry.QUERY | RepositoryEntry.FOLDER:
         rootPath = catalog.getString("Data Source");
         int separator = path.indexOf("::");

         if(separator < 0) {
            break;
         }

         String parentPath = path.substring(separator + 2, path.length());
         String queryFolderName = path.substring(0, separator);
         path = parentPath + "/" + queryFolderName;
         break;
      case RepositoryEntry.LOGIC_MODEL | RepositoryEntry.FOLDER:
      case RepositoryEntry.PARTITION | RepositoryEntry.FOLDER:
      case RepositoryEntry.VPM:
         rootPath = catalog.getString("Data Source");
         path = getDataSourceModelFullPath(type, path, catalog);
         break;
      case RepositoryEntry.LIBRARY_FOLDER:
         rootPath = catalog.getString("Library");

         if("*".equals(path)) {
            return rootPath;
         }

         break;
      case RepositoryEntry.SCRIPT:
      case RepositoryEntry.SCRIPT | RepositoryEntry.FOLDER:
      case RepositoryEntry.TABLE_STYLE:
      case RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER:
         rootPath = catalog.getString("Library");
         path = getLibraryFullPath(type, path, catalog);
         break;
      case RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER:
         if("/".equals(path)) {
            return catalog.getString("Repository");
         }

         break;
      case RepositoryEntry.WORKSHEET_FOLDER:
         rootPath = catalog.getString("Worksheets");

         if("/".equals(path)) {
            return rootPath;
         }

         break;
      case RepositoryEntry.WORKSHEET:
         rootPath = catalog.getString("Worksheets");
         break;
      case RepositoryEntry.DASHBOARD:
         rootPath = catalog.getString("Dashboard");
         break;
      case RepositoryEntry.DASHBOARD_FOLDER:
         rootPath = catalog.getString("Dashboard");

         if("/".equals(path)) {
            return rootPath;
         }

         break;
         case RepositoryEntry.SCHEDULE_TASK | RepositoryEntry.FOLDER:
            rootPath = catalog.getString("Schedule Tasks");

            if("/".equals(path)) {
               return rootPath;
            }

         break;
      }

      if(rootPath != null) {
         path = rootPath + "/" + path;
      }

      if(owner != null) {
         path = "User/" + owner.name + "/" + path;
      }

      return path;
   }

   private static String getQueryFullPath(XQuery query) {
      StringBuilder fullPath = new StringBuilder();
      boolean insertSeparator = false;

      if(query.getDataSource() != null) {
         insertSeparator = true;
         fullPath.append(query.getDataSource().getFullName());
      }

      if(query.getFolder() != null && !query.getFolder().isEmpty()) {
         if(insertSeparator) {
            fullPath.append("/");
         }

         fullPath.append(query.getFolder());
      }

      fullPath.append("/").append(query.getName());

      return fullPath.toString();
   }

   private static String getDataSourceModelFullPath(int type, String path, Catalog catalog) {
      if(path == null) {
         return path;
      }

      final String dataModelLabel = catalog.getString("Data Model");
      String dataSourceModelName;
      String parentPath;
      String dataModelFolder = null;
      int separator = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

      if(separator >= 0) {
         parentPath = path.substring(0, separator);
         dataSourceModelName = path.substring(separator + XUtil.DATAMODEL_FOLDER_SPLITER.length());
         int modelNameSeparator = dataSourceModelName.indexOf(XUtil.DATAMODEL_PATH_SPLITER);

         if(modelNameSeparator < 0) {
            return path;
         }

         dataModelFolder = dataSourceModelName.substring(0, modelNameSeparator);
         dataSourceModelName = dataSourceModelName.substring(modelNameSeparator + 1);
      }
      else {
         separator = path.indexOf("^");

         if(separator < 0) {
            return path;
         }

         dataSourceModelName = path.substring(separator + 1, path.length());
         parentPath = path.substring(0, separator);
      }

      if(type == RepositoryEntry.VPM) {
         return parentPath + "/" + dataSourceModelName;
      }
      else {
         return parentPath + "/" + dataModelLabel +
            (dataModelFolder == null ? "/" : "/" + dataModelFolder + "/") + dataSourceModelName;
      }
   }

   private static String getLibraryFullPath(int type, String path, Catalog catalog) {
      String parentLabel = null;

      switch(type) {
      case RepositoryEntry.SCRIPT:
         parentLabel = catalog.getString("Scripts");
         break;
      case RepositoryEntry.TABLE_STYLE:
         parentLabel = catalog.getString("Table Styles");
         break;
      case RepositoryEntry.SCRIPT | RepositoryEntry.FOLDER:
         if("*".equals(path)) {
            return catalog.getString("Scripts");
         }

         break;
      case RepositoryEntry.TABLE_STYLE | RepositoryEntry.FOLDER:
         if("*".equals(path)) {
            return catalog.getString("Table Styles");
         }

         break;
      }

      if(parentLabel == null) {
         return path;
      }

      return parentLabel + "/" + path;
   }

   /**
    * Find column by entity and attribute and igonre alias.
    */
   public static DataRef findColumn(ColumnSelection cols, String entity, String attr) {
      Enumeration e = cols.getAttributes();

      while(e.hasMoreElements()) {
         DataRef dr = (DataRef) e.nextElement();

         if(dr != null && Tool.equals(dr.getEntity(), entity, false) &&
            Tool.equals(dr.getAttribute(), attr, false))
         {
            return dr;
         }
      }

      return null;
   }

   /**
    * Gets the global preview row limit.
    *
    * @return the row limit.
    */
   public static int getQueryPreviewMaxrow() {
      return getQueryPreviewMaxrow(5000);
   }

   /**
    * Gets the global preview row limit.
    *
    * @param defaultLimit the default limit to use if the property is not defined.
    *
    * @return the row limit.
    */
   public static int getQueryPreviewMaxrow(int defaultLimit) {
      return getMaxrowProperty("query.preview.maxrow", defaultLimit);
   }

   /**
    * Gets the preview row limit for a particular scope.
    *
    * @param localLimit the row limit of the local scope. It is assumed that a value less than or
    *                   equal to zero is undefined.
    *
    * @return the row limit.
    */
   public static int getQueryLocalPreviewMaxrow(int localLimit) {
      int max;

      if(SreeEnv.getProperty("query.preview.maxrow",  null) == null) {
         max = localLimit;
      }
      else {
         final int defaultLimit = 5000;
         int globalLimit = getQueryPreviewMaxrow(defaultLimit);

         if(globalLimit > 0 && localLimit > 0) {
            max = Math.min(globalLimit, localLimit);
         }
         else if(globalLimit > 0) {
            max = globalLimit;
         }
         else if(localLimit > 0) {
            max = localLimit;
         }
         else {
            max = defaultLimit;
         }
      }

      // make sure the preview doesn't exceed the runtime limit
      return getQueryLocalRuntimeMaxrow(max);
   }

   /**
    * Gets the global runtime row limit.
    *
    * @return the row limit.
    */
   public static int getQueryRuntimeMaxrow() {
      return getQueryRuntimeMaxrow(0);
   }

   /**
    * Gets the global runtime row limit.
    *
    * @param defaultLimit the default limit to use if the property is not defined.
    *
    * @return the row limit.
    */
   private static int getQueryRuntimeMaxrow(int defaultLimit) {
      return getMaxrowProperty("query.runtime.maxrow", defaultLimit);
   }

   /**
    * Gets the runtime row limit for a particular scope.
    *
    * @param localLimit the row limit of the local scope. It is assumed that a value less than or
    *                   equal to zero is undefined.
    *
    * @return the row limit.
    */
   public static int getQueryLocalRuntimeMaxrow(int localLimit) {
      int max = 0;
      int maxRow = Util.getOrganizationMaxRow();

      if(SreeEnv.getProperty("query.runtime.maxrow", null) == null && maxRow == 0) {
         max = localLimit;
      }
      else {
         int globalLimit = getQueryRuntimeMaxrow(0);

         if(maxRow > 0) {
            max = globalLimit == 0 ? maxRow : Math.min(globalLimit, maxRow);
         }

         if(globalLimit > 0 && localLimit > 0) {
            max = Math.min(globalLimit, localLimit);
         }
         else if(globalLimit > 0) {
            max = globalLimit;
         }
         else if(localLimit > 0) {
            max = localLimit;
         }
      }

      return max;
   }

   public static int getTableOutputMaxrow() {
      return getTableOutputMaxrow(0);
   }

   /**
    * Gets the row limit for table output.
    *
    * @param defaultLimit the default limit to use if the property is not defined.
    *
    * @return the row limit.
    */
   public static int getTableOutputMaxrow(int defaultLimit) {
      return getMaxrowProperty("table.output.maxrow", defaultLimit);
   }

   public static int getOrganizationMaxRow() {
      int maxRow = Util.MAX_ROW_COUNT;
      String rowCount = SreeEnv.getProperty("max.row.count", false, true);

      if(rowCount != null) {
         maxRow = Integer.parseInt(rowCount);
      }

      return maxRow;
   }

   public static int getOrganizationMaxColumn() {
      int maxCol = Util.MAX_COLUMN_COUNT;
      String colCount = SreeEnv.getProperty("max.col.count", false, true);

      if(colCount != null) {
         maxCol = Integer.parseInt(colCount);
      }

      return maxCol;
   }

   public static int getOrganizationMaxCellSize() {
      int maxSize = Util.MAX_CELL_SIZE;
      String cellSize = SreeEnv.getProperty("max.cell.size", false, true);

      if(cellSize != null) {
         maxSize = Integer.parseInt(cellSize);
      }

      return maxSize;
   }

   public static String getColumnLimitMessage() {
      return Catalog.getCatalog().getString(
         "common.oganization.colMaxCount", Util.getOrganizationMaxColumn());
   }

   public static String getTextLimitMessage() {
      return Catalog.getCatalog().getString(
         "common.limited.text", Util.getOrganizationMaxCellSize());
   }

   private static int getMaxrowProperty(String propertyName, int defaultLimit) {
      String property = SreeEnv.getProperty(propertyName);
      int max = 0;
      int organizationMax = Util.getOrganizationMaxRow();

      if(property != null) {
         try {
            max = NumberParser.getInteger(property);
         }
         catch(Exception ex) {
            LOG.warn("Invalid value for {} property: {}", propertyName, property, ex);
         }
      }

      // organization id will set later after organization ui is add, so just using fixed value now
      if(max > 0 || organizationMax > 0) {
         if(max > 0 && organizationMax > 0) {
            return Math.min(max, organizationMax);
         }
         else if(max > 0) {
            return max;
         }
         else {
            return organizationMax;
         }
      }

      return defaultLimit;
   }

   private static int getQueryRuntimeTimeout() {
      String prop;

      if((prop = SreeEnv.getProperty("query.runtime.timeout")) != null) {
         try {
            return NumberParser.getInteger(prop);
         }
         catch(Exception ex) {
            LOG.warn("Failed to set value for query.runtime.timeout property: {}", prop, ex);
         }
      }

      return Integer.MAX_VALUE;
   }

   /**
    * @param queryParamStr the target url query parameter string.
    * @return url query param map.
    */
   public static HashMap<String, String> getQueryParamMap(String queryParamStr) {
      HashMap<String, String> map = new HashMap<>();

      if(queryParamStr == null || queryParamStr.indexOf("=") == -1) {
         return map;
      }

      String[] params = queryParamStr.split("&");

      for(int i = 0; i < params.length; i++) {
         String pair = params[i];
         int idx = pair.indexOf("=");

         if(idx == -1 && idx + 1 < pair.length()) {
            continue;
         }

         String key = pair.substring(0, idx);
         String value = pair.substring(idx + 1);

         map.put(key.toLowerCase(), value);
      }

      return map;
   }

   /**
    * Rename the depended in a script.
    *
    * @param oname  the specified old assembly name.
    * @param nname  the specified new assembly name.
    * @param script the specified script.
    *
    * @return the renamed script.
    */
   public static String renameScriptDepended(final String oname,
                                             final String nname, String script)
   {
      return renameScriptDepended(oname, nname, script, null);
   }

   public static String renameScriptRefDepended(final String oname,
                                                final String nname, String script)
   {
      return renameScriptRefDepended(oname, nname, script, null);
   }

   /**
    * Rename the depended in a script.
    *
    * @param oname  the specified old assembly name.
    * @param nname  the specified new assembly name.
    * @param script the specified script.
    *
    * @return the renamed script.
    */
   public static String renameScriptRefDepended(final String oname,
                                                final String nname, String script,
                                                Function<String, Boolean> acceptFunc)
   {
      if(script == null || script.length() == 0) {
         return script;
      }

      final StringBuilder sb = new StringBuilder();

      ScriptIterator.ScriptListener listener = (ScriptIterator.Token token,
                                                ScriptIterator.Token pref,
                                                ScriptIterator.Token cref) ->
      {
         if(token.isRef() && token.val.equals(oname) && (cref == null || !"[".equals(cref.val)) &&
            (acceptFunc == null || acceptFunc.apply(sb.toString())))
         {
            sb.append(new ScriptIterator.Token(token.type, nname, token.length));
         }
         else if(token.type == ScriptIterator.Token.TEXT && token.val.contains("['" + oname + "']")
            && (acceptFunc == null || acceptFunc.apply(sb.toString())))
         {
            sb.append(new ScriptIterator.Token(token.type, token.val.replace(oname, nname),
               token.length));
         }
         else {
            sb.append(token);
         }
      };

      ScriptIterator iterator = new ScriptIterator(script);
      iterator.addScriptListener(listener);
      iterator.iterate();

      return sb.toString();
   }

   public static String renameScriptDepended(final String oname,
                                             final String nname, String script,
                                             Function<String, Boolean> acceptFunc)
   {
      if(script == null || script.length() == 0) {
         return script;
      }

      final StringBuilder sb = new StringBuilder();

      ScriptIterator.ScriptListener listener = (ScriptIterator.Token token,
                                                ScriptIterator.Token pref,
                                                ScriptIterator.Token cref) ->
      {
         if(token.val.contains(oname)) {
            sb.append(token.val.replace(oname, nname));
         }
         else {
            sb.append(token);
         }
      };

      ScriptIterator iterator = new ScriptIterator(script);
      iterator.addScriptListener(listener);
      iterator.iterate();

      return sb.toString();
   }

   public static String[] getDateParts(String dataType) {
      if(XSchema.DATE.equals(dataType)) {
         return new String[]{"Year", "QuarterOfYear", "MonthOfYear", "DayOfMonth", "DayOfWeek"};
      }
      else if(XSchema.TIME.equals(dataType)) {
         return new String[]{"HourOfDay", "MinuteOfHour", "SecondOfMinute"};
      }
      else if(XSchema.TIME_INSTANT.equals(dataType)) {
         return new String[]{"Year", "QuarterOfYear", "MonthOfYear", "DayOfMonth", "DayOfWeek",
            "HourOfDay", "MinuteOfHour", "SecondOfMinute"};
      }

      return null;
   }

   /**
    * Get the file path which not exist in the disk.
    */
   public static String getNonexistentFilePath(String filePath, int counter) {
      File file = new File(filePath);

      if(!file.exists()) {
         return filePath;
      }

      String parent = file.getParent();
      String fileName = file.getName();
      String fileExtension = fileName.substring(fileName.lastIndexOf('.'));
      fileName = fileName.substring(0, fileName.lastIndexOf('.'));

      StringBuilder builder = new StringBuilder();
      builder.append(parent);
      builder.append(File.separator);
      builder.append(fileName);
      builder.append("(");
      builder.append(counter);
      builder.append(")");
      builder.append(fileExtension);
      String nfilePath = builder.toString();
      file = new File(nfilePath);

      if(!file.exists()) {
         return nfilePath;
      }

      return getNonexistentFilePath(filePath, ++counter);
   }

   public static String getDatePartFunc(String key) {
      return datePartMap.get(key);
   }

   /**
    * Add "Copy of " to the name.
    */
   public static String getCopyName(String name) {
      return "Copy of " + name;
   }

   /**
    * Get the next index of the copyname.
    */
   public static String getNextCopyName(String originName, String lastName) {
      if(lastName.endsWith(originName)) {
         return lastName + "1";
      }

      int idx = lastName.lastIndexOf(originName) + originName.length();
      int index = Integer.parseInt(lastName.substring(idx)) + 1;
      return lastName.substring(0, idx) + index;
   }

   public static void drawWatermark(Graphics g, Dimension size) {
      Font ofont = g.getFont();
      g.setColor(Color.lightGray);
      String text = Catalog.getCatalog().getString("elastic.license.exhausted.watermark");

      for(int y = 100; y < size.height - 40; y += 180) {
         int x = (y / 4) + 80;
         g.setFont(WATER_FONT);
         g.drawString(text, x, y);
      }

      g.setColor(Color.black);
      g.setFont(ofont);
   }

   public static BufferedImage createWatermarkImage() {
      int width = 300;
      int height = 150;
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = image.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2d.setColor(Color.lightGray);
      g2d.setFont(WATER_FONT);
      String text = Catalog.getCatalog().getString("elastic.license.exhausted.watermark");
      FontMetrics fm = Common.getFontMetrics(WATER_FONT);
      int txtWidth = (int) Common.stringWidth(text, WATER_FONT, fm);
      float x =  (width - txtWidth) / 2;
      float y = height / 2;
      x = Math.max(x, 5);
      g2d.drawString(text, x, y);
      g2d.dispose();

      return image;
   }

   public static String BASE_MAX_ROW_KEY = "^_base_^";
   public static String SUB_MAX_ROW_KEY = "^_sub_^";
   public static String HINT_MAX_ROW_KEY = "^_hint_maxrow_^";

   private static final Font WATER_FONT = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.BOLD, 22);
   public static final String DATE_PART_COLUMN = "date_part_column";

   private static final int MAX_ROW_COUNT = 0;
   private static final int MAX_COLUMN_COUNT = 200;
   private static final int MAX_CELL_SIZE = 500;
   private static final HashMap<String, String> datePartMap = new HashMap<>();

   static {
      datePartMap.put("Year", "year");
      datePartMap.put("QuarterOfYear", "quarter");
      datePartMap.put("MonthOfYear", "month");
      datePartMap.put("DayOfMonth", "day");
      datePartMap.put("DayOfWeek", "weekday");
      datePartMap.put("HourOfDay", "hour");
      datePartMap.put("MinuteOfHour", "minute");
      datePartMap.put("SecondOfMinute", "second");
   }

   private static final Logger LOG = LoggerFactory.getLogger(Util.class);
}
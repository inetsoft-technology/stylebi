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
package inetsoft.report.io.viewsheet.excel;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.io.viewsheet.ExportUtil;
import inetsoft.report.painter.HeaderPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableDateComparisonFormat;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.CoreTool;
import inetsoft.web.viewsheet.service.VSExportService;
import org.apache.commons.lang.StringUtils;

import java.awt.*;
import java.io.*;
import java.util.regex.Pattern;

public class CSVUtil {
   private static final String UNSAFE_CHARS = "^(\\s*[@=+-]+)+";
   private static final Pattern IS_NUMBER =
      Pattern.compile("^\\s*[+-]?\\p{Sc}?[0-9,]+([.]\\d+)?([Ee][+-]?\\d+)?\\s*[%]?$");
   private static final Pattern JUST_UNSAFE_CHARS = Pattern.compile("^(\\s*[@=+-]*)$");

   public static boolean hasLargeDataTable(RuntimeViewsheet rvs) {
      if("true".equals(SreeEnv.getProperty("legacy.excel.largetable.export"))) {
         return false;
      }

      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();
      vs.updateCSSFormat("xlsx", null, box);

      try {
         for(Assembly assembly : vs.getAssemblies()) {
            if(assembly instanceof TableDataVSAssembly) {
               TableLens lens = box.getVSTableLens(assembly.getAbsoluteName(), false);

               // if the table has a lot of rows, use csv format to save memory
               if(lens != null && lens.moreRows(VSExportService.EXCEL_MAX_ROW)) {
                  return true;
               }
            }
         }
      }
      catch(Exception ignore) {
      }

      return false;
   }

   public static void writeTableDataAssembly(XTable table, OutputStream out, String delim)
         throws UnsupportedEncodingException
   {
      writeTableDataAssembly(table, out, delim, "\"", true);
   }

   public static void writeTableDataAssembly(XTable table, OutputStream out, String delim,
                                             String quote, boolean keepHeader)
         throws UnsupportedEncodingException
   {
      PrintWriter writer = null;
      boolean isUTF8 = SreeEnv.getProperty("text.encoding.utf8").equalsIgnoreCase("true");
      String encode = SreeEnv.getProperty("text.encoding.export", isUTF8 ? "UTF8" : null);

      if(encode != null) {
         writer = new PrintWriter(new OutputStreamWriter(out, encode));
      }
      else {
         writer = new PrintWriter(new OutputStreamWriter(out));
      }

      if(StringUtils.isEmpty(quote)) {
         quote = "\"";
      }

      int cols = table.getColCount();
      int[] widths = table instanceof VSTableLens ? ((VSTableLens) table).getColumnWidths() : null;
      TableDateComparisonFormat dcDatePartFormat = new TableDateComparisonFormat(null);

      for(int r = keepHeader ? 0 : table.getHeaderRowCount(); table.moreRows(r); r++) {
         for(int c = 0; c < cols; c++) {
            Object obj;
            boolean specialType = false;

            if(widths != null && c < widths.length && widths[c] == 0) {
               continue;
            }

            if(r < table.getHeaderRowCount()) {
               obj = table.getObject(r, c);
            }
            else {
               String fmt = ExportUtil.getCellFormat(table, r, c, false);
               obj = ExportUtil.getObject(table, r, c, fmt, false);
            }

            if(c > 0) {
               writer.print(delim);
            }

            if(obj instanceof DCMergeDatesCell) {
               obj = ((DCMergeDatesCell) obj).getFormatedOriginalDate();
               specialType = true;
            }
            else if(obj instanceof DCMergeDatePartFilter.MergePartCell) {
               obj = dcDatePartFormat.format(obj);
               specialType = true;
            }

            if(obj instanceof PresenterPainter) {
               PresenterPainter painter = (PresenterPainter) obj;

               if(painter.getPresenter() instanceof HeaderPresenter) {
                  if(painter.getObject() == null) {
                     obj = null;
                  }
               }

               specialType = true;
            }

            if(!specialType) {
               obj = table.getObject(r, c);
            }

            if(obj != null) {
               String str = CoreTool.toString(obj);
               str = cleanCsv(str);
               writer.print(quote + str.replaceAll("\"", "\"\"") + quote);
            }
         }

         writer.println();
      }

      writer.flush();
   }

   public static boolean needExport(VSAssembly assembly) {
      if(VSUtil.isTipView(assembly.getAbsoluteName(), assembly.getViewsheet())
         || VSUtil.isPopComponent(assembly.getAbsoluteName(),  assembly.getViewsheet()))
      {
         return false;
      }

      Viewsheet viewsheet = assembly.getViewsheet();

      if(assembly.isEmbedded() && (assembly instanceof AnnotationVSAssembly ||
         assembly  instanceof AnnotationRectangleVSAssembly ||
         assembly  instanceof AnnotationLineVSAssembly))
      {
         return false;
      }

      if(assembly.getVSAssemblyInfo() != null &&
         viewsheet.isVisible(assembly, AbstractSheet.SHEET_RUNTIME_MODE))
      {
         Dimension psize = viewsheet.getPixelSize(assembly.getVSAssemblyInfo());

         if((psize.width <= 0 || psize.height <= 0) && !(assembly instanceof AnnotationVSAssembly))
         {
            return false;
         }

         if(assembly.getAssemblyType() == Viewsheet.SELECTION_LIST_ASSET ||
            assembly.getAssemblyType() == Viewsheet.TIME_SLIDER_ASSET)
         {
            Assembly container = assembly.getContainer();

            if(container instanceof CurrentSelectionVSAssembly) {
               CurrentSelectionVSAssembly csAssembly =
                       (CurrentSelectionVSAssembly) container;
               int gap = assembly.getPixelOffset().y - csAssembly.getPixelOffset().y;
               return gap > 0 && gap < csAssembly.getPixelSize().height;
            }
         }

         return true;
      }

      return false;
   }

   /**
    * Removes unsafe characters from input string to mitigate CSV Injection
    * @param str Input string to clean
    * @return A cleaned string for CSV output
    */
   public static String cleanCsv(String str) {
      return IS_NUMBER.matcher(str).matches() || JUST_UNSAFE_CHARS.matcher(str).matches()
         ? str : str.replaceFirst(UNSAFE_CHARS, "");
   }
}

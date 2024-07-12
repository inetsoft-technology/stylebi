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

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Util;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;

import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Common utility methods for excel export.
 *
 * @version 8.5, 7/19/2006
 * @author InetSoft Technology Corp
 */
public class ExcelVSUtil {
   /**
    * Excel width factor.
    */
   public static final double EXCEL_PIXEL_WIDTH_FACTOR = 32;

   /**
    * Excel height factor.
    */
   public static final double EXCEL_PIXEL_HEIGHT_FACTOR = 15;

   /**
    * The header cell's horizontal border type.
    */
   public static final int CELL_HEADER = 1;

   /**
    * The content cell's horizontal border type.
    */
   public static final int CELL_CONTENT = 2;

   /**
    * The tail cell's horizontal border type.
    */
   public static final int CELL_TAIL = 4;

   /**
    * Default table column width.
    */
   public static final int DEFAULT_COLWIDTH = 70;

   /**
    * Default table row width.
    */
   public static final int DEFAULT_ROWWIDTH = 18;

   /**
    * Excel line no border.
    */
   public static final int EXCEL_NO_BORDER = -1;

   /**
    * Excel line solid border.
    */
   public static final int EXCEL_SOLID_BORDER = 0;

   /**
    * Fix the link, if a '?' follows the host[:port], there should be a '/'
    * before it.
    *
    * @param link link string to fix
    * @param type the type of the link
    * @return updated url
    */
   public static String fixLink(String link, int type) {
      if(type != inetsoft.report.Hyperlink.WEB_LINK) {
         return link;
      }

      int pos = link.indexOf("?");

      if(pos == -1 || pos > 0 && link.charAt(pos - 1) == '/') {
         return link;
      }

      URL url = null;

      try {
         url = new URL(link);
      }
      catch(Exception e) {
         return link;
      }

      String host = url.getHost();
      int port = url.getPort();

      if(port != -1) {
         host += ":" + port;
      }

      int hpos = link.indexOf(host);

      if(hpos + host.length() >= link.length() ||
         link.charAt(hpos + host.length()) != '?')
      {
         return link;
      }

      StringWriter writer = new StringWriter();
      writer.append(link.substring(0, pos));
      writer.append("/");
      writer.append(link.substring(pos));
      return writer.toString();
   }

   /**
    * Get the string URL from the given hyperlink ref.
    * @param hyperlink the link info
    * @return compiled url
    */
   public static String getURL(inetsoft.report.Hyperlink.Ref hyperlink) {
      String url = Util.createURL(hyperlink);
      return ExcelVSUtil.fixLink(url, hyperlink.getLinkType());
   }

   /**
    * Calculate the pixel column widths.
    */
   public static int[] calculateColumnWidths(Viewsheet vs,
      TableDataVSAssemblyInfo info, VSTableLens lens, boolean isFillColumns,
      boolean matchLayout, boolean needDistributeWidth)
   {
      int totalWidth = 0;
      int totalPixelW = info.getPixelSize().width;
      int lensColumnCount = lens == null ? 0 : lens.getColCount();
      int[] ws = new int[lensColumnCount];
      int[] widths = lens == null ? new int[0] : lens.getColumnWidths();
      int firstTrunkCol = -1;

      // get user set column widths
      for(int i = 0; i < ws.length; i++) {
         double w = info.getColumnWidth2(i, lens);

         if(Double.isNaN(w) && widths != null && i < widths.length) {
            w = widths[i];

            // @by ankitmathur, 4-09-2015, track the column which is truncated
            // due to the size of the Assembly.
            if(firstTrunkCol < 0 && totalWidth + (int) w > totalPixelW) {
               firstTrunkCol = i - 1;
            }
         }
         else if(Double.isNaN(w)) {
            w = DEFAULT_COLWIDTH;
         }

         w = lens.getColumnWidthWithPadding(w, i);
         ws[i] = (int) w;
         totalWidth += w;
      }

      // if totalwidth expand the table pixel width, reset the cell width
      // fixed bug #29975 :In fact, it didn't expand totalPixelW when export html.
      if(!matchLayout && totalWidth > totalPixelW) {
         totalWidth = 0;
      }

      // distribute width
      if(totalWidth > 0) {
         if(!needDistributeWidth) {
            int[] nws = null;

            if(totalWidth < totalPixelW) {
               int remainWidth = totalPixelW - totalWidth;
               // For bug1418289518567, add the remaining width to the last
               // column for Tables as well as Crosstabs.
               if((info instanceof TableVSAssemblyInfo ||
                  info instanceof CalcTableVSAssemblyInfo ||
                  // @by yanie: bug1416909636463
                  // Crosstab in viewsheet is changed to be fill-columns, modify
                  // export logic accordingly
                  info instanceof CrosstabVSAssemblyInfo) && !info.isShrink())
               {
                  // @by ankitmathur, 4-09-2015, totalWidth represents the size
                  // of the table assembly. If the table has horizontal scroll
                  // bars, we need to set the width of the truncated column
                  // (last visible column within the assembly bounds) to the
                  // width of the next Column. If the table does not contain
                  // scrollbars, then the last column will need to expand to
                  // the remaining assembly width.
                  if(firstTrunkCol >= 0) {
                     int trunkColSize = ws[firstTrunkCol];
                     ws[firstTrunkCol] = AssetUtil.defw;

                     if(ws[ws.length - 1] == DEFAULT_COLWIDTH) {
                        ws[ws.length - 1] += trunkColSize;
                     }
                  }
                  // don't expand if set explicitly to 0
                  else if(ws[ws.length - 1] > 0){
                     ws[ws.length - 1] = ws[ws.length - 1] + remainWidth;
                  }
               }
               else if(!info.isShrink()) {
                  List<Integer> remainColWidth = new ArrayList<>();

                  for(int i = totalPixelW / AssetUtil.defw; i >= 0; i--) {
                     if(remainWidth > 0 &&
                        remainWidth >= AssetUtil.defw)
                     {
                        remainColWidth.add(AssetUtil.defw);
                        remainWidth -= AssetUtil.defw;
                     }
                     else if(remainWidth > 0 &&
                        remainWidth < AssetUtil.defw)
                     {
                        remainColWidth.add(remainWidth);
                        break;
                     }
                  }

                  nws = new int[ws.length + remainColWidth.size()];
                  System.arraycopy(ws, 0, nws, 0, ws.length);

                  for(int i = ws.length; i < nws.length; i++) {
                     nws[i] = remainColWidth.get(i - ws.length);
                  }
               }
            }
            // Account for the remaining columns only if "Match Layout"
            // export option is selected.
            else if(totalWidth > totalPixelW && matchLayout) {
               java.util.List<Integer> wsList = new ArrayList<>();
               int temp = 0;

               for(int width : ws) {
                  temp += width;

                  if(temp > totalPixelW) {
                     wsList.add(width - (temp - totalPixelW));
                     break;
                  }

                  wsList.add(width);
               }

               nws = new int[wsList.size()];

               for(int i = 0; i < wsList.size(); i++) {
                  nws[i] = wsList.get(i);
               }
            }

            // For bug1418289518567, For Crosstabs we need to accommodate the
            // for the "extra" columns determined by the Viewsheet grid. Since
            // the width of the last column of the Crosstab will have been
            // expanded to cover this area, we can set the width value to 0 for the
            // extra columns.
            // 1-06-2015, adding logic for Freehand tables as well because
            // the change for bug1418832574870, now makes Freehand tables
            // behave the same as Crosstabs.
            if((info instanceof CrosstabVSAssemblyInfo ||
               info instanceof CalcTableVSAssemblyInfo) &&
               nws == null && totalPixelW > totalWidth)
            {
               nws = new int[ws.length + ((totalPixelW - totalWidth) / AssetUtil.defw)];
               System.arraycopy(ws, 0, nws, 0, ws.length);

               for(int i = ws.length; i < nws.length; i++) {
                  nws[i] = 0;
               }
            }

            return nws != null ? nws : ws;
         }

         int consumed = 0;

         for(int i = 0; i < ws.length; i++) {
            ws[i] = ws[i] * totalPixelW / totalWidth;
            consumed += ws[i];
         }

         // rounding error
         ws[ws.length - 1] += totalPixelW - consumed;
      }
      // truncate to fit assembly width
      else {
         for(int i = 0; i < ws.length; i++) {
            double w = info.getColumnWidth2(i, lens);

            if(Double.isNaN(w) && widths != null && i < widths.length) {
               w = widths[i];
            }
            else if(Double.isNaN(w)) {
               w = DEFAULT_COLWIDTH;
            }

            w = lens.getColumnWidthWithPadding(w, i);

            if(totalWidth + w > totalPixelW) {
               w = totalPixelW - totalWidth;
               ws[i] = (int) w;
               break;
            }
            else {
               ws[i] = (int) w;
               totalWidth += w;
            }
         }
      }

      return ws;
   }

   /**
    * Calculate the pixel column widths.
    */
   public static int[] calculateRowHeights(Viewsheet vs,
      TableDataVSAssemblyInfo info, VSTableLens lens,
      boolean matchLayout, boolean needDistributeWidth)
   {
      int totalHeight = 0;
      int totalPixelH = info.getPixelSize().height;
      int lensRowCount = lens == null ? 0 : lens.getRowCount();
      int[] hs = new int[lensRowCount];
      int[] heights = lens == null ? new int[0] : lens.getRowHeights();

      // get user set column widths
      for(int i = 0; i < hs.length; i++) {
         int h = 0;

         if(i >= heights.length) {
            h = AssetUtil.defh;
         }
         else {
            h = lens.getWrappedHeight(i, true);
         }

         if(Double.isNaN(h) && heights != null && i < heights.length) {
            h = heights[i];
         }
         else if(Double.isNaN(h)) {
            h = DEFAULT_ROWWIDTH;
         }

         h = (int) lens.getRowHeightWithPadding(h, i);
         hs[i] = h;
         totalHeight += h;
      }

      // if totalwidth expand the table pixel width, reset the cell width
      if(!matchLayout && totalHeight > totalPixelH) {
         totalHeight = 0;
      }

      return hs;
   }
}

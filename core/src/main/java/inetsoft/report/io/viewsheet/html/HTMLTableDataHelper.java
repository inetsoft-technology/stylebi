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
package inetsoft.report.io.viewsheet.html;

import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

/**
 * Table helper used when exporting to HTML.
 */
public class HTMLTableDataHelper {
   /**
    * Creates a new instance of <tt>HTMLTableDataHelper</tt>.
    */
   public HTMLTableDataHelper() {
   }

   /**
    * Creates a new instance of <tt>HTMLTableDataHelper</tt>.
    *
    * @param helper the coordinate helper.
    * @param vs     the viewsheet being exported.
    */
   public HTMLTableDataHelper(HTMLCoordinateHelper helper, Viewsheet vs) {
      this.vHelper = helper;
      this.vs = vs;
   }

   /**
    * Creates a new instance of <tt>HTMLTableDataHelper</tt>.
    *
    * @param helper   the coordinate helper.
    * @param vs       the viewsheet being exported.
    * @param assembly the assembly being written.
    */
   public HTMLTableDataHelper(HTMLCoordinateHelper helper, Viewsheet vs, VSAssembly assembly) {
      this(helper, vs);
      this.assembly = assembly;
      initAnnotation();
   }

   /**
    * The table card's inset, as the exporter resolves it.
    */
   public void setCardInset(Insets inset) {
      this.cardInset = inset == null ? new Insets(0, 0, 0, 0) : inset;
   }

   protected boolean hasCardInset() {
      return cardInset.top != 0 || cardInset.left != 0 || cardInset.bottom != 0 ||
         cardInset.right != 0;
   }

   protected int getGridWidth(Rectangle2D bounds) {
      return Math.max(0, (int) bounds.getWidth() - cardInset.left - cardInset.right);
   }

   protected int getGridHeight(Rectangle2D bounds) {
      return Math.max(0, (int) bounds.getHeight() - cardInset.top - cardInset.bottom);
   }

   /**
    * The box inside the card that holds the title and the data.
    */
   protected String getGridBoxStart(Rectangle2D bounds) {
      return "<div style='position:absolute;left:" + cardInset.left + "px;top:" +
         cardInset.top + "px;width:" + getGridWidth(bounds) + "px;height:" +
         getGridHeight(bounds) + "px'>";
   }

   protected Insets cardInset = new Insets(0, 0, 0, 0);

   protected void fixShrinkTableBounds(TableDataVSAssemblyInfo info, Rectangle2D bounds) {
      if(info.isShrink()) {
         int titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
                      ((TitledVSAssemblyInfo) info).getTitleHeight();
         // the card wraps the content plus its inset
         int height = totalHeight + titleH + cardInset.top + cardInset.bottom;
         Dimension d = new Dimension(totalWidth + cardInset.left + cardInset.right, height);
         d = vHelper.getOutputSize(d);

         bounds.setRect(bounds.getX(), bounds.getY(), Math.min(bounds.getWidth(), d.getWidth()),
                        Math.min(bounds.getHeight(), d.getHeight()));
      }
   }

   /**
    * Init the annotation map with pattern "rowIdx_colIdx --> value".
    */
   private void initAnnotation() {
      if(annotationMap != null || assembly == null) {
         return;
      }

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();

      if(info instanceof BaseAnnotationVSAssemblyInfo) {
         List<String> annotations =
            ((BaseAnnotationVSAssemblyInfo) info).getAnnotations();
         Viewsheet vs = VSUtil.getTopViewsheet(this.vs);

         if(vs == null) {
            return;
         }

         annotationMap = new HashMap<>();

         for(String annotation : annotations) {
            AnnotationVSAssembly ass = (AnnotationVSAssembly) vs.getAssembly(annotation);

            if(ass == null) {
               continue;
            }

            AnnotationVSAssemblyInfo aInfo = (AnnotationVSAssemblyInfo) ass.getVSAssemblyInfo();

            if(aInfo.getType() != AnnotationVSAssemblyInfo.DATA) {
               continue;
            }

            if(!aInfo.isVisible()) {
               continue;
            }

            String key = aInfo.getRow() + "_" + aInfo.getCol();
            List<VSAssemblyInfo> list = annotationMap.get(key);

            if(list == null) {
               list = new ArrayList<>();
               annotationMap.put(key, list);
            }

            list.add(aInfo);
         }
      }
   }

   /**
    * Get the specified cell's annotation.
    */
   private List<VSAssemblyInfo> getAnnotations(int row, int col) {
      return annotationMap == null ? null : annotationMap.get(row + "_" + col);
   }

   protected void updateAnnotations(int r, int c, VSCompositeFormat cfmt) {
      List<VSAssemblyInfo> annos = getAnnotations(r, c);

      if(annos != null) {
         VSAssemblyInfo info = assembly.getVSAssemblyInfo();
         int titleH = 0;

         if(info instanceof TitledVSAssemblyInfo) {
            titleH = !((TitledVSAssemblyInfo) info).isTitleVisible() ? 0 :
                     ((TitledVSAssemblyInfo) info).getTitleHeight();
         }

         float vBorderWidth = Common.getLineWidth(cfmt.getBorders().bottom) +
            Common.getLineWidth(cfmt.getBorders().top);
         float hBorderWidth = Common.getLineWidth(cfmt.getBorders().left) +
            Common.getLineWidth(cfmt.getBorders().right);
         Point pos = vs.getPixelPosition(info);
         int x = (int) (pos.x + cardInset.left + (hBorderWidth * (c + 1)));
         int y = (int) (titleH + pos.y + cardInset.top + (vBorderWidth * (r + 1)));

         for(int i = 0; i < c; i++) {
            x += columnWidths[i];
         }

         for(int i = 0; i < r; i++) {
            y += rowHeights[i];
         }

         int width = columnWidths[c];
         int height = rowHeights[r];
         int nx = x + width / 2;
         int ny = y + height / 2;

         for(VSAssemblyInfo anno : annos) {
            Point npos = AnnotationVSUtil.getNewPos(vs, anno, nx, ny);

            if(npos == null) {
               continue;
            }

            AnnotationVSUtil.refreshAnnoPosition(vs, anno, npos);
         }
      }
   }

   protected Viewsheet vs;
   protected VSAssembly assembly;
   protected HTMLCoordinateHelper vHelper = null;
   protected int[] rowHeights = null;
   protected int[] columnWidths = null;
   protected int totalWidth = 0; // table shrink total width.
   protected int totalHeight = 0; // table total data height.
   private Map<String, List<VSAssemblyInfo>> annotationMap;
   private static final Logger LOG = LoggerFactory.getLogger(HTMLTableDataHelper.class);
}

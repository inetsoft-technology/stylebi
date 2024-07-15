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
package inetsoft.report.io.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Bounds;
import inetsoft.report.internal.Common;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;

/**
 * The class is used to calculate the bounds and drawing.
 *
 * @version 10.1, 13/01/2009
 * @author InetSoft Technology Corp
 */
public class CoordinateHelper {
   /**
    * Identify to get title row bounds.
    */
   public static final String TITLE = "title_bounds";
   /**
    * Identify to get data row bounds.
    */
   public static final String DATA = "data_bounds";
   /**
    * Identify to get bounds of a object.
    */
   public static final String ALL = "object_bounds";

   /**
    * Set the viewsheet.
    * @param sheet the specified viewsheet.
    */
   public void setViewsheet(Viewsheet sheet) {
      this.vs = sheet;
   }

   /**
    * Get the bounds of the assembly when export ppt/pdf.
    * @param assembly the specified assembly to get bounds.
    * @param type identify to get the bounds of title or data or the object
    *  bounds.
    * @return the assembly's exporting bounds.
    */
   public Rectangle2D getBounds(AbstractVSAssembly assembly, String type) {
      return getBounds(assembly, type, false, null);
   }

   /**
    * Get the bounds of the assembly when export ppt/pdf.
    * @param assembly the specified assembly to get bounds.
    * @param type identify to get the bounds of title or data or the object
    *  bounds.
    * @return the assembly's exporting bounds.
    */
   public Rectangle2D getBounds(AbstractVSAssembly assembly, String type,
      Dimension size)
   {
      return getBounds(assembly, type, false, size);
   }

   /**
    * Get the bounds of the assembly when export ppt/pdf.
    * @param assembly the specified assembly to get bounds.
    * @param type identify to get the bounds of title or data or the object
    *  bounds.
    * @param prepareMode identify is in prepare bounds mode, because if
    *  in prepareBounds, we should get the really title bounds to layout, but
    *  when in export title in container, the title should be make sure in
    *  bounds, and if return null, means the title should not print out.
    * @return the assembly's exporting bounds.
    */
   public Rectangle2D getBounds(AbstractVSAssembly assembly, String type,
      boolean prepareMode, Dimension size)
   {
      return getBounds(assembly, type, prepareMode, false, size);
   }

   /**
    * Get the bounds of the assembly when export ppt/pdf.
    * @param assembly the specified assembly to get bounds.
    * @param type identify to get the bounds of title or data or the object
    *  bounds.
    * @param prepareMode identify is in prepare bounds mode, because if
    *  in prepareBounds, we should get the really title bounds to layout, but
    *  when in export title in container, the title should be make sure in
    *  bounds, and if return null, means the title should not print out.
    * @param expand identify get object bounds to expand assembly, if true, then
    *  return the really object bounds, otherwise return the bounds in bound of
    *  container.
    * @return the assembly's exporting bounds.
    */
   public Rectangle2D getBounds(AbstractVSAssembly assembly, String type,
      boolean prepareMode, boolean expand, Dimension dim)
   {
      CurrentSelectionVSAssembly cassembly = null;

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         cassembly = (CurrentSelectionVSAssembly) assembly.getContainer();
      }

      if(cassembly == null) {
         Point pos = vs.getPixelPosition(assembly.getInfo());
         Dimension size = getAssemblySize(assembly, dim);
         size = new Dimension(size.width, AssetUtil.defh);
         VSAssemblyInfo assemblyInfo = assembly.getVSAssemblyInfo();
         int titleHeight = 0;

         if(assemblyInfo instanceof TitledVSAssemblyInfo) {
            titleHeight = ((TitledVSAssemblyInfo) assemblyInfo).isTitleVisible() ?
               ((TitledVSAssemblyInfo) assemblyInfo).getTitleHeight() : 0;

            if(TITLE.equals(type)) {
               size.height = titleHeight;
            }
         }

         if(TITLE.equals(type)) {
            return createBounds(pos, size);
         }
         else if(DATA.equals(type)) {
            pos = new Point(pos.x, pos.y + titleHeight);
            return createBounds(pos, size);
         }

         return getBounds(assembly.getVSAssemblyInfo());
      }

      Viewsheet vs = assembly.getViewsheet();
      Rectangle2D cbounds = getBounds(cassembly.getVSAssemblyInfo(), true);
      CurrentSelectionVSAssemblyInfo cinfo = (CurrentSelectionVSAssemblyInfo)
         cassembly.getVSAssemblyInfo();
      // current selection title height
      float titleH = cinfo.getTitleHeight();
      // children title height
      float cTitleH = (float) AssetUtil.defh;

      if(assembly.getVSAssemblyInfo() instanceof TitledVSAssemblyInfo) {
         cTitleH = ((TitledVSAssemblyInfo) assembly.getVSAssemblyInfo()).getTitleHeight();
      }

      String[] assemblies = cassembly.getAssemblies();
      int outN = !cassembly.isShowCurrentSelection() ? 0 :
         cassembly.getOutSelectionTitles().length;
      float currentY = (float) (cbounds.getY() + titleH + outN * AssetUtil.defh);

      for(int i = 0; i < assemblies.length; i++) {
         if(assembly.getName().equals(assemblies[i])) {
            break;
         }

         VSAssembly ass = (VSAssembly) vs.getAssembly(assemblies[i]);

         if(ass == null) {
            continue;
         }

         currentY += getAssemblySize(ass, dim).height;
      }

      Rectangle2D bounds = null;

      // data should be really bounds, and the object bounds should in
      // container bounds, title bounds is specifial processed
      if(TITLE.equals(type)) {
         bounds = new Rectangle2D.Double(cbounds.getX(), currentY,
            cbounds.getWidth(), cTitleH);

         // if not prepare, and bounds can not print title, return null,
         // otherwise if not prepare, to fix the bounds in bounds
         if(!prepareMode && (bounds.getY() + bounds.getHeight() / 2 >
             cbounds.getY() + cbounds.getHeight()))
         {
            return null;
         }
         else if(prepareMode) {
            return scaleBounds(bounds);
         }
      }
      else if(DATA.equals(type)) {
         return scaleBounds(new Rectangle2D.Double(
                               cbounds.getX(), currentY + cTitleH, cbounds.getWidth(),
                               AssetUtil.defh));
      }
      else {
         bounds = new Rectangle2D.Double(cbounds.getX(), currentY,
            cbounds.getWidth(), getAssemblySize(assembly, dim).height);

         if(expand) {
            return scaleBounds(bounds);
         }
      }

      double bx = Math.max(cbounds.getX(), bounds.getX());
      double by = Math.min(cbounds.getY() + cbounds.getHeight(), bounds.getY());
      double bw = Math.min(bounds.getWidth(),
                           cbounds.getWidth() - (bx - cbounds.getX()));
      bw = Math.max(0, bw);
      double bh = Math.min(bounds.getHeight(),
                           cbounds.getHeight() - (by - cbounds.getY()));
      bh = Math.max(0, bh);
      return scaleBounds(new Rectangle2D.Double(bx, by, bw, bh));
   }

   /**
    * Get the bounds of the pdf helper.
    * @param info the specified VSAssemblyInfo.
    */
   public Rectangle2D getBounds(VSAssemblyInfo info) {
      return getBounds(info, false);
   }

   /**
    * Get the bounds of the pdf helper.
    * @param info the specified VSAssemblyInfo.
    */
   public Rectangle2D getBounds(VSAssemblyInfo info, boolean ignoreScale) {
      Point pos = ignoreScale ? vs.getPixelPosition(info) : getOutputPosition(vs.getPixelPosition(info));
      Dimension size = ignoreScale ? vs.getPixelSize(info) : getOutputSize(vs.getPixelSize(info));

      if(info instanceof LineVSAssemblyInfo) {
         Object[] newInfo = VSUtil.refreshLineInfo(
            vs, (LineVSAssemblyInfo) info);
         pos = ignoreScale ? (Point) newInfo[0] : getOutputPosition((Point) newInfo[0]);
         size = ignoreScale ? (Dimension) newInfo[1] : getOutputSize((Dimension) newInfo[1]);
      }
      else if(info instanceof AnnotationRectangleVSAssemblyInfo) {
         Viewsheet vs0 = VSUtil.getTopViewsheet(vs);
         pos = ignoreScale ? vs0.getPixelPosition(info) : getOutputPosition(vs0.getPixelPosition(info));
         size = ignoreScale ?vs0.getPixelSize(info) : getOutputSize(vs0.getPixelSize(info));
      }
      else if(info instanceof TextVSAssemblyInfo) {
         fixTextSize(size, pos, (TextVSAssemblyInfo) info);
      }

      return new Rectangle2D.Double(pos.x, pos.y, size.width, size.height);
   }

   public static void fixTextSize(Dimension size, Point pos, TextVSAssemblyInfo tinfo) {
      if(tinfo.isAutoSize()) {
         VSCompositeFormat format = tinfo.getFormat();
         Font font = format.getFont();
         FontMetrics fm = Common.getFontMetrics(tinfo.getFormat().getFont());
         int width = (int) Common.stringWidth(tinfo.getText(), font, fm);

         // if only have smaller pixel size, do not enlarge text.
         if(size.width > 0) {
            int h = Math.max(20, width * 20 / size.width);
            size.height = h;
         }

         fixTextHeight(size, pos, tinfo);
      }
   }

   private static void fixTextHeight(Dimension size, Point pos, TextVSAssemblyInfo tinfo) {
      if(tinfo.isAutoSize()) {
         VSCompositeFormat format = tinfo.getFormat();
         Font font = format.getFont();

         int align = format.getAlignment() == StyleConstants.NONE ?
            StyleConstants.H_LEFT | StyleConstants.V_TOP : format.getAlignment();

         if((align & StyleConstants.V_ALIGN_MASK) == 0) {
            align |= StyleConstants.V_TOP;
         }

         Bounds bounds = new Bounds(pos.x, pos.y, size.width, size.height);
         float height = Common.getStringHeight(font, tinfo.getValue() + "", bounds, align,
            format.isWrapping(), 0, 0);

         if(height > size.height) {
            size.height = (int) Math.ceil(height);
         }
      }
   }

   /**
    * Get the bounds of the pdf helper.
    * @param position the specified Point.
    * @param size the specifed Dimension.
    */
   public Rectangle2D getBounds(Point position, Dimension size) {
      return scaleBounds(getBounds(position, size, true));
   }

   /**
    * Get the bounds of the pdf helper.
    * @param position the specified Point.
    * @param size the specifed Dimension.
    */
   public Rectangle2D createBounds(Point position, Dimension size) {
    return scaleBounds(createBounds(position, size, true));
   }

   /**
    * Get the bounds of the pdf helper.
    * @param position the specified Point.
    * @param size the specifed Dimension.
    */
   public Rectangle2D createBounds(Point position, Dimension size, boolean ignoreScale) {
      Point pixelPos1 = position;
      Point pixelPos2 = new Point(position.x + size.width, position.y + size.height);
      Point p1 = ignoreScale ? pixelPos1 : getOutputPosition(pixelPos1);
      Dimension width = new Dimension(pixelPos2.x - pixelPos1.x,
                                      pixelPos2.y - pixelPos1.y);
      width = ignoreScale ? width : getOutputSize(width);

      return new Rectangle2D.Double(p1.x, p1.y, width.width, width.height);
   }


   /**
    * Get the bounds of the pdf helper.
    * @param position the specified Point.
    * @param size the specifed Dimension.
    */
   public Rectangle2D getBounds(Point position, Dimension size,
                                boolean ignoreScale) {
      Point point1 = new Point(position.x + size.width,
            position.y + size.height);
      Point pixelPos1 = vs.getPixelPosition(position);
      Point pixelPos2 = vs.getPixelPosition(point1);
      Point p1 = ignoreScale ? pixelPos1 : getOutputPosition(pixelPos1);
      Dimension width = new Dimension(pixelPos2.x - pixelPos1.x,
                                      pixelPos2.y - pixelPos1.y);
      width = ignoreScale ? width : getOutputSize(width);

      return new Rectangle2D.Double(p1.x, p1.y, width.width, width.height);
   }

   /**
    * Transfer the given bounds according to scale
    */
   public Rectangle2D scaleBounds(Rectangle2D bounds) {
      double scale = getScale();
      return new Rectangle2D.Double(bounds.getX() * scale,
         bounds.getY() * scale, bounds.getWidth() * scale,
         bounds.getHeight() * scale);
   }

   /**
    * Transfer the given length according to scale
    */
   public double scaleLength(double length1) {
      return length1 * getScale();
   }

   /**
    * Get the position on the output media.
    */
   public Point getOutputPosition(Point pos) {
      return pos;
   }

   /**
    * Get the size on the output media.
    */
   public Dimension getOutputSize(Dimension size) {
      return size;
   }

   /**
    * Get pixel to point scale.
    */
   public double getScale() {
      return 1.0;
   }

   /**
    * Get all the bounds by giving the specific position and size.
    * @param assembly the target assembly to process bounds.
    * @param totalHeight totalHeight of all cells.
    * @param ncol number of columns.
    * @return the bounds list with position and size.
    */
   public List prepareBounds(AbstractVSAssembly assembly, int[] totalHeight,
                             int ncol)
   {
      return prepareBounds(assembly, totalHeight, ncol, null);
   }

   /**
    * Get all the bounds by giving the specific position and size.
    * @param assembly the target assembly to process bounds.
    * @param totalHeight totalHeight of all cells.
    * @param ncol number of columns.
    * @return the bounds list with position and size.
    */
   public List prepareBounds(AbstractVSAssembly assembly, int[] totalHeight,
                             int ncol, Dimension size)
   {
      List boundsList = new Vector();
      int showType = -1;

      if(assembly instanceof SelectionListVSAssembly) {
         showType = ((SelectionListVSAssembly) assembly).getShowType();
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         showType = ((SelectionTreeVSAssembly) assembly).getShowType();
      }

      if(showType == -1) {
         return boundsList;
      }

      CurrentSelectionVSAssembly cassembly = null;

      if(assembly.getContainer() instanceof CurrentSelectionVSAssembly) {
         cassembly = (CurrentSelectionVSAssembly) assembly.getContainer();
      }

      Rectangle2D totalBounds = getBounds(assembly, ALL, true, size);
      totalHeight[0] = (int) totalBounds.getHeight();

      if(showType != SelectionVSAssemblyInfo.LIST_SHOW_TYPE) {
         boundsList.add(totalBounds);
         return boundsList;
      }

      if(totalBounds.getWidth() <= 0 || totalBounds.getHeight() <= 0) {
         return boundsList;
      }

      // process title bounds
      Rectangle2D titleBounds = getBounds(assembly, TITLE, true, size);

      if(titleBounds.getY() + titleBounds.getHeight() / 2 >
         totalBounds.getY() + totalBounds.getHeight())
      {
         return boundsList;
      }

      boundsList.add(titleBounds);

      // process data bounds
      Rectangle2D dataBounds = getBounds(assembly, DATA, true, size);

      if(dataBounds.getY() + dataBounds.getHeight() / 2 >
         totalBounds.getY() + totalBounds.getHeight())
      {
         return boundsList;
      }

      float currentY = (float) dataBounds.getY();
      float endY = (float) (totalBounds.getY() + totalBounds.getHeight());
      double startX = titleBounds.getX() + 1;
      double cellw = titleBounds.getWidth() / ncol - 1;

      List<Double> rowHeights = new ArrayList<>();
      SelectionBaseVSAssemblyInfo info =
         (SelectionBaseVSAssemblyInfo) assembly.getVSAssemblyInfo();

      if(info instanceof SelectionListVSAssemblyInfo) {
         SelectionListVSAssemblyInfo listInfo = (SelectionListVSAssemblyInfo) info;
         rowHeights = listInfo.getRowHeights();
      }
      else  {
         SelectionTreeVSAssemblyInfo treeInfo = (SelectionTreeVSAssemblyInfo) info;
         rowHeights = treeInfo.getRowHeights();
      }

      // process all avaliable bounds
      OUTER:
      for(int i = 0; i < rowHeights.size(); i++) {
         double rowHeight = scaleLength(rowHeights.get(i));

         for(int j = 0; j < ncol; j++) {
            Rectangle2D bounds = new Rectangle2D.Double(startX + j * cellw,
               currentY, cellw, rowHeight);

            if(bounds.getY() + bounds.getHeight() / 2 > endY) {
               break OUTER;
            }

            boundsList.add(bounds);
         }

         currentY += rowHeight;
      }

      return boundsList;
   }

   public int getTitleHeightOffset(VSAssemblyInfo info) {
      return 5;
   }

   /**
    * Get table lens row/column count.
    */
   public static Dimension getLensSize(TableLens lens, boolean matchLayout) {
      if(lens == null) {
         return null;
      }

      int rowCnt = lens.getRowCount();

      if(lens instanceof VSTableLens) {
         rowCnt = 0;

         for(int i = 0; i < lens.getRowCount(); i++) {
            rowCnt += ((VSTableLens) lens).getLineCount(i, matchLayout);
         }
      }

      return new Dimension(lens.getColCount(), rowCnt);
   }

   /**
    * Get viewsheet assembly size.
    */
   public static Dimension getAssemblySize(VSAssembly assembly, Dimension size) {
      if(assembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssembly slider = (TimeSliderVSAssembly) assembly;
         TimeSliderVSAssemblyInfo info = (TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo();

         if(info.isTitleVisible()) {
            int titleh = info.getTitleHeight();
            Dimension psize = assembly.getPixelSize();

            if(info.isHidden()) {
               return new Dimension(psize.width, titleh);
            }

            return new Dimension(psize.width, psize.height + titleh);
         }
      }

      if(assembly instanceof SelectionListVSAssembly &&
         ((SelectionListVSAssembly)assembly).getShowType() ==
         SelectionListVSAssemblyInfo.DROPDOWN_SHOW_TYPE)
      {
         Dimension psize = assembly.getPixelSize();
         SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) assembly.getInfo() ;
         return new Dimension(psize.width, sinfo.getTitleHeight());
      }

      if(assembly instanceof SelectionListVSAssembly) {
         return assembly.getPixelSize();
      }

      if(!(assembly instanceof TableDataVSAssembly)) {
         return assembly.getPixelSize();
      }

      TableDataVSAssemblyInfo table = (TableDataVSAssemblyInfo) assembly.getInfo() ;

      return getTableDataSize(table, size);
   }

   /**
    * Get viewsheet assembly info size.
    */
   public static Dimension getAssemblySize(VSAssemblyInfo info, Dimension size) {
      if(!(info instanceof TableDataVSAssemblyInfo)) {
         return info.getPixelSize();
      }

      return getTableDataSize((TableDataVSAssemblyInfo) info, size);
   }

   /**
    * Get viewsheet table size.
    */
   private static Dimension getTableDataSize(TableDataVSAssemblyInfo info,
      Dimension size)
   {
      if(!info.isShrink() || size == null || info instanceof TableVSAssemblyInfo) {
         return info.getPixelSize();
      }

      Dimension isize = info.getPixelSize();
      //maybe need caculate title/header count
      int width = Math.min(isize.width / AssetUtil.defw, size.width);
      int height = Math.min(isize.height / AssetUtil.defh, size.height + 1);

      return new Dimension(width, height);
   }

   protected Viewsheet vs;
}

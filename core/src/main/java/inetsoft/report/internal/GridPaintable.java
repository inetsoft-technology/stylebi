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
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Arrays;

/**
 * Paint grid in a tabular layout.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class GridPaintable extends BasePaintable implements Cloneable {
   /**
    * Create a grid paintable.
    */
   public GridPaintable() {
      super(null);
   }

   /**
    * Create a grid paintable.
    * @param x x position of the grid.
    * @param y top position of the grid.
    */
   public GridPaintable(int x, int y) {
      super(null);
      this.x = x;
      this.y = y;
   }

   /**
    * If the grid is in layout model, all borders will be drawn as dash
    * lines.
    */
   public void setLayoutMode(boolean layout) {
      layoutLine = layout ? Integer.valueOf(StyleConstants.THIN_LINE) : null;
   }

   /**
    * Get the row index of the top row in the region.
    */
   public int getTopRow() {
      return row;
   }

   /**
    * Get the number of rows in the region.
    */
   public int getRowCount() {
      return nrow;
   }

   /**
    * Get the number of cols in the region.
    */
   public int getColCount() {
      return ncol;
   }

   public StylePage getPage() {
      return page;
   }

   public void setPage(StylePage page) {
      this.page = page;
   }

   /**
    * Set the grid region this paintable represents.
    * @param row top row number.
    * @param heights actual row heights.
    * @param sheet source report.
    */
   public void setGridRegion(int row, int[] heights, TabularSheet sheet) {
      this.row = row;
      this.nrow = heights.length;
      this.ncol = sheet.getColCount();
      this.heights = heights;
       this.designtime = false;
      widths = new float[ncol];
      cells = new CellInfo[nrow][ncol];

      for(int i = 0; i < widths.length; i++) {
         widths[i] = sheet.getColWidthPoints(i);
      }

      for(int i = 0; i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            cells[i][j] = new CellInfo(sheet, i + row, j);
         }
      }
   }

   /**
    * Paint the element on to a page.
    */
   @Override
   public void paint(Graphics g) {
      // this avoids using DimGraphics in designer master layout mode
      if(g instanceof GraphicsWrapper) {
         g = ((GraphicsWrapper) g).getGraphics();
      }

      Shape clip = g.getClip();
      Color ocolor = g.getColor();

      // draw individual cells
      for(int i = 0; cells != null && i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            int r = i + row;

            if(cells[i][j].span != null) {
               // if top row, always draw
               if(cells[i][j].span.x != 0 || cells[i][j].span.y != 0 && i > 0) {
                  continue;
               }

               r = Math.max(row, r + cells[i][j].span.y);
            }

            Rectangle box = getCellBounds(r, j);

            // draw background
            if(cells[i][j].background != null) {
               Dimension d = cells[i][j].bgsize;
               int ix, iy, iw, ih;

               ix = box.x;
               iy = box.y;
               iw = box.width;
               ih = box.height;

               if(cells[i][j].background instanceof Image) {
                  Image img = (Image) cells[i][j].background;

                  if(img instanceof MetaImage) {
                     img = ((MetaImage) img).getImage();
                  }

                  // the meta image could fail
                  if(img != null) {
                     Tool.waitForImage(img);
                     Shape oclip = g.getClip();

                     g.clipRect(box.x, box.y, box.width, box.height);

                     int iiw = (d == null) ? img.getWidth(null) : d.width;
                     int iih = (d == null) ? img.getHeight(null) : d.height;

                     switch(cells[i][j].bglayout) {
                     case StyleConstants.BACKGROUND_CENTER:
                        ix = (box.width - iiw) / 2 + box.x;
                        iy = (box.height - iih) / 2 + box.y;
                        iw = iiw;
                        ih = iih;
                     default:
                        break;
                     }

                     if(iiw > 0 && iih > 0) {
                        for(int x = ix; x < ix + iw; x += iiw) {
                           for(int y = iy; y < iy + ih; y += iih) {
                              g.drawImage(img, x, y, iiw, iih, null);
                           }
                        }
                     }

                     g.setClip(oclip);
                  }
               }
               else if(cells[i][j].background instanceof Color) {
                  Color clr = (Color) cells[i][j].background;
                  int right = (cells[i][j].borders != null) ?
                     cells[i][j].borders.right : StyleConstants.THIN_LINE;
                  int bottom = (cells[i][j].borders != null) ?
                     cells[i][j].borders.bottom : StyleConstants.THIN_LINE;
                  float rightW = Common.getLineWidth(right);
                  float bottomW = Common.getLineWidth(bottom);

                  rightW = (rightW > 1) ? 0.5f : rightW / 2;
                  bottomW = (bottomW > 1) ? 0.5f : bottomW / 2;

                  g.setColor(clr);

                  switch(cells[i][j].bglayout) {
                  case StyleConstants.BACKGROUND_CENTER:
                     iw = (d == null) ? box.width : d.width;
                     ih = (d == null) ? box.height : d.height;
                     iw = (iw > box.width) ? box.width : iw;
                     ih = (ih > box.height) ? box.height : ih;
                     ix = (box.width - iw) / 2 + box.x;
                     iy = (box.height - ih) / 2 + box.y;
                  default:
                     break;
                  }

                  Common.fillRect(g, ix, iy, iw - rightW, ih - bottomW);
               }
            }

            // draw border
            if(cells[i][j].borders != null || designtime) {
               Insets borders = getCellBorder(i + row, j);
               borders = borders == null ? new Insets(0, 0, 0, 0) : borders;
               int top = 0, left = 0, bottom = 0, right = 0;
               Color color = cells[i][j].borderColor;

               if(layoutLine != null) {
                  top = (designtime && borders.top == StyleConstants.NONE) ?
                     layoutLine.intValue() : borders.top;
                  left = (designtime && borders.left == StyleConstants.NONE) ?
                     layoutLine.intValue() : borders.left;
                  bottom = (designtime &&
                     borders.bottom == StyleConstants.NONE) ?
                     layoutLine.intValue() : borders.bottom;
                  right = (designtime && borders.right == StyleConstants.NONE) ?
                     layoutLine.intValue() : borders.right;
                  color = (designtime && color == null) ?
                     layoutColor : color;
               }
               else {
                  top = (designtime && borders.top == StyleConstants.NONE) ?
                     designLine : borders.top;
                  left = (designtime && borders.left == StyleConstants.NONE) ?
                     designLine : borders.left;
                  bottom = (designtime &&
                     borders.bottom == StyleConstants.NONE) ?
                     designLine : borders.bottom;
                  right = (designtime && borders.right == StyleConstants.NONE) ?
                     designLine : borders.right;

                  int bval = borders.top + borders.left + borders.bottom +
                     borders.right;

                  color = (designtime && (color == null || bval == 0)) ?
                     designColor : color;
               }

               // int design mode, avoid double border at same side
               if(designtime) {
                  if(i > 0 && borders.top == StyleConstants.NONE) {
                     top = StyleConstants.NONE;
                  }

                  if(j > 0 && borders.left == StyleConstants.NONE) {
                     left = StyleConstants.NONE;
                  }
               }

               float topW = Common.getLineWidth(top);
               float leftW = Common.getLineWidth(left);
               float bottomW = Common.getLineWidth(bottom);
               float rightW = Common.getLineWidth(right);

               g.setColor((color == null) ? Color.black : color);

               if(top != StyleConstants.NONE) {
                  if(designtime && borders.top == StyleConstants.NONE) {
                     // @by larryl, if in layout mode, force layout color (green)
                     // otherwise use design mode color (gray)
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  Common.drawHLine(g, box.y, box.x, box.x + box.width - rightW,
                     top, 0, left);
               }

               g.setColor((color == null) ? Color.black : color);

               if(left != StyleConstants.NONE) {
                  if(designtime && borders.left == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  Common.drawVLine(g, box.x, box.y,
                     box.y + box.height - bottomW, left, 0, top);
               }

               if(bottom != StyleConstants.NONE) {
                  if(designtime && borders.bottom == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  Common.drawHLine(g, box.y + box.height - bottomW, box.x,
                     box.x + box.width - rightW, bottom, left, 0);
               }

               if(right != StyleConstants.NONE) {
                  if(designtime && borders.right == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  Common.drawVLine(g, box.x + box.width - rightW, box.y,
                     box.y + box.height - bottomW, right, top, 0);
               }

               // fix upper-right corner
               if(rightW > 0 && topW > 0) {
                  if(designtime && borders.top == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  float adj = (rightW > 1) ? 0.5f : rightW / 2f;
                  Common.drawHLine(g, box.y, box.x + box.width - rightW,
                                   box.x + box.width - adj, top, 0, right);
               }

               // fix lower-left corner
               if(leftW > 0 && bottomW > 0) {
                  if(designtime && borders.left == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  float adj = (bottomW > 1) ? 0.5f : bottomW / 2f;
                  Common.drawVLine(g, box.x, box.y + box.height - bottomW,
                                   box.y + box.height - adj, left, 0, bottom);
               }

               // fix lower-right corner
               if(rightW > 0 && bottomW > 0) {
                  if(designtime && borders.right == StyleConstants.NONE) {
                     g.setColor(layoutLine == null ? designColor : layoutColor);
                  }
                  else {
                     g.setColor((color == null) ? Color.black : color);
                  }

                  float radj = (rightW > 1) ? 0.5f : rightW / 2f;
                  float badj = (bottomW > 1) ? 0.5f : bottomW / 2f;

                  Common.drawHLine(g, box.y + box.height - bottomW,
                                   box.x + box.width - rightW,
                                   box.x + box.width - radj, bottom,
                                   right, 0);
                  Common.drawVLine(g, box.x + box.width - rightW,
                                   box.y + box.height - bottomW,
                                   box.y + box.height - badj,
                                   right, bottom, 0);
               }
            }
         }
      }

      g.setClip(clip);
      g.setColor(ocolor);
   }

   /**
    * Check if this paintable is blank. If it is, it does not need to be
    * painted. This is used in HTML generation to determine if a background
    * image needs to be created for a page.
    */
   public boolean isEmpty() {
      boolean blank = true;

      // draw individual cells
      for(int i = 0; cells != null && i < cells.length && blank; i++) {
         for(int j = 0; j < cells[i].length && blank; j++) {
            blank = cells[i][j].background == null &&
               cells[i][j].borders == null;
         }
      }

      return blank;
   }

   /**
    * Get the bounds of the specified cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public Rectangle getCellBounds(int r, int c) {
      r -= row;

      if(r < 0) {
         return null;
      }

      float bx = x, by = y, bw = widths[c], bh = heights[r];

      if(cells[r][c].span != null) {
         int fromTop = 0;

         r += cells[r][c].span.y;
         // @by larryl, if we are printing a cell in the middle at the top
         // of a page, the number of rows should not include the rows not
         // on the current page
         if(r < 0) {
            fromTop = -r;
            r = 0;
         }

         c += cells[r][c].span.x;

         for(int i = 1; i < cells[r][c].span.height - fromTop
                && i + r < heights.length; i++) {
            bh += heights[i + r];
         }

         for(int i = 1; i < cells[r][c].span.width; i++) {
            bw += widths[i + c];
         }
      }

      for(int i = 0; i < r; i++) {
         by += heights[i];
      }

      for(int i = 0; i < c; i++) {
         bx += widths[i];
      }

      int ix = (int) bx;
      int iy = (int) by;

      return new Rectangle(ix, iy, (int) (bw + bx - ix), (int) (bh + by - iy));
   }

   /**
    * Locate the cell where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @param adj_span true to adjust the cell location to the span cell.
    * @return cell location or null if outside of table.
    */
   public Point locate(int xd, int yd, boolean adj_span) {
      return locate(xd, yd, false, adj_span);
   }

   /**
    * Locate the cell border where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @return cell location or null if outside of table. The X and Y may
    * be one beyond the bounds of the region. In that case it indicates
    * the last border on the right or bottom.
    */
   public Point locateBorder(int xd, int yd) {
      return locate(xd, yd, true, true);
   }

   /**
    * Locate the row where the Y position is in.
    */
   public int locateRow(int yd) {
      if(yd < y) {
         return -1;
      }

      yd -= y;

      for(int i = 0; i < heights.length; i++) {
         if(yd < heights[i]) {
            return i + row;
         }

         yd -= heights[i];
      }

      return -1;
   }

   /**
    * Locate the column where the X position is in.
    */
   public int locateCol(int xd) {
      if(xd < x) {
         return -1;
      }

      xd -= x;

      for(int i = 0; i < widths.length; i++) {
         if(xd < widths[i]) {
            return i;
         }

         xd -= widths[i];
      }

      return -1;
   }

   /**
    * Locate the cell where the point falls into.
    * @param xd x coordinate relative to page.
    * @param yd y coordinate relative to page.
    * @param border true to locate the cell border.
    * @param adj_span true to adjust the location to span cell.
    * point is in, false to locate the entire cell.
    */
   private Point locate(int xd, int yd, boolean border, boolean adj_span) {
      if(xd < x || yd < y) {
         return null;
      }

      xd -= x;
      yd -= y;

      Point cell = new Point(-1, -1);

      for(int i = 0; i < heights.length; i++) {
         if(border && yd < 2 || !border && yd < heights[i]) {
            cell.y = i;
            break;
         }

         yd -= heights[i];
      }

      if(border && yd < 2) {
         cell.y = heights.length;
      }

      for(int i = 0; i < widths.length; i++) {
         if(border && xd < 2 || !border && xd < widths[i]) {
            cell.x = i;
            break;
         }

         xd -= widths[i];
      }

      if(border && xd < 2) {
         cell.x = widths.length;
      }

      if(cell.x >= 0 && cell.y >= 0) {
         Rectangle span = cells[cell.y][cell.x].span;

         if(adj_span && span != null) {
            cell.x += span.x;
            cell.y += span.y;
         }

         cell.y += row;
         return cell;
      }

      return null;
   }

   /**
    * Get the row height in pixels.
    */
   public int getRowHeight(int r) {
      r -= row;
      return heights[r];
   }

   /**
    * Set the row height in pixels.
    */
   public void setRowHeight(int r, int h) {
      r -= row;
      heights[r] = h;
   }

   /**
    * Get the column width in pixels.
    */
   public float getColWidth(int c) {
      return widths[c];
   }

   /**
    * Set the grid column width. This is only used internally.
    */
   public void setColWidth(int c, int w) {
      widths[c] = w;
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public Rectangle getBounds() {
      if(widths == null || heights == null) {
         return null;
      }

      float w = 0;
      int h = 0;

      for(int i = 0; i < widths.length; i++) {
         w += widths[i];
      }

      for(int i = 0; i < heights.length; i++) {
         h += heights[i];
      }

      return new Rectangle((int) x, (int) y, (int) Math.ceil(w), h);
   }

   /**
    * Get the position of the top of the row.
    */
   public int getY(int r) {
      int ry = y;

      r -= row;

      // not in grid
      if(r > heights.length) {
         return -1;
      }

      for(int i = 0; i < r; i++) {
         ry += heights[i];
      }

      return ry;
   }

   /**
    * Get the position of the left of the column.
    */
   public int getX(int c) {
      float cx = x;

      // not in grid
      if(c > widths.length) {
         return -1;
      }

      for(int i = 0; i < c; i++) {
         cx += widths[i];
      }

      return (int) cx;
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   public void adjustLoc(Point loc) {
      x = loc.x;
      y = loc.y;
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return new Point(x, y);
   }

   /**
    * Get the report element that this paintable area corresponds to.
    * @return report element.
    */
   @Override
   public ReportElement getElement() {
      return null;
   }

   /**
    * Get the border color of the specified cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public Color getCellBorderColor(int r, int c) {
      r -= row;

      if(checkBound(r, c)) {
         return cells[r][c].borderColor;
      }

      return Color.white;
   }

   /**
    * Get the border type of the specified cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public Insets getCellBorder(int r, int c) {
      r -= row;

      if(checkBound(r, c)) {
         return cells[r][c].borders;
      }

      return new Insets(0, 0, 0, 0);
   }

   public float[] getWidths() {
      return widths;
   }

   public int[] getHeights() {
      return heights;
   }

   /**
    * Get the background of the specified cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public Object getCellBackground(int r, int c) {
      r -= row;

      if(checkBound(r, c)) {
         return cells[r][c].background;
      }

      return null;
   }

   /**
    * Get the background layout of the specified cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public int getCellBackgroundLayout(int r, int c) {
      r -= row;

      return cells[r][c].bglayout;
   }

   /**
    * Get the background size of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell background size.
    */
   public Dimension getCellBackgroundSize(int r, int c) {
      r -= row;

      return cells[r][c].bgsize;
   }

   /**
    * Get the right bottom cell of the specified span cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public Rectangle getCellSpan(int r, int c) {
      r -= row;

      if(checkBound(r, c)) {
         return cells[r][c].span;
      }

      return null;
   }

   /**
    * Get the whether to fit page of cell.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   public boolean getFitPage(int r, int c) {
      if(checkBound(r, c)) {
         return cells[r][c].fitPage;
      }

      return true;
   }

   /**
    * Check if the index loc in array.
    * @param r row index in the tabular layout.
    * @param c column index.
    */
   private boolean checkBound(int r, int c) {
      try {
         CellInfo info = cells[r][c];

         return true;
      }
      catch(ArrayIndexOutOfBoundsException e) {
         return false;
      }
   }

   /**
    * CellInfo holds the attributes of a cell.
    */
   public static class CellInfo implements java.io.Serializable {
      public CellInfo() {
      }

      public CellInfo(TabularSheet sheet, int r, int c) {
         span = sheet.getCellSpan(r, c);

         if(span != null) {
            span = new Rectangle(span);
            r += span.y;
            c += span.x;
         }

         background = sheet.getCellBackground(r, c);
         bgsize = sheet.getCellBackgroundSize(r, c);
         bglayout = sheet.getCellBackgroundLayout(r, c);
         borders = sheet.getCellBorders(r, c);
         borderColor = sheet.getCellBorderColor(r, c);
      }

      /**
       * Check if current CellInfo's content is equal to target one's.
       * @param obj the target CellInfo.
       */
      public boolean equalsContent(CellInfo info) {
         return info != null && this.background == info.background
            && this.bgsize == info.bgsize && this.bglayout == info.bglayout
            && this.borders == info.borders
            && this.borderColor == info.borderColor && this.span == info.span
            && this.fitPage == info.fitPage;
      }

      public Object background = null;
      Dimension bgsize = null; // background size
      int bglayout = StyleConstants.BACKGROUND_TILED;// background layout
      public Insets borders = null;
      public Color borderColor = null;
      public Rectangle span = null;
      private boolean fitPage = true;
   }

   /**
    * Scale the positions and sizes.
    */
   void scale(double xs, double ys) {
      x = (int) (x * xs);
      y = (int) (y * ys);

      for(int i = 0; i < heights.length; i++) {
         heights[i] = (int) (heights[i] * ys);
      }

      for(int i = 0; i < widths.length; i++) {
         widths[i] = (float) (widths[i] * xs);
      }
   }

   @Override
   public Object clone() {
      try {
         GridPaintable pt = (GridPaintable) super.clone();

         pt.heights = new int[heights.length];
         System.arraycopy(heights, 0, pt.heights, 0, heights.length);
         pt.widths = new float[widths.length];
         System.arraycopy(widths, 0, pt.widths, 0, widths.length);

         return pt;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone grid paintable", ex);
      }

      return null;
   }

   /**
    * Check if current GridPaintables's content is equal to target one's.
    * @param obj the target GridPaintables.
    */
   public boolean equalsContent(Object obj) {
      if(obj == null || !(obj instanceof GridPaintable)) {
         return false;
      }

      GridPaintable pt = (GridPaintable) obj;

      return this.row == pt.row && this.nrow == pt.nrow && this.ncol == pt.ncol
         && this.getLocation().equals(pt.getLocation())
         && Arrays.equals(this.heights, pt.heights)
         && Arrays.equals(this.widths, pt.widths)
         && equalsCells(this.cells, pt.cells)
         && this.layoutLine == pt.layoutLine
         && this.designtime == pt.designtime
         && this.layoutColor.equals(pt.layoutColor)
         && this.designLine == pt.designLine
         && this.designColor.equals(pt.designColor);
   }

   /**
    * Check if target GridPaintables' CellInfos of are same.
    * @param cells1 the CellInfos of the first GridPaintable.
    * @param cells2 the CellInfos of the second GridPaintable.
    */
   public boolean equalsCells(CellInfo[][] cells1, CellInfo[][] cells2) {
      if(cells1 == null && cells2 == null) {
         return true;
      }

      if(cells1 != null && cells2 != null) {
         if(cells1.length != cells2.length) {
            return false;
         }

         for(int i = 0; i < cells1.length; i++) {
            if(cells1[i].length != cells2[i].length) {
               return false;
            }

            for(int j = 0; j < cells1[i].length; j++) {
               if(!cells1[i][j].equalsContent(cells2[i][j])) {
                  return false;
               }
            }
         }

         return true;
      }

      return false;
   }

   private int row, nrow, ncol;
   private int x, y;
   private CellInfo[][] cells;
   private int[] heights;
   private float[] widths;
   private transient StylePage page;

   private transient Integer layoutLine; // force borders, in design mode
   private transient boolean designtime = false;

   static final Color layoutColor = new Color(86, 175, 112);
   static final int designLine = StyleConstants.DASH_LINE;
   static final Color designColor = Color.gray;

   private static final Logger LOG =
      LoggerFactory.getLogger(GridPaintable.class);
}

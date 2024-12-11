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
package inetsoft.report;

import inetsoft.graph.data.DataSet;
import inetsoft.report.internal.*;
import inetsoft.report.lens.DefaultTextLens;
import inetsoft.report.painter.*;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.Vector;

/**
 * TabularSheet is one of the two report types in Style Report. It uses
 * a grid layout model. Report contents are divided into grid cells.
 * Each cell is independently processed. It is best suited for reports
 * with contents divided into tabular sections.
 * <p>
 * ReportSheet contains the common API for report generation.
 * Please refer to the Style Report Programming Guide for more details
 * on the concepts and features of the TabularSheet.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TabularSheet extends ReportSheet {
    private int r;
    private int c;
    private int fill;

    /**
    * Create an empty TabularSheet.
    */
   public TabularSheet() {
   }

   /**
    * Normalizing the grid removes all unnecessary cell spanning and empty
    * invisible rows/columns.
    * @param sheet the TabularSheet need to normalize.
    * @return true if the TabularSheet grid changed.
    */
   private static boolean normalizeTabularGrid(TabularSheet sheet) {
      boolean changed = false;

      // remove invisible empty rows
      for(int i = 0; i < sheet.getRowCount(); i++) {
         boolean empty = true;

         for(int j = 0; j < sheet.getColCount(); j++) {
            Rectangle span = sheet.getCellSpan(i, j);

            if(span == null || span.y == 0) {
               empty = false;
               break;
            }
         }

         if(empty && i > 0) {
            sheet.setMinRowHeight(i - 1,
               sheet.getMinRowHeight(i - 1) + sheet.getMinRowHeight(i));
            sheet.removeRows(i--, 1);
            changed = true;
         }
      }

      int lastIdx = 0;
      float w = 0;

      // remove invisible empty columns
      for(int i = 0; i < sheet.getColCount(); i++) {
         boolean empty = true;

         for(int j = 0; j < sheet.getRowCount(); j++) {
            Rectangle span = sheet.getCellSpan(j, i);

            if(span == null || span.x == 0) {
               empty = false;
               break;
            }
         }

         if(empty && i > 0) {
            if(lastIdx != i) {
               w = sheet.getColWidthPoints(i - 1) + sheet.getColWidthPoints(i);
               lastIdx = i;
            }
            else {
               w += sheet.getColWidthPoints(i);
            }

            sheet.setColWidth(i - 1, Float.toString(w));
            sheet.removeCols(i--, 1);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Get number of rows in the grid.
    */
   public int getRowCount() {
      return rows.length;
   }

   /**
    * Get number of columns in the grid.
    */
   public int getColCount() {
      return cols.length;
   }

   /**
    * Merge the cells in the region. The merging is done by setting the
    * cell spanning of the cells. If a cell in the merge region is already
    * a span cell, its full span is also merged into the region.
    */
   public void mergeCells(int r, int c, int nrow, int ncol) {
      checkBounds(r, c);

      if(r + nrow > cells.length || c + ncol > cells[r].length) {
         throw new IndexOutOfBoundsException("Cell spanning out of bounds: " +
            r + "," + c + " " + nrow + "x" + ncol);
      }

      int endrow = r + nrow;
      int endcol = c + ncol;
      nrow = ncol = 0;

      // if a cell is merged into the new cell, all
      // overlapping cells of that original cell should be merged
      // as well. Otherwise we may get a cell cut off in the middle
      // by the new span.
      while(endrow - r != nrow || endcol - c != ncol) {
         nrow = endrow - r;
         ncol = endcol - c;

         // merge all span setting covered in the region
         for(int i = r; i < endrow; i++) {
            for(int j = c; j < endcol; j++) {
               if(cells[i][j] != null && cells[i][j].span != null) {
                  c = Math.min(c, j + cells[i][j].span.x);
                  r = Math.min(r, i + cells[i][j].span.y);
                  endcol = Math.max(endcol,
                     j + cells[i][j].span.width + cells[i][j].span.x);
                  endrow = Math.max(endrow,
                     i + cells[i][j].span.height + cells[i][j].span.y);

                  // @by larryl, if a cell is merged into the new cell, all
                  // overlapping cells of that original cell should be merged
                  // as well. Otherwise we may get a cell cut off in the middle
                  // by the new span. An alternative is to reset the span of the
                  // cut off cells
                  /*
                  endrow = r + nrow;
                  endcol = c + ncol;
                  */
               }
            }
         }
      }

      // set the spanning
      Vector all = new Vector(); // all elements in the span cells
      Object background = null;
      Insets borders = null;
      Color borderColor = null;
      Dimension backgsize = null;
      int backglayout = StyleConstants.BACKGROUND_TILED;

      if(cells[r][c] != null) {
         background = cells[r][c].background;
         backgsize = cells[r][c].bgsize;
         backglayout = cells[r][c].bglayout;
         borders = cells[r][c].borders;
         borderColor = cells[r][c].borderColor;
      }

      // set span and merge attributes
      for(int i = r; i < r + nrow; i++) {
         for(int j = c; j < c + ncol; j++) {
            if(cells[i][j] == null) {
               cells[i][j] = new Cell();
            }
            else if(cells[i][j].elements != null) {
               append(all, cells[i][j].elements);
               cells[i][j].elements.clear();

               if(background == null) {
                  background = cells[i][j].background;
                  backgsize = cells[i][j].bgsize;
                  backglayout = cells[i][j].bglayout;
               }

               if(borders == null) {
                  borders = cells[i][j].borders;
                  borderColor = cells[i][j].borderColor;
               }
            }

            cells[i][j].span = new Rectangle(c - j, r - i, ncol, nrow);
         }
      }

      /**
      // @by larryl, if a span cell is cut if half by a new span cell, we need
      // to reset the span information
      if(r + nrow < getRowCount()) {
         int next = r + nrow;

         for(int i = c; i < c + ncol; i++) {
            if(cells[next][i] != null && cells[next][i].span != null) {
               if(cells[next][i].span.y < 0) {
                  int nrow0 = cells[next][i].span.height +
                     cells[next][i].span.y;
                  int ncol0 = cells[next][i].span.width;
                  int c0 = i + cells[next][i].span.x;
                  int cn = c0 + cells[next][i].span.width;
                  int r0 = next + cells[next][i].span.y;
                  int rn = r0 + cells[next][i].span.height;

                  for(int ri = r0; ri < rn; ri++) {
                     for(int ci = c0; ci < cn; ci++) {
                        // the cell already covered by the newly merged cell
                        if(ri >= r && ri < r + nrow &&
                           ci >= c && ci < c + ncol)
                        {
                           continue;
                        }

                        // cell on the left side of the new merged cell
                        if(ri < next && ci < c) {
                           cells[ri][ci].span = new Rectangle(c0 - ci, r0 - ri,
                                                              c - c0, next - r0);
                        }
                        // cell on the right side of the new merged cell
                        else if(ri < next && ci >= c + ncol) {
                           cells[ri][ci].span = new Rectangle(
                              c + ncol - ci, r0 - ri,
                              cn - c - ncol, next - r0);
                        }
                        // cell below the new mreged cell
                        else {
                           cells[ri][ci].span = new Rectangle(c0 - ci, next - ri,
                                                              ncol0, nrow0);
                        }
                     }
                  }
               }
            }
         }
      }

      // @by larryl, if a span cell is cut if half by a new span cell, we need
      // to reset the span information
      if(c + ncol < getColCount()) {
         int next = c + ncol;

         for(int i = r; i < r + nrow; i++) {
            if(cells[i][next] != null && cells[i][next].span != null) {
               if(cells[i][next].span.x < 0) {
                  int ncol0 = cells[i][next].span.width +
                     cells[i][next].span.x;
                  int nrow0 = cells[i][next].span.height;
                  int r0 = i + cells[i][next].span.y;
                  int rn = r0 + cells[i][next].span.height;
                  int c0 = next + cells[i][next].span.x;
                  int cn = c0 + cells[i][next].span.width;

                  for(int ri = r0; ri < rn; ri++) {
                     for(int ci = c0; ci < cn; ci++) {
                        // the cell already covered by the newly merged cell
                        if(ri >= r && ri < r + nrow &&
                           ci >= c && ci < c + ncol)
                        {
                           continue;
                        }

                        // cell on the top side of the new merged cell
                        if(ci < next && ri < r) {
                           cells[ri][ci].span = new Rectangle(c0 - ci, r0 - ri,
                                                              next - c0,
                                                              r - r0);
                        }
                        // cell below the new merged cell is already taken care
                        // of in the previous loop.
                        // cell right of the new merged cell
                        else {
                           cells[ri][ci].span = new Rectangle(next - ci, r0 - ri,
                                                              ncol0, nrow0);
                        }
                     }
                  }
               }
            }
         }
      }
      */

      cells[r][c].elements = all;

      // set all cell attributes to merged attributes
      for(int i = r; i < r + nrow; i++) {
         for(int j = c; j < c + ncol; j++) {
            cells[i][j].background = background;
            cells[i][j].bgsize = backgsize;
            cells[i][j].bglayout = backglayout;
            cells[i][j].borders = borders;
            cells[i][j].borderColor = borderColor;
         }
      }
   }

   /**
    * Split a cell into rows. If the cell is a span cell, the row spanning
    * is undone before new rows are inserted into the grid.
    * @param r row index.
    * @param c column index.
    * @param nrow number of rows to split the cell into.
    */
   public void splitRows(int r, int c, int nrow) {
      splitCell(r, c, nrow, 0);
   }

   /**
    * Split a cell into columns. If the cell is a span cell, the column
    * spanning is undone before new columns are inserted into the grid.
    * @param r row index.
    * @param c column index.
    * @param ncol number of columns to split the cell into.
    */
   public void splitCols(int r, int c, int ncol) {
      splitCell(r, c, 0, ncol);
   }

   /**
    * Split the cell into number of rows and columns.
    */
   private void splitCell(int r, int c, int nrow, int ncol) {
      checkBounds(r, c);

      Cell cell = cells[r][c];

      if(cell == null) {
         cells[r][c] = cell = new Cell();
      }

      Object defBackground = cell.background;
      Insets defBorders = cell.borders;
      Color defBorderColor = cell.borderColor;
      Dimension defBgsize = cell.bgsize;
      int defBglayout = cell.bglayout;

      // if in middle of a span cell, move the r,c to the upper left corner
      if(cell.span != null) {
         r += cell.span.y;
         c += cell.span.x;
         defBackground = cells[r][c].background;
         defBgsize = cells[r][c].bgsize;
         defBglayout = cells[r][c].bglayout;
         defBorders = cells[r][c].borders;
         defBorderColor = cells[r][c].borderColor;
      }

      if(nrow > 1) {
         // if cell spans multiple rows, undo the spanning first
         if(cell.span != null && cell.span.height > 1) {
            int i = 0;
            Rectangle span = new Rectangle(cell.span);

            // set the span to 1
            for(; nrow > 1 && i < span.height; i++, r++, nrow--) {
               for(int j = 0; j < span.width; j++) {
                  cells[r][j + c].span.y = 0;
                  cells[r][j + c].span.height = 1;
                  cells[r][j + c].background = defBackground;
                  cells[r][j + c].bgsize = defBgsize;
                  cells[r][j + c].bglayout = defBglayout;
                  cells[r][j + c].borders = defBorders;
                  cells[r][j + c].borderColor = defBorderColor;
               }
            }

            // adjust the remaining span
            for(int top = i; i < span.height; i++, r++, nrow--) {
               for(int j = 0; j < span.width; j++) {
                  cells[r][j + c].span.y = top - i;
                  cells[r][j + c].span.height = span.height - top;
                  cells[r][j + c].background = defBackground;
                  cells[r][j + c].bgsize = defBgsize;
                  cells[r][j + c].bglayout = defBglayout;
                  cells[r][j + c].borders = defBorders;
                  cells[r][j + c].borderColor = defBorderColor;
                  cells[r][j + c].elements = new Vector();
               }
            }

            nrow++;
            r--;
         }

         // no more span cell to expand, insert new rows
         if(nrow > 1) {
            int minRowHeight = getMinRowHeight(r);
            int h;

            if(minRowHeight < 0) {
               // to fix bug1219310181375
               // copy the minRowHeight when the height is -1("fit page")
               // because in Java, the "/" is a exactly divisible operator
               // e.g. if number is a integer and number > 1,
               // then  -1 / number = 0
               h = minRowHeight;
            }
            else {
               h = minRowHeight / nrow;
            }

            insertRows(r, nrow - 1); // insert before

            // copy the attribute to the new cell
            for(int i = 0; i < nrow - 1; i++) {
               setCellBackground(r + i, c, defBackground);
               setCellBackgroundSize(r + i, c, defBgsize);
               setCellBackgroundLayout(r + i, c, defBglayout);
               setCellBorders(r + i, c, defBorders);
               setCellBorderColor(r + i, c, defBorderColor);
            }

            // copy the contents to the first column
            cells[r][c].elements = cells[r + nrow - 1][c].elements;
            cells[r][c].current = cells[r + nrow - 1][c].current;
            cells[r + nrow - 1][c].elements = new Vector();

            // set new height
            for(int i = 0; i < nrow; i++) {
               setMinRowHeight(r + i, h);
            }

            // merge the new rows in other cells
            for(int i = 0; i < c; i++) {
               mergeCells(r, i, nrow, 1);
            }

            int next = (cells[r + 1][c] == null ||
               cells[r + 1][c].span == null) ?
               (c + 1) :
               (c + cells[r + 1][c].span.width);

            for(int i = next; i < getColCount(); i++) {
               mergeCells(r, i, nrow, 1);
            }
         }
      }

      if(ncol > 1) {
         // if cell spans multiple columns, undo the spanning first
         if(cell.span != null && cell.span.width > 1) {
            int i = 0;
            Rectangle span = new Rectangle(cell.span);

            for(; ncol > 1 && i < span.width; i++, c++, ncol--) {
               // unwind span
               for(int j = 0; j < span.height; j++) {
                  cells[j + r][c].span.x = 0;
                  cells[j + r][c].span.width = 1;
                  cells[j + r][c].background = defBackground;
                  cells[j + r][c].bgsize = defBgsize;
                  cells[j + r][c].bglayout = defBglayout;
                  cells[j + r][c].borders = defBorders;
                  cells[j + r][c].borderColor = defBorderColor;
               }
            }

            // adjust the remaining span
            for(int top = i; i < span.width; i++, c++, ncol--) {
               for(int j = 0; j < span.height; j++) {
                  cells[j + r][c].span.x = top - i;
                  cells[j + r][c].span.width = span.width - top;
                  cells[j + r][c].background = defBackground;
                  cells[j + r][c].bgsize = defBgsize;
                  cells[j + r][c].bglayout = defBglayout;
                  cells[j + r][c].borders = defBorders;
                  cells[j + r][c].borderColor = defBorderColor;
               }
            }

            ncol++;
            c--;
         }

         // no more span cell to expand, insert new columns
         if(ncol > 1) {
            String w = divideWidth(getColWidth(c), ncol);

            insertCols(c, ncol - 1);

            // copy the attribute to the new cell
            for(int i = 0; i < ncol - 1; i++) {
               setCellBackground(r, c + i, defBackground);
               setCellBackgroundSize(r, c + i, defBgsize);
               setCellBackgroundLayout(r, c + i, defBglayout);
               setCellBorders(r, c + i, defBorders);
               setCellBorderColor(r, c + i, defBorderColor);
            }

            // copy the contents to the first column
            cells[r][c].elements = cells[r][c + ncol - 1].elements;
            cells[r][c].current = cells[r][c + ncol - 1].current;
            cells[r][c + ncol - 1].elements = new Vector();

            // set new width
            for(int i = 0; i < ncol; i++) {
               setColWidth(c + i, w);
            }

            // merge the new rows in other cells
            for(int i = 0; i < r; i++) {
               mergeCells(i, c, 1, ncol);
            }

            int next = (cells[r][c + 1] == null ||
               cells[r][c + 1].span == null) ?
               (r + 1) :
               (r + cells[r][c + 1].span.height);

            for(int i = next; i < getRowCount(); i++) {
               mergeCells(i, c, 1, ncol);
            }
         }
      }
   }

   /**
    * Get the background image or color of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell background image or color.
    */
   public Object getCellBackground(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ? null : cells[r][c].background;
   }

   /**
    * Set the background image or color of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param bg cell background image or color.
    */
   public void setCellBackground(int r, int c, Object bg) {
      checkBounds(r, c);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].background = bg;
   }

   /**
    * Get the background layout of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell background layout.
    */
   public int getCellBackgroundLayout(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ?
         StyleConstants.BACKGROUND_TILED :
         cells[r][c].bglayout;
   }

   /**
    * Set the background layout of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param layout cell background layout.
    */
   public void setCellBackgroundLayout(int r, int c, int layout) {
      checkBounds(r, c);
      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].bglayout = layout;
   }

   /**
    * Get the background size of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell background size.
    */
   public Dimension getCellBackgroundSize(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ? null : cells[r][c].bgsize;
   }

   /**
    * Set the background size of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param d cell background size.
    */
   public void setCellBackgroundSize(int r, int c, Dimension d) {
      checkBounds(r, c);
      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].bgsize = d;
   }

   /**
    * Set the background size of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param width cell background width.
    * @param height cell background height.
    */
   public void setCellBackgroundSize(int r, int c, int width, int height) {
      setCellBackgroundSize(r, c, new Dimension(width, height));
   }

   /**
    * Get the borders of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell borders line styles.
    */
   public Insets getCellBorders(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ? null : cells[r][c].borders;
   }

   /**
    * Set the borders of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param borders cell borders line styles.
    */
   public void setCellBorders(int r, int c, Insets borders) {
      checkBounds(r, c);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].borders = borders;
   }

   /**
    * Get the border color of the specified cell.
    * @param r row index.
    * @param c column index.
    * @return cell border color.
    */
   public Color getCellBorderColor(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ? null : cells[r][c].borderColor;
   }

   /**
    * Set the border color of the specified cell.
    * @param r row index.
    * @param c column index.
    * @param color cell border color.
    */
   public void setCellBorderColor(int r, int c, Color color) {
      checkBounds(r, c);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].borderColor = color;
   }

   /**
    * Check if the cell contents is repeated.
    * @param r row index.
    * @param c column index.
    * @return true if cell contents is repeated on every page it is printed.
    */
   public boolean isCellRepeat(int r, int c) {
      checkBounds(r, c);

      return (cells[r][c] == null) ? false : cells[r][c].repeat;
   }

   /**
    * Set the repeat flag of a cell. If this flag is true, the cell contents
    * is repeated on every page it is printed on. This can be used to create
    * a sidebar on a report.
    * @param r row index.
    * @param c column index.
    * @param repeat repeat flag.
    */
   public void setCellRepeat(int r, int c, boolean repeat) {
      checkBounds(r, c);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].repeat = repeat;
   }

   /**
    * Check if the cell fit page.
    * @param r row index.
    * @param c column index.
    * @return true if fit page.
    */
   public boolean isFitPage(int r, int c) {
      checkBounds(r, c);
      return (cells[r][c] == null) ? true : cells[r][c].fitPage;
   }

   /**
    * Set the fitPage of a cell. If this flag is true, the cell
    * print bottom border to fit content, otherwise print on last page bottom.
    * @param r row index.
    * @param c column index.
    * @param print print flag.
    */
   public void setFitPage(int r, int c, boolean print) {
      checkBounds(r, c);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      cells[r][c].fitPage = print;
   }

   /**
    * Set cell span directly.
    * @param r row index.
    * @param c column index.
    * @param rec span rectangle.
    */
   public void setCellSpan(int r, int c, Rectangle rec) {
      checkBounds(r, c);

      if(rec != null && (r + rec.y + rec.height > cells.length ||
         c + rec.x + rec.width > cells[r].length))
      {
         throw new IndexOutOfBoundsException("Cell spanning out of bounds: " +
            r + "," + c + " " + rec);
      }

      cells[r][c].span = rec;
   }

   /**
    * Merge the cells in the region. This is different from the mergeCells()
    * call. If the span region overlaps an existing span region, the new
    * span region is not merged into the span region. Instead, the existing
    * span region is reset to remove the overlapping, and then the new
    * spanning is applied.
    */
   public void setCellSpan(int r, int c, int nrow, int ncol) {
      checkBounds(r, c);

      if(r + nrow > cells.length || c + ncol > cells[r].length) {
         throw new IndexOutOfBoundsException("Cell spanning out of bounds: " +
            r + "," + c + " " + nrow + "x" + ncol);
      }

      // unwind overlapping span cells before doing a merge
      for(int i = r; i < r + nrow; i++) {
         for(int j = c; j < c + ncol; j++) {
            if(cells[i][j] != null && cells[i][j].span != null) {
               int sr = cells[i][j].span.y + i;
               int sc = cells[i][j].span.x + j;
               int snrow = cells[i][j].span.height;
               int sncol = cells[i][j].span.width;

               // unwind the current span setting
               for(int si = sr; si < sr + snrow; si++) {
                  for(int sj = sc; sj < sc + sncol; sj++) {
                     cells[si][sj].span = null;
                  }
               }

               // set the span for the original span area outside of this
               // new span region

               // region above the new span region
               if(sr < r) {
                  mergeCells(sr, sc, r - sr, sncol);
                  snrow -= r - sr;
                  sr = r;
               }

               // region below the new span region
               if(sr + snrow > r + nrow) {
                  int n = sr + snrow - r - nrow;

                  mergeCells(r + nrow, sc, n, sncol);
                  snrow -= n;
               }

               // region left of the new span region
               if(sc < c && snrow > 0) {
                  mergeCells(sr, sc, snrow, c - sc);
               }

               // region right of the new span region
               if(sc + sncol > c + ncol && snrow > 0) {
                  mergeCells(sr, c + ncol, snrow, sc + sncol - c - ncol);
               }
            }
         }
      }

      // set the new span region
      mergeCells(r, c, nrow, ncol);
   }

   /**
    * Get the span setting of the specified cell.
    * @return cell span information. If the specified cell is inside a
    * span cell, the x value is the negative value of the distance from
    * this cell to the left most cell in the span cell. The y value is
    * the negative value of the distance from this cell to the top most
    * cell in the span cell. The width is the column span, and height
    * is the row span.
    */
   public Rectangle getCellSpan(int r, int c) {
      checkBounds(r, c);

      if(cells[r][c] != null && cells[r][c].span != null) {
         return new Rectangle(cells[r][c].span);
      }

      return null;
   }

   /**
    * Set the row height in pixels (points). A value of zero indicates the
    * row height should be calculated from the cell contents.
    * @param r row index.
    * @param height row height in points (1/72 inch).
    */
   public void setMinRowHeight(int r, int height) {
      checkBounds(r, 0);
      rows[r].height = height;
   }

   /**
    * Get the row height.
    * @param r row index.
    * @return row height in points.
    */
   public int getMinRowHeight(int r) {
      checkBounds(r, 0);
      return rows[r].height;
   }

   /**
    * Get the actual row height. This value is only available after this
    * report has been printed or otherwise processed.
    */
   public int getRowHeight(int r) {
      checkBounds(r, 0);
      return rows[r].advanced;
   }

   /**
    * Set the page orientation for a row. If orientation is set for a row,
    * it automatically forces a new page, and change the new page to the
    * specified orientation. Set orientation to null to use the default
    * orientation.
    * @param orientation a Integer object with value in
    * StyleConstants.PORTRAIT or StyleConstants.LANDSCAPE.
    */
   public void setRowOrientation(int r, Integer orientation) {
      checkBounds(r, 0);
      rows[r].orient = orientation;
   }

   /**
    * Get the orientation of a row.
    */
   public Integer getRowOrientation(int r) {
      checkBounds(r, 0);
      return rows[r].orient;
   }

   /**
    * Set the page margin for a row. If margin is set for a row,
    * it automatically forces a new page, and change the new page to the
    * specified margin. Set orientation to null to use the default
    * orientation.
    * @param margin margin in inches.
    */
   public void setRowMargin(int r, Margin margin) {
      checkBounds(r, 0);
      rows[r].margin = margin;
   }

   /**
    * Get the margin of a row.
    */
   public Margin getRowMargin(int r) {
      checkBounds(r, 0);
      return rows[r].margin;
   }

   /**
    * Set the column width. The width is specified as a percentage of the
    * page width (e.g. 50%), or proportion to other columns (e.g. 2*), or
    * as fixed size in points (e.g. 200).
    * @param c column index.
    * @param width width specification.
    */
   public void setColWidth(int c, String width) {
      if(width.startsWith("-")) {
         throw new RuntimeException("Width can not be negative: " + width);
      }

      checkBounds(0, c);
      cols[c].width = width;
   }

   /**
    * Get the column width setting. The width is specified as a string.
    * See setColWidth() for width specification format.
    * @param c column index.
    * @return width specification.
    */
   public String getColWidth(int c) {
      checkBounds(0, c);
      return cols[c].width;
   }

   /**
    * Get the column width in number of points. This width is calculated
    * during the layout process. It is only available after a report has
    * been printed.
    */
   public float getColWidthPoints(int c) {
      checkBounds(0, c);
      return cols[c].points;
   }

   /**
    * append rows after the specified row. To add rows to the end of
    * the table, use row count as the row index.
    * @param selreg selected cells.
    * @param nrow number of rows to append.
    */
   public void appendRows(Rectangle selreg, int nrow) {
      Cell[][] ncells = new Cell[getRowCount() + nrow][];
      int appendRow = selreg.y + selreg.height;
      System.arraycopy(cells, 0, ncells, 0, appendRow);
      System.arraycopy(cells, appendRow, ncells, appendRow + nrow, getRowCount() - appendRow);
      boolean toend = appendRow >= getRowCount();
      int proto = appendRow - 1;
      // allocate the new rows
      for(int i = appendRow; i < appendRow + nrow; i++) {
         ncells[i] = new Cell[getColCount()];
      }

      // copy the column spanning information
      for(int i = 0; i < ncells[0].length; i++) {
         Cell cell = cells[proto][i];

         if(proto >= 0 && cell != null && cell.span != null) {
            Rectangle span = cells[proto][i].span;

            for(int j = appendRow; j < appendRow + nrow; j++) {
               if(ncells[j][i] == null) {
                  ncells[j][i] = new Cell();
               }

               ncells[j][i].span = new Rectangle(span);

               // column appended after a span cell
               if(toend || span.y == 0 || span.y < 0 && span.height == Math.abs(span.y) + 1) {
                  // copy col span setting
                  ncells[j][i].span.y = 0;
                  ncells[j][i].span.height = 1;
               }
            }

            // if appended to the middle of a span cell, extend the span cell
            if(!toend && proto < cells.length && span.height > Math.abs(span.y) + 1) {
               int nheight = cells[proto][i].span.height + nrow;
               int srow = proto + cells[proto][i].span.y;

               for(int k = srow; k < srow + nheight; k++) {
                  ncells[k][i].span.y = srow - k;
                  ncells[k][i].span.height = nheight;
               }
            }
         }
      }

      // append to rows array
      Row[] rows2 = new Row[rows.length + nrow];
      Integer orient = rows[proto].orient;

      System.arraycopy(rows, 0, rows2, 0, appendRow);
      System.arraycopy(rows, appendRow, rows2, appendRow + nrow,
         getRowCount() - appendRow );

      for(int i = appendRow; i < appendRow + nrow; i++) {
         rows2[i] = new Row();
         rows2[i].orient = orient;
      }

      rows = rows2;
      cells = ncells;
   }

   /**
    * append cols after the specified row. To add cols to the end of
    * the table, use col count as the col index.
    * @param selreg selected cells.
    * @param ncol number of rows to append.
    */
   public void appendColumns(Rectangle selreg, int ncol) {
      int appendCol = selreg.x + selreg.width;
      boolean toend = appendCol >= getColCount();
      int proto = appendCol - 1;

      for(int i = 0; i < cells.length; i++) {
         Cell[] nrow = new Cell[cells[i].length + ncol];
         System.arraycopy(cells[i], 0, nrow, 0, appendCol);
         System.arraycopy(cells[i], appendCol, nrow, appendCol + ncol, cells[i].length - appendCol);
         Cell ocell = cells[i][proto];

         // adjust span information
         if(proto >= 0 && ocell != null && ocell.span != null) {
            Rectangle span = cells[i][proto].span;

            for(int j = appendCol; j < appendCol + ncol; j++) {
               if(nrow[j] == null) {
                  nrow[j] = new Cell();
               }

               nrow[j].span = new Rectangle(span);

               // column appended after a span cell
               if(toend || span.x == 0 || span.x < 0 && span.width == Math.abs(span.x) + 1) {
                  // copy row span setting
                  nrow[j].span.x = 0;
                  nrow[j].span.width = 1;
               }
            }

            // if appended to the middle of a span cell, extend the span cell
            if(!toend && proto < getColCount() && span.width > Math.abs(span.x) + 1) {
               int nwidth = cells[i][proto].span.width + ncol;
               int scol = proto + cells[i][proto].span.x;

               for(int k = scol; k < scol + nwidth; k++) {
                  nrow[k].span.x = scol - k;
                  nrow[k].span.width = nwidth;
               }
            }
         }

         cells[i] = nrow;
      }

      // append to cols array
      Column[] cols2 = new Column[cols.length + ncol];
      System.arraycopy(cols, 0, cols2, 0, appendCol);
      System.arraycopy(cols, appendCol, cols2, appendCol + ncol, getColCount() - appendCol);

      for(int i = appendCol; i < appendCol + ncol; i++) {
         cols2[i] = new Column();
      }

      cols = cols2;
   }

   /**
    * Insert rows before the specified row. To add rows to the end of
    * the table, use row count as the row index.
    * @param r row index.
    * @param nrow number of rows to insert.
    */
   public void insertRows(int r, int nrow) {
      Cell[][] ncells = new Cell[getRowCount() + nrow][];

      System.arraycopy(cells, 0, ncells, 0, r);
      System.arraycopy(cells, r, ncells, r + nrow, getRowCount() - r);

      boolean toend = r >= getRowCount();
      int proto = toend ? (getRowCount() - 1) : r;

      // allocate the new rows
      for(int i = r; i < r + nrow; i++) {
         ncells[i] = new Cell[getColCount()];
      }

      // copy the column spanning information
      for(int i = 0; i < ncells[0].length; i++) {
         // adjust span information
         if(proto >= 0 && cells[proto][i] != null &&
            cells[proto][i].span != null) {
            for(int j = r; j < r + nrow; j++) {
               if(ncells[j][i] == null) {
                  ncells[j][i] = new Cell();
               }

               ncells[j][i].span = new Rectangle(cells[proto][i].span);

               // column inserted before a span cell
               if(toend || cells[proto][i].span.y == 0) {
                  // copy col span setting
                  ncells[j][i].span.y = 0;
                  ncells[j][i].span.height = 1;
               }
            }

            // if inserted to the middle of a span cell, extend the span cell
            if(!toend && proto < cells.length && cells[proto][i] != null &&
               cells[proto][i].span != null && cells[proto][i].span.y < 0) {
               int nheight = cells[proto][i].span.height + nrow;
               int srow = proto + cells[proto][i].span.y;

               for(int k = srow; k < srow + nheight; k++) {
                  ncells[k][i].span.y = srow - k;
                  ncells[k][i].span.height = nheight;
               }
            }
         }
      }

      // insert to rows array
      Row[] rows2 = new Row[rows.length + nrow];
      Integer orient = rows[proto].orient;

      System.arraycopy(rows, 0, rows2, 0, r);
      System.arraycopy(rows, r, rows2, r + nrow, getRowCount() - r);

      for(int i = r; i < r + nrow; i++) {
         rows2[i] = new Row();
         rows2[i].orient = orient;
      }

      rows = rows2;
      cells = ncells;
   }

   /**
    * Insert columns before the specified column. To add columns to the
    * end of the table, use column count as the column index.
    * @param c column index.
    * @param ncol number of columns to insert.
    */
   public void insertCols(int c, int ncol) {
      boolean toend = c >= getColCount();
      int proto = toend ? (getColCount() - 1) : c;

      for(int i = 0; i < cells.length; i++) {
         Cell[] row = new Cell[cells[i].length + ncol];

         System.arraycopy(cells[i], 0, row, 0, c);
         System.arraycopy(cells[i], c, row, c + ncol, cells[i].length - c);

         // adjust span information
         if(proto >= 0 && cells[i][proto] != null &&
            cells[i][proto].span != null) {
            for(int j = c; j < c + ncol; j++) {
               if(row[j] == null) {
                  row[j] = new Cell();
               }

               row[j].span = new Rectangle(cells[i][proto].span);

               // column inserted before a span cell
               if(toend || cells[i][proto].span.x == 0) {
                  // copy row span setting
                  row[j].span.x = 0;
                  row[j].span.width = 1;
               }
            }

            // if inserted to the middle of a span cell, extend the span cell
            if(!toend && proto < cells[i].length && cells[i][proto] != null &&
               cells[i][proto].span != null && cells[i][proto].span.x < 0) {
               int nwidth = cells[i][proto].span.width + ncol;
               int scol = proto + cells[i][proto].span.x;

               for(int k = scol; k < scol + nwidth; k++) {
                  row[k].span.x = scol - k;
                  row[k].span.width = nwidth;
               }
            }
         }

         cells[i] = row;
      }

      // insert to cols array
      Column[] cols2 = new Column[cols.length + ncol];

      System.arraycopy(cols, 0, cols2, 0, c);
      System.arraycopy(cols, c, cols2, c + ncol, getColCount() - c);

      for(int i = c; i < c + ncol; i++) {
         cols2[i] = new Column();
      }

      cols = cols2;
   }

   /**
    * Remove rows at specified row.
    * @param r row index.
    * @param nrow number of rows.
    */
   public void removeRows(int r, int nrow) {
      checkBounds(r, 0);

      if(getRowCount() == 1) {
         throw new RuntimeException("Can't remove the only row in grid");
      }

      nrow = Math.min(nrow, getRowCount() - r);

      // adjust spanning
      for(int i = r; i < r + nrow; i++) {
         for(int j = 0; j < getColCount(); j++) {
            if(cells[i][j] != null && cells[i][j].span != null) {
               Rectangle span = new Rectangle(cells[i][j].span);
               // adj is the overlap of the span region with the deleted rows
               int adj = Math.min(i + span.y + span.height, r + nrow) -
                  Math.max(i + span.y, r);

               // nothing to do
               if(adj <= 0) {
                  continue;
               }

               // reduce spanning
               int sr = i + span.y;
               int sc = j + span.x;

               for(int rr = sr; rr < sr + span.height; rr++) {
                  for(int cc = sc; cc < sc + span.width; cc++) {
                     if(rr < r) {
                        cells[rr][cc].span.height -= adj;
                     }
                     else if(rr >= r + nrow) {
                        cells[rr][cc].span.y += adj;
                        cells[rr][cc].span.height -= adj;
                     }
                     else {
                        cells[rr][cc].span = null;
                     }
                  }
               }
            }
         }
      }

      Cell[][] ncells = new Cell[cells.length - nrow][];

      System.arraycopy(cells, 0, ncells, 0, r);
      System.arraycopy(cells, r + nrow, ncells, r, ncells.length - r);

      Row[] rows2 = new Row[rows.length - nrow];

      System.arraycopy(rows, 0, rows2, 0, r);
      System.arraycopy(rows, r + nrow, rows2, r, rows2.length - r);
      rows = rows2;
      cells = ncells;
   }

   /**
    * Remove columns at specified column.
    * @param c column index.
    * @param ncol number of columns.
    */
   public void removeCols(int c, int ncol) {
      checkBounds(0, c);
      ncol = Math.min(ncol, getColCount() - c);

      if(getColCount() == 1) {
         throw new RuntimeException("Can't remove the only column in grid");
      }

      // adjust spanning
      for(int i = 0; i < getRowCount(); i++) {
         for(int j = c; j < c + ncol; j++) {
            if(cells[i][j] != null && cells[i][j].span != null) {
               Rectangle span = new Rectangle(cells[i][j].span);
               // adj is the overlap of the span region with the deleted cols
               int adj = Math.min(j + span.x + span.width, c + ncol) -
                  Math.max(j + span.x, c);

               // nothing to do
               if(adj <= 0) {
                  continue;
               }

               // reduce spanning
               int sr = i + span.y;
               int sc = j + span.x;

               for(int rr = sr; rr < sr + span.height; rr++) {
                  for(int cc = sc; cc < sc + span.width; cc++) {
                     if(cc < c) {
                        cells[rr][cc].span.width -= adj;
                     }
                     else if(cc >= c + ncol) {
                        cells[rr][cc].span.x += adj;
                        cells[rr][cc].span.width -= adj;
                     }
                     else {
                        cells[rr][cc].span = null;
                     }
                  }
               }
            }
         }
      }

      // remove columns
      for(int i = 0; i < cells.length; i++) {
         Cell[] row = new Cell[cells[i].length - ncol];

         System.arraycopy(cells[i], 0, row, 0, c);
         System.arraycopy(cells[i], c + ncol, row, c, row.length - c);
         cells[i] = row;
      }

      Column[] cols2 = new Column[cols.length - ncol];

      System.arraycopy(cols, 0, cols2, 0, c);
      System.arraycopy(cols, c + ncol, cols2, c, cols2.length - c);
      cols = cols2;
   }

   /**
    * Add a shape to the report. The shape is drawn as the background of
    * the report.
    * @param shape a shape (line, rectangle, or oval).
    */
   public void addShape(PageLayout.Shape shape) {
      shapes.addElement(shape);
   }

   /**
    * Get the number of shapes contained in this report.
    */
   public int getShapeCount() {
      return shapes.size();
   }

   /**
    * Get the specified shape.
    */
   public PageLayout.Shape getShape(int idx) {
      return (PageLayout.Shape) shapes.elementAt(idx);
   }

   /**
    * Remove the specified shape.
    */
   public void removeShape(int idx) {
      shapes.removeElementAt(idx);
   }

   /**
    * The following methods are used to add elements to the sheet.
    */

   /**
    * Add an object to the document. First the TabularSheet checks if
    * a presenter is register for this type of object. If there is
    * a presenter, a PresenterPainter is created to paint the object
    * using the presenter.
    * <p>
    * If there is no presenter registered at the document for this
    * type of object, the TabularSheet then check if a Format is
    * register for this class. If there is a Format, it's used to
    * format the object into string and treated as a regular text.
    * <p>
    * If there is no format registered for this object type, the
    * object is converted to a string (toString()) and treated as
    * a regular text element.
    * @param r row index.
    * @param c column index.
    * @param obj object value.
    * @return element id.
    */
   public String addObject(int r, int c, Object obj) {
      Presenter presenter = (Presenter) presentermap.get(obj.getClass());
      String id = null;

      if(presenter != null) {
         id = addElement(r, c,
            new PainterElementDef(this, new PresenterPainter(obj, presenter)));
      }
      else {
         id = addText(r, c, toString(obj));
      }

      return id;
   }

   /**
    * Add a text element to the document. The text string can be a simple
    * string, or contains multiple lines separated by the newline
    * character.
    * @param r row index.
    * @param c column index.
    * @param text text string.
    * @return element id.
    */
   public String addText(int r, int c, String text) {
      return addText(r, c, new DefaultTextLens(text));
   }

   /**
    * Add a text element to the document. The TextLens provides an extra
    * level of indirection. For example, it can be used to refer to a
    * TextField on a GUI screen, the most up-to-date value in the text
    * field will be used when printing the document. This way a TabularSheet
    * can be created once, and don't need to be modified when the
    * text contents change.
    * <p>
    * The inetsoft.report.lens package also contains a StreamTextLens, which
    * allows retrieving text from a file, URL, or any input stream.
    * @param r row index.
    * @param c column index.
    * @param text text content lens.
    * @return element id.
    */
   public String addText(int r, int c, TextLens text) {
      return addElement(r, c, new TextElementDef(this, text));
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param r row index.
    * @param c column index.
    * @param text text content.
    * @return element id.
    */
   public String addTextBox(int r, int c, TextLens text) {
      return addElement(r, c, new TextBoxElementDef(this, text));
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param r row index.
    * @param c column index.
    * @param text text content.
    * @return element id.
    */
   public String addTextBox(int r, int c, String text) {
      return addTextBox(r, c, new DefaultTextLens(text));
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param r row index.
    * @param c column index.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param winch area width in inches. Pass 0 to use default.
    * @param hinch area height in inches. Pass 0 to use default.
    * @param textalign text alignment within the box.
    * @return element id.
    */
   public String addTextBox(int r, int c, TextLens text, int border,
                            double winch, double hinch, int textalign) {
      TextBoxElement box = new TextBoxElementDef(this, text, winch, hinch);

      box.setBorder(border);
      box.setTextAlignment(textalign);
      return addElement(r, c, box);
   }

   /**
    * Add a text box to the document. A text box is a standalone area
    * on the document that contains a text string. It has a border around
    * by default. The text box element is similar to the painter elements
    * in that they both use the painter layout and wrapping options. The
    * current setting of the painter layout and wrapping are used by the
    * text box elements as well.
    * @param r row index.
    * @param c column index.
    * @param text text content.
    * @param border border line style. One of the line styles defined in
    * the StyleConstants class.
    * @param winch area width in inches. Pass 0 to use default.
    * @param hinch area height in inches. Pass 0 to use default.
    * @param textalign text alignment within the box.
    * @return element id.
    */
   public String addTextBox(int r, int c, String text, int border,
                            double winch, double hinch, int textalign) {
      return addTextBox(r, c, new DefaultTextLens(text), border, winch, hinch,
         textalign);
   }

   /**
    * Add a painter element to the document. A painter is a self contained
    * object that can paint a document area. It can be used to add any
    * content to the document, through which the program has full control
    * on exact presentation on the document. Painter is the general
    * mechanism used to support some of the more common data types. For
    * example, Component and Image are handled internally by a painter
    * object. The program is free to define its own painter.
    * @param r row index.
    * @param c column index.
    * @param area the painter element.
    * @return element id.
    */
   public String addPainter(int r, int c, Painter area) {
      if(area instanceof ScaledPainter) {
         Size size = ((ScaledPainter) area).getSize();

         return addPainter(r, c, area, size.width, size.height);
      }

      return addElement(r, c, new PainterElementDef(this, area));
   }

   /**
    * This is same as addPainter() except an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param r row index.
    * @param c column index.
    * @param area the painter element.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   public String addPainter(int r, int c, Painter area,
                            double winch, double hinch) {
      return addElement(r, c, new PainterElementDef(this, area, winch, hinch));
   }

   /**
    * Add a chart to the report. The chart behaves like a painter.
    * It reserves an area and paints a chart on the area. The current
    * PainterLayout value is also applied to the chart.
    * @param r row index.
    * @param c column index.
    * @param chart chart data model.
    * @return element id.
    */
   public String addChart(int r, int c, DataSet chart) {
      return addElement(r, c, new ChartElementDef(this, chart));
   }

   /**
    * This is same as addChart() exception an explicit size of the
    * chart area is specified. The specified size (in inches) is the
    * preferred size of this chart on paper, where as the preferred
    * size of the Chart object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Chart.getPreferredSize)
    * to the area defined.
    * @param r row index.
    * @param c column index.
    * @param chart the chart element.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   public String addChart(int r, int c, DataSet chart,
                          double winch, double hinch) {
      return addElement(r, c, new ChartElementDef(this, chart, winch, hinch));
   }

   /**
    * Add an AWT component to the document. The onscreen image of the
    * component is 'copied' on to the document.
    * @param r row index.
    * @param c column index.
    * @param comp component.
    * @return element id.
    */
   public String addComponent(int r, int c, Component comp) {
      return addPainter(r, c, new ComponentPainter(comp));
   }

   /**
    * This is same as addComponent() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The component is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param r row index.
    * @param c column index.
    * @param component component to paint.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   public String addComponent(int r, int c, Component component, double winch, double hinch) {
      return addPainter(r, c, new ComponentPainter(component), winch, hinch);
   }

   /**
    * Add an image to the document.
    * @param r row index.
    * @param c column index.
    * @param image image object.
    * @return element id.
    */
   public String addImage(int r, int c, Image image) {
      ImageElementDef elem = new ImageElementDef(this);
      elem.setPainter(new ImagePainter(image));
      return addElement(r, c, elem);
   }

   /**
    * This is same as addImage() exception an explicit size of the
    * painter area is specified. The specified size (in inches) is the
    * preferred size of this painter on paper, where as the preferred
    * size of the Painter object is treated as the preferred pixels
    * of the area. The pixels in the specified area may be different
    * depending on the resolution of the output media. The image is
    * scaled from the pixel size (returned by Painter.getPreferredSize)
    * to the area defined.
    * @param r row index.
    * @param c column index.
    * @param image image to paint.
    * @param winch area width in inches.
    * @param hinch area height in inches.
    * @return element id.
    */
   public String addImage(int r, int c, Image image, double winch, double hinch) {
      ImageElementDef elem = new ImageElementDef(this);
      elem.setPainter(new ImagePainter(image));

      if(winch > 0 && hinch > 0) {
         elem.setSize(new Size((float) winch, (float) hinch));
      }

      return addElement(r, c, elem);
   }

   /**
    * Add an image to the document.
    * @param r row index.
    * @param c column index.
    * @param image image URL.
    * @return element id.
    */
   public String addImage(int r, int c, URL image) throws IOException {
      return addImage(r, c, Tool.getImage(image.openStream()));
   }

   /**
    * Add horizontal space to the document. The space is added after the
    * current element.
    * @param r row index.
    * @param c column index.
    * @param pixels space in pixels.
    * @return element id.
    */
   public String addSpace(int r, int c, int pixels) {
      return addElement(r, c, new SpaceElementDef(this, pixels));
   }

   /**
    * Add one or more newline to the document.
    * @param r row index.
    * @param c column index.
    * @param n number of newline.
    * @return element id.
    */
   public String addNewline(int r, int c, int n) {
      return addElement(r, c, new NewlineElementDef(this, n, false));
   }

   /**
    * Add a break to the document. The break is different from a newline
    * in that the break does not reset the hanging indent which is set
    * by a bullet.
    * @param r row index.
    * @param c column index.
    * @return element id.
    */
   public String addBreak(int r, int c) {
      return addElement(r, c, new NewlineElementDef(this, 1, true));
   }

   /**
    * Add a page break to the document. This causes the print to advance
    * to a new page.
    * @param r row index.
    * @param c column index.
    * @return element id.
    */
   public String addPageBreak(int r, int c) {
      return addElement(r, c, new PageBreakElementDef(this));
   }

   /**
    * Add a conditional page break. The print advance to a new page if
    * the remaining space in the current page is less than the specified
    * minimum space.
    * @param r row index.
    * @param c column index.
    * @param min minimu space in pixels.
    * @return element id.
    */
   public String addConditionalPageBreak(int r, int c, int min) {
      return addElement(r, c, new CondPageBreakElementDef(this, min));
   }

   /**
    * Add a conditional page break. The print advance to a new page if
    * the remaining space in the current page is less than the specified
    * minimum space.
    * @param r row index.
    * @param c column index.
    * @param inch minimu space in inches.
    * @return element id.
    */
   public String addConditionalPageBreak(int r, int c, double inch) {
      return addElement(r, c, new CondPageBreakElementDef(this, inch));
   }

   /**
    * Add a table to the document. The table lens object encapsulates the
    * table attributes and contents. Through the table lens, the print
    * discovers table attributes such as color, border, font, etc..
    * For more details, refer the TableLens document.
    * @param r row index.
    * @param c column index.
    * @param table table lens.
    * @return element id.
    */
   public String addTable(int r, int c, TableLens table) {
      return addElement(r, c, new TableElementDef(this, table));
   }

   /**
    * Add an element to the document. Classes extending the TabularSheet
    * can extend element classes from the Element, and use
    * this method for adding the element to the document.
    * @param r row index.
    * @param c column index.
    * @param e document element.
    * @return element id.
    */
   public String addElement(int r, int c, ReportElement e) {
      checkBounds(r, c);

      BaseElement elem = (BaseElement) e;

      if(elem.isNewline() && !elem.isContinuation()) {
         hindent = 0;
      }

      elem.setReport(this);

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      if(cells[r][c].elements == null) {
         cells[r][c].elements = new Vector();
      }

      cells[r][c].elements.addElement(e);
      return e.getID();
   }

   /**
    * Return the number of elements in the document.
    * @param r row index.
    * @param c column index.
    * @return number of elements.
    */
   public int getElementCount(int r, int c) {
      checkBounds(r, c);

      if(cells[r][c] != null && cells[r][c].elements != null) {
         return cells[r][c].elements.size();
      }

      return 0;
   }

   /**
    * Get the specified element.
    * @param r row index.
    * @param c column index.
    * @param idx element index.
    * @return document element.
    */
   public ReportElement getElement(int r, int c, int idx) {
      if(cells[r][c] != null && cells[r][c].elements != null) {
         return (ReportElement) cells[r][c].elements.elementAt(idx);
      }

      return null;
   }

   /**
    * Find the cell an element is contained.
    * @param e element.
    * @return cell where the element is contained.
    */
   public Point getElementCell(ReportElement e) {
      for(int i = 0; i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            if(getElementIndex(i, j, e) >= 0) {
               return new Point(j, i);
            }
         }
      }

      return null;
   }

   /**
    * Get the index of the specified element.
    * @param r row index.
    * @param c column index.
    * @param e element.
    * @return element index.
    */
   public int getElementIndex(int r, int c, ReportElement e) {
      checkBounds(r, c);
      int result = -1;

      if(cells[r][c] != null && cells[r][c].elements != null) {
         result = cells[r][c].elements.indexOf(e);
      }

      return result;
   }

   /**
    * Remove the specified element.
    * @param r row index.
    * @param c column index.
    * @param idx element index.
    */
   public void removeElement(int r, int c, int idx) {
      checkBounds(r, c);

      if(cells[r][c] != null && cells[r][c].elements != null) {
         synchronized(cells[r][c].elements) {
            if(idx >= 0 && idx < cells[r][c].elements.size()) {
               cells[r][c].elements.removeElementAt(idx);
            }
         }
      }
   }

   /**
    * Remove all elements from the specified cell.
    * @param r row index.
    * @param c column index.
    */
   public void removeAllElements(int r, int c) {
      checkBounds(r, c);

      if(cells[r][c] != null && cells[r][c].elements != null) {
         cells[r][c].elements.removeAllElements();
      }
   }

   /**
    * Remove the specified element.
    * @param id element id in string format.
    */
   @Override
   public synchronized void removeElement(String id) {
      ReportElement elem = getElement(id);
      Point pos = getElementCell(elem);

      if(pos != null) {
         int idx = getElementIndex(pos.y, pos.x, elem);

         ReportElement cell = getElement(pos.y, pos.x, idx);

         if(cell == elem) {
            removeElement(pos.y, pos.x, idx);
         }
      }
      else {
         removeHeaderFooterElement(elem);
      }
   }

   /**
    * Replace the specified element.
    * @param r row index.
    * @param c column index.
    * @param idx element index.
    */
   protected void replaceElement(int r, int c, int idx, ReportElement e) {
      checkBounds(r, c);

      if(cells[r][c] != null && cells[r][c].elements != null) {
         synchronized(cells[r][c].elements) {
            if(idx >= 0 && idx < cells[r][c].elements.size()) {
               cells[r][c].elements.setElementAt(e, idx);
            }
         }
      }
   }

   /**
    * Replace the specified element.
    * @param id element id in string format.
    */
   @Override
   protected synchronized void replaceElement(String id, ReportElement e) {
      ReportElement elem = this.getElement(id);
      Point pos = getElementCell(elem);

      if(pos != null) {
         int idx = getElementIndex(pos.y, pos.x, elem);

         replaceElement(pos.y, pos.x, idx, e);
      }
      else {
         replaceHeaderFooterElement(elem, e);
      }
   }

   /**
    * Move element up or down.
    * @param id element id in string format.
    * @param direction move direction, up or down.
    */
   @Override
   public synchronized void moveElement(String id, int direction) {
      ReportElement elem = this.getElement(id);
      Point pos = getElementCell(elem);

      if(pos != null) {
         moveElement(elem, pos, direction);
      }
      else {
         moveHeaderFooterElement(elem, direction);
      }
   }

   /**
    * Move element up or down.
    * @param elem the element.
    * @direction moving direction, up or down.
    */
   private synchronized void moveElement(
      ReportElement elem, Point pos, int direction) {
      int idx = getElementIndex(pos.y, pos.x, elem);
      int oidx = idx;
      int count = (direction == UP) ? 0 : getElementCount(pos.y, pos.x);
      int dir = (direction == UP) ? 1 : -1;
      int delta = (direction == UP) ? 1 : 0;

      for(idx -= dir; (count + idx * dir) > 0; idx -= dir) {
         BaseElement em2 = (BaseElement) getElement(pos.y, pos.x, idx);

         if(!em2.isFlowControl() || em2 instanceof PageBreakElement) {
            break;
         }
      }

      if((count + idx * dir + delta) > 0) {
         if(direction == UP) {
            removeElement(pos.y, pos.x, oidx);
            insertElement(pos.y, pos.x, idx, elem);
         }
         else {
            insertElement(pos.y, pos.x, idx + 1, elem);
            removeElement(pos.y, pos.x, oidx);
         }
      }
   }

   /**
    * Insert the element at specified position (before).
    * @param idx position to insert.
    * @param r row index.
    * @param c column index.
    * @param e element.
    * @return element ID.
    */
   public String insertElement(int r, int c, int idx, ReportElement e) {
      checkBounds(r, c);
      hindent = 0;

      if(cells[r][c] == null) {
         cells[r][c] = new Cell();
      }

      if(cells[r][c].elements == null) {
         cells[r][c].elements = new Vector();
      }

      synchronized(cells[r][c].elements) {
         if(idx >= 0 && idx <= cells[r][c].elements.size()) {
            cells[r][c].elements.insertElementAt(e, idx);
         }
      }

      return e.getID();
   }

   /**
    * Get all elements in the report body.
    * @return vector of all elements.
    */
   @Override
   public Vector getAllElements() {
      Vector all = new Vector();

      for(int r = 0; r < cells.length; r++) {
         for(int c = 0; c < cells[r].length; c++) {
            if(cells[r][c] != null && cells[r][c].elements != null) {
               append(all, cells[r][c].elements);
            }
         }
      }

      return all;
   }

   /**
    * Print one page. Return true if more contents need to be printed.
    * Normally print(PrintJob) should be used for printing. This function
    * is used by print() to print individual pages.
    * <p>
    * A StylePage contains information on how to print a particular page.
    * Its print() method can be used to perform the actual printing of
    * the page contents to a printer graphics.
    *
    * @param pg style page.
    */
   @Override
   public synchronized boolean printNext(StylePage pg) {
      Dimension pgsize = pg.getPageDimension();
      pmargin = getPrinterMargin();
      printBox = new Rectangle((int) ((cmargin.left - pmargin.left) * 72),
         (int) ((cmargin.top - pmargin.top) * 72),
         pgsize.width - (int) ((cmargin.left + cmargin.right) * 72),
         pgsize.height - (int) ((cmargin.top + cmargin.bottom) * 72));
      frames = npframes = null;
      pageBox = printBox;

      boolean more = printNextPage(pg);

      // clear memory cache, used internally to conserve memory
      if(!more) {
         ObjectCache.clear();
      }

      return more;
   }

   /**
    * Get the page orientation for the next page. The page orientation
    * can be changed in middle of a report by setting it in PageLayout
    * for StyleSheet, or setting the row orientation in a TabularSheet.
    * @return the next page orientation. Null if using default orientation.
    */
   @Override
   public Integer getNextOrientation() {
      if(currR < rows.length) {
         if(rows[currR].orient != null) {
            nextOrient = rows[currR].orient;
         }

         if(rows[currR].margin != null) {
            cmargin = rows[currR].margin;
         }
      }

      return super.getNextOrientation();
   }

   /**
    * Print the next page. It may be called to print from a middle of
    * of page provided the printBox is setup correctly before the call.
    */
   @Override
   public boolean printNextPage(StylePage pg) {
      // the first time the report is printed, call onLoad
      if(!calced) {
         calcWidths(pageBox.width);
      }

      pg.setBackground(getBackground());
      pg.setBackgroundLayout(getBackgroundLayout());
      pg.setBackgroundSize(getBackgroundSize());

      if(currR >= getRowCount()) {
         return false;
      }

      normalizeTabularGrid(this);

      // calculate the top position in the virtual report coord space
      Rectangle vclip = new Rectangle();

      for(int i = 0; i <= currR; i++) {
         vclip.y += rows[i].advanced;
      }

      // calculate the width
      float w = 0;

      for(int i = 0; i < cols.length; i++) {
         w += cols[i].points;
      }

      vclip.width = (int) w;

      float y = printBox.y - pageBox.y; // could start in middle for subreport
      // the row positions
      float[] nexty = new float[getRowCount() + 1];
      int max_row = getRowCount();
      // true if the column has finished printing
      boolean[] done = new boolean[getColCount()];
      int topR = currR;
      Vector heights = new Vector();

      // @by larryl, can't use pageBox.y since it could be a subreport
      GridPaintable pt = new GridPaintable(printBox.x, printBox.y);
      pg.addPaintable(pt);

      boolean first = true;
      int shapeIdx = pg.getPaintableCount();

      // calculate each column's width before print a report as a single page.
      // If there is a Table components with "Fit Content" property
      // in the cells, then extend the column's width to the
      // actual width of the Table component.
      // If there are two or more Table components with "Fit Content" property,
      // then extend the column's width to the maximum actual width of them.
      String prop = getProperty("autoWidth");
      boolean autoWidth = prop != null && prop.equals("true");
      float pageWidthWithoutMargin = 0;

       if(autoWidth) {
         for(int col = 0; col < getColCount(); col++) {
            for(int row = 0; row < getRowCount(); row++) {
               for(int i = 0; i < getElementCount(row, col); i++) {
                  ReportElement elem = getElement(row, col, i);
                  float colWidthSum = getElementWidth(elem);

                  cols[col].points = Math.max(colWidthSum, cols[col].points);
               }
            }

            // @by dot_xu, for implementing the feature feature1214524685332,
            // we assume the reserved gap is 2 pixels.
            cols[col].points += 2;
            pageWidthWithoutMargin += cols[col].points;
         }

         Dimension d = pg.getPageDimension();

         // add the margins of the left and right sides of the page
         // 1 inch = 72 pixels
         double pageWidth = pageWidthWithoutMargin +
            (getMargin().left + getMargin().right) * 72;

         if(pageWidth > d.width) {
            // set the sum of the columns' width as the page's width
            pg.setPageDimension(new Dimension((int) pageWidth, d.height));

            // also extend the printBox and oprintBox's width
            printBox.width = (int) pageWidth;
         }
      }

      // print cells
      for(int irow = 0; currR < getRowCount(); currR++, irow++) {
         if(rows[currR].orient != null &&
            (nextOrient == null || !nextOrient.equals(rows[currR].orient)))
         {
            nextOrient = rows[currR].orient;
            break;
         }

         if(!first && rows[currR].margin != null &&
            !cmargin.equals(rows[currR].margin))
         {
            break;
         }

         for(int i = 0; i < getColCount(); i++) {
            // if the column is already done, ignore
            if(done[i]) {
               continue;
            }

            Cell cell = cells[currR][i];
            Rectangle span = null;

            // middle of span cell, get the upper-left corner
            if(cell != null && cell.span != null) {
               span = cell.span;

               // not the first column in span cell, ignore
               if(span.x != 0) {
                  continue;
               }

               // middle of span cell and not the top of page, ignore
               if(span.y != 0 && currR > topR) {
                  continue;
               }

               cell = cells[currR + span.y][i];
            }

            if(cell != null) {
               boolean more = cell.printNext(pg, currR, i, y, pageBox, this);
               done[i] = more;

               if(more) {
                  if(span != null && span.height > 1) {
                     max_row = Math.min(max_row,
                        currR + span.y + span.height - 1);

                     // @by larryl, if the orientation or margin changed in
                     // middle of the span cell, it may cause the layout to
                     // be wrong. The following loop forces the cell to finish
                     // before the switch can happen
                     for(int k = currR + 1; k < max_row + 1 && k < rows.length;
                         k++)
                     {
                        if(rows[k].margin != null && cmargin != null &&
                           !cmargin.equals(rows[k].margin) ||
                           rows[k].orient != null && nextOrient != null &&
                           !nextOrient.equals(rows[k].orient))
                        {
                           max_row = k - 1;
                           break;
                        }
                     }
                  }
                  else {
                     max_row = currR;
                  }
               }

               int nextR = (span == null || span.height <= 1) ?
                  (currR + 1) :
                  (currR + span.y + span.height);

               nexty[nextR] = Math.max(nexty[nextR], printHead.y + y);
            }
         }

         // make sure the minimum row height is satisfied
         for(int k = currR; k < getRowCount(); k++) {
            if(rows[k].height > 0 && rows[k].height > rows[k].advanced) {
               int minH = rows[k].height - rows[k].advanced;

               nexty[k + 1] = Math.max(nexty[k + 1], y + minH);
               nexty[k + 1] = Math.min(nexty[k + 1], pageBox.height);
            }

            // record the actual row height
            nexty[k + 1] = (float) Math.ceil(nexty[k + 1]); // at point boundary
         }

         // add a minimum height to a row, only if the row is printed first time
         if(nexty[currR + 1] == y && rows[currR].advanced == 0) {
            nexty[currR + 1] += MIN_HEIGHT;
         }

         // if continue to next page, fill the current page
         // @by mikec, IBM JDK1.4.2 has bug in Math.max.
         if(nexty[currR + 1] < pageBox.height) {
            if(currR >= max_row) {
               for(int i = 0; i < getColCount(); i++) {
                  Cell cell = cells[currR][i];

                  if(cell != null && cell.fitPage) {
                     nexty[currR + 1] = pageBox.height;
                  }
               }
            }
            else if(rows[currR].height < 0) {
               nexty[currR + 1] = pageBox.height;
            }
         }
         else if(nexty[currR + 1] > pageBox.height) {
            nexty[currR + 1] = pageBox.height;
         }

         // @by larryl, if the current row height is not set, find the nexty
         // and use that for the current row. This can happen if the cells on
         // this row is span cell, so the nexty is set on the row of the bottom
         // of the span cell.
         if(nexty[currR + 1] <= y) {
            for(int k = currR + 2; k < nexty.length; k++) {
               if(nexty[k] > 0) {
                  nexty[currR + 1] = nexty[k];
                  break;
               }
            }
         }

         rows[currR].advanced += nexty[currR + 1] - y;
         heights.addElement(Float.valueOf(nexty[currR + 1] - y));

         // on the last page, make the full page paintable for all shapes
         if(currR == getRowCount() - 1 && currR < max_row) {
            vclip.height = Math.max(vclip.height, pageBox.height);
         }
         else {
            vclip.height += nexty[currR + 1] - y;
         }

         if(currR >= max_row) {
            break;
         }
         else if(rows[currR].height > 0 && rows[currR].advanced < rows[currR].height) {
            // @by mikec, if height is zero and advanced less than height,
            // it will always not be draw and trap into infinite loop.
            break;
         }
         else if(nexty[currR + 1] > pageBox.height - 2) {
            // if the current page is full and the max_row is not not reached
            // it means the current row has been finished and we should advance
            // the row index here
            currR++;
            break;
         }

         first = false;
         y = nexty[currR + 1];
      }

      int[] arr = new int[heights.size()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = ((Number) heights.elementAt(i)).intValue();
      }

      // check the shapes to put in the grid
      for(int i = 0; i < getShapeCount(); i++) {
         PageLayout.Shape shape = getShape(i);
         Rectangle bounds = shape.getBounds();

         // normalize the rectangle
         if(bounds.width < 0) {
            bounds.x += bounds.width;
            bounds.width = -bounds.width;
         }

         if(bounds.height < 0) {
            bounds.y += bounds.height;
            bounds.height = -bounds.height;
         }

         // @by larryl, line may have width/height of 0, change to 1 so it
         // does not fail the intersects test
         bounds.width = Math.max(1, bounds.width);
         bounds.height = Math.max(1, bounds.height);

         bounds.x -= pageBox.x;
         bounds.y -= pageBox.y;

         if(vclip.intersects(bounds)) {
            ShapePaintable shapept = new ShapePaintable(shape);

            shapept.setVirtualClip(vclip);
            shapept.setPrintableBounds(pageBox);

            pg.insertPaintable(shapeIdx++, shapept);
         }
      }

      pt.setGridRegion(topR, arr, this);

      firePageBreakEvent(pg, currR < getRowCount());
      return currR < getRowCount();
   }

   /**
    * Remove all elements from the contents.
    */
   @Override
   protected void removeContents() {
      for(int i = 0; i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            if(cells[i][j] != null) {
               cells[i][j].elements.clear();
            }
         }
      }
   }

   /**
    * Reset all elements in the contents.
    */
   @Override
   protected void resetContents() {
      calced = false;
      currR = 0;

      for(int i = 0; i < cells.length; i++) {
         rows[i].advanced = 0;

         for(int j = 0; j < cells[i].length; j++) {
            if(cells[i][j] != null) {
               cells[i][j].reset();
            }
         }
      }
   }

   /**
    * Check row and column index bounds.
    */
   private void checkBounds(int r, int c) {
      if(r >= cells.length || c >= cells[r].length) {
         throw new IndexOutOfBoundsException("Cell " + r + "," + c +
            " does not exist.");
      }
   }

   /**
    * Calculate the actual column width based on width specifications.
    */
   private void calcWidths(int width) {
      double totalW = 0; // total width
      double totalProp = 0; // total proportion

      // find all pixel width (fixed size)
      for(int i = 0; i < cols.length; i++) {
         String w = cols[i].width;

         if(w == null) {
            w = cols[i].width = "1*";
         }

         String trimStr = null;

         try {
            if(w.endsWith("%")) {
               trimStr = w.substring(0, w.length() - 1).trim();
               double perc = Double.valueOf(trimStr);

               cols[i].points = (float) (perc * width / 100);
               totalW += cols[i].points;
            }
            else if(cols[i].width.endsWith("*")) {
               trimStr = w.substring(0, w.length() - 1).trim();
               cols[i].points = Float.valueOf(trimStr);
               totalProp += cols[i].points;
            }
            else {
               trimStr = cols[i].width;
               cols[i].points = Float.valueOf(trimStr);
               totalW += cols[i].points;
            }

            if(totalW > pageBox.width) {
               if(totalW - cols[i].points < pageBox.width) {
                  cols[i].points = (float) (pageBox.width -
                     (totalW - cols[i].points));
               }
               else {
                  cols[i].points = 0;
               }
            }
         }
         catch(Exception ex) {
            LOG.warn("Invalid number for width of column " +
               i + ": " + trimStr, ex);
            cols[i].width = "1*";
            totalProp += 1;
         }
      }

      // divide the remaining width among the proportional columns.
      for(int i = 0; i < cols.length; i++) {
         if(cols[i].width != null && cols[i].width.endsWith("*")) {
            cols[i].points = (float) (cols[i].points * (width - totalW) /
                                      totalProp);
            if(cols[i].points < 0) {
               cols[i].points = 0;
            }
         }
      }
   }

   /**
    * Divide a width specification.
    */
   private String divideWidth(String w, int div) {
      String sign = "";

      if(w.endsWith("*") || w.endsWith("%")) {
         sign = w.substring(w.length() - 1);
         w = w.substring(0, w.length() - 1);
      }

      return (Double.valueOf(w).doubleValue() / div) + sign;
   }

   public String toString() {
      Object[] param = {getAllHeaderElementCount(), getRowCount(),
                        getColCount(), getAllFooterElementCount()};
      return Catalog.getCatalog().
         getString("designer.tabular.tabularTemplate", param);
   }

   // grid cell information
   private static class Cell implements Serializable, Cloneable {
      /**
       * Reset the cell. Next time the cell is printed from the beginning.
       */
      public void reset() {
         // in case that elements being reset for times, which might re-execute
         // script for times.
         if(elements != null && !inited) {
            for(int k = 0; k < elements.size(); k++) {
               BaseElement elem = ((BaseElement) elements.elementAt(k));
               elem.reset();
            }
         }

         current = 0;
         done = false;
         inited = true;
      }

      /**
       * Reset the inited for cell.
       */
      public void rsetInitd(){
         inited = false;
      }

      /**
       * Print this cell on current page.
       * @param pg output page.
       * @param r cell row index.
       * @param c cell column index.
       * @param y row Y position on the page.
       * @param pageBox page printable area.
       */
      public boolean printNext(StylePage pg, int r, int c, float y,
                               Rectangle pageBox, TabularSheet report) {
         report.printHead = new Position(0, 0);
         Vector elems = elements;

         if(repeat && current >= elems.size()) {
            reset();
         }

         // nothing to print
         if(elems == null || elems.size() == 0 ||
            current >= elems.size())
         {
            return false;
         }

         float x = pageBox.x;

         for(int i = 0; i < c; i++) {
            x += report.cols[i].points;
         }

         // ajdust spanned width
         float w = report.cols[c].points;

         if(span != null) {
            for(int i = 1; i < span.width; i++) {
               w += report.cols[c + i].points;
            }
         }

         // if no width is allocated, return
         if(w == 0) {
            return false;
         }

         // mark this cell as not initialized, then reseting cell is required
         inited = false;
         report.printBox = new Rectangle((int) x, (int) y + pageBox.y,
                                         (int) (w + x - (int) x),
                                         (int) (pageBox.height - y));

         // adjust the printing area for the borders
         int top = 0, left = 0, bot = 0, right = 0;
         // a 1 pixel gap is reserved for sides with borders
         Insets margin = new Insets(0, 0, 0, 0);

         if(borders != null) {
            top = (int) Math.ceil(Common.getLineWidth(borders.top));
            left = (int) Math.ceil(Common.getLineWidth(borders.left));
            bot = (int) Math.ceil(Common.getLineWidth(borders.bottom));
            right = (int) Math.ceil(Common.getLineWidth(borders.right));

            margin.top = (top > 0) ? 1 : 0;
            margin.left = (left > 0) ? 1 : 0;
            margin.bottom = (bot > 0) ? 1 : 0;
            margin.right = (right > 0) ? 1 : 0;

            report.printBox.y += top + margin.top;
            report.printBox.height -= top + bot + margin.top + margin.bottom;
            report.printBox.x += left + margin.left;
            report.printBox.width -= left + right + margin.left + margin.right;
         }

         report.frames = new Rectangle[] {report.printBox};
         report.npframes = new Rectangle[] {
            new Rectangle((int) x + left + margin.left,
                          pageBox.y + top + margin.top,
                          (int) w - left - right - margin.left - margin.right,
                          pageBox.height - top - bot - margin.top -
                          margin.bottom)};
         report.currFrame = 0;
         report.current = this.current;
         int ocurrent = current;
         int optcnt = pg.getPaintableCount();
         done = !report.printNextArea(pg, elems, true);

         // @by larryl, if the last element is page break, the printNextArea
         // would ignore it to avoid an empty last page. Force to advanced to
         // next page if there are more rows below.
         if(done && r < report.getRowCount() - 1 &&
            ((BaseElement) elems.get(elems.size() - 1)).isBreakArea())
         {
            done = false;
         }

         report.keepWithNext(pg, ocurrent, elems);
         current = report.current;

         // @by larryl, if the subreport has page area, the printHead.y is
         // relative to the top of the area, but the tabular grid needs to
         // count its position on the page
         for(int i = optcnt; i < pg.getPaintableCount(); i++) {
            Rectangle bounds = pg.getPaintable(i).getBounds();

            report.printHead.y = Math.max(report.printHead.y,
                                          bounds.y + bounds.height - report.printBox.y);
         }

         // adj header so it's relative to orig printBox
         // it is used by the caller to calculate the total advance
         report.printHead.y += top + bot + margin.top + margin.bottom;

         return !done;
      }

      /**
       * Make a copy of cell.
       */
      @Override
      public Object clone() {
         try {
            Cell cell2 = (Cell) super.clone();

            // deep clone Rectangle and Dimension
            cell2.span = span == null ? null : (Rectangle) span.clone();
            cell2.bgsize = bgsize == null ? null : (Dimension) bgsize.clone();
            cell2.inited = false;

            return cell2;
         }
         catch(Exception ex) {
            LOG.error("Failed to clone tabular sheet", ex);
         }

         return null;
      }

      // variables used during printing
      int current = 0;
      boolean done = false; // true if printing done
      Vector elements = new Vector(); // ReportElement
      Object background = null;
      Dimension bgsize = null; // background size
      int bglayout = StyleConstants.BACKGROUND_TILED;// background layout
      Insets borders = null;
      Color borderColor = null;
      Rectangle span = null;
      boolean repeat = false;
      boolean fitPage = true;
      boolean inited = false;
   }

   // grid row attributes
   private static class Row implements Serializable, Cloneable {
      int height = MIN_HEIGHT;
      Integer orient = null; // orientation
      int advanced = 0; // amount already advanced in this row
      Margin margin = null; // new page margin

      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            LOG.error("Failed to clone row", ex);
         }

         return null;
      }
   }

   // grid column attributes
   private static class Column implements Serializable, Cloneable {
      String width = "1*";
      float points = 0; // calculated based on page width and width spec

      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            LOG.error("Failed to clone column", ex);
         }

         return null;
      }
   }

   @Override
   public Object clone() {
      return clone(true);
   }

   @Override
   public Object clone(boolean deep) {
      TabularSheet report = (TabularSheet) super.clone(deep);
      copyTabularSheet(report, false, deep);

      return report;
   }

   /**
    * Copy this report to that report.
    * @param report the report copied to.
    * @param flag if true flag all copied elements as from template.
    */
   protected void copyTabularSheet(TabularSheet report, boolean flag, boolean deep) {
      copyReportSheet(report, flag, deep);

      report.cells = new Cell[cells.length][cells[0].length];

      for(int i = 0; i < cells.length; i++) {
         for(int j = 0; j < cells[i].length; j++) {
            if(cells[i][j] != null) {
               report.cells[i][j] = (Cell) cells[i][j].clone();

               if(cells[i][j].elements != null) {
                  report.cells[i][j].elements =
                     report.cloneElements(cells[i][j].elements);
               }
            }
         }
      }

      report.rows = new Row[rows.length];

      for(int i = 0; i < rows.length; i++) {
         report.rows[i] = (Row) rows[i].clone();
      }

      report.cols = new Column[cols.length];

      for(int i = 0; i < cols.length; i++) {
         report.cols[i] = (Column) cols[i].clone();
      }

      report.shapes = (Vector) shapes.clone();
   }

   // printing states
   private boolean calced = false; // true if pre-calc done
   private int currR = 0; // current row
   private Cell[][] cells = new Cell[1][1];
   private Row[] rows = {new Row()};
   private Column[] cols = {new Column()};
   private Vector shapes = new Vector(); // shapes on a report
   private static int MIN_HEIGHT;

   private static final Logger LOG = LoggerFactory.getLogger(TabularSheet.class);

   static {
      MIN_HEIGHT = Integer.parseInt(SreeEnv.getProperty("grid.min.height"));
   }
}

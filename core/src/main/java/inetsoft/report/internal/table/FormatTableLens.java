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
package inetsoft.report.internal.table;

import inetsoft.graph.data.CalcColumn;
import inetsoft.report.*;
import inetsoft.report.composition.graph.calc.PercentColumn;
import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.util.FixedSizeSparseMatrix;
import inetsoft.util.SparseMatrix;

import java.awt.*;
import java.text.Format;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

/**
 * FormatTableLens is used to apply table format.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class FormatTableLens extends AttributeTableLens
   implements CachedTableLens, PerRegionTableLens
{
   /**
    * Create a format table lens.
    */
   public FormatTableLens(TableLens table) {
      this(table, true);
   }

   /**
    * Create a format table lens.
    */
   public FormatTableLens(TableLens table, boolean tfmt) {
      super(table);

      headerR = table.getHeaderRowCount();
      check = tfmt;
   }

   /**
    * Get the locale.
    * @return the locale.
    */
   protected abstract Locale getLocale();

   /**
    * Get the format map.
    * @return the format map.
    */
   protected abstract Map<TableDataPath, TableFormat> getFormatMap();

   /**
    * Set the region this table lens is for.
    */
   @Override
   public void setRegion(Rectangle reg) {
      this.reg = reg;
   }

   /**
    * Set the cell span information.
    */
   public void setSpanMap(SpanMap spanmap) {
      this.spanmap = spanmap;
   }

   /**
    * Build the cell span information.
    */
   public void buildSpanMap() {
      spanmap = new SpanMap();

      for(int i = 0; moreRows(i); i++) {
         for(int j = 0; j < getColCount(); j++) {
            Dimension span = getSpan(i, j);

            if(span != null) {
               spanmap.add(i, j, span.height, span.width);
            }
         }
      }
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeFont(table.getFont(r, c),
                       colf == null ? null : colf.font,
                       rowf == null ? null : rowf.font,
                       cellf == null ? null : cellf.font);
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
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeColor(table.getForeground(r, c),
                        colf == null ? null : colf.foreground,
                        rowf == null ? null : rowf.foreground,
                        cellf == null ? null : cellf.foreground);
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
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeColor(table.getBackground(r, c),
                        colf == null ? null : colf.background,
                        rowf == null ? null : rowf.background,
                        cellf == null ? null : cellf.background);
   }

   @Override
   public int getAlpha(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeInt(table.getAlpha(r, c),
         colf == null ? -1 : colf.alpha,
         rowf == null ? -1 : rowf.alpha,
         cellf == null ? -1 : cellf.alpha);
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
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeColor(table.getBackground(r, c, spanRow),
                        colf == null ? null : colf.background,
                        rowf == null ? null : rowf.background,
                        cellf == null ? null : cellf.background);
   }

   /**
    * Return the per cell alignment.
    * @param r row number.
    * @param c column number
    * @return cell alignment for the specified cell
    */
   @Override
   public int getAlignment(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      return mergeAlignment(
         table.getAlignment(r, c),
         colf == null || colf.alignment == null ? -1 : colf.alignment.intValue(),
         rowf == null || rowf.alignment == null ? -1 : rowf.alignment.intValue(),
         cellf == null || cellf.alignment == null ? -1 : cellf.alignment.intValue());
   }

   private int mergeAlignment(int int1, int int2, int int3, int int4) {
      return int4 >= 0 ? int4 : (int3 >= 0 ? int3 : (int2 >= 0 ? int2 : int1));
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
      checkInit();
      int r2 = nextRow(r, c);
      boolean last = !moreRows(r2);
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = (r >= 0) ? getRowTableFormat(r) : null;
      TableFormat cellf = (r >= 0) ? getCellTableFormat(r, c) : null;
      TableFormat rowf2 = !last ? getRowTableFormat(r2) : null;
      TableFormat cellf2 = !last ? getCellTableFormat(r2, c) : null;
      int def = table.getRowBorder(r, c);

      int line = mergeLineStyle(def,
                                colf == null ? null : colf.borders,
                                rowf == null ? null : rowf.borders,
                                cellf == null ? null : cellf.borders,
                                rowf2 == null ? null : rowf2.borders,
                                cellf2 == null ? null : cellf2.borders,
                                true, r < 0, last);

      // check for page before and after, only allow the setting on rows
      if(c == 0) {
         if(rowf != null && rowf.pageAfter ||
            rowf2 != null && rowf2.pageBefore ||
            (def & TableLens.BREAK_BORDER) != 0)
         {
            line = line | TableLens.BREAK_BORDER;
         }
      }

      return line;
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
      checkInit();
      int c2 = nextCol(r, c);
      boolean last = c2 >= getColCount();
      TableFormat colf = (c >= 0) ? getColTableFormat(c) : null;
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = (c >= 0) ? getCellTableFormat(r, c) : null;
      TableFormat colf2 = !last ? getColTableFormat(c2) : null;
      TableFormat cellf2 = !last ? getCellTableFormat(r, c2) : null;

      return mergeLineStyle(table.getColBorder(r, c),
                            colf == null ? null : colf.borders,
                            rowf == null ? null : rowf.borders,
                            cellf == null ? null : cellf.borders,
                            colf2 == null ? null : colf2.borders,
                            cellf2 == null ? null : cellf2.borders,
                            false, c < 0, last);
   }

   /**
    * Return the color for drawing the row border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getRowBorderColor(int r, int c) {
      if(c < 0) {
         return null;
      }

      checkInit();
      int r2 = nextRow(r, c);
      boolean last = !moreRows(r2);
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = (r >= 0) ? getRowTableFormat(r) : null;
      TableFormat cellf = (r >= 0) ? getCellTableFormat(r, c) : null;
      TableFormat rowf2 = !last ? getRowTableFormat(r2) : null;
      TableFormat cellf2 = !last ? getCellTableFormat(r2, c) : null;
      return mergeLineColor(table.getRowBorderColor(r, c),
                            colf, rowf, cellf, rowf2, cellf2,
                            true, c < 0, r < 0, last);
   }

   /**
    * Return the color for drawing the column border lines.
    * @param r row number.
    * @param c column number.
    * @return ruling color.
    */
   @Override
   public Color getColBorderColor(int r, int c) {
      if(r < 0) {
         return null;
      }

      checkInit();
      int c2 = nextCol(r, c);
      boolean last = c2 >= getColCount();
      TableFormat colf = (c >= 0) ? getColTableFormat(c) : null;
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = (c >= 0) ? getCellTableFormat(r, c) : null;
      TableFormat colf2 = !last ? getColTableFormat(c2) : null;
      TableFormat cellf2 = !last ? getCellTableFormat(r, c2) : null;

      return mergeLineColor(table.getColBorderColor(r, c),
                            colf, rowf, cellf, colf2, cellf2,
                            false, c < 0, r < 0, last);
   }

   /**
    * Get the cell format defined in this table lens.
    * @param r the specified row.
    * @param c the specified col.
    * @return cell format for the specified cell.
    */
   @Override
   public Format getCellFormat(int r, int c) {
      return getCellFormat(r, c, false);
   }

   /**
    * Add a getCellFormat method for 3 parameters overriding
    * the method in AttributeTableLens.
    * @param r the specified row.
    * @param c the specified col.
    * @param cellOnly specified cellonly.
    * @return cell format for the specified cell.
    */
   @Override
   public Format getCellFormat(int r, int c, boolean cellOnly) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);
      Format defaultformat = table.getDefaultFormat(r, c);

      if(table instanceof AbstractTableLens) {
         ((AbstractTableLens) table).setLocal(getLocale());
      }

      Format fmt =  mergeFormat(defaultformat,
         attritable == null ? null : attritable.getCellFormat(r, c),
         colf == null ? null : colf.getFormat(getLocale()),
         rowf == null ? null : rowf.getFormat(getLocale()),
         cellf == null ? null : cellf.getFormat(getLocale()));

      if(fmt == null && getCalcColumn(r, c) instanceof PercentColumn) {
         return NumberFormat.getPercentInstance();
      }

      return fmt;
   }

   private CalcColumn getCalcColumn(int r, int c) {
      CrossFilter filter = Util.getCrossFilter(this);

      if(!(filter instanceof CrossCalcFilter)) {
         return null;
      }

      int aggrIdx = CrossTabFilterUtil.getAggregateIndex((CrossTabFilter) filter.getTable(), r, c);

      if(aggrIdx < 0) {
         return null;
      }

      return ((CrossCalcFilter) filter).getCalcColumn(r, c);
   }

   protected Format getScriptCellFormat(int r, int c, boolean cellOnly) {
      return super.getCellFormat(r, c, cellOnly);
   }

   @Override
   public void setPresenter(String header, Presenter p) {
      if(attritable != null) {
         attritable.setPresenter(header, p);
      }
      else {
         super.setPresenter(header, p);
      }
   }

   /**
    * Set a presenter on a cell.
    */
   @Override
   public void setPresenter(int r, int c, Presenter presenter) {
      if(attritable != null) {
         attritable.setPresenter(r, c, presenter);
      }
      else {
         super.setPresenter(r, c, presenter);
      }
   }

   @Override
   public void setPresenter(int col, Presenter p) {
      if(attritable != null) {
         attritable.setPresenter(col, p);
      }
      else {
         super.setPresenter(col, p);
      }
   }

   @Override
   public void setHyperlink(int r, int c, Hyperlink.Ref link) {
      if(attritable != null) {
         attritable.setHyperlink(r, c, link);
      }
      else {
         super.setHyperlink(r, c, link);
      }
   }

   /**
    * Get the presenter for the specified cell.
    * @param r the specified row
    * @param c the specified col
    * @return presenter for the specified cell
    */
   @Override
   public Presenter getPresenter(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);
      Presenter presenter = attritable == null ? null :
         attritable.getPresenter(r, c);

      final Optional<TableFormat> format = Stream.of(colf, rowf, cellf)
         .filter(Objects::nonNull)
         .findFirst();
      final Presenter mergePresenter = format.map(TableFormat::getPresenter).orElse(presenter);

      if(mergePresenter != null) {
         format.ifPresent(f -> mergePresenter.setBackground(f.background));
      }

      return mergePresenter;
   }

   /**
    * Return the per cell line wrap mode.
    * @param r row number
    * @param c column number
    * @return true if line wrapping should be done
    */
   @Override
   public boolean isLineWrap(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);

      if(cellf != null && cellf.linewrap != null) {
         return cellf.linewrap.booleanValue();
      }
      else if(rowf != null && rowf.linewrap != null) {
         return rowf.linewrap.booleanValue();
      }
      else if(colf != null && colf.linewrap != null) {
         return colf.linewrap.booleanValue();
      }
      else {
         return table.isLineWrap(r, c);
      }
   }

   /**
    * Return the per cell suppress if duplicate mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if duplicate should be done
    */
   @Override
   public boolean isSuppressIfDuplicate(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);
      boolean sdup = attritable == null ? false :
         attritable.isSuppressIfDuplicate(r, c);

      return mergeBoolean(sdup, colf == null ? false : colf.suppressIfDuplicate,
                          rowf == null ? false : rowf.suppressIfDuplicate,
                          cellf == null ? false : cellf.suppressIfDuplicate);
   }

   /**
    * Return the per cell suppress if zero mode.
    * @param r row number
    * @param c column number
    * @return true when suppress if zero should be done
    */
   @Override
   public boolean isSuppressIfZero(int r, int c) {
      checkInit();
      TableFormat colf = getColTableFormat(c);
      TableFormat rowf = getRowTableFormat(r);
      TableFormat cellf = getCellTableFormat(r, c);
      boolean szero = attritable == null ? false :
         attritable.isSuppressIfZero(r, c);

      return mergeBoolean(szero, colf == null ? false : colf.suppressIfZero,
                          rowf == null ? false : rowf.suppressIfZero,
                          cellf == null ? false : cellf.suppressIfZero);
   }

   /**
    * Invalidate the table filter forcely, and the table filter will perform
    * filtering calculation to validate itself.
    */
   @Override
   public synchronized void invalidate() {
      inited = false;
      super.invalidate();
   }

   /**
    * Set a cell value.
    */
   @Override
   public void setObject(int r, int c, Object val) {
      // @by billh, don't use cache
      table.setObject(r, c, val);
   }

   /**
    * Clear all cached data.
    */
   @Override
   public void clearCache() {
      colcache = null;
      rowcache = null;
      cellcache = null;
   }

   /*
    * Clear the cell cache.
    */
   public void clearCellCache() {
      cellcache = null;
   }

   /**
    * Check if inited.
    */
   protected final void checkInit() {
      if(!inited) {
         // @by larryl, this can't be in synchronized block because the base
         // table may already be locked and it could cause a deadlock.
         if(descriptor == null) {
            descriptor = getDescriptor();
         }

         checkInit0();
      }
   }

   /**
    * Initialize cache.
    */
   private synchronized void checkInit0() {
      if(!inited) {
         inited = true;
         colcache = new FixedSizeSparseMatrix();
         rowcache = new FixedSizeSparseMatrix();
         cellcache = new FixedSizeSparseMatrix();

         rowFmt = false;
         colFmt = false;

         if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
            return;
         }

         paths = new TableDataPath[getTable().getColCount()][];
         List[] plist = new List[paths.length];
         List<TableDataPath> rlist = new ArrayList<>();
         cpath = new TableDataPath[getTable().getColCount()];

         for(int i = 0; i < plist.length; i++) {
            plist[i] = new ArrayList();
         }

         for(TableDataPath fmtPath : getFormatMap().keySet()) {
            if(fmtPath == null) {
               continue;
            }

            String[] parr = fmtPath.getPath();

            // is row data path?
            if(fmtPath.isRow()) {
               rlist.add(fmtPath);
               rowFmt = true;
            }
            // is col data path?
            else if(fmtPath.isCol()) {
               if(parr.length > 0) {
                  int[] col = TableAttr.getColumns(getTable(), parr[0]);

                  if(col != null) {
                     for(int i = 0; i < col.length; i++) {
                        if(col[i] < cpath.length) {
                           cpath[col[i]] = fmtPath;
                        }
                     }
                  }

                  colFmt = true;
               }
            }
            // is cell data path?
            else {
               if(parr.length > 0) {
                  int[] col = TableAttr.getColumns(getTable(), parr[0]);

                  if(col != null) {
                     for(int i = 0; i < col.length; i++) {
                        if(col[i] < plist.length) {
                           plist[col[i]].add(fmtPath);
                        }
                     }
                  }
               }
               else {
                  // need to get from base table
                  nomatch = true;
               }
            }
         }

         for(int i = 0; i < plist.length; i++) {
            paths[i] = (TableDataPath[]) plist[i].toArray(new TableDataPath[plist[i].size()]);
         }

         rpath = rlist.toArray(new TableDataPath[rlist.size()]);
      }
   }

   /**
    * Merge font.
    */
   protected Font mergeFont(Font font1, Font font2, Font font3, Font font4) {
      return font4 != null ? font4 :
         (font3 != null ? font3 : (font2 != null ? font2 : font1));
   }

   /**
    * Merge color.
    */
   private Color mergeColor(Color color1, Color color2, Color color3,
                            Color color4) {
      return color4 != null ? color4 :
         (color3 != null ? color3 : (color2 != null ? color2 : color1));
   }

   /**
    * Merge int.
    */
   protected int mergeInt(int int1, int int2, int int3, int int4) {
      return int4 >= 0 ? int4 : (int3 >= 0 ? int3 : (int2 >= 0 ? int2 : int1));
   }

   /**
    * Merge format.
    */
   protected Format mergeFormat(Format defaultFormat, Format fmt1, Format fmt2,
                              Format fmt3, Format fmt4) {
      return fmt4 != null ? fmt4 :
         (fmt3 != null ? fmt3 : (fmt2 != null ? fmt2 :
            (fmt1 != null ? fmt1 : defaultFormat)));
   }

   /**
    * Merge boolean.
    */
   private boolean mergeBoolean(boolean b1, boolean b2, boolean b3,
                                boolean b4) {
      return b4 ? b4 : (b3 ? b3 : (b2 ? b2 : b1));
   }

   /**
    * Merge line styles. Line styles are determined by the top and
    * bottom cells (or left and right).
    */
   private int mergeLineStyle(int def, Insets colf, Insets rowf,
                              Insets cellf, Insets next1, Insets next2,
                              boolean row, boolean first, boolean last) {
      Insets top = cellf != null ? cellf : (rowf != null ? rowf : colf);
      Insets bot = next2 != null ? next2 : next1;
      int style = -1;

      // @by larryl if the next format is not set, use the format defined for
      // the entire column or row
      if(bot == null && !last) {
         bot = row ? colf : rowf;
      }

      if(top == null && !first) {
         top = row ? colf : rowf;
      }

      if(top != null && bot != null) {
         int topline, botline;

         if(row) {
            topline = first ? -1 : top.bottom;
            botline = bot.top;
         }
         else {
            topline = first ? -1 : top.right;
            botline = bot.left;
         }

         // @by larryl, supports 0 to force no border, -1 to use default
         switch(topline) {
         case 0:
            return 0;
         case -1:
            if(botline != -1) {
               return botline;
            }
         default:
            // continue
         }

         // @by larryl, supports 0 to force no border, -1 to use default
         switch(botline) {
         case 0:
            return 0;
         case -1:
            if(topline != -1) {
               return topline;
            }
         default:
            // continue
         }

         if(topline != -1 && botline != -1) {
            return Util.mergeLineStyle(topline, botline);
         }
      }
      else if(top != null) {
         style = row ? top.bottom : top.right;
      }
      else if(bot != null) {
         style = row ? bot.top : bot.left;
      }

      // @by larryl, supports 0 to force no border, -1 to use default
      switch(style) {
      case 0:
         return 0;
      case -1:
         return def;
      default:
         return style;
      }
   }

   /**
    * Merge line color. Line colors are determined by the top and
    * bottom cells (or left and right).
    */
   private Color mergeLineColor(Color def, TableFormat colf,
                                TableFormat rowf, TableFormat cellf,
                                TableFormat next1, TableFormat next2,
                                boolean row, boolean fcol, boolean frow,
                                boolean last) {
      Color top = null;
      Color bot = null;

      if(cellf != null) {
         top = row ? cellf.bottomBorderColor : cellf.rightBorderColor;
      }

      if(top == null && !frow) {
         if(rowf != null) {
            top = row ? rowf.bottomBorderColor : rowf.rightBorderColor;
         }
      }

      if(top == null && !fcol) {
         if(top == null && colf != null) {
            top = row ? colf.bottomBorderColor : colf.rightBorderColor;
         }
      }

      if(next2 != null) {
         bot = row ? next2.topBorderColor : next2.leftBorderColor;
      }

      if(bot == null && next1 != null) {
         bot = row ? next1.topBorderColor : next1.leftBorderColor;
      }

      return mergeLineColor(top, bot, def);
   }

   /**
    * Merge the colors. If two colors are different, we use the color
    * with the greater rgb value.
    */
   private Color mergeLineColor(Color c1, Color c2, Color def) {
      if(c1 == null) {
         return c2 == null ? def : c2;
      }
      else if(c2 == null) {
         return c1 == null ? def : c1;
      }

      int rgb1 = c1.getRGB();
      int rgb2 = c2.getRGB();

      return rgb1 == rgb2 ? c1 : Util.mergeColor(c1, c2);
   }

   /**
    * Get table format at a table col.
    * @param col the specified col
    */
   protected TableFormat getColTableFormat(int col) {
      // @by larryl, optimization. Don't check if no column format is set.
      if(!colFmt) {
         return null;
      }

      FixedSizeSparseMatrix colcache = this.colcache;

      if(colcache == null) {
         colcache = this.colcache = new FixedSizeSparseMatrix();
      }

      Object fmt = colcache.get(0, col);

      if(fmt == SparseMatrix.NULL) {
        fmt = getColTableFormat0(col);
        colcache.set(0, col, fmt);
      }

      return (TableFormat) fmt;
   }

   /**
    * Get table format of a table col.
    * @param col the specified col
    */
   protected TableFormat getColTableFormat0(int col) {
      if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         return null;
      }

      TableDataPath path = (col < cpath.length) ? cpath[col] : null;
      return path == null ? null : (TableFormat) getFormatMap().get(path);
   }

   /**
    * Get table format at a table row.
    * @param row the specified row
    */
   protected TableFormat getRowTableFormat(int row) {
      // @by larryl, optimization. row format is relative rare so don't
      // spend the time to check if no row format is set
      if(!rowFmt) {
         return null;
      }

      FixedSizeSparseMatrix rowcache = this.rowcache;

      if(rowcache == null) {
         rowcache = this.rowcache = new FixedSizeSparseMatrix();
      }

      Object fmt = rowcache.get(0, row);

      if(fmt == SparseMatrix.NULL) {
         fmt = getRowTableFormat0(row);
         rowcache.set(0, row, fmt);
      }

      return (TableFormat) fmt;
   }

   /**
    * Get table format of a table row.
    * @param row the specified row
    */
   protected TableFormat getRowTableFormat0(int row) {
      if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         return null;
      }

      TableDataPath path = getRowDataPath(row);
      return path == null ? null : (TableFormat) getFormatMap().get(path);
   }

   /**
    * Get table format at a table cell.
    * @param row the specified row index
    * @param col the specified col index
    * @return table format if any, null otherwise
    */
   protected final TableFormat getCellTableFormat(int row, int col) {
      if(row < 0 || col < 0) {
         return null;
      }

      FixedSizeSparseMatrix cellcache = this.cellcache;

      if(cellcache == null) {
         cellcache = this.cellcache = new FixedSizeSparseMatrix();
      }

      Object fmt = cellcache.get(row, col);

      if(fmt == SparseMatrix.NULL) {
         fmt = getCellTableFormat0(row, col);
         cellcache.set(row, col, fmt);
      }

      return (TableFormat) fmt;
   }

   /**
    * Get table format at a table cell.
    * @param row the specified row index
    * @param col the specified col index
    * @return table format if any, null otherwise
    */
   protected TableFormat getCellTableFormat0(int row, int col) {
      // @by larryl, if a span cell, use the format set for the top
      // left corner. This is generally not necessary but is needed for
      // the borders
      Rectangle span = getSpan0(row, col);

      if(span != null) {
         row += span.y;
         col += span.x;
      }

      checkInit();
      TableDataPath path = getCellDataPath(row, col);
      return path == null ? null : getFormatMap().get(path);
   }

   /**
    * Get the span of a cell.
    */
   protected Rectangle getSpan0(int row, int col) {
      if(spanmap != null) {
         return spanmap.get(row, col);
      }

      return null;
   }

   /**
    * Get the row index of the cell the is below the current cell. Take
    * into account of cell span.
    */
   private int nextRow(int row, int col) {
      // if this is the last header row, the next row is the top of region
      if(row == headerR - 1 && reg != null) {
         row = reg.y - 1;
      }

      Rectangle span = getSpan0(row, col);
      row = (span != null) ? row + span.height : row + 1;

      while(table.moreRows(row) && table.getRowHeight(row) == 0) {
         row++;
      }

      return row;
   }

   /**
    * Get the column index of the cell the is at right of the current cell.
    * Take into account of cell span.
    */
   private int nextCol(int row, int col) {
      Rectangle span = getSpan0(row, col);
      col = (span != null) ? col + span.width : col + 1;

      while(col < table.getColCount() && table.getColWidth(col) == 0) {
         col++;
      }

      return col;
   }

   /**
    * Get table data path stored in table highlight map of the table row.
    * @param row the specified row
    */
   private TableDataPath getRowDataPath(int row) {
      if(descriptor.getType() != TableDataDescriptor.CROSSTAB_TABLE) {
         for(int i = 0; i < rpath.length; i++) {
            if(descriptor.isRowDataPath(row, rpath[i])) {
               return rpath[i];
            }
         }
      }

      return null;
   }

   /**
    * Get table data path stored in table format map of the table cell.
    * @param row the specified row
    * @param col the specified col
    */
   protected final TableDataPath getCellDataPath(int row, int col) {
      if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         Iterator keys = getFormatMap().keySet().iterator();

         while(keys.hasNext()) {
            TableDataPath path = (TableDataPath) keys.next();

            if(descriptor.isCellDataPathType(row, col, path)) {
               return path;
            }
         }

         return null;
      }
      else {
         if(col < paths.length) {
            TableDataPath[] cpath = paths[col];

            for(int i = 0; i < cpath.length; i++) {
               if(descriptor.isCellDataPath(row, col, cpath[i])) {
                  return cpath[i];
               }
            }
         }

         /* data path from archived table (PagedRegionTableLens) may not match and it should
         be return, or the format will be lost. (42429)
         if(nomatch) {
            return getTable().getDescriptor().getCellDataPath(row, col);
         }

         //return null;
         */

         return getTable().getDescriptor().getCellDataPath(row, col);
      }
   }

   protected TableDataDescriptor descriptor;

   private transient FixedSizeSparseMatrix colcache;
   private transient FixedSizeSparseMatrix rowcache;
   private transient FixedSizeSparseMatrix cellcache;

   private TableDataPath[] rpath;  // row data path
   private TableDataPath[][] paths; // cell data path
   private TableDataPath[] cpath; // col data path
   private SpanMap spanmap;
   private boolean inited = false;
   private boolean rowFmt = false;
   private boolean colFmt = false;
   private boolean nomatch = false; // true if cell datapath not by column
   private int headerR = 1;
   private Rectangle reg;
}

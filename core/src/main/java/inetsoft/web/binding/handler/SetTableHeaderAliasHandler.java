/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.binding.handler;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;

/**
 * The header-rename mechanism the real Composer UI uses (a double-click on a table or crosstab
 * header, typed text) is not a dedicated field: it is a {@code MESSAGE_FORMAT}-valued
 * {@link VSFormat} written onto the header's own {@link TableDataPath} inside the assembly's
 * {@link FormatInfo}. Extracted from {@code ComposerVSTableService}'s own private {@code
 * setAlias} (which now delegates here, so native Composer behavior is unchanged) so the wiz
 * {@code set_column_labels} write path can reuse the identical mechanism rather than a second,
 * drifting copy of it.
 *
 * <p>A sibling of {@link ClearTableHeaderAliasHandler}, which already lives in this package and
 * already does the reverse (removing a {@code MESSAGE_FORMAT} alias) via the same {@code
 * matchAgg}/{@code matchDim} matching this class's {@link #findHeaderPath} reuses.
 */
public class SetTableHeaderAliasHandler {
   /**
    * Writes {@code messageTxt} as a fixed {@code MESSAGE_FORMAT} override at {@code dataPath}.
    * {@code TableFormat} renders a {@code MESSAGE_FORMAT} cell by running the cell's raw value
    * through a {@code MessageFormat} built from this string — a literal with no {@code {0}}/
    * {@code {1}} placeholders (exactly what a header rename writes) comes back unchanged, so this
    * is a real, already-shipped reuse of the generic cell-formatting pipeline to force a fixed
    * replacement string onto one header cell, not a new rendering mechanism.
    *
    * <p>No escaping of {@code messageTxt} for {@code MessageFormat}'s own {@code {}/'} syntax —
    * matching native {@code ComposerVSTableService}'s existing (unescaped) behavior exactly,
    * including its latent issue with those characters. Not something to fix here as a side effect
    * of the wiz path reusing this code.
    */
   public static void setAlias(TableDataPath dataPath, FormatInfo formatInfo, String messageTxt) {
      VSCompositeFormat format = formatInfo.getFormat(dataPath);

      if(format == null) {
         format = new VSCompositeFormat();
      }
      else {
         format = format.clone();
      }

      VSFormat ufmt = format.getUserDefinedFormat();

      if(ufmt == null) {
         ufmt = new VSFormat();
         format.setUserDefinedFormat(ufmt);
      }

      ufmt.setFormatValue(VSFormat.MESSAGE_FORMAT);
      ufmt.setFormatExtentValue(messageTxt);

      formatInfo.setFormat(dataPath, format);
   }

   /**
    * {@code setAlias} plus the HEADER/GROUP_HEADER duality {@code changeColumnTitle} itself
    * writes defensively: a crosstab summary header cell's {@code TableDataPath} type flips
    * between {@code HEADER} and {@code GROUP_HEADER} depending on whether any group (row/col)
    * dimension is bound, and the two never exist on the same crosstab at once, so writing both is
    * cheap insurance against a drill/pivot changing which one is live.
    */
   public static void setAliasWithHeaderDuality(TableDataPath dataPath, FormatInfo formatInfo,
                                                String messageTxt)
   {
      setAlias(dataPath, formatInfo, messageTxt);

      if(dataPath.getType() == TableDataPath.GROUP_HEADER) {
         TableDataPath other = (TableDataPath) dataPath.clone();
         other.setType(TableDataPath.HEADER);
         setAlias(other, formatInfo, messageTxt);
      }
      else if(dataPath.getType() == TableDataPath.HEADER) {
         TableDataPath other = (TableDataPath) dataPath.clone();
         other.setType(TableDataPath.GROUP_HEADER);
         setAlias(other, formatInfo, messageTxt);
      }
   }

   /**
    * The missing link a name-based rename needs that a UI click already has for free: given the
    * live {@code DataRef} a shelf/column/index resolved to, scans the rendered lens's header
    * region and returns the first {@code TableDataPath} that {@link
    * ClearTableHeaderAliasHandler#matchAgg}/{@code matchDim} would also recognize as this ref's
    * header cell. {@code null} if the rendered lens currently has no such cell (e.g. the column
    * does not render at all right now).
    *
    * <p>A crosstab's header region is an L-shape, not the {@code [0, getHeaderRowCount())} x
    * {@code [0, getHeaderColCount())} rectangle {@code HighlightDialogService.getFirstDataCell}
    * computes for a different purpose (the position of the first true *data* cell, a single
    * point — not a scan bound for every axis). When an aggregate shelf has no dimension bound on
    * the opposite axis (e.g. a crosstab with row dimensions but no column dimensions, laid out
    * side-by-side), its header cell renders inside a header *row* ({@code row <
    * getHeaderRowCount()}) but past the header *column* rectangle ({@code col >=
    * getHeaderColCount()}) — the "arm" of the L that the rectangle-only scan missed, which is why
    * an aggregate rename on exactly this common shape (1 row dimension, 0 column dimensions, 1+
    * aggregates) previously failed with "could not find" even though the column-identity
    * resolution itself succeeded. The mirrored arm (a header *column* extending past the header
    * *row* rectangle, for the transposed shape with no row dimension) is scanned symmetrically.
    *
    * @param colIndex the ref's position on its own shelf — {@code matchDim} addresses a dimension
    *                 header purely positionally (its {@code "Cell [row,col]"} path segment), not
    *                 by name, so this must be the same index the caller resolved the ref with.
    *                 Unused when {@code dataRef} is an aggregate ({@code matchAgg} matches by full
    *                 name instead).
    */
   public static TableDataPath findHeaderPath(VSTableLens lens, DataRef dataRef, int colIndex) {
      int headerRows = lens.getHeaderRowCount();
      int headerCols = lens.getHeaderColCount();
      int colCount = lens.getColCount();
      int rowCount = lens.getRowCount();

      // The top headerRows rows are header rows by definition, regardless of column --
      // side-by-side aggregate headers live here past the headerCols rectangle.
      int rowArmColBound = colCount < 0 ? headerCols : colCount;
      TableDataPath found = scanHeaderRegion(lens, dataRef, colIndex, 0, headerRows, 0,
                                             rowArmColBound);

      if(found != null) {
         return found;
      }

      // The left headerCols columns are header columns by definition, regardless of row --
      // the mirrored (non-side-by-side) summary header column lives here past the headerRows
      // rectangle. Rows already covered by the arm above are skipped.
      int colArmRowBound = rowCount < 0 ? headerRows : rowCount;
      return scanHeaderRegion(lens, dataRef, colIndex, headerRows, colArmRowBound, 0, headerCols);
   }

   private static TableDataPath scanHeaderRegion(VSTableLens lens, DataRef dataRef, int colIndex,
                                                  int rowStart, int rowEnd, int colStart,
                                                  int colEnd)
   {
      for(int row = rowStart; row < rowEnd; row++) {
         for(int col = colStart; col < colEnd; col++) {
            TableDataPath path = lens.getTableDataPath(row, col);
            String[] segments = path == null ? null : path.getPath();

            if(segments == null || segments.length == 0) {
               continue;
            }

            if(ClearTableHeaderAliasHandler.matchAgg(dataRef, segments) ||
               ClearTableHeaderAliasHandler.matchDim(dataRef, segments, colIndex))
            {
               return path;
            }
         }
      }

      return null;
   }

   private SetTableHeaderAliasHandler() {
   }
}

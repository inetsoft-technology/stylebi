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
    * extent ({@code row} in {@code [0, getHeaderRowCount())}, {@code col} in
    * {@code [0, getHeaderColCount())} — the same extent {@code HighlightDialogService
    * .getFirstDataCell} already computes for a different feature) and returns the first
    * {@code TableDataPath} that {@link ClearTableHeaderAliasHandler#matchAgg}/{@code matchDim}
    * would also recognize as this ref's header cell. {@code null} if the rendered lens currently
    * has no such cell (e.g. the column does not render at all right now).
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

      for(int row = 0; row < headerRows; row++) {
         for(int col = 0; col < headerCols; col++) {
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

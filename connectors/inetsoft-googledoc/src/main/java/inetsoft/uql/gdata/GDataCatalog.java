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
package inetsoft.uql.gdata;

import com.google.api.services.drive.model.File;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SheetProperties;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;

import java.util.*;

/**
 * Assembles {@link GDataRuntime}'s {@link TabularCatalogProvider} answers out of the connector's
 * own Drive/Sheets API calls (via {@link GDataRuntime}'s package-private static fetch methods --
 * the mocking seam, 01-design.md Part D.15). Mirrors {@code AnalyticsCatalog}'s role:
 * package-private, static methods, no state of its own.
 *
 * No caching: the two SPI phases share nothing that stays fresh across calls. A sheet's TITLE
 * (fetched by {@code listDatasets}) goes stale the moment a user renames it, and X6 (a
 * {@code describeDataset} call must reject a dataset id it did not itself emit) requires the live
 * lookup anyway, so a cache would have to be invalidated on exactly the events that make the
 * check necessary.
 *
 * Sheets is the first connector on this SPI where "a column has a type" is not a source concept:
 * types live on CELLS, not columns (a date is a number plus a number format). The column *list*
 * comes from the header row (a declaration, per {@link TabularCatalogProvider#describeDataset}'s
 * own javadoc); the column *types* come from sampling 3 data rows (charter Q4). See
 * 01-design.md Part D.9 for why {@link TabularDatasetSchema#columnsMayBeIncomplete()} can only
 * express the list half of that split, not the type half.
 */
final class GDataCatalog {
   private GDataCatalog() {
   }

   /**
    * Every GRID sheet in every spreadsheet this data source's Drive credentials can see.
    *
    * @see GDataRuntime#listSpreadsheetFiles for level 1 (Drive, paged to exhaustion -- A3/X3)
    * @see GDataRuntime#listSheetProperties for level 2 (one Sheets metadata call per spreadsheet)
    */
   static TabularCatalog listDatasets(GDataDataSource ds) throws Exception {
      List<TabularDatasetRef> datasets = new ArrayList<>();

      for(File file : GDataRuntime.listSpreadsheetFiles(ds)) {
         for(SheetProperties props : GDataRuntime.listSheetProperties(ds, file.getId())) {
            if(!isGridSheet(props) || props.getSheetId() == null) {
               continue;
            }

            datasets.add(new TabularDatasetRef(
               SheetsDatasetId.compose(file.getId(), props.getSheetId().toString())));
         }
      }

      // No relationships: Sheets declares no edges between one sheet and another (N5).
      return new TabularCatalog(datasets, List.of());
   }

   /**
    * F4. A sheet whose {@code sheetType} is {@code OBJECT} (chart-only) or {@code DATA_SOURCE} has
    * no grid at all -- listing it would produce a dataset whose {@code describeDataset} can only
    * throw, which is not the truncation X3 forbids (X3 is about dropping datasets that ARE
    * describable). {@code null} is tolerated as GRID: it is only absent if a future/older response
    * drops the field, and the overwhelmingly common sheet type IS a grid. Hidden sheets are NOT
    * filtered here -- {@code runQuery} reads a hidden sheet fine, so omitting it would hide an
    * annotatable target.
    */
   private static boolean isGridSheet(SheetProperties props) {
      return props.getSheetType() == null || "GRID".equals(props.getSheetType());
   }

   /**
    * One sheet's columns, sampled from its header row plus up to 3 data rows.
    *
    * Exactly two Google API calls: {@link GDataRuntime#listSheetProperties} resolves the dataset
    * id's numeric {@code sheetId} to the sheet's current title (required because
    * {@code spreadsheets().get().setRanges(...)} addresses a sheet by title, never by id -- see
    * 01-design.md Part D.3) and, by failing to find that id, enforces X6; then
    * {@link GDataRuntime#fetchSampleRows} reads the 4-row range. There cannot be a single call:
    * {@code Spreadsheets$Get}'s whole parameter surface is {@code setRanges}/{@code setFields}/
    * {@code setIncludeGridData} -- nothing accepts a sheet id.
    */
   static TabularDatasetSchema describeDataset(GDataDataSource ds, String datasetId)
      throws Exception
   {
      SheetsDatasetId.Parsed parsed = SheetsDatasetId.parse(datasetId);
      String title = resolveTitle(ds, parsed);
      String a1Range = quoteTitle(title) + "!1:4";
      List<RowData> rows = GDataRuntime.fetchSampleRows(ds, parsed.spreadsheetId(), a1Range);

      // T1, copied from GDataRuntime:83-93 (minus its columnCount fallback -- there is no
      // fallback here; width == 0 means the first four rows are entirely empty and we throw
      // rather than trust gridProperties.columnCount, which is the grid width, not the data
      // width, and is deliberately never fetched by listSheetProperties' field mask).
      int width = 0;

      for(RowData row : rows) {
         if(row != null && row.getValues() != null) {
            width = Math.max(width, row.getValues().size());
         }
      }

      if(width == 0) {
         throw new Exception("Google Sheets dataset '" + datasetId + "' (sheet '" + title +
            "') has no data in its first 4 rows; nothing to describe.");
      }

      RowData headerRow = rows.isEmpty() ? null : rows.get(0);
      List<TabularColumn> columns = new ArrayList<>();
      Set<String> seenNames = new HashSet<>();
      int droppedCount = 0;

      for(int c = 0; c < width; c++) {
         CellData headerCell = cellAt(headerRow, c);
         String raw = headerName(headerCell);

         if(raw == null) {
            // charter T2 (empty header cell) + F5 (error-valued header cell) + a non-string
            // header cell (headerName() below returns null for all three): a synthesized name
            // would claim a column runQuery names null, i.e. one nothing can bind to.
            droppedCount++;
            continue;
         }

         String name = raw.trim();

         if(name.isEmpty()) {
            // A6/C3: a whitespace-only header. Handled here, at the connector layer, so the
            // assertion lands on the connector's own return value rather than on
            // TabularCatalogService.validateColumnNames rejecting it and taking down the whole
            // describeTable.
            droppedCount++;
            continue;
         }

         if(!seenNames.add(name)) {
            // F1/Q3 (the licensed deviation, 03-reconcile.md D1): keep the FIRST occurrence of a
            // duplicate trimmed name, drop every later one. First wins because
            // applyAnnotationToDoc's `find` (wiz) resolves to the first match, so keeping the
            // first occurrence is the only choice under which the surviving catalog column and
            // the column the merge actually annotates are the same column.
            droppedCount++;
            continue;
         }

         String type = resolveColumnType(rows, c);
         // Q7: label is the raw (untrimmed) header only when it differs from the trimmed name;
         // null when they agree, to avoid a redundant key on every field.
         String label = name.equals(raw) ? null : raw;
         columns.add(new TabularColumn(name, type, null, label, null));
      }

      if(columns.isEmpty()) {
         // Every header cell in [0, width) was empty/error/non-string/blank/a duplicate.
         // TabularCatalogProvider's own contract: "never with an empty column list -- throw
         // instead."
         throw new Exception("Google Sheets dataset '" + datasetId + "' (sheet '" + title +
            "') has no usable column headers in its first row.");
      }

      Map<String, String> params = new LinkedHashMap<>();
      params.put("spreadsheetId", parsed.spreadsheetId());
      params.put("worksheetId", parsed.sheetId());
      // Pinned, not merely reported: the column names above were built ASSUMING row 1 is the
      // header. If a query were filled with firstRowAsHeader=false, runQuery synthesizes A/B/C...
      // instead, and every annotated column name would be wrong.
      params.put("firstRowAsHeader", "true");

      // A16 (widened, C6): true exactly when at least one column was dropped, for ANY of the five
      // routes above -- never a literal.
      return new TabularDatasetSchema(datasetId, columns, List.of(), params, droppedCount > 0);
   }

   /**
    * Resolves the dataset id's numeric {@code sheetId} to this sheet's CURRENT title, and is the
    * X6 check: an id this data source never emitted, or a sheet since deleted, throws here rather
    * than answering with some other sheet's schema.
    */
   private static String resolveTitle(GDataDataSource ds, SheetsDatasetId.Parsed parsed)
      throws Exception
   {
      for(SheetProperties props : GDataRuntime.listSheetProperties(ds, parsed.spreadsheetId())) {
         if(props.getSheetId() != null && parsed.sheetId().equals(props.getSheetId().toString())) {
            return props.getTitle();
         }
      }

      throw new Exception("Google Sheets dataset '" +
         SheetsDatasetId.compose(parsed.spreadsheetId(), parsed.sheetId()) +
         "' is unknown: spreadsheet '" + parsed.spreadsheetId() +
         "' has no sheet with id " + parsed.sheetId() + ".");
   }

   /**
    * F6. {@code runQuery} builds its A1 range with the sheet title UNQUOTED
    * ({@code GDataRuntime.java:75}), so it fails today for any title containing a space, `!`, `:`,
    * or a leading digit. This connector does not inherit that: always quote, and double an
    * embedded {@code '} -- "always" rather than "only when needed" because "when needed" is a
    * second grammar to get wrong, and a quoted title is valid A1 notation unconditionally. This
    * makes {@code describeDataset} strictly more capable than {@code runQuery} for such a title;
    * that asymmetry is recorded (X4, see 01-design.md Part D.3 and the class javadoc above), not
    * levelled -- N2 forbids touching {@code runQuery} this round.
    */
   private static String quoteTitle(String title) {
      return "'" + title.replace("'", "''") + "'";
   }

   private static CellData cellAt(RowData row, int col) {
      if(row == null || row.getValues() == null || col >= row.getValues().size()) {
         // A sampled row can be shorter than the reported width (Sheets truncates trailing
         // empties) -- this bounds check, not a blind row.getValues().get(col), is what makes
         // that safe.
         return null;
      }

      return row.getValues().get(col);
   }

   /**
    * The header row's honest string name for column {@code col}, or {@code null} when there is
    * none to report: an empty cell, an error-valued cell (F5), or a non-string cell (e.g. a
    * numeric header like {@code 2024} -- {@code runQuery}'s own {@code getValidHeaderName} is
    * never reached for such a cell either, since that branch is guarded by
    * {@code data[c] instanceof String}). [unverified -- do not assert either way] what
    * {@code runQuery} ultimately names such a column at render time; this method does not guess.
    */
   private static String headerName(CellData headerCell) {
      if(!XSchema.STRING.equals(GDataColumnTypes.typeOfCell(headerCell))) {
         return null;
      }

      return headerCell.getEffectiveValue().getStringValue();
   }

   /**
    * X1/Q4: rows 2-4 (index 1..3) of the sample, in order; the first row whose cell in this
    * column returns a non-null type signal wins. All-empty/all-error -> {@link XSchema#STRING} --
    * never a null type, and never an {@code XSchema.UNKNOWN} either (a legal constant, but one
    * that tells the annotating LLM nothing it can use; {@code runQuery} would put a {@code null}
    * in the cell, which an {@code XObjectColumn} of nulls is read as a string column downstream).
    */
   private static String resolveColumnType(List<RowData> rows, int col) {
      for(int r = 1; r < rows.size() && r <= 3; r++) {
         String type = GDataColumnTypes.typeOfCell(cellAt(rows.get(r), col));

         if(type != null) {
            return type;
         }
      }

      return XSchema.STRING;
   }
}

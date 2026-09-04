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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.*;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.XTableTableNode;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class GDataRuntime extends TabularRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      return GDataCatalog.listDatasets((GDataDataSource) dataSource);
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      return GDataCatalog.describeDataset((GDataDataSource) dataSource, datasetId);
   }

   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      XSwappableTable table = new XSwappableTable();
      GDataQuery gdataQuery = (GDataQuery) query;
      GDataDataSource ds = (GDataDataSource) query.getDataSource();

      if(gdataQuery.getSpreadsheet().getSelectedFile() == null) {
         table.complete();
         table.dispose();
         String msg = ResourceBundle.getBundle("inetsoft.uql.gdata.Bundle")
            .getString("error.nullSheet");
         Tool.addUserMessage(msg);
         return new XTableTableNode(table);
      }

      try {
         Sheets service = getSheets(ds, true);
         Spreadsheet spreadsheet = service.spreadsheets()
            .get(gdataQuery.getSpreadsheet().getSelectedFile().getId())
            .setFields("properties.title,sheets(properties.sheetId,properties.title,properties.gridProperties)")
            .execute();
         Sheet worksheet = spreadsheet.getSheets().stream()
            .filter(s -> gdataQuery.getWorksheetId().equals(s.getProperties().getSheetId().toString()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Worksheet not found"));
         String wsName = worksheet.getProperties().getTitle();
         int columnCount = worksheet.getProperties().getGridProperties().getColumnCount();
         int rowCount = worksheet.getProperties().getGridProperties().getRowCount();
         String range = wsName + "!" + "A1:" + getColumnName(columnCount) + rowCount;

         spreadsheet = service.spreadsheets()
            .get(gdataQuery.getSpreadsheet().getSelectedFile().getId())
            .setFields("properties.title,sheets(data.rowData.values(effectiveValue,effectiveFormat.numberFormat))")
            .setRanges(Collections.singletonList(range))
            .execute();

         int maxColumns = 0;

         for(RowData rowData : spreadsheet.getSheets().get(0).getData().get(0).getRowData()) {
            if(rowData != null && rowData.getValues() != null) {
               maxColumns = Math.max(maxColumns, rowData.getValues().size());
            }
         }

         if(maxColumns > 0) {
            columnCount = maxColumns;
         }

         XTableColumnCreator[] creators = new XTableColumnCreator[columnCount];

         for(int i = 0; i < columnCount; i++) {
            creators[i] = XObjectColumn.getCreator();
            creators[i].setDynamic(false);
         }

         table.init(creators);
         Object[] data = new Object[columnCount];
         final Object[] headers = new Object[columnCount];
         boolean headersInit = false;

         if(!gdataQuery.isFirstRowAsHeader()) {
            for(int i = 0; i < columnCount; i++) {
               headers[i] = getColumnName(i + 1);
            }

            table.addRow(headers);
            headersInit = true;
         }

         for(RowData rowData : spreadsheet.getSheets().get(0).getData().get(0).getRowData()) {
            if(rowData == null || rowData.getValues() == null) {
               continue;
            }

            List<CellData> values = rowData.getValues();
            int cols = values.size();

            for(int c = 0; c < cols; c++) {
               CellData cellData = values.get(c);

               if(cellData == null || cellData.getEffectiveValue() == null) {
                  data[c] = null;
               }
               else if(cellData.getEffectiveValue().getNumberValue() != null) {
                  Double value = cellData.getEffectiveValue().getNumberValue();

                  if(cellData.getEffectiveFormat() == null ||
                     cellData.getEffectiveFormat().getNumberFormat() == null)
                  {
                     data[c] = value;
                  }
                  else {
                     NumberFormat format = cellData.getEffectiveFormat().getNumberFormat();

                     if("DATE".equals(format.getType())) {
                        long days = value.longValue();
                        Instant instant = BASE_DATE
                           .plus(days + 1, ChronoUnit.DAYS)
                           .toInstant();
                        data[c] = new Date(instant.toEpochMilli());
                     }
                     else if("DATE_TIME".equals(format.getType())) {
                        long days = value.longValue();
                        long time = (long) ((value - days) * 24 * 60 * 60 * 1e3 + .5);
                        Instant instant = BASE_DATE
                           .plus(days, ChronoUnit.DAYS)
                           .plus(time, ChronoUnit.MILLIS)
                           .minusSeconds(OffsetDateTime.now().getOffset().getTotalSeconds())
                           .toInstant();
                        data[c] = new Timestamp(instant.toEpochMilli());
                     }
                     else if("TIME".equals(format.getType())) {
                        long time = (long) (value * 24 * 60 * 60 * 1e3 + 0.5);
                        Instant instant = BASE_DATE
                           .plus(time, ChronoUnit.MILLIS)
                           .minusSeconds(OffsetDateTime.now().getOffset().getTotalSeconds())
                           .toInstant();
                        data[c] = new Time(instant.toEpochMilli());
                     }
                     else {
                        data[c] = value;
                     }
                  }
               }
               else if(cellData.getEffectiveValue().getBoolValue() != null) {
                  data[c] = cellData.getEffectiveValue().getBoolValue();
               }
               else {
                  data[c] = cellData.getEffectiveValue().getStringValue();

                  if(!headersInit && data[c] instanceof String) {
                     data[c] = getValidHeaderName(((String) data[c]), c, gdataQuery);
                  }
               }

               if(headers[c] instanceof String && query.getColumnType((String) headers[c]) != null)
               {
                  data[c] = transform(query, (String) headers[c], data[c]);
               }
            }

            for(int c = cols; c < columnCount; c++) {
               data[c] = null;
            }

            table.addRow(data);

            if(!headersInit) {
               System.arraycopy(data, 0, headers, 0, columnCount);
               headersInit = true;
            }
         }

         table.complete();
      }
      catch(Exception ex) {
         table.complete();
         table.dispose();
         LOG.error("Failed to execute Google query: {}", ds.getName(), ex);
         String msg = "Failed to execute Google query: " + ds.getName() +
            " (" + ex.getMessage() + ")";
         Tool.addUserMessage(msg);
         handleError(params, ex, () -> null);
      }

      return new XTableTableNode(table);
   }

   public void testDataSource(TabularDataSource ds, VariableTable params) throws Exception {
      GDataDataSource gdataDs = (GDataDataSource) ds;
      getDrive(gdataDs).about().get().setFields("user").execute();
   }

   // Package-private (not private), same reason and same seam as getDrive below: it lets
   // GDataCatalogTest mockStatic(GDataRuntime.class) with CALLS_REAL_METHODS for
   // listSheetProperties/fetchSampleRows while stubbing only this method, so a test can execute
   // those two methods' REAL bodies -- including their field-mask strings -- rather than mocking
   // them out entirely (P6 fix round 1, item 1: A5's guarantee that gridProperties is never
   // requested rests on listSheetProperties' own field mask, which no test executed before this).
   static Sheets getSheets(GDataDataSource ds, boolean saveTokens) {
      return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, createInitializer(ds, saveTokens))
         .setApplicationName(APPLICATION_ID)
         .build();
   }

   // Package-private (not private) so GDataCatalogTest can mockStatic(GDataRuntime.class) and stub
   // this one method for listSpreadsheetFiles' own unit test (the paging loop lives INSIDE
   // listSpreadsheetFiles, i.e. behind the seam every other catalog test mocks). Never called
   // directly by GDataCatalog.
   static Drive getDrive(GDataDataSource ds) {
      return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, createInitializer(ds, true))
         .setApplicationName(APPLICATION_ID)
         .build();
   }

   /**
    * Every spreadsheet ({@code mimeType='application/vnd.google-apps.spreadsheet'}, not trashed)
    * this data source's Drive credentials can see, paged to exhaustion. New for the tabular
    * catalog SPI (A2/A3); {@code runQuery}/{@code testDataSource}/{@code listWorksheets} do not use
    * it and are unchanged.
    *
    * <p>{@code setPageSize(1000)} is a round-trip optimization, never the loop's stopping
    * condition -- the loop stops on {@code nextPageToken == null}, so a large page size cannot
    * become a silent truncation cap (X3).
    *
    * <p>The field mask requests only {@code id} (and {@code nextPageToken}) -- {@code name} is
    * deliberately NOT fetched. Nothing in this connector reads {@code File.getName()}: a level-2
    * failure for one spreadsheet propagates rather than being caught and logged per spreadsheet
    * (A11 requires the propagation), so there is no per-spreadsheet log message that would need a
    * human-readable handle.
    *
    * <p>{@code setIncludeItemsFromAllDrives}/{@code setSupportsAllDrives} must be set together:
    * without both, a shared-drive spreadsheet is invisible, which is exactly the kind of silent
    * partial listing X3 forbids.
    *
    * <p>{@code getIncompleteSearch()} is WARNed, not ignored and not thrown on: ignoring it is the
    * silent truncation X3 forbids, and throwing would refuse to annotate an entire data source
    * because one shared drive was momentarily unreachable. There is no SPI-level slot for "the
    * dataset list itself may be incomplete" -- {@code TabularDatasetSchema.columnsMayBeIncomplete}
    * is documented about one dataset's column list, not the catalog's dataset list -- so a WARN
    * plus a record in stylebi-wiz {@code docs/tabular/stylebi-tabular-wiz-integration.md} §5.3
    * no. 45 is the whole of what is available this round.
    */
   static List<File> listSpreadsheetFiles(GDataDataSource ds) throws IOException {
      List<File> files = new ArrayList<>();
      Drive drive = getDrive(ds);
      String pageToken = null;

      do {
         FileList response = drive.files().list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
            .setFields("nextPageToken,files(id)")
            .setPageSize(1000)
            .setSpaces("drive")
            .setIncludeItemsFromAllDrives(true)
            .setSupportsAllDrives(true)
            .setOrderBy("name")
            .setPageToken(pageToken)
            .execute();

         if(response.getFiles() != null) {
            files.addAll(response.getFiles());
         }

         if(Boolean.TRUE.equals(response.getIncompleteSearch())) {
            LOG.warn("Google Drive reported an incomplete search while listing spreadsheets for " +
               "data source '{}'; the resulting catalog may be missing spreadsheets from an " +
               "unreachable shared drive.", ds.getName());
         }

         pageToken = response.getNextPageToken();
      }
      while(pageToken != null);

      return files;
   }

   /**
    * One spreadsheet's own sheets -- {@code sheetId}, {@code title}, {@code sheetType} -- via a
    * properties-only field mask. Used by BOTH catalog phases: level 2 of {@code listDatasets}
    * (A2), and {@code describeDataset}'s call 1, which resolves the dataset id's numeric
    * {@code sheetId} to a sheet title and, by failing for an unknown spreadsheet or a since-deleted
    * sheet, enforces X6.
    *
    * <p>Deliberately NOT a reuse of {@link #listWorksheets}: that method returns
    * {@code String[][]} and therefore cannot carry {@code sheetType} (needed to filter non-GRID
    * sheets, A18); it fetches the ENTIRE spreadsheet metadata document with no field mask
    * (`:238`); and it is reachable from {@link GDataQuery#getWorksheets()} on the UI dialog path,
    * which this change must not alter (N2).
    *
    * <p>{@code gridProperties} is deliberately absent from this field mask. Adding it is free, and
    * that is exactly the problem: a {@code columnCount} sitting on the object is a
    * {@code columnCount} someone will use, and it is the wrong number for sizing a sample range
    * (T1 -- a new sheet is 1000x26 empty). Omitting it makes the trap unreachable rather than
    * merely un-taken.
    */
   static List<SheetProperties> listSheetProperties(GDataDataSource ds, String spreadsheetId)
      throws IOException
   {
      Spreadsheet response = getSheets(ds, false).spreadsheets().get(spreadsheetId)
         .setFields("sheets.properties(sheetId,title,sheetType)")
         .execute();
      List<SheetProperties> properties = new ArrayList<>();

      if(response.getSheets() != null) {
         for(Sheet sheet : response.getSheets()) {
            properties.add(sheet.getProperties());
         }
      }

      return properties;
   }

   /**
    * The header row plus up to 3 data rows of one sheet, via a single row-only A1 range
    * (e.g. {@code 'Sheet1'!1:4}) -- {@code describeDataset}'s call 2 (A17). Bounded to 4 rows
    * regardless of the sheet's actual size: {@code runQuery} fetches
    * {@code A1:<cols><rowCount>}, i.e. the entire sheet, and this method must not copy that
    * (Part D.14).
    *
    * <p>[assumption] that a row-only A1 range is accepted by
    * {@code spreadsheets().get().setRanges(...)} -- this is A1 notation's documented form but
    * could not be exercised against a live server in this environment. If a live call ever rejects
    * it: add {@code gridProperties.columnCount} to {@link #listSheetProperties}'s field mask, build
    * {@code A1:<letter(columnCount)>4} instead, and rely entirely on the caller's width-narrowing
    * step (the T1 workaround) for the real column count -- A5 still passes, because it asserts the
    * REPORTED column count, not the requested range.
    */
   static List<RowData> fetchSampleRows(GDataDataSource ds, String spreadsheetId, String a1Range)
      throws IOException
   {
      Spreadsheet response = getSheets(ds, false).spreadsheets().get(spreadsheetId)
         .setRanges(Collections.singletonList(a1Range))
         .setFields("sheets.data.rowData.values(effectiveValue,effectiveFormat.numberFormat)")
         .execute();

      if(response.getSheets() == null || response.getSheets().isEmpty()) {
         return List.of();
      }

      Sheet sheet = response.getSheets().get(0);

      if(sheet.getData() == null || sheet.getData().isEmpty() ||
         sheet.getData().get(0).getRowData() == null)
      {
         return List.of();
      }

      return sheet.getData().get(0).getRowData();
   }

   private static HttpRequestInitializer createInitializer(GDataDataSource ds, boolean saveTokens) {
      return new GDataRequestInitializer(ds, saveTokens);
   }

   static String[][] listWorksheets(GDataDataSource ds, String spreadsheetId) throws IOException {
      List<String[]> worksheets = new ArrayList<>();
      Spreadsheet response = getSheets(ds, false).spreadsheets().get(spreadsheetId).execute();

      for(Sheet sheet : response.getSheets()) {
         worksheets.add(new String[] {
            sheet.getProperties().getTitle(), Integer.toString(sheet.getProperties().getSheetId())
         });
      }

      return worksheets.toArray(new String[0][]);
   }

   private String getColumnName(int column) {
      StringBuilder columnName = new StringBuilder();
      int dividend = column;
      int modulo;

      while(dividend > 0) {
         modulo = (dividend - 1) % 26;
         columnName.insert(0, Character.toChars(65 + modulo));
         dividend = (dividend - modulo) / 26;
      }

      return columnName.toString();
   }

   /**
    * Get a valid header name that doesn't contain leading or trailing spaces
    */
   private String getValidHeaderName(String name, int col, GDataQuery query) {
      if(name == null) {
         return null;
      }

      String trimmedName = name.trim();

      // if trimmed is the same then just return
      if(trimmedName.equals(name)) {
         return name;
      }

      XTypeNode[] cols = query.getOutputColumns();

      // if a new query
      if(cols == null || cols.length <= col) {
         return trimmedName;
      }
      // for existing query check if the col name is untrimmed and if so then leave it unchanged
      // so that it doesn't break existing ws/vs bindings
      else if(name.equals(cols[col].getName())) {
         return name;
      }

      return trimmedName;
   }

   private static final Logger LOG = LoggerFactory.getLogger(GDataRuntime.class.getName());

   static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
   private static final HttpTransport HTTP_TRANSPORT;
   private static final String APPLICATION_ID = "InetSoft-GoogleSheetsDataLoader/1.0";
   private static final OffsetDateTime BASE_DATE =
      OffsetDateTime.of(1899, 12, 30, 0, 0, 0, 0, ZoneOffset.UTC);

   static {
      try {
         HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      }
      catch(Exception e) {
         throw new ExceptionInInitializerError(e);
      }
   }
}

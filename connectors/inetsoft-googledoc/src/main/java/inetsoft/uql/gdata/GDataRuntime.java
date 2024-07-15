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
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class GDataRuntime extends TabularRuntime {
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      XSwappableTable table = new XSwappableTable();
      GDataQuery gdataQuery = (GDataQuery) query;
      GDataDataSource ds = (GDataDataSource) query.getDataSource();

      try {
         Sheets service = getSheets(ds, true);
         Spreadsheet spreadsheet = service.spreadsheets()
            .get(gdataQuery.getSpreadsheetId())
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
            .get(gdataQuery.getSpreadsheetId())
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
         Tool.addUserMessage("Failed to execute Google query: " + ds.getName() +
                             " (" + ex.getMessage() + ")");
         handleError(params, ex, () -> null);
      }

      return new XTableTableNode(table);
   }

   public void testDataSource(TabularDataSource ds, VariableTable params) throws Exception {
      GDataDataSource gdataDs = (GDataDataSource) ds;
      listSpreadsheets(gdataDs);
   }

   private static Sheets getSheets(GDataDataSource ds, boolean saveTokens) {
      return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, createInitializer(ds, saveTokens))
         .setApplicationName(APPLICATION_ID)
         .build();
   }

   private static Drive getDrive(GDataDataSource ds) {
      return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, createInitializer(ds, true))
         .setApplicationName(APPLICATION_ID)
         .build();
   }

   private static HttpRequestInitializer createInitializer(GDataDataSource ds, boolean saveTokens) {
      return new GDataRequestInitializer(ds, saveTokens);
   }

   static String[][] listSpreadsheets(GDataDataSource ds) throws IOException {
      List<String[]> spreadsheets = new ArrayList<>();
      String pageToken = null;

      do {
         FileList result = getDrive(ds).files().list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
            .setFields("nextPageToken, files(id, name)")
            .setIncludeItemsFromAllDrives(true)
            .setIncludeTeamDriveItems(true)
            .setSupportsAllDrives(true)
            .setSupportsTeamDrives(true)
            .setPageToken(pageToken)
            .setPageSize(1000)
            .setOrderBy("name")
            .execute();

         for(File file : result.getFiles()) {
            spreadsheets.add(new String[] { file.getName(), file.getId() });

            if(spreadsheets.size() > 3500) {
               return spreadsheets.toArray(new String[0][]);
            }
         }

         pageToken = result.getNextPageToken();
      }
      while(pageToken != null);

      return spreadsheets.toArray(new String[0][]);
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

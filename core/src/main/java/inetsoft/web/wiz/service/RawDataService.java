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

package inetsoft.web.wiz.service;

import inetsoft.web.wiz.WizUtil;
import inetsoft.web.wiz.request.ExportDatabaseTableToCsvRequest;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.util.Tool;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Service
public class RawDataService {
   public RawDataService(XRepository xrepository, AssetRepository assetRepository) {
      this.xrepository = xrepository;
      this.assetRepository = assetRepository;
   }

   public void writeWorksheetTableCsvStream(String wsId, String tableName, XPrincipal principal,
                                            OutputStream outputStream)
      throws Exception
   {
      if(wsId == null || Tool.isEmptyString(wsId)) {
         throw new RuntimeException("Invalid request.");
      }

      AssetEntry entry = AssetEntry.createAssetEntry(wsId);
      AbstractSheet sheet = assetRepository.getSheet(entry, principal, true, AssetContent.ALL);

      if(!(sheet instanceof Worksheet)) {
         throw new RuntimeException("Worksheet " + wsId + " not found.");
      }

      AssetQuerySandbox box = new AssetQuerySandbox((Worksheet) sheet);

      Assembly targetTable = null;

      for(Assembly assembly : sheet.getAssemblies()) {
         if(assembly instanceof TableAssembly tableAssembly &&
            Tool.equals(tableName, tableAssembly.getAbsoluteName()))
         {
            targetTable = tableAssembly;
            break;
         }
      }

      if(targetTable == null) {
         throw new RuntimeException("Table " + tableName + " not found.");
      }

      TableLens lens =
         box.getTableLens(targetTable.getAbsoluteName(), AssetQuerySandbox.RUNTIME_MODE);

      writeCsvContent(lens, outputStream);
   }

   public void writeDataSourceTableCsvStream(ExportDatabaseTableToCsvRequest requestData,
                                             XPrincipal principal, OutputStream outputStream)
      throws Exception
   {
      String datasourcePath = requestData.getDatasourcePath();
      XDataSource dataSource = xrepository.getDataSource(datasourcePath);

      if(!(dataSource instanceof JDBCDataSource)) {
         throw new Exception("Data source " + datasourcePath + " not found.");
      }

      JDBCQuery query = new JDBCQuery();
      UniformSQL sql = new UniformSQL();
      query.setSQLDefinition(sql);
      query.setDataSource(dataSource);

      AssetEntry tableEntry = requestData.getTable();
      setupTable(query, tableEntry, principal);

      AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
      AssetEntry.Selector selector =
         new AssetEntry.Selector(AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL);
      AssetEntry[] entries = assetRepository.getEntries(
         tableEntry, principal, ResourceAction.READ, selector);

      if(entries == null || entries.length == 0) {
         throw new RuntimeException("No columns found.");
      }

      setupColumns(query, entries);

      XDataService service = XFactory.getDataService();
      XNode result = service.execute(principal.getName(), query, null, principal, true, null);

      writeCsvContent(new XNodeTableLens(result), outputStream);
   }

   private void setupTable(JDBCQuery query, AssetEntry table, XPrincipal principal) throws Exception {
      JDBCDataSource dataSource = (JDBCDataSource) query.getDataSource();
      String session = System.getProperty("user.name");
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      String name = table.getProperty("source_with_no_quote");
      SelectTable selectTable = sql.addTable(name);
      selectTable.setCatalog(table.getProperty(XSourceInfo.CATALOG));
      selectTable.setSchema(table.getProperty(XSourceInfo.SCHEMA));
      sql.removeAllFields();
      JDBCUtil.fixUniformSQLInfo(sql, xrepository, session, dataSource, principal);
   }

   private void setupColumns(JDBCQuery query, AssetEntry[] entries) {
      if(query == null || entries == null) {
         return;
      }

      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      for(AssetEntry entry : entries) {
         String table = sql.getTableAlias(0);
         String path = Tool.buildString(table, ".", entry.getProperty("attribute"));
         selection.addColumn(path);
         selection.setTable(path, table);
         selection.setType(path,
                           entry.getProperty("dtype") != null ? entry.getProperty("dtype") : XField.STRING_TYPE);
      }
   }

   private void writeCsvContent(TableLens lens, OutputStream outputStream) throws IOException {
      int loadRows =
         Integer.parseInt(SreeEnv.getProperty(WizUtil.ANNOTATION_RAW_DATA_MAX_ROW, "100000"));

      if(Util.getOrganizationMaxRow() > 0) {
         loadRows = Math.min(loadRows, Util.getOrganizationMaxRow() + 1);
      }

      lens.moreRows(loadRows);

      int start = 0;
      int rowCount = lens.getRowCount();
      int colCount = lens.getColCount();

      BufferedWriter writer =
         new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

      for(int row = start; row < rowCount; row++) {
         for(int col = 0; col < colCount; col++) {
            if(col > 0) {
               writer.write(',');
            }

            Object value = lens.getObject(row, col);
            String cell = Tool.toString(value);

            if(cell.contains(",") || cell.contains("\"") || cell.contains("\n")) {
               writer.write('"');
               writer.write(cell.replace("\"", "\"\""));
               writer.write('"');
            }
            else {
               writer.write(cell);
            }
         }

         writer.write('\n');

         if(row > 0 && row % 1000 == 0) {
            writer.flush();
         }
      }

      writer.flush();
   }

   private final XRepository xrepository;
   private final AssetRepository assetRepository;
}

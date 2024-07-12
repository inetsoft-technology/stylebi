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
package inetsoft.util.oak;

import inetsoft.sree.RepletRegistry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.SreeHome;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;

import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@SreeHome("Put the path to the target sree.home here")
@Disabled
public class GenerateDataTest {
   private AssetRepository assetRepository;
   private XRepository xRepository;
   private RepletRegistry repletRegistry;
   private XEmbeddedTable embeddedTable;

   @BeforeEach
   void beforeEach() throws Exception {
      assetRepository = AssetUtil.getAssetRepository(false);
      xRepository = XFactory.getRepository();
      repletRegistry = RepletRegistry.getRegistry();

      String[] types = {
         XSchema.INTEGER, XSchema.STRING, XSchema.STRING, XSchema.STRING, XSchema.STRING,
         XSchema.STRING
      };
      List<Object[]> rows = new ArrayList<>();

      try(BufferedReader reader = new BufferedReader(new InputStreamReader(
         GenerateDataTest.class.getResourceAsStream("customers.csv"))))
      {
         String line = reader.readLine();
         String[] row = line.split(",");
         Object[] rowData = new Object[row.length];
         System.arraycopy(row, 0, rowData, 0, row.length);
         rows.add(rowData);

         while((line = reader.readLine()) != null) {
            if(!line.isEmpty()) {
               row = line.split(",");
               rowData = new Object[row.length];

               for(int i = 0; i < row.length; i++) {
                  if(i == 0) {
                     rowData[i] = Integer.valueOf(row[i]);
                  }
                  else {
                     rowData[i] = row[i];
                  }
               }
            }
         }
      }

      embeddedTable = new XEmbeddedTable(types, rows.toArray(new Object[0][]));
   }

   @Test
   void generateTestData() throws Exception {
      List<String> viewsheets = new ArrayList<>();

      generateDataSources();

      for(int i = 0; i < 5; i++) {
         generateReportFolder("", 0, i, new String[0], 0);
      }

      for(int i = 0; i < 5; i++) {
         generateWorksheetFolder("", 0, i);
      }

      for(int i = 0; i < 5; i++) {
         generateViewsheetFolder("", 0, i, viewsheets);
      }

      generateTasks(viewsheets);
   }

   private void generateDataSources() throws Exception {
      DataSourceFolder folder = new DataSourceFolder("Shared Data Sources", LocalDateTime.now(), null);
      xRepository.updateDataSourceFolder(folder, null);

      for(int i = 0; i < 5; i++) {
         String dataSourceName = String.format("Shared Data Sources/Data Source %d", i + 1);
         System.err.println("generate data source: " + dataSourceName);
         JDBCDataSource dataSource = new JDBCDataSource();
         dataSource.setName(dataSourceName);
         dataSource.setCustom(true);
         dataSource.setDriver("org.h2.Driver");
         dataSource.setURL("jdbc:h2:mem:db1;INIT=create table SALES(REGION VARCHAR(255), TX_DATE TIMESTAMP, AMOUNT DECIMAL(8,2))");
         dataSource.setRequireLogin(false);
         xRepository.updateDataSource(dataSource, null, false);

         String queryFolder = String.format("DS %d Queries", i + 1);

         for(int j = 0; j < 5; j++) {
            JDBCQuery query = new JDBCQuery();
            query.setDataSource(xRepository.getDataSource(dataSourceName));
            query.setName(String.format("DS %d Query %d", i + 1, j + 1));
            query.setFolder(queryFolder);
            query.setSQLDefinition(new UniformSQL("select REGION, TX_DATE, AMOUNT from SALES order by REGION, TX_DATE", true));
            xRepository.updateQuery(query, null);
         }
      }
   }

   private int generateReportFolder(String parentPath, int level, int index, String[] queries,
                                    int queryIndex) throws Exception
   {
      String path;

      if(level == 0) {
         path = "" + ((char) ('A' + index));
      }
      else {
         AssetEntry parent = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, parentPath, null);
         String parentName = parent.getName();
         String name = parentName + ((char) ('A' + index));
         path = parentPath + "/" + name;
      }

      repletRegistry.addFolder(path);
      repletRegistry.save();

      if(level < 3) {
         for(int i = 0; i < 5; i++) {
            queryIndex = generateReportFolder(path, level + 1, i, queries, queryIndex);
         }
      }

      return queryIndex;
   }

   private void generateWorksheetFolder(String parentPath, int level, int index) throws Exception {
      AssetEntry entry;

      if(level == 0) {
         String name = "" + ((char) ('A' + index));
         entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, name, null);
      }
      else {
         AssetEntry parent = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, parentPath, null);
         String parentName = parent.getName();
         String name = parentName + ((char) ('A' + index));
         String path = parentPath + "/" + name;
         entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, path, null);
      }

      assetRepository.addFolder(entry, null);

      for(int i = 0; i < 5; i++) {
         generateWorksheet(entry.getPath(), i);
      }

      if(level < 3) {
         for(int i = 0; i < 5; i++) {
            generateWorksheetFolder(entry.getPath(), level + 1, i);
         }
      }
   }

   private void generateWorksheet(String parentPath, int index) throws Exception {
      AssetEntry parent = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, parentPath, null);
      String parentName = parent.getName();
      String name = parentName + String.format("WS%02d", index + 1);
      String path = parentPath + "/" + name;
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null);
      System.err.println("generate ws: " + path);
      assetRepository.setSheet(entry, createWorksheet(), null, false);
   }

   private Worksheet createWorksheet() {
      Worksheet worksheet = new Worksheet();
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(worksheet, "customers");
      assembly.setEmbeddedData(embeddedTable);
      worksheet.addAssembly(assembly);
      worksheet.setPrimaryAssembly(assembly);
      worksheet.setCreated(System.currentTimeMillis());
      worksheet.setCreatedBy("anonymous");
      worksheet.setLastModified(System.currentTimeMillis());
      worksheet.setLastModifiedBy("anonymous");
      return worksheet;
   }

   private void generateViewsheetFolder(String parentPath, int level, int index,
                                        List<String> viewsheets) throws Exception
   {
      AssetEntry entry;

      if(level == 0) {
         String name = "" + ((char) ('A' + index));
         entry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, name, null);
      }
      else {
         AssetEntry parent = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, parentPath, null);
         String parentName = parent.getName();
         String name = parentName + ((char) ('A' + index));
         String path = parentPath + "/" + name;
         entry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, path, null);
      }

      assetRepository.addFolder(entry, null);

      for(int i = 0; i < 5; i++) {
         generateViewsheet(entry.getPath(), i, viewsheets);
      }

      if(level < 3) {
         for(int i = 0; i < 5; i++) {
            generateViewsheetFolder(entry.getPath(), level + 1, i, viewsheets);
         }
      }
   }

   public void generateViewsheet(String parentPath, int index, List<String> viewsheets)
      throws Exception
   {
      AssetEntry parent = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, parentPath, null);
      String parentName = parent.getName();
      String name = parentName + String.format("VS%02d", index + 1);
      String path = parentPath + "/" + name;
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null);
      String wsName = parentName + String.format("WS%02d", index + 1);
      String wsPath = parentPath + "/" + wsName;
      AssetEntry wsEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, wsPath, null);
      wsEntry = assetRepository.getAssetEntry(wsEntry);
      System.err.println("generate vs: " + path);
      viewsheets.add(entry.toIdentifier());
      assetRepository.setSheet(entry, createViewsheet(wsEntry), null, false);
   }

   private Viewsheet createViewsheet(AssetEntry wsEntry) {
      Viewsheet viewsheet = new Viewsheet(wsEntry);
      viewsheet.setCreated(System.currentTimeMillis());
      viewsheet.setCreatedBy("anonymous");
      viewsheet.setLastModified(System.currentTimeMillis());
      viewsheet.setLastModifiedBy("anonymous");

      TableVSAssembly assembly = new TableVSAssembly();
      TableVSAssemblyInfo ainfo = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      ainfo.setPixelOffset(new Point(20, 20));
      ainfo.setPixelSize(new Dimension(400, 200));
      ainfo.setPrimary(true);
      ainfo.setZIndex(1);
      ainfo.setHeaderRowHeights(new int[] { 20 });
      ainfo.setDataRowHeight(20);
      ainfo.setName("TableView1");
      ainfo.setSourceInfo(new SourceInfo(7, null, "customers"));
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(createColumn("id"));
      columns.addAttribute(createColumn("first_name"));
      columns.addAttribute(createColumn("lastName"));
      columns.addAttribute(createColumn("email"));
      columns.addAttribute(createColumn("cc_type"));
      columns.addAttribute(createColumn("cc_number"));
      ainfo.setColumnSelection(columns);
      viewsheet.addAssembly(assembly);

      return viewsheet;
   }

   private ColumnRef createColumn(String name) {
      AttributeRef attr = new AttributeRef(name);
      attr.setSqlType(12);
      ColumnRef column = new ColumnRef(attr);
      column.setSqlType(12);

      if("id".equals(name)) {
         column.setDataType("integer");
      }

      return column;
   }

   private void generateTasks(List<String> viewsheets) throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, "/", null);
      AssetFolder folder = new AssetFolder();
      IndexedStorage.getIndexedStorage().putXMLSerializable(entry.toIdentifier(), folder);

      for(int i = 0; i < 200; i++) {
         generateTask(i, viewsheets);
      }
   }

   private void generateTask(int index, List<String> viewsheets) throws Exception {
      String name = String.format("admin:Task %03d", index + 1);
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK, name, null);
      System.err.println("generate task: " + name);

      ScheduleTask task = new ScheduleTask();
      task.setName(name);
      task.setOwner(new IdentityID("admin", OrganizationManager.getCurrentOrgName()));
      task.setEnabled(true);
      task.setDeleteIfNoMoreRun(false);
      task.setDurable(false);
      task.setPath("/");

      TimeCondition condition = new TimeCondition();
      condition.setType(6);
      condition.setHour(1);
      condition.setMinute(30);
      condition.setSecond(0);
      condition.setHourEnd(23);
      condition.setMinuteEnd(-1);
      condition.setSecondEnd(-1);
      condition.setDaysOfWeek(new int[] { 6 });
      condition.setInterval(1);
      task.addCondition(condition);

      ViewsheetAction action = new ViewsheetAction(viewsheets.get(index), null);
      action.setBookmarks(new String[]{"(Home)"});
      action.setBookmarkTypes(new int[]{1});
      action.setFilePath(2, "/tmp/" + name + ".pdf");
      task.addAction(action);

      ScheduleManager.getScheduleManager().setScheduleTask(
         name, task, entry.getParent(), SUtil.getPrincipal(new IdentityID("admin", OrganizationManager.getCurrentOrgName()), "localhost", false));
   }
}

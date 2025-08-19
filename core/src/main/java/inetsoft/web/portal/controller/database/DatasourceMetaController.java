/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.portal.controller.database;

import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.*;
import inetsoft.web.portal.data.GetTableColumnMetaRequest;
import inetsoft.web.portal.model.database.TableMeta;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class DatasourceMetaController {
   public DatasourceMetaController(XRepository xrepository, DataSourceService dataSourceService) {
      this.xrepository = xrepository;
      this.dataSourceService = dataSourceService;
   }

   @GetMapping(value = "/api/datasource/table/meta")
   public Object getMetaData(@RequestBody GetTableColumnMetaRequest data, XPrincipal principal) throws Exception {
      String dsName = data.getDsName();
      XDataSource dataSource = xrepository.getDataSource(dsName);

      if(!(dataSource instanceof JDBCDataSource jdbcDataSource)) {
         throw new Exception("Data source " + dsName + " not found.");
      }

      DefaultMetaDataProvider metaDataProvider = getMetaDataProvider(dsName);

      if(metaDataProvider == null) {
         throw new Exception("No meta data provider found for data source " + dsName);
      }

      XNode rootMetaData = metaDataProvider.getRootMetaData(XUtil.OUTER_MOSE_LAYER_DATABASE);

      if(rootMetaData == null) {
         throw new Exception("No meta data found for data source " + dsName);
      }

      SQLTypes sqlTypes = SQLTypes.getSQLTypes(jdbcDataSource);
      XNode node = sqlTypes.getQualifiedTableNode(data.getTableName(), data.isHasCatalog(),
                                                  data.isHasSchema(), data.getCatalogSep(), jdbcDataSource,
                                                  data.getCatalog(), data.getSchema());

      if(rootMetaData.getAttribute("supportCatalog") != null) {
         node.setAttribute("supportCatalog", rootMetaData.getAttribute("supportCatalog"));
      }

      XNode tableNode = metaDataProvider.getMetaData(node, true);

      if(tableNode == null) {
         throw new Exception("Table " + data.getTableName() + " not found in data source " + dsName);
      }

      TableMeta tableMeta = new TableMeta();
      tableMeta.setName(tableNode.getName());
      tableMeta.setCatalog(data.getCatalog());
      tableMeta.setSchema(data.getSchema());
      List<TableMeta.ColumnMeta> columns = new ArrayList<>();
      tableMeta.setColumns(columns);

      for(int i = 0; i < tableNode.getChildCount(); i++) {
         XNode columnNode = tableNode.getChild(i);

         if(columnNode == null) {
            continue;
         }

         TableMeta.ColumnMeta columnMeta = new TableMeta.ColumnMeta();
         columnMeta.setName(columnNode.getName());

         if(columnNode instanceof XTypeNode typeNode) {
            columnMeta.setType(typeNode.getType());
         }

         columnMeta.setPrimaryKey("true".equals(columnNode.getAttribute("PrimaryKey")));
         columnMeta.setLength((Integer) columnNode.getAttribute("length"));

         if(columnNode.getAttribute("ForeignKey") != null) {
            Vector<String[]> foreignKeys = (Vector<String[]>) columnNode.getAttribute("ForeignKey");
            List<String[]> foreignKeyList = new ArrayList<>(foreignKeys);
            columnMeta.setForeignKeys(foreignKeyList);
         }

         columns.add(columnMeta);
      }

      return tableNode;
   }

   private DefaultMetaDataProvider getMetaDataProvider(String database) {
      try {
         JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.getDataSource(database);
         XDataModel dataModel = dataSourceService.getDataModel(database);
         return dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
      }
      catch(Exception e) {
         return null;
      }
   }

   private final XRepository xrepository;
   private final DataSourceService dataSourceService;
}

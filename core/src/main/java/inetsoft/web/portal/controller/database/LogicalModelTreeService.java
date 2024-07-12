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
package inetsoft.web.portal.controller.database;

import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.portal.model.database.XAttributeModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Service
public class LogicalModelTreeService {
   public LogicalModelTreeService(XRepository repository, DataSourceService dataSourceService) {
      this.repository = repository;
      this.dataSourceService = dataSourceService;
   }

   /**
    * refresh partition meta when init to avoid get the out date metadata.
    */
   public void refreshPartitionMetaData(String database, String partition, String modelName,
                                        String modelParent, String connection)
      throws Exception
   {
      getPartitionMetaData(database, partition, modelName, modelParent, connection, true);
   }

   /**
    * refresh partition meta when init to avoid get the out date metadata.
    */
   public void refreshPartitionMetaData(String database, LogicalModelDefinition model)
      throws Exception
   {
      getPartitionMetaData(database, model.getPartition(), model.getName(),
         model.getParent(), model.getConnection(), true);
   }

   public XNode getPartitionMetaData(String database, String name, String logicalModelName,
                                     String logicalModelParent, String additional, boolean refresh)
      throws Exception
   {
      XDataModel dataModel = repository.getDataModel(database);

      if(dataModel == null) {
         throw new FileNotFoundException(database);
      }

      XPartition partition = dataModel.getPartition(name);

      if(partition == null) {
         throw new FileNotFoundException(database + "/" + name);
      }

      additional = additional != null ? additional : XUtil.OUTER_MOSE_LAYER_DATABASE;

      if(!StringUtils.isEmpty(logicalModelName) && !StringUtils.isEmpty(logicalModelParent)) {
         XLogicalModel pLogicalModel = dataModel.getLogicalModel(logicalModelParent);
         XLogicalModel logicalModel = null;

         if(pLogicalModel != null) {
            logicalModel = pLogicalModel.getLogicalModel(logicalModelName);
         }

         if(logicalModel != null && !StringUtils.isEmpty(logicalModel.getConnection())) {
            additional = logicalModel.getConnection();
         }
      }

      JDBCDataSource dataSource = (JDBCDataSource) dataSourceService.
         getDataSource(database, additional);
      DefaultMetaDataProvider metaData =
         dataSourceService.getDefaultMetaDataProvider(dataSource, dataModel);
      String paritionName = name;

      if(XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional) && logicalModelParent != null &&
         partition.containPartition("(Default Connection)"))
      {
         paritionName += ":(Default Connection)";
      }
      else if(!XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional) &&
         !StringUtils.isEmpty(additional))
      {
         paritionName += ":" + additional;
      }

      if(refresh) {
         metaData.refreshMetaData(paritionName, true, additional);
      }

      return metaData.getMetaData(paritionName, true, additional);
   }

   public TreeNodeModel getPhysicalModelTree(String database, String name, String logicalModelName,
                                             String logicalModelParent, String additional)
      throws Exception
   {
      XNode root = getPartitionMetaData(database, name, logicalModelName,
         logicalModelParent, additional, false);
      JDBCDataSource dataSource = (JDBCDataSource) repository.getDataSource(database);
      SQLTypes sqlTypes = SQLTypes.getSQLTypes(dataSource);
      List<TreeNodeModel> tables = new ArrayList<>();

      for(int i = 0; root!= null && i < root.getChildCount(); i++) {
         XNode tableNode = root.getChild(i);

         List<TreeNodeModel> columns = new ArrayList<>();

         for(int j = 0; j < tableNode.getChildCount(); j++) {
            XNode columnNode = tableNode.getChild(j);
            XAttributeModel column = new XAttributeModel();
            column.setName(columnNode.getName());
            column.setTable(tableNode.getName());
            column.setDataType(columnNode.getValue() != null ? (String) columnNode.getValue() :
               convertToXType(sqlTypes, columnNode.getAttribute("sqltype")) );
            column.setQualifiedName(column.getTable() + "." + column.getName());
            column.setType("column");

            TreeNodeModel columnNodeModel = TreeNodeModel.builder()
               .label(columnNode.getName())
               .data(column)
               .leaf(true)
               .build();

            columns.add(columnNodeModel);
         }

         TreeNodeModel tableNodeModel = TreeNodeModel.builder()
            .label(tableNode.getName())
            .data(tableNode.getName())
            .type("entity")
            .children(columns)
            .leaf(false)
            .build();

         tables.add(tableNodeModel);
      }

      return TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Physical Model Tables"))
         .children(tables)
         .leaf(false)
         .expanded(true)
         .build();
   }

   /**
    * Convert a SQL type to a XSchema type.
    */
   private String convertToXType(SQLTypes sqlTypes, Object sqlType) {
      if(sqlType == null) {
         return null;
      }

      int type = -1;

      if(sqlType instanceof Integer) {
         type = (Integer) sqlType;
      }
      else {
         try {
            type = Integer.parseInt(sqlType + "");
         }
         catch(NumberFormatException ignore) {
         }
      }

      String name = sqlTypes.convertToXType(type);
      return name == null ? XSchema.STRING : name;
   }

   private final XRepository repository;
   private final DataSourceService dataSourceService;
}

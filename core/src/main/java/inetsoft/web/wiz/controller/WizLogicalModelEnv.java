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
package inetsoft.web.wiz.controller;

import inetsoft.uql.XDataSource;
import inetsoft.uql.XNode;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.uql.util.XUtil;
import inetsoft.web.portal.controller.database.DataSourceService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The live {@link WizLogicalModelValidator.Env}: the data model of one data source and its
 * physical metadata, read through the same provider {@code LogicalModelService} uses, so the
 * validator and the service see the same tables and columns.
 */
final class WizLogicalModelEnv implements WizLogicalModelValidator.Env {
   WizLogicalModelEnv(String database, XDataModel dataModel, DataSourceService dataSourceService) {
      this(database, dataModel, dataSourceService, link -> link);
   }

   /**
    * @param viewsheets resolves a viewsheet drill link to its asset identifier, {@code null} when
    *                   no readable viewsheet is named
    */
   WizLogicalModelEnv(String database, XDataModel dataModel, DataSourceService dataSourceService,
                      java.util.function.UnaryOperator<String> viewsheets)
   {
      this.database = database;
      this.dataModel = dataModel;
      this.dataSourceService = dataSourceService;
      this.viewsheets = viewsheets;
   }

   @Override
   public String resolveViewsheet(String link) {
      return viewsheets.apply(link);
   }

   @Override
   public XDataModel dataModel() {
      return dataModel;
   }

   @Override
   public boolean connectionExists(String connection) {
      XDataSource source = dataSourceService.getDataSource(database);
      return source instanceof JDBCDataSource &&
         ((JDBCDataSource) source).getDataSource(connection) != null;
   }

   @Override
   public Map<String, Map<String, String>> metadata(XPartition view, String connection)
      throws Exception
   {
      String additional = connection == null ? XUtil.OUTER_MOSE_LAYER_DATABASE : connection;
      JDBCDataSource source = (JDBCDataSource) dataSourceService.getDataSource(
         dataModel.getDataSource(), additional);
      DefaultMetaDataProvider provider =
         dataSourceService.getDefaultMetaDataProvider(source, dataModel);
      XNode root = provider.getMetaData(view, true, additional);
      Map<String, Map<String, String>> tables = new LinkedHashMap<>();

      if(root == null) {
         return tables;
      }

      for(int i = 0; i < root.getChildCount(); i++) {
         XNode table = root.getChild(i);
         Map<String, String> columns = new LinkedHashMap<>();

         for(int j = 0; j < table.getChildCount(); j++) {
            XNode column = table.getChild(j);
            columns.put(column.getName(), dataType(source, column));
         }

         tables.put(table.getName(), columns);
      }

      return tables;
   }

   /** As {@code LogicalModelTreeService.getPhysicalModelTree}: the node value, else the SQL type. */
   private static String dataType(JDBCDataSource source, XNode column) {
      if(column.getValue() != null) {
         return column.getValue().toString();
      }

      Object sqlType = column.getAttribute("sqltype");
      int type = -1;

      if(sqlType instanceof Integer) {
         type = (Integer) sqlType;
      }
      else if(sqlType != null) {
         try {
            type = Integer.parseInt(sqlType.toString());
         }
         catch(NumberFormatException ignore) {
            // falls back to string below
         }
      }

      String name = null;

      if(sqlType != null) {
         try {
            name = SQLTypes.getSQLTypes(source).convertToXType(type);
         }
         catch(RuntimeException ignore) {
            // falls back to string below
         }
      }

      return name == null ? XSchema.STRING : name;
   }

   private final String database;
   private final XDataModel dataModel;
   private final DataSourceService dataSourceService;
   private final java.util.function.UnaryOperator<String> viewsheets;
}

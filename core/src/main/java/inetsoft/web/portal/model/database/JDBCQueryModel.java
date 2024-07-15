/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database;

import inetsoft.uql.XDataSource;
import inetsoft.uql.jdbc.*;

import java.util.*;

public class JDBCQueryModel {
   public JDBCQueryModel(JDBCQuery query) {
      super();

      setName(query.getName());
      setFolder(query.getFolder());
      XDataSource ds = query.getDataSource();
      setDatasource(ds == null ? null : ds.getFullName());
      setDescription(query.getDescription());
      SQLDefinition sql = query.getSQLDefinition();

      if(sql instanceof UniformSQL) {
         setSql(new UniformSQLModel((UniformSQL) sql));
      }

      setMaxrows(query.getMaxRows());
      setTimeout(query.getTimeout());
      Enumeration<String> vars = query.getDefinedVariables();
      List<String> vnames = new ArrayList<>();

      while(vars.hasMoreElements()) {
         vnames.add(vars.nextElement());
      }

      Collections.sort(vnames);
      variables = new XVariableModel[vnames.size()];

      for(int i = 0; i < vnames.size(); i++) {
         variables[i] = new XVariableModel(query.getVariable(vnames.get(i)));
      }
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getFolder() {
      return folder;
   }

   public void setFolder(String folder) {
      this.folder = folder;
   }

   public String getDatasource() {
      return datasource;
   }

   public void setDatasource(String datasource) {
      this.datasource = datasource;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public UniformSQLModel getSql() {
      return sql;
   }

   public void setSql(UniformSQLModel sql) {
      this.sql = sql;
   }

   public int getMaxrows() {
      return maxrows;
   }

   public void setMaxrows(int maxrows) {
      this.maxrows = maxrows;
   }

   public int getTimeout() {
      return timeout;
   }

   public void setTimeout(int timeout) {
      this.timeout = timeout;
   }

   public XVariableModel[] getVariables() {
      return variables;
   }

   public void setVariables(XVariableModel[] variables) {
      this.variables = variables;
   }

   private String name;
   private String folder;
   private String datasource;
   private String description;
   private UniformSQLModel sql;
   private int maxrows = 0;
   private int timeout = 0;

   private XVariableModel[] variables;
}
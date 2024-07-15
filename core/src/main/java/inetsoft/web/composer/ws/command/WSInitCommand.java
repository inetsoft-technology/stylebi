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
package inetsoft.web.composer.ws.command;

import inetsoft.sree.security.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

import java.security.Principal;
import java.util.Arrays;

/**
 * Set initialization info for WS.
 */
public class WSInitCommand implements ViewsheetCommand {
   public WSInitCommand(Principal principal) {
      final DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      try {
         hasJDBC = Arrays.stream(registry.getDataSourceFullNames())
            .anyMatch(name -> registry.getDataSource(name) instanceof JDBCDataSource);
      }
      catch(Exception ex) {
         // ignore
      }

      SecurityEngine security = SecurityEngine.getSecurity();

      try {
         sqlEnabled = security.checkPermission(
            principal, ResourceType.PHYSICAL_TABLE, "*", ResourceAction.ACCESS);
      }
      catch(Exception ex) {
         sqlEnabled = false;
      }

      try {
         freeFormSqlEnabled = security.checkPermission(
            principal, ResourceType.FREE_FORM_SQL, "*", ResourceAction.ACCESS);
      }
      catch(Exception ex) {
         freeFormSqlEnabled = false;
      }

      try {
         crossJoinEnabled = security.checkPermission(
            principal, ResourceType.CROSS_JOIN, "*", ResourceAction.ACCESS);
      }
      catch(Exception ex) {
         crossJoinEnabled = false;
      }
   }

   public boolean isJdbcExists() {
      return hasJDBC;
   }

   public boolean isSqlEnabled() {
      return sqlEnabled;
   }

   public boolean isFreeFormSqlEnabled() {
      return freeFormSqlEnabled;
   }

   public boolean isCrossJoinEnabled() {
      return crossJoinEnabled;
   }

   private boolean hasJDBC;
   private boolean sqlEnabled;
   private boolean freeFormSqlEnabled;
   private boolean crossJoinEnabled;
}

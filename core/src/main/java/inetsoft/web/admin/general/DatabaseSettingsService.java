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
package inetsoft.web.admin.general;

import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.util.Catalog;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.general.model.DatabaseSettingsModel;
import inetsoft.web.admin.security.ConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.sql.*;
import java.util.Properties;

@Service
public class DatabaseSettingsService {

   public ConnectionStatus testConnection(DatabaseSettingsModel model,
                                          Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      String url = model.databaseURL();
      String driver = model.driver();
      String defaultDB = model.defaultDB();
      String username = "";
      String password = "";

      if(model.requiresLogin()) {
         username = model.username();
         password = model.password();
      }

      if(url != null && url.contains("$(sree.home)")) {
         url = url.replace(
            "$(sree.home)", ConfigurationContext.getContext().getHome().replace('\\', '/'));
      }

      if(url == null || url.isEmpty() || driver == null || driver.isEmpty()) {
         // incomplete configuration, don't bother trying to test it
         return new ConnectionStatus(
            catalog.getString("em.data.databases.testConfigurationError"));
      }

      Driver realDriver;

      try {
         realDriver = JDBCHandler.getDriver(driver);
         Properties properties = new Properties();

         if(model.requiresLogin() && username != null && password != null) {
            properties.setProperty("user", username);
            properties.setProperty("password", password);
         }

         try(Connection conn = realDriver.connect(url, properties)) {
            // MS Access do not throw Exception when url is wrong, but connection is null.
            // so we should throw Exception.
            if(conn == null) {
               throw new Exception("jdbc url is wrong");
            }

            if(defaultDB != null && !defaultDB.isEmpty()) {
               try {
                  conn.setCatalog(defaultDB);
               }
               catch(Exception e) {
                  conn.setCatalog("[" + defaultDB + "]");
               }
            }
         }
      }
      catch(ClassNotFoundException e) {
         LOG.error("Failed to instantiate JDBC driver {}", driver, e);
         return new ConnectionStatus(catalog.getString("em.data.databases.testDriverError"));
      }
      catch(SQLException e) {
         LOG.error("Failed to connect to database {}", url, e);
         return new ConnectionStatus(
            catalog.getString("em.data.databases.testConnectionError", e.getMessage()));
      }
      catch(Exception e) {
         LOG.error("Failed to test connection", e);
         return new ConnectionStatus(catalog.getString("em.data.databases.testError"));
      }

      return new ConnectionStatus(catalog.getString("em.security.testlogin.note2"), true);
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseSettingsService.class);
}

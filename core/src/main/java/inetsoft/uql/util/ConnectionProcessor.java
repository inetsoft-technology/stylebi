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
package inetsoft.uql.util;

import inetsoft.uql.*;

import java.security.Principal;

public class ConnectionProcessor {
   public static ConnectionProcessor getInstance() {
      ConnectionProcessor processor = ConnectionProcessor.processor;

      if(processor == null) {
         try {
            processor = (ConnectionProcessor)
               Class.forName("inetsoft.enterprise.datasource.ConnectionProcessor").newInstance();
         }
         catch(Exception ex) {
            processor = new ConnectionProcessor();
         }

         ConnectionProcessor.processor = processor;
      }

      return processor;
   }

   public XDataSource getAdditionalConnection(XDataSource dx) {
      return dx;
   }

   /**
    * Set additional data sources.
    */
   public void setAdditionalDatasource(XPrincipal principal) {
   }

   /**
    * Get additional data source.
    */
   public XDataSource getDatasource(Principal user, XDataSource ds) {
      return ds;
   }

   /**
    * Get a specific additional data source.
    */
   public XDataSource getDatasource(Principal user, XDataSource ds, String additional) {
      return ds;
   }

   /**
    * Get a data source for the user.
    */
   public String getAdditionalDatasource(Principal user, String dsname, String additional) {
      return null;
   }

   private static ConnectionProcessor processor;
}

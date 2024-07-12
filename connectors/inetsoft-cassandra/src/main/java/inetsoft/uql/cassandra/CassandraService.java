/*
 * inetsoft-cassandra - StyleBI is a business intelligence web application.
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
package inetsoft.uql.cassandra;

import inetsoft.uql.tabular.TabularService;

import java.util.Locale;
import java.util.ResourceBundle;

public class CassandraService extends TabularService {
   public String getDataSourceType() {
      return CassandraDataSource.TYPE;
   }

   public String getDataSourceClass() {
      return "inetsoft.uql.cassandra.CassandraDataSource";
   }

   public String getQueryClass() {
      return "inetsoft.uql.cassandra.CassandraQuery";
   }

   public String getRuntimeClass() {
      return "inetsoft.uql.cassandra.CassandraRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return ResourceBundle.getBundle("inetsoft.uql.cassandra.Bundle", locale)
         .getString("display.name");
   }
}
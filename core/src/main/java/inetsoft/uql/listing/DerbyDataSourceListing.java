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
package inetsoft.uql.listing;

import inetsoft.uql.DataSourceListing;
import inetsoft.uql.XDataSource;
import inetsoft.uql.jdbc.JDBCDataSource;

public class DerbyDataSourceListing extends DataSourceListing {
   public DerbyDataSourceListing() {
      super("Derby", "Relational Database", "/inetsoft/uql/listing/derby.svg");
   }

   @Override
   public XDataSource createDataSource() throws Exception {
      final JDBCDataSource ds = new JDBCDataSource();
      ds.setName(getAvailableName());
      ds.setURL("jdbc:derby://<host>[:<port>]/databaseName");
      ds.setDriver("org.apache.derby.jdbc.ClientDriver");

      return ds;
   }
}

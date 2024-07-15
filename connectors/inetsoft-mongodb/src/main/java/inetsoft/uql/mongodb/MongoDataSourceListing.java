/*
 * inetsoft-mongodb - StyleBI is a business intelligence web application.
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
package inetsoft.uql.mongodb;

import inetsoft.uql.DataSourceListing;
import inetsoft.uql.XDataSource;

import java.rmi.RemoteException;

public class MongoDataSourceListing extends DataSourceListing {
   public MongoDataSourceListing() {
      super("MongoDB REST", "NoSQL", "/inetsoft/uql/mongodb/mongodb.svg");
   }

   @Override
   public XDataSource createDataSource() throws RemoteException {
      MongoDataSource dataSource = new MongoDataSource();
      dataSource.setName(getAvailableName());
      return dataSource;
   }
}

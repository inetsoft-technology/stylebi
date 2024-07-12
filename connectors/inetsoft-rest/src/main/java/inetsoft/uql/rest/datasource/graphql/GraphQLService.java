/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.datasource.graphql;

import inetsoft.uql.tabular.TabularService;

public class GraphQLService extends TabularService {
   @Override
   public String getDataSourceType() {
      return GraphQLDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return GraphQLDataSource.class.getName();
   }

   @Override
   public String getQueryClass() {
      return GraphQLQuery.class.getName();
   }

   @Override
   public String getRuntimeClass() {
      return GraphQLRuntime.class.getName();
   }
}

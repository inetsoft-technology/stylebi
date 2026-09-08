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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.rest.AbstractRestRuntime;
import inetsoft.uql.rest.QueryRunner;
import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularCatalogProvider;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularDatasetSchema;

public class RestXMLRuntime extends AbstractRestRuntime implements TabularCatalogProvider {
   @Override
   protected QueryRunner getQueryRunner(AbstractRestQuery q) {
      final RestXMLQuery query = (RestXMLQuery) q;
      final XMLRestDataIteratorStrategyFactory factory = new XMLRestDataIteratorStrategyFactory();
      return new RestXMLQueryRunner(query, factory);
   }

   /**
    * Always throws: Rest.XML's dataset structure (URL suffix, XPath, columns) is declared per
    * query, not on the data source itself, so there is no server-side resource list to enumerate.
    */
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      throw new Exception("Rest.XML does not support catalog enumeration: its dataset " +
         "structure (URL suffix, XPath, columns) is declared per-query, not on the data " +
         "source itself, so there is no server-side resource list to enumerate. Describe a " +
         "specific endpoint directly via describeDataset with an opaque endpoint token instead.");
   }

   /**
    * {@code dataSource} is intentionally unused: the whole point of the opaque-token design is
    * that {@code RestXMLDataSource} carries nothing (just a bare URL) the token doesn't already
    * carry itself.
    */
   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      return RestXmlEndpointCatalog.describeDataset(datasetId);
   }
}

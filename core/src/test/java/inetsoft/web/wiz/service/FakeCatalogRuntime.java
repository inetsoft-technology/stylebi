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
package inetsoft.web.wiz.service;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XTableNode;
import inetsoft.uql.tabular.*;

import java.util.Map;

/**
 * A connector that exists only in this test source tree. If core needed anything OData-specific
 * to serve the non-JDBC branch, these tests would fail, because this class cannot satisfy it —
 * that is the whole point of B5 (charter: core carries zero OData knowledge).
 */
public class FakeCatalogRuntime extends TabularRuntime implements TabularCatalogProvider {
   public FakeCatalogRuntime(TabularCatalog catalog, Map<String, TabularDatasetSchema> schemas) {
      this.catalog = catalog;
      this.schemas = schemas;
   }

   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) {
      return catalog;
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      TabularDatasetSchema schema = schemas.get(datasetId);

      if(schema == null) {
         throw new Exception("Unknown fake dataset: " + datasetId);
      }

      return schema;
   }

   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      throw new UnsupportedOperationException("not used by these tests");
   }

   @Override
   public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      throw new UnsupportedOperationException("not used by these tests");
   }

   private final TabularCatalog catalog;
   private final Map<String, TabularDatasetSchema> schemas;
}

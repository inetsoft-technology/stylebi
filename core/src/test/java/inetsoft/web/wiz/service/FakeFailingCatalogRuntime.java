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

/**
 * A catalog provider whose SPI methods always fail — proves a connector-side failure surfaces to
 * the caller instead of being swallowed into a silently-empty (and therefore falsely successful)
 * annotation.
 */
public class FakeFailingCatalogRuntime extends TabularRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      throw new Exception("fake catalog listing failure");
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      throw new Exception("fake dataset description failure");
   }

   @Override
   public XTableNode runQuery(TabularQuery query, VariableTable params) {
      throw new UnsupportedOperationException("not used by these tests");
   }

   @Override
   public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
      throw new UnsupportedOperationException("not used by these tests");
   }
}

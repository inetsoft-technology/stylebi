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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

/**
 * Regression coverage for Redmine #76075: a non-JDBC/tabular data source (Cassandra, OData, etc.)
 * must stay expandable (leaf: false) in the wiz annotation tree, since support for these sources
 * is under active development. A previous fix (#4581) marked these leaf, was closed as a
 * regression, but was accidentally reintroduced via an unrelated merge -- this guards against
 * that reintroduction happening again.
 */
@Tag("core")
class MetadataApiServiceFilterWizTreeTest {
   private MetadataApiService service(XRepository repo) {
      return new MetadataApiService(repo, mock(DataSourceService.class),
                                    mock(AssetRepository.class), mock(AssetTreeService.class),
                                    new ObjectMapper());
   }

   private AssetEntry dataSourceEntry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_SOURCE, name, null);
   }

   private TreeNodeModel node(String name) {
      return TreeNodeModel.builder().label(name).data(dataSourceEntry(name)).leaf(false).build();
   }

   @Test
   void filterWizTreeDoesNotMarkNonJdbcSourcesAsLeaves() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("sakila")).thenReturn(mock(JDBCDataSource.class));
      when(repo.getDataSource("cassandra")).thenReturn(mock(TabularDataSource.class));

      TreeNodeModel root = TreeNodeModel.builder()
         .label("Data Source")
         .addChildren(node("sakila"), node("cassandra"))
         .build();

      Map<String, Boolean> leafByLabel = new HashMap<>();

      for(TreeNodeModel child : service(repo).filterWizTree(root).children()) {
         leafByLabel.put(child.label(), child.leaf());
      }

      assertEquals(Boolean.FALSE, leafByLabel.get("sakila"), "a JDBC source stays expandable");
      assertEquals(Boolean.FALSE, leafByLabel.get("cassandra"),
                   "a non-JDBC source stays expandable too -- support is coming, don't hide it");
   }
}

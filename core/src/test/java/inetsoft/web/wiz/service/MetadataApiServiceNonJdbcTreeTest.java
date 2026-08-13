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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

/**
 * A non-relational data source has no catalog/schema/table metadata behind it, so expanding it in
 * the wiz tree returns an empty child list every time -- indistinguishable from a database that has
 * not finished loading. It is marked a leaf instead.
 *
 * Reproduced against a running deployment before this was written: a registered Cassandra source
 * appeared in the tree next to the JDBC ones with leaf=false, and expanding it returned HTTP 200
 * with children: []. Note that it did NOT throw, which is what the design doc had assumed.
 */
@Tag("core")
class MetadataApiServiceNonJdbcTreeTest {
   private MetadataApiService service(XRepository repo) {
      return new MetadataApiService(repo, mock(DataSourceService.class),
                                    mock(AssetRepository.class), mock(AssetTreeService.class),
                                    new ObjectMapper());
   }

   private AssetEntry dataSourceEntry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_SOURCE, name, null);
   }

   @Test
   void tabularSourceIsNonJdbc() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("cassandra-one")).thenReturn(mock(TabularDataSource.class));

      assertTrue(service(repo).isNonJdbcDataSource(dataSourceEntry("cassandra-one")));
   }

   @Test
   void jdbcSourceIsNotFlagged() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("sakila")).thenReturn(mock(JDBCDataSource.class));

      assertFalse(service(repo).isNonJdbcDataSource(dataSourceEntry("sakila")));
   }

   /*
    * The three ways this must answer "no".
    *
    * A lookup that fails or comes back empty is not evidence that a source is non-relational.
    * Answering yes there would collapse a real database to a leaf and put every table in it out of
    * reach -- a far worse outcome than leaving one node pointlessly expandable.
    */
   @Test
   void unresolvableSourceIsNotFlagged() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("gone")).thenReturn(null);

      assertFalse(service(repo).isNonJdbcDataSource(dataSourceEntry("gone")));
   }

   @Test
   void lookupFailureIsNotFlagged() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("broken")).thenThrow(new RuntimeException("repository down"));

      assertFalse(service(repo).isNonJdbcDataSource(dataSourceEntry("broken")));
   }

   @Test
   void nonDataSourceEntryIsNotFlagged() throws Exception {
      XRepository repo = mock(XRepository.class);
      AssetEntry folder =
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "somewhere", null);

      assertFalse(service(repo).isNonJdbcDataSource(folder));
   }

   @Test
   void nullEntryIsNotFlagged() {
      assertFalse(service(mock(XRepository.class)).isNonJdbcDataSource(null));
   }

   private TreeNodeModel node(String name) {
      return TreeNodeModel.builder().label(name).data(dataSourceEntry(name)).leaf(false).build();
   }

   /*
    * Drives the real filter, which the per-predicate cases above cannot: they call the helper
    * directly, so deleting the production change leaves every one of them green. This one fails if
    * the filter stops marking a non-relational source as a leaf, or starts marking a JDBC one.
    *
    * XMLA is here because it is the other non-JDBC family in this tree and was previously untested.
    */
   @Test
   void filterMarksOnlyNonRelationalSourcesAsLeaves() throws Exception {
      XRepository repo = mock(XRepository.class);
      when(repo.getDataSource("sakila")).thenReturn(mock(JDBCDataSource.class));
      when(repo.getDataSource("cassandra")).thenReturn(mock(TabularDataSource.class));
      when(repo.getDataSource("cube")).thenReturn(mock(XMLADataSource.class));

      TreeNodeModel root = TreeNodeModel.builder()
         .label("Data Source")
         .addChildren(node("sakila"), node("cassandra"), node("cube"))
         .build();

      Map<String, Boolean> leafByLabel = new HashMap<>();

      for(TreeNodeModel child : service(repo).filterWizTree(root).children()) {
         leafByLabel.put(child.label(), child.leaf());
      }

      assertEquals(Boolean.FALSE, leafByLabel.get("sakila"), "a JDBC source stays expandable");
      assertEquals(Boolean.TRUE, leafByLabel.get("cassandra"), "a tabular source becomes a leaf");
      assertEquals(Boolean.TRUE, leafByLabel.get("cube"), "an XMLA source becomes a leaf");
   }
}

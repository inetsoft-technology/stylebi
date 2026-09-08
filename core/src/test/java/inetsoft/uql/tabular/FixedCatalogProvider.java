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
package inetsoft.uql.tabular;

import java.util.List;

/**
 * The minimal test double for {@link TabularCatalogProvider}'s two new default methods: a fixed
 * catalog, and NOTHING ELSE overridden. {@link TabularCatalogProvider} does not depend on
 * {@link TabularRuntime}, so unlike {@code FakeCatalogRuntime} this does not need to extend it —
 * exercising the interface's default methods needs only the interface itself.
 *
 * Deliberately overrides neither {@code listDatasets(TabularDataSource, TabularCatalogRequest)} nor
 * {@code listRelationships}: that is what makes a test built on this double a test of the DEFAULT
 * implementation (charter A1/A5), and, combined with never being used as a production
 * implementation, part of the constructive argument behind charter A9.
 */
class FixedCatalogProvider implements TabularCatalogProvider {
   FixedCatalogProvider(List<TabularDatasetRef> datasets) {
      this(datasets, List.of());
   }

   FixedCatalogProvider(List<TabularDatasetRef> datasets, List<TabularRelationship> relationships) {
      this.catalog = new TabularCatalog(datasets, relationships);
   }

   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) {
      return catalog;
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId) {
      throw new UnsupportedOperationException("not used by these tests");
   }

   private final TabularCatalog catalog;
}

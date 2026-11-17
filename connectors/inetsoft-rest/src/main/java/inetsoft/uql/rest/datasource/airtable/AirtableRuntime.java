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
package inetsoft.uql.rest.datasource.airtable;

import inetsoft.uql.rest.json.EndpointJsonRuntime;
import inetsoft.uql.tabular.*;

/**
 * Airtable's own runtime: the shared {@link EndpointJsonRuntime} plus the table catalog, which
 * Airtable publishes through its Meta API. Both methods are pure delegation into
 * {@link AirtableMetaClient} (I/O) and {@link AirtableCatalog} (pure Meta API JSON to neutral
 * {@code Tabular*} records); see those classes for the design.
 */
public class AirtableRuntime extends EndpointJsonRuntime implements TabularCatalogProvider {
   @Override
   public TabularCatalog listDatasets(TabularDataSource<?> dataSource) throws Exception {
      return AirtableCatalog.listDatasets(new AirtableMetaClient(airtable(dataSource)));
   }

   @Override
   public TabularDatasetSchema describeDataset(TabularDataSource<?> dataSource, String datasetId)
      throws Exception
   {
      return AirtableCatalog.describeDataset(new AirtableMetaClient(airtable(dataSource)),
                                             datasetId);
   }

   private static AirtableDataSource airtable(TabularDataSource<?> dataSource) {
      if(!(dataSource instanceof AirtableDataSource)) {
         throw new IllegalArgumentException(
            "AirtableRuntime can only describe an Airtable data source, got " +
            (dataSource == null ? "null" : dataSource.getClass().getName()));
      }

      return (AirtableDataSource) dataSource;
   }
}

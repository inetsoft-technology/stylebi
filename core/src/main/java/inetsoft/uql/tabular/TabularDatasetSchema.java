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
import java.util.Map;

/**
 * The columns of one dataset.
 *
 * @param datasetId echoes the id that was asked for, so a result is self-identifying.
 * @param columns   non-empty and in source order. An implementation that finds no columns throws
 *                  rather than returning an empty list — see TabularCatalogProvider.
 * @param keyColumns names, from {@code columns}, that the source declares as this dataset's key;
 *                  empty when the source declares none. Never null.
 * @param params    binding parameters for this dataset, keyed by the connector's own query bean
 *                  property names (the same names {@code TabularUtil.getPropertyMap} derives from
 *                  its getters/setters, not a {@code @Property(label=...)} display string) so a
 *                  caller with this dataset's parameter contract in hand can match them up without
 *                  any translation table. Empty when the connector's target identity needs no such
 *                  parameters (e.g. the dataset id is itself the one property value a query needs)
 *                  or the connector has not implemented this yet. Never null — use the 3-arg
 *                  constructor below, which defaults to {@link Map#of()}, rather than passing null.
 */
public record TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                                   List<String> keyColumns, Map<String, String> params) {
   /**
    * Compatibility constructor for callers written before {@code params} existed — every existing
    * connector-test construction site and any connector that has not opted into target binding
    * parameters yet. Defaults to an empty map, which {@code TabularCatalogService} treats
    * identically to "the connector said nothing": the COMMON extension's {@code params} key is
    * omitted entirely, not written empty.
    */
   public TabularDatasetSchema(String datasetId, List<TabularColumn> columns,
                               List<String> keyColumns)
   {
      this(datasetId, columns, keyColumns, Map.of());
   }
}

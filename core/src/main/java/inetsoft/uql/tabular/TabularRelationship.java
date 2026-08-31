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
 * A relationship the SOURCE ITSELF declares between two of its datasets — not an inferred one.
 *
 * <p><b>Stated residual:</b> {@code TabularCatalogService} checks {@code fromColumns}/
 * {@code toColumns} for non-emptiness and equal size, but does NOT verify that an entry actually
 * names a real column of {@code fromDataset}/{@code toDataset}'s own schema — doing so would
 * require the {@code listDatasets} phase to call {@code describeDataset} for every referenced
 * dataset, which reverses the SPI's two-phase separation (the reason a connector like SharePoint
 * does not cache between phases). A connector emitting a relationship is responsible for this
 * invariant itself.
 *
 * @param name        stable identifier for this edge within the catalog; non-blank, and unique
 *                    within the catalog.
 * @param fromDataset a {@link TabularDatasetRef#id()} present in the same {@link TabularCatalog}.
 * @param toDataset   likewise.
 * @param fromColumns column names in {@code fromDataset}; non-empty, and the same size as
 *                    {@code toColumns}, positionally paired.
 * @param toColumns   column names in {@code toDataset}; non-empty.
 */
public record TabularRelationship(String name, String fromDataset, String toDataset,
                                  List<String> fromColumns, List<String> toColumns) {}

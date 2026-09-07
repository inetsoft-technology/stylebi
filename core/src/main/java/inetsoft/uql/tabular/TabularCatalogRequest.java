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

/**
 * One page of a {@link TabularCatalogProvider#listDatasets(TabularDataSource, TabularCatalogRequest)}
 * request.
 *
 * @param nameContains a substring of a {@link TabularDatasetRef#id()}, matched case-insensitively.
 *                      {@code null} or blank means no filtering -- every dataset matches. This is
 *                      deliberately the ONLY filter dimension this contract carries.
 * @param limit         the maximum number of datasets one page may hold. Must be greater than 0 --
 *                      enforced by this record's compact constructor.
 * @param cursor        the exact {@code nextCursor} a previous call returned, or {@code null} for
 *                      the first page. Opaque to every caller.
 */
public record TabularCatalogRequest(String nameContains, int limit, String cursor) {
   public TabularCatalogRequest {
      if(limit <= 0) {
         throw new IllegalArgumentException(
            "TabularCatalogRequest.limit must be > 0, got " + limit);
      }
   }
}

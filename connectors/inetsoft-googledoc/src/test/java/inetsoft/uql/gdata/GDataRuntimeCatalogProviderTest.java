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
package inetsoft.uql.gdata;

import inetsoft.uql.tabular.TabularCatalogProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A1: {@code TabularCatalogService.resolveProvider} must stop throwing
 * {@code UnsupportedDatasourceException} for a {@code "GoogleDocs"} data source, i.e.
 * {@code GDataRuntime} must implement {@link TabularCatalogProvider}.
 *
 * <p>Uses {@code Class.isAssignableFrom}, not {@code new GDataRuntime() instanceof ...}:
 * {@code new GDataRuntime()} would not run any constructor logic of consequence, but the
 * class-literal form additionally avoids ever needing
 * to INITIALIZE {@code GDataRuntime} (a class-literal reference does not trigger static
 * initialization), which matters here because {@code GDataRuntime}'s static initializer runs
 * {@code GoogleNetHttpTransport.newTrustedTransport()} -- a gate traded, not dropped: the same
 * "missing implements clause" defect is caught either way, with one fewer thing that can fail for
 * an unrelated reason.
 */
class GDataRuntimeCatalogProviderTest {
   @Test
   void implementsCatalogProvider() {
      assertTrue(TabularCatalogProvider.class.isAssignableFrom(GDataRuntime.class));
   }
}

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
package inetsoft.uql.odata;

import inetsoft.uql.tabular.TabularCatalog;
import inetsoft.uql.tabular.TabularDatasetSchema;

import java.util.Map;

/**
 * The parsed, neutral snapshot of one {@code $metadata} document — both SPI phases are served
 * from this one object, so one download serves the whole annotation run's SPI traffic (see
 * {@link ODataCatalogCache}). Holds no {@code org.w3c.dom} type, so nothing EDMX-shaped can escape
 * the cache by accident.
 */
record ODataCatalogSnapshot(TabularCatalog catalog, Map<String, TabularDatasetSchema> schemasByEntitySet) {}

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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.sree.security.OrganizationManager;
import org.w3c.dom.Node;

import java.util.concurrent.TimeUnit;

/**
 * Caches the parsed catalog of one OData data source's {@code $metadata} document so that an
 * annotation run's SPI traffic — one {@code listDatasets} plus N {@code describeDataset} calls —
 * costs at most one {@code $metadata} download per TTL window, not one per call.
 *
 * <p>THE CACHE FIELD BELOW MUST STAY {@code static}. {@code TabularUtil.createRuntime} (core)
 * builds a NEW {@link ODataRuntime} instance on every call — the same lifetime
 * {@code TabularUtil.createQuery} already has — so an instance field here could never be hit; that
 * is the exact reason {@code ODataQuery.getOutputColumns()}'s own instance-level cache is dead
 * code today. See {@code 12-spi-design.md} §4.2 and §6.4, and
 * {@code ODataCatalogCacheTest#twoRuntimeInstancesStillShareOneDownload}, which fails immediately
 * if this is ever made an instance field.</p>
 */
final class ODataCatalogCache {
   private ODataCatalogCache() {
   }

   static ODataCatalogSnapshot snapshot(ODataDataSource ds) throws Exception {
      Key key = new Key(currentOrgId(), ds.getFullName(), ds.getURL());

      // Caffeine's mapping-function loader gives single-flight per key and — critically — does
      // NOT cache a thrown exception: on failure nothing is stored, so the next call retries
      // rather than extending a transient outage for a full TTL.
      return CACHE.get(key, k -> load(ds));
   }

   private static ODataCatalogSnapshot load(ODataDataSource ds) {
      Node schemaNode = ODataRuntime.getSchemaNode(ds);

      if(schemaNode == null) {
         throw new RuntimeException(
            "Could not read $metadata for OData data source '" + ds.getName() + "'.");
      }

      return ODataCatalog.parse(schemaNode);
   }

   private static String currentOrgId() {
      // Confirmed (not assumed): WizServiceAuthenticationFilter sets ThreadContext's context
      // principal before the wiz API filter chain runs, so getCurrentOrgID() is meaningful here.
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   private record Key(String orgId, String dataSourceName, String url) {}

   // See the class javadoc: this MUST remain static.
   private static final Cache<Key, ODataCatalogSnapshot> CACHE = Caffeine.newBuilder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .maximumSize(16)
      .build();
}

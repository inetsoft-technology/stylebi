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
package inetsoft.uql.serverfile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.tabular.TabularCatalog;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Caches one ServerFile root's enumerated catalog so that an annotation run's SPI traffic -- the
 * scaled-annotation picker's threshold probe plus up to {@code MAX_SELECT_ALL_PAGES} pages of
 * {@code PAGE_SIZE = 50} (portal's {@code TargetPickerDialog.tsx}) -- costs at most one full
 * directory walk per TTL window, not one per page.
 *
 * <p>THE MEASURED COST THIS PAYS FOR (see the design doc's B.2 for the full derivation): a 5,000
 * file source holding 200 mid-size (~5 MB) workbooks costs roughly 1.0 s per {@link
 * ServerFileCatalog#listDatasets} call -- the tree walk itself (tens of ms) plus opening every
 * workbook once for its sheet names (Excel enumeration reads only the sheet list, not the cells --
 * see {@code ServerFileCatalog}'s own javadoc). The picker's up-to-101 enumerations of that source
 * would otherwise cost roughly 100 s of repeated server work for one dialog session; this cache
 * turns that into roughly 1 s.
 *
 * <p>THE ONE COST THIS DECISION ACCEPTS: a file dropped into (or removed from) a watched directory
 * can take up to {@code TTL_SECONDS} to appear in (or disappear from) the catalog. Nothing else in
 * the product caches a ServerFile listing today, so this is a genuinely new, if small and bounded,
 * staleness window -- recorded in {@code docs/tabular/stylebi-tabular-wiz-integration.md} §5.3 and
 * in the PR body, per the run's charter (assertion A14).
 *
 * <p>THE CACHE FIELD BELOW MUST STAY {@code static}. {@code TabularUtil.createRuntime} (core)
 * builds a NEW {@link ServerFileRuntime} instance on every call, so an instance field here could
 * never be hit -- see {@code ServerFileCatalogCacheTest#twoRuntimeInstancesShareOneEnumeration},
 * which drives two separate runtime instances specifically to catch that mistake.
 *
 * <p>Caffeine's mapping-function loader gives single-flight per key and, critically, does NOT
 * cache a thrown exception (nothing is stored on failure), so a failed enumeration is retried on
 * the very next call rather than freezing a transient outage -- a missing/unreadable root, say --
 * for a full TTL window. See {@code ServerFileCatalogCacheTest#aFailedEnumerationIsNotCached}.
 */
final class ServerFileCatalogCache {
   private ServerFileCatalogCache() {
   }

   static TabularCatalog catalog(ServerFileDataSource ds) throws Exception {
      // Deliberately NOT pre-validated here (no requireRoot call before the cache lookup): a
      // missing/unreadable root must be a failure the CACHE'S OWN loader sees and does not cache,
      // not one that bypasses the cache by throwing before it is ever consulted -- see
      // ServerFileCatalogCacheTest#aFailedEnumerationIsNotCached, which retries against the same
      // key once the root starts existing.
      File configuredRoot = ds.getFile();
      String rootKey = configuredRoot == null ? "" : configuredRoot.getAbsolutePath();
      Key key = new Key(currentOrgId(), ds.getFullName(), rootKey);

      // Caffeine's mapping function is a plain Function<K,V> -- checked exceptions from
      // listDatasets are boxed here and unboxed below so a caller sees the SAME exception
      // listDatasets itself would have thrown, uncached (see the class javadoc).
      try {
         return CACHE.get(key, k -> {
            try {
               return ServerFileCatalog.listDatasets(ds);
            }
            catch(Exception e) {
               throw new CatalogLoadException(e);
            }
         });
      }
      catch(CatalogLoadException e) {
         if(e.getCause() instanceof Exception cause) {
            throw cause;
         }

         throw e;
      }
   }

   private static final class CatalogLoadException extends RuntimeException {
      CatalogLoadException(Exception cause) {
         super(cause);
      }
   }

   private static String currentOrgId() {
      // Same reasoning as ODataCatalogCache: WizServiceAuthenticationFilter sets ThreadContext's
      // context principal before the wiz API filter chain runs, so getCurrentOrgID() is meaningful
      // here.
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   private record Key(String orgId, String dataSourceName, String canonicalRoot) {}

   /**
    * 60s, not OData's 10 minutes: a {@code $metadata} document changes when a service is
    * redeployed; a shared directory changes when a user drops a file in it. 60s covers one picker
    * session (the ~101 calls above are seconds apart) while keeping "I added a file and it is not
    * listed" bounded to a minute. See the design doc's D.3 for the full reasoning.
    */
   static final long TTL_SECONDS = 60;

   // See the class javadoc: this MUST remain static.
   private static final Cache<Key, TabularCatalog> CACHE = Caffeine.newBuilder()
      .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
      .maximumSize(16)
      .build();
}

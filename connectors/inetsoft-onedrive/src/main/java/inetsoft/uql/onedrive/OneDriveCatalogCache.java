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
package inetsoft.uql.onedrive;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.tabular.TabularCatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Caches one OneDrive data source's enumerated catalog so that an annotation run's SPI traffic --
 * the scaled-annotation picker's threshold probe plus its paging -- costs at most one full
 * {@code browseChildren} walk per TTL window, not one per page.
 *
 * <p>THIS MATTERS MORE HERE THAN FOR ServerFileCatalogCache (the predecessor round's equivalent).
 * There, an uncached re-enumeration cost a local disk walk. Here, the default paged
 * {@code TabularCatalogProvider} implementation RE-ENUMERATES THE WHOLE SOURCE PER PAGE (see
 * {@code OneDriveRuntime}'s own javadoc on why no cursor-based override exists), and one
 * {@code listDatasets} call against OneDrive is up to {@code OneDriveRuntime.MAX_GRAPH_REQUESTS}
 * (200) live Microsoft Graph HTTP requests against a throttled API with no client-side backoff.
 * Without this cache, the picker's own paging would multiply that by the number of pages in one
 * dialog session.
 *
 * <p>THE ONE COST THIS DECISION ACCEPTS: a file added to (or removed from) the drive can take up to
 * {@code TTL_SECONDS} to appear in (or disappear from) the catalog -- recorded in
 * {@code docs/tabular/stylebi-tabular-wiz-integration.md} §5.3 and in the PR body, per the round's
 * charter (assertion B11).
 *
 * <p>THE CACHE FIELD BELOW MUST STAY {@code static} -- {@code TabularUtil.createRuntime} (core)
 * builds a NEW {@link OneDriveRuntime} instance on every call, so an instance field here could never
 * be hit. See {@code OneDriveCatalogCacheTest#twoRuntimeInstancesShareOneEnumeration}, which drives
 * two separate runtime instances specifically to catch that mistake.
 *
 * <p>Caffeine's mapping-function loader gives single-flight per key and does NOT cache a thrown
 * exception, so a failed enumeration (an expired token, a Graph outage) is retried on the very next
 * call rather than freezing a transient failure for a whole TTL window. See
 * {@code OneDriveCatalogCacheTest#aFailedEnumerationIsNotCached}.
 *
 * <p>THE CACHE KEY hashes the access token rather than storing it, and rather than reusing
 * {@code ServerFileCatalogCache}'s third component ({@code canonicalRoot} -- {@code
 * OneDriveDataSource} has no root/folder property at all, see the design doc's §6). The token is
 * what actually selects WHICH drive is being enumerated (there is no configured root to fingerprint
 * instead), so a re-authentication to a DIFFERENT account changes the key and MISSES rather than
 * silently serving the previous account's listing -- the cross-account variant of the predecessor
 * round's F9 (a cache key that diverges from the security-relevant identity), closed here by
 * construction. Hashed, never stored raw, so the key itself is not something a heap dump or a log
 * line could leak a live credential from. A token refresh is roughly hourly against a 60s TTL, so
 * refresh-induced cache misses are negligible.
 */
final class OneDriveCatalogCache {
   private OneDriveCatalogCache() {
   }

   static TabularCatalog catalog(OneDriveDataSource ds) throws Exception {
      return catalog(ds, () -> OneDriveCatalog.listDatasets(ds));
   }

   /**
    * Test seam: {@code loader} replaces the real, Graph-backed {@code OneDriveCatalog.listDatasets}
    * call, so a test can prove the CACHE's own behavior (single-flight per key, failure not cached,
    * key changing with the token fingerprint) without a live tenant. Production always goes through
    * the one-arg overload above; nothing else calls this overload.
    */
   static TabularCatalog catalog(OneDriveDataSource ds, java.util.concurrent.Callable<TabularCatalog> loader)
      throws Exception
   {
      Key key = new Key(currentOrgId(), ds.getFullName(), tokenFingerprint(ds));

      // Caffeine's mapping function is a plain Function<K,V> -- checked exceptions from the loader
      // are boxed here and unboxed below so a caller sees the SAME exception the loader itself
      // would have thrown, uncached (see the class javadoc).
      try {
         return CACHE.get(key, k -> {
            try {
               return loader.call();
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
      // Same reasoning as ServerFileCatalogCache/ODataCatalogCache: WizServiceAuthenticationFilter
      // sets ThreadContext's context principal before the wiz API filter chain runs, so
      // getCurrentOrgID() is meaningful here.
      return OrganizationManager.getInstance().getCurrentOrgID();
   }

   /**
    * SHA-256 hex of the current access token -- NEVER the token itself, which must not sit in a map
    * key that could reach a heap dump or a log. A missing/blank token still produces a stable,
    * distinguishable key rather than throwing here: a bad/expired token is a failure {@code
    * OneDriveCatalog.listDatasets} itself will surface (and which the class javadoc's "not cached on
    * failure" behavior already handles), not one this cache should pre-empt.
    */
   private static String tokenFingerprint(OneDriveDataSource ds) {
      String token = ds.getAccessToken();

      if(token == null) {
         return "";
      }

      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
      }
      catch(NoSuchAlgorithmException e) {
         // SHA-256 is a JDK-guaranteed algorithm (every conforming implementation provides it) --
         // this branch exists only to satisfy the checked exception, never to be reached.
         throw new IllegalStateException(e);
      }
   }

   private record Key(String orgId, String dataSourceName, String tokenFingerprint) {}

   /**
    * 60s, matching {@code ServerFileCatalogCache}'s own TTL for the same reason: it covers one
    * picker dialog session (a handful of calls seconds apart) while keeping "I added a file and it
    * is not listed" bounded to a minute.
    */
   static final long TTL_SECONDS = 60;

   // See the class javadoc: this MUST remain static.
   private static final Cache<Key, TabularCatalog> CACHE = Caffeine.newBuilder()
      .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
      .maximumSize(16)
      .build();
}

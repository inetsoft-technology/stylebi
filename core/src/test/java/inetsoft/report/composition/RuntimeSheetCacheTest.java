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
package inetsoft.report.composition;

import org.apache.ignite.cache.affinity.AffinityKey;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Bug #76674: the funnel guard on {@link RuntimeSheetCache#getAffinityKey(String)}.
 *
 * <p>A null viewsheet runtime id feeding cluster-affinity routing has now been fixed one call
 * site at a time four times (#4971/PCB-006, #5186/#76615, #5252/#76666). The id comes from
 * {@code RuntimeViewsheetRef}, a STOMP-message-scoped bean populated only from a native header on
 * a live browser WebSocket session; a caller reached by a plain Java call or a plain HTTP request
 * gets null. Every affinity-routed path in the codebase -- all 240+ {@code @ClusterProxyKey}
 * methods, through both {@code affinityCall} and {@code affinityCallAsync} -- funnels through
 * this one method, so guarding here is what covers the pattern rather than one more site.
 *
 * <p>Before the guard, the null travelled one line further into Ignite's {@code AffinityKey}
 * constructor and surfaced as {@code NullPointerException("Ouch! Argument cannot be null: key")},
 * which is uninterpretable without decompiling ignite-core (Ignite ships no sources jar). These
 * tests pin the named message and, just as importantly, pin that the guard did not disturb key
 * computation for the ids that are actually valid.
 *
 * <p>{@code getAffinityKey} reads no instance state -- only its argument and the static
 * {@code getOriginalId} -- so a constructor-free mock exercising the real method is sufficient
 * here and avoids standing up a Cluster and an Ignite cache.
 */
@Tag("core")
class RuntimeSheetCacheTest {
   /**
    * The guard itself. Asserting on the message matters as much as the throw: the whole cost of
    * this defect class has been that the failure did not say what was wrong, so a test that
    * accepts any NullPointerException would also accept a regression back to Ignite's "Ouch!".
    */
   @Test
   void aNullRuntimeIdIsRejectedByNameRatherThanReachingIgnite() {
      RuntimeSheetCache cache = mock(RuntimeSheetCache.class, CALLS_REAL_METHODS);

      NullPointerException thrown =
         assertThrows(NullPointerException.class, () -> cache.getAffinityKey(null));

      assertNotNull(thrown.getMessage(), "the guard must explain itself, not throw bare");
      assertTrue(thrown.getMessage().contains("Runtime id is null"),
                 "the message has to name the null runtime id as the cause, but was: " +
                 thrown.getMessage());
      assertFalse(thrown.getMessage().contains("Ouch!"),
                  "reaching Ignite's own message means the guard was bypassed: " +
                  thrown.getMessage());
   }

   /**
    * The no-regression half. The guard is a precondition check and must leave the returned key
    * identical for every id that was already valid -- both parts of it, since the affinity part
    * is what actually colocates the call.
    */
   @Test
   void anOrdinaryRuntimeIdStillProducesTheSameAffinityKey() {
      RuntimeSheetCache cache = mock(RuntimeSheetCache.class, CALLS_REAL_METHODS);

      AffinityKey<String> key = cache.getAffinityKey("rt-viewsheet-1");

      assertEquals("rt-viewsheet-1", key.key());
      assertEquals("rt-viewsheet-1", key.affinityKey(),
                   "a non-temp id is its own affinity key");
   }

   /**
    * The case the guard could plausibly have broken: a preview/temp id colocates with the sheet
    * it was derived from, which is the whole reason getAffinityKey is a two-argument construction
    * rather than a plain key. Pinning it here keeps a future edit to this method from quietly
    * collapsing temp ids onto themselves and scattering a preview away from its parent sheet.
    */
   @Test
   void aTempRuntimeIdStillColocatesWithTheSheetItCameFrom() {
      RuntimeSheetCache cache = mock(RuntimeSheetCache.class, CALLS_REAL_METHODS);

      AffinityKey<String> key = cache.getAffinityKey("rt-viewsheet-1-temp-7");

      assertEquals("rt-viewsheet-1-temp-7", key.key(), "the key stays the full temp id");
      assertEquals("rt-viewsheet-1", key.affinityKey(),
                   "but it must route to the original sheet's node");
   }
}

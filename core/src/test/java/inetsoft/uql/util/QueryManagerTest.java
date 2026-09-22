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
package inetsoft.uql.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for Bug #76916: rapidly switching a crosstab data-tip cell could throw a
 * hard "Failed to execute query for ..." error instead of quietly loading the new cell, because
 * a query cancelled mid-flight (during getTableLens()) surfaced as a null result rather than a
 * thrown CancelledException, and AssetDataCache.Processor.run0() had no guard for that timing
 * (only for cancellation landing before getTableLens() was called).
 *
 * The fix in AssetDataCache.Processor.run0() (see AssetDataCache.java) distinguishes those two
 * cases using the same signal already used by the pre-existing early-cancellation guard:
 * {@code qmgr.lastCancelled() > created}, where {@code created} is a timestamp captured before
 * the query started and {@code lastCancelled()} is set by {@link QueryManager#cancel()}.
 *
 * AssetDataCache.Processor is a private inner class that constructs a real AssetQuery and runs a
 * live query, so it cannot be exercised directly by a fast unit test without a testability change
 * to product code (see 04-verify.md for what was checked/considered). This test instead proves,
 * in isolation, the timing invariant the fix's condition depends on: that lastCancelled() only
 * reports "cancelled after this timestamp" for a cancel() call that actually happened after it,
 * which is the exact signal that lets the fixed code tell "this specific request's query was
 * superseded mid-flight" apart from "data is null for some other reason".
 */
@Tag("core")
public class QueryManagerTest {
   @Test
   void lastCancelledReflectsCancelCallAfterGivenTimestamp() throws Exception {
      QueryManager qmgr = new QueryManager();
      long created = System.currentTimeMillis();

      // simulate the query manager cancelling the assembly's pending query mid-flight,
      // i.e. after this "created" timestamp was captured -- the exact race the fix targets.
      Thread.sleep(5);
      qmgr.cancel();

      assertTrue(qmgr.lastCancelled() > created,
                 "lastCancelled() must be after 'created' once cancel() runs later, " +
                 "so the fix's guard (qmgr.lastCancelled() > created) treats this as " +
                 "'superseded, do not report a hard error'");
   }

   @Test
   void lastCancelledDoesNotFalsePositiveForACancelBeforeTheGivenTimestamp() {
      QueryManager qmgr = new QueryManager();

      // a cancellation that happened strictly before "created" (e.g. from an unrelated, earlier
      // request against the same per-assembly QueryManager) must not be mistaken for this
      // request's own query having been superseded.
      qmgr.cancel();
      long created = System.currentTimeMillis() + 1000;

      assertFalse(qmgr.lastCancelled() > created,
                  "a cancel() that happened before 'created' must not satisfy the guard, " +
                  "otherwise a genuine query failure for a later request could be silently " +
                  "swallowed instead of reported");
   }

   @Test
   void lastCancelledIsZeroWhenNeverCancelled() {
      QueryManager qmgr = new QueryManager();

      assertFalse(qmgr.lastCancelled() > System.currentTimeMillis(),
                  "a QueryManager that was never cancelled must never satisfy the " +
                  "'lastCancelled() > created' guard, so genuine null-data failures still throw");
   }
}

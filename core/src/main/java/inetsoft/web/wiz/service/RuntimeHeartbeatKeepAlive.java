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
package inetsoft.web.wiz.service;

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.util.TimedQueue;

/**
 * Bug #79074: a wiz pairing call that blocks for a long time on a slow or multi-round-trip query
 * (e.g. a paginated REST connector doing many sequential fetches) against a pairing-held
 * {@link RuntimeSheet} does not otherwise touch that runtime's heartbeat while the call is in
 * flight -- the only {@code rs.access(true)} call happens once, at pairing-access grant time
 * ({@code SheetRuntimeAccess.getWorksheetForPairing}/{@code getViewsheetForPairing}), before any
 * such call is ever reached.
 *
 * <p>{@code WorksheetEngine}'s recycle sweep ({@code RecycleTask}, a fixed 3-minute interval)
 * disposes a runtime sheet once {@link RuntimeSheet#isTimeout()} is true, which includes a stale
 * heartbeat ({@link RuntimeSheet#DEFAULT_HEARTBEAT_TIMEOUT}, 3 minutes -- also a hard floor no
 * deployment can lower). Its own "currently executing, don't evict" guard ({@code emap}) is
 * populated only by {@code ViewsheetEngine.addExecution}/{@code removeExecution} -- declared on
 * {@code ViewsheetService}, not {@code WorksheetService} -- so a worksheet runtime has no such
 * protection at all. A query that runs past this window gets its runtime disposed mid-query, and
 * the next call against the same runtime id fails with "runtime not found or expired," silently
 * losing whatever else was unsaved on that sheet.</p>
 *
 * <p>This keeps the runtime's heartbeat fresh (not its idle/accessed clock -- {@code access(false)}
 * -- so ordinary idle-timeout accounting is unaffected) on a periodic background task for the
 * duration of a caller-identified blocking section, via {@link #start} / {@link #stop}. Each call
 * site is responsible for calling {@link #stop} once the work it guards has genuinely finished --
 * including work that keeps running on a different thread than the one that called
 * {@link #start} (see {@code WorksheetAgentController.refreshData}'s single-assembly branch, which
 * starts the heartbeat before submitting work to {@code RenderWaitSupport.awaitOrRetry} but stops
 * it from inside that work's own callable, so the heartbeat survives a client-visible timeout as
 * long as the background execution it was protecting is still genuinely running).</p>
 *
 * <p>Deliberately scoped to the specific blocking call a caller identifies, rather than extending
 * {@code WorksheetEngine}'s shared {@code emap} mechanism to cover worksheet runtimes generically:
 * as of this writing, exactly three call sites in {@code inetsoft.web.wiz} reach a long-lived,
 * pairing-held runtime's own {@code AssetQuerySandbox.getTableLens} this way (this class's own
 * two callers plus {@code WorksheetPreviewService.preview0}) -- every other {@code getTableLens}
 * call site in that package (e.g. {@code RawDataService}, {@code WorksheetTableService.probeTable}/
 * {@code probeExecutable}) deliberately builds a fresh, disposable {@code AssetQuerySandbox}
 * rather than using the runtime's own, specifically to avoid a stale-cache correctness problem
 * unrelated to this one -- and is therefore not registered in {@code WorksheetEngine.amap} and not
 * exposed to this defect at all. A product-wide {@code WorksheetService} interface change (moving
 * {@code addExecution}/{@code removeExecution} up from {@code ViewsheetService}, and changing
 * {@code isValidExecutingObject}'s single existing override to also recognize worksheet runtimes)
 * would be a materially larger, harder-to-verify-without-a-live-instance change for a gap that is,
 * on inspection, closed and bounded rather than open-ended.</p>
 */
public final class RuntimeHeartbeatKeepAlive {
   private RuntimeHeartbeatKeepAlive() {
   }

   /**
    * Starts refreshing {@code rs}'s heartbeat on a periodic background task. The returned handle
    * must be passed to {@link #stop} exactly once the work it is protecting has genuinely
    * finished (on whichever thread that turns out to be) -- otherwise it leaks, refreshing the
    * heartbeat forever.
    */
   public static TimedQueue.TimedRunnable start(RuntimeSheet rs) {
      return TimedQueue.add(new TimedQueue.TimedRunnable(INTERVAL_MS) {
         @Override
         public boolean isRecurring() {
            return true;
         }

         @Override
         public void run() {
            rs.access(false);
         }
      });
   }

   /**
    * Stops a heartbeat started by {@link #start}. Safe to call more than once for the same handle
    * (e.g. once from the work's own completion path and again, defensively, from an outer catch
    * that cannot tell whether the work ran) -- {@link TimedQueue#remove} is a no-op for an entry
    * that is no longer queued.
    */
   public static void stop(TimedQueue.TimedRunnable heartbeat) {
      TimedQueue.remove(heartbeat);
   }

   /**
    * A third of {@link RuntimeSheet#DEFAULT_HEARTBEAT_TIMEOUT} -- a 3x safety margin against
    * scheduling slop in the background timer this interval drives.
    *
    * <p>Public and non-final solely so a unit test (in this class's own package, or a caller's,
    * e.g. {@code inetsoft.web.wiz.worksheet}) can shrink it, to observe the periodic refresh
    * firing without waiting out a production-sized (minutes-long) interval; production code
    * never reassigns this.</p>
    */
   public static long INTERVAL_MS = RuntimeSheet.DEFAULT_HEARTBEAT_TIMEOUT / 3;
}

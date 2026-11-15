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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.service.RenderWaitSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bug #81364: lets a retry of the whole-viewsheet image reach the export that
 * {@link RenderWaitSupport#awaitOrRetry} timed out on, instead of starting a new one.
 *
 * <p>Every whole-viewsheet export re-runs the root onLoad ({@code prepareForExport}), and the
 * timed-out export's PNG used to be dropped, so a dashboard whose export costs more than the wait
 * budget could never produce an image however often it was retried. This keeps the export's
 * result per (runtime viewsheet, principal):</p>
 * <ul>
 *   <li>a call while an export of that key is still running joins it and waits on it within its own
 *       budget (so it no longer hits "export already in progress" either);</li>
 *   <li>a call after it finished takes its PNG, once; the entry is then gone;</li>
 *   <li>a finished PNG is served only if the viewsheet state digest (see {@link #fingerprint}) was
 *       the same when the export's onLoad scripts had finished (the baseline, taken through
 *       {@code ViewsheetSandbox.setExportPreparedListener}), when the export finished, and when
 *       it is collected, and for at most {@link #ttlMs}; otherwise it is discarded and a fresh
 *       export starts. onLoad's own writes (a clock stamp, a counter) are in the baseline, so
 *       they do not defeat the reuse; a change by another caller after onLoad and during the
 *       render makes the collecting retry start over.</li>
 *   <li>if a digest cannot be computed, freshness cannot be proved: the PNG is then served only
 *       within {@link #unverifiedTtlMs} of finishing, with a warning, instead of being discarded
 *       into another long export (which would be the original non-convergence).</li>
 * </ul>
 *
 * <p>The PNG does not depend on the requested width/height (scaling happens afterwards), so they
 * are not part of the key. What stays undetectable: a pure joiner (a call that waits on an export
 * that is still running and returns when it finishes) gets that export's PNG unchecked, so a
 * change made while it ran can show up as the old state, exactly as for any single export that
 * races a change; a change made before onLoad finished, which is inside the baseline; and state
 * that is neither in the viewsheet XML, the variable table nor the
 * reset/touch stamps is bounded only by the TTL.</p>
 */
final class InFlightViewsheetExport {
   private InFlightViewsheetExport() {
   }

   /**
    * @return the PNG; throws {@link RenderNotReadyException} if it did not finish within
    *         {@code timeoutMs}. The export keeps running and a retry joins it.
    */
   static byte[] await(RuntimeViewsheet rvs, Principal principal, Callable<byte[]> export,
                       long timeoutMs, int retryAfterSeconds)
      throws Exception
   {
      String key = rvs.getID() + "|" + (principal == null ? "" : principal.getName());
      long deadline = System.currentTimeMillis() + timeoutMs;
      Entry joined = take(key, rvs);

      if(joined != null) {
         return join(key, joined, deadline, retryAfterSeconds);
      }

      Entry mine = new Entry();
      Entry raced = REGISTRY.putIfAbsent(key, mine);

      if(raced != null) {
         return join(key, raced, deadline, retryAfterSeconds);
      }

      Callable<byte[]> tracked = () -> {
         ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

         try {
            if(box != null) {
               box.setExportPreparedListener(() -> mine.baseDigest = DIGEST.apply(rvs));
            }

            byte[] bytes = export.call();
            mine.finishDigest = DIGEST.apply(rvs);
            mine.finishedAt = System.currentTimeMillis();
            mine.result.complete(bytes);
            sweep();
            return bytes;
         }
         catch(Throwable t) {
            REGISTRY.remove(key, mine);
            mine.result.completeExceptionally(t);
            throw t;
         }
         finally {
            if(box != null) {
               box.setExportPreparedListener(null);
            }
         }
      };

      try {
         byte[] bytes = RenderWaitSupport.awaitOrRetry(
            tracked, Math.max(1, deadline - System.currentTimeMillis()), retryAfterSeconds);
         REGISTRY.remove(key, mine);
         return bytes;
      }
      catch(RenderNotReadyException e) {
         throw e;
      }
      catch(Throwable t) {
         // failed before it ran (submit failed): nothing will ever complete the entry
         if(!mine.result.isDone()) {
            REGISTRY.remove(key, mine);
            mine.result.completeExceptionally(t);
         }

         throw t;
      }
   }

   private static void sweep() {
      long now = System.currentTimeMillis();
      REGISTRY.entrySet().removeIf(e -> e.getValue().expired(now));
   }

   /** The entry to join, or null if there is none usable (a stale or expired one is dropped). */
   private static Entry take(String key, RuntimeViewsheet rvs) {
      sweep();
      Entry entry = REGISTRY.get(key);

      if(entry == null) {
         return null;
      }

      if(entry.result.isDone() && !entry.result.isCompletedExceptionally()) {
         String current = DIGEST.apply(rvs);

         if(entry.baseDigest == null || entry.finishDigest == null || current == null) {
            long age = System.currentTimeMillis() - entry.finishedAt;

            if(age > unverifiedTtlMs) {
               REGISTRY.remove(key, entry);
               return null;
            }

            LOG.warn("Serving the finished viewsheet export of {} without a state check: the " +
                     "viewsheet state digest could not be computed", key);
         }
         else if(!entry.baseDigest.equals(entry.finishDigest) ||
                 !entry.finishDigest.equals(current))
         {
            LOG.debug("Discarding the finished viewsheet export of {}: state changed", key);
            REGISTRY.remove(key, entry);
            return null;
         }
      }

      return entry;
   }

   private static byte[] join(String key, Entry entry, long deadline, int retryAfterSeconds)
      throws Exception
   {
      try {
         byte[] bytes = entry.result.get(
            Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
         REGISTRY.remove(key, entry);
         return bytes;
      }
      catch(TimeoutException e) {
         throw new RenderNotReadyException(retryAfterSeconds);
      }
      catch(ExecutionException e) {
         if(e.getCause() instanceof Exception ex) {
            throw ex;
         }

         throw e;
      }
   }

   /**
    * A digest of the viewsheet's persisted state and its reset/touch stamps, null if it cannot be
    * computed (then a finished PNG is never reused).
    */
   static String fingerprint(RuntimeViewsheet rvs) {
      try {
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            return null;
         }

         StringWriter sw = new StringWriter();
         PrintWriter writer = new PrintWriter(sw);
         vs.writeXML(writer);
         writer.flush();
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         md.update(sw.toString().getBytes(StandardCharsets.UTF_8));
         md.update((rvs.getLastReset() + ":" + rvs.getTouchTimestamp())
                      .getBytes(StandardCharsets.UTF_8));
         // viewsheet parameters live in the sandbox variable table, not in the viewsheet XML
         // (set_parameters does not necessarily move the reset/touch stamps)
         ViewsheetSandbox box = rvs.getViewsheetSandbox().orElse(null);

         if(box != null && box.getVariableTable() != null) {
            StringWriter vsw = new StringWriter();
            PrintWriter vw = new PrintWriter(vsw);
            box.getVariableTable().printKey(vw);
            vw.flush();
            md.update(vsw.toString().getBytes(StandardCharsets.UTF_8));
         }

         return HexFormat.of().formatHex(md.digest());
      }
      catch(Exception e) {
         LOG.debug("Cannot fingerprint the viewsheet", e);
         return null;
      }
   }

   /** Test hook: drops every retained export. */
   static void clear() {
      REGISTRY.clear();
      DIGEST = InFlightViewsheetExport::fingerprint;
      unverifiedTtlMs = 10_000L;
   }

   private static final class Entry {
      final CompletableFuture<byte[]> result = new CompletableFuture<>();
      volatile String baseDigest;
      volatile String finishDigest;
      volatile long finishedAt;

      boolean expired(long now) {
         return result.isDone() && now - finishedAt > ttlMs;
      }
   }

   /** How long a finished, unclaimed PNG is kept. */
   static volatile long ttlMs = 60_000L;
   /** How long a finished PNG whose state could not be digested is served. */
   static volatile long unverifiedTtlMs = 10_000L;
   /** Test hook. */
   static volatile java.util.function.Function<RuntimeViewsheet, String> DIGEST =
      InFlightViewsheetExport::fingerprint;
   private static final Map<String, Entry> REGISTRY = new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(InFlightViewsheetExport.class);
}

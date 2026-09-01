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

import inetsoft.util.ThreadContext;

import java.security.Principal;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds a call that may block for a long time on a first-time, uncached query execution
 * (rendering a viewsheet whose table data hasn't run yet in this runtime, or warming that data
 * ahead of an edit that reads it) instead of letting the request thread hang until it finishes.
 *
 * <p>The work is run on a dedicated executor and given {@code timeoutMs} to finish. If it does
 * not, {@link #awaitOrRetry} throws {@link RenderNotReadyException} — the same "not ready yet,
 * retry after N seconds" contract {@code ScriptImageService.getAssemblyImage} already uses for
 * a chart graph that hasn't finished computing. The work itself is <em>not</em> cancelled on
 * timeout: it keeps running in the background, so whatever it computes (StyleBI's
 * {@code ViewsheetSandbox} caches a table/chart's result once computed) is warm by the time the
 * caller retries, rather than restarting from nothing.
 */
public final class RenderWaitSupport {
   private RenderWaitSupport() {
   }

   public static <T> T awaitOrRetry(Callable<T> work, long timeoutMs, int retryAfterSeconds)
      throws Exception
   {
      // work runs on a fresh virtual thread (see EXECUTOR below), not the calling thread, so the
      // caller's ThreadContext principal/locale must be captured here and re-installed inside the
      // callable -- otherwise any permission/entitlement check work performs sees no principal at
      // all, regardless of who the real caller is.
      Principal principal = ThreadContext.getContextPrincipal();
      Locale locale = ThreadContext.getLocale();
      Callable<T> contextPropagatingWork = () -> {
         ThreadContext.setContextPrincipal(principal);
         ThreadContext.setLocale(locale);

         try {
            return work.call();
         }
         finally {
            ThreadContext.setContextPrincipal(null);
            ThreadContext.setLocale(null);
         }
      };

      Future<T> future = EXECUTOR.submit(contextPropagatingWork);

      try {
         return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException e) {
         throw new RenderNotReadyException(retryAfterSeconds);
      }
      catch(ExecutionException e) {
         Throwable cause = e.getCause();

         if(cause instanceof Exception ex) {
            throw ex;
         }

         throw e;
      }
   }

   // Virtual threads: cheap enough that a timed-out-but-still-running attempt left behind by a
   // caller that gave up doesn't cost a pooled platform thread. Mirrors
   // WizVisualizationService.THUMBNAIL_EXECUTOR.
   private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
}

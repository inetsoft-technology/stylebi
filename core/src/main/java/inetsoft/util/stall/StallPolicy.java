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
package inetsoft.util.stall;

import inetsoft.sree.SreeEnv;
import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Locale;
import java.util.function.Function;

/**
 * The settings of the lock-stall watchdog (bug #76967). A wait that sees no progress for
 * {@code stall.watchdog.noProgressMillis} is a stall. In {@code fail} mode, the default
 * (Feature #77123), a stall throws a {@link LockStallException} once the watchdog confirms the
 * wait can never progress: it is the one victim the watchdog picks in a wait-for cycle, or it
 * waits for a cycle that its victim's failure did not release, or for a JVM deadlock. Until
 * then, and for good if it is only a long wait (e.g. for a lock owner in a slow query), it is
 * reported like an {@code alert}-mode stall and goes on. If the victim cannot reach its own
 * check within two wait slices, the next member of its cycle becomes the victim, one at a
 * time. {@code stall.watchdog.failOnTimeout=true} fails a {@code fail}-mode stall on the
 * timeout alone, as {@code fail} did before Feature #77123. In {@code alert} mode a stall only
 * dumps the threads and logs a warning, and {@code off} registers no waits at all.
 *
 * <p>A thread in a timed park, such as {@code tryLock(timeout)}, is never an edge of a
 * wait-for cycle, whatever its timeout, as the park ends by itself. So a cycle closed by a
 * loop like {@code while(!lock.tryLock(t))} is not detected: its waits are only reported and
 * go on, and health stays UP. That is no regression, as no park was an edge before
 * Feature #77123.
 *
 * <p>The time threshold alone is no proof of a lock cycle: a wait gets progress credit from a
 * blocker only if that blocker's own registered wait progresses or it is running, so a lock
 * owner that polls a slow data source with sleeps, or waits for another thread's query, looks
 * like a stall. That is why the default {@code fail} mode needs the confirmation.
 *
 * <p>Only a {@code fail}-mode wait that its failure did not release (its thread could not
 * unwind, or, with {@code failOnTimeout}, never reached its check) turns the deadlock health
 * check DOWN. {@code alert} never fails a query and a single {@code alert}-mode wait, or an
 * unconfirmed {@code fail}-mode one, never turns health DOWN by itself; a JVM deadlock is DOWN
 * in every mode, as before the watchdog, and so is a wait-for cycle of registered waits and
 * monitors that no member's failure releases (bug #77152, see {@link StallWatchdog}).
 *
 * <p>The properties are cached for 10 seconds, like {@code SreeEnv.Value}, so a change takes
 * effect without a restart. Before the server environment is initialized (e.g. in plain unit
 * tests) the defaults are used.
 *
 * <p>{@code stall.watchdog.maxDumps} (default 20) is how many {@code stall-dump-*.txt} files
 * are kept in {@code stall.watchdog.dumpDir}: writing a new dump deletes the oldest ones
 * beyond it.
 *
 * <p>SREE lowercases property names, so a JVM override of one of these must be written in
 * lowercase, e.g. {@code -Dstall.watchdog.noprogressmillis=2000},
 * {@code -Dstall.watchdog.maxdumps=50} or {@code -Dstall.watchdog.failontimeout=true}. A
 * value set in {@code sree.properties} is not affected and accepts any case.
 *
 * <p>Keep {@code noProgressMillis} well above 5 seconds. A lens worker queued on the
 * on-demand {@code ThreadPool} may wait up to 5 seconds for an idle pool thread that is
 * holding in its clean-up ({@code ThreadPool.cleanUp}); the worker has not started, so its
 * waiter sees no progress and a shorter limit can trip a false stall.
 */
public final class StallPolicy {
   /**
    * What to do when a wait stalls.
    */
   public enum Mode {
      /**
       * Fail the stalled wait with a {@link LockStallException}, after a thread dump, once
       * the watchdog confirms it can never progress, or on the timeout alone with
       * {@code stall.watchdog.failOnTimeout}. A failed wait that is not released turns health
       * DOWN. The default (Feature #77123).
       */
      FAIL,
      /**
       * Only dump the threads and log a warning, the wait goes on. Never fails a query and
       * never turns health DOWN by itself, however long the stall persists, unless the wait
       * is stuck in a wait-for cycle (see {@link StallWatchdog}).
       */
      ALERT,
      /** No watchdog, waits are not registered. */
      OFF
   }

   /**
    * Fixed settings, for tests; the server reads the properties, see {@link #get()}. There is
    * no shorter form: a test states {@code failOnTimeout}, so it never tests the opt-in rule
    * without saying so.
    *
    * @param maxDumps      how many {@code stall-dump-*.txt} files to keep in the dump
    *                      directory, the oldest are deleted when a new one is written.
    * @param failOnTimeout in {@code fail} mode, fail a stall on the timeout alone, instead of
    *                      only once the watchdog confirms the wait can never progress.
    */
   public StallPolicy(Mode mode, long noProgressMillis, long scanMillis, File dumpDir,
                      int maxDumps, boolean failOnTimeout)
   {
      this.mode = mode;
      this.failOnTimeout = failOnTimeout;
      this.noProgressMillis = noProgressMillis;
      this.scanMillis = scanMillis;
      this.dumpDir = dumpDir;
      this.maxDumps = maxDumps;
   }

   /**
    * Get the current settings.
    */
   public static StallPolicy get() {
      StallPolicy policy = override;

      if(policy != null) {
         return policy;
      }

      long now = System.currentTimeMillis();
      Cached cached = cache;

      if(cached == null || now - cached.ts() > REFRESH_MILLIS) {
         cached = new Cached(read(), now);
         cache = cached;
      }

      return cached.policy();
   }

   /**
    * Use fixed settings instead of the properties, for tests. {@code null} restores the
    * properties.
    */
   public static void setOverride(StallPolicy policy) {
      override = policy;
   }

   public Mode getMode() {
      return mode;
   }

   /**
    * Check if a {@code fail}-mode stall fails on the timeout alone
    * ({@code stall.watchdog.failOnTimeout}), rather than only once the watchdog confirms the
    * wait can never progress (the default).
    */
   public boolean isFailOnTimeout() {
      return failOnTimeout;
   }

   public long getNoProgressMillis() {
      return noProgressMillis;
   }

   public long getScanMillis() {
      return scanMillis;
   }

   public File getDumpDir() {
      return dumpDir;
   }

   /**
    * Get how many thread dump files to keep in the dump directory
    * ({@code stall.watchdog.maxDumps}): writing a new one deletes the oldest beyond it.
    */
   public int getMaxDumps() {
      return maxDumps;
   }

   @Override
   public String toString() {
      return "StallPolicy{mode=" + mode + ", failOnTimeout=" + failOnTimeout +
         ", noProgressMillis=" + noProgressMillis +
         ", scanMillis=" + scanMillis + ", dumpDir=" + dumpDir + ", maxDumps=" + maxDumps +
         '}';
   }

   static StallPolicy fromProperties(Function<String, String> properties, File defaultDumpDir) {
      Mode mode = parseMode(properties.apply(MODE_PROPERTY));
      long noProgress = parseMillis(properties.apply(NO_PROGRESS_PROPERTY),
                                    DEFAULT_NO_PROGRESS_MILLIS);
      long scan = parseMillis(properties.apply(SCAN_PROPERTY), DEFAULT_SCAN_MILLIS);
      String dir = properties.apply(DUMP_DIR_PROPERTY);
      File dumpDir = dir == null || dir.isBlank() ? defaultDumpDir : new File(dir.trim());
      int maxDumps = (int) Math.min(Integer.MAX_VALUE, parseMillis(
         properties.apply(MAX_DUMPS_PROPERTY), DEFAULT_MAX_DUMPS));
      boolean failOnTimeout = parseBoolean(properties.apply(FAIL_ON_TIMEOUT_PROPERTY));
      return new StallPolicy(mode, noProgress, scan, dumpDir, maxDumps, failOnTimeout);
   }

   static Mode parseMode(String value) {
      if(value != null) {
         switch(value.trim().toLowerCase(Locale.ROOT)) {
         case "fail":
            return Mode.FAIL;
         case "alert":
            return Mode.ALERT;
         case "off":
            return Mode.OFF;
         default:
            LOG.warn("Invalid {} value \"{}\", using fail", MODE_PROPERTY, value);
         }
      }

      return Mode.FAIL;
   }

   static boolean parseBoolean(String value) {
      if(value == null || value.isBlank()) {
         return false;
      }

      String trimmed = value.trim();

      if(!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
         LOG.warn("Invalid {} value \"{}\", using false", FAIL_ON_TIMEOUT_PROPERTY, value);
      }

      return "true".equalsIgnoreCase(trimmed);
   }

   static long parseMillis(String value, long def) {
      if(value == null || value.isBlank()) {
         return def;
      }

      try {
         long millis = Long.parseLong(value.trim());

         if(millis > 0) {
            return millis;
         }
      }
      catch(NumberFormatException ignore) {
         // fall through to the default
      }

      LOG.warn("Invalid lock stall watchdog value \"{}\", using {}", value, def);
      return def;
   }

   private static StallPolicy read() {
      try {
         if(SreeEnv.isInitialized()) {
            return fromProperties(SreeEnv::getProperty, getLogDir());
         }
      }
      catch(RuntimeException ex) {
         LOG.debug("Failed to read the lock stall watchdog properties", ex);
      }

      return DEFAULT;
   }

   private static File getLogDir() {
      try {
         File parent = new File(LogManager.getInstance().getBaseLogFile(false)).getParentFile();

         if(parent != null) {
            return parent;
         }
      }
      catch(RuntimeException ex) {
         LOG.debug("Failed to get the log directory for lock stall dumps", ex);
      }

      return TEMP_DIR;
   }

   private record Cached(StallPolicy policy, long ts) {
   }

   public static final String MODE_PROPERTY = "stall.watchdog.mode";
   public static final String NO_PROGRESS_PROPERTY = "stall.watchdog.noProgressMillis";
   public static final String SCAN_PROPERTY = "stall.watchdog.scanMillis";
   public static final String DUMP_DIR_PROPERTY = "stall.watchdog.dumpDir";
   public static final String MAX_DUMPS_PROPERTY = "stall.watchdog.maxDumps";
   public static final String FAIL_ON_TIMEOUT_PROPERTY = "stall.watchdog.failOnTimeout";
   public static final int DEFAULT_MAX_DUMPS = 20;
   public static final long DEFAULT_NO_PROGRESS_MILLIS = 300000L;
   public static final long DEFAULT_SCAN_MILLIS = 30000L;

   private static final Logger LOG = LoggerFactory.getLogger(StallPolicy.class);
   private static final long REFRESH_MILLIS = 10000L;
   private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"));
   private static final StallPolicy DEFAULT = new StallPolicy(
      Mode.FAIL, DEFAULT_NO_PROGRESS_MILLIS, DEFAULT_SCAN_MILLIS, TEMP_DIR, DEFAULT_MAX_DUMPS,
      false);
   private static volatile StallPolicy override;
   private static volatile Cached cache;

   private final Mode mode;
   private final boolean failOnTimeout;
   private final long noProgressMillis;
   private final long scanMillis;
   private final File dumpDir;
   private final int maxDumps;
}

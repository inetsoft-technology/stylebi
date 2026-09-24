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
 * {@code stall.watchdog.noProgressMillis} is a stall: in {@code fail} mode it throws a
 * {@link LockStallException}, in {@code alert} mode it only dumps the threads and logs a
 * warning, and {@code off} registers no waits at all.
 *
 * <p>The properties are cached for 10 seconds, like {@code SreeEnv.Value}, so a change takes
 * effect without a restart. Before the server environment is initialized (e.g. in plain unit
 * tests) the defaults are used.
 *
 * <p>SREE lowercases property names, so a JVM override of one of these must be written in
 * lowercase, e.g. {@code -Dstall.watchdog.noprogressmillis=2000}. A value set in
 * {@code sree.properties} is not affected and accepts any case.
 */
public final class StallPolicy {
   /**
    * What to do when a wait stalls.
    */
   public enum Mode {
      /** Fail the stalled wait with a {@link LockStallException}, after a thread dump. */
      FAIL,
      /** Only dump the threads and log a warning, the wait goes on. */
      ALERT,
      /** No watchdog, waits are not registered. */
      OFF
   }

   public StallPolicy(Mode mode, long noProgressMillis, long scanMillis, File dumpDir) {
      this.mode = mode;
      this.noProgressMillis = noProgressMillis;
      this.scanMillis = scanMillis;
      this.dumpDir = dumpDir;
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

   public long getNoProgressMillis() {
      return noProgressMillis;
   }

   public long getScanMillis() {
      return scanMillis;
   }

   public File getDumpDir() {
      return dumpDir;
   }

   @Override
   public String toString() {
      return "StallPolicy{mode=" + mode + ", noProgressMillis=" + noProgressMillis +
         ", scanMillis=" + scanMillis + ", dumpDir=" + dumpDir + '}';
   }

   static StallPolicy fromProperties(Function<String, String> properties, File defaultDumpDir) {
      Mode mode = parseMode(properties.apply(MODE_PROPERTY));
      long noProgress = parseMillis(properties.apply(NO_PROGRESS_PROPERTY),
                                    DEFAULT_NO_PROGRESS_MILLIS);
      long scan = parseMillis(properties.apply(SCAN_PROPERTY), DEFAULT_SCAN_MILLIS);
      String dir = properties.apply(DUMP_DIR_PROPERTY);
      File dumpDir = dir == null || dir.isBlank() ? defaultDumpDir : new File(dir.trim());
      return new StallPolicy(mode, noProgress, scan, dumpDir);
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
   public static final long DEFAULT_NO_PROGRESS_MILLIS = 300000L;
   public static final long DEFAULT_SCAN_MILLIS = 30000L;

   private static final Logger LOG = LoggerFactory.getLogger(StallPolicy.class);
   private static final long REFRESH_MILLIS = 10000L;
   private static final File TEMP_DIR = new File(System.getProperty("java.io.tmpdir"));
   private static final StallPolicy DEFAULT = new StallPolicy(
      Mode.FAIL, DEFAULT_NO_PROGRESS_MILLIS, DEFAULT_SCAN_MILLIS, TEMP_DIR);
   private static volatile StallPolicy override;
   private static volatile Cached cache;

   private final Mode mode;
   private final long noProgressMillis;
   private final long scanMillis;
   private final File dumpDir;
}

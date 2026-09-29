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
package inetsoft.util.script.graal.pool;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration of the worksheet script context pool (bug #76960). Whether the pool is on is
 * read once per sandbox ({@link #isEnabled()}); the tuning values once per env ({@link #read()}).
 * The pool is on by default (Feature #77123); {@code script.ws.contextPool=false} turns it off.
 *
 * @param idleMillis          an idle pooled context is closed after this long.
 * @param cleanThreshold      a context whose non-configurable leftover globals exceed this is
 *                            closed instead of reused.
 * @param warnSlotsPerSandbox warn (never cap) when one sandbox has more contexts than this.
 * @param warnSlotsPerNode    warn (never cap) when the node has more pooled contexts than this.
 * @param batchRows           above 0, a formula lens batches its rows under one claimed span;
 *                            0 turns batching off. A first or random batch is what pool off
 *                            computes (context-pool regression D1).
 * @param maxBatchRows        the most rows one batch reads ahead: under sequential access a
 *                            lens's batches double from the pool-off look-ahead up to this
 *                            (spec §14.14). Never below batchRows.
 */
public record PoolConfig(long idleMillis, int cleanThreshold, int warnSlotsPerSandbox,
                         int warnSlotsPerNode, int batchRows, int maxBatchRows)
{
   public static final String ENABLED = "script.ws.contextPool";
   public static final String IDLE_MILLIS = "script.ws.contextPool.idleMillis";
   public static final String CLEAN_THRESHOLD = "script.ws.contextPool.cleanThreshold";
   public static final String WARN_SLOTS_PER_SANDBOX = "script.ws.contextPool.warnSlotsPerSandbox";
   public static final String WARN_SLOTS_PER_NODE = "script.ws.contextPool.warnSlotsPerNode";
   public static final String BATCH_ROWS = "script.ws.contextPool.batchRows";
   public static final String MAX_BATCH_ROWS = "script.ws.contextPool.maxBatchRows";

   /**
    * A claim that leaves more configurable foreign globals than this closes its context
    * instead of deleting them, each delete costing about 48 us (spec §14.1).
    */
   public static final int MAX_FOREIGN_DELETES = 32;

   public static PoolConfig defaults() {
      return new PoolConfig(60000L, 256, 16, 2000, 256, 8192);
   }

   /**
    * The pool mode when {@link #ENABLED} is not set: on (Feature #77123).
    */
   public static final boolean DEFAULT_ENABLED = true;

   /**
    * @return whether a sandbox built now runs its worksheet scripts on pooled contexts: true
    *         unless {@link #ENABLED} is set to something other than true (any case), for
    *         example {@code script.ws.contextPool=false}. A value that is neither true nor
    *         false (for example a typo) also turns the pool off, and is logged once as a WARN;
    *         an explicit false is logged once as an INFO.
    */
   public static boolean isEnabled() {
      try {
         String value = SreeEnv.getProperty(ENABLED);

         if(value == null || value.isBlank()) {
            return DEFAULT_ENABLED;
         }

         String trimmed = value.trim();

         if("true".equalsIgnoreCase(trimmed)) {
            return true;
         }

         if(!"false".equalsIgnoreCase(trimmed)) {
            warnUnrecognized(trimmed);
         }
         else if(OFF_LOGGED.compareAndSet(false, true)) {
            LOG.info("{}=false: the worksheet script context pool is off, so the engine-lock " +
                        "hangs of Bug #77016 can occur; see the release note (Turning it off) and " +
                        "pair it with stall.watchdog.mode=fail.", ENABLED);
         }

         return false;
      }
      catch(Exception ex) {
         return DEFAULT_ENABLED;
      }
   }

   /**
    * Logs one WARN per distinct unrecognized {@link #ENABLED} value, at most
    * {@link #MAX_WARNED_VALUES} values per JVM.
    */
   private static void warnUnrecognized(String value) {
      if(WARNED_VALUES.size() < MAX_WARNED_VALUES && WARNED_VALUES.add(value)) {
         String shown = value.length() > 64 ? value.substring(0, 64) + "..." : value;
         LOG.warn("{}='{}' is neither true nor false; the worksheet script context pool is " +
                     "turned OFF. Set it to true (or remove it) to keep the pool on.",
                  ENABLED, shown);
      }
   }

   public static PoolConfig read() {
      PoolConfig def = defaults();
      int batchRows = (int) longProperty(BATCH_ROWS, def.batchRows());
      return new PoolConfig(
         longProperty(IDLE_MILLIS, def.idleMillis()),
         (int) longProperty(CLEAN_THRESHOLD, def.cleanThreshold()),
         (int) longProperty(WARN_SLOTS_PER_SANDBOX, def.warnSlotsPerSandbox()),
         (int) longProperty(WARN_SLOTS_PER_NODE, def.warnSlotsPerNode()),
         batchRows,
         Math.max(batchRows, (int) longProperty(MAX_BATCH_ROWS, def.maxBatchRows())));
   }

   private static long longProperty(String name, long def) {
      try {
         String value = SreeEnv.getProperty(name);
         return value == null || value.isBlank() ? def : Long.parseLong(value.trim());
      }
      catch(Exception ex) {
         return def;
      }
   }

   static final int MAX_WARNED_VALUES = 16;
   private static final Set<String> WARNED_VALUES = ConcurrentHashMap.newKeySet();
   private static final AtomicBoolean OFF_LOGGED = new AtomicBoolean();
   private static final Logger LOG = LoggerFactory.getLogger(PoolConfig.class);
}

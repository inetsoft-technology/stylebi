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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Configuration of the worksheet script context pool (bug #76960). The pool is always on
 * (Feature #78459): a sandbox reads its mode once ({@link #isPoolMode()}), which only a test
 * can turn off; the tuning values are read once per env ({@link #read()}). The former switch
 * {@link #ENABLED} is retired and ignored with a WARN ({@link #warnIfSwitchSet()}).
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
 * @param maxHomes            the most exclusive homes of one sandbox: idle contexts reserved
 *                            for the formula tables whose vars hold arrays or objects living on
 *                            them (Testing #77123, B1 residual part 2), so up to this many
 *                            tables of a sandbox keep one each. Past it a home is soft:
 *                            another claim takes it over after saving its values.
 * @param maxHomesPerNode     the most exclusive homes of the node, which bounds the memory
 *                            they retain (about 0.16 MB per context).
 * @param handOffMillis       the time bound of one hand-off snapshot or rebuild of such values,
 *                            not the script timeout; past it the values are lost with a warning.
 * @param handOffEntries      the most array elements and object properties one hand-off saves.
 */
public record PoolConfig(long idleMillis, int cleanThreshold, int warnSlotsPerSandbox,
                         int warnSlotsPerNode, int batchRows, int maxBatchRows, int maxHomes,
                         int maxHomesPerNode, long handOffMillis, int handOffEntries)
{
   public PoolConfig(long idleMillis, int cleanThreshold, int warnSlotsPerSandbox,
                     int warnSlotsPerNode, int batchRows, int maxBatchRows)
   {
      this(idleMillis, cleanThreshold, warnSlotsPerSandbox, warnSlotsPerNode, batchRows,
           maxBatchRows, DEFAULT_MAX_HOMES, DEFAULT_MAX_HOMES_PER_NODE,
           DEFAULT_HAND_OFF_MILLIS, DEFAULT_HAND_OFF_ENTRIES);
   }

   public static final int DEFAULT_MAX_HOMES = 4;
   public static final int DEFAULT_MAX_HOMES_PER_NODE = 128;
   public static final long DEFAULT_HAND_OFF_MILLIS = 5000L;
   public static final int DEFAULT_HAND_OFF_ENTRIES = 200_000;

   /**
    * The retired switch that could turn the pool off before Feature #78459. It is ignored;
    * {@link #warnIfSwitchSet()} logs once if it is still set.
    */
   public static final String ENABLED = "script.ws.contextPool";
   public static final String IDLE_MILLIS = "script.ws.contextPool.idleMillis";
   public static final String CLEAN_THRESHOLD = "script.ws.contextPool.cleanThreshold";
   public static final String WARN_SLOTS_PER_SANDBOX = "script.ws.contextPool.warnSlotsPerSandbox";
   public static final String WARN_SLOTS_PER_NODE = "script.ws.contextPool.warnSlotsPerNode";
   public static final String BATCH_ROWS = "script.ws.contextPool.batchRows";
   public static final String MAX_BATCH_ROWS = "script.ws.contextPool.maxBatchRows";
   public static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   public static final String MAX_HOMES_PER_NODE = "script.ws.contextPool.maxHomesPerNode";
   public static final String HAND_OFF_MILLIS = "script.ws.contextPool.handOffMillis";
   public static final String HAND_OFF_ENTRIES = "script.ws.contextPool.handOffEntries";

   /**
    * A claim that leaves more configurable foreign globals than this closes its context
    * instead of deleting them, each delete costing about 48 us (spec §14.1).
    */
   public static final int MAX_FOREIGN_DELETES = 32;

   public static PoolConfig defaults() {
      return new PoolConfig(60000L, 256, 16, 2000, 256, 8192);
   }

   /**
    * @return whether a sandbox built now runs its worksheet scripts on pooled contexts:
    *         always, since Feature #78459 removed the switch. Only
    *         {@link #setPoolOffForTests(boolean)} turns it off.
    */
   public static boolean isPoolMode() {
      return !poolOffForTests;
   }

   /**
    * Test seam: makes the sandboxes built afterwards run pool off, for the tests of the
    * pool-off code paths that remain until they are removed. No property reaches it.
    */
   public static void setPoolOffForTests(boolean off) {
      poolOffForTests = off;
   }

   /**
    * Logs one WARN per JVM if the retired {@link #ENABLED} switch is still set, to any
    * non-blank value. Never throws.
    */
   public static void warnIfSwitchSet() {
      try {
         String value = SreeEnv.getProperty(ENABLED);

         if(value != null && !value.isBlank() && SWITCH_WARNED.compareAndSet(false, true)) {
            LOG.warn("{} is no longer supported and is ignored: the worksheet script context " +
                        "pool is always on (Feature #78459). Remove it from sree.properties.",
                     ENABLED);
         }
      }
      catch(Exception ignore) {
         // the warning is best effort
      }
   }

   /**
    * Test seam: lets the next {@link #warnIfSwitchSet()} log again.
    */
   public static void resetSwitchWarningForTests() {
      SWITCH_WARNED.set(false);
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
         Math.max(batchRows, (int) longProperty(MAX_BATCH_ROWS, def.maxBatchRows())),
         (int) Math.max(0, longProperty(MAX_HOMES, def.maxHomes())),
         (int) Math.max(0, longProperty(MAX_HOMES_PER_NODE, def.maxHomesPerNode())),
         Math.max(1, longProperty(HAND_OFF_MILLIS, def.handOffMillis())),
         (int) Math.max(1, longProperty(HAND_OFF_ENTRIES, def.handOffEntries())));
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

   private static volatile boolean poolOffForTests;
   private static final AtomicBoolean SWITCH_WARNED = new AtomicBoolean();
   private static final Logger LOG = LoggerFactory.getLogger(PoolConfig.class);
}

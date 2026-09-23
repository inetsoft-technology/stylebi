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

/**
 * Configuration of the worksheet script context pool (bug #76960). Whether the pool is on is
 * read once per sandbox ({@link #isEnabled()}); the tuning values once per env ({@link #read()}).
 *
 * @param idleMillis          an idle pooled context is closed after this long.
 * @param cleanThreshold      a context whose non-configurable leftover globals exceed this is
 *                            closed instead of reused.
 * @param warnSlotsPerSandbox warn (never cap) when one sandbox has more contexts than this.
 * @param warnSlotsPerNode    warn (never cap) when the node has more pooled contexts than this.
 * @param batchRows           the minimum rows a lens populates under one claimed span.
 * @param maxBatchRows        the most rows one batch reads ahead: under sequential access a
 *                            consumer's batches double from batchRows up to this (spec §14.14).
 *                            Never below batchRows.
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
    * @return whether a sandbox built now runs its worksheet scripts on pooled contexts.
    */
   public static boolean isEnabled() {
      try {
         return "true".equalsIgnoreCase(SreeEnv.getProperty(ENABLED, "false"));
      }
      catch(Exception ex) {
         return false;
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
}

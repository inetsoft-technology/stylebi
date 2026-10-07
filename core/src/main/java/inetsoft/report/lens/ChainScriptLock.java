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
package inetsoft.report.lens;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.util.script.LendableReentrantLock;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * Finds the script engine execution lock that reading a table chain may wait for, so an async
 * lens (DistinctTableLens, SummaryFilter) can take it before its own monitor and compute on the
 * reader's thread, instead of starting a worker that needs it (bug #77223). A worker that needs
 * the lock can only be lent it by a waiter that recorded it, never by a waiter inside script
 * evaluation on that engine, whose context must not be entered by another thread. A lens that
 * reads its whole base under its own monitor (SetTableLens, RankingTableLens, SortFilter,
 * CrossTabFilter) takes it before that monitor too, so a thread holding the lock never waits
 * for the monitor of a reader that waits for the lock (bug #77874).
 *
 * <p>Unlike {@code PostProcessor.ConditionFilter2.needsScriptExecutionLock}, which treats every
 * async lens as needing the lock so that it has something to lend (bug #76938), this walks
 * through async lenses: a worker over a base that takes no lock keeps running as before.
 */
public final class ChainScriptLock {
   private ChainScriptLock() {
   }

   /**
    * A table that takes a script engine's execution lock itself when its rows are read, e.g. a
    * formula lens or a condition filter that takes the lock before its own monitor.
    */
   public interface Source {
      /**
       * @return the execution lock reading this table may still take, or {@code null} if it
       *         takes none, e.g. with pooled script contexts or when nothing is left to
       *         compute.
       */
      Lock getScriptLock();
   }

   /**
    * Get the script engine execution lock that reading {@code table} may take.
    *
    * @return the lock, or {@code null} if reading the chain takes no engine lock, the locks of
    *         more than one engine (a chain over another sandbox's lenses, bug #76964), or the
    *         lock below a join, whose threads read its tables and are never lent the lock
    *         (bug #77016). These chains are read as before.
    */
   public static LendableReentrantLock find(TableLens table) {
      Finder finder = new Finder();
      return finder.walk(table) ? finder.lock : null;
   }

   private static final class Finder {
      /**
       * @return {@code false} if the chain must be read as before, see find().
       */
      boolean walk(TableLens table) {
         if(table == null || !visited.add(table)) {
            return true;
         }

         if(table instanceof JoinTableLens || table instanceof CrossJoinTableLens) {
            Finder below = new Finder();
            return walkChildren(table, below) && below.lock == null;
         }

         if(table instanceof Source) {
            Lock own = ((Source) table).getScriptLock();

            if(own != null) {
               if(!(own instanceof LendableReentrantLock) || lock != null && lock != own) {
                  return false;
               }

               lock = (LendableReentrantLock) own;
            }
         }

         return walkChildren(table, this);
      }

      private static boolean walkChildren(TableLens table, Finder finder) {
         if(table instanceof TableFilter) {
            for(TableLens child : ((TableFilter) table).getTables()) {
               if(!finder.walk(child)) {
                  return false;
               }
            }
         }

         if(table instanceof BinaryTableFilter) {
            for(TableLens child : ((BinaryTableFilter) table).getTables()) {
               if(!finder.walk(child)) {
                  return false;
               }
            }
         }

         return true;
      }

      private final Set<TableLens> visited = Collections.newSetFromMap(new IdentityHashMap<>());
      private LendableReentrantLock lock;
   }
}

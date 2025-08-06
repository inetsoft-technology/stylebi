/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedTransaction;
import org.apache.ignite.transactions.Transaction;

public class IgniteTransaction implements DistributedTransaction {
   public IgniteTransaction(Transaction tx) {
      this.tx = tx;
   }

   @Override
   public long startTime() {
      return tx.startTime();
   }

   @Override
   public long timeout() {
      return tx.timeout();
   }

   @Override
   public long timeout(long timeout) {
      return tx.timeout(timeout);
   }

   @Override
   public boolean setRollbackOnly() {
      return tx.setRollbackOnly();
   }

   @Override
   public boolean isRollbackOnly() {
      return tx.isRollbackOnly();
   }

   @Override
   public void commit() {
      tx.commit();
   }

   @Override
   public void close() {
      tx.close();
   }

   @Override
   public void rollback() {
      tx.rollback();
   }

   @Override
   public void resume() {
      tx.resume();
   }

   @Override
   public void suspend() {
      tx.suspend();
   }

   @Override
   public String label() {
      return tx.label();
   }

   private final Transaction tx;
}

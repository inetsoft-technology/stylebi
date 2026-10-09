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
package inetsoft.sree.internal.cluster;

/**
 * Thrown by {@link Cluster#runInTransaction} when the transaction failed: it timed out (including
 * waiting for a lock), was found in a deadlock, was rolled back by the cluster, or a node it
 * locked keys on left the cluster. A transaction that failed before it was committed was rolled
 * back, and none of its writes were committed. Only a failure while committing, such as a
 * heuristic outcome, can leave some or all of its writes committed.
 */
public class DistributedTransactionException extends RuntimeException {
   public DistributedTransactionException(String message) {
      super(message);
   }

   public DistributedTransactionException(String message, Throwable cause) {
      super(message, cause);
   }
}

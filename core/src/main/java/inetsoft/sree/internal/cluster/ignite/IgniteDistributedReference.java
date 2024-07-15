/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.internal.cluster.DistributedReference;
import org.apache.ignite.IgniteAtomicReference;

public class IgniteDistributedReference<V> implements DistributedReference<V> {
   public IgniteDistributedReference(IgniteAtomicReference<V> delegate) {
      this.delegate = delegate;
   }

   @Override
   public V get() {
      return delegate.get();
   }

   @Override
   public void set(V newValue) {
      delegate.set(newValue);
   }

   @Override
   public boolean compareAndSet(V expect, V update) {
      return delegate.compareAndSet(expect, update);
   }

   @Override
   public V getAndSet(V newValue) {
      V oldValue = delegate.get();
      delegate.set(newValue);
      return oldValue;
   }

   private final IgniteAtomicReference<V> delegate;
}

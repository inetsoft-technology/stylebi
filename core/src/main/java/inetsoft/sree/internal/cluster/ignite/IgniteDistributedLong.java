/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedLong;
import org.apache.ignite.IgniteAtomicLong;

class IgniteDistributedLong extends DistributedLong {
   IgniteDistributedLong(IgniteAtomicLong delegate) {
      this.delegate = delegate;
   }

   public long get() {
      return delegate.get();
   }

   public void set(long newValue) {
      delegate.getAndSet(newValue);
   }

   public void lazySet(long newValue) {
      delegate.getAndSet(newValue);
   }

   public long getAndSet(long newValue) {
      return delegate.getAndSet(newValue);
   }

   public boolean compareAndSet(long expect, long update) {
      return delegate.compareAndSet(expect, update);
   }

   public long getAndIncrement() {
      return delegate.getAndIncrement();
   }

   public long getAndDecrement() {
      return delegate.getAndAdd(-1L);
   }

   public long getAndAdd(long delta) {
      return delegate.getAndAdd(delta);
   }

   public long incrementAndGet() {
      return delegate.incrementAndGet();
   }

   public long decrementAndGet() {
      return delegate.decrementAndGet();
   }

   public long addAndGet(long delta) {
      return delegate.addAndGet(delta);
   }

   @Override
   public String toString() {
      return Long.toString(get());
   }

   @Override
   public int intValue() {
      return (int) get();
   }

   @Override
   public long longValue() {
      return get();
   }

   @Override
   public float floatValue() {
      return (float) get();
   }

   @Override
   public double doubleValue() {
      return (double) get();
   }

   private final IgniteAtomicLong delegate;
}

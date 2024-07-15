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
package inetsoft.sree.internal.cluster;

/**
 * Class that reproduces the interface of {@link java.util.concurrent.atomic.AtomicLong} while
 * wrapping the underlying implementation of a distributed value. {@code AtomicLong} cannot be
 * extended because all of the methods are final.
 */
public abstract class DistributedLong extends Number {
   public abstract long get();

   public abstract void set(long newValue);

   public abstract void lazySet(long newValue);

   public abstract long getAndSet(long newValue);

   public abstract boolean compareAndSet(long expect, long update);

   public abstract long getAndIncrement();

   public abstract long getAndDecrement();

   public abstract long getAndAdd(long delta);

   public abstract long incrementAndGet();

   public abstract long decrementAndGet();

   public abstract long addAndGet(long delta);
}

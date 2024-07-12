/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster;

/**
 * {@code DistributedReference} is an interface for a distributed version of
 * {@link java.util.concurrent.atomic.AtomicReference}.
 *
 * @param <V> the type of referenced object.
 */
public interface DistributedReference<V> {
   /**
    * Gets the current value.
    *
    * @return the current value
    */
   V get();

   /**
    * Sets to the given value.
    *
    * @param newValue the new value
    */
   void set(V newValue);

   /**
    * Atomically sets the value to the given updated value
    * if the current value {@code ==} the expected value.
    * @param expect the expected value
    * @param update the new value
    * @return {@code true} if successful. False return indicates that
    * the actual value was not equal to the expected value.
    */
   boolean compareAndSet(V expect, V update);

   /**
    * Atomically sets to the given value and returns the old value.
    *
    * @param newValue the new value
    * @return the previous value
    */
   V getAndSet(V newValue);
}

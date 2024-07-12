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
package inetsoft.util;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

/**
 * Class that provides a pool of objects.
 *
 * @param <T> the type of pooled object.
 */
public abstract class ObjectPool<T> {
   /**
    * Creates a new instance of <tt>ObjectPool</tt>.
    *
    * @param minSize the minimum number of pooled objects to be maintained. This
    *                controls how many objects are initially created. The
    *                minimum should be less than or equal to the maximum idle
    *                objects.
    * @param maxSize the maximum number of objects allowed to be in existence at
    *                any given time. If this value is zero, there is no limit
    *                placed on the maximum number of objects.
    * @param maxIdle the maximum number of idle objects to retain in the pool.
    * @param timeout the maximum number of milliseconds to wait for an available
    *                object if the maximum size is full. If this value is zero,
    *                consumers will block indefinitely.
    * 
    * @throws Exception if the initial pooled objects could not be created.
    */
   public ObjectPool(int minSize, int maxSize, int maxIdle, long timeout)
      throws Exception
   {
      this.maxSize = maxSize;
      this.maxIdle = maxIdle;
      this.timeout = timeout;

      for(int i = 0; i < minSize; i++) {
         pool.addLast(create());
      }
   }

   /**
    * Borrows an object from the pool.
    *
    * @return the pooled object.
    */
   public T borrowObject() throws Exception {
      T object = null;
      lock.lock();

      try {
         while(object == null) {
            if(pool.isEmpty()) {
               if(maxSize == 0 || getObjectCount() < maxSize) {
                  object = create();
               }
               else {
                  if(timeout == 0L) {
                     condition.await();
                  }
                  else if(!condition.await(timeout, TimeUnit.MILLISECONDS)) {
                     throw new Exception(
                        "Timed out waiting for available object");
                  }

                  object = pool.pollFirst();
               }
            }
            else {
               object = pool.pollFirst();
            }

            if(object != null && !validate(object)) {
               destroy(object);
               object = null;
            }
         }

         borrowed.add(object);
      }
      finally {
         lock.unlock();
      }

      return object;
   }

   /**
    * Returns an object to the pool.
    *
    * @param object the object to return.
    *
    * @return true if the object was borrowed from this pool.
    */
   public boolean returnObject(T object) {
      boolean found = true;
      lock.lock();

      try {
         if(borrowed.remove(object)) {
            if(pool.size() == maxIdle) {
               destroy(object);
            }
            else {
               pool.addLast(object);
            }
         }
         else if(flushed.remove(object)) {
            destroy(object);
         }
         else {
            found = false;
         }

         condition.signal();
      }
      finally {
         lock.unlock();
      }

      return found;
   }

   /**
    * Flushes all objects from the pool. Objects that are currently borrowed
    * will be destroyed when returned.
    */
   public void flush() {
      lock.lock();

      try {
         flushed.addAll(borrowed);
         borrowed.clear();

         while(!pool.isEmpty()) {
            destroy(pool.removeFirst());
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Creates a new instance of a pooled object.
    * 
    * @return the object to be pooled.
    * 
    * @throws Exception if the instantiation failed.
    */
   protected abstract T create() throws Exception;

   /**
    * Destroys an object being discarded from the pool.
    *
    * @param object the object to destroy.
    */
   protected abstract void destroy(T object);

   /**
    * Determines if the pooled object is still valid.
    *
    * @param object the object to test.
    *
    * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
    */
   protected abstract boolean validate(T object);

   /**
    * Gets the total number of live objects.
    *
    * @return the object count.
    */
   private int getObjectCount() {
      return pool.size() + borrowed.size();
   }

   private final int maxSize;
   private final int maxIdle;
   private final long timeout;

   private final Lock lock = new ReentrantLock();
   private final Condition condition = lock.newCondition();
   private final Deque<T> pool = new ArrayDeque<>();
   private final Set<T> borrowed = new HashSet<>();
   private final Set<T> flushed = new HashSet<>();
}

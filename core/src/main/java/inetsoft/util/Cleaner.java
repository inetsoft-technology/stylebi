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
package inetsoft.util;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that encapsulates most of the implementation details of replacing {@code finalize()}
 * methods with phantom references.
 */
public final class Cleaner extends GroupedThread {
   private Cleaner() {
   }

   /**
    * Adds a new cleaner reference.
    *
    * @param reference the reference to add.
    * @param <T>       the type of object referred to by the reference.
    */
   public static synchronized <T> void add(Reference<T> reference) {
      LOCK.lock();

      try {
         getInstance().references.add(reference);
      }
      finally {
         LOCK.unlock();
      }
   }

   @Override
   protected void doRun() {
      while(!isCancelled()) {
         try {
            Reference<?> reference = (Reference) queue.remove();
            LOCK.lock();

            try {
               references.remove(reference);
            }
            finally {
               LOCK.unlock();
            }

            reference.close();
         }
         catch(InterruptedException e) {
            if(!isCancelled()) {
               LOG.warn("Error cleaning up resources", e);
            }
         }
         catch(Exception e) {
            LOG.warn("Error cleaning up resources", e);
         }
      }
   }

   private static Cleaner getInstance() {
      LOCK.lock();

      try {
         if(INSTANCE == null) {
            INSTANCE = new Cleaner();
            INSTANCE.start();
         }
      }
      finally {
         LOCK.unlock();
      }

      return INSTANCE;
   }

   private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
   // this just maintains a reference to the phantom references so that they aren't garbage
   // collected before the objects that they refer to
   @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
   private final Set<Reference> references = new ObjectOpenHashSet<>();

   private static final Lock LOCK = new ReentrantLock();
   private static Cleaner INSTANCE;
   private static final Logger LOG = LoggerFactory.getLogger(Cleaner.class);

   /**
    * Specialization of {@code PhantomReference} for use by {@code Cleaner}. The {@link #close()}
    * method is called to clean up any resources held by the referent object. Implementing classes
    * will typically hold a reference to the resources that should be cleaned up, and then perform
    * that clean up in the {@link #close()} method. Implementing classes <b>must not</b> keep a
    * reference to the referent, as this will cause a memory leak.
    *
    * @param <T> the type of the referent.
    */
   public static abstract class Reference<T> extends PhantomReference<T> implements AutoCloseable {
      /**
       * Creates a new instance of {@code Reference}.
       *
       * @param referent the object referred to by this reference.
       */
      public Reference(T referent) {
         super(referent, getInstance().queue);

         Class<?> clazz = getClass();

         if((clazz.isMemberClass() || clazz.isAnonymousClass() || clazz.isLocalClass()) &&
            !Modifier.isStatic(clazz.getModifiers()))
         {
            // Enforce that cleaner references must be top-level or static, otherwise it will
            // introduce a memory leak by keeping a reference to the outer class, which is typically
            // the referent.
            throw new IllegalStateException(
               "A cleaner reference cannot be a non-static nested class");
         }
      }

      @Override
      public void clear() {
         LOCK.lock();

         try {
            getInstance().references.remove(this);
         }
         finally {
            LOCK.unlock();
         }

         super.clear();
      }
   }
}

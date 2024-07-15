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

import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class that manages the lifecycle of singleton services.
 *
 * @since 12.3
 */
public final class SingletonManager {
   /**
    * Gets the singleton instance of the specified type of service.
    *
    * @param type       the service class.
    * @param parameters the service creation parameters.
    *
    * @param <T> the service type.
    *
    * @return the service instance.
    */
   public static <T> T getInstance(Class<T> type, Object ... parameters) {
      return INSTANCE.doGetInstance(type, parameters);
   }

   @SuppressWarnings({ "unchecked", "SynchronizationOnLocalVariableOrMethodParameter", "rawtypes" })
   private <T> T doGetInstance(Class<T> type, Object... parameters) {
      Reference<T> reference = (Reference<T>) instances.get(type);

      if(reference == null) {
         try {
            while(!resetLock.readLock().tryLock(5, TimeUnit.SECONDS)) {
               if((Thread.currentThread() instanceof GroupedThread) &&
                  ((GroupedThread) Thread.currentThread()).isCancelled())
               {
                  throw new RuntimeException("Thread cancelled while waiting for lock");
               }
               else if(resetting) {
                  throw new ResurrectException();
               }
            }
         }
         catch(InterruptedException e) {
            throw new RuntimeException("Thread interrupted while waiting for lock", e);
         }

         try {
            synchronized(type) {
               reference = (Reference<T>) instances.get(type);

               if(reference == null) {
                  if(resetting) {
                     throw new ResurrectException();
                  }

                  if(type.isAnnotationPresent(Singleton.class)) {
                     Class<? extends Reference> referenceClass =
                        type.getAnnotation(Singleton.class).value();

                     if(referenceClass != DefaultReference.class) {
                        reference = type.getAnnotation(Singleton.class).value()
                           .getConstructor().newInstance();
                     }
                  }

                  if(reference == null) {
                     reference = new DefaultReference<>(type);
                  }

                  instances.put(type, reference);
               }
            }
         }
         catch(NoSuchMethodException | IllegalAccessException |
               InstantiationException e) {
            throw new RuntimeException("Failed to create service instance", e);
         }
         catch(InvocationTargetException e) {
            throw new RuntimeException(
               "Failed to create service instance", e.getTargetException());
         }
         finally {
            resetLock.readLock().unlock();
         }
      }

      if(reference.disposed) {
         throw new ResurrectException();
      }

      return reference.get(parameters);
   }

   /**
    * Closes all existing singleton service instances.
    */
   public static void reset() {
      INSTANCE.doReset();
   }

   @SuppressWarnings("unchecked")
   private void doReset() {
      resetLock.writeLock().lock();

      try {
         resetting = true;

         try {
            // dispose of the data space(s) so that any listeners are removed and don't try to
            // access an object during or after it's shutdown.
            Reference<DataSpace> dataSpace =
               (Reference<DataSpace>) instances.remove(DataSpace.class);

            if(dataSpace != null) {
               dataSpace.disposed = true;
               dataSpace.dispose();
            }

            // make sure that the log manager is disposed first so that the context logging filter
            // doesn't resurrect anything--default logging will be used during the reset
            Reference<LogManager> log = (Reference<LogManager>) instances.remove(LogManager.class);

            if(log != null) {
               log.disposed = true;
               log.dispose();
            }

            Reference<?>[] references = instances.values().toArray(new Reference<?>[0]);
            /*
             References that must be disposed of last are annotated with @ShutdownOrder, current
             order is:
             all singletons without annotation, in unspecified order
             PortalThemesManager
             Config
             InetsoftConfig
             DbConfig
             AuthenticationService
             HazelcastCluster
             FileSystemService
             ConfigurationContext
            */
            Arrays.sort(references);

            for(Reference<?> reference : references) {
               instances.entrySet().removeIf(e -> e.getValue() == reference);
               reference.disposed = true;

               try {
                  reference.dispose();
               }
               catch(ResurrectException ignore) {
               }
            }

            instances.clear();
         }
         finally {
            resetting = false;
         }
      }
      finally {
         resetLock.writeLock().unlock();
      }
   }

   /**
    * Closes an existing singleton service instance.
    *
    * @param type the type of service to close.
    */
   public static void reset(Class<?> type) {
      INSTANCE.doReset(type);
   }

   @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
   private void doReset(Class<?> type) {
      Reference<?> reference;

      synchronized(type) {
         reference = instances.remove(type);
      }

      if(reference != null) {
         reference.dispose();
      }
   }

   private final Map<Class<?>, Reference<?>> instances = new ConcurrentHashMap<>();
   private volatile boolean resetting = false;
   private final ReadWriteLock resetLock = new ReentrantReadWriteLock(true);

   private static final SingletonManager INSTANCE = new SingletonManager();

   private static final Logger LOG = LoggerFactory.getLogger(SingletonManager.class);

   /**
    * Annotation used to associate a reference provider with a service interface.
    */
   @Target(ElementType.TYPE)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface Singleton {
      /**
       * The class responsible for managing the singleton service instance.
       */
      Class<? extends Reference> value() default DefaultReference.class;
   }

   /**
    * Annotation used to order the shutdown of singletons.
    */
   @Target(ElementType.TYPE)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface ShutdownOrder {
      /**
       * Classes that the annotated singleton must be shutdown after. If empty, the annotated
       * singleton must be shut down after any singletons without this annotation, but before any
       * any singletons with a non-empty value.
       */
      Class<?>[] after() default {};
   }

   /**
    * Interface for classes that manage a singleton service instance.
    *
    * @param <T> the service interface type.
    */
   public static abstract class Reference<T> implements Comparable<Reference<?>> {
      /**
       * Gets the service instance.
       *
       * @param parameters the instance creation parameters.
       *
       * @return the service instance.
       */
      public abstract T get(Object ... parameters);

      /**
       * Disposes of the service instance.
       */
      public abstract void dispose();

      @Override
      public int compareTo(SingletonManager.Reference<?> o) {
         ShutdownOrder thisOrder = getOrder();
         ShutdownOrder otherOrder = o.getOrder();

         if(thisOrder == null && otherOrder != null) {
            return -1;
         }
         else if(thisOrder != null && otherOrder == null) {
            return 1;
         }
         else if(thisOrder == null) {
            return 0;
         }

         if(thisOrder.after().length == 0 && otherOrder.after().length > 0) {
            return -1;
         }
         else if(thisOrder.after().length > 0 && otherOrder.after().length == 0) {
            return 1;
         }
         else if(thisOrder.after().length == 0 && otherOrder.after().length == 0) {
            return 0;
         }

         Class<?> type = getOrderType();

         for(Class<?> after : getShutdownAfter(otherOrder.after(), new HashSet<>())) {
            if(after.equals(type)) {
               return -1;
            }
         }

         type = o.getOrderType();

         for(Class<?> after : getShutdownAfter(thisOrder.after(), new HashSet<>())) {
            if(after.equals(type)) {
               return 1;
            }
         }

         return 0;
      }

      ShutdownOrder getOrder() {
         return getClass().getAnnotation(ShutdownOrder.class);
      }

      Class<?> getOrderType() {
         return getClass();
      }

      @Override
      public String toString() {
         return "Reference{" + getClass().getName() + "}";
      }

      private static Set<Class<?>> getShutdownAfter(Class<?>[] types, Set<Class<?>> allTypes) {
         for(Class<?> type : types) {
            if(!allTypes.contains(type)) {
               allTypes.add(type);
               ShutdownOrder order = type.getAnnotation(ShutdownOrder.class);

               if(order != null) {
                  getShutdownAfter(order.after(), allTypes);
               }
            }
         }

         return allTypes;
      }

      private volatile boolean disposed = false;
   }

   /**
    * Singleton reference implementation that lazily creates the service via reflection.
    *
    * @param <T> the service implementation type.
    */
   private static final class DefaultReference<T> extends Reference<T> {
      /**
       * Creates a new instance of <tt>DefaultReference</tt>.
       *
       * @param type the service class.
       */
      DefaultReference(Class<T> type) {
         this.type = type;
      }

      @Override
      public T get(Object ... parameters) {
         if(instance == null) {
            synchronized(this) {
               if(instance == null) {
                  try {
                     instance = type.getConstructor().newInstance();
                  }
                  catch(InstantiationException | IllegalAccessException |
                        NoSuchMethodException e)
                  {
                     throw new RuntimeException("Failed to create service instance", e);
                  }
                  catch(InvocationTargetException e) {
                     throw new RuntimeException(
                        "Failed to create service instance", e.getTargetException());
                  }
               }
            }
         }

         return instance;
      }

      @Override
      public synchronized void dispose() {
         if(instance instanceof AutoCloseable) {
            try {
               ((AutoCloseable) instance).close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close service instance", e);
            }
         }

         instance = null;
      }

      @Override
      ShutdownOrder getOrder() {
         return type.getAnnotation(ShutdownOrder.class);
      }

      @Override
      Class<?> getOrderType() {
         return type;
      }

      @Override
      public String toString() {
         return "DefaultReference{" + type.getName() + "}";
      }

      private final Class<T> type;
      private T instance;
   }

   public static final class ResurrectException extends IllegalStateException {
      public ResurrectException() {
         super("Singleton manager is being reset, cannot resurrect instances");
      }
   }
}

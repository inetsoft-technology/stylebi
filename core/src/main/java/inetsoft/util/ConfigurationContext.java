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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Class that stores information that is specific configuration home directory.
 */
@SuppressWarnings("unchecked")
public class ConfigurationContext implements AutoCloseable {
   /**
    * Gets the shared instance of the configuration context.
    *
    * @return the configuration context.
    */
   public static ConfigurationContext getContext() {
      if(INSTANCE == null) {
         synchronized(ConfigurationContext.class) {
            if(INSTANCE == null) {
               INSTANCE = new ConfigurationContext();
            }
         }
      }

      return INSTANCE;
   }

   /**
    * Gets the configuration home directory.
    *
    * @return the home directory.
    */
   public String getHome() {
      return home;
   }

   /**
    * Sets the configuration home directory.
    *
    * @param home the home directory.
    */
   public void setHome(String home) {
      String oldHome = this.home;
      this.home = home == null ? "." : home;
      support.firePropertyChange("home", oldHome, this.home);
   }

   /**
    * Gets a stored value.
    *
    * @param key the key associated with the value.
    *
    * @param <T> the type of the value.
    *
    * @return the value or <tt>null</tt> if not set.
    */
   public <T> T get(String key) {
      return (T) data.get(key);
   }

   /**
    * Gets a stored value. If the specified key is not already associated with a value (or is mapped
    * to {@code null}), attempts to compute its value using the given mapping
    * function and enters it into this map unless {@code null}.
    *
    * @param key the key associated with the value.
    * @param mappingFunction the function to compute a value.
    *
    * @param <T> the type of the value.
    *
    * @return the value. Concurrent callers get the same value, although the mapping
    *         function may be called more than once, the extra values are discarded.
    *
    * @see Map#computeIfAbsent(Object, Function)
    */
   public <T> T computeIfAbsent(String key, Function<? super String, ? extends T> mappingFunction) {
      Objects.requireNonNull(mappingFunction);
      T value;

      if((value = get(key)) == null) {
         T newValue;

         if((newValue = mappingFunction.apply(key)) != null) {
            // only store the value if no other thread stored one meanwhile, so all callers
            // get the same value (bug #76938). the function is called outside of the
            // map's locking, so it may use this context itself
            T oldValue = (T) data.putIfAbsent(key, newValue);

            if(oldValue != null) {
               return oldValue;
            }

            support.firePropertyChange(key, null, newValue);
            return newValue;
         }
      }

      return value;
   }

   /**
    * Sets a stored value.
    *
    * @param key   the key associated with the value.
    * @param value the value to store.
    *
    * @param <T> the type of the value.
    *
    * @return the previous value associated with the key or <tt>null</tt> if none.
    */
   public <T> T put(String key, Object value) {
      T oldValue = (T) data.put(key, value);
      support.firePropertyChange(key, oldValue, value);
      return oldValue;
   }

   /**
    * Removes a stored value.
    *
    * @param key the key associated with the value.
    *
    * @param <T> the type of the value.
    *
    * @return the value that was associated with the key or <tt>null</tt> if none.
    */
   public <T> T remove(String key) {
      T oldValue = (T) data.remove(key);
      support.firePropertyChange(key, oldValue, null);
      return oldValue;
   }

   public void addPropertyChangeListener(PropertyChangeListener listener) {
      support.addPropertyChangeListener(listener);
   }

   public void removePropertyChangeListener(PropertyChangeListener listener) {
      support.removePropertyChangeListener(listener);
   }

   public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      support.addPropertyChangeListener(propertyName, listener);
   }

   public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      support.removePropertyChangeListener(propertyName, listener);
   }

   public void setApplicationContext(ApplicationContext applicationContext) {
      beanCache.invalidateAll();
      this.applicationContext = applicationContext;

      if(applicationContext != null) {
         springContextReady.complete(null);
      }
   }

   public ApplicationContext getApplicationContext() {
      return applicationContext;
   }

   /**
    * Returns a future that completes the first time the Spring application context is initialized.
    * This future is never reset, so it remains done even if the context is later refreshed or
    * replaced. Callers running in non-Spring threads (e.g. Ignite affinity executors) can await
    * this future to avoid racing against Spring startup on a newly joined cluster node.
    */
   public CompletableFuture<Void> getSpringContextReady() {
      return springContextReady;
   }

   public <T> T lookupProxyTarget(Class<T> type) {
      return getSpringBean(type);
   }

   public <T> T getSpringBean(Class<T> type) {
      if(applicationContext == null) {
         throw new ShutdownException();
      }

      // Fast path: already cached.
      Object existing = beanCache.getIfPresent(type);

      if(existing != null && existing != MISSING_BEAN) {
         return type.cast(existing);
      }

      if(IN_CACHE_LOAD.get()) {
         // This thread is already inside getSpringBean — bypass the cache entirely to prevent
         // re-entrant ConcurrentHashMap.compute() on the same thread (which deadlocks).
         return applicationContext.getBean(type);
      }

      // Slow path: call applicationContext.getBean() WITHOUT holding the Caffeine/
      // ConcurrentHashMap bin lock.  Caffeine's Cache.get(key, loader) holds that lock for
      // the entire loader duration, which can be minutes when a @Lazy bean's @PostConstruct
      // blocks on async cluster work (e.g. LocalKeyValueStorage waits up to 3 min for the
      // initial data load).  A background thread calling getSpringBean for any bean whose
      // Class happens to hash to the same bin would deadlock until the timeout fires.
      //
      // Spring singleton beans are safe to retrieve concurrently — getBean() is idempotent
      // and thread-safe, so two concurrent cache-miss threads both get the same instance.
      // It is not free of waiting: during a refresh, a singleton that is not created yet
      // waits for the refreshing thread's singleton lock, see awaitSpringBean().
      IN_CACHE_LOAD.set(true);
      T bean;

      try {
         bean = applicationContext.getBean(type);
      }
      finally {
         IN_CACHE_LOAD.remove();
      }

      beanCache.put(type, bean);
      return bean;
   }

   /**
    * Gets a Spring singleton from a thread that the thread refreshing the context may itself be
    * waiting on, such as a cluster singleton-service thread.
    *
    * <p>Until the bean factory configuration is frozen, Spring creates singletons under one lock,
    * and the refreshing thread holds it for as long as it creates beans. Looking up a singleton
    * that is not created yet parks on that lock, which deadlocks when the refreshing thread waits
    * for the calling thread meanwhile (Bug #76975). So while the configuration is not frozen and
    * no bean of the type is created yet, this method waits for the bean instead of looking it up,
    * for at most the given time, after which it looks it up anyway.</p>
    *
    * @param type    the bean type.
    * @param timeout the maximum time to wait for the bean to be created.
    * @param unit    the unit of the timeout.
    *
    * @return the bean.
    */
   public <T> T awaitSpringBean(Class<T> type, long timeout, TimeUnit unit) {
      long deadline = System.nanoTime() + unit.toNanos(timeout);

      while(!isSingletonAvailable(type)) {
         if(System.nanoTime() - deadline >= 0) {
            LOG.warn("Spring bean {} was not created within {} {}, looking it up anyway",
                     type.getName(), timeout, unit);
            break;
         }

         try {
            Thread.sleep(20L);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
         }
      }

      return getSpringBean(type);
   }

   /**
    * Determines if a singleton of the given type can be looked up without waiting on the singleton
    * lock of a context that is being refreshed. Only reads state that Spring keeps outside of that
    * lock.
    */
   private boolean isSingletonAvailable(Class<?> type) {
      ApplicationContext context = applicationContext;

      if(!(context instanceof ConfigurableApplicationContext configurable) ||
         beanCache.getIfPresent(type) != null)
      {
         return true;
      }

      try {
         ConfigurableListableBeanFactory factory = configurable.getBeanFactory();

         if(factory.isConfigurationFrozen()) {
            return true;
         }

         String[] names = factory.getBeanNamesForType(type, true, false);

         if(names.length == 0) {
            // not defined in this context, the lookup reports or resolves it
            return true;
         }

         for(String name : names) {
            if(factory.containsSingleton(name)) {
               return true;
            }
         }

         return false;
      }
      catch(IllegalStateException e) {
         // the context is not refreshed yet or already closed, the lookup reports it
         return true;
      }
   }

   /**
    * Gets an optional Spring bean by type. Returns {@code null} if the bean is not registered
    * (e.g., the providing {@code @Configuration} was excluded by a {@code @Conditional}).
    * Falls back to reflection-based instantiation when not running in Spring.
    */
   public <T> T getOptionalSpringBean(Class<T> type) {
      if(applicationContext == null) {
         return null;
      }

      // Fast path: already cached (including MISSING_BEAN sentinel).
      Object existing = beanCache.getIfPresent(type);

      if(existing != null) {
         return existing == MISSING_BEAN ? null : type.cast(existing);
      }

      if(IN_CACHE_LOAD.get()) {
         // Re-entrant call on the same thread — bypass the cache.
         try {
            return applicationContext.getBean(type);
         }
         catch(NoSuchBeanDefinitionException e) {
            return null;
         }
      }

      // Slow path: retrieve from Spring WITHOUT holding the Caffeine bin lock.
      // See getSpringBean() for the full explanation.
      IN_CACHE_LOAD.set(true);
      Object bean;

      try {
         bean = applicationContext.getBean(type);
      }
      catch(NoSuchBeanDefinitionException e) {
         bean = MISSING_BEAN;
      }
      finally {
         IN_CACHE_LOAD.remove();
      }

      beanCache.put(type, bean);
      return bean == MISSING_BEAN ? null : type.cast(bean);
   }

   public Object getSpringBean(String name) {
      if(applicationContext == null) {
         throw new ShutdownException();
      }

      return applicationContext.getBean(name);
   }

   public <T> T getSpringBean(String name, Class<T> type) {
      if(applicationContext == null) {
         throw new ShutdownException();
      }

      return applicationContext.getBean(name, type);
   }

   @Override
   public void close() throws IOException {
      for(Iterator<Object> it = data.values().iterator(); it.hasNext();) {
         Object value = it.next();

         if(value instanceof AutoCloseable) {
            try {
               ((AutoCloseable) value).close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close context value", e);
            }
         }

         it.remove();
      }
   }

   private final Map<String, Object> data = new ConcurrentHashMap<>();
   private final Cache<Class<?>, Object> beanCache = Caffeine.newBuilder()
      .maximumSize(500L)
      .build();
   private final PropertyChangeSupport support = new PropertyChangeSupport(this);
   private volatile String home = ".";
   private ApplicationContext applicationContext;
   private final CompletableFuture<Void> springContextReady = new CompletableFuture<>();
   private static volatile ConfigurationContext INSTANCE;
   private static final Object MISSING_BEAN = new Object();
   // Prevents ConcurrentHashMap recursive update when Spring bean initialization
   // triggers a nested getSpringBean/getOptionalSpringBean call on the same thread.
   private static final ThreadLocal<Boolean> IN_CACHE_LOAD = ThreadLocal.withInitial(() -> false);
   private static final Logger LOG = LoggerFactory.getLogger(ConfigurationContext.class);
}

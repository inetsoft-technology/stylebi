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

import inetsoft.util.config.InetsoftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Class that stores information that is specific configuration home directory.
 */
@SuppressWarnings("unchecked")
@SingletonManager.ShutdownOrder(after = InetsoftConfig.class)
public class ConfigurationContext implements AutoCloseable {
   /**
    * Gets the shared instance of the configuration context.
    *
    * @return the configuration context.
    */
   public static ConfigurationContext getContext() {
      return SingletonManager.getInstance(ConfigurationContext.class);
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
    * @return the value.
    *
    * @see Map#computeIfAbsent(Object, Function)
    */
   public <T> T computeIfAbsent(String key, Function<? super String, ? extends T> mappingFunction) {
      Objects.requireNonNull(mappingFunction);
      T value;

      if((value = get(key)) == null) {
         T newValue;

         if((newValue = mappingFunction.apply(key)) != null) {
            put(key, newValue);
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
   private final PropertyChangeSupport support = new PropertyChangeSupport(this);
   private volatile String home = ".";
   private static final Logger LOG = LoggerFactory.getLogger(ConfigurationContext.class);
}

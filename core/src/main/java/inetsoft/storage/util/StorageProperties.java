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
package inetsoft.storage.util;

import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * {@code StorageProperties} is a specialization of {@link Properties} that stores its values in a
 * key-value store.
 */
public class StorageProperties extends Properties implements AutoCloseable {
   /**
    * Creates a new instance of {@code StorageProperties}.
    *
    * @param id the unique identifier of the key-value store.
    */
   public StorageProperties(String id) {
      this(id, null);
   }

   /**
    * Creates a new instance of {@code StorageProperties}.
    *
    * @param id       the unique identifier of the key-value store.
    * @param defaults the default property values.
    */
   public StorageProperties(String id, Properties defaults) {
      this.id = id;
      this.defaults = defaults;
      this.storage = SingletonManager.getInstance(KeyValueStorage.class, id);
      this.storage.addListener(listener);
   }

   @Override
   public synchronized void load(Reader reader) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public synchronized void load(InputStream inStream) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void store(Writer writer, String comments) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public void store(OutputStream out, String comments) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getProperty(String key) {
      String value = storage.get(key);
      return (value == null && defaults != null) ? defaults.getProperty(key) : value;
   }

   @Override
   public Enumeration<?> propertyNames() {
      return Collections.enumeration(stringPropertyNames());
   }

   @Override
   public Set<String> stringPropertyNames() {
      Set<String> names = new HashSet<>();

      if(defaults != null) {
         names.addAll(defaults.stringPropertyNames());
      }

      storage.keys().forEach(names::add);
      return Collections.unmodifiableSet(names);
   }

   @Override
   public int size() {
      return storage.size();
   }

   @Override
   public boolean isEmpty() {
      return storage.size() == 0;
   }

   @Override
   public Enumeration<Object> keys() {
      return Collections.enumeration(storage.keys()
                                        .collect(Collectors.toSet()));
   }

   @Override
   public Enumeration<Object> elements() {
      return Collections.enumeration(storage.stream()
                                        .map(KeyValuePair::getValue)
                                        .collect(Collectors.toList()));
   }

   @Override
   public boolean contains(Object value) {
      return containsValue(value);
   }

   @Override
   public boolean containsValue(Object value) {
      return storage.stream().anyMatch(p -> Objects.equals(value, p.getValue()));
   }

   @Override
   public boolean containsKey(Object key) {
      return (key instanceof String) && storage.contains((String) key);
   }

   @Override
   public Object get(Object key) {
      return (key instanceof String) ? storage.get((String) key) : null;
   }

   @Override
   public synchronized Object put(Object key, Object value) {
      if(!(key instanceof String)) {
         throw new IllegalArgumentException("Storage properties can only use string keys");
      }

      if(!(value instanceof String)) {
         throw new IllegalArgumentException("Storage properties can only use string values");
      }

      try {
         return storage.put((String) key, (String) value).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to set property {}", key, e);
         return null;
      }
   }

   @Override
   public synchronized Object remove(Object key) {
      if(!(key instanceof String)) {
         return null;
      }

      try {
         return storage.remove((String) key).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to remove property {}", key, e);
         return null;
      }
   }

   @Override
   public synchronized void putAll(Map<?, ?> t) {
      SortedMap<String, String> values = new TreeMap<>();

      for(Map.Entry<?, ?> e : t.entrySet()) {
         Object key = e.getKey();
         Object value = e.getValue();

         if(!(key instanceof String)) {
            throw new IllegalArgumentException("Storage properties can only use string keys");
         }

         if(!(value instanceof String)) {
            throw new IllegalArgumentException("Storage properties can only use string values");
         }

         values.put((String) key, (String) value);
      }

      try {
         storage.putAll(values).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to put values into key-value store", e);
      }
   }

   @Override
   public synchronized void clear() {
      try {
         storage.replaceAll(new TreeMap<>()).get();
      }
      catch(InterruptedException | ExecutionException e) {
         LOG.error("Failed to clear key-value store", e);
      }
   }

   @Override
   public void close() throws Exception {
      if(storage != null) {
         storage.close();
      }
   }

   @Override
   public synchronized String toString() {
      return toMap().toString();
   }

   @Override
   public Set<Object> keySet() {
      return Collections.unmodifiableSet(stringPropertyNames());
   }

   @Override
   public Collection<Object> values() {
      return Collections.unmodifiableCollection(storage.stream()
                                                   .map(KeyValuePair::getValue)
                                                   .collect(Collectors.toList()));
   }

   @Override
   public Set<Map.Entry<Object, Object>> entrySet() {
      return Collections.unmodifiableMap(toMap()).entrySet();
   }

   public void addPropertyChangeListener(PropertyChangeListener listener) {
      changeSupport.addPropertyChangeListener(listener);
   }

   public void removePropertyChangeListener(PropertyChangeListener listener) {
      changeSupport.removePropertyChangeListener(listener);
   }

   public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      changeSupport.addPropertyChangeListener(propertyName, listener);
   }

   public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
      changeSupport.removePropertyChangeListener(propertyName, listener);
   }

   private Map<Object, Object> toMap() {
      Map<Object, Object> map = new HashMap<>();
      storage.stream().forEach(p -> map.put(p.getKey(), p.getValue()));
      return map;
   }

   private Object readResolve() throws ObjectStreamException {
      this.storage = SingletonManager.getInstance(KeyValueStorage.class, id);
      this.storage.addListener(listener);
      return this;
   }

   private final String id;
   private final Properties defaults;
   private transient KeyValueStorage<String> storage;
   private transient final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
   private static final Logger LOG = LoggerFactory.getLogger(StorageProperties.class);

   private transient final KeyValueStorage.Listener<String> listener = new KeyValueStorage.Listener<String>() {
      @Override
      public void entryAdded(KeyValueStorage.Event<String> event) {
         changeSupport.firePropertyChange(event.getKey(), event.getOldValue(), event.getNewValue());
      }

      @Override
      public void entryUpdated(KeyValueStorage.Event<String> event) {
         changeSupport.firePropertyChange(event.getKey(), event.getOldValue(), event.getNewValue());
      }

      @Override
      public void entryRemoved(KeyValueStorage.Event<String> event) {
         changeSupport.firePropertyChange(event.getKey(), event.getOldValue(), event.getNewValue());
      }
   };
}

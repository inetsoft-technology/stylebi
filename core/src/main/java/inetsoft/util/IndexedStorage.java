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

import inetsoft.sree.security.Organization;
import inetsoft.uql.util.AbstractIdentity;
import org.w3c.dom.Document;

import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * This interface defines the API for a storage that can store and retrieve
 * block of data using a key.
 * The implementation may be file based or database based.
 *
 * @version 8.0, 6/2/2005
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(IndexedStorage.Reference.class)
public interface IndexedStorage {
   /**
    * Remove all key-data mappings from storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   boolean clear();

   /**
    * Close the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   default boolean close() {
      return true;
   }

   /**
    * Disposes of the indexed storage.
    */
   void dispose();

   /**
    * Check if the storage contains the key or not.
    * @param key the specified key
    * @return <tt>true</tt> if the storage contains the key,
    * <tt>false</tt> otherwise.
    */
   boolean contains(String key);

   /**
    * Check if the storage contains the key or not.
    * @param key the specified key
    * @param orgID the orgID of the storage to check.
    * @return <tt>true</tt> if the storage contains the key,
    * <tt>false</tt> otherwise.
    */
   boolean contains(String key, String orgID);

   /**
    * Gets the XMLSerializable value that is associated with the specified key.
    *
    * @param key the data key.
    * @param trans transformation before parsing.
    * @return the associated value.
    * @throws Exception if get XMLSerializable value failed.
    */
   XMLSerializable getXMLSerializable(String key, TransformListener trans) throws Exception;

   /**
    * Gets the XMLSerializable value that is associated with the specified key.
    *
    * @param key the data key.
    * @param trans transformation before parsing.
    * @param orgID the orgID of the storage to check.
    * @return the associated value.
    * @throws Exception if get XMLSerializable value failed.
    */
   XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID) throws Exception;

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws Exception if put XMLSerializable value failed.
    */
   void putXMLSerializable(String key, XMLSerializable value) throws Exception;

   /**
    * This is for rename transformation,
    * save document directly insteadof parse -> write to improve performance.
    *
    * @param key the asset identifier.
    * @param doc the document after transformation.
    * @param className the asset sheet class name.
    */
   default void putDocument(String key, Document doc, String className) {
      putDocument(key, doc, className, null);
   }

   /**
    * This is for rename transformation,
    * save document directly insteadof parse -> write to improve performance.
    *
    * @param key the asset identifier.
    * @param doc the document after transformation.
    * @param className the asset sheet class name.
    * @param orgID the target organization id.
    */
   void putDocument(String key, Document doc, String className, String orgID);

   /**
    * This is for rename transformation.
    * Get the asset document, and transformation document directly to improve performance.
    * @param key the asset identifier.
    * @return the asset document.
    */
   default Document getDocument(String key) {
      return getDocument(key, null);
   }

   /**
    * This is for rename transformation.
    * Get the asset document, and transformation document directly to improve performance.
    * @param key the asset identifier.
    * @return the asset document.
    */
   Document getDocument(String key, String orgID);

   /**
    * Gets the data value that is associated with the specified key.
    *
    * @param key the data key.
    *
    * @return the associated value.
    * @throws Exception if get Serializable value failed.
    */
   Object getSerializable(String key) throws Exception;

   /**
    * Get the data length.
    *
    * @param key the data key.
    *
    * @return the data length.
    */
   long getDataLength(String key);

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws Exception if put Serializable value failed.
    */
   void putSerializable(String key, Serializable value) throws Exception;

   /**
    * Remove the data for this key from this storage if present.
    * @param key key whose data is to be removed from the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   boolean remove(String key);

   /**
    * Remove the data for this key from this storage if present.
    * @param key key whose data is to be removed from the storage.
    * @param isDatasource <tt>true</tt> if it's remove datasource, <tt>false</tt> otherwise.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   default boolean remove(String key, boolean isDatasource) {
      return this.remove(key);
   }

   /**
    * Rename a key.
    * @param okey the specified old key.
    * @param nkey the specified new key.
    * @param overwrite <tt>true</tt> to overwrite new key if exists,
    * <tt>false</tt> otherwise.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   boolean rename(String okey, String nkey, boolean overwrite);

   /**
    * Get the size.
    * @return the size of the storage, <tt>-1</tt> if exception occurs.
    */
   long size();

   /**
    * Add a refresh listener.
    * @param listener the specified refresh listener.
    * @deprecated use {@link #addStorageRefreshListener(StorageRefreshListener)} instead.
    */
   @Deprecated
   void addRefreshedListener(PropertyChangeListener listener);

   /**
    * Remove a refresh listener.
    * @param listener the specified refresh listener.
    * @deprecated use {@link #removeStorageRefreshListener(StorageRefreshListener)} instead.
    */
   @Deprecated
   void removeRefreshedListener(PropertyChangeListener listener);

   /**
    * Adds a listener that is notified when the contents of the storage have
    * been modified.
    *
    * @param l the listener to add.
    *
    * @since 12.1
    */
   void addStorageRefreshListener(StorageRefreshListener l);

   /**
    * Removes a listener from the notification list.
    *
    * @param l the listener to remove.
    *
    * @since 12.1
    */
   void removeStorageRefreshListener(StorageRefreshListener l);

   /**
    * Add transform listener.
    */
   void addTransformListener(TransformListener listener);

   /**
    * Remove transform listener.
    */
   void removeTransformListener(TransformListener listener);

   /**
    * Get last modified timestamp.
    */
   long lastModified();

   /**
    * Gets the last modified timestamp for entry with the specified key.
    *
    * @param key the key for the entry.
    * @return the last modified timestamp, or 0 if the entry doesn't exist.
    */
   long lastModified(String key);

   /**
    * Gets the last modified timestamp for entry with the specified key.
    *
    * @param key the key for the entry.
    * @param orgID the orgID of the storage to check.
    * @return the last modified timestamp, or 0 if the entry doesn't exist.
    */
   long lastModified(String key, String orgID);

   /**
    * Gets the most recent timestamp for the entries with keys that match the
    * specified filter. If the filter is <tt>null</tt>, all entries are used.
    *
    * @param filter the filter.
    *
    * @return the last modified timestamp.
    */
   long lastModified(Filter filter);

   /**
    * Gets the last modified timestamps for the entries with keys that match the
    * specified filter. If the filter is <tt>null</tt>, all entries are
    * returned.
    *
    * @param filter the filter.
    *
    * @return a map of keys to the corresponding last modified timestamp.
    */
   Map<String, Long> getTimestamps(Filter filter);

   /**
    * Gets the last modified timestamps that are greater than or equal to a
    * value for the entries with keys that match the specified filter. If the
    * filter is <tt>null</tt>, the timestamps for all entries are checked.
    *
    * @param filter the filter.
    * @param from   the minimum (inclusive) timestamp to return.
    *
    * @return a map of keys to the corresponding last modified timestamp.
    */
   Map<String, Long> getTimestamps(Filter filter, long from);

   /**
    * Gets the list of keys in this indexed storage. If the filter is specified,
    * it is used to determine which keys are returned. If the filter is
    * <tt>null</tt>, all keys will be returned.
    *
    * @param filter the filter, may be <tt>null</tt>.
    *
    * @return the matching keys.
    */
   Set<String> getKeys(Filter filter);

   /**
    * Gets the list of keys in this indexed storage. If the filter is specified,
    * it is used to determine which keys are returned. If the filter is
    * <tt>null</tt>, all keys will be returned.
    *
    * @param filter the filter, may be <tt>null</tt>.
    * @param orgID the orgID of the storage to check.
    *
    * @return the matching keys.
    */
   Set<String> getKeys(Filter filter, String orgID);

   /**
    * Checks if the indexed storage has been initialized for use in other singletons.
    * In the case for blob storage, it checks if the specific orgID's storage has been initialized.
    * Used to lazy load the initialization for RepletEngine and the registry singletons.
    *
    * @param orgID the orgID of the storage to check.
    *
    * @return flag indicating if the storage has been initialized
    */
   boolean isInitialized(String orgID);

   void setInitialized(String orgID);

   /**
    * Copies data over from one store to another
    *
    * @param oorg the oorg organization
    * @param norg the norg organization
    *
    * @since 14.0
    */
   void migrateStorageData(AbstractIdentity oorg, AbstractIdentity norg) throws Exception;

   /**
    * Copies data over from one store to another
    *
    * @param oname the oname
    * @param nname the nname
    *
    * @since 14.0
    */
   void migrateStorageData(String  oname, String nname) throws Exception;

   /**
    * Copies data over from one store to another
    *
    * @param oOrg the oOrg used for the old storage
    * @param nOrg the nOrg used for the old storage
    *
    * @since 14.0
    */
   void copyStorageData(Organization oOrg, Organization nOrg) throws Exception;

   /**
    * Deletes an indexed storage store
    *
    * @param orgID the orgId used by the storage to delete
    *
    * @since 14.0
    */
   void removeStorage(String orgID) throws Exception;

   /**
    * Gets the singleton instance of the indexed storage.
    *
    * @return the storage instance.
    */
   static IndexedStorage getIndexedStorage() {
      return SingletonManager.getInstance(IndexedStorage.class);
   }

   /**
    * Interface used to filter the keys listed by the indexed storage.
    */
   @FunctionalInterface
   interface Filter {
      /**
       * Determines if the specified key matches the filter criteria.
       *
       * @param key the key to check.
       *
       * @return <tt>true</tt> if the key matches; <tt>false</tt> otherwise.
       */
      boolean accept(String key);
   }

   class Reference extends SingletonManager.Reference<IndexedStorage> {
      @Override
      public IndexedStorage get(Object... parameters) {
         if(indexedStorage == null) {
            indexedStorage = new BlobIndexedStorage();
         }

         return indexedStorage;
      }

      @Override
      public void dispose() {
         if(indexedStorage != null) {
            indexedStorage.dispose();
            indexedStorage = null;
         }
      }

      private IndexedStorage indexedStorage;
   }
}

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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Indexed storage wrapper.
 *
 * @version 8.0, 6/2/2005
 * @author InetSoft Technology Corp
 */
public class IndexedStorageWrapper implements IndexedStorage {
   /**
    * Constructor.
    */
   public IndexedStorageWrapper(IndexedStorage storage) {
      this.storage = storage;
      this.listeners = new ArrayList<>();
   }

   /**
    * Remove all key-data mappings from storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   @Override
   public boolean clear() {
      boolean result = storage.clear();

      if(result) {
         fireEvent();
      }

      return result;
   }

   /**
    * Close the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   @Override
   public boolean close() {
      return storage.close();
   }

   @Override
   public void dispose() {
      storage.dispose();
   }

   /**
    * Check if the storage containe the key or not.
    * @param key the specified key
    * @return <tt>true</tt> if the storage containe the key,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean contains(String key) {
      return storage.contains(key);
   }

   @Override
   public boolean contains(String key, String orgID) {
      return storage.contains(key, orgID);
   }

   /**
    * Gets the XMLSerializable value that is associated with the specified key.
    *
    * @param key the data key.
    * @param trans transformation before parsing.
    * @return the associated value.
    * @throws Exception if get XMLSerializable value failed.
    */
   @Override
   public XMLSerializable getXMLSerializable(String key, TransformListener trans)
      throws Exception
   {
      return storage.getXMLSerializable(key, trans);
   }

   @Override
   public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID)
      throws Exception
   {
      return storage.getXMLSerializable(key, trans, orgID);
   }

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws Exception if put XMLSerializable value failed.
    */
   @Override
   public void putXMLSerializable(String key, XMLSerializable value)
      throws Exception
   {
      storage.putXMLSerializable(key, value);
      fireEvent();
   }

   /**
    * This is for rename transformation,
    * save document directly insteadof parse -> write to improve performance.
    *
    * @param key the asset identifier.
    * @param doc the document after transformation.
    * @param className the asset sheet class name.
    * @throws Exception
    */
   @Override
   public void putDocument(String key, Document doc, String className) {
      storage.putDocument(key, doc, className);
   }

   /**
    * This is for rename transformation.
    * Get the asset document, and transformation document directly to improve performance.
    * @param key the asset identifier.
    * @return the asset document.
    */
   @Override
   public Document getDocument(String key) {
      return storage.getDocument(key);
   }

   /**
    * Gets the data value that is associated with the specified key.
    *
    * @param key the data key.
    *
    * @return the associated value.
    * @throws Exception if get Serializable value failed.
    */
   @Override
   public Object getSerializable(String key) throws Exception {
      return storage.getSerializable(key);
   }

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws Exception if put Serializable value failed.
    */
   @Override
   public void putSerializable(String key, Serializable value)
      throws Exception
   {
      storage.putSerializable(key, value);
      fireEvent();
   }

   /**
    * Get the data length.
    *
    * @param key the data key.
    *
    * @return the data length.
    */
   @Override
   public long getDataLength(String key) {
      return storage.getDataLength(key);
   }

   /**
    * Remove the data for this key from this storage if present.
    * @param key key whose data is to be removed from the storage.
    * @return <tt>true</tt> if success, <tt>false</tt> otherwise.
    */
   @Override
   public boolean remove(String key) {
      boolean result = storage.remove(key);

      if(result) {
         fireEvent();
      }

      return result;
   }

   /**
    * Rename a key.
    * @param okey the specified old key.
    * @param nkey the specified new key.
    * @param overwrite <tt>true</tt> to overwrite new key if exists,
    * <tt>false</tt> otherwise.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean rename(String okey, String nkey, boolean overwrite) {
      boolean result = storage.rename(okey, nkey, overwrite);

      if(result) {
         fireEvent();
      }

      return result;
   }

   /**
    * Get the size.
    * @return the size of the storage, <tt>-1</tt> if exception occurs.
    */
   @Override
   public long size() {
      return storage.size();
   }

   /**
    * Add a change listener which will be notified when storage data changes.
    * @param listener the specified change listener.
    */
   public void addChangeListener(ChangeListener listener) {
      for(WeakReference<ChangeListener> ref : listeners) {
         if(listener.equals(ref.get())) {
            return;
         }
      }

      WeakReference<ChangeListener> ref = new WeakReference<>(listener);
      listeners.add(ref);
   }

   /**
    * Remove a change listener.
    * @param listener the specified change listener.
    */
   public void removeChangeListener(ChangeListener listener) {
      for(Iterator<WeakReference<ChangeListener>> i = listeners.iterator();
          i.hasNext();)
      {
         WeakReference<ChangeListener> ref = i.next();

         if(listener.equals(ref.get())) {
            i.remove();
            break;
         }
      }
   }

   /**
    * Fire event.
    */
   private void fireEvent() {
      ChangeEvent event = new ChangeEvent(this);

      for(Iterator<WeakReference<ChangeListener>> i = listeners.iterator();
          i.hasNext();)
      {
         WeakReference<ChangeListener> ref = i.next();
         ChangeListener listener = ref.get();

         if(listener == null) {
            i.remove();
         }
         else {
            try {
               listener.stateChanged(event);
            }
            catch(Exception ex) {
               LOG.error("State chane listener error: " +
                  listener, ex);
            }
         }
      }
   }

   /**
    * Add a refresh listener.
    * @param listener the specified refresh listener.
    */
   @Override
   @SuppressWarnings("deprecation")
   public void addRefreshedListener(PropertyChangeListener listener) {
      storage.addRefreshedListener(listener);
   }

   /**
    * Remove a refresh listener.
    * @param listener the specified refresh listener.
    */
   @Override
   @SuppressWarnings("deprecation")
   public void removeRefreshedListener(PropertyChangeListener listener) {
      storage.removeRefreshedListener(listener);
   }

   @Override
   public void addStorageRefreshListener(StorageRefreshListener l) {
      storage.addStorageRefreshListener(l);
   }

   @Override
   public void removeStorageRefreshListener(StorageRefreshListener l) {
      storage.removeStorageRefreshListener(l);
   }

   /**
    * Add transform listener.
    */
   @Override
   public void addTransformListener(TransformListener listener) {
      storage.addTransformListener(listener);
   }

   /**
    * Remove transform listener.
    */
   @Override
   public void removeTransformListener(TransformListener listener) {
      storage.removeTransformListener(listener);
   }

   /**
    * Get last modified timestamp.
    * @return last modified timestamp.
    */
   @Override
   public long lastModified() {
      return storage.lastModified();
   }

   @Override
   public long lastModified(String key) {
      return storage.lastModified(key);
   }

   @Override
   public long lastModified(String key, String orgID) {
      return storage.lastModified(key, orgID);
   }

   @Override
   public long lastModified(Filter filter) {
      return storage.lastModified(filter);
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter) {
      return storage.getTimestamps(filter);
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter, long from) {
      return storage.getTimestamps(filter, from);
   }

   @Override
   public Set<String> getKeys(Filter filter) {
      return storage.getKeys(filter);
   }

   @Override
   public Set<String> getKeys(Filter filter, String orgID) {
      return storage.getKeys(filter, orgID);
   }

   @Override
   public boolean isInitialized(String orgID) {
      return storage.isInitialized(orgID);
   }

   @Override
   public void setInitialized(String orgID) {
      storage.setInitialized(orgID);
   }

   @Override
   public void migrateStorageData(String oId, String nId) throws Exception {
      storage.migrateStorageData(oId, nId);
   }

   @Override
   public void copyStorageData(String oId, String nId) throws Exception {
      storage.copyStorageData(oId, nId);
   }

   @Override
   public void removeStorage(String orgID) throws Exception  {
      storage.removeStorage(orgID);
   }

   private IndexedStorage storage;
   private List<WeakReference<ChangeListener>> listeners;

   private static final Logger LOG =
      LoggerFactory.getLogger(IndexedStorageWrapper.class);
}

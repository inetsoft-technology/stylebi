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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.util.Drivers;
import inetsoft.web.AutoSaveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

/**
 * <tt>AbstractIndexedStorage</tt> implements the common APIs of
 * <tt>IndexedStorage</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AbstractIndexedStorage implements IndexedStorage {
   @Override
   public final void addRefreshedListener(final PropertyChangeListener listener) {
      addStorageRefreshListener(new RefreshedListener(listener));
   }

   @Override
   public final void removeRefreshedListener(PropertyChangeListener listener) {
      // this works because hashCode() and equals() are delegated to the
      // the property change listener
      removeStorageRefreshListener(new RefreshedListener(listener));
   }

   @Override
   public void addStorageRefreshListener(StorageRefreshListener l) {
      refreshListeners.add(l);
   }

   @Override
   public void removeStorageRefreshListener(StorageRefreshListener l) {
      refreshListeners.remove(l);
   }

   /**
    * Fire event.
    */
   protected void fireEvent(AssetEntry entry) {
      StorageRefreshEvent event = null;
      Set<StorageRefreshListener> refreshListeners = new LinkedHashSet<>(this.refreshListeners);

      for(StorageRefreshListener listener : refreshListeners) {
         if(event == null) {
            List<TimestampIndexChange> changes = new ArrayList<>();

            if(entry != null) {
               changes.add(new TimestampIndexChange(entry.toIdentifier(),
                  TimestampIndexChangeType.ADD));
            }
            
            event = new StorageRefreshEvent(this, lastModified(), changes);
         }

         listener.storageRefreshed(event);
      }
   }

   /**
    * Return the data's length to which the specified key is mapped.
    */
   @Override
   public long getDataLength(String key) {
      long result = 0L;

      try {
         byte[] data = get(key);

         if(data != null) {
            result = data.length;
         }

         return result;
      }
      catch(Exception e) {
         LOG.error(
                     "Failed to get indexed storage data length", e);
      }

      return result;
   }

   /**
    * Return the value's length.
    */
   public static long getXMLSerializableDataLength(XMLSerializable value, String key) {
      long result = 0L;
      byte[] data = encodeXMLSerializable(value, key);

      if(data != null) {
         result = data.length;
      }

      return result;
   }

   /**
    * Return the data to which the specified key is mapped in this storage.
    * @param key key whose associated data is to be returned.
    * @return the data to which this map maps the specified key, or
    * <tt>null</tt> if the storage contains no mapping for this key.
    * @throws Exception if get value object failed.
    */
   public abstract byte[] get(String key) throws Exception;

   /**
    * Add the specified data with the specified key in this storage
    * If the storage previously contained a data for this key, the old data is
    * replaced by the specified data.
    * @param key key with which the specified data is to be associated.
    * @param value data to be associated with the specified key.
    * @throws Exception if put key-value pair failed.
    */
   protected abstract void put(String key, byte[] value) throws Exception;

   @Override
   public boolean contains(String key, String orgID) {
      return contains(key);
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
   public XMLSerializable getXMLSerializable(String key, TransformListener trans) throws Exception {
      // @by davyc, getXMLSerializable and encodeXMLSerializable may be need
      // swap for large data, but current now seems no problem for large
      // embedded table
      byte[] data;
      XMLSerializable result = null;
      AssetEntry entry = AssetEntry.createAssetEntry(key);

      try {
         data = get(key);

         if(data != null) {
            // @by ChrisSpagnoli bug1412273657631 #2 2014-10-30
            result = getXMLSerializable(data, trans, entry != null ? entry.getName() : null);
         }
      }
      catch(Exception ex) {
         AssetEntry.Type type = entry == null ? null : entry.getType();
         String path = entry == null ? null : entry.getPath();
         String user = entry == null ? null : entry.getUser().name;
         throw new Exception(
            "Failed to read asset: type=" + type + ", path=" + path + ", owner=" + user, ex);
      }

      return result;
   }

   @Override
   public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID) throws Exception {
      return  this.getXMLSerializable(key, trans);
   }

   /**
    * Gets the XMLSerializable value that is associated with the specified key.
    */
   private XMLSerializable getXMLSerializable(byte[] data, TransformListener trans,
                                              String assetPath) throws Exception
   {
      Map<String, Object> map = parseData(data);

      if(map.get("cname") == null) {
         throw new MissingAssetClassNameException(
            "Asset class name not specified in file header or processing instruction");
      }

      Object obj = map.get("doc");

      if(obj == null) {
         return null;
      }

      String cname = (String) map.get("cname");
      Document doc = (Document) obj;
      XMLSerializable result = (XMLSerializable)
         Drivers.getInstance().getDriverClass(cname).getConstructor().newInstance();

      if(trans != null) {
         trans.transform(doc, cname);
      }

      for(TransformListener listener : listeners) {
         listener.transform(doc, cname, assetPath, trans);
      }

      Element element = doc.getDocumentElement();
      result.parseXML(element);

      return result;
   }

   // Extract classname from processing instruction
   private String getClassName(Node node) {
      // optimization, xpath is expensive
      NodeList list = node.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         Node currentNode = list.item(i);
         String cname = getClassName(currentNode);

         if(cname != null) {
            return cname;
         }
      }

      if(node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
         String str = node.getTextContent();
         int cidx = str.indexOf("classname=\"");

         if(cidx >= 0) {
            str = str.substring(11);
            return str.substring(0, str.indexOf('"'));
         }
      }

      return null;
   }

   /**
    * Add transform listener.
    */
   @Override
   public void addTransformListener(TransformListener listener) {
      listeners.add(listener);
   }

   /**
    * Remove transform listener.
    */
   @Override
   public void removeTransformListener(TransformListener listener) {
      listeners.remove(listener);
   }

   @Override
   public long lastModified(String key, String orgID) {
      return lastModified(key);
   }

   /**
    * Gets the list of transform listeners.
    */
   protected List<TransformListener> getTransformListeners() {
      return Collections.unmodifiableList(listeners);
   }

   /**
    * Adds a data value that is associated with the specified key.
    *
    * @param key the data key.
    * @param value the value to add.
    *
    * @throws Exception if put XMLSerializable failed.
    */
   @Override
   public void putXMLSerializable(String key, XMLSerializable value)
      throws Exception
   {
      byte[] data = encodeXMLSerializable(value, key);

      if(data != null) {
         put(key, data);
      }
   }

   /**
    * This is for rename transformation,
    * save document directly insteadof parse -> write to improve performance.
    *
    * @param key the asset identifier.
    * @param doc the document after transformation.
    * @param className the asset sheet class name.
    */
   @Override
   public void putDocument(String key, Document doc, String className) {
      try {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();
         XMLTool.writeAssets(doc, bout, className, key);
         byte[] data = bout.toByteArray();
         put(key, data);
      }
      catch(Exception exc) {
         LOG.error("Failed to write to indexed storage: {}", key, exc);
      }
   }

   /**
    * This is for rename transformation.
    * Get the asset document, and transformation document directly to improve performance.
    * @param key the asset identifier.
    * @return the asset document.
    */
   @Override
   public Document getDocument(String key) {
      try {
         byte[] data = get(key);
         Map<String, Object> map = parseData(data);
         Object obj = map != null ? map.get("doc") : null;
         return obj == null ? null : (Document) obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to get document.", ex);
      }

      return null;
   }

   private Map<String, Object> parseData(byte[] data) {
      if(data == null) {
         return null;
      }

      Map<String, Object> map = new HashMap<>();

      try {
         boolean bc = false;
         final ByteArrayInputStream in = new ByteArrayInputStream(data);
         in.mark(5);

         // see if file starts with <?xml
         if(in.read() != 0x3c || in.read() != 0x3f || in.read() != 0x78 ||
            in.read() != 0x6d || in.read() != 0x6c)
         {
            bc = true;
         }

         in.reset();

         String cname;
         Document doc;

         if(bc) {
            int len = in.read();
            byte[] chars = new byte[len];

            for(int i = 0; i < len; i++) {
               chars[i] = (byte) in.read();
            }

            cname = new String(chars, StandardCharsets.UTF_8);
            doc = Tool.parseXML(in, "UTF-8", false, false);
         }
         else {
            doc = Tool.parseXML(in, "UTF-8", false, false);
            cname = getClassName(doc);
         }

         map.put("cname", cname);
         map.put("doc", doc);
         in.close();
      }
      catch(Exception ex) {
         LOG.error("Failed to parse document.", ex);

         try {
            File temp = Files.createTempFile("xmlfile", "xml").toFile();

            try(OutputStream out = new FileOutputStream(temp)) {
               out.write(data);
            }

            LOG.error(">>> Offending XML file saved in: " + temp);
         }
         catch(IOException e) {
            LOG.error("Failed to write temp file: " + e, e);
         }
      }

      return map;
   }

   /**
    * Encodes an XMLSerializable data value.
    *
    * @param value    the value to encode.
    * @param key      the key to the value.
    * @return the encoded value.
    */
   public static byte[] encodeXMLSerializable(XMLSerializable value, String key) {
      byte[] result = null;

      if(value != null) {
         try {
            String cname = value.getClass().getName();

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8));

            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<?inetsoft-asset classname=\"" + cname +
                           "\" identifier=\"" + Tool.escape(key) + "\"?>");
            value.writeXML(writer);
            writer.flush();
            writer.close();

            result = bout.toByteArray();
         }
         catch(Exception exc) {
            LOG.error("Failed to write to indexed storage: {}", key, exc);
         }
      }

      return result;
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
      Object result = null;
      byte[] data = get(key);

      if(data != null) {
         result = new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
      }

      return result;
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
   public void putSerializable(String key, Serializable value) throws Exception
   {
      byte[] data = encodeSerializable(value);

      if(data != null) {
         put(key, data);
      }
   }

   @Override
   public Set<String> getKeys(Filter filter) {
      Set<String> result = new HashSet<>();
      IdentityID[] users = null;

      try {
         Class<?> clazz = Class.forName("inetsoft.sree.security.SecurityEngine");
         Method method = clazz.getMethod("getSecurity");
         Object engine = method.invoke(null);

         if(engine != null) {
            method = clazz.getMethod("getUsers");
            users = (IdentityID[]) method.invoke(engine);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to list users", e);
      }

      // import data sources
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, "/", null);
      getKeys(entry, result, null, filter);

      // import queries
      entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY_FOLDER, "/", null);
      getKeys(entry, result, null, filter);

      // import global worksheets
      entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, "/", null);
      getKeys(entry, result, null, filter);

      // import global viewsheets
      entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null);
      Set<String> visited = new HashSet<>();
      getKeys(entry, result, visited, filter);

      // import all users' worksheets & viewsheets
      if(users != null) {
         for(IdentityID user : users) {
            importBookmarks(filter, result, visited, user);

            XPrincipal principal = new XPrincipal(user);
            IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
            entry = AssetEntry.createUserRoot(principal);
            getKeys(entry, result, null, filter);

            entry = new AssetEntry(
               AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/",
               pId);
            Set<String> userVisited = new HashSet<>();
            getKeys(entry, result, userVisited, filter);

            importBookmarks(filter, result, userVisited, user);
         }
      }


      return result;
   }

   @Override
   public Set<String> getKeys(Filter filter, String orgID) {
      return this.getKeys(filter);
   }

   @Override
   public boolean isInitialized(String orgID) {
      return init;
   }

   @Override
   public void setInitialized(String orgID) {
      init = true;
   }

   @Override
   public void migrateStorageData(Organization oorg, Organization norg) throws Exception  {
      // no-op
   }
   @Override
   public void copyStorageData(String oId, String nId) throws Exception  {
      // no-op
   }

   @Override
   public void removeStorage(String orgID) throws Exception  {
      // no-op
   }

   private void importBookmarks(Filter filter, Set<String> result, Set<String> visited, IdentityID user)
   {
      processBookmarks(visited, user, entry -> {
         String id = entry.toIdentifier();

         if(contains(id) && (filter == null || filter.accept(id))) {
            result.add(id);
         }
      });
   }

   private static void processBookmarks(Set<String> ids, IdentityID user, Consumer<AssetEntry> fn) {
      for(String id : ids) {
         id = Tool.replaceAll(id, "^", "__");
         id = Tool.replaceAll(id, "/", "__");
         AssetEntry entry = new AssetEntry(
            AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET_BOOKMARK,
            id, user);
         fn.accept(entry);
      }
   }

   /**
    * Recursive method used to get all the keys in this storage.
    *
    * @param entry   the entry for the sub-tree to enumerate.
    * @param result  the set to which the matching keys will be added.
    * @param visited the set of visited leaf entries.
    * @param filter  the filter used to match the result keys.
    */
   private void getKeys(AssetEntry entry, Set<String> result,
                        Set<String> visited, Filter filter)
   {
      if(entry.isActualFolder() && entry.isValid()) {
         String identifier = entry.toIdentifier();

         if(contains(identifier)) {
            try {
               Object value = getXMLSerializable(identifier, null);

               if(value instanceof AssetFolder) {
                  if(filter == null || filter.accept(identifier)) {
                     result.add(identifier);
                  }

                  AssetFolder folder = (AssetFolder) value;
                  AssetEntry[] children = folder.getEntries();

                  if(children != null) {
                     for(AssetEntry child : children) {
                        identifier = child.toIdentifier();

                        if(contains(identifier)) {
                           if(filter == null || filter.accept(identifier)) {
                              result.add(identifier);
                           }

                           if(child.isActualFolder() &&
                              (child.isRoot() || !child.getName().equals("")))
                           {
                              getKeys(child, result, visited, filter);
                           }
                           else if(visited != null) {
                              visited.add(identifier);
                           }
                        }
                     }
                  }
               }
            }
            catch(Exception e) {
               LOG.warn("Failed to load folder", e);
            }
         }
      }
   }

   /**
    * Encodes a Serializable value into a compressed byte array.
    *
    * @param value the value to encode.
    *
    * @return the compressed data.
    */
   private static byte[] encodeSerializable(Serializable value) {
      byte[] result = null;

      if(value != null) {
         try {
            result = Tool.serialize(value);
         }
         catch(Exception exc) {
            LOG.error("Failed to encode serializable", exc);
         }
      }

      return result;
   }

   /**
    * Get the auto saved sheet.
    */
   public XMLSerializable getAutoSavedSheet(AssetEntry entry, Principal user)
      throws Exception
   {
      byte[] data = null;
      XMLSerializable sheet = null;
      ByteArrayOutputStream output = null;
      FileInputStream input = null;

      try {
         File file = AutoSaveUtils.getAutoSavedFile(entry, user);

         if(!file.exists()) {
            return null;
         }

         if(file.isFile()) {
            output = new ByteArrayOutputStream();
            input = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int len;

            while((len = input.read(buffer)) >= 0) {
               output.write(buffer, 0, len);
            }

            data = output.toByteArray();
         }

         if(data != null) {
            // @by ChrisSpagnoli bug1412273657631 #2 2014-10-30
            sheet = getXMLSerializable(data, null, entry.getName());
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read auto-saved sheet", ex);
      }
      finally {
         if(output != null) {
            output.close();
         }

         if(input != null) {
            input.close();
         }
      }

      return sheet;
   }

   private final class RefreshedListener implements StorageRefreshListener {
      RefreshedListener(PropertyChangeListener listener) {
         this.listener = listener;
      }

      @Override
      public void storageRefreshed(StorageRefreshEvent event) {
         listener.propertyChange(new PropertyChangeEvent(
            AbstractIndexedStorage.this, "IndexedStorage", null, null));
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         RefreshedListener that = (RefreshedListener) o;
         return listener.equals(that.listener);

      }

      @Override
      public int hashCode() {
         return listener.hashCode();
      }

      private final PropertyChangeListener listener;
   }

   private final Set<StorageRefreshListener> refreshListeners = new LinkedHashSet<>();
   private final List<TransformListener> listeners = new ArrayList<>();
   private boolean init = false;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractIndexedStorage.class);
}

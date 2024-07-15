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
package inetsoft.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.storage.KeyValuePair;
import inetsoft.storage.KeyValueStorage;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class that encapsulates a recycle bin.
 */
public class RecycleBin implements XMLSerializable, AutoCloseable {
   /**
    * Creates a new instance of <tt>RecycleBin</tt>.
    */
   public RecycleBin() {
      getStorage();
   }

   /**
    * Gets the shared instance of the recycle bin.
    *
    * @return the recycle bin.
    */
   public static RecycleBin getRecycleBin() {
      return SingletonManager.getInstance(RecycleBin.class);
   }

   /**
    * Get all paths in recycle bin.
    */
   public Collection<Entry> getEntries() {
      return getStorage().stream()
         .map(KeyValuePair::getValue)
         .collect(Collectors.toList());
   }

   /**
    * Adds an entry to this recycle bin.
    *
    * @param path          the path to the entry in the recycle bin.
    * @param originalPath  the original path of the deleted asset.
    * @param name          the original name of the deleted asset.
    * @param permission    the original permission on the deleted asset.
    * @param type          the original type of the deleted asset.
    * @param originalScope the original scope of the deleted asset.
    */
   public synchronized void addEntry(String path, String originalPath,
                                     String name, Permission permission, int type,
                                     int originalScope, IdentityID originalUser)
   {
      Entry entry = new Entry();
      entry.setPath(path);
      entry.setName(name);
      entry.setTimestamp(new Date(System.currentTimeMillis()));
      entry.setPermission(permission);
      entry.setType(type);
      entry.setOriginalUser(originalUser);
      entry.setOriginalPath(originalPath);
      entry.setOriginalScope(originalScope);

      try {
         getStorage().put(path, entry).get();
      }
      catch(Exception e) {
         LOG.error("Failed to add entry {}", path, e);
      }
   }

   /**
    * Rename the folder (in the original path).
    */
   public synchronized void renameFolder(String oldPath, String newPath) {
      Map<String, Entry> map = new HashMap<>();
      KeyValueStorage<Entry> storage = getStorage();
      storage.stream().forEach(p -> map.put(p.getKey(), p.getValue()));

      for(Map.Entry<String, Entry> e : map.entrySet()) {
         Entry entry = e.getValue();
         String origPath = entry.getOriginalPath();

         if(origPath.startsWith(oldPath + "/")) {
            origPath = newPath + origPath.substring(oldPath.length());
            entry.setOriginalPath(origPath);

            try {
               storage.put(e.getKey(), entry).get();
            }
            catch(Exception ex) {
               LOG.error("Failed to rename folder {} to {}", oldPath, newPath, e);
            }
         }
      }
   }

   /**
    * Get the original path of an item in recycle bin.
    */
   public synchronized String getOriginalPath(String path) {
      RecycleBin.Entry binEntry = getEntry(path);
      return binEntry != null ? binEntry.getOriginalPath() : path;
   }

   /**
    * Gets the meta-data for the item at the specified path.
    *
    * @param path the path to the entry in the recycle bin.
    *
    * @return the meta-data entry.
    */
   public synchronized Entry getEntry(String path) {
      return getStorage().get(path);
   }

   /**
    * Removes the entry at the specified path.
    *
    * @param path the path to the entry in the recycle bin.
    */
   public synchronized void removeEntry(String path) {
      getStorage().remove(path);
   }

   public void removeStorage(String orgID) throws Exception {
      getStorage(orgID).deleteStore();
      getStorage(orgID).close();
   }

   public void migrateStorageData(String oId, String id) throws Exception {
      KeyValueStorage<Entry> oStorage = getStorage(oId);
      KeyValueStorage<Entry> nStorage = getStorage(id);
      SortedMap<String, Entry> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
      removeStorage(oId);
   }

   public void copyStorageData(String oId, String id) {
      KeyValueStorage<Entry> oStorage = getStorage(oId);
      KeyValueStorage<Entry> nStorage = getStorage(id);
      SortedMap<String, Entry> data = new TreeMap<>();
      oStorage.stream().forEach(pair -> data.put(pair.getKey(), pair.getValue()));
      nStorage.putAll(data);
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      NodeList nodes = Tool.getChildNodesByTagName(tag, "entry");

      for(int i = 0; i < nodes.getLength(); i++) {
         Entry entry = new Entry();
         entry.parseXML((Element) nodes.item(i));
         getStorage().put(entry.getPath(), entry).get();
      }
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<recycleBin>");
      getStorage().stream().forEach(e -> e.getValue().writeXML(writer));
      writer.println("</recycleBin>");
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   private KeyValueStorage<Entry> getStorage() {
      return this.getStorage(null);
   }

   private KeyValueStorage<Entry> getStorage(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }
      else {
         orgID = orgID;
      }
      String storeID = orgID.toLowerCase() + "__" + "recyclebin";
      return SingletonManager.getInstance(KeyValueStorage.class, storeID);
   }

   private static final Logger LOG = LoggerFactory.getLogger(RecycleBin.class);

   public static final class Entry implements XMLSerializable, Serializable {
      /**
       * Gets the path to the entry in the recycle bin folder.
       *
       * @return the path.
       */
      public String getPath() {
         return path;
      }

      /**
       * Sets the path to the entry in the recycle bin folder.
       *
       * @param path the path.
       */
      public void setPath(String path) {
         this.path = path;
      }

      /**
       * Gets the original path to the deleted asset.
       *
       * @return the original path.
       */
      public String getOriginalPath() {
         return originalPath;
      }

      /**
       * Sets the original path to the deleted asset.
       *
       * @param originalPath the original path.
       */
      public void setOriginalPath(String originalPath) {
         this.originalPath = originalPath;
      }

      /**
       * Gets the original name to the deleted asset.
       *
       * @return the original name.
       */
      public String getName() {
         return name;
      }

      /**
       * Sets the original name to the deleted asset.
       *
       * @param name the original name.
       */
      public void setName(String name) {
         this.name = name;
      }

      /**
       * Gets the type to the deleted asset.
       *
       * @return the type.
       */
      public int getType() {
         return type;
      }

      /**
       * Sets the type to the deleted asset.
       *
       * @param type the original type of the RepositoryEntry
       */
      public void setType(int type) {
         this.type = type;
      }

      /**
       * Gets the date and time at which the asset was deleted.
       *
       * @return the timestamp.
       */
      @JsonFormat(shape = JsonFormat.Shape.STRING)
      public Date getTimestamp() {
         return timestamp;
      }

      /**
       * Sets the date and time at which the asset was deleted.
       *
       * @param timestamp the timestamp.
       */
      public void setTimestamp(Date timestamp) {
         this.timestamp = timestamp;
      }

      /**
       * Gets the permissions that were assigned to the deleted asset.
       *
       * @return the original permissions.
       */
      public Permission getPermission() {
         return permission;
      }

      /**
       * Sets the permissions that were assigned to the deleted asset.
       *
       * @param permission the original permissions.
       */
      public void setPermission(Permission permission) {
         this.permission = permission;
      }

      /**
       * Gets the scope of the deleted asset.
       *
       * @return the scope.
       */
      public int getOriginalScope() {
         return originalScope;
      }

      /**
       * Sets the scope of the deleted asset.
       *
       * @param originalScope the original scope.
       */
      public void setOriginalScope(int originalScope) {
         this.originalScope = originalScope;
      }

      /**
       * Get the original user of the deleted asset.
       */
      public IdentityID getOriginalUser() {
         return this.originalUser;
      }

      /**
       * Set the original user of the deleted asset.
       */
      public void setOriginalUser(IdentityID user) {
         this.originalUser = user;
      }

      @JsonIgnore
      public boolean isSheet() {
         return this.type == RepositoryEntry.VIEWSHEET ||
            this.type == RepositoryEntry.WORKSHEET;
      }

      @JsonIgnore
      public boolean isFolder() {
         return (this.type & RepositoryEntry.FOLDER) == RepositoryEntry.FOLDER;
      }

      @JsonIgnore
      public boolean isWSFolder() {
         return this.type == RepositoryEntry.WORKSHEET_FOLDER;
      }

      @JsonIgnore
      public boolean isPrototype() {
         return this.type == RepositoryEntry.PROTOTYPE;
      }

      @Override
      public void writeXML(PrintWriter writer) {
         writer.println("<entry>");

         if(getPath() != null) {
            writer.format("<path><![CDATA[%s]]></path>%n", getPath());
         }

         if(getOriginalPath() != null) {
            writer.format("<originalPath><![CDATA[%s]]></originalPath>%n", getOriginalPath());
         }

         if(getName() != null) {
            writer.format("<name><![CDATA[%s]]></name>%n", getName());
         }

         if(getTimestamp() != null) {
            writer.format("<timestamp>%d</timestamp>%n", getTimestamp().getTime());
         }

         if(getPermission() != null) {
            getPermission().writeXML(writer);
         }

         if(getType() != -1) {
            writer.format("<type><![CDATA[%d]]></type>%n", getType());
         }

         if(getOriginalScope() != -1) {
            writer.format("<originalScope><![CDATA[%d]]></originalScope>%n", getOriginalScope());
         }

         if(getOriginalUser() != null) {
            writer.format("<originalUser><![CDATA[%s]]></originalUser>%n", getOriginalUser().convertToKey());
         }

         writer.println("</entry>");
      }

      @Override
      public void parseXML(Element tag) throws Exception {
         Element element;

         if((element = Tool.getChildNodeByTagName(tag, "path")) != null) {
            setPath(Tool.getValue(element));
         }

         if((element = Tool.getChildNodeByTagName(tag, "originalPath")) != null) {
            setOriginalPath(Tool.getValue(element));
         }

         if((element = Tool.getChildNodeByTagName(tag, "name")) != null) {
            setName(Tool.getValue(element));
         }

         if((element = Tool.getChildNodeByTagName(tag, "timestamp")) != null) {
            setTimestamp(new Date(Long.parseLong(Tool.getValue(element))));
         }

         if((element = Tool.getChildNodeByTagName(tag, "permission")) != null) {
            Permission permission = new Permission();
            permission.parseXML(element);
            setPermission(permission);
         }

         if((element = Tool.getChildNodeByTagName(tag, "type")) != null) {
            setType(Integer.parseInt(Tool.getValue(element)));
         }

         if((element = Tool.getChildNodeByTagName(tag, "originalScope")) != null) {
            setOriginalScope(Integer.parseInt(Tool.getValue(element)));
         }

         if((element = Tool.getChildNodeByTagName(tag, "originalUser")) != null) {
            setOriginalUser(IdentityID.getIdentityIDFromKey(Tool.getValue(element)));
         }
      }

      private String path;
      private String name;
      private int type;
      private Permission permission;
      private Date timestamp;
      private IdentityID originalUser;
      private String originalPath;
      private int originalScope;
   }
}

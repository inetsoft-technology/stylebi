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
package inetsoft.mv;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class MVDefMap extends AbstractMap<String, MVDef> {
   MVDefMap() {
      try {
         indexedStorage = IndexedStorage.getIndexedStorage();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get indexed storage", e);
      }

      String file = "materializedviews.xml";
      Tool.lock("porting/" + file);

      try {
         DataSpace space = DataSpace.getDataSpace();
         if(space.exists(null, file)) {
            portMaterializedViewFile(file);
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to import legacy materialized view definitions", e);
      }
      finally {
         Tool.unlock("porting/" + file);
      }
   }

   @Override
   public boolean containsKey(Object key) {
      String identifier = getEntry((String) key).toIdentifier();
      return indexedStorage.contains(identifier);
   }

   @Override
   public MVDef get(Object key) {
      return get(key, null);
   }

   public MVDef get(Object key, String orgID) {
      MVDef mv = null;
      String identifier = getEntry((String) key, orgID).toIdentifier();
      long ts = indexedStorage.lastModified(identifier, orgID);
      MVDefWrapper wrapper = cache.get(identifier);

      if(wrapper != null && ts == wrapper.ts) {
         mv = wrapper.mv;
      }

      try {
         if(mv == null) {
            mv = (MVDef) indexedStorage.getXMLSerializable(identifier, null, orgID);
            cache.put(identifier, new MVDefWrapper(mv, ts));
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to load MV definition: " + key, e);
      }

      return mv;
   }

   @Override
   public MVDef put(String key, MVDef value) {
      MVDef oldValue = null;
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      try {
         AssetFolder folder = getRoot();
         AssetEntry entry = getEntry(key);

         if(folder.containsEntry(entry)) {
            entry = folder.getEntry(entry);
            folder.removeEntry(entry);
            oldValue = get(key);
         }

         folder.addEntry(entry);
         indexedStorage.putXMLSerializable(getRootIdentifier(), folder);
         indexedStorage.putXMLSerializable(entry.toIdentifier(), value);
         rootFolders.put(entry.getOrgID(), (AssetFolder) indexedStorage
            .getXMLSerializable(getRootIdentifier(), null, entry.getOrgID()));
         rootTS.put(orgID, indexedStorage.lastModified(getRootIdentifier()));
         cache.remove(entry.toIdentifier());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to save MV definition: " + key, e);
      }
      finally {
         indexedStorage.close();
      }

      return oldValue;
   }

   @Override
   public MVDef remove(Object key) {
      MVDef mv = get(key);
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(mv != null) {
         AssetEntry entry = getEntry((String) key);
         String identifier = entry.toIdentifier();

         try {
            AssetFolder root = getRoot();
            root.removeEntry(entry);
            indexedStorage.putXMLSerializable(getRootIdentifier(), root);
            indexedStorage.remove(identifier);
            rootFolders.put(orgID, (AssetFolder) indexedStorage
               .getXMLSerializable(getRootIdentifier(), null, orgID));
            rootTS.put(orgID, indexedStorage.lastModified(getRootIdentifier()));
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to remove MV definition: " + key, e);
         }
         finally {
            indexedStorage.close();
         }
      }

      return mv;
   }

   @Override
   public void clear() {
      try {
         AssetFolder root = getRoot();
         String orgID = OrganizationManager.getInstance().getCurrentOrgID();

         for(AssetEntry entry : root.getEntries()) {
            indexedStorage.remove(entry.toIdentifier());
            root.removeEntry(entry);
         }

         indexedStorage.putXMLSerializable(getRootIdentifier(), root);
         rootFolders.put(orgID, (AssetFolder) indexedStorage
            .getXMLSerializable(getRootIdentifier(), null, orgID));
         rootTS.put(orgID, indexedStorage.lastModified(getRootIdentifier()));
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to clear MV definitions", e);
      }
      finally {
         indexedStorage.close();
      }
   }

   @Override
   public Set<String> keySet() {
      return keySet(null);
   }

   public Set<String> keySet(String orgID) {
      try {
         return Arrays.stream(getRoot(orgID).getEntries())
            .map(AssetEntry::getName)
            .collect(Collectors.toSet());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed list MV definitions", e);
      }
   }

   @Override
   public Set<Entry<String, MVDef>> entrySet() {
      return new MVDefEntrySet();
   }

   @Override
   public int size() {
      try {
         int total = 0;

         for(String orgID : getOrgIDS()) {
            total += getRoot(orgID).size();
         }

         return total;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to list MV definitions", e);
      }
   }

   private void initRoot() throws Exception {
      try {
         if(!indexedStorage.contains(getRootIdentifier())) {
            indexedStorage.putXMLSerializable(getRootIdentifier(), new AssetFolder());
         }
      }
      finally {
         indexedStorage.close();
      }
   }

   public void initLastModified() {
      if(ThreadContext.getContextPrincipal() != null) {
         if (!this.rootTS.containsKey(OrganizationManager.getInstance().getCurrentOrgID())) {
            this.rootTS.put(OrganizationManager.getInstance().getCurrentOrgID(), indexedStorage.lastModified(getRootIdentifier()));
         }
      }
   }

   private void portMaterializedViewFile(String file) throws Exception {
      DataSpace space = DataSpace.getDataSpace();
      Document document;

      try {
         try(InputStream input = space.getInputStream(null, file)) {
            if(input == null) {
               return;
            }

            document = Tool.parseXML(input);
         }

         if(space.exists(null, file + ".bak")) {
            space.delete(null, file + ".bak");
         }

         space.rename(space.getPath(null, file), space.getPath(null, file + ".bak"));
      }
      catch(Exception e) {
         try {
            space.rename(space.getPath(null, file), space.getPath(null, file + ".corrupt"));

            if(!space.exists(null, file + ".bak")) {
               throw e;
            }

            LOG.warn("Corrupt {} file, loading from back up", file, e);

            try(InputStream input = space.getInputStream(null, file + ".bak")) {
               document = Tool.parseXML(input);
            }
         }
         catch(Exception e2) {
            space.rename(
               space.getPath(null, file + ".bak"), space.getPath(null, file + ".corrupt.2"));
            throw new Exception("Failed to load back up " + file, e2);
         }
      }

      NodeList nodes = Tool.getChildNodesByTagName(document.getDocumentElement(), "MVDef");
      AssetFolder root = getRoot();

      try {
         for(int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            MVDef mv = new MVDef();
            mv.parseXML(element);

            AssetEntry entry = getEntry(mv);
            root.addEntry(entry);
            indexedStorage.putXMLSerializable(entry.toIdentifier(), mv);
         }

         indexedStorage.putXMLSerializable(getRootIdentifier(), root);
      }
      finally {
         indexedStorage.close();
      }
   }

   private AssetFolder getRoot() throws Exception {
      return getRoot(null);
   }

   private AssetFolder getRoot(String orgID) throws Exception {
      if (orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      } else {
         orgID = orgID;
      }
      long ts = indexedStorage.lastModified(getRootIdentifier(orgID), orgID);
      long currts = rootTS.containsKey(orgID) ? rootTS.get(orgID) : 0;
      AssetFolder rootFolder = rootFolders.get(orgID);

      if(ts > currts || rootFolder == null) {
         if(!indexedStorage.contains(getRootIdentifier(orgID), orgID)) {
            indexedStorage.putXMLSerializable(getRootIdentifier(orgID), new AssetFolder());
         }

         rootFolders.put(orgID, (AssetFolder) indexedStorage.getXMLSerializable(getRootIdentifier(orgID), null, orgID));
         rootTS.put(orgID, ts);
      }

      return rootFolders.get(orgID);
   }

   private AssetEntry getRootEntry(String orgID) {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.MV_DEF_FOLDER, "/", null, orgID);
   }

   private String getRootIdentifier() {
      return getRootEntry(null).toIdentifier();
   }

   private String getRootIdentifier(String orgID) {
      return getRootEntry(orgID).toIdentifier();
   }

   private AssetEntry getEntry(MVDef mv) {
      return getEntry(mv.getName());
   }

   private AssetEntry getEntry(String name) {
      return getEntry(name, OrganizationManager.getInstance().getCurrentOrgID());
   }

   private AssetEntry getEntry(String name, String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.MV_DEF, name, null, orgID);
   }

   private String[] getOrgIDS() {
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      return Arrays.stream(provider.getOrganizations())
         .map(name -> provider.getOrganization(name).getId())
         .toArray(String[]::new);
   }

   private final IndexedStorage indexedStorage;
   private final Map<String, MVDefWrapper> cache = new ConcurrentHashMap<>();
   private ConcurrentHashMap<String, Long> rootTS = new ConcurrentHashMap<>();
   private ConcurrentHashMap<String, AssetFolder> rootFolders = new ConcurrentHashMap<>();

   private static final Logger LOG = LoggerFactory.getLogger(MVDefMap.class);

   private static final class MVDefWrapper {
      MVDefWrapper(MVDef mv, long ts) {
         this.mv = mv;
         this.ts = ts;
      }

      private final MVDef mv;
      private final long ts;
   }

   private final class MVDefEntry implements Map.Entry<String, MVDef> {
      MVDefEntry(String key) {
         this.key = key;
      }

      @Override
      public String getKey() {
         return key;
      }

      @Override
      public MVDef getValue() {
         return MVDefMap.this.get(key);
      }

      @Override
      public MVDef setValue(MVDef value) {
         return MVDefMap.this.put(key, value);
      }

      private final String key;
   }

   private final class MVDefEntryIterator implements Iterator<Map.Entry<String, MVDef>> {
      MVDefEntryIterator() {
         try {
            root = getRoot();
            entries = root.getEntries();
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to get MV definition folder", e);
         }
      }

      @Override
      public boolean hasNext() {
         boolean result = false;

         for(int i = index + 1; i < entries.length; i++)  {
            if(indexedStorage.contains(entries[i].toIdentifier())) {
               result = true;
               break;
            }
         }

         return result;
      }

      @Override
      public Entry<String, MVDef> next() {
         MVDefEntry entry = null;

         do {
            ++index;

            if(index < entries.length && indexedStorage.contains(entries[index].toIdentifier())) {
               entry = new MVDefEntry(entries[index].getName());
               removed = false;
            }
         }
         while(index < entries.length && entry == null);

         if(index >= entries.length) {
            throw new NoSuchElementException();
         }

         return entry;
      }

      @Override
      public void remove() {
         if(index < 0) {
            throw new IllegalStateException("next has not been called");
         }

         if(removed) {
            throw new IllegalStateException(
               "remove has already been called on the current element");
         }

         removed = true;
         AssetEntry entry = entries[index];
         MVDefMap.this.remove(entry.getName());
         root.removeEntry(entry);
      }

      private final AssetFolder root;
      private final AssetEntry[] entries;
      private int index = -1;
      private boolean removed = false;
   }

   private final class MVDefEntrySet extends AbstractSet<Map.Entry<String, MVDef>> {
      @Override
      public Iterator<Entry<String, MVDef>> iterator() {
         return new MVDefEntryIterator();
      }

      @Override
      public int size() {
         return MVDefMap.this.size();
      }
   }
}

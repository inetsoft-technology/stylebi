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

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.ProcessingInstruction;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code BlobIndexedStorage} is an implementation of {@link IndexedStorage}.
 */
public class BlobIndexedStorage extends AbstractIndexedStorage {
   /**
    * Creates a new instance of {@code BlobIndexedStorage}.
    */
   public BlobIndexedStorage() {
   }

   private BlobStorage<Metadata> getMetadataStorage(String orgID) {
      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      String storeID = orgID.toLowerCase() + "__" + "indexedStorage";
      return SingletonManager.getInstance(BlobStorage.class, storeID,
                                          true, changeListener);
   }
   @Override
   public XMLSerializable getXMLSerializable(String key, TransformListener trans) throws Exception {
      return this.getXMLSerializable(key, trans, null);
   }

   @Override
   public XMLSerializable getXMLSerializable(String key, TransformListener trans, String orgID) throws Exception {
      try {
         BlobStorage<Metadata> storage = getMetadataStorage(orgID);
         Metadata metadata = storage.getMetadata(key);
         AssetEntry entry = AssetEntry.createAssetEntry(key);

         if(metadata.getFolder() != null) {
            return (AssetFolder) metadata.getFolder().clone();
         }

         XMLSerializable value =
            (XMLSerializable) Class.forName(metadata.getClassName()).getConstructor().newInstance();

         try(InputStream input = storage.getInputStream(key)) {
            Document document = Tool.parseXML(input);

            if(trans != null) {
               trans.transform(document, metadata.getClassName());
            }

            for(TransformListener listener : getTransformListeners()) {
               listener.transform(
                  document, metadata.getClassName(), entry != null ? entry.getName() : null, trans);
            }

            value.parseXML(document.getDocumentElement());
         }

         return value;
      }
      catch(FileNotFoundException | NoSuchFileException ignore) {
         return null;
      }
   }

   /**
    * Get the proper last modified timestamp for the target resource.
    */
   private long getLastModified(String key, XMLSerializable value) {
      AssetEntry entry = AssetEntry.createAssetEntry(key);
      long lastModified = 0;

      if(entry.isViewsheet() || entry.isVSSnapshot()) {
         lastModified = ((Viewsheet) value).getLastModified();
      }

      if(entry.isWorksheet()) {
         lastModified = ((Worksheet) value).getLastModified();
      }

      // keep the modified timestamp when importing asset.
      if(lastModified > 0 && (System.currentTimeMillis() - lastModified) > 1000 * 60) {
         return lastModified;
      }

      return 0L;
   }

   @Override
   public void putXMLSerializable(String key, XMLSerializable value) throws Exception {
      Metadata metadata = new Metadata();
      metadata.setClassName(value.getClass().getName());
      metadata.setIdentifier(key);
      int orgIDIndex = StringUtils.ordinalIndexOf(key, "^", 4);
      String orgID = key.substring(orgIDIndex + 1);

      if(value instanceof AssetFolder) {
         metadata.setFolder((AssetFolder) value);
         getMetadataStorage(orgID).createDirectory(key, metadata);
      }
      else {
         try(BlobTransaction<Metadata> tx = getMetadataStorage(orgID).beginTransaction();
             OutputStream out =
                tx.newStream(key, metadata, null, getLastModified(key, value)))
         {
            PrintWriter writer =
               new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<?inetsoft-asset classname=\"" + metadata.getClassName() +
                              "\" identifier=\"" + key + "\"?>");
            value.writeXML(writer);
            writer.flush();
            tx.commit();
         }
      }
   }

   @Override
   public Document getDocument(String key) {
      try {
         Metadata metadata = getMetadataStorage(null).getMetadata(key);

         if(metadata.getFolder() != null) {
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            metadata.getFolder().writeXML(writer);
            writer.flush();
            return Tool.parseXML(new StringReader(buffer.toString()));
         }

         try(InputStream input = getMetadataStorage(null).getInputStream(key)) {
            return Tool.parseXML(input);
         }
      }
      catch(FileNotFoundException ignore) {
         return null;
      }
      catch(IOException | ParserConfigurationException e) {
         LOG.error("Failed to get document.", e);
         return null;
      }
   }

   @Override
   public void putDocument(String key, Document doc, String className) {
      Metadata metadata = new Metadata();
      metadata.setClassName(className);
      metadata.setIdentifier(key);

      try {
         Class<?> valueClass = Class.forName(className);

         if(AssetFolder.class.isAssignableFrom(valueClass)) {
            AssetFolder folder = (AssetFolder) valueClass.getConstructor().newInstance();
            folder.parseXML(doc.getDocumentElement());
            metadata.setFolder(folder);
            getMetadataStorage(null).createDirectory(key, metadata);
         }
         else {
            ProcessingInstruction pi = doc.createProcessingInstruction(
               "inetsoft-asset", "classname=\"" + className + "\" identifier=\"" + key + "\"");
            doc.insertBefore(pi, doc.getDocumentElement());

            try(BlobTransaction<Metadata> tx = getMetadataStorage(null).beginTransaction();
                OutputStream out = tx.newStream(key, metadata))
            {
               TransformerFactory factory = TransformerFactory.newInstance();

               try {
                  factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
               }
               catch(IllegalArgumentException e) {
                  LOG.debug("Transformer attribute {} not supported", XMLConstants.ACCESS_EXTERNAL_DTD);
               }

               try {
                  factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
               }
               catch(IllegalArgumentException e) {
                  LOG.debug(
                     "Transformer attribute {} not supported", XMLConstants.ACCESS_EXTERNAL_STYLESHEET);
               }

               DOMSource source = new DOMSource(doc);
               StreamResult result = new StreamResult(out);
               Transformer transformer = factory.newTransformer();
               transformer.transform(source, result);
               tx.commit();
            }
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to write to indexed storage: {}", key, exc);
      }
   }

   @Override
   public byte[] get(String key) throws Exception {
      try(InputStream input = getMetadataStorage(null).getInputStream(key)) {
         return IOUtils.toByteArray(input);
      }
      catch(FileNotFoundException ignore) {
         return null;
      }
   }

   @Override
   protected void put(String key, byte[] value) throws Exception {
      try(BlobTransaction<Metadata> tx = getMetadataStorage(null).beginTransaction();
          OutputStream out = tx.newStream(key, new Metadata()))
      {
         out.write(value);
         tx.commit();
      }
   }

   @Override
   public boolean clear() {
      // todo handle clearing the storage
      return false;
   }

   @Override
   public void dispose() {
      try {
         getMetadataStorage(null).close();
      }
      catch(Exception e) {
         LOG.warn("Failed to close blob storage", e);
      }
   }

   @Override
   public boolean contains(String key) {
      return contains(key, null);
   }

   @Override
   public boolean contains(String key, String orgID) {
      return getMetadataStorage(orgID).exists(key);
   }

   @Override
   public boolean remove(String key) {
      try {
         int orgIDIndex = StringUtils.ordinalIndexOf(key, "^", 4);
         String orgID = key.substring(orgIDIndex + 1);
         getMetadataStorage(orgID).delete(key);
         return true;
      }
      catch(FileNotFoundException ignore) {
         return false;
      }
      catch(IOException e) {
         LOG.error("Failed to delete {}", key, e);
         return false;
      }
   }

   @Override
   public boolean rename(String okey, String nkey, boolean overwrite) {
      if(!overwrite && getMetadataStorage(null).exists(nkey)) {
         return false;
      }

      try {
         getMetadataStorage(null).rename(okey, nkey);
         return true;
      }
      catch(IOException e) {
         LOG.error("Failed to rename {} to {}", okey, nkey, e);
         return false;
      }
   }

   @Override
   public long size() {
      return getMetadataStorage(null).stream()
         .mapToLong(Blob::getLength)
         .sum();
   }

   @Override
   public long lastModified() {
      long ts = 0;

      for(BlobStorage<Metadata> storage : getStorages()) {
         long nts = storage.getLastModified().toEpochMilli();

         if(ts < nts) {
            ts = nts;
         }
      }

      return ts;
   }

   @Override
   public long lastModified(String key) {
      return this.lastModified(key, null);
   }

   @Override
   public long lastModified(String key, String orgID) {
      try {
         return getMetadataStorage(orgID).getLastModified(key).toEpochMilli();
      }
      catch(FileNotFoundException ignore) {
         return 0;
      }
   }

   @Override
   public long lastModified(Filter filter) {
      long ts = 0;

      for(BlobStorage<Metadata> storage : getStorages()) {
         long nts = storage.stream()
            .filter(b -> filter == null || filter.accept(b.getPath()))
            .map(Blob::getLastModified)
            .mapToLong(Instant::toEpochMilli)
            .max()
            .orElse(0L);

         if(ts < nts) {
            ts = nts;
         }
      }

      return ts;
   }

   private List<BlobStorage<Metadata>> getStorages() {
      // Iterate through all available orgIDs
      ArrayList<BlobStorage<Metadata>> storages = new ArrayList<>();
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();

      for(String orgName : provider.getOrganizations()) {
         String orgID = provider.getOrganization(orgName).getId();

         String storeID = orgID.toLowerCase() + "__" + "indexedStorage";
         storages.add(SingletonManager.getInstance(BlobStorage.class, storeID,
                                             true, changeListener));
      }

      return storages;
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter) {
      Map<String, Long> timestamps = new HashMap<>();
      getMetadataStorage(null).stream()
         .filter(b -> filter == null || filter.accept(b.getPath()))
         .forEach(b -> timestamps.put(b.getPath(), b.getLastModified().toEpochMilli()));
      return timestamps;
   }

   @Override
   public Map<String, Long> getTimestamps(Filter filter, long from) {
      Map<String, Long> timestamps = new HashMap<>();
      Instant ts = Instant.ofEpochMilli(from);
      getMetadataStorage(null).stream()
         .filter(b -> filter == null || filter.accept(b.getPath()))
         .filter(b -> !b.getLastModified().isBefore(ts))
         .forEach(b -> timestamps.put(b.getPath(), b.getLastModified().toEpochMilli()));
      return timestamps;
   }

   @Override
   public Set<String> getKeys(Filter filter) {
      return this.getKeys(filter, null);
   }

   @Override
   public Set<String> getKeys(Filter filter, String orgID) {
      return getMetadataStorage(orgID).stream()
         .map(Blob::getPath)
         .filter(p -> filter == null || filter.accept(p))
         .collect(Collectors.toSet());
   }

   @Override
   public boolean isInitialized(String orgID) {
      return cachedOrgIDs.contains(orgID);
   }

   @Override
   public void setInitialized(String orgID) {
      cachedOrgIDs.add(orgID);
   }

   @Override
   public void migrateStorageData(Organization oorg, Organization norg) throws Exception  {
      String oId = oorg.getId();
      String nId = norg.getId();

      for(String key : getKeys(null, oId)) {
         AssetEntry entry = AssetEntry.createAssetEntry(key);
         entry = entry.cloneAssetEntry(nId);
         IdentityID user = entry.getUser();

         if(user != null && !Tool.equals(oorg.getName(), norg.getName())) {
            user.setOrganization(norg.getName());
         }

         String identifier = entry.toIdentifier();

         XMLSerializable data = getXMLSerializable(key, null, oId);

         if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
            migrateViewsheet(oorg, norg, ((Viewsheet) data));
         }
         else if(entry.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK) {
            String path = entry.getPath();
            int index = path.lastIndexOf("__");

            if(index != -1) {
               String str = path.substring(index + 2);

               if(Tool.equals(str, oId)) {
                  path = path .substring(0, index) + "__" + nId;
               }
            }

            entry.setPath(path);
            identifier = entry.toIdentifier(true);
         }
         else if(entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER ||
            entry.getType() == AssetEntry.Type.SCHEDULE_TASK_FOLDER ||
            (entry.getType() == AssetEntry.Type.DATA_SOURCE_FOLDER && data instanceof AssetFolder))
         {
            List<AssetEntry> newEntries = new ArrayList<>();
            AssetFolder folder = (AssetFolder) data;

            for(AssetEntry folderEntry : folder.getEntries()) {
               newEntries.add(folderEntry.cloneAssetEntry(nId));
               folder.removeEntry(folderEntry);
            }

            for(AssetEntry newEntry : newEntries) {
               folder.addEntry(newEntry);
            }
         }

         putXMLSerializable(identifier, data);
      }

      if(!Tool.equals(oId, nId)) {
         removeStorage(oId);
      }
   }

   private void migrateViewsheet(Organization oorg, Organization norg, Viewsheet viewsheet) {
      String oId = oorg.getId();
      String nId = norg.getId();
      String oname = oorg.getName();
      String nname = norg.getName();
      AssetEntry baseEntry = viewsheet.getBaseEntry();

      if(!Tool.equals(oId, nId) && baseEntry != null) {
         baseEntry = baseEntry.cloneAssetEntry(nId);
      }

      if(!Tool.equals(oname, nname) && baseEntry != null) {
         IdentityID user = baseEntry.getUser();

         if(user != null) {
            user.setOrganization(nname);
         }
      }

      viewsheet.setBaseEntry(baseEntry);
      MigrateUtil.updateAllAssemblyHyperlink(viewsheet, oorg, norg);
   }

   @Override
   public void copyStorageData(String oId, String nId) throws Exception  {
      for(String key : getKeys(null, oId)) {
         int orgIDIndex = StringUtils.ordinalIndexOf(key, "^", 4);
         orgIDIndex = orgIDIndex == -1 ? key.length() : orgIDIndex;
         String identifier = key.substring(0, orgIDIndex) + "^" + nId;
         AssetEntry entry = AssetEntry.createAssetEntry(key.substring(0, orgIDIndex) + "^" + nId);
         XMLSerializable data = getXMLSerializable(key, null, oId);

         if(entry.getType() == AssetEntry.Type.VIEWSHEET) {
            if(((Viewsheet) data).getBaseEntry() != null) {
               AssetEntry wentry = ((Viewsheet) data).getBaseEntry().cloneAssetEntry(nId);
               ((Viewsheet) data).setBaseEntry(wentry);
            }
         }
         else if(entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER) {
            List<AssetEntry> newEntries = new ArrayList<>();
            AssetFolder folder = (AssetFolder) data;

            for(AssetEntry folderEntry : folder.getEntries()) {
               newEntries.add(folderEntry.cloneAssetEntry(nId));
               folder.removeEntry(folderEntry);
            }

            for(AssetEntry newEntry : newEntries) {
               folder.addEntry(newEntry);
            }
         }

         putXMLSerializable(identifier, data);
      }
   }


   @Override
   public void removeStorage(String orgID) throws Exception  {
      getMetadataStorage(orgID).deleteBlobStorage();
      cachedOrgIDs.remove(orgID);
   }

   private Set<String> cachedOrgIDs = new HashSet<>();
   private static final Logger LOG = LoggerFactory.getLogger(BlobIndexedStorage.class);

   public static final class Metadata implements Serializable {
      public String getClassName() {
         return className;
      }

      public void setClassName(String className) {
         this.className = className;
      }

      public String getIdentifier() {
         return identifier;
      }

      public void setIdentifier(String identifier) {
         this.identifier = identifier;
      }

      public AssetFolder getFolder() {
         return folder;
      }

      public void setFolder(AssetFolder folder) {
         this.folder = folder;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }

         if(o == null || getClass() != o.getClass()) {
            return false;
         }

         Metadata metadata = (Metadata) o;
         return Objects.equals(className, metadata.className) &&
            Objects.equals(identifier, metadata.identifier) &&
            Objects.equals(folder, metadata.folder);
      }

      @Override
      public int hashCode() {
         return Objects.hash(className, identifier, folder);
      }

      @Override
      public String toString() {
         return "Metadata{" +
            "className='" + className + '\'' +
            ", identifier='" + identifier + '\'' +
            ", folder=" + folder +
            '}';
      }

      private String className;
      private String identifier;
      private AssetFolder folder;
   }

   private final BlobStorage.Listener<Metadata> changeListener = new BlobStorage.Listener<Metadata>() {
      @Override
      public void blobAdded(BlobStorage.Event<Metadata> event) {
         fireEvent0(event);
      }

      @Override
      public void blobUpdated(BlobStorage.Event<Metadata> event) {
         fireEvent0(event);
      }

      @Override
      public void blobRemoved(BlobStorage.Event<Metadata> event) {
         fireEvent0(event);
      }

      private void fireEvent0(BlobStorage.Event<Metadata> event) {
         Blob<Metadata> value = event.getOldValue();

         if(value != null && value.getPath() != null) {
            String mapName = event.getMapName();
            String orgID = null;

            if(mapName.startsWith("inetsoft.storage.kv.") && mapName.endsWith("__indexedStorage")) {
               orgID = mapName.substring(20, mapName.indexOf("__indexedStorage"));
            }

            Principal oldPrincipal = ThreadContext.getPrincipal();
            XPrincipal tempPrincipal = new XPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()), new IdentityID[0], new String[0], orgID);

            AssetEntry entry = AssetEntry.createAssetEntry(value.getPath(), orgID);
            if(entry != null && !entry.isViewsheet()) {
               ThreadContext.setPrincipal(tempPrincipal);
               fireEvent(entry);
               ThreadContext.setPrincipal(oldPrincipal);
            }
         }
      }
   };
}

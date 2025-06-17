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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.storage.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.migrate.*;
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
import java.util.concurrent.*;
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
                              "\" identifier=\"" + Tool.escape(key) + "\"?>");
            value.writeXML(writer);
            writer.flush();
            tx.commit();
         }
      }
   }

   @Override
   public Document getDocument(String key, String orgID) {
      try {
         Metadata metadata = getMetadataStorage(orgID).getMetadata(key);

         if(metadata.getFolder() != null) {
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            metadata.getFolder().writeXML(writer);
            writer.flush();
            return Tool.parseXML(new StringReader(buffer.toString()));
         }

         try(InputStream input = getMetadataStorage(orgID).getInputStream(key)) {
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
   public void putDocument(String key, Document doc, String className, String orgID) {
      Metadata metadata = new Metadata();
      metadata.setClassName(className);
      metadata.setIdentifier(key);

      try {
         Class<?> valueClass = Class.forName(className);

         if(AssetFolder.class.isAssignableFrom(valueClass)) {
            AssetFolder folder = (AssetFolder) valueClass.getConstructor().newInstance();
            folder.parseXML(doc.getDocumentElement());
            metadata.setFolder(folder);
            getMetadataStorage(orgID).createDirectory(key, metadata);
         }
         else {
            ProcessingInstruction pi = doc.createProcessingInstruction("inetsoft-asset",
               "classname=\"" + className + "\" identifier=\"" + Tool.escape(key) + "\"");
            doc.insertBefore(pi, doc.getDocumentElement());

            try(BlobTransaction<Metadata> tx = getMetadataStorage(orgID).beginTransaction();
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

   public byte[] get(String key, String orgID) throws Exception {
      try(InputStream input = getMetadataStorage(orgID).getInputStream(key)) {
         return IOUtils.toByteArray(input);
      }
      catch(FileNotFoundException ignore) {
         return null;
      }
   }

   @Override
   protected void put(String key, byte[] value, String orgID) throws Exception {
      try(BlobTransaction<Metadata> tx = getMetadataStorage(orgID).beginTransaction();
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

   public void listBlobs(String outputFile, String orgID)  throws IOException {
      BlobStorage<Metadata> storage = getMetadataStorage(orgID);

      if(storage != null) {
         storage.listBlobs(outputFile);
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

      for(String orgID : provider.getOrganizationIDs()) {
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
   public void migrateStorageData(AbstractIdentity oorg, AbstractIdentity norg) throws Exception  {
      migrateStorageData(oorg, norg, true, true);
   }

   @Override
   public void migrateStorageData(String oname, String nname) throws Exception {
      int numThreads = Runtime.getRuntime().availableProcessors();
      ExecutorService executor = Executors.newFixedThreadPool(numThreads);
      Organization currOrg = SecurityEngine.getSecurity().getSecurityProvider()
                              .getOrganization(OrganizationManager.getInstance().getCurrentOrgID());

      for(String key : getKeys(null)) {
         final AssetEntry entry = AssetEntry.createAssetEntry(key);
         boolean viewsheet = entry.isViewsheet() || entry.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK;

         if(entry.isScheduleTask() && !ScheduleManager.isInternalTask(entry.getName())) {
            executor.submit(() -> new MigrateScheduleTask(entry, oname, nname, currOrg).updateNameProcess());
         }
         else if(entry.getUser() != null && entry.getUser().name.equals(oname) || viewsheet) {
            if(viewsheet) {
               updateDependencySheet(oname, nname, key, executor);
               executor.submit(() -> new MigrateViewsheetTask(entry, oname, nname, currOrg).updateNameProcess());
            }
            else if(entry.isWorksheet()) {
               executor.submit(() -> new MigrateWorksheetTask(entry, oname, nname, currOrg).updateNameProcess());
            }
            else if(entry.isLogicModel()) {
               executor.submit(() -> new MigrateLogicalModelTask(entry, oname, nname, currOrg).updateNameProcess());
            }
            else if(entry.isDomain()) {
               executor.submit(() -> new MigrateCubeTask(entry, oname, nname, currOrg).updateNameProcess());
            }
            else if(entry.getType() == AssetEntry.Type.MV_DEF) {
               // done by mv manager.
            }
            else {
               XMLSerializable data = getXMLSerializable(key, null);
               AssetEntry nentry = entry.cloneAssetEntry(entry.getOrgID(), nname);
               fixRightUser(oname, nname, nentry);
               String identifier = nentry.toIdentifier();

               if(entry.isFolder() && data instanceof AssetFolder folder) {
                  List<AssetEntry> newEntries = new ArrayList<>();

                  for(AssetEntry folderEntry : folder.getEntries()) {
                     newEntries.add(folderEntry.cloneAssetEntry(folderEntry.getOrgID(), nname));
                     folder.removeEntry(folderEntry);
                  }

                  for(AssetEntry newEntry : newEntries) {
                     fixRightUser(oname, nname, newEntry);
                     folder.addEntry(newEntry);
                  }

                  data = folder;
               }

               putXMLSerializable(identifier, data);
            }
         }
         else if((entry.getType().id() & AssetEntry.Type.FOLDER.id()) == AssetEntry.Type.FOLDER.id()) {
            XMLSerializable data = getXMLSerializable(key, null);

            if(entry.isFolder() && data instanceof AssetFolder folder) {
               for(AssetEntry folderEntry : folder.getEntries()) {
                  for(String favoriteUser : folderEntry.getFavoritesUsers()) {
                     if(favoriteUser.startsWith(oname + IdentityID.KEY_DELIMITER)) {
                        String newFavoriteUser = favoriteUser.replace(oname + IdentityID.KEY_DELIMITER,
                                                                      nname + IdentityID.KEY_DELIMITER);
                        folderEntry.deleteFavoritesUser(favoriteUser);
                        folderEntry.addFavoritesUser(newFavoriteUser);
                     }
                  }
               }
            }

            putXMLSerializable(entry.toIdentifier(), data);
         }
      }

      executor.shutdown();

      try {
         executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
      }
      catch(InterruptedException ignore) {
         LOG.error(Catalog.getCatalog().getString(
            "Failed to finish migrate storage from {0} to {1}", oname, nname));
      }
   }

   private void updateDependencySheet(String oname, String nname, String key,
                                      ExecutorService executor)
   {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         RenameTransformObject obj = service.get(key);

         if(!(obj instanceof DependenciesInfo) ||
            ((DependenciesInfo) obj).getDependencies() == null)
         {
            return;
         }

         DependenciesInfo info = (DependenciesInfo) obj;
         List<AssetObject> infos = info.getDependencies();

         for(AssetObject asset : infos) {
            if(!(asset instanceof AssetEntry entry)) {
               continue;
            }

            if(entry.isViewsheet()) {
               executor.submit(() -> new MigrateViewsheetTask(entry, oname, nname).updateNameProcess());
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to update the dependencies to file.", e);
      }
   }

   private void fixRightUser(String oname, String nname, AssetEntry entry) {
      if(Tool.equals(oname, entry.getCreatedUsername())) {
         entry.setCreatedUsername(nname);
      }

      if(Tool.equals(oname, entry.getModifiedUsername())) {
         entry.setModifiedUsername(nname);
      }
   }

   private void migrateStorageData(AbstractIdentity oorg, AbstractIdentity norg, boolean removeOld,
                                   boolean rename)
      throws Exception
   {
      String oId = oorg instanceof Organization ? ((Organization) oorg).getId() :
         OrganizationManager.getInstance().getCurrentOrgID();
      String nId = norg instanceof Organization ? ((Organization) norg).getId() :
         OrganizationManager.getInstance().getCurrentOrgID();
      int numThreads = Runtime.getRuntime().availableProcessors();
      ExecutorService executor = Executors.newFixedThreadPool(numThreads);

      for(String key : getKeys(null, oId)) {
         final AssetEntry entry = AssetEntry.createAssetEntry(key);

         if(entry.isViewsheet()) {
            executor.submit(() -> new MigrateViewsheetTask(entry, oorg, norg).process());
         }
         else if(entry.getType() == AssetEntry.Type.VIEWSHEET_BOOKMARK) {
            executor.submit(() -> new MigrateBookmarkTask(entry, oorg, norg).process());
         }
         else if(entry.isWorksheet()) {
            executor.submit(() -> new MigrateWorksheetTask(entry, oorg, norg).process());
         }
         else if(entry.isLogicModel()) {
            executor.submit(() -> new MigrateLogicalModelTask(entry, oorg, norg).process());
         }
         else if(entry.isDomain()) {
            executor.submit(() -> new MigrateCubeTask(entry, oorg, norg).process());
         }
         else if(entry.isScheduleTask() && !ScheduleManager.isInternalTask(entry.getName())) {
            XMLSerializable result = getXMLSerializable(key, null, oId);

            if(result instanceof ScheduleTask) {
               ScheduleTask task = (ScheduleTask) result;
               boolean usedTimeRange = task.getConditionStream()
                  .filter(cond -> cond instanceof TimeCondition && ((TimeCondition) cond).getTimeRange() != null)
                  .findFirst()
                  .isPresent();

               if(usedTimeRange) {
                  continue;
               }
            }

            executor.submit(() -> new MigrateScheduleTask(entry, oorg, norg).process());
         }
         else if(entry.getType() == AssetEntry.Type.MV_DEF || entry.getType() == AssetEntry.Type.MV_DEF_FOLDER) {
            // done by mv manager.
         }
         else if(norg instanceof Organization) {
            XMLSerializable data = getXMLSerializable(key, null, oId);

            if(entry.isFolder() && data instanceof AssetFolder) {
               List<AssetEntry> newEntries = new ArrayList<>();
               AssetFolder folder = (AssetFolder) data;

               for(AssetEntry folderEntry : folder.getEntries()) {
                  newEntries.add(folderEntry.cloneAssetEntry((Organization) norg));
                  folder.removeEntry(folderEntry);
               }

               for(AssetEntry newEntry : newEntries) {
                  if(!rename) {
                     newEntry.clearFavoritesUser();
                  }

                  folder.addEntry(newEntry);
               }
            }

            AssetEntry nentry = entry.cloneAssetEntry((Organization) norg);
            String identifier = nentry.toIdentifier();
            putXMLSerializable(identifier, data);
         }
      }

      executor.shutdown();

      try {
         executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
      }
      catch(InterruptedException ignore) {
         LOG.error(Catalog.getCatalog().getString(
            "Failed to finish migrate storage from {0} to {1}", oorg, norg));
      }

      if(removeOld && !Tool.equals(oId, nId)) {
         removeStorage(oId);
      }
   }

   @Override
   public void copyStorageData(Organization oOrg, Organization nOrg, boolean rename) throws Exception {
      migrateStorageData(oOrg, nOrg, false, rename);
   }

   @Override
   public void removeStorage(String orgID) throws Exception  {
      BlobStorage<Metadata> metadataStorage = getMetadataStorage(orgID);
      metadataStorage.deleteBlobStorage();
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
            XPrincipal tempPrincipal = new XPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()), new IdentityID[0], new String[0], orgID);

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

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
package inetsoft.uql.service;

import inetsoft.report.PropertyChangeEvent;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.*;
import inetsoft.uql.xmla.Domain;
import inetsoft.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Serializable;
import java.lang.SecurityException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data source registry stores information on all data sources. The
 * information is stored in a XML file. Applications should not use
 * the data source registry directly. The registry information can
 * be accessed through the XRepository service API.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class DataSourceRegistry implements MessageListener {
   /**
    * Get data source registry.
    * @return data source registry if any, null otherwise.
    */
   public static DataSourceRegistry getRegistry() {
      return ConfigurationContext.getContext().getSpringBean(DataSourceRegistry.class);
   }

   /**
    * Constructor.
    */
   public DataSourceRegistry(IndexedStorage indexedStorage, Config uqlConfig, Cluster cluster) throws Exception {
      this.indexedStorage = indexedStorage;
      this.uqlConfig = uqlConfig;
      this.cluster = cluster;
   }

   @PostConstruct
   public void setListeners() {
      initLastModified();
      indexedStorage.addStorageRefreshListener(this::fireEvent);

      // @by stephenwebster, For Align Kpital.
      // Using getRoot() here is problematic since the getObject method caches
      // null results.  If the registry needs to be ported, calling getRoot
      // again immediately after may result in getting the cached null result,
      // so the port will fail.  Instead, use a one-time check on the indexed
      // storage directly to see whether the root exists, which is done in the
      // initRoot method.  I have modified initRoot to return a boolean value
      // to indicate whether the root was setup or not.
      // This init method has been moved to the authentication service on login.
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void shutdown() {
      cluster.removeMessageListener(this);
      indexedStorage.removeStorageRefreshListener(this::fireEvent);
   }

   /**
    * Factory method for retrieving the correct IndexedStorage.
    */
   protected IndexedStorage initIndexedStorage() throws Exception {
      return IndexedStorage.getIndexedStorage();
   }

   public void initLastModified() {
      if(ThreadContext.getContextPrincipal() != null) {
         if(!this.ts.containsKey(OrganizationManager.getInstance().getCurrentOrgID())) {
            this.ts.put(OrganizationManager.getInstance().getCurrentOrgID(), indexedStorage.lastModified(datasourceFilter));
         }
      }
   }

   /**
    * Get last modified timestamp.
    * @return last modified timestamp.
    */
   public synchronized long lastModified() {
      return ts.get(OrganizationManager.getInstance().getCurrentOrgID());
   }

   /**
    * Parse Domain.
    */
   public synchronized void parseDomain(Element rnode) throws Exception {
      NodeList nlist = rnode.getElementsByTagName("Domain");

      for(int i = 0; i < nlist.getLength(); i++) {
         XDomainWrapper wrapper = new XDomainWrapper();
         wrapper.parseXML((Element) nlist.item(i));
         setDomain(wrapper.getDomain());
      }
   }

   /**
    * Parse XDataSource.
    */
   public synchronized void parseXDataSource(Element rnode, boolean isImport) throws Exception {
      NodeList nlist = rnode.getElementsByTagName("datasource");

      for(int i = 0; i < nlist.getLength(); i++) {
         XDataSource source = parseXDataSource2((Element) nlist.item(i), true);

         if(source == null) {
            continue;
         }

         String[] parents = source.getFullName().split("/");
         String parent = "";

         for(int j = 0; j < parents.length - 1; j++) {
            parent += parents[j]; //NOSONAR calling toString twice per loop is just as bad as concat

            // the data source at a parent path isn't removed with its additional connections,
            // and no folder is created beside it (Bug #77702)
            if(containObject(new AssetEntry(
               AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, parent, null)))
            {
               throw new MessageException(Catalog.getCatalog().getString(
                  "common.datasource.createUnderDataSource", source.getFullName(), parent));
            }

            DataSourceFolder folder = getDataSourceFolder(parent);

            if(folder == null) {
               LocalDateTime created;

               if(source.getCreated() != 0) {
                  Date date = new Date(source.getCreated());
                  created = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
               }
               else {
                  created = LocalDateTime.now();
               }

               folder = new DataSourceFolder(parent, created, source.getCreatedBy());
               setDataSourceFolder(folder);
            }

            parent += "/"; //NOSONAR calling toString twice per loop is just as bad as concat
         }

         setDataSource(source, isImport);
         parseDataModel(source.getFullName(), rnode);

         NodeList nlist0 = rnode.getElementsByTagName("additional");

         for(int j = 0; j < nlist0.getLength(); j++) {
            Element item = (Element) nlist0.item(j);
            AdditionalConnectionDataSource<?> additional =
               (AdditionalConnectionDataSource<?>) parseXDataSource2(item, true);
            String parentName = Tool.getAttribute(item, "parent");

            if(Objects.equals(parentName, source.getFullName())) {
               ((AdditionalConnectionDataSource<?>) source).addDatasource(additional);
            }
         }
      }
   }

   /**
    * Parse XDataSource.
    */
   @SuppressWarnings("UnusedParameters")
   public XDataSource parseXDataSource2(Element elem, boolean check) throws Exception {
      XDataSourceWrapper wrapper = new XDataSourceWrapper();
      wrapper.parseXML(elem);
      return wrapper.getSource();
   }

   /**
    * Gets the paths that are used by both a data source and a data source folder in the current
    * organization. Such data can't be created any more (Bug #77691), but may exist. The data
    * sources in such a folder are stored at the paths of additional connections of the data
    * source, so renaming or deleting one side is refused while the other side holds anything
    * (see {@link #checkDataSourcePathClash(String)}).
    *
    * @return the paths, sorted.
    */
   public List<String> getDataSourcePathClashes() {
      AssetFolder root = getRoot();

      if(root == null) {
         return Collections.emptyList();
      }

      Set<String> folders = new HashSet<>();

      for(AssetEntry entry : root.getEntries(AssetEntry.Type.DATA_SOURCE_FOLDER)) {
         folders.add(entry.getPath());
      }

      List<String> clashes = new ArrayList<>();

      for(AssetEntry entry : root.getEntries(AssetEntry.Type.DATA_SOURCE)) {
         String path = entry.getPath();

         // the root folder may list a missing asset (bug #60767)
         if(folders.contains(path) && containObject(entry) && containObject(new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null)))
         {
            clashes.add(path);
         }
      }

      Collections.sort(clashes);
      return clashes;
   }

   /**
    * Logs the paths used by both a data source and a data source folder, once for each
    * organization. They are reported, not repaired.
    */
   private void reportDataSourcePathClashes() {
      String orgID = null;

      try {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();

         if(orgID == null || !clashesReported.add(orgID)) {
            return;
         }

         List<String> clashes = getDataSourcePathClashes();

         if(!clashes.isEmpty()) {
            // renaming, moving or deleting one side is refused while the other side holds
            // anything under the path (Bug #77725), so the way out is to empty one side first
            LOG.warn("A data source and a data source folder share these paths in organization " +
                        "{}: {}. Saving the data source keeps the data sources in the folder. " +
                        "Renaming, moving or deleting the data source is refused while the " +
                        "folder holds data sources or subfolders, and renaming, moving or " +
                        "deleting the folder is refused while the data source has additional " +
                        "connections or data models, unless both are deleted together. " +
                        "Renaming or moving a folder above one of them is refused while its " +
                        "folder holds data sources or subfolders. The two are separated by " +
                        "moving the folder's data sources and subfolders out of it and then " +
                        "renaming the data source.",
                     orgID, clashes);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to check the data source paths of organization {}", orgID, e);
      }
   }

   /**
    * Checks that a data source may be deleted, renamed or moved: if a data source folder has the
    * same path (Bug #77691), the folder's data sources and subfolders are stored under the path
    * of the data source and would be deleted or moved with it (Bug #77725). Thrown before
    * anything is written.
    *
    * @param path the path of the data source.
    *
    * @throws MessageException if a folder at the path holds data sources or subfolders.
    */
   public void checkDataSourcePathClash(String path) {
      PathClashSides sides = getPathClashSides(path);

      if(sides != null) {
         sides.check(sides.folderSide(), "common.datasource.pathClashFolderNotEmpty", path);
      }
   }

   /**
    * Checks that a data source folder may be renamed or moved: if a data source has the same path
    * (Bug #77691), its additional connections and data models are stored under the path of the
    * folder and would be moved with it, and a folder below it with a data source at its path
    * would have its data sources taken for additional connections (Bug #77725). Thrown before
    * anything is written.
    *
    * @param path the path of the folder.
    *
    * @throws MessageException if a data source at the path has additional connections or data
    *                          models, or a data source at the path of a folder below it has a
    *                          folder that holds data sources or subfolders.
    */
   public void checkDataSourceFolderPathClash(String path) {
      checkDataSourceFolderDeletePathClash(path);

      AssetEntry[] subfolders = getEntries(path + "/", AssetEntry.Type.DATA_SOURCE_FOLDER);
      Arrays.sort(subfolders, Comparator.comparing(AssetEntry::getPath));

      for(AssetEntry subfolder : subfolders) {
         PathClashSides sides = getPathClashSides(subfolder.getPath());

         if(sides != null) {
            sides.check(sides.folderSide(), "common.datasource.pathClashBelow", path,
                        subfolder.getPath());
         }
      }
   }

   /**
    * Checks that a data source folder may be deleted: if a data source has the same path
    * (Bug #77691), its additional connections and data models are stored under the path of the
    * folder and would be deleted with it (Bug #77725). A folder below it with a data source at
    * its path is deleted with that data source, both are what the delete asks for. Thrown before
    * anything is written.
    *
    * @param path the path of the folder.
    *
    * @throws MessageException if a data source at the path has additional connections or data
    *                          models.
    */
   public void checkDataSourceFolderDeletePathClash(String path) {
      PathClashSides sides = getPathClashSides(path);

      if(sides != null) {
         sides.check(sides.dataSourceSide(), "common.datasource.pathClashDataSourceNotEmpty",
                     path);
      }
   }

   /**
    * Checks if a data source and a data source folder share a path (older data, Bug #77691).
    *
    * @param path the path.
    *
    * @return {@code true} if both a data source and a folder are stored at the path.
    */
   public boolean isDataSourcePathClash(String path) {
      return path != null &&
         containObject(new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null)) &&
         containObject(new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null));
   }

   /**
    * Gets which side of a data source and data source folder at the same path holds entries
    * under the path. The folder side is every subfolder, every data source stored with a full
    * path name (a data source of the folder) and everything under it, and the data models and
    * domains of those data sources. The data source side is every data source stored with a bare
    * name (an additional connection), and the logical models, physical views and VPMs of the
    * data source with their extended models. A data source one level under the path that can't
    * be read can't be told apart, so it counts on both sides (as {@code unreadable}).
    *
    * @return the sides, or {@code null} if a data source and a folder don't share the path.
    */
   private PathClashSides getPathClashSides(String path) {
      if(!isDataSourcePathClash(path)) {
         return null;
      }

      String prefix = path + "/";
      List<AssetEntry> entries = new ArrayList<>();
      // the logical models and physical views of the data source, whose extended models are
      // stored under them
      Set<String> models = new HashSet<>();

      for(AssetEntry entry : getEntries(prefix)) {
         // the root folder may list a missing asset (bug #60767)
         if(!containObject(entry)) {
            continue;
         }

         entries.add(entry);
         String name = entry.getPath().substring(prefix.length());

         if(name.indexOf('/') < 0 && (entry.getType() == AssetEntry.Type.LOGIC_MODEL ||
            entry.getType() == AssetEntry.Type.PARTITION))
         {
            models.add(entry.getType() + "/" + name);
         }
      }

      boolean folderSide = false;
      boolean dataSourceSide = false;
      String unreadable = null;

      for(AssetEntry entry : entries) {
         String name = entry.getPath().substring(prefix.length());
         int index = name.indexOf('/');
         AssetEntry.Type type = entry.getType();

         if(type == AssetEntry.Type.DATA_SOURCE && index < 0) {
            String storedName = getStoredDataSourceName(entry);

            if(storedName == null) {
               unreadable = unreadable == null ? entry.getPath() : unreadable;
            }
            else if(storedName.indexOf('/') < 0) {
               dataSourceSide = true;
            }
            else {
               folderSide = true;
            }
         }
         else if(index < 0 && (type == AssetEntry.Type.LOGIC_MODEL ||
            type == AssetEntry.Type.PARTITION || type == AssetEntry.Type.VPM) ||
            index > 0 && name.indexOf('/', index + 1) < 0 &&
            (type == AssetEntry.Type.EXTENDED_LOGIC_MODEL &&
               models.contains(AssetEntry.Type.LOGIC_MODEL + "/" + name.substring(0, index)) ||
             type == AssetEntry.Type.EXTENDED_PARTITION &&
               models.contains(AssetEntry.Type.PARTITION + "/" + name.substring(0, index))))
         {
            dataSourceSide = true;
         }
         else {
            folderSide = true;
         }
      }

      return new PathClashSides(folderSide, dataSourceSide, unreadable);
   }

   /**
    * Gets the name a data source entry is stored with, or {@code null} if it can't be read.
    */
   private String getStoredDataSourceName(AssetEntry entry) {
      return getStoredDataSourceName(entry, null);
   }

   private String getStoredDataSourceName(AssetEntry entry, String orgID) {
      try {
         XMLSerializable obj = getObject(entry, true, false, orgID);
         return obj instanceof XDataSourceWrapper wrapper && wrapper.getSource() != null ?
            wrapper.getSource().getFullName() : null;
      }
      catch(Exception e) {
         LOG.debug("Failed to read data source {}", entry.getPath(), e);
         return null;
      }
   }

   // which sides of a data source and folder at the same path hold entries under the path, and
   // a data source under the path that can't be read, which counts on both sides
   private record PathClashSides(boolean folderSide, boolean dataSourceSide, String unreadable) {
      // refuses if the side is not empty, or if an entry can't be told apart
      void check(boolean side, String key, Object... args) {
         if(side) {
            throw new MessageException(Catalog.getCatalog().getString(key, args));
         }

         if(unreadable != null) {
            throw new MessageException(Catalog.getCatalog().getString(
               "common.datasource.pathClashUnreadable", args[0], unreadable));
         }
      }
   }

   /**
    * Check if the specified data source exists in this repository.
    */
   public synchronized boolean containDatasource(String dsname) {
      if(dsname != null && checkPermission(ResourceType.DATA_SOURCE, dsname, ResourceAction.READ)) {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE, dsname, null);

         try {
            return indexedStorage.contains(entry.toIdentifier());
         }
         finally {
            indexedStorage.close();
         }
      }

      return false;
   }

   /**
    * Check if the specified data source folder exists in this repository.
    */
   public synchronized boolean containDataSourceFolder(String name) {
      if(name != null &&
         checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.READ)) {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE_FOLDER, name, null);

         try {
            return indexedStorage.contains(entry.toIdentifier());
         }
         finally {
            indexedStorage.close();
         }
      }

      return false;
   }

   /**
    * Get simple names of all the data sources in this repository.
    * @return names of all the data sources.
    */
   public synchronized String[] getDataSourceNames() {
      return Arrays.stream(getDataSourceFullNames())
         .map(DataSourceFolder::getDisplayName)
         .toArray(String[]::new);
   }

   /**
    * Get full names of all the data sources in this repository.
    * @return full names of all the data sources.
    */
   public String[] getDataSourceFullNames() {
      // need to filter out names of jdbc additional connections,
      // which aren't presented as datasources on their own.
      String[] unfilteredNames = getFullNames(AssetEntry.Type.DATA_SOURCE).values().stream()
         .flatMap(List::stream).toArray(String[]::new);
      return readableSources(getDataSourceFullNames0(unfilteredNames, null));
   }

   /**
    * Get full names of all the data sources in this repository.
    * @return full names of all the data sources.
    */
   public String[] getDataSourceFullNames(IdentityID orgID) {
      // need to filter out names of jdbc additional connections,
      // which aren't presented as datasources on their own.
      String[] unfilteredNames = getFullNames(AssetEntry.Type.DATA_SOURCE, orgID.orgID).values().stream()
         .flatMap(List::stream).toArray(String[]::new);
      return readableSources(getDataSourceFullNames0(unfilteredNames, orgID.orgID));
   }

   public String[] getDataSourceFullNames(String path) {
      // need to filter out names of jdbc additional connections,
      // which aren't presented as datasources on their own.
      String key = getFirstFolder(path);
      List<String> names = getFullNames(AssetEntry.Type.DATA_SOURCE).get(key);

      if(key.isEmpty() || key.equals("/")) {
         return getDataSourceFullNames();
      }

      if(names != null) {
         String[] unfilteredNames = names.toArray(new String[0]);
         return readableSources(getDataSourceFullNames0(unfilteredNames, null));
      }

      return new String[0];
   }

   public String[] getDataSourceFullNames(String path, String orgID) {
      String key = getFirstFolder(path);
      List<String> names = getFullNames(AssetEntry.Type.DATA_SOURCE, orgID).get(key);

      if(key.isEmpty() || key.equals("/")) {
         return readableSources(getFullNames(AssetEntry.Type.DATA_SOURCE, orgID).values()
            .stream().flatMap(List::stream).toArray(String[]::new));
      }

      if(names != null) {
         String[] unfilteredNames = names.toArray(new String[0]);
         return readableSources(getDataSourceFullNames0(unfilteredNames, orgID));
      }

      return new String[0];
   }

   /**
    * @param orgID the organization of the names, or {@code null} for the current one.
    */
   private String[] getDataSourceFullNames0(String[] unfilteredNames, String orgID) {
      List<String> result = new ArrayList<>();
      Set<String> folders = null;

      for(String name : unfilteredNames) {
         boolean isAdditional = false;

         // check if any datasource's path is a prefix to name.
         // if so, that datasource is the base datasource, and "name" is the
         // name of an additional connection
         for(String baseDS : unfilteredNames) {
            if(name.startsWith(baseDS + "/")) {
               if(folders == null) {
                  folders = new HashSet<>();
                  getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER, orgID != null ? orgID :
                     OrganizationManager.getInstance().getCurrentOrgID())
                     .values().forEach(folders::addAll);
               }

               // Bug #77725, a data source of a folder at the path of the data source (older
               // data, Bug #77691) is stored with its full path, an additional connection isn't
               if(folders.contains(baseDS) && isStoredWithPathName(name, orgID)) {
                  continue;
               }

               isAdditional = true;
               break;
            }
         }

         if(!isAdditional) {
            result.add(name);
         }
      }

      return result.toArray(new String[0]);
   }

   /**
    * Get all data source folder names in repository.
    * @return names of all the data source folders.
    */
   public synchronized String[] getDataSourceFolderNames() {
      return Arrays.stream(getDataSourceFolderFullNames())
         .map(DataSourceFolder::getDisplayName)
         .toArray(String[]::new);
   }

   /**
    * Get all data source folder names in repository.
    * @return full names of all the data source folders.
    */
   public String[] getDataSourceFolderFullNames() {
      return readableFolders(getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER).values().stream()
         .flatMap(List::stream).toArray(String[]::new));
   }

   public String[] getDataSourceFolderFullNames(String prefix) {
      String key = getFirstFolder(prefix);
      Map<String, List<String>> allFolders = getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER);

      if(key.isEmpty() || key.equals("/")) {
         return getDataSourceFolderFullNames();
      }

      List<String> names = allFolders.get(key);
      return names != null ? readableFolders(names.toArray(new String[0])) : new String[0];
   }

   public String[] getDataSourceFolderFullNames(String prefix, String orgID) {
      String key = getFirstFolder(prefix);
      Map<String, List<String>> allFolders = getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER, orgID);

      if(key.isEmpty() || key.equals("/")) {
         return readableFolders(getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER, orgID).values()
            .stream().flatMap(List::stream).toArray(String[]::new));
      }

      List<String> names = allFolders.get(key);
      return names != null ? readableFolders(names.toArray(new String[0])) : new String[0];
   }

   // Bug #77539: the names of a listing a sheet script makes itself that its user may read
   private static String[] readableSources(String[] names) {
      return ScriptDataSourceAccess.readable(ResourceType.DATA_SOURCE, names);
   }

   private static String[] readableFolders(String[] names) {
      return ScriptDataSourceAccess.readable(ResourceType.DATA_SOURCE_FOLDER, names);
   }

   private static String getFirstFolder(String prefix) {
      int slash = prefix.indexOf('/');
      return slash > 0 ? prefix.substring(0, slash) : prefix;
   }

   /**
    * Get data source object by its name.
    * @param dsname the specified data source.
    * @return corresponding data source object if any, null otherwise.
    */
   public XDataSource getDataSource(String dsname, String orgID) {
      if(dsname == null || !checkPermission(ResourceType.DATA_SOURCE, dsname, ResourceAction.READ))
      {
         return null;
      }

      XDataSource result = null;

      try {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE, dsname, null, orgID);
         XDataSourceWrapper wrapper = (XDataSourceWrapper) getObject(entry, true, true, orgID);

         if(wrapper != null) {
            result = wrapper.getSource();
            result = isSupported(result) ? result : null;

            if(result == null) {
               cachemap.remove(entry);
               wrapper = (XDataSourceWrapper) getObject(entry, true, true, orgID);

               if(wrapper != null) {
                  result = wrapper.getSource();
                  result = isSupported(result) ? result : null;
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get data source: " + dsname, e);
      }

      return result;
   }

   public XDataSource getDataSource(String dsname) {
      return getDataSource(dsname, null);
   }

   /**
    * Get data source folder object by its name.
    * @param name the specified data source folder.
    * @return corresponding data source folder object if any, null otherwise.
    */
   public synchronized DataSourceFolder getDataSourceFolder(String name) {
      if(name == null ||
         !checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.READ)) {
         return null;
      }

      DataSourceFolder result = null;

      try {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE_FOLDER, name, null);
         result = (DataSourceFolder) getObject(entry, true);
      }
      catch(Exception e) {
         LOG.error("Failed to get data source folder: " + name, e);
      }

      return result;
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx the specified data source.
    */
   public synchronized void setDataSource(XDataSource dx, boolean isImport) {
      setDataSource(dx, null, true, false, isImport, true);
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx           the specified data source.
    * @param oname        only used in RempteDataSourceRegistry.
    * @param actionRecord only used in RemoteDataSourceRegistry.
    * @param checkDelete  check the delete permission.
    * @param fireEvent    whether to fire an event after storing the object
    */
   public synchronized void setDataSource(XDataSource dx, String oname, Boolean actionRecord,
                                          boolean checkDelete, boolean isImport, boolean fireEvent)
   {
      for(String existQueryFolder : existQueryFolders) {
         dx.addFolder(existQueryFolder);
      }

      if(dx != null) {
         String dxname = dx.getFullName();
         boolean existing = containDatasource(dxname);

         if(existing) {
            boolean write = checkPermission(ResourceType.DATA_SOURCE, dxname, ResourceAction.WRITE);
            boolean delete = checkPermission(ResourceType.DATA_SOURCE, dxname, ResourceAction.DELETE);

            if(checkDelete && !delete && !write) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.delete", dxname));
            }
            else if(!checkDelete && !write) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.overwrite", dxname));
            }

            XDataSource odx = getDataSource(oname);

            if(odx != null && !odx.equalsConnection(dx)) {
               fireConnectionChangeEvent();
            }
         }
         else {
            int idx = dxname.lastIndexOf('/');
            boolean allowed = false;

            if(idx < 0) {
               allowed = checkPermission(ResourceType.CREATE_DATA_SOURCE,
                  "*", ResourceAction.ACCESS);
            }

            if(!allowed && idx < 0) {
               allowed = checkPermission(ResourceType.DATA_SOURCE_FOLDER,
                  "/", ResourceAction.WRITE);
            }
            else if(!allowed) {
               String folder = dxname.substring(0, idx);

               if(folder.isEmpty()) {
                  folder = "/";
               }

               allowed =
                  checkPermission(ResourceType.DATA_SOURCE_FOLDER, folder, ResourceAction.WRITE);
            }

            if(!allowed) {
               throw new SecurityException(
                  Catalog.getCatalog().getString("Permission denied to create datasource"));
            }
         }

         try {
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                              AssetEntry.Type.DATA_SOURCE, dxname, null);
            if(!isImport) {
               dx.setLastModified(System.currentTimeMillis());
            }

            setObject(entry, new XDataSourceWrapper(dx), fireEvent);

            if(dx.getType().equals("jdbc")) {
               AssetEntry dEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                                  AssetEntry.Type.DATA_MODEL, dxname, null);

               if(!containObject(dEntry)) {
                  setDataModel(new XDataModel(dxname));
               }
            }
            else if(dx.getType().equals("xmla")) {
               AssetEntry dEntry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                                  AssetEntry.Type.DOMAIN, dxname, null);

               if(!containObject(dEntry)) {
                  Domain domain = new Domain();
                  domain.setDataSource(dxname);
                  setDomain(domain);
               }
            }
         }
         catch(Exception e) {
            LOG.error(
               "Failed to set datasource: " + dx.getFullName(), e);
         }
      }
   }

   /**
    * Add or replace a data source folder.
    * @param folder the specified data source folder.
    */
   public synchronized void setDataSourceFolder(DataSourceFolder folder) {
      if(folder == null) {
         return;
      }

      String name = folder.getFullName();

      if(containDataSourceFolder(name) &&
         !checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.WRITE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to overwrite datasource folder"));
      }

      try {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE_FOLDER, name, null);
         setObject(entry, folder);
      }
      catch(Exception e) {
         LOG.error("Failed to set datasource folder: " + folder.getFullName(), e);
      }
   }

   /**
    * Remove a data source from the repository.
    * @param dxname the specified data source name.
    */
   public synchronized void removeDataSource(String dxname) {
      if(!checkPermission(ResourceType.DATA_SOURCE, dxname, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to delete datasource"));
      }

      // Bug #77725, before the try, which only logs a failure
      checkDataSourcePathClash(dxname);

      try {
         // read before the data source and its additional connections are removed. The test
         // query of a removed additional connection is kept under its name alone
         String additionalName = getJDBCAdditionalConnectionName(dxname);
         String[] additionalNames = additionalName != null ?
            new String[] { additionalName } : getAdditionalConnectionNames(dxname);
         List<String> resources = getAdditionalConnectionResources(dxname);
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE, dxname, null);

         // the permission of the data source itself, so that a data source created later at
         // its path doesn't get it. Only when there is one at the path: the path may also be a
         // permission resource that is not a registry path, e.g. "P::add", whose additional
         // connection is not removed (Bug #77700)
         if(containObject(entry)) {
            resources.add(dxname);
         }

         removeObject(entry);
         removeObjects(getEntries(dxname + "/"));
         removeObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_MODEL, dxname, null));
         removeConnectionTestQueries(dxname, additionalNames);
         removeDataSourcePermissions(resources);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove data source: " + dxname, e);
      }
   }

   /**
    * Update a data source. For importing.
    * @param dxname the specified data source name.
    * @param elem   the element with which the datasource will be updated.
    */
   public synchronized void updateDataSource(String dxname, Element elem, boolean isImport)
      throws Exception
   {
      if(!checkPermission(ResourceType.DATA_SOURCE, dxname, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to delete datasource"));
      }

      removeObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                  AssetEntry.Type.DATA_SOURCE, dxname, null));
      removeObjects(getEntries(dxname + "/", AssetEntry.Type.DOMAIN));
      parseDomain(elem);
      parseXDataSource(elem, isImport);
      parseDataModel(dxname, elem);
   }

   private synchronized void parseDataModel(String dxname, Element elem) throws Exception {
      NodeList nlist = elem.getElementsByTagName("DataModel");

      if(nlist.getLength() == 0) {
         return;
      }

      XDataModel dmodel = getDataModel(dxname);

      if(dmodel == null) {
         dmodel = new XDataModel(dxname);
         dmodel.parseXML(elem);
         setDataModel(dmodel);
         return;
      }

      for(int i = 0; i < nlist.getLength(); i++) {
         Element delem = (Element) nlist.item(i);
         NodeList folderList = delem.getElementsByTagName("folder");

         for(int j = 0; j < folderList.getLength(); j++) {
            Element node = (Element) folderList.item(j);
            String createdDateStr = Tool.getAttribute(node, "createdDate");
            long createdDate = createdDateStr == null ? 0 : Long.parseLong(createdDateStr);
            dmodel.addFolder(Tool.getAttribute(node, "name"), Tool.getAttribute(node, "createdBy"),
               createdDate);
         }
      }

      setDataModel(dmodel);
   }

   /**
    * Remove a data source folder and its children from the repository.
    * @param name the specified data source folder name.
    */
   public synchronized void removeDataSourceFolder(String name) {
      removeDataSourceFolder(name, false);
   }

   /**
    * Remove a data source folder and its children from the repository.
    *
    * @param name           the specified data source folder name.
    * @param withDataSource {@code true} to also remove a data source at the path of the folder
    *                       (older data, Bug #77691) with its additional connections and data
    *                       models. Otherwise the delete is refused if the data source has any
    *                       (Bug #77725).
    */
   public synchronized void removeDataSourceFolder(String name, boolean withDataSource) {
      if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to delete datasource folder"));
      }

      // Bug #77725, before the try, which only logs a failure
      if(!withDataSource) {
         checkDataSourceFolderDeletePathClash(name);
      }

      removeDataSourceFolder0(name);

      if(withDataSource && containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, name, null)))
      {
         removeDataSource(name);
      }
   }

   private void removeDataSourceFolder0(String name) {
      // the folders whose removal was tried, whose permissions are removed if they are gone
      List<AssetEntry> removedFolders = new ArrayList<>();

      try {
         // Bug #77725, a subfolder at the path of a data source (older data, Bug #77691) is
         // removed first, with its data sources and subfolders, and then the data source.
         // Removed first, the data source would take the folder's data sources and subfolders
         // and leave their permissions behind. A deeper one first.
         AssetEntry[] clashed = Arrays.stream(
            getEntries(name + "/", AssetEntry.Type.DATA_SOURCE_FOLDER))
            .filter(entry -> isDataSourcePathClash(entry.getPath()))
            .sorted(Comparator.comparing(AssetEntry::getPath).reversed())
            .toArray(AssetEntry[]::new);

         for(AssetEntry entry : clashed) {
            if(!checkPermission(
               ResourceType.DATA_SOURCE_FOLDER, entry.getPath(), ResourceAction.DELETE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "Permission denied to delete datasource folder"));
            }

            removeDataSourceFolder0(entry.getPath());
            removeDataSource(entry.getPath());
         }

         AssetEntry[] allDSChildren =
            getEntries(name + "/", AssetEntry.Type.DATA_SOURCE);
         AssetEntry[] allFolderChildren =
            getEntries(name + "/", AssetEntry.Type.DATA_SOURCE_FOLDER);
         // a data source before its additional connections, which removeDataSource reads to
         // remove their connection test queries
         Arrays.sort(allDSChildren, Comparator.comparing(AssetEntry::getPath));
         // a subfolder before its parent, so that a failure stops with the parent still listed
         Arrays.sort(allFolderChildren,
                     Comparator.comparing(AssetEntry::getPath).reversed());

         for(AssetEntry entry : allDSChildren) {
            removeDataSource(entry.getPath());
         }

         for(AssetEntry entry : allFolderChildren) {
            if(!checkPermission(
               ResourceType.DATA_SOURCE_FOLDER, entry.getPath(), ResourceAction.DELETE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "Permission denied to delete datasource folder"));
            }

            removeObject(entry);
            removedFolders.add(entry);
         }

         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE_FOLDER, name, null);

         // only when there is a folder at the path, as for the permission of a data source
         if(containObject(entry)) {
            removedFolders.add(entry);
         }

         removeObject(entry);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove datasource folder: " + name, e);
      }

      // the permissions of the removed folders, so that a folder created later at the path of
      // one doesn't get it. A folder that is still listed keeps its permission (Bug #77731)
      removePermissions(ResourceType.DATA_SOURCE_FOLDER, getUnlistedPaths(removedFolders));
   }

   /**
    * Gets the paths of the entries that the stored index of the registry doesn't list. The index
    * is read from the storage, not from the cache: removeObject removes the entry from the
    * cached index before it saves it, so after a failed save the cached index no longer lists an
    * entry that the stored one still lists. Nothing is returned if the index can't be read.
    */
   private List<String> getUnlistedPaths(List<AssetEntry> entries) {
      List<String> paths = new ArrayList<>();

      if(entries.isEmpty()) {
         return paths;
      }

      AssetFolder root;

      try {
         root = (AssetFolder) indexedStorage.getXMLSerializable(getRootIdentifier(), null);
      }
      catch(Exception e) {
         LOG.warn("Failed to read the data source index, the permissions are kept", e);
         return paths;
      }
      finally {
         indexedStorage.close();
      }

      if(root == null) {
         return paths;
      }

      for(AssetEntry entry : entries) {
         if(!root.containsEntry(entry)) {
            paths.add(entry.getPath());
         }
      }

      return paths;
   }

   /**
    * Gets the full paths of the data sources in a data source folder and in its subfolders at
    * any depth, without their additional connections, sorted.
    * <p>
    * An additional connection is a data source entry whose parent path is a data source entry
    * of the folder, the rule of renameDataSourceFolder. Only entries under the folder are
    * consulted, so a data source at the path of the folder itself doesn't hide any.
    */
   public List<String> getFolderTreeDataSourceNames(String folder) {
      Set<String> paths = new HashSet<>();

      for(AssetEntry entry : getEntries(folder + "/", AssetEntry.Type.DATA_SOURCE)) {
         paths.add(entry.getPath());
      }

      List<String> names = new ArrayList<>();

      for(String path : paths) {
         String parent = path.substring(0, path.lastIndexOf('/'));

         // Bug #77725, a data source of a folder at the path of the parent isn't an additional
         // connection of it
         if(!paths.contains(parent) || isFolderDataSourcePath(parent, path)) {
            names.add(path);
         }
      }

      Collections.sort(names);
      return names;
   }

   /**
    * Gets the full paths of the subfolders of a data source folder at any depth, sorted.
    */
   public List<String> getFolderTreeSubfolderNames(String folder) {
      List<String> names = new ArrayList<>();

      for(AssetEntry entry : getEntries(folder + "/", AssetEntry.Type.DATA_SOURCE_FOLDER)) {
         names.add(entry.getPath());
      }

      Collections.sort(names);
      return names;
   }

   /**
    * Get domain object of a data source.
    * @param datasource the specified data source.
    * @return domain object of the specified data source if any, null otherwise.
    */
   public XDomain getDomain(String datasource) {
      if(datasource == null ||
         !checkPermission(ResourceType.DATA_SOURCE, datasource, ResourceAction.READ)) {
         return null;
      }

      XDomain result = null;

      try {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DOMAIN, datasource, null);
         Object object = getObject(entry, true);

         if(object != null) {
            result = object instanceof XDomainWrapper ?
               ((XDomainWrapper) object).getDomain() : (XDomain) object;
         }
         else {
            //required to write DataModel to export
            result = getDataModel(datasource);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get domain: " + datasource, e);
      }

      return result;
   }

   /**
    * Add a domain object to the repository.
    * @param dx           the specified domain object to add.
    * @param recordAction record action
    */
   public synchronized void setDomain(XDomain dx, boolean recordAction) {
      if(dx != null) {
         String name = dx.getDataSource();

         if(!checkPermission(ResourceType.DATA_SOURCE, name, ResourceAction.WRITE)) {
            throw new SecurityException("Permission denied to modify datasource");
         }

         try {
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                              AssetEntry.Type.DOMAIN, name, null);
            setObject(entry, new XDomainWrapper(dx));
         }
         catch(Exception e) {
            LOG.error("Failed to set domain: " + dx.getDataSource(), e);
         }
      }
   }

   /**
    * Add a domain object to the repository.
    * @param dx the specified domain object to add.
    */
   public synchronized void setDomain(XDomain dx) {
      this.setDomain(dx, true);
   }

   /**
    * Data source renamed, sync data model and domain. A data source that can't be loaded is not
    * moved here: {@link #renameDataSourceFolder(String, String)} relies on that and moves it with
    * the rest of the folder, and a move of it on its own is refused by the callers (Bug #77727).
    *
    * @throws DataSourceRenameException if a write failed. The objects not yet moved are still at
    *                                   the old path.
    */
   public void renameDatasource(String oname, String nname) {
      if(Tool.equals(oname, nname)) {
         return;
      }

      XDataSource ds = getDataSource(oname);

      if(ds == null) {
         return;
      }

      if(!checkPermission(ResourceType.DATA_SOURCE, oname, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "security.nopermission.delete", "datasource"));
      }

      if(!checkPermission(ResourceType.DATA_SOURCE, oname, ResourceAction.WRITE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "security.nopermission.write", "datasource"));
      }

      // Bug #77725, a folder at the path would have its data sources moved under the new name
      checkDataSourcePathClash(oname);

      XDomain domain = getDomain(oname);
      XDataModel model = getDataModel(oname);
      ds.setName(nname);
      boolean moved = false;

      // Bug #77704, the data source, its model, its domain and the objects under it are moved
      // together: all are written before the index is saved and before the permission of the
      // data source is moved. A failed write is thrown, with nothing moved.
      try {
         List<EntryMove> moves = new ArrayList<>();
         moves.add(createMove(oname, nname, AssetEntry.Type.DATA_SOURCE,
                              new XDataSourceWrapper(ds)));

         if(model != null) {
            model.setDataSource(nname);
            moves.add(createMove(oname, nname, AssetEntry.Type.DATA_MODEL, model));
         }

         if(domain != null && !(domain instanceof XDataModel)) {
            domain.setDataSource(nname);
            moves.add(createMove(oname, nname, AssetEntry.Type.DOMAIN,
                                 new XDomainWrapper(domain)));
         }

         moves.addAll(createMoves(oname + "/", nname + "/", false,
                                  ds instanceof AdditionalConnectionDataSource, Set.of()));
         moveEntries(moves);
         moved = true;
         updateQueryFolders(ds, oname);
      }
      catch(Exception e) {
         String failed = oname;

         if(e instanceof MoveEntriesException moveException) {
            // only a permission wasn't moved
            moved = moveException.isCommitted();
            failed = moveException.getPath() != null ? moveException.getPath() : oname;
         }

         if(!moved) {
            // the cached instances were given the new name
            for(AssetEntry.Type type : new AssetEntry.Type[] {
               AssetEntry.Type.DATA_SOURCE, AssetEntry.Type.DATA_MODEL, AssetEntry.Type.DOMAIN })
            {
               cachemap.remove(new AssetEntry(AssetRepository.QUERY_SCOPE, type, oname, null));
            }

            clearCache2();
         }

         throw new DataSourceRenameException(
            oname, nname, failed, moved ? Map.of(oname, nname) : Map.of(), e);
      }

      if(ds instanceof JDBCDataSource) {
         renameConnectionTestQuery(oname, nname);
      }
   }

   /**
    * Moves the legacy connection test query of a renamed or moved JDBC data source, which older
    * versions kept in SreeEnv under the data source full name.
    */
   private void renameConnectionTestQuery(String oname, String nname) {
      try {
         if(JDBCUtil.renameConnectionTestQuery(oname, nname)) {
            SreeEnv.save();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to move the connection test query of data source {} to {}",
                  oname, nname, e);
      }
   }

   /**
    * Checks if a path is that of an additional connection of a data source, e.g. "P/add" or
    * "F/P/add". The parent path must resolve to a data source that supports additional
    * connections and that has an additional connection with the last path segment as its name.
    * A data source in a data source folder, e.g. "F/P", is not an additional connection.
    *
    * @param path the registry path.
    *
    * @return {@code true} if the path is that of an additional connection.
    */
   public boolean isAdditionalConnectionPath(String path) {
      if(path == null) {
         return false;
      }

      int index = path.lastIndexOf('/');

      if(index <= 0 || index == path.length() - 1) {
         return false;
      }

      XDataSource parent = getDataSource(path.substring(0, index));
      return parent instanceof AdditionalConnectionDataSource<?> ads &&
         ads.containDatasource(path.substring(index + 1)) &&
         !isFolderDataSourcePath(path.substring(0, index), path);
   }

   /**
    * Checks if a data source entry under the path of a data source is a data source of a data
    * source folder at the same path (older data, Bug #77691), not an additional connection: the
    * folder exists and the entry is stored with a full path name, which an additional connection
    * never is. An entry that can't be read is taken for an additional connection (Bug #77725).
    *
    * @param dataSource the path of the data source, e.g. "P".
    * @param path       the path of the entry, e.g. "P/x".
    *
    * @return {@code true} if the entry is a data source of the folder.
    */
   public boolean isFolderDataSourcePath(String dataSource, String path) {
      return containObject(new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, dataSource, null)) &&
         isStoredWithPathName(path, null);
   }

   // a data source entry that can be read and is stored with a full path name
   private boolean isStoredWithPathName(String path, String orgID) {
      AssetEntry entry = orgID == null ?
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null) :
         new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null,
                        orgID);
      String name = getStoredDataSourceName(entry, orgID);
      return name != null && name.indexOf('/') >= 0;
   }

   /**
    * Checks if a registry path is already used by a data source (including an additional
    * connection) or by a data source folder in the current organization. A data source and a
    * folder must never share a path: the data sources in the folder would become additional
    * connections of the data source. The path is compared exactly (case-sensitive) and, unlike
    * {@link #getDataSource(String)}, a globally shared data source of the host organization is
    * not taken into account.
    *
    * @param path the registry path, e.g. "F/P".
    *
    * @return {@code true} if a data source or a data source folder exists at the path.
    */
   public boolean isDataSourcePathInUse(String path) {
      return path != null &&
         (containObject(new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, path, null)) ||
          containObject(new AssetEntry(
             AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, path, null)));
   }

   /**
    * Gets the data source that a registry path lies under, i.e. the first parent segment of the
    * path, e.g. "P" for "P/X" or "F/P" for "F/P/X", that is a data source in the current
    * organization. A data source or folder must not be moved under a data source: it would become
    * an additional connection of that data source. The path is compared exactly (case-sensitive)
    * and, like {@link #isDataSourcePathInUse(String)}, a globally shared data source of the host
    * organization is not taken into account.
    *
    * @param path the registry path, e.g. "F/P/X".
    *
    * @return the path of the data source, or {@code null} if no parent segment is a data source.
    */
   public String getDataSourceAncestor(String path) {
      if(path == null) {
         return null;
      }

      for(int i = path.indexOf('/'); i != -1; i = path.indexOf('/', i + 1)) {
         String parent = path.substring(0, i);

         if(containObject(new AssetEntry(
            AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, parent, null)))
         {
            return parent;
         }
      }

      return null;
   }

   /**
    * Checks if a registry path is the same as another path or lies under it, e.g. "F", "F/G" or
    * "F/G/H" for "F", but not "Fx". A data source folder must not be moved or renamed into itself
    * or one of its subfolders: there would be no parent folder left for it.
    *
    * @param path    the registry path, e.g. "F".
    * @param newPath the path to check, e.g. "F/G".
    *
    * @return {@code true} if the new path is the path or one of its descendants.
    */
   public static boolean isSameOrDescendantPath(String path, String newPath) {
      return Tool.isSameOrDescendantPath(path, newPath);
   }

   /**
    * Gets the name of a JDBC additional connection from its path, or null if the path is not
    * that of a JDBC additional connection.
    */
   private String getJDBCAdditionalConnectionName(String dxname) {
      int index = dxname.lastIndexOf('/');

      if(index <= 0 ||
         !(getDataSource(dxname.substring(0, index)) instanceof AdditionalConnectionDataSource) ||
         !(getDataSource(dxname) instanceof JDBCDataSource) ||
         // Bug #77725, a data source of a folder at the path of the data source
         isFolderDataSourcePath(dxname.substring(0, index), dxname))
      {
         return null;
      }

      return dxname.substring(index + 1);
   }

   /**
    * Gets the names of the additional connections of a data source.
    */
   private String[] getAdditionalConnectionNames(String dxname) {
      String prefix = dxname + "/";

      try {
         return Arrays.stream(getEntries(prefix, AssetEntry.Type.DATA_SOURCE))
            .map(entry -> entry.getPath().substring(prefix.length()))
            .filter(name -> !name.contains("/"))
            .toArray(String[]::new);
      }
      catch(Exception e) {
         LOG.warn("Failed to get the additional connections of data source {}", dxname, e);
         return new String[0];
      }
   }

   /**
    * Gets the permission resources of the additional connections that are removed with a data
    * source: "parent::name" of a removed additional connection, or "dxname::name" of each
    * additional connection of a removed data source. Must be called before the entries are
    * removed. The path may also be a permission resource that is not a registry path, e.g.
    * "P::add", which has none.
    */
   private List<String> getAdditionalConnectionResources(String dxname) {
      List<String> resources = new ArrayList<>();

      // never keeps the data source from being removed
      try {
         if(isAdditionalConnectionPath(dxname)) {
            int index = dxname.lastIndexOf('/');
            resources.add(dxname.substring(0, index) + XUtil.ADDITIONAL_DS_CONNECTOR +
                             dxname.substring(index + 1));
         }
         else if(getDataSource(dxname) instanceof AdditionalConnectionDataSource) {
            for(String name : getAdditionalConnectionNames(dxname)) {
               resources.add(dxname + XUtil.ADDITIONAL_DS_CONNECTOR + name);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to get the additional connections of data source {}", dxname, e);
      }

      return resources;
   }

   /**
    * Removes the permissions of removed data sources and additional connections, so that one
    * created later with the same name doesn't get them.
    */
   private void removeDataSourcePermissions(List<String> resources) {
      removePermissions(ResourceType.DATA_SOURCE, resources);
   }

   /**
    * Removes the permissions of removed resources of a type, so that one created later with the
    * same name doesn't get them.
    */
   private void removePermissions(ResourceType type, List<String> resources) {
      if(resources.isEmpty()) {
         return;
      }

      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = engine == null ? null : engine.getSecurityProvider();

      if(engine == null || provider != null && provider.isVirtual()) {
         return;
      }

      for(String resource : resources) {
         try {
            engine.removePermission(type, resource);
         }
         catch(Exception e) {
            LOG.warn("Failed to remove the permission of {} {}", type, resource, e);
         }
      }
   }

   /**
    * Removes the connection test queries of a removed data source and of its additional
    * connections, or of a removed additional connection. The test query of an additional
    * connection is kept under its name alone, so it is not removed if a data source of that full
    * name exists.
    */
   private void removeConnectionTestQueries(String dxname, String[] additionalNames) {
      try {
         boolean changed = JDBCUtil.removeConnectionTestQueryIfSet(dxname);

         for(String name : additionalNames) {
            if(!containObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                             AssetEntry.Type.DATA_SOURCE, name, null)))
            {
               changed = JDBCUtil.removeConnectionTestQueryIfSet(name) || changed;
            }
         }

         if(changed) {
            SreeEnv.save();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to remove the connection test query of data source {}", dxname, e);
      }
   }

   /**
    * Update permissions for query folders of the target datasource.
    * @param ds    target datasource.
    * @param oname the old name of the datasource.
    */
   private void updateQueryFolders(XDataSource ds, String oname) {
      String[] folders = ds.getFolders();

      for(String folder : folders) {
         String opath = oname + "/" + folder;
         String npath = ds.getFullName() + "/" + folder;
         XUtil.updateQueryFolderPermission(opath, npath, this::updatePermission);
      }
   }

   /**
    * Data source folder renamed, sync its subfolders and sub datasources.
    */
   public void renameDataSourceFolder(String oname, String nname) {
      if(Tool.equals(oname, nname)) {
         return;
      }

      // a folder moved into one of its subfolders would be renamed again by the move of the rest
      // below and be left with no parent folder. Thrown before anything is written.
      if(isSameOrDescendantPath(oname, nname)) {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.datasource.moveIntoItself", oname));
      }

      DataSourceFolder folder = getDataSourceFolder(oname);

      if(folder == null) {
         return;
      }

      checkDSFolderRenamePermission(oname);
      // Bug #77725, before anything is written
      checkDataSourceFolderPathClash(oname);
      // Bug #77704, a failed write stops the rename and is thrown, with the data sources moved
      // before it. The folders created for the move are removed again if nothing was moved in.
      Map<String, String> moved = new LinkedHashMap<>();
      Map<String, String> targets = new LinkedHashMap<>();
      String current = oname;

      try {
         AssetEntry[] allDSChildren =
            getEntries(oname + "/", AssetEntry.Type.DATA_SOURCE);
         AssetEntry[] allFolderChildren =
            getEntries(oname + "/", AssetEntry.Type.DATA_SOURCE_FOLDER);
         // Decided before anything is renamed, from the entries only (no data source is loaded,
         // so a parent whose connector isn't installed or that can't be read counts too): an
         // additional connection is a data source entry whose parent path is a data source
         // entry. This is the structural rule only. When a data source and a folder share a
         // path, a data source of that folder is taken for an additional connection too.
         Set<String> dsPaths = new HashSet<>();

         for(AssetEntry entry : allDSChildren) {
            dsPaths.add(entry.getPath());
         }

         List<AssetEntry> additionals = new ArrayList<>();
         Set<String> additionalPaths = new HashSet<>();

         for(AssetEntry entry : allDSChildren) {
            String path = entry.getPath();
            int index = path.lastIndexOf('/');

            if(index > 0 && dsPaths.contains(path.substring(0, index))) {
               additionals.add(entry);
               additionalPaths.add(path);
            }
         }

         // a folder before its subfolders
         Arrays.sort(allFolderChildren, Comparator.comparing(AssetEntry::getPath));
         List<DataSourceFolder> subfolders = new ArrayList<>();

         // checked and read before anything is written
         for(AssetEntry entry : allFolderChildren) {
            current = entry.getPath();
            checkDSFolderRenamePermission(current);
            DataSourceFolder dsfolder = getDataSourceFolder(current);

            if(dsfolder == null) {
               throw new IOException("Failed to read data source folder: " + current);
            }

            subfolders.add(dsfolder);
         }

         // the new folders are written first, with the permissions of the old ones, so a data
         // source moved before a failed write is in a folder like its old one
         current = oname;

         if(createMoveTargetFolder(folder, nname)) {
            targets.put(oname, nname);
         }

         for(DataSourceFolder dsfolder : subfolders) {
            String opath = dsfolder.getFullName();
            String npath = nname + opath.substring(oname.length());
            current = opath;

            if(createMoveTargetFolder(dsfolder, npath)) {
               targets.put(opath, npath);
            }
         }

         // a data source before its additional connections, as in removeDataSourceFolder
         Arrays.sort(allDSChildren, Comparator.comparing(AssetEntry::getPath));

         for(AssetEntry entry : allDSChildren) {
            // an additional connection is moved by its parent's renameObjects, together with its
            // "parent::name" permission. Renamed on its own, it would keep the permission under
            // the old key and get the full path as its name.
            if(additionalPaths.contains(entry.getPath())) {
               continue;
            }

            String opath = entry.getPath();
            String npath = nname + opath.substring(oname.length());
            current = opath;
            renameDatasource(opath, npath);

            // not moved if it can't be loaded, it is moved with the rest below
            if(containObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                            AssetEntry.Type.DATA_SOURCE, npath, null)))
            {
               moved.put(opath, npath);
            }
         }

         // an additional connection still at its old path: its parent wasn't renamed, e.g. its
         // connector isn't installed or it can't be read. The move of the rest below moves the
         // entry but not the "parent::name" permission, which is moved once the entry is.
         List<AssetEntry> leftAdditionals =
            additionals.stream().filter(this::containObject).toList();

         // the rest, e.g. a data source that can't be loaded, before the old folders are gone
         current = oname;
         List<EntryMove> rest = createMoves(oname + "/", nname + "/", false, false,
                                            Set.of(allFolderChildren));

         try {
            moveEntries(rest);
         }
         catch(MoveEntriesException e) {
            // moved, only a permission wasn't
            if(e.isCommitted()) {
               addMovedDataSources(rest, moved);

               try {
                  moveAdditionalPermissions(leftAdditionals, oname, nname);
               }
               catch(Exception ex) {
                  e.addSuppressed(ex);
               }
            }

            throw e;
         }

         addMovedDataSources(rest, moved);
         moveAdditionalPermissions(leftAdditionals, oname, nname);

         for(DataSourceFolder dsfolder : subfolders) {
            String opath = dsfolder.getFullName();
            String npath = nname + opath.substring(oname.length());
            current = opath;
            dsfolder.setName(npath);
            moveObject(opath, npath, AssetEntry.Type.DATA_SOURCE_FOLDER, dsfolder);
         }

         current = oname;
         folder.setName(nname);
         moveObject(oname, nname, AssetEntry.Type.DATA_SOURCE_FOLDER, folder);
      }
      catch(SecurityException se) {
         discardMoveTargetFolders(targets);
         throw se;
      }
      catch(DataSourceRenameException e) {
         discardMoveTargetFolders(targets);
         e.addMovedDataSources(oname, nname, moved);
         throw e;
      }
      catch(Exception e) {
         discardMoveTargetFolders(targets);
         String failed = e instanceof MoveEntriesException moveException &&
            moveException.getPath() != null ? moveException.getPath() : current;
         throw new DataSourceRenameException(oname, nname, failed, moved, e);
      }
   }

   // moves the "parent::name" permissions of additional connections moved to a new folder
   private void moveAdditionalPermissions(List<AssetEntry> additionals, String oname,
                                          String nname)
   {
      for(AssetEntry entry : additionals) {
         String opath = entry.getPath();
         int index = opath.lastIndexOf('/');
         String oparent = opath.substring(0, index);
         String nparent = nname + oparent.substring(oname.length());
         String name = opath.substring(index + 1);
         updatePermission(ResourceType.DATA_SOURCE, oparent + "::" + name,
                          nparent + "::" + name);
      }
   }

   // the data sources, not additional connections, of a batch that was moved
   private static void addMovedDataSources(List<EntryMove> moves, Map<String, String> moved) {
      for(EntryMove move : moves) {
         if(move.oentry().isDataSource() && move.name()) {
            moved.put(move.oentry().getPath(), move.nentry().getPath());
         }
      }
   }

   /**
    * Writes the folder that a data source folder is moved to, before anything is moved into it,
    * with the permission of the old folder. The old folder is kept until the move is done.
    *
    * @param oname the old path of the folder.
    * @param nname the new path of the folder.
    *
    * @return {@code true} if the folder was created, {@code false} if the old folder doesn't exist
    * or the new one already does.
    */
   public boolean createMoveTargetFolder(String oname, String nname) throws Exception {
      DataSourceFolder folder = getDataSourceFolder(oname);
      return folder != null && createMoveTargetFolder(folder, nname);
   }

   boolean createMoveTargetFolder(DataSourceFolder folder, String nname)
      throws Exception
   {
      String oname = folder.getFullName();
      AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                         AssetEntry.Type.DATA_SOURCE_FOLDER, oname, null);
      AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                         AssetEntry.Type.DATA_SOURCE_FOLDER, nname, null);

      if(Tool.equals(oname, nname) || containObject(nentry)) {
         return false;
      }

      AssetEntry stored = getRoot().getEntry(oentry);

      if(stored != null) {
         nentry.copyProperties(stored);
      }

      AssetUtil.updateMetaData(
         nentry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());
      DataSourceFolder copy = (DataSourceFolder) folder.clone();
      copy.setName(nname);

      try {
         indexedStorage.putXMLSerializable(nentry.toIdentifier(), copy);

         try {
            updateRoot(List.of(), List.of(nentry));
         }
         catch(Exception e) {
            removeStoredObject(nentry);
            throw e;
         }

         clearCache2();

         try {
            copyPermission(ResourceType.DATA_SOURCE_FOLDER, oname, nname);
         }
         catch(Exception e) {
            discardMoveTargetFolder(oname, nname);
            throw e;
         }

         return true;
      }
      finally {
         indexedStorage.close();
      }
   }

   /**
    * Removes a folder that {@link #createMoveTargetFolder(String, String)} created, after the
    * move failed, if nothing was moved into it and the old folder is still there.
    *
    * @param oname the old path of the folder.
    * @param nname the new path of the folder.
    */
   public void discardMoveTargetFolder(String oname, String nname) {
      AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                         AssetEntry.Type.DATA_SOURCE_FOLDER, oname, null);
      AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                         AssetEntry.Type.DATA_SOURCE_FOLDER, nname, null);

      if(Tool.equals(oname, nname) || !containObject(oentry) || !containObject(nentry) ||
         getEntries(nname + "/").length > 0)
      {
         return;
      }

      try {
         updateRoot(List.of(nentry), List.of());
         removeStoredObject(nentry);
         clearCache2();
         SecurityEngine engine = SecurityEngine.getSecurity();

         if(!engine.getSecurityProvider().isVirtual() &&
            engine.getPermission(ResourceType.DATA_SOURCE_FOLDER, nname) != null)
         {
            engine.removePermission(ResourceType.DATA_SOURCE_FOLDER, nname);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to remove the data source folder {} created for a move that failed",
                  nname, e);
      }
      finally {
         indexedStorage.close();
      }
   }

   // the folders created for a move, the subfolders first
   private void discardMoveTargetFolders(Map<String, String> targets) {
      List<Map.Entry<String, String>> list = new ArrayList<>(targets.entrySet());
      Collections.reverse(list);

      for(Map.Entry<String, String> target : list) {
         discardMoveTargetFolder(target.getKey(), target.getValue());
      }
   }

   /**
    * Check rename permission for datasource folder.
    * @param name the datasource folder path.
    */
   private void checkDSFolderRenamePermission(String name) {
      if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "security.nopermission.delete", "datasource folder"));
      }

      if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.WRITE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "security.nopermission.write", "datasource folder"));
      }
   }

   /**
    * Get the children data source folder from the specified data source folder
    * path.
    * @param path the specified data source folder name.
    * @return the children data source folder of the specified data source
    * folder.
    */
   public List<String> getSubfolderNames(String path) {
      return getSubfolderNames(path, false);
   }

   /**
    * Get the children data source folder from the specified data source folder
    * path.
    * @param path the specified data source folder name.
    * @param allChild the all sub child of specified path data source folder name.
    * @return the children data source folder of the specified data source
    * folder.
    */
   public List<String> getSubfolderNames(String path, boolean allChild, String orgID) {
      path = path == null ? "" : path.endsWith("/") ? path : path + "/";

      String[] folderFullNames = getDataSourceFolderFullNames(path, orgID);
      List<String> names = allChild ? getAllSubChildren(path, folderFullNames) : getChildren(path, folderFullNames);

      return names;
   }

   public List<String> getSubfolderNames(String path, boolean allChild) {
      path = path == null ? "" : path.endsWith("/") ? path : path + "/";

      String[] folderFullNames = getDataSourceFolderFullNames(path);
      List<String> names = allChild ? getAllSubChildren(path, folderFullNames) : getChildren(path, folderFullNames);

      return names;
   }

   /**
    * Get the children data source from the specified data source folder path.
    * @param path the specified data source folder name.
    * @return the children data source of the specified data source folder.
    */
   public List<String> getSubDataSourceNames(String path) {
      return getSubDataSourceNames(path, false);
   }

   /**
    * Get the children data source from the specified data source folder path.
    * @param path the specified data source folder name.
    * @param allChild the all sub child of specified path data source folder.
    * @return the children data source of the specified data source folder.
    */
   public List<String> getSubDataSourceNames(String path, boolean allChild) {
      path = path == null ? "" : path.endsWith("/") ? path :  path + "/";
      String[] fullNames = getDataSourceFullNames(path);
      return allChild ? getAllSubChildren(path, fullNames) : getChildren(path, fullNames);
   }

   public List<String> getSubDataSourceNames(String path, boolean allChild, String orgID) {
      path = path == null ? "" : path.endsWith("/") ? path :  path + "/";
      String[] fullNames = getDataSourceFullNames(path, orgID);
      return allChild ? getAllSubChildren(path, fullNames) : getChildren(path, fullNames);
   }

   /**
    * Get all sub children of the specified path.
    */
   private List<String> getAllSubChildren(String path, String[] names) {
      List<String> children = new ArrayList<>();

      for(String name : names) {
         if("/".equals(path) && name.indexOf('/') == -1  || name.contains(path)) {
            children.add(name);
         }
      }

      return children;
   }

   /**
    * Get children of the specified path.
    */
   private List<String> getChildren(String path, String[] names) {
      List<String> children = new ArrayList<>();

      for(String name : names) {
         if(path.isEmpty() && name.indexOf('/') < 0 ||
            name.startsWith(path) && name.indexOf('/', path.length() + 1) < 0)
         {
            children.add(name);
         }
      }

      return children;
   }

   /**
    * Remove a domain object from the repository.
    * @param datasource the specified data source name.
    */
   public synchronized void removeDomain(String datasource) {
      if(!checkPermission(ResourceType.DATA_SOURCE, datasource, ResourceAction.WRITE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to modify datasource"));
      }

      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DOMAIN, datasource, null);
      removeObject(entry);
   }

   /**
    * Get data model object of a data source.
    * @param datasource the specified data source name.
    * @return data model object of the specified data source object if any,
    * null otherwise.
    */
   public XDataModel getDataModel(String datasource) {
      if(datasource == null || !checkPermission(ResourceType.DATA_SOURCE, datasource, ResourceAction.READ)) {
         return null;
      }

      XDataSource xds = getDataSource(datasource);

      // for experimental hive data model support
      if(!Drivers.getInstance().isHiveEnabled() && (xds instanceof JDBCDataSource) &&
         ((JDBCDataSource) xds).getDatabaseType() == JDBCDataSource.JDBC_HIVE) {
         return null;
      }

      XDataModel result = null;

      try {
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_MODEL, datasource, null);
         result = (XDataModel) getObject(entry, true);

         if(result != null && !drillPathsFixed.containsKey(datasource)) {
            fixDrillPaths(result);
            drillPathsFixed.put(datasource, datasource);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get data model for data " +
                      "source: " + datasource, e);
      }

      return result;
   }

   /**
    * Corrects incomplete drill paths defined in older versions of the software.
    * @param model the model to fix.
    * @since 11.5
    */
   private void fixDrillPaths(XDataModel model) {
      for(String name : model.getLogicalModelNames()) {
         XLogicalModel logicalModel = model.getLogicalModel(name);

         if(logicalModel != null) {
            fixDrillPaths(logicalModel);
         }
      }
   }

   /**
    * Corrects incomplete drill paths defined in older versions of the software.
    * @param model the model to fix.
    * @since 11.5
    */
   private void fixDrillPaths(XLogicalModel model) {
      for(Enumeration<XEntity> entities = model.getEntities();
          entities.hasMoreElements(); ) {
         XEntity entity = entities.nextElement();

         for(Enumeration<XAttribute> attributes = entity.getAttributes();
             attributes.hasMoreElements(); ) {
            XAttribute attribute = attributes.nextElement();
            XMetaInfo meta = attribute.getXMetaInfo();
            XDrillInfo drillInfo = meta.getXDrillInfo();

            if(drillInfo != null && !drillInfo.isEmpty()) {
               for(int j = 0; j < drillInfo.getDrillPathCount(); j++) {
                  DrillPath drillPath = drillInfo.getDrillPath(j);

                  for(Enumeration<String> e = drillPath.getParameterNames();
                      e.hasMoreElements(); ) {
                     String parameterName = e.nextElement();

                     if("Parameter[0]".equals(parameterName)) {
                        drillPath.removeParameterField(parameterName);
                     }
                  }
               }
            }
         }
      }

      String[] modelNames = model.getLogicalModelNames();

      if(modelNames != null) {
         for(String modelName : modelNames) {
            fixDrillPaths(model.getLogicalModel(modelName));
         }
      }
   }

   /**
    * Add a data model object to the repository.
    * @param dx the specified data model object.
    */
   public synchronized void setDataModel(XDataModel dx) {
      if(dx != null) {
         try {
            AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                              AssetEntry.Type.DATA_MODEL, dx.getDataSource(), null);
            setObject(entry, dx);
         }
         catch(Exception e) {
            LOG.error(
               "Failed to set data model: " + dx.getDataSource(), e);
         }
      }
   }

   /**
    * Remove a data model from the repository.
    * @param datasource the specified data source name.
    */
   protected synchronized void removeDataModel(String datasource) {
      if(!checkPermission(ResourceType.DATA_SOURCE, datasource, ResourceAction.DELETE)) {
         throw new SecurityException("Permission denied to modify datasource");
      }

      try {
         // read before the additional connections of the data source are removed below
         List<String> additionalResources = getAdditionalConnectionResources(datasource);
         //Remove all children. Because of the appended "/", the domain (if
         //present) won't be affected.
         removeObjects(getEntries(datasource + "/"));
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_MODEL, datasource, null);
         removeObject(entry);
         removeDataSourcePermissions(additionalResources);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove data model: " + datasource, e);
      }
   }

   /**
    * Fire event.
    */
   protected synchronized void fireEvent(StorageRefreshEvent e) {
      long nts = e.getLastModified();
      long currts;
      if(ts.containsKey(OrganizationManager.getInstance().getCurrentOrgID())) {
         currts = ts.get(OrganizationManager.getInstance().getCurrentOrgID());
      }
      else {
         currts = 0;
      }

      if(nts != 0 && nts != currts && currts < nts) {
         long actual = indexedStorage.lastModified(datasourceFilter);

         if(currts < actual) {
            clearCache();
            ts.put(OrganizationManager.getInstance().getCurrentOrgID(), nts);
            PropertyChangeEvent event =
               new PropertyChangeEvent(this, "DataSourceRegistry", null, null);
            final List<PropertyChangeListener> refreshedListeners =
               new ArrayList<>(this.refreshedListeners);

            (new GroupedThread() {
               protected void doRun() {
                  for(PropertyChangeListener listener : refreshedListeners) {
                     listener.propertyChange(event);
                  }
               }
            }).start();
         }
      }
   }

   /**
    * Add a refresh listener that will be notified if the datasource registry
    * has changed on disk.
    * @param listener the specified refresh listener.
    */
   public void addRefreshedListener(PropertyChangeListener listener) {
      refreshedListeners.add(listener);
   }

   /**
    * Remove a refresh listener.
    *
    * @param listener the specified refresh listener.
    */
   public void removeRefreshedListener(PropertyChangeListener listener) {
      refreshedListeners.remove(listener);
   }

   /**
    * Fire a modified event.
    */
   protected void fireModifiedEvent(String orgId) {
      final List<PropertyChangeListener> modifiedListeners =
         new ArrayList<>(this.modifiedListeners);

      (new GroupedThread() {
         protected void doRun() {
            for(PropertyChangeListener listener : modifiedListeners) {
               listener.propertyChange(
                  new PropertyChangeEvent(DataSourceRegistry.this,
                                          "DataSourceRegistry", null, null, orgId));
            }
         }
      }).start();
   }

   /**
    * Add a modified listener that will be notified if the query registry
    * has changed when saved.
    * @param listener the specified modified listener.
    */
   public void addModifiedListener(PropertyChangeListener listener) {
      modifiedListeners.add(listener);
   }

   /**
    * Remove a modified listener.
    * @param listener the specified modified listener.
    */
   public void removeModifiedListener(PropertyChangeListener listener) {
      modifiedListeners.remove(listener);
   }

   /**
    * Fire a connection event.
    */
   protected void fireConnectionChangeEvent() {
      try {
         Cluster.getInstance().sendMessage(new DataSourceConnectionChangedMessage());
      }
      catch(Exception e) {
         LOG.error("Failed to send DataSourceConnectionChangedMessage", e);
      }
   }

   /**
    * Checks whether the data source registry contains a particular object
    *
    * @param entry AssetEntry that describes the object.
    *
    * @return true if the registry contains the object
    */
   public boolean containObject(AssetEntry entry) {
      try {
         return indexedStorage.contains(entry.toIdentifier());
      }
      finally {
         indexedStorage.close();
      }
   }

   /**
    * Stores an object.
    * @param entry AssetEntry that describes the object
    * @param obj   the Object to be stored
    */
   public void setObject(AssetEntry entry, XMLSerializable obj) {
      setObject(entry, obj, true);
   }

   /**
    * Stores an object.
    * @param entry AssetEntry that describes the object
    * @param obj   the Object to be stored
    * @param fireEvent whether to fire an event after storing the object
    */
   public void setObject(AssetEntry entry, XMLSerializable obj, boolean fireEvent) {
      try {
         AssetFolder root = getRoot(entry.getOrgID());

         if(root != null && root.containsEntry(entry)) {
            entry = root.getEntry(entry);
            root.removeEntry(entry);
         }

         AssetUtil.updateMetaData(
            entry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());

         if(root != null) {
            root.addEntry(entry);
            setRoot(root);
         }

         indexedStorage.putXMLSerializable(entry.toIdentifier(), obj);
         clearCache2();
         sendClearDataSourceCacheEvent();

         if(fireEvent) {
            fireModifiedEvent(entry.getOrgID());
         }
         else {
            // update the last modified timestamp to prevent firing of the storage refresh event
            String orgId = OrganizationManager.getInstance().getCurrentOrgID();

            if(orgId != null) {
               this.ts.put(orgId, System.currentTimeMillis());
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to set object: " + entry.getPath(), e);
      }
      finally {
         indexedStorage.close();
      }
   }

   private void sendClearDataSourceCacheEvent() throws Exception {
      Cluster.getInstance().sendMessage(new ClearDataSourceCacheEvent(
         OrganizationManager.getInstance().getCurrentOrgID()));
   }

   /**
    * Updates a stored object
    *
    * @param oname the full path to the object
    * @param nname the full path of the new object
    * @param type  the AssetEntry type of the object
    * @param obj   the object to store
    */
   public void updateObject(String oname, String nname, AssetEntry.Type type, XMLSerializable obj) {
      try {
         moveObject(oname, nname, type, obj);
      }
      catch(Exception e) {
         LOG.error("Failed to update object: {}", oname, e);
      }
   }

   /**
    * Updates a stored object, like {@link #updateObject(String, String, AssetEntry.Type,
    * XMLSerializable)}, and throws a failed write.
    */
   private void moveObject(String oname, String nname, AssetEntry.Type type,
                           XMLSerializable obj) throws Exception
   {
      moveEntries(List.of(createMove(oname, nname, type, obj)));
   }

   /**
    * Creates the move of a stored object, the new entry keeping the properties and the creation
    * info of the old one.
    */
   private EntryMove createMove(String oname, String nname, AssetEntry.Type type,
                                XMLSerializable obj)
   {
      AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, oname, null);
      AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, nname, null);

      AssetEntry[] entries = getEntries(oentry.getParentPath(), type);
      String oldIdentifier = oentry.toIdentifier();

      oentry = Arrays.stream(entries)
         .filter(entry -> Tool.equals(entry.toIdentifier(), oldIdentifier))
         .findFirst()
         .orElse(oentry);

      nentry.copyProperties(oentry);
      // keep the original creation info, renaming or updating an object should only
      // change the last modified info, not who created it or when it was created
      nentry.setCreatedUsername(oentry.getCreatedUsername());
      nentry.setCreatedDate(oentry.getCreatedDate());
      return new EntryMove(oentry, nentry, obj, false, false);
   }

   /**
    * Updates a stored object
    *
    * @param oentry the old entry of the object
    * @param nentry the updated entry of the object
    * @param obj    the object to update
    */
   public void updateObject(AssetEntry oentry, AssetEntry nentry, XMLSerializable obj) {
      try {
         moveEntries(List.of(new EntryMove(oentry, nentry, obj, false, false)));
      }
      catch(Exception e) {
         LOG.error("Failed to update object: {}", oentry.getPath(), e);
      }
   }

   /**
    * Rename a set of objects stored in the registry - all whose full paths
    * start with oldPrefix. Be careful of cases where the name of one asset
    * is the prefix of another; don't forget to append an "/" to both oldPrefix
    * and newPrefix when necessary.
    * @param oldPrefix prefix of the paths of the objects to be renamed
    * @param newPrefix String to replace oldPrefix
    */
   public void renameObjects(String oldPrefix, String newPrefix) {
      renameObjects(oldPrefix, newPrefix, false);
   }

   /**
    * Rename a set of objects stored in the registry - all whose full paths
    * start with oldPrefix. Be careful of cases where the name of one asset
    * is the prefix of another; don't forget to append an "/" to both oldPrefix
    * and newPrefix when necessary.
    * @param oldPrefix prefix of the paths of the objects to be renamed
    * @param newPrefix String to replace oldPrefix
    */
   public void renameObjects(String oldPrefix, String newPrefix, boolean keepCreatedInfo) {
      renameObjects(oldPrefix, newPrefix, keepCreatedInfo, false);
   }

   /**
    * Rename a set of objects stored in the registry - all whose full paths
    * start with oldPrefix. Be careful of cases where the name of one asset
    * is the prefix of another; don't forget to append an "/" to both oldPrefix
    * and newPrefix when necessary.
    * @param oldPrefix prefix of the paths of the objects to be renamed
    * @param newPrefix String to replace oldPrefix
    */
   public void renameObjects(String oldPrefix, String newPrefix, boolean keepCreatedInfo,
                             boolean isAdditionalSource)
   {
      try {
         moveEntries(createMoves(oldPrefix, newPrefix, keepCreatedInfo, isAdditionalSource,
                                 Set.of()));
      }
      catch(Exception e) {
         LOG.error(
            "Failed to rename objects: " + oldPrefix, e);
      }
   }

   /**
    * Creates the moves of the objects whose paths start with a prefix.
    *
    * @param skipped the entries to leave, which the caller moves itself.
    */
   private List<EntryMove> createMoves(String oldPrefix, String newPrefix,
                                       boolean keepCreatedInfo, boolean isAdditionalSource,
                                       Set<AssetEntry> skipped)
   {
      AssetEntry[] entries = getEntries(oldPrefix);
      // an additional connection is a data source entry whose parent path is a data source
      // entry, as in renameDataSourceFolder
      Set<String> dsPaths = new HashSet<>();

      for(AssetEntry entry : entries) {
         if(entry.isDataSource()) {
            dsPaths.add(entry.getPath());
         }
      }

      List<EntryMove> moves = new ArrayList<>();

      for(AssetEntry oentry : entries) {
         String opath = oentry.getPath();

         if(!opath.startsWith(oldPrefix) || skipped.contains(oentry)) {
            continue;
         }

         String npath = newPrefix + opath.substring(oldPrefix.length());
         AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                            oentry.getType(), npath, null);
         if(keepCreatedInfo) {
            nentry.setCreatedUsername(oentry.getCreatedUsername());
            nentry.setCreatedDate(oentry.getCreatedDate());
         }

         nentry.copyProperties(oentry);
         int index = opath.lastIndexOf('/');
         boolean additional = isAdditionalSource ||
            index > 0 && dsPaths.contains(opath.substring(0, index));
         moves.add(new EntryMove(oentry, nentry, renameDomain(oentry, npath),
                                 oentry.isDataSource() && !additional,
                                 isAdditionalSource && oentry.isDataSource()));
      }

      return moves;
   }

   /**
    * Bug #77732, gets a copy of a moved data model or domain with its data source set to the new
    * path. Its path is the path of its data source, and a data model lists its logical models,
    * partitions and VPMs by its data source. The cached instance isn't changed, so a move that
    * fails leaves it as it was.
    *
    * @return the copy, or null to move the stored object as it is.
    */
   private XMLSerializable renameDomain(AssetEntry oentry, String npath) {
      if(oentry.getPath().equals(npath)) {
         return null;
      }

      if(oentry.getType() == AssetEntry.Type.DATA_MODEL) {
         if(getObject(oentry, true, false) instanceof XDataModel model) {
            XDataModel copy = model.clone();
            copy.setDataSource(npath);
            return copy;
         }
      }
      else if(oentry.getType() == AssetEntry.Type.DOMAIN) {
         if(getObject(oentry, true, false) instanceof XDomainWrapper wrapper &&
            wrapper.getDomain() != null && wrapper.getDomain().clone() instanceof XDomain copy)
         {
            copy.setDataSource(npath);
            return new XDomainWrapper(copy);
         }
      }

      return null;
   }

   /**
    * Moves stored objects to their new entries. Bug #77704, the index is the commit point: every
    * object is written under its new key first, then the index is saved once, and only then are
    * the old keys removed and the permissions moved. If a write fails before the index is saved,
    * what was written is removed again, the index is left as it was and the failure is thrown,
    * so nothing has moved and nothing is lost.
    */
   void moveEntries(List<EntryMove> moves) throws Exception {
      try {
         List<AssetEntry> oentries = new ArrayList<>();
         List<AssetEntry> nentries = new ArrayList<>();
         List<WrittenEntry> written = new ArrayList<>();
         String path = null;

         try {
            for(EntryMove move : moves) {
               AssetEntry oentry = move.oentry();
               AssetEntry nentry = move.nentry();
               path = oentry.getPath();
               AssetUtil.updateMetaData(
                  nentry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());
               oentries.add(oentry);
               nentries.add(nentry);
               boolean renamed = !oentry.toIdentifier().equals(nentry.toIdentifier());
               boolean created = renamed && !indexedStorage.contains(nentry.toIdentifier());
               XMLSerializable obj = move.obj() != null ?
                  move.obj() : getObject(oentry, false, false);

               // can't be loaded, e.g. a data source whose connector isn't installed or an
               // entry that is corrupt. Written again, it would be stored with no content.
               if(obj == null ||
                  obj instanceof XDataSourceWrapper wrapper && wrapper.getSource() == null ||
                  obj instanceof XDomainWrapper domain && domain.getDomain() == null)
               {
                  boolean moved = renamed && copyStoredDocument(oentry, nentry, move.name());
                  written.add(new WrittenEntry(oentry, nentry, created, renamed && !moved,
                                               moved));
               }
               else {
                  indexedStorage.putXMLSerializable(nentry.toIdentifier(), obj);
                  written.add(new WrittenEntry(oentry, nentry, created, renamed, false));
               }
            }

            // the index, not a single object
            path = null;
            updateRoot(oentries, nentries);
         }
         catch(Exception e) {
            // the index wasn't saved, take back what was written
            for(WrittenEntry entry : written.reversed()) {
               undo(entry);
            }

            throw new MoveEntriesException(path, false, e);
         }

         for(WrittenEntry entry : written) {
            cachemap.remove(entry.oentry());
            cachemap.remove(entry.nentry());

            if(entry.removeOld()) {
               removeStoredObject(entry.oentry());
            }
         }

         clearCache2();
         MoveEntriesException permissionFailure = null;

         // the objects have moved, a permission that can't be moved is kept under its old key
         // and reported, the others are still moved
         for(EntryMove move : moves) {
            AssetEntry oentry = move.oentry();
            AssetEntry nentry = move.nentry();

            if(move.sourcePermission()) {
               oentry = (AssetEntry) oentry.clone();
               oentry.setProperty("source", oentry.getParentPath() + "::" + oentry.getName());
               nentry = (AssetEntry) nentry.clone();
               nentry.setProperty("source", nentry.getParentPath() + "::" + nentry.getName());
            }

            Resource oresource = AssetUtil.getSecurityResource(oentry);
            Resource nresource = AssetUtil.getSecurityResource(nentry);

            try {
               updatePermission(oresource.getType(), oresource.getPath(), nresource.getPath());
            }
            catch(Exception e) {
               LOG.error("Failed to move the permission of {} to {}", oentry.getPath(),
                         nentry.getPath(), e);

               if(permissionFailure == null) {
                  permissionFailure = new MoveEntriesException(oentry.getPath(), true, e);
               }
            }
         }

         if(permissionFailure != null) {
            throw permissionFailure;
         }
      }
      finally {
         indexedStorage.close();
      }
   }

   /**
    * Thrown by {@link #moveEntries(List)}.
    */
   static class MoveEntriesException extends IOException {
      /**
       * @param path      the path of the object whose write failed, or null for the index.
       * @param committed {@code true} if the objects were moved and only a permission wasn't.
       */
      MoveEntriesException(String path, boolean committed, Exception cause) {
         super(cause.getMessage(), cause);
         this.path = path;
         this.committed = committed;
      }

      String getPath() {
         return path;
      }

      boolean isCommitted() {
         return committed;
      }

      private final String path;
      private final boolean committed;
   }

   /**
    * The move of a stored object.
    *
    * @param obj              the object to write, or null to write the stored one.
    * @param name             {@code true} to set the stored name to the new path if the stored
    *                         document is moved as it is, for a data source that isn't an
    *                         additional connection.
    * @param sourcePermission {@code true} if the permission is keyed by "parent::name", for an
    *                         additional connection.
    */
   record EntryMove(AssetEntry oentry, AssetEntry nentry, XMLSerializable obj,
                            boolean name, boolean sourcePermission)
   {
   }

   /**
    * An object written under its new key by {@link #moveEntries(List)}, before the index is
    * saved.
    *
    * @param created   {@code true} if the new key didn't exist before.
    * @param removeOld {@code true} if the old key is removed once the index is saved.
    * @param renamed   {@code true} if the stored document was renamed to the new key as it is,
    *                  so the old key no longer exists.
    */
   private record WrittenEntry(AssetEntry oentry, AssetEntry nentry, boolean created,
                               boolean removeOld, boolean renamed)
   {
   }

   // takes back what moveEntries wrote for an object, when the index can't be saved
   private void undo(WrittenEntry entry) {
      String okey = entry.oentry().toIdentifier();
      String nkey = entry.nentry().toIdentifier();

      try {
         if(entry.renamed()) {
            if(!indexedStorage.rename(nkey, okey, true)) {
               LOG.error("Failed to move {} back to {}", entry.nentry().getPath(),
                         entry.oentry().getPath());
            }
         }
         else if(entry.created()) {
            indexedStorage.remove(nkey, true);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to move {} back to {}", entry.nentry().getPath(),
                   entry.oentry().getPath(), e);
      }
   }

   /**
    * Removes and adds entries in the index and saves it. If it can't be saved, the cached index
    * is restored too.
    */
   private void updateRoot(List<AssetEntry> removed, List<AssetEntry> added) throws Exception {
      AssetFolder root = getRoot();
      Map<AssetEntry, AssetEntry> previous = new HashMap<>();

      for(AssetEntry entry : removed) {
         previous.put(entry, root.getEntry(entry));
      }

      for(AssetEntry entry : added) {
         previous.put(entry, root.getEntry(entry));
      }

      removed.forEach(root::removeEntry);
      added.forEach(root::addEntry);

      try {
         setRoot(root);
      }
      catch(Exception e) {
         previous.forEach((entry, stored) -> {
            root.removeEntry(entry);

            if(stored != null) {
               root.addEntry(stored);
            }
         });

         throw e;
      }
   }

   // removes a stored object that the index no longer lists, a failure leaves it unused
   private void removeStoredObject(AssetEntry entry) {
      try {
         indexedStorage.remove(entry.toIdentifier(), true);
      }
      catch(Exception e) {
         LOG.warn("Failed to remove the stored object {}", entry.getPath(), e);
      }
   }

   private void copyPermission(ResourceType type, String oldResource, String newResource) {
      SecurityEngine engine = SecurityEngine.getSecurity();

      if(engine.getSecurityProvider().isVirtual()) {
         return;
      }

      Permission permission = engine.getPermission(type, oldResource);

      if(permission != null) {
         savePermission(engine, type, newResource, permission);
      }
   }

   /**
    * Writes the stored document of an object that can't be loaded under the key of its new
    * entry as it is, so it can be loaded again once its connector is installed. The old key is
    * kept, unless the document isn't well-formed, in which case it is renamed as it is.
    *
    * @param oentry         the old entry of the object.
    * @param nentry         the new entry of the object.
    * @param dataSourceName {@code true} to set the stored name to the new path, for a data source
    *                       that isn't an additional connection.
    *
    * @return {@code true} if the stored document was renamed, so the old key no longer exists.
    *
    * @throws IOException if it can't be written.
    */
   private boolean copyStoredDocument(AssetEntry oentry, AssetEntry nentry,
                                      boolean dataSourceName) throws IOException
   {
      String okey = oentry.toIdentifier();
      String nkey = nentry.toIdentifier();
      Document doc;

      try {
         doc = indexedStorage.getDocument(okey, oentry.getOrgID());
      }
      catch(Exception e) {
         LOG.warn("Failed to read {}, moving it without changes", oentry.getPath(), e);
         doc = null;
      }

      // not well-formed, keep its content as it is under the new key
      if(doc == null || doc.getDocumentElement() == null) {
         if(!indexedStorage.contains(okey, oentry.getOrgID())) {
            LOG.warn("No stored document to move for {}", oentry.getPath());
            return false;
         }

         if(!indexedStorage.rename(okey, nkey, true)) {
            throw new IOException(
               "Failed to move " + oentry.getPath() + " to " + nentry.getPath());
         }

         return true;
      }

      String className = oentry.isDataSource() ? XDataSourceWrapper.class.getName() : null;

      // putDocument adds the processing instruction with the new identifier
      for(Node node = doc.getFirstChild(); node != null; ) {
         Node next = node.getNextSibling();

         if(node instanceof ProcessingInstruction pi && "inetsoft-asset".equals(pi.getTarget())) {
            Matcher matcher = CLASS_NAME_PATTERN.matcher(pi.getData());
            className = matcher.find() ? matcher.group(1) : className;
            doc.removeChild(pi);
         }

         node = next;
      }

      if(className == null) {
         throw new IOException("Failed to move " + oentry.getPath() + " to " +
                               nentry.getPath() + ", its class isn't known");
      }

      if(dataSourceName) {
         doc.getDocumentElement().setAttribute("name", nentry.getPath());
      }

      indexedStorage.putDocument(nkey, doc, className, nentry.getOrgID());

      // putDocument logs a failure instead of throwing it
      if(!indexedStorage.contains(nkey, nentry.getOrgID())) {
         throw new IOException("Failed to move " + oentry.getPath() + " to " + nentry.getPath());
      }

      return false;
   }

   /**
    * Gets the type stored for a data source without loading it, e.g. for a data source whose
    * connector isn't installed, which {@link #getDataSource(String)} returns null for.
    *
    * @param path the data source path.
    *
    * @return the type, or null if the data source has no stored document that can be read.
    */
   public String getStoredDataSourceType(String path) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, path, null);

      try {
         Document doc = indexedStorage.getDocument(entry.toIdentifier(), entry.getOrgID());
         Element elem = doc == null ? null : doc.getDocumentElement();
         return elem == null ? null : Tool.getAttribute(elem, "type");
      }
      catch(Exception e) {
         LOG.warn("Failed to read the stored data source: {}", path, e);
         return null;
      }
      finally {
         indexedStorage.close();
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event == null || !(event.getMessage() instanceof ClearDataSourceCacheEvent)) {
         return;
      }

      clearCache();
   }

   public void removeCacheEntry(Object key) {
      cachemap.remove(key);
   }

   /**
    * Gets an object from the registry
    *
    * @param entry   the AssetEntry describing the object
    * @return the object described by entry.
    */
   public XMLSerializable getObject(AssetEntry entry, boolean cloneIt) {
      return getObject(entry, true, cloneIt);
   }

   public void setCache(AssetEntry entry, XMLSerializable result) {
      CachedObject cache = cachemap.get(entry);
      long time = cache == null ? System.currentTimeMillis() : cache.timeTS;
      cachemap.put(entry, new CachedObject(time, result));
   }

   /**
    * Gets an object from the registry
    *
    * @param entry   the AssetEntry describing the object
    * @return the object described by entry.
    */
   private CachedObject getCachedObject(AssetEntry entry, boolean checkTS) {
      CachedObject obj = cachemap.get(entry); //NOSONAR can't use computeIfAbsent, b/c null would be present and not recomputed
      String orgId = entry.getOrgID();

      // fast path: if the registry-level timestamp for this org hasn't advanced past
      // when we cached this object, no individual entry in that org can have changed —
      // skip the per-entry Ignite lastModified call entirely
      if(obj != null && orgId != null) {
         Long registryTS = ts.get(orgId);

         if(registryTS != null && registryTS <= obj.timeTS) {
            return obj;
         }
      }

      String identifier = entry.toIdentifier();
      long timeTS = indexedStorage.lastModified(identifier, orgId);

      // if changed, throw out the cached value
      // timeTS as 0 means we didn't find it
      if(obj != null && timeTS > obj.timeTS || timeTS == 0) {
         obj = null;
      }

      return obj;
   }

   public XMLSerializable getObject(AssetEntry entry, boolean closeIndex, boolean cloneIt) {
      return getObject(entry, closeIndex, cloneIt, null);
   }

   public XMLSerializable getObject(AssetEntry entry, boolean closeIndex, boolean cloneIt,
                                    String orgId)
   {
      CachedObject obj = getCachedObject(entry, true);

      if(obj == null) {
         XMLSerializable result = null;
         String identifier = entry.toIdentifier();

         try {
            result = indexedStorage.getXMLSerializable(identifier, null, orgId);
         }
         catch(Exception e) {
            LOG.error("Failed to get object: {}", entry.getPath(), e);
         }
         finally {
            if(closeIndex) {
               indexedStorage.close();
            }
         }

         String hostOrgID = Organization.getDefaultOrganizationID();

         //if object is null, retry with host-org when global default visible
         if(result == null && SUtil.isDefaultVSGloballyVisible() && !ignoreGlobalShare() &&
            !Tool.equals(orgId, hostOrgID) && Tool.equals(entry.getOrgID(), hostOrgID))
         {
            try {
               result = indexedStorage.getXMLSerializable(identifier, null, hostOrgID);
            }
            catch(Exception e) {
               LOG.error("Failed to get object: {}", entry.getPath(), e);
            }
            finally {
               if(closeIndex) {
                  indexedStorage.close();
               }
            }
         }

         if(result != null) {
            obj = new CachedObject(System.currentTimeMillis(), result);
            cachemap.put(entry, obj);
         }
      }

      return obj == null ? null : obj.getObject(cloneIt);
   }

   private boolean ignoreGlobalShare() {
      Boolean isCheckingDuplicate = DataSourceRegistry.IGNORE_GLOBAL_SHARE.get();
      return isCheckingDuplicate != null && isCheckingDuplicate;
   }

   /**
    * Remove an object from the registry
    * @param entry the AssetEntry specifying the object to be removed
    */
   public void removeObject(AssetEntry entry) {
      try {
         AssetFolder root = getRoot();
         root.removeEntry(entry);
         setRoot(root);
         indexedStorage.remove(entry.toIdentifier(), true);
         clearCache2();
         sendClearDataSourceCacheEvent();

         //delete vpm, should not delete data source permissions
         if(entry.isVPM()) {
            return;
         }

         Resource resource = AssetUtil.getSecurityResource(entry);
         updatePermission(resource.getType(), resource.getPath(), null);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove object: " + entry.getPath(), e);
      }
      finally {
         indexedStorage.close();
      }
   }

   /**
    * Remove multiple objects
    * @param entries the AssetEntry Array specifying the objects to be removed
    */
   public void removeObjects(AssetEntry[] entries) {
      try {
         AssetFolder root = getRoot();

         for(AssetEntry entry : entries) {
            root.removeEntry(entry);
            indexedStorage.remove(entry.toIdentifier(), true);
            Resource resource = AssetUtil.getSecurityResource(entry);
            updatePermission(resource.getType(), resource.getPath(), null);
            clearCache2();
         }

         sendClearDataSourceCacheEvent();
         setRoot(root);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove objects from DataRegistry", e);
      }
      finally {
         indexedStorage.close();
      }
   }

   public boolean checkPermission(ResourceType type, String resource, ResourceAction action) {
      // Bug #77539: an access a sheet script makes itself must be one its user may make;
      // the data sources of a running asset are not checked here, as before
      return ScriptDataSourceAccess.permits(type, resource, action);
   }

   protected void updatePermission(ResourceType type, String oldResource, String newResource) {
      if(type == null || oldResource == null || newResource == null) {
         return;
      }

      SecurityEngine engine = SecurityEngine.getSecurity();

      if(engine.getSecurityProvider().isVirtual()) {
         return;
      }

      Permission permission = engine.getPermission(type, oldResource);

      // Bug #77704, removed from the old key only once it can be read back under the new one.
      // The authorization provider logs a failed save instead of throwing it.
      if(permission != null && !oldResource.equals(newResource)) {
         savePermission(engine, type, newResource, permission);
         engine.removePermission(type, oldResource);

         if(engine.getPermission(type, oldResource) != null) {
            LOG.warn("Failed to remove the permission of {} {} after it was moved to {}",
                     type, oldResource, newResource);
         }
      }
   }

   /**
    * Saves a permission and checks that it can be read back, since the authorization provider
    * logs a failed save instead of throwing it.
    *
    * @throws UncheckedIOException if it isn't saved.
    */
   private static void savePermission(SecurityEngine engine, ResourceType type, String resource,
                                      Permission permission)
   {
      engine.setPermission(type, resource, permission);

      // a blank permission is removed instead
      if(!permission.isBlank() && !permission.equals(engine.getPermission(type, resource))) {
         throw new UncheckedIOException(new IOException(
            "Failed to save the permission of " + type + " " + resource));
      }
   }

   /**
    * Helper method for getting the entries of all assets stored in the registry
    * with paths that start with the given prefix.
    * @param prefix the prefix String of the paths of all assets returned
    * @return an AssetEntry Array specifying all the relevant assets
    */
   public AssetEntry[] getEntries(String prefix) {
      ArrayList<AssetEntry> result;
      result = new ArrayList<>();
      AssetEntry[] allEntries = getRoot().getEntries();

      for(AssetEntry entry : allEntries) {
         if(entry.getPath().startsWith(prefix)) {
            result.add(entry);
         }
      }

      return result.toArray(new AssetEntry[0]);
   }

   /**
    * Helper method for getting the entries of all assets stored in the registry
    * with paths that start with the given prefix and match the AssetEntry type
    * specified by the type argument.
    * @param prefix the prefix String of the paths of all assets returned
    * @param type   the AssetEntry type with which to filter results by
    * @return an AssetEntry Array specifying all the relevant assets
    */
   public AssetEntry[] getEntries(String prefix, AssetEntry.Type type) {
      ArrayList<AssetEntry> result = new ArrayList<>();
      AssetEntry[] allEntries = getRoot().getEntries();

      for(AssetEntry entry : allEntries) {
         if(entry.getPath().startsWith(prefix) && entry.getType() == type &&
            // bug #60767, handle corrupt registry where folder contains missing asset
            containObject(entry))
         {
            result.add(entry);
         }
      }

      return result.toArray(new AssetEntry[0]);
   }

   @EventListener(PluginRemovedEvent.class)
   public void onPluginRemoved(PluginRemovedEvent event) {
      clearCache();
   }

   public void clearCache() {
      cachemap.clear();
      clearCache2(null);
   }

   public void clearCache(String orgId) {
      clearCache2(orgId);
   }

   private void clearCache2() {
      clearCache2(null);
   }

   private void clearCache2(String orgId) {
      if(orgId != null) {
         allFolders.remove(orgId);
         allDataSources.remove(orgId);
      }
      else {
         allFolders.clear();
         allDataSources.clear();
      }
   }

   /**
    * Checks whether a datasource is supported by the software
    */
   private boolean isSupported(XDataSource source) {
      boolean supported = false;

      if(source != null) {
         String type = source.getType();
         supported = true;

         if(type != null && uqlConfig.getDataSourceClass(type) == null) {
            supported = false;
         }

         if(!supported) {
            LOG.warn("Unsupported data source: {}", source.getFullName());
         }
      }

      return supported;
   }

   /**
    * Initializes and stores the AssetFolder that serves as the root of the
    * data source registry.
    */
   public void init() throws Exception {
      try {
         initLastModified();

         if(!indexedStorage.contains(getRootIdentifier())) {
            setRoot(new AssetFolder());
         }
      }
      finally {
         indexedStorage.close();
      }

      reportDataSourcePathClashes();
   }

   /**
    * Get the AssetFolder that stores all the AssetEntries of assets stored in
    * this registry.
    */
   private AssetFolder getRoot() {
      // avoid loading root in parallel (multiple threads).
      rootLock.lock();

      try {
         AssetEntry entry = getRootEntry();
         return (AssetFolder) getObject(entry, true, false);
      }
      finally {
         rootLock.unlock();
      }
   }

   /**
    * Get the AssetFolder that stores all the AssetEntries of assets stored in
    * this registry.
    */
   private AssetFolder getRoot(String orgID) {
      // avoid loading root in parallel (multiple threads).
      rootLock.lock();

      try {
         AssetEntry entry = getRootEntry();
         entry.setOrgID(orgID);
         return (AssetFolder) getObject(entry, true, false, orgID);
      }
      finally {
         rootLock.unlock();
      }
   }

   /**
    * Change the root folder.
    */
   private void setRoot(AssetFolder root) throws Exception {
      try {
         indexedStorage.putXMLSerializable(getRootIdentifier(), root);
         setCache(getRootEntry(), root);
         clearCache2();
      }
      finally {
         indexedStorage.close();
      }
   }

   /**
    * Helper method. Get the String identifier of the AssetEntry that specifies
    * the root of this registry.
    */
   private String getRootIdentifier() {
      AssetEntry rootEntry = getRootEntry();
      return rootEntry.toIdentifier();
   }

   private AssetEntry getRootEntry() {
      return new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE_FOLDER, "/", null);
   }

   /**
    * Helper method. Get the full paths of all assets stored in this registry
    * that match the AssetEntry type given.
    * @param type the type of the entries to be returned - see AssetEntry
    */
   private Map<String, List<String>> getFullNames(AssetEntry.Type type) {
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();
      return getFullNames(type, orgID);
   }

   /**
    * Helper method. Get the full paths of all assets stored in this registry
    * that match the AssetEntry type given.
    * @param type the type of the entries to be returned - see AssetEntry
    */
   private Map<String, List<String>> getFullNames(AssetEntry.Type type, String orgID) {
      Map<String, List<String>> cachedNames;

      if(type == AssetEntry.Type.DATA_SOURCE_FOLDER) {
         cachedNames = this.allFolders.computeIfAbsent(orgID, k -> new ConcurrentHashMap<>());
      }
      else {
         cachedNames = this.allDataSources.computeIfAbsent(orgID, k -> new ConcurrentHashMap<>());
      }

      synchronized(cachedNames) {
         if(!cachedNames.isEmpty()) {
            return cachedNames;
         }

         AssetFolder root = getRoot(orgID);

         try {
            List<AssetEntry> entries = root != null ? root.getEntries(type) : Collections.emptyList();

            // Bug #77539: the cache is shared by every caller, so it holds every name;
            // a listing a script makes itself is filtered per call (readable)
            for(AssetEntry entry : entries) {
               String name = entry.getPath();
               String key = getFirstFolder(name);
               List<String> list = cachedNames.computeIfAbsent(key, k -> new ArrayList<>());
               list.add(name);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to get asset names", e);
         }
      }

      return cachedNames;
   }

   /**
    * Gets indexedStorage
    * @return indexedStorage
    */
   protected IndexedStorage getIndexedStorage() {
      return indexedStorage;
   }

   /**
    * A cached value.
    */
   private static class CachedObject {
      CachedObject(long timeTS, XMLSerializable object) {
         this.timeTS = timeTS;
         this.object = object;
      }

      XMLSerializable getObject(boolean cloneIt) {
         if(object == null) {
            return null;
         }

         if(cloneIt && object instanceof Cloneable) {
            try {
               return (XMLSerializable) cloneMethod.invoke(object);
            }
            catch(Exception e) {
               LOG.warn("Failed to clone object", e);
            }
         }

         return object;
      }

      final long timeTS;
      final XMLSerializable object;
      private static final Method cloneMethod;

      static {
         try {
            cloneMethod = Object.class.getDeclaredMethod("clone");
            cloneMethod.setAccessible(true);
         }
         catch(Exception e) {
            throw new ExceptionInInitializerError(e);
         }
      }
   }

   public void setExistQueryFolders(String[] folders) {
      this.existQueryFolders = folders;
   }

   public static boolean matchesDataSourceFilter(String key) {
      return key.startsWith(FILTER_PREFIX) && FILTERED_ASSET_TYPES.stream()
         .map(t -> AssetRepository.QUERY_SCOPE + "^" + t.id() + "^")
         .anyMatch(key::startsWith);
   }

   /**
    * Message that notifies the nodes in the cluster that a datasource connection has changed.
    */
   public static class DataSourceConnectionChangedMessage implements Serializable {
      private static final long serialVersionUID = 1L;
   }

   private String[] existQueryFolders = new String[0];
   private ConcurrentHashMap<String, Long> ts = new ConcurrentHashMap<>(); // last modified timestamp
   private final List<PropertyChangeListener> refreshedListeners = Collections.synchronizedList(new ArrayList<>());
   private final List<PropertyChangeListener> modifiedListeners = Collections.synchronizedList(new ArrayList<>());
   private final IndexedStorage indexedStorage;
   private final Config uqlConfig;
   private final Cluster cluster;
   private final Map<Object, CachedObject> cachemap = new ConcurrentHashMap<>();
   private final Map<String, Map<String, List<String>>> allFolders = new ConcurrentHashMap<>();
   private final Map<String, Map<String, List<String>>> allDataSources = new ConcurrentHashMap<>();

   private final Map<String, String> drillPathsFixed = new ConcurrentHashMap<>();
   // the organizations whose data source path clashes are reported
   private final Set<String> clashesReported = ConcurrentHashMap.newKeySet();
   private final Lock rootLock = new ReentrantLock();

   private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("classname=\"([^\"]*)\"");
   private static final Logger LOG = LoggerFactory.getLogger(DataSourceRegistry.class);

   private static final String FILTER_PREFIX = AssetRepository.QUERY_SCOPE + "^";
   private static final EnumSet<AssetEntry.Type> FILTERED_ASSET_TYPES = EnumSet.of(
      AssetEntry.Type.DATA_SOURCE, AssetEntry.Type.DATA_SOURCE_FOLDER, AssetEntry.Type.DATA_MODEL,
      AssetEntry.Type.PARTITION, AssetEntry.Type.EXTENDED_PARTITION, AssetEntry.Type.LOGIC_MODEL,
      AssetEntry.Type.EXTENDED_LOGIC_MODEL, AssetEntry.Type.VPM, AssetEntry.Type.DOMAIN);

   private static final IndexedStorage.Filter datasourceFilter =
      DataSourceRegistry::matchesDataSourceFilter;
   public static final ThreadLocal<Boolean> IGNORE_GLOBAL_SHARE = ThreadLocal.withInitial(() -> false);

}

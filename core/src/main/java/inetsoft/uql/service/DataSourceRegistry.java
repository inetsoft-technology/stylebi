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
package inetsoft.uql.service;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.util.*;
import inetsoft.uql.xmla.Domain;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.SecurityException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Data source registry stores information on all data sources. The
 * information is stored in a XML file. Applications should not use
 * the data source registry directly. The registry information can
 * be accessed through the XRepository service API.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(DataSourceRegistry.Reference.class)
public class DataSourceRegistry implements MessageListener {
   /**
    * Get data source registry.
    * @return data source registry if any, null otherwise.
    */
   public static DataSourceRegistry getRegistry() {
      return SingletonManager.getInstance(DataSourceRegistry.class);
   }

   /**
    * Constructor.
    */
   public DataSourceRegistry() throws Exception {
      indexedStorage = initIndexedStorage();
      initLastModified();
      indexedStorage.addStorageRefreshListener(DataSourceRegistry.this::fireEvent);

      // @by stephenwebster, For Align Kpital.
      // Using getRoot() here is problematic since the getObject method caches
      // null results.  If the registry needs to be ported, calling getRoot
      // again immediately after may result in getting the cached null result,
      // so the port will fail.  Instead, use a one-time check on the indexed
      // storage directly to see whether the root exists, which is done in the
      // initRoot method.  I have modified initRoot to return a boolean value
      // to indicate whether the root was setup or not.
      // This init method has been moved to the authentication service on login.
      Cluster.getInstance().addMessageListener(this);
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
            DataSourceFolder folder = getDataSourceFolder(parent);

            if(folder == null) {
               XDataSource dataSource = getDataSource(parent);

               // for importing, remove the data source when it`s name is same to importing data
               // source folder.because the importing data source path will be same to exist
               // source`s additional data source.
               if(dataSource != null) {
                  removeDataSource(parent);
                  LOG.warn(Catalog.getCatalog().getString("Overwrite Existing Files") +
                     parent);
               }

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
      return getDataSourceFullNames0(unfilteredNames);
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
         return getDataSourceFullNames0(unfilteredNames);
      }

      return new String[0];
   }

   private static String[] getDataSourceFullNames0(String[] unfilteredNames) {
      List<String> result = new ArrayList<>();

      for(String name : unfilteredNames) {
         boolean isAdditional = false;

         // check if any datasource's path is a prefix to name.
         // if so, that datasource is the base datasource, and "name" is the
         // name of an additional connection
         for(String baseDS : unfilteredNames) {
            if(name.startsWith(baseDS + "/")) {
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
      return getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER).values().stream()
         .flatMap(List::stream).toArray(String[]::new);
   }

   public String[] getDataSourceFolderFullNames(String prefix) {
      String key = getFirstFolder(prefix);
      Map<String, List<String>> allFolders = getFullNames(AssetEntry.Type.DATA_SOURCE_FOLDER);

      if(key.isEmpty() || key.equals("/")) {
         return getDataSourceFolderFullNames();
      }

      List<String> names = allFolders.get(key);
      return names != null ? names.toArray(new String[0]) : new String[0];
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
         XDataSourceWrapper wrapper = (XDataSourceWrapper) getObject(entry, true);

         if(wrapper != null) {
            result = wrapper.getSource();
            result = isSupported(result) ? result : null;
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
      setDataSource(dx, null, true, false, isImport);
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx           the specified data source.
    * @param oname        only used in RempteDataSourceRegistry.
    * @param actionRecord only used in RemoteDataSourceRegistry.
    * @param checkDelete  check the delete permission.
    */
   public synchronized void setDataSource(XDataSource dx, String oname, Boolean actionRecord,
                                          boolean checkDelete, boolean isImport)
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

            setObject(entry, new XDataSourceWrapper(dx));

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
      try {
         removeObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
                                     AssetEntry.Type.DATA_SOURCE, dxname, null));
         removeObjects(getEntries(dxname + "/"));
         removeObject(new AssetEntry(AssetRepository.QUERY_SCOPE,
            AssetEntry.Type.DATA_MODEL, dxname, null));
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
            dmodel.addFolder(Tool.getAttribute(node, "name"));
         }
      }

      setDataModel(dmodel);
   }

   /**
    * Remove a data source folder and its children from the repository.
    * @param name the specified data source folder name.
    */
   public synchronized void removeDataSourceFolder(String name) {
      if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, name, ResourceAction.DELETE)) {
         throw new SecurityException(Catalog.getCatalog().getString(
            "Permission denied to delete datasource folder"));
      }

      try {
         AssetEntry[] allDSChildren =
            getEntries(name + "/", AssetEntry.Type.DATA_SOURCE);
         AssetEntry[] allFolderChildren =
            getEntries(name + "/", AssetEntry.Type.DATA_SOURCE_FOLDER);

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
         }

         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_SOURCE_FOLDER, name, null);
         removeObject(entry);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to remove datasource folder: " + name, e);
      }
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
    * Data source renamed, sync data model and domain.
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

      XDomain domain = getDomain(oname);
      XDataModel model = getDataModel(oname);
      ds.setName(nname);

      if(model != null) {
         model.setDataSource(nname);
         updateObject(oname, nname, AssetEntry.Type.DATA_MODEL, model);
      }

      if(domain != null && !(domain instanceof XDataModel)) {
         domain.setDataSource(nname);
         updateObject(oname, nname, AssetEntry.Type.DOMAIN,
                      new XDomainWrapper(domain));
      }

      updateObject(oname, nname, AssetEntry.Type.DATA_SOURCE,
                   new XDataSourceWrapper(ds));
      renameObjects(oname + "/", nname + "/", false,
         ds instanceof AdditionalConnectionDataSource);
      updateQueryFolders(ds, oname);
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

      DataSourceFolder folder = getDataSourceFolder(oname);

      if(folder == null) {
         return;
      }

      checkDSFolderRenamePermission(oname);

      try {
         AssetEntry[] allDSChildren =
            getEntries(oname + "/", AssetEntry.Type.DATA_SOURCE);
         AssetEntry[] allFolderChildren =
            getEntries(oname + "/", AssetEntry.Type.DATA_SOURCE_FOLDER);

         for(AssetEntry entry : allDSChildren) {
            String opath = entry.getPath();
            String npath = nname + opath.substring(oname.length());
            renameDatasource(opath, npath);
         }

         for(AssetEntry entry : allFolderChildren) {
            checkDSFolderRenamePermission(entry.getPath());
            DataSourceFolder dsfolder = getDataSourceFolder(entry.getPath());
            String opath = entry.getPath();
            String npath = nname + opath.substring(oname.length());
            dsfolder.setName(npath);
            updateObject(opath, npath, AssetEntry.Type.DATA_SOURCE_FOLDER, dsfolder);
         }

         folder.setName(nname);
         updateObject(oname, nname, AssetEntry.Type.DATA_SOURCE_FOLDER, folder);
         renameObjects(oname + "/", nname + "/");
      }
      catch(SecurityException se) {
         throw se;
      }
      catch(Exception e) {
         LOG.error(
            "Failed to rename datasource folder: " + oname, e);
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

   private XRepository getXRepository() {
      try {
         return XFactory.getRepository();
      }
      catch(Exception ex) {
         throw new RuntimeException("Failed to get xrepository ", ex);
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
         //Remove all children. Because of the appended "/", the domain (if
         //present) won't be affected.
         removeObjects(getEntries(datasource + "/"));
         AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                           AssetEntry.Type.DATA_MODEL, datasource, null);
         removeObject(entry);
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
   protected void fireModifiedEvent() {
      final List<PropertyChangeListener> modifiedListeners =
         new ArrayList<>(this.modifiedListeners);

      (new GroupedThread() {
         protected void doRun() {
            for(PropertyChangeListener listener : modifiedListeners) {
               listener.propertyChange(
                  new PropertyChangeEvent(DataSourceRegistry.this,
                                          "DataSourceRegistry", null, null));
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
      try {
         AssetFolder root = getRoot();

         if(root.containsEntry(entry)) {
            entry = root.getEntry(entry);
            root.removeEntry(entry);
         }

         AssetUtil.updateMetaData(
            entry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());
         root.addEntry(entry);
         setRoot(root);

         indexedStorage.putXMLSerializable(entry.toIdentifier(), obj);
         clearCache2();
         Cluster.getInstance().sendMessage(new ClearDataSourceCacheEvent());

         fireModifiedEvent();
      }
      catch(Exception e) {
         LOG.error("Failed to set object: " + entry.getPath(), e);
      }
      finally {
         indexedStorage.close();
      }
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
      AssetEntry oentry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, oname, null);
      AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, nname, null);

      AssetEntry[] entries = getEntries(oentry.getParentPath(), type);
      String oldIdentifier = oentry.toIdentifier();

      oentry = Arrays.stream(entries)
         .filter(entry -> Tool.equals(entry.toIdentifier(), oldIdentifier))
         .findFirst()
         .orElse(oentry);

      nentry.copyProperties(oentry);
      updateObject(oentry, nentry, obj);
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
         AssetUtil.updateMetaData(
            nentry, ThreadContext.getContextPrincipal(), System.currentTimeMillis());
         AssetFolder root = getRoot();
         root.removeEntry(oentry);
         cachemap.remove(oentry);
         clearCache2();
         root.addEntry(nentry);
         setRoot(root);

         if(!oentry.toIdentifier().equals(nentry.toIdentifier())) {
            indexedStorage.remove(oentry.toIdentifier(), true);
         }

         indexedStorage.putXMLSerializable(nentry.toIdentifier(), obj);
         Resource oresource = AssetUtil.getSecurityResource(oentry);
         Resource nresource = AssetUtil.getSecurityResource(nentry);
         updatePermission(oresource.getType(), oresource.getPath(), nresource.getPath());
      }
      catch(Exception e) {
         LOG.error("Failed to update object: {}", oentry.getPath(), e);
      }
      finally {
         indexedStorage.close();
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
         AssetEntry[] entries = getEntries(oldPrefix);
         AssetFolder root = getRoot();

         for(AssetEntry oentry : entries) {
            String opath = oentry.getPath();

            if(opath.startsWith(oldPrefix)) {
               String npath = newPrefix + opath.substring(oldPrefix.length());
               AssetEntry nentry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                                  oentry.getType(), npath, null);
               if(keepCreatedInfo) {
                  nentry.setCreatedUsername(oentry.getCreatedUsername());
                  nentry.setCreatedDate(oentry.getCreatedDate());
               }

               nentry.copyProperties(oentry);
               AssetUtil.updateMetaData(
                  nentry, ThreadContext.getContextPrincipal(),
                  System.currentTimeMillis());
               root.removeEntry(oentry);
               root.addEntry(nentry);
               XMLSerializable obj = getObject(oentry, false, false);
               indexedStorage.remove(oentry.toIdentifier(), true);
               indexedStorage.putXMLSerializable(nentry.toIdentifier(), obj);
               clearCache2();

               if(isAdditionalSource && oentry.isDataSource()) {
                  oentry = (AssetEntry) oentry.clone();
                  oentry.setProperty("source", oentry.getParentPath() + "::" + oentry.getName());
                  nentry = (AssetEntry) nentry.clone();
                  nentry.setProperty("source", nentry.getParentPath() + "::" + nentry.getName());
               }

               Resource oresource = AssetUtil.getSecurityResource(oentry);
               Resource nresource = AssetUtil.getSecurityResource(nentry);
               updatePermission(oresource.getType(), oresource.getPath(), nresource.getPath());
            }
         }

         setRoot(root);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to rename objects: " + oldPrefix, e);
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
      String identifier = entry.toIdentifier();
      long timeTS = indexedStorage.lastModified(identifier, entry.getOrgID());

      // if changed, throw out the cached value
      // timeTS as 0 means we didn't find it
      if(obj != null && timeTS > obj.timeTS || timeTS == 0) {
         obj = null;
      }

      return obj;
   }

   public XMLSerializable getObject(AssetEntry entry, boolean closeIndex, boolean cloneIt) {
      CachedObject obj = getCachedObject(entry, true);

      if(obj == null) {
         XMLSerializable result = null;
         String identifier = entry.toIdentifier();

         try {
            result = indexedStorage.getXMLSerializable(identifier, null);
         }
         catch(Exception e) {
            LOG.error("Failed to get object: {}", entry.getPath(), e);
         }
         finally {
            if(closeIndex) {
               indexedStorage.close();
            }
         }

         if(result != null) {
            obj = new CachedObject(System.currentTimeMillis(), result);
            cachemap.put(entry, obj);
         }
      }

      return obj == null ? null : obj.getObject(cloneIt);
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
         Cluster.getInstance().sendMessage(new ClearDataSourceCacheEvent());

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

         Cluster.getInstance().sendMessage(new ClearDataSourceCacheEvent());
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

   public boolean checkPermission(@SuppressWarnings("unused") ResourceType type,
                                  @SuppressWarnings("unused") String resource,
                                  @SuppressWarnings("unused") ResourceAction action)
   {
      // Do nothing.
      return true;
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

      if(permission != null) {
         engine.removePermission(type, oldResource);
         engine.setPermission(type, newResource, permission);
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

   public void clearCache() {
      cachemap.clear();
      clearCache2();
   }

   private void clearCache2() {
      allFolders.clear();
      allDataSources.clear();
   }

   /**
    * Checks whether a datasource is supported by the software
    */
   private boolean isSupported(XDataSource source) {
      boolean supported = false;

      if(source != null) {
         String type = source.getType();
         supported = true;

         if(type != null && Config.getDataSourceClass(type) == null) {
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
      ResourceType resourceType;
      Map<String, List<String>> cachedNames;
      String orgID = OrganizationManager.getInstance().getCurrentOrgID();

      if(type == AssetEntry.Type.DATA_SOURCE_FOLDER) {
         resourceType = ResourceType.DATA_SOURCE_FOLDER;

         if(this.allFolders.containsKey(orgID)) {
            cachedNames = this.allFolders.get(orgID);
         }
         else {
            cachedNames = new ConcurrentHashMap<>();
            this.allFolders.put(orgID, cachedNames);
         }
      }
      else {
         resourceType = ResourceType.DATA_SOURCE;

         if(this.allDataSources.containsKey(orgID)) {
            cachedNames = this.allDataSources.get(orgID);
         }
         else {
            cachedNames = new ConcurrentHashMap<>();
            this.allDataSources.put(orgID, cachedNames);
         }
      }

      synchronized(cachedNames) {
         if(!cachedNames.isEmpty()) {
            return cachedNames;
         }

         AssetFolder root = getRoot();

         try {
            List<AssetEntry> entries = root != null ? root.getEntries(type) : Collections.emptyList();

            for(AssetEntry entry : entries) {
               String name = entry.getPath();

               if(checkPermission(resourceType, name, ResourceAction.READ)) {
                  String key = getFirstFolder(name);
                  List<String> list = cachedNames.computeIfAbsent(key, k -> new ArrayList<>());
                  list.add(name);
               }
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

   private static boolean matchesDataSourceFilter(String key) {
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
   private final List<PropertyChangeListener> refreshedListeners = new ArrayList<>();
   private final List<PropertyChangeListener> modifiedListeners = new ArrayList<>();
   private final IndexedStorage indexedStorage;
   private final Map<Object, CachedObject> cachemap = new ConcurrentHashMap<>();
   private final Map<String, Map<String, List<String>>> allFolders = new ConcurrentHashMap<>();
   private final Map<String, Map<String, List<String>>> allDataSources = new ConcurrentHashMap<>();

   private final Map<String, String> drillPathsFixed = new ConcurrentHashMap<>();
   private final Lock rootLock = new ReentrantLock();

   private static final Logger LOG = LoggerFactory.getLogger(DataSourceRegistry.class);

   private static final String FILTER_PREFIX = AssetRepository.QUERY_SCOPE + "^";
   private static final EnumSet<AssetEntry.Type> FILTERED_ASSET_TYPES = EnumSet.of(
      AssetEntry.Type.DATA_SOURCE, AssetEntry.Type.DATA_SOURCE_FOLDER, AssetEntry.Type.DATA_MODEL,
      AssetEntry.Type.PARTITION, AssetEntry.Type.EXTENDED_PARTITION, AssetEntry.Type.LOGIC_MODEL,
      AssetEntry.Type.EXTENDED_LOGIC_MODEL, AssetEntry.Type.VPM, AssetEntry.Type.DOMAIN);

   private static final IndexedStorage.Filter datasourceFilter =
      DataSourceRegistry::matchesDataSourceFilter;

   public static final class Reference // NOSONAR this is an inner class that is only referenced in the annotation
      extends SingletonManager.Reference<DataSourceRegistry>
   {
      @Override
      public synchronized DataSourceRegistry get(Object... parameters) {
         if(registry == null) {
            try {
               registry = new DataSourceRegistry();
            }
            catch(SAXParseException e) {
               LOG.error(String.format(
                  "Parsing error: line %d column %d, %s",
                  e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }
            catch(Exception e) {
               LOG.error("Failed to load data source registry file", e);
            }
         }

         return registry;
      }

      @Override
      public synchronized void dispose() {
         if(registry != null) {
            Cluster.getInstance().removeMessageListener(registry);
            registry = null;
         }
      }

      private DataSourceRegistry registry;
   }
}

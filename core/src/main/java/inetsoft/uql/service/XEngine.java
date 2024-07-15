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

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.XSessionManager;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.*;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.*;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.impl.TabularHandler;
import inetsoft.uql.util.*;
import inetsoft.uql.xmla.Domain;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.web.cluster.ClearLocalNodeMetaDataCacheMessage;
import inetsoft.web.cluster.RefreshMetaDataMessage;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

/**
 * This class implements the XRepository (and XDataService) API. It is the
 * query engine that executes queries.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XEngine implements XRepository, XQueryRepository {
   /**
    * Create an query engine.u
    */
   public XEngine() {
      super();
      cluster = Cluster.getInstance();
      cluster.addMessageListener(messageListener);
   }

   /**
    * Load data source meta data in background.
    */
   private void loadMetaData(final String dx) {
      Thread thread = new GroupedThread() {
         @Override
         protected void doRun() {
            loadMetaData0(dx);
         }
      };

      // pre-load meta data so there is no long delay
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setName(JDBCHandler.REFRESH_META_DATA);
      thread.start();
   }

   /**
    * Load data source meta data.
    */
   private void loadMetaData0(String dx) {
      if(getDSRegistry().getDataSource(dx) == null) {
         return;
      }

      Object session = System.getProperty("user.name");
      String[] names = dx != null ? new String[] {dx} : getDataSourceNames();

      for(String name : names) {
         try {
            final XNode mtype = new XNode();
            getMetaData(session, getDataSource(name), mtype, true, null);
         }
         catch(Exception ex) {
            LOG.debug("Failed to get meta-data for data source: " + dx, ex);
         }
      }
   }

   /**
    * Get query type in the query repository.
    * @return query type which is one of the predefined types in query
    * repository.
    */
   @Override
   public int getQueryType() {
      return NORMAL_QUERY;
   }

   /**
    * Get the names of data sources in this repository.
    */
   @Override
   public String[] getDataSourceNames() {
      return getDSRegistry().getDataSourceNames();
   }

   /**
    * Get full names of all the data sources in this repository.
    * @return full names of all the data sources.
    */
   @Override
   public String[] getDataSourceFullNames() {
      return getDSRegistry().getDataSourceFullNames();
   }

   /**
    * Get all data source folder names in repository.
    * @return names of all the data source folders.
    */
   @Override
   public String[] getDataSourceFolderNames() {
      return getDSRegistry().getDataSourceFolderNames();
   }

   /**
    * Get all data source folder names in repository.
    * @return full names of all the data source folders.
    */
   @Override
   public String[] getDataSourceFolderFullNames() {
      return getDSRegistry().getDataSourceFolderFullNames();
   }

   /**
    * Get the named data source.
    */
   @Override
   public XDataSource getDataSource(String dsname) {
      return getDataSource(dsname, true);
   }

   /**
    * Get the named data source.
    */
   @Override
   public XDataSource getDataSource(String dsname, boolean clone) {
      XDataSource dx = getDSRegistry().getDataSource(dsname);

      if(clone) {
         try {
            dx = (XDataSource) dx.clone();
         }
         catch(Exception ignore) {
         }
      }

      return dx;
   }

   /**
    * Get the named data source folder.
    */
   @Override
   public DataSourceFolder getDataSourceFolder(String dsname) {
      return getDSRegistry().getDataSourceFolder(dsname);
   }

   /**
    * Get the named data source folder.
    * @param clone true to clone data source folder, false otherwise.
    */
   @Override
   public DataSourceFolder getDataSourceFolder(String dsname, boolean clone) {
      DataSourceFolder folder = getDSRegistry().getDataSourceFolder(dsname);

      if(clone) {
         try {
            folder = (DataSourceFolder) folder.clone();
         }
         catch(Exception ignore) {
         }
      }

      return folder;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void updateDataSource(XDataSource dx, String oname) throws Exception {
      updateDataSource(dx, oname, true, false);
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   public void updateDataSource(XDataSource dx, String oname, boolean checkDelete) throws Exception {
      updateDataSource(dx, oname, true, checkDelete);
   }

   public void renameSourceFolder(XDataSource source, String oname) {
      if(Tool.equals(oname, source.getFullName())) {
         return;
      }

      // Bug #60289, rename transform task is submitted in updateDataSource() for REST
      if(!((source instanceof ListedDataSource) ||
         source.getType().startsWith(SourceInfo.REST_PREFIX)))
      {
         RenameDependencyInfo dinfo = null;

         if(source instanceof XMLADataSource) {
            dinfo = DependencyTransformer.createCubeDependencyInfo(oname, source.getFullName());
         }
         else {
            dinfo = DependencyTransformer.createDependencyInfo(oname, source.getFullName());
         }

         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
      }
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * @param actionName action name of the action record.
    * Otherwise it should be null.
    */
   public void updateDataSource(XDataSource dx, String oname,
                                Boolean actionRecord, String actionName, boolean checkDelete)
      throws Exception
   {
      XDataSource odx = oname == null ? null : getDSRegistry().getDataSource(oname);
      boolean nameChanged = oname != null && !Tool.equals(oname, dx.getFullName());
      boolean changed = odx != null && (!Tool.equals(dx, odx) || dx.getLastModified() != odx.getLastModified());

      if(nameChanged || changed) {
         // when a datasource has been updated, remove the handlers for
         // that datasource from the cache.
         for(Pair pair : sessionrun.keySet()) {
            XDataSource oldDx = (XDataSource) pair.v2;

            if(oldDx.getFullName().equals(nameChanged ? oname : dx.getFullName())) {
               // don't drop connections just for name change
               //sessionrun.get(pair).reset();
               sessionrun.remove(pair);
            }
         }
      }

      if(nameChanged || changed) {
         removeMetaData(nameChanged ? oname : dx.getFullName());
      }

      String dname = oname != null ? oname : dx.getFullName();
      getDSRegistry().renameDatasource(oname, dx.getFullName());

      if(nameChanged || changed || odx == null) {
         getDSRegistry().setDataSource(dx, oname, actionRecord, checkDelete, false);
      }

      // Only add transform task when data source is renamed, if folder is reanmed, it will add task in renama action.
      // Should only add one task for one action to avoid tranform one report/ws/vs for some time.
      if(nameChanged) {
         final String type = dx.getType();
         boolean tabular = dx instanceof ListedDataSource ||
            type.startsWith(SourceInfo.REST_PREFIX) || dx instanceof TabularDataSource;
         String oname2 = oname;
         String nname2 = dx.getFullName();

         if(oname2.contains("/")) {
            oname2 = oname2.substring(oname2.lastIndexOf("/") + 1);
         }

         if(nname2.contains("/")) {
            nname2 = nname2.substring(nname2.lastIndexOf("/") + 1);
         }

         if(!tabular && Tool.equals(oname2, nname2)) {
            return;
         }

         RenameDependencyInfo dinfo =
            DependencyTransformer.createDependencyInfo(dx, oname, dx.getFullName());
         XFactory.getRepository().renameTransform(dinfo);
      }

      if(dx instanceof AdditionalConnectionDataSource) {
         AdditionalConnectionDataSource<?> base = (AdditionalConnectionDataSource<?>) dx;
         String[] names = base.getDataSourceNames();
         Arrays.sort(names);

         for(String name : names) {
            AdditionalConnectionDataSource<?> jds = base.getDataSource(name);
            base.addDatasource(jds);
         }
      }
   }

   /**
    * Rename transform assets dependent on the source.
    * @param info the rename dependency info.
    */
   @Override
   public void renameTransform(RenameDependencyInfo info) {
      RenameTransformHandler.getTransformHandler().addTransformTask(info);
   }

   /**
    * Rename transform assets dependent on the source.
    * @param info the rename dependency info.
    */
   @Override
   public void renameTransform(RenameInfo info) {
      RenameTransformHandler.getTransformHandler().addTransformTask(info);
   }

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   public void updateDataSource(XDataSource dx, String oname,
      Boolean actionRecord, boolean checkDelete) throws Exception
   {
      updateDataSource(dx, oname, actionRecord, null, checkDelete);
   }

   @Override
   public void updateDataSourceFolder(DataSourceFolder folder, String oname)
      throws Exception
   {
      updateDataSourceFolder(folder, oname, false);
   }

   /**
    * copy data source of the data source folder.
    * @param folder new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    */
   public void copyDataSource(DataSourceFolder folder, String oname)
      throws Exception
   {
      List<String> children = getDSRegistry().getSubDataSourceNames(oname);

      for(String cname : children) {
         XDataSource child = getDataSource(cname);
         String fullName = child.getFullName();
         String childName = child.getName();
         String parent = DataSourceFolder.getParentName(fullName);
         String newName = Util.getCopyName(childName);
         String name = folder.getFullName();

         while(true) {
            if("Valid".equals(XUtil.isDataSourceNameValid(this, newName, parent))) {
               break;
            }

            newName = Util.getNextCopyName(childName, newName);
         }

         newName = name + "/" + newName;

         XDataModel model = getDataModel(fullName);
         child.removeFolders();
         child.setName(newName);
         updateDataSource(child, newName, true, false);

         if(model != null) {
            XDataModel modelCopy = (XDataModel) model.clone();
            modelCopy.setDataSource(newName);

            for(String partition : model.getPartitionNames()) {
               XPartition p =
                  (XPartition) model.getPartition(partition).clone();
               modelCopy.addPartition(p);
            }

            for(String lname : model.getLogicalModelNames()) {
               XLogicalModel l = model.getLogicalModel(lname);
               modelCopy.addLogicalModel((XLogicalModel) l.clone());
            }

            for(String vpm : model.getVirtualPrivateModelNames()) {
               modelCopy.addVirtualPrivateModel((VirtualPrivateModel)
                  model.getVirtualPrivateModel(vpm).clone(), true);
            }

            updateDataModel(modelCopy);
         }

         if(child instanceof XMLADataSource) {
            Domain domain = (Domain) getDomain(fullName);

            if(domain != null) {
               domain = (Domain) domain.clone();
               domain.setDataSource(newName);
               updateDomain(domain);
            }
         }
      }
   }

   /**
    * copy data source folder.
    * @param folder new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    */
   public void copyDataSourceFolder(DataSourceFolder folder, String oname)
      throws Exception
   {
      List<String> children = getDSRegistry().getSubfolderNames(oname);

      for(String fname : children) {
         DataSourceFolder child = getDataSourceFolder(fname);
         int onameIdx = fname.indexOf(oname);
         String newName = fname;

         if(onameIdx != -1) {
            newName = fname.substring(0, onameIdx) + folder.getFullName() +
                    fname.substring(onameIdx + oname.length());
         }

         child.setName(newName);
         copyDataSourceFolder(child, fname);
      }

      getDSRegistry().setDataSourceFolder(folder);
      copyDataSource(folder, oname);
   }

   /**
    * cut a data source folder in the repository when name of the data source
    * folder change.
    * @param folder new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    */
   public void cutDataSourceFolder(DataSourceFolder folder, String oname)
      throws Exception
   {
      List<String> children = getDSRegistry().getSubDataSourceNames(oname);

      for(String name : children) {
         XDataSource child = getDataSource(name);
         int onameIdx = name.indexOf(oname);
         String newName = name;

         if(onameIdx != -1) {
            newName = name.substring(0, onameIdx) + folder.getFullName() +
                    name.substring(onameIdx + oname.length());
         }

         child.setName(newName);
         RenameDependencyInfo dinfo =
            DependencyTransformer.createDependencyInfo(child, name, newName, true);
         RenameTransformHandler.getTransformHandler().addTransformTask(dinfo);
         updateDataSource(child, name, false, false);
      }

      children = getDSRegistry().getSubfolderNames(oname);

      for(String name : children) {
         DataSourceFolder child = getDataSourceFolder(name);
         int onameIdx = name.indexOf(oname);
         String newName = name;

         if(onameIdx != -1) {
            newName = name.substring(0, onameIdx) + folder.getFullName() +
                    name.substring(onameIdx + oname.length());
         }

         child.setName(newName);
         updateDataSourceFolder(child, name);
      }
   }
   /**
    * Add or replace a data source folder in the repository.
    * @param folder new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    */
   @Override
   public void updateDataSourceFolder(DataSourceFolder folder, String oname,
                                      Boolean forcerename) throws Exception
   {
      boolean nameChanged =
         oname != null && !Tool.equals(oname, folder.getFullName());

      if(forcerename) {
         copyDataSourceFolder(folder, oname);
      }
      else {
         if(nameChanged) {
            // Should check permission of this datasource folder before cut it.
            // and it's just for remote.
            if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, oname, ResourceAction.DELETE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.delete", oname));
            }

            if(!checkPermission(ResourceType.DATA_SOURCE_FOLDER, oname, ResourceAction.WRITE)) {
               throw new SecurityException(Catalog.getCatalog().getString(
                  "security.nopermission.write", oname));
            }

            cutDataSourceFolder(folder, oname);
         }

         getDSRegistry().renameDataSourceFolder(oname, folder.getFullName());
         getDSRegistry().setDataSourceFolder(folder);
      }
   }

   /**
    * Check permission for remote. and it's just for remote.
    * @param type resource type
    * @param resource resource name
    * @param action action
    * @return true: current user have permission to operation this resource.
    */
   public boolean checkPermission(ResourceType type, String resource, ResourceAction action) {
      // NO-OP
      return true;
   }

   /**
    * Remove a data source from the repository.
    */
   @Override
   public boolean removeDataSource(String dxname, boolean removeAnyWay) {
      removeMetaData(dxname);
      getDSRegistry().removeDataModel(dxname);
      getDSRegistry().removeDataSource(dxname);

      return true;
   }

   /**
    * Remove a data source folder from the repository.
    */
   @Override
   public boolean removeDataSourceFolder(String name) throws Exception {
      return removeDataSourceFolder(name, false);
   }

   /**
    * Remove a data source folder from the repository.
    */
   public boolean removeDataSourceFolder(String name, boolean removeAnyWay)
      throws Exception
   {
      List<String> children = getDSRegistry().getSubfolderNames(name);

      for(String child : children) {
         removeDataSourceFolder(child, removeAnyWay);
      }

      children = getDSRegistry().getSubDataSourceNames(name);

      for(String child : children) {
         if(!removeDataSource(child, removeAnyWay)) {
            return false;
         }
      }

      getDSRegistry().removeDataSourceFolder(name);
      return true;
   }

   /**
    * Get the children data source folder from the specified data source folder
    * path.
    * @param path the specified data source folder name.
    * @return the children data source folder of the specified data source
    * folder.
    */
   @Override
   public String[] getSubfolderNames(String path) {
      return getDSRegistry().getSubfolderNames(path).toArray(new String[0]);
   }

   /**
    * Get the children data source from the specified data source folder path.
    * @param path the specified data source folder name.
    * @return the children data source of the specified data source folder.
    */
   @Override
   public String[] getSubDataSourceNames(String path) {
      return getDSRegistry().getSubDataSourceNames(path).toArray(new String[0]);
   }

   /**
    * Remove meta data files with specified data source.
    */
   public void removeMetaData(String dsname) {
      XDataSource ds = getDSRegistry().getDataSource(dsname);

      if(ds instanceof AdditionalConnectionDataSource) {
         AdditionalConnectionDataSource<?> jds = (AdditionalConnectionDataSource<?>) ds;

         String[] names = jds.getDataSourceNames();

         for(String name : names) {
            removeMetaDataFiles(dsname + "__" + name);
         }
      }

      removeMetaDataFiles(dsname);
   }

   /**
    * Remove meta data files with specified data source.
    */
   public void removeMetaDataFiles(String key) {
      key = OrganizationManager.getInstance().getCurrentOrgID() + "__" + getKey(key);
      String keyHeader = key + "__";
      // remove the related file caches
      File cacheDir = getMetaDataDir();

      if(cacheDir.isDirectory()) {
         File[] files = cacheDir.listFiles();

         if(files != null) {
            for(File file : files) {
               String tempKey = file.getName();

               if(tempKey.startsWith(keyHeader)) {
                  Tool.deleteFile(file);
               }
            }
         }

         Tool.deleteFile(getMetaDataFile(key));
      }

      // remove the related memory caches
      for(String tempKey : metaDataCache.keySet()) {
         if(tempKey.startsWith(keyHeader) || tempKey.equals(key)) {
            metaDataCache.remove(tempKey);
         }
      }
   }

   /**
    * Remove meta data files with specified data source.
    */
   public void removeMetaCache(String key) {
      key = getKey(key);
      String keyHeader = key + "__";

      for(String tempKey : metaDataCache.keySet()) {
         if(tempKey.startsWith(keyHeader) || tempKey.equals(key)) {
            metaDataCache.remove(tempKey);
         }
      }
   }

   /**
    * Remove a data source from the repository.
    */
   @Override
   public void removeDataSource(String dxname) throws Exception {
      removeDataSource(dxname, false);
   }

   /**
    * Get the data model for the specified data source.
    *
    * @param dxname the name of the data source.
    */
   @Override
   public XDataModel getDataModel(String dxname) {
      return getDSRegistry().getDataModel(dxname);
   }

   /**
    * Add or replace a data model in the repository.
    * @param dm new data model.
    */
   @Override
   public void updateDataModel(XDataModel dm) {
      dm.validate();
      getDSRegistry().setDataModel(dm);
   }

   /**
    * Remove a data model from the repository.
    *
    * @param datasource the name of the data source which the model represents.
    */
   public void removeDataModel(String datasource) {
      getDSRegistry().removeDataModel(datasource);
   }

   /**
    * Get the domain for the specified data source.
    *
    * @param dxname the name of the data source.
    */
   @Override
   public XDomain getDomain(String dxname) {
      return getDSRegistry().getDomain(dxname);
   }

   /**
    * Add or replace a domain in the repository.
    *
    * @param dx the new domain.
    */
   @Override
   public void updateDomain(XDomain dx) {
      getDSRegistry().setDomain(dx);
   }

   @Override
   public void updateDomain(XDomain domain, boolean recordAction) {
      getDSRegistry().setDomain(domain, recordAction);
   }

   /**
    * Remove a domain from the repository.
    *
    * @param datasource the name of the data source to which the domain is
    *                   associated.
    */
   @Override
   public void removeDomain(String datasource) {
      getDSRegistry().removeDomain(datasource);
   }

   /**
    * Add or replace a query in the repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   public void updateQuery(XQuery dx, String oname) throws Exception {
      updateQuery(dx, oname, true, true);
   }

   /**
    * Add or replace a query in the repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   public void updateQuery(XQuery dx, String oname, boolean queryChange) throws Exception {
      updateQuery(dx, oname, true, queryChange);
   }

   /**
    * Add or replace a query in the repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   public void updateQuery(XQuery dx, String oname, Boolean actionRecord, Boolean queryChange) {
      dx.revalidate();

      XDataSource xds = dx.getDataSource();

      // reset all XHandler for this data source because query parameters
      // may have changed
      for(Pair pair : sessionrun.keySet()) {
         if(pair.v2.equals(xds)) {
            XHandler handler = sessionrun.get(pair);
            handler.reset(dx);
         }
      }
   }

   /**
    * Rename a query folder in the repository.
    * @param nname new name of query folder.
    * @param oname old name of the query folder, if the name has been
    * changed. Otherwise it should be null.
    */
   @Override
   public void renameQueryFolder(String nname, String oname) {
   }

   /**
    * Connect to the data service.
    * @param uinfo user info.
    * @return session object.
    */
   @Override
   public Object bind(Object uinfo) throws RemoteException {
      return uinfo;
   }

   /**
    * Close an active session.
    * @param session session object.
    */
   @Override
   public void close(Object session) throws RemoteException {
      for(Map.Entry<Pair, XHandler> e : sessionrun.entrySet()) {
         Pair pair = e.getKey();

         if(pair.isSession(session)) {
            XHandler handler = e.getValue();

            try {
               handler.close();
            }
            catch(Exception ex) {
               LOG.error("Failed to close handler for {}", pair, ex);
            }

            sessionrun.remove(pair);
         }
      }
   }

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param query query name.
    */
   @Override
   public UserVariable[] getConnectionParameters(Object session, String query)
      throws RemoteException
   {
      if(query.startsWith(":")) {
         String ds = query.substring(1);
         String ads =
            XUtil.getAdditionalDatasource(ThreadContext.getContextPrincipal(), ds);
         XDataSource xds = getDataSource(ds);

         if(ads != null) {
            XDataSource xds2 = ((AdditionalConnectionDataSource<?>) xds).getDataSource(ads);
            xds = xds2 != null ? xds2 : xds;
         }

         return getConnectionParameters(session, xds);
      }

      return null;
   }

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param xquery query to get connection parameters.
    */
   @Override
   public UserVariable[] getConnectionParameters(Object session, XQuery xquery)
      throws RemoteException
   {
      if(xquery == null) {
         throw new RemoteException("Query not found!");
      }

      XDataSource dx = xquery.getDataSource();

      return getConnectionParameters(session, dx);
   }

   /**
    * Get the parameters for a data source. The parameters should be filled in
    * and passed to the connect method. If the data source is already
    * connected or the data source does not require any connection parameter,
    * the method returns an empty array.
    * @param session session object.
    * @param dx data source to get connection parameters.
    */
   @Override
   public UserVariable[] getConnectionParameters(Object session, XDataSource dx)
      throws RemoteException
   {
      if(dx == null) {
         throw new RemoteException("Data source not found:");
      }

      if(dx instanceof AdditionalConnectionDataSource) {
         String ds2 = XUtil.getAdditionalDatasource(
            ThreadContext.getContextPrincipal(), dx.getFullName());

         if(ds2 != null && ((AdditionalConnectionDataSource<?>) dx).containDatasource(ds2)) {
            dx = ((AdditionalConnectionDataSource<?>) dx).getDataSource(ds2);
         }
      }

      XAgent agent = XAgent.getAgent(dx);
      Pair pair = new Pair(session, dx);
      Pair pair2 = null;

      for(Pair opair : sessionrun.keySet()) {
         if(opair.equals(pair) && Tool.equals(opair.getUser(), pair.getUser())) {
            pair2 = opair;
            break;
         }
      }

      boolean cparameters =
         "true".equals(SreeEnv.getProperty("datasource.cache.parameters"));

      // @by billh, we always share connection in design time,
      // or end users check on datasource.cache.parameters, or same user
      if(sessionrun.get(pair) != null &&
         agent.isConnectionReusable() &&
         (pair2 != null || cparameters))
      {
         return null;
      }

      return dx.getParameters();
   }

   private void closeInvalidConnection(Object session, XQuery xquery) throws RemoteException {
      if(xquery == null) {
         throw new RemoteException("Query not found!");
      }

      XDataSource dx = xquery.getDataSource();

      if(dx instanceof AdditionalConnectionDataSource) {
         String ds2 = XUtil.getAdditionalDatasource(
            ThreadContext.getContextPrincipal(), dx.getFullName());

         if(ds2 != null && ((AdditionalConnectionDataSource<?>) dx).containDatasource(ds2)) {
            dx = ((AdditionalConnectionDataSource<?>) dx).getDataSource(ds2);
         }
      }

      Pair pair = new Pair(session, dx);
      XHandler handler = sessionrun.get(pair);

      if(handler instanceof JDBCHandler) {
         JDBCDataSource connectionDx = ((JDBCHandler) handler).getDataSource();

         if(connectionDx != null && connectionDx.isRequireLogin() && connectionDx.getUser() == null) {
            try {
               handler.close();
            }
            catch(Exception ex) {
               LOG.error("Failed to close handler for {}", pair, ex);
            }

            this.sessionrun.remove(pair);
         }
      }
   }

   /**
    * Get the parameters for a query. The parameters should be filled in
    * and passed to execute().
    * @param session session object.
    * @param xquery query to get parameters.
    * @param promptOnly true if only include the user variables that
    * are declared as 'Prompt User'.
    */
   @Override
   public UserVariable[] getQueryParameters(Object session, XQuery xquery, boolean promptOnly)
      throws RemoteException
   {
      List<UserVariable> vars = new ArrayList<>();
      populateVariables(session, xquery, vars, promptOnly);

      if(vars.size() == 0) {
         return null;
      }

      return vars.toArray(new UserVariable[0]);
   }

   /**
    * Add the query's user variables to the list.
    */
   private void populateVariables(Object session, XQuery xquery, List<UserVariable> vars,
                                  boolean promptOnly) throws RemoteException
   {
      closeInvalidConnection(session, xquery);
      appendArrayToVector(vars, getConnectionParameters(session, xquery));
      appendArrayToVector(vars, VpmProcessor.getInstance().getVPMParameters(xquery, ThreadContext.getContextPrincipal(), promptOnly));

      // find all un-set variables in the query variables
      Enumeration keys = xquery.getVariableNames();

      while(keys.hasMoreElements()) {
         String name = (String) keys.nextElement();
         XVariable var = xquery.getVariable(name);

         if((var instanceof UserVariable) &&
            (((UserVariable) var).isPrompt() || !promptOnly)) {
            vars.add((UserVariable) var);
         }
      }
   }

   /**
    * Append an array to a vector.
    */
   private <T> void appendArrayToVector(List<T> list, T[] array) {
      if(array != null) {
         list.addAll(Arrays.asList(array));
      }
   }

   /**
    * Test a data source connection. The data source connection is
    * shared by a session.
    * @param session session object.
    * @param dx the specified data source.
    * @param params connection parameters.
    */
   @Override
   public void testDataSource(Object session, XDataSource dx,
                              VariableTable params) throws Exception {
      if(dx == null) {
         throw new RemoteException("Data source not found.");
      }

      // @by billh, it is unreasonable to close this session,
      // so here I discard the logic
      // close(session);

      String type = dx.getType();
      String cls = Config.getHandlerClass(type);

      if(cls == null) {
         throw new Exception("Data source not supported: " + dx.getName());
      }

      XHandler handler = (XHandler) Config.getClass(type, cls).newInstance();

      handler.setSession(session);
      handler.testDataSource(dx, params);
   }

   /**
    * Initialize a data source connection. The data source connection is
    * shared by a session.
    * @param session session object.
    * @param query query name.
    * @param params connection parameters.
    */
   @Override
   public void connect(Object session, String query, VariableTable params)
      throws Exception
   {
      XDataSource dx = null;

      if(query.startsWith("::")) {
         close(session);
         query = query.substring(1);
      }

      if(query.startsWith(":")) {
         dx = getDataSource(query.substring(1));
      }

      if(dx == null) {
         throw new RemoteException("Data source not found: " + query);
      }

      connect(session, dx, params);
   }

   /**
    * Initialize a data source connection. The data source connection is
    * shared by a session.
    * @param session session object.
    * @param dx the specified data source.
    * @param params connection parameters.
    */
   @Override
   public void connect(Object session, XDataSource dx, VariableTable params)
      throws Exception
   {
      if(dx == null) {
         throw new RemoteException(Catalog.getCatalog().getString(
            "common.uql.serviceEngine.dataSourceNotFound"));
      }

      Pair pair = new Pair(session, dx);
      XHandler handler = sessionrun.get(pair);

      // ignore if already connected
      if(handler == null) {
         String type = dx.getType();
         String cls = Config.getHandlerClass(type);

         if(cls == null) {
            throw new Exception("Data source not supported: " + dx.getName());
         }

         handler = (XHandler) Config.getClass(type, cls).newInstance();
         handler.setSession(session);
         handler.setRepository(this);
         handler.connect(dx, params);
         sessionrun.put(pair, handler);
      }
      // if datasource contains connection parameters with null variable table,
      // it's not right or necessary to connect again
      else if(getConnectionParameters(session, dx) != null && params != null) {
         handler.connect(dx, params);
         sessionrun.remove(pair);
         sessionrun.put(pair, handler);
      }
   }

   /**
    * Get the handler.
    * @param session the specified session object.
    * @param dx the specified data source.
    * @param params the specified variable table.
    */
   @Override
   public XHandler getHandler(Object session, XDataSource dx,
                              VariableTable params)
      throws Exception
   {
      XHandler handler = sessionrun.get(new Pair(session, dx));

      if(handler == null) {
         connect(session, dx, params);
      }

      handler = sessionrun.get(new Pair(session, dx));
      return handler;
   }

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, String query, VariableTable vars)
      throws Exception
   {
      return execute(session, query, vars, null, false, null);
   }

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, String query, VariableTable vars,
                        boolean resetVariables) throws Exception {
      return execute(session, query, vars, null, resetVariables, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, String query, VariableTable vars,
                        Principal user)
      throws Exception
   {
      return execute(session, query, vars, user, false, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, String query, VariableTable vars,
      Principal user, boolean resetVariables) throws Exception
   {
      return execute(session, query, vars, user, resetVariables, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param query query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @param visitor used to process cache facility.
    */
   @Override
   public XNode execute(Object session, String query, VariableTable vars,
      Principal user, boolean resetVariables, DataCacheVisitor visitor)
      throws Exception
   {
      //Do not support query.
      return null;
   }

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, XQuery xquery, VariableTable vars)
      throws Exception
   {
      return execute(session, xquery, vars, ThreadContext.getContextPrincipal(),
                     false, null);
   }

   /**
    * Execute the query and return the result set.
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, XQuery xquery, VariableTable vars,
                        boolean resetVariables)
      throws Exception
   {
      return execute(session, xquery, vars, ThreadContext.getContextPrincipal(),
                     resetVariables, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, XQuery xquery, VariableTable vars,
                        Principal user)
      throws Exception
   {
      return execute(session, xquery, vars, user, false, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode execute(Object session, XQuery xquery, VariableTable vars,
                        Principal user, boolean resetVariables)
      throws Exception
   {
      return execute(session, xquery, vars, user, resetVariables, null);
   }

   /**
    * Execute the query and return the result set.
    *
    * @param session session object.
    * @param xquery query to execute.
    * @param vars variable values for the query.
    * @param user a Principal object that identifies the user executing the
    *             query.
    * @param resetVariables <code>true</code> if should reset cached variables.
    * @param visitor used to process cache facility.
    */
   @Override
   public XNode execute(Object session, XQuery xquery, VariableTable vars,
                        Principal user, boolean resetVariables,
                        DataCacheVisitor visitor)
      throws Exception
   {
      long start_time = System.currentTimeMillis();
      XDataSource dx = xquery.getDataSource();

      if(xquery instanceof TabularQuery && dx != null) {
         final XDataSource upToDateDatasource = getDataSource(dx.getFullName());

         if(dx.getClass().equals(upToDateDatasource.getClass())) {
            dx = upToDateDatasource;
            xquery.setDataSource(dx);
         }
      }

      XHandler handler;

      synchronized(sessionrun) {
         // make sure var table is not null
         if(vars == null) {
            vars = new VariableTable();
         }

         handler = sessionrun.get(new Pair(session, dx));

         // create data source connection, if user/password not remembered,
         // force to enter the information again.
         if(handler == null || getConnectionParameters(session, dx) != null) {
            connect(session, dx, vars);
            handler = sessionrun.get(new Pair(session, dx));
         }
      }

      // usually we needn't reset the variables related the query,
      // but when design, we should always reset the variables.
      if(resetVariables && handler != null) {
         handler.reset(xquery);
      }

      if(xquery instanceof JDBCQuery &&
         ((JDBCQuery) xquery).getSQLDefinition() instanceof UniformSQL &&
         !(visitor instanceof XSessionManager.DataCacheResult))
      {
         UniformSQL usql = (UniformSQL) ((JDBCQuery) xquery).getSQLDefinition();
         usql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      }

      // @by henryh, 2004-08-12
      // if it is a non-prompt user variable, add the variable to params.
      // if it is a query variable, we execute it first and add it to params.
      Enumeration variables = xquery.getVariableNames();
      VariableTable queryvars;

      if("true".equalsIgnoreCase(SreeEnv.getProperty("query.variable.unique"))) {
         queryvars = vars;
      }
      else {
         queryvars = new VariableTable();
         queryvars.setBaseTable(vars);
      }

      while(variables.hasMoreElements()) {
         String name = (String) variables.nextElement();

         if(queryvars.get(name) == null) {
            XVariable var = xquery.getVariable(name);

            if(var instanceof UserVariable) {
               UserVariable userVar = (UserVariable) var;

               if(!userVar.isPrompt()) {
                  if(userVar.getValueNode() != null) {
                     queryvars.put(name, userVar.getValueNode().getValue());
                  }
                  else if(!queryvars.contains(name)) {
                     queryvars.put(name, null);
                  }
               }
            }
            else if(var instanceof QueryVariable) {
               QueryVariable queryVar = (QueryVariable) var;
               queryvars.put(name, queryVar.execute(vars));
            }
         }
      }

      try {
         assert handler != null;
         XNode root = handler.execute(xquery, queryvars, user, visitor);
         long duration = System.currentTimeMillis() - start_time;
         LOG.debug("Query {} completed in {} ms", xquery.getName(), duration);

         return root;
      }
      catch(Exception ex) {
         throw ex;
      }
   }

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param session session object.
    * @param dx data source.
    * @param mtype meta data type, defined in each data source.
    * @return return the root node of the meta data tree.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode getMetaData(Object session, XDataSource dx, XNode mtype)
      throws Exception
   {
      return getMetaData(session, dx, mtype, true);
   }

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param session session object.
    * @param dx data source.
    * @param mtype meta data type, defined in each data source.
    * @param clone true to clone the cached node.
    * @return return the root node of the meta data tree.
    * @deprecated
    */
   @Deprecated
   @Override
   public XNode getMetaData(Object session, XDataSource dx, XNode mtype,
                            boolean clone) throws Exception {
      XDataSource odx = dx;
      Principal user = ThreadContext.getContextPrincipal();

      // for physical view edit in portal.
      String additional = null;

      if(mtype != null) {
         additional = (String) mtype.getAttribute("additional");
      }

      boolean portal_data = mtype != null && "true".equals(mtype.getAttribute(XUtil.PORTAL_DATA));

      // find right additional datasource for portal data model.
      if(portal_data && !StringUtils.isEmpty(additional) &&
         !XUtil.OUTER_MOSE_LAYER_DATABASE.equals(additional) &&
         dx instanceof AdditionalConnectionDataSource && !Tool.equals(additional, dx.getName()))
      {
         dx = ((AdditionalConnectionDataSource<?>) dx).getDataSource(additional);
      }
      // shouldn't load data by user for portal data model.
      else if(!portal_data && !dx.isFromPortal()) {
         dx = XUtil.getDatasource(user, dx, additional);
      }

      if(dx == null) {
         dx = odx;
      }

      String key = OrganizationManager.getInstance().getCurrentOrgID() + "__" + dx.getFullName();
      boolean mysql = false;

      if(dx instanceof AdditionalConnectionDataSource &&
         ((AdditionalConnectionDataSource<?>) dx).getBaseDatasource() != null)
      {
         key = ((AdditionalConnectionDataSource<?>) dx).getBaseDatasource().getFullName() +
            "__" + key;
         mysql = (dx instanceof JDBCDataSource) &&
            ((JDBCDataSource) dx).getDatabaseType() == JDBCDataSource.JDBC_MYSQL;
      }

      boolean cache = true;
      XNode node;

      // @by Charvi
      // If the schema name and package name exists for a
      // stored procedure, then prepend it to the procedure's name.
      if(mtype != null) {
         Object catalog = mtype.getAttribute("catalog");
         Object schema = mtype.getAttribute("schema");
         Object pkg = mtype.getAttribute("package");
         Object cstr = mtype.getAttribute("cache");

         // cache may be turned off for SQL meta data
         if(cstr != null && cstr.equals("false")) {
            cache = false;
         }

         key = key.concat("__");

         // @by jasons, prepend the catalog for MySQL 5 schema support
         // @by billh, fix customer bug bug1297460887597. For most dbs,
         // we should append catalog to key regardless of schema
         if(catalog != null && (!mysql || schema == null) &&
            !mtype.getName().contains(catalog + "."))
         {
            key = key.concat(catalog + ".");
         }

         // @by charvi
         // Prepend the schema name to the procedure name only
         // if it hasn't been done already.
         if(schema != null && !mtype.getName().contains(schema + ".")) {
            key = key.concat(schema + ".");
         }

         // @by charvi
         // Prepend the package name to the procedure name only
         // if it hasn't been done already.
         if(pkg != null && !mtype.getName().contains(pkg + ".")) {
            key = key.concat(pkg + ".");
         }

         String mdType = (String) mtype.getAttribute("type");

         if(mdType == null && (dx instanceof JDBCDataSource)) {
            // use default query type to prevent cache misses
            mdType = "TABLE";
         }

         key = key.concat(mtype.getName() + mdType);
      }

      if(cache) {
         key = getKey(key);
         key = Tool.normalizeFileName(key);
         Pair pair = new Pair(session, dx);
         XHandler handler = sessionrun.get(pair);

         // should not lock the cache for so long time
         synchronized(lock) {
            node = getCachedMetaData(key);

            while(node == pendingFlag) {
               try {
                  lock.wait(200);

                  // Release the jdbc get meta lock to avoid others thread block
                  // when current thread is padding wait.
                  if(handler instanceof JDBCHandler) {
                     ((JDBCHandler) handler).waitMetaLock(20);
                  }
               }
               catch(Exception ex) {
                  LOG.debug("Time out waiting for meta-data cache", ex);
               }

               node = getCachedMetaData(key);
            }

            if(mtype != null && node != null && node.getChildCount() > 0 &&
               "KEYRELATION".equals(mtype.getAttribute("type")))
            {
               // 11.5 backward compatibility: previous versions used exported
               // keys, changed to imported keys in 11.5, so we need to discard
               // old meta data
               if(node.getChild(0).getName().startsWith("ExportKey")) {
                  node = null;
               }
            }

            // we are going to fetch the meta data next, first mark the
            // key as pending, so we don't need to lock the metaDataCache
            // while waiting for results from the db
            if(node == null) {
               metaDataCache.put(key, pendingFlag);
            }
         }

         if(node == null) {
            try {
               // this can be done outside of synchronized block because
               // the key is already marked as pending in the cache
               node = getMetaDataInternal(session, dx, mtype);

               if(node == null) {
                  node = new XNode();
               }

               synchronized(lock) {
                  // Swallow npe for tabular data source meta data
                  if(!(node == null &&
                     sessionrun.get(new Pair(session, dx)) instanceof TabularHandler))
                  {
                     writeMetaDataCache(key, node);
                  }
               }
            }
            catch(Throwable ex) {
               LOG.error("Failed to retrieve meta data: " + key,
                  ex);

               // add a holder so when there is problem getting
               // meta data for an object, we don't keep trying
               // which may hold up the entire server
               metaDataCache.put(key, node = new XNode());
            }

            synchronized(lock) {
               lock.notifyAll();
            }
         }

         // for read only, we need not clone the node
         if(clone) {
            node = (XNode) node.clone();
         }
      }
      else {
         node = getMetaDataInternal(session, dx, mtype);
      }

      if(mtype == null) {
         node.setName(odx.getFullName());
      }

      return node;
   }

   @Override
   public Future<XNode> getMetaDataAsync(Object session, XDataSource dx, XNode mtype, boolean clone,
                                         MetaDataListener listener)
   {
      return Tool.makeAsync(() -> getMetaData(session, dx, mtype, clone, listener));
   }

   /**
    * Delegate to the handler to get the meta data.  Separated from getMetaData
    * so that subclass can override this method, but still take advantage of the
    * cache in XEngine.
    * @param session session object.
    * @param dx data source.
    * @param mtype meta data type, defined in each data source.
    */
   protected XNode getMetaDataInternal(Object session, XDataSource dx, XNode mtype)
         throws Exception
   {
      Pair pair = new Pair(session, dx);
      XHandler handler = sessionrun.get(pair);

      if(handler instanceof JDBCHandler) {
         if(!Tool.equals(dx, ((JDBCHandler) handler).getDataSource()) && dx.getParameters() == null) {
            sessionrun.remove(pair);
            handler = null;
         }
      }

      if(handler == null) {
         VariableTable vars = null;

         if(getConnectionParameters(session, dx) != null) {
            vars = new VariableTable();
            Principal principal = ThreadContext.getContextPrincipal();
            boolean resetParameters = dx instanceof JDBCDataSource &&
               ((JDBCDataSource) dx).isRequireLogin() && !((JDBCDataSource) dx).isRequireSave();

            if(resetParameters) {
               ((JDBCDataSource) dx).setUser(null);
            }

            if(principal instanceof XPrincipal) {
               Object username = ((XPrincipal) principal).getParameter(
                  XUtil.DB_USER_PREFIX + dx.getFullName());

               if(username != null) {
                  vars.put(XUtil.DB_USER_PREFIX + dx.getFullName(), username.toString());
               }

               Object password = ((XPrincipal) principal).getParameter(
                  XUtil.DB_PASSWORD_PREFIX + dx.getFullName());

               if(password != null) {
                  vars.put(XUtil.DB_PASSWORD_PREFIX + dx.getFullName(), password.toString());
               }
            }
         }

         connect(session, dx, vars);
         handler = sessionrun.get(new Pair(session, dx));
      }

      if(handler == null) {
         throw new Exception("Data source not connected: " + dx.getName());
      }

      return handler.getMetaData(mtype);
   }

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param session session object.
    * @param dx data source.
    * @param mtype meta data type, defined in each data source.
    * @param clone true to clone the cached node.
    * @return return the root node of the meta data tree.
    */
   @SuppressWarnings("deprecation")
   @Override
   public XNode getMetaData(Object session, XDataSource dx, XNode mtype,
                            boolean clone, MetaDataListener listener)
      throws Exception
   {
      if(listener == null) {
         return getMetaData(session, dx, mtype, clone);
      }

      MetaDataLoader loader = new MetaDataLoader(session, dx, mtype, clone);
      new GroupedThread(loader).start();
      long last = System.currentTimeMillis();
      String msg = "";

      while(!loader.isCompleted()) {
         long now = System.currentTimeMillis();

         if(now - last >= 1000) {
            last = now;
            msg += ".";

            if(msg.length() > 6) {
               msg = ".";
            }

            listener.start(dx, msg);
         }

         try {
            Thread.sleep(50);
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      listener.end(dx);

      if(loader.ex != null) {
         throw loader.ex;
      }
      else {
         return loader.res;
      }
   }

   /**
    * Clear cached data.
    */
   @Override
   public void clearCache() {
      metaDataCache.clear();
   }

   @Override
   public void close() throws Exception {
      clearCache();

      if(cluster != null) {
         cluster.removeMessageListener(messageListener);
      }

      changeDebouncer.close();
   }

   /**
    * Refresh matadata of a datasource. It will remove the related file caches
    * and memory caches.
    *
    * @param dxName the specified datasource name.
    */
   @Override
   public void refreshMetaData(String dxName) {
      removeMetaDataFiles(dxName);
      processClusterNodes(dxName);
      loadMetaData(dxName);
   }

   /**
    * Clear the cached meta data.
    */
   @Override
   public void refreshMetaData() {
      File cacheFile = getMetaDataDir();

      if(cacheFile.isDirectory()) {
         Tool.deleteFile(cacheFile);
      }

      processClusterNodes(null);
      metaDataCache.clear();

      // reset all XHandler for this data source because query parameters
      // may have changed

      for(XHandler handler : sessionrun.values()) {
         handler.reset();
      }

      JDBCHandler.getConnectionPoolFactory().closeAllConnectionsPools();
   }

   /**
    * Read the cached database meta data from disk.
    */
   public XNode getCachedMetaData(String key) {
      key = getKey(key);
      XNode meta = metaDataCache.get(key);

      if(meta != null) {
         return meta;
      }

      try {
         File file = getMetaDataFile(key);

         if(file.exists()) {
            ObjectInputStream in = new ObjectInputStream(
               new BufferedInputStream(new FileInputStream(file)));
            in.readObject(); // build number

            meta = (XNode) in.readObject();
            in.close();

            metaDataCache.put(key, meta);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to get meta-data cache: " + key, e);
      }

      return meta;
   }

   public void removeQueryCache(Object session, XQuery xquery, VariableTable vars,
                               Principal user, Class<?> type) throws Exception
   {
      if(xquery == null) {
         return;
      }

      XDataSource dx = xquery.getDataSource();

      if(xquery instanceof TabularQuery && dx != null) {
         final XDataSource upToDateDatasource =
            DataSourceRegistry.getRegistry().getDataSource(dx.getFullName());

         if(dx.getClass().equals(upToDateDatasource.getClass())) {
            dx = upToDateDatasource;
            xquery.setDataSource(dx);
         }
      }

      XHandler handler;

      synchronized(sessionrun) {
         // make sure var table is not null
         if(vars == null) {
            vars = new VariableTable();
         }

         handler = sessionrun.get(new Pair(session, dx));

         // create data source connection, if user/password not remembered,
         // force to enter the information again.
         if(handler == null || getConnectionParameters(session, dx) != null) {
            connect(session, dx, vars);
            handler = sessionrun.get(new Pair(session, dx));
         }
      }

      handler.removeQueryCache(xquery, vars, user, type);
   }

   /**
    * Get the key without slash.
    */
   private String getKey(String key) {
      return Tool.replaceAll(key, "/", "^_^");
   }

   /**
    * Creates a File object with the given key.
    * @param key the key identifying the node
    */
   private File getMetaDataFile(String key) {
      // @by yanie: bug1429511645290
      // @by OliverYeung (edited): bug1432623624823
      // Add the hashcode of dx name to avoid case insensitive path(windows)
      return FileSystemService.getInstance().getFile(getMetaDataDir(), key + key.hashCode());
   }

   /**
    * Creates a File object representing the directory of the metadata cache.
    * @return the file
    */
   private File getMetaDataDir() {
      return FileSystemService.getInstance().getFile(
         System.getProperty("user.home", ".") + File.separator + ".srMetaData");
   }

   /**
    * Write the hashtable which contains database meta data to disk.
    */
   private void writeMetaDataCache(final String key, final XNode meta) {
      metaDataCache.put(key, meta);

      (new GroupedThread() {
         {
            setPriority(Thread.MIN_PRIORITY);
         }

         @Override
         protected void doRun() {
            try {
               File dir = getMetaDataDir();

               if(!dir.exists() && !dir.mkdirs()) {
                  LOG.warn("Failed to create cache directory: {}", dir);
               }

               ObjectOutputStream out = new ObjectOutputStream(
                  new BufferedOutputStream(new FileOutputStream(getMetaDataFile(key))));

               out.writeObject(Tool.getBuildNumber());
               out.writeObject(meta);
               out.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to write meta-data cache: {}", key, e);
            }
         }
      }).start();
   }

   class Pair {
      Pair(Object v1, Object v2) {
         this.v1 = v1;
         this.v2 = v2;
         this.user = ThreadContext.getContextPrincipal();
      }

      @Override
      public int hashCode() {
         int hash = (v1 != null) ? v1.hashCode() : 0;

         hash += (v2 != null) ? v2.hashCode() : 0;
         return hash;
      }

      public Principal getUser() {
         return user;
      }

      @Override
      public boolean equals(Object obj) {
         try {
            Pair pair = (Pair) obj;

            return (v1 == null && pair.v1 == null ||
                  v1 != null && pair.v1 != null && v1.equals(pair.v1)) &&
               (v2 == null && pair.v2 == null ||
               v2 != null && pair.v2 != null && v2.equals(pair.v2));
         }
         catch(Exception e) {
            return false;
         }
      }

      @Override
      public String toString() {
         return "Pair[" + v1 + "," + v2 + "," + user + "]";
      }

      boolean isSession(Object session) {
         return v1 != null && v1.equals(session);
      }

      Object v1, v2;
      Principal user;
   }

   /**
    * Get the data source registry.
    * @return the data source registry.
    */
   private DataSourceRegistry getDSRegistry() {
      try {
         return DataSourceRegistry.getRegistry();
      }
      catch(Exception ex) {
         throw new RuntimeException("Failed to get data source registry", ex);
      }
   }

   /**
    * Process cluster node to refresh meta data.
    */
   private void processClusterNodes(String dxname) {
      Cluster cluster = Cluster.getInstance();
      boolean clusterEnabled = "server_cluster".equals(SreeEnv.getProperty("server.type"));

      if(!clusterEnabled) {
         if(cluster != null) {
            try {
               cluster.sendMessage(new ClearLocalNodeMetaDataCacheMessage(dxname));
            }
            catch (Exception ex) {
               LOG.error(ex.getMessage(), ex);
            }
         }

         return;
      }

      Set<String> clusterNodes = cluster.getClusterNodes();

      try {
         String local = Tool.getIP();

         for(String clusterNode : clusterNodes) {
            if(cluster.getClusterNodeHost(clusterNode).equals(local)) {
               continue;
            }

            cluster.sendMessage(clusterNode, new RefreshMetaDataMessage(dxname));
         }
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
      }
   }

   private void clusterMessageReceived(MessageEvent event) {
      if(event.getMessage() instanceof DataSourceRegistry.DataSourceConnectionChangedMessage) {
         DataSourceRegistry.DataSourceConnectionChangedMessage message =
            (DataSourceRegistry.DataSourceConnectionChangedMessage) event.getMessage();
         changeDebouncer.debounce(message, 500L, TimeUnit.MILLISECONDS, this::resetAllHandlers);
      }
   }

   private void resetAllHandlers() {
      sessionrun.values().stream()
         .filter(JDBCHandler.class::isInstance)
         .map(JDBCHandler.class::cast)
         .forEach(JDBCHandler::resetConnection);
      JDBCHandler.getConnectionPoolFactory().closeAllConnectionsPools();
   }

   public class MetaDataLoader implements Runnable {
      MetaDataLoader(Object session, XDataSource dx, XNode mtype, boolean clone) {
         this.session = session;
         this.dx = dx;
         this.mtype = mtype;
         this.clone = clone;
      }

      @SuppressWarnings("deprecation")
      @Override
      public void run() {
         try {
            res = getMetaData(session, dx, mtype, clone);
         }
         catch(Exception ex) {
            this.ex = ex;
         }
         finally {
            completed = true;
         }
      }

      public boolean isCompleted() {
         return completed;
      }

      private Object session;
      private XDataSource dx;
      private XNode mtype;
      private boolean clone;
      private boolean completed;
      private XNode res;
      private Exception ex;
   }

   private final MessageListener messageListener = this::clusterMessageReceived;
   // [session, dxtype] -> XHandler
   private final Map<Pair, XHandler> sessionrun = new ConcurrentHashMap<>();
   // [dxname, mtype] -> XNode
   private Map<String, XNode> metaDataCache = new ConcurrentHashMap<>();
   private final Object lock = new Object();
   // special value used to mark a meta data as pending
   private final XNode pendingFlag = new XNode();
   private Cluster cluster;
   private DefaultDebouncer<DataSourceRegistry.DataSourceConnectionChangedMessage>
   changeDebouncer = new DefaultDebouncer<>();

   private static final Logger LOG = LoggerFactory.getLogger(XEngine.class);
}

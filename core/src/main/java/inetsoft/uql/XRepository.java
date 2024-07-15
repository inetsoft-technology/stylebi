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
package inetsoft.uql;

import inetsoft.uql.asset.sync.RenameDependencyInfo;
import inetsoft.uql.asset.sync.RenameInfo;
import inetsoft.uql.erm.XDataModel;
import inetsoft.util.SingletonManager;

import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.Future;

/**
 * XRepository defines the API to the data source and query registries.
 * It is normally used during design time to modify the registries, such
 * as creating new data sources or queries, and modifying the existing
 * data sources or queries. Runtime methods for executing queries are
 * defined in the base class, XDataService.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(XFactory.Reference.class)
public interface XRepository extends XDataService, XQueryRepository {
   /**
    * Get the names of data sources in this repository.
    */
   String[] getDataSourceNames() throws RemoteException;

   /**
    * Get full names of all the data sources in this repository.
    * @return full names of all the data sources.
    */
   String[] getDataSourceFullNames() throws RemoteException;

   /**
    * Get all data source folder names in repository.
    * @return names of all the data source folders.
    */
   String[] getDataSourceFolderNames() throws RemoteException;

   /**
    * Get all data source folder names in repository.
    * @return full names of all the data source folders.
    */
   String[] getDataSourceFolderFullNames() throws RemoteException;

   /**
    * Get the children data source folder from the specified data source folder
    * path.
    * @param path the specified data source folder name.
    * @return the children data source folder of the specified data source
    * folder.
    */
   String[] getSubfolderNames(String path) throws RemoteException;

   /**
    * Get the children data source from the specified data source folder path.
    * @param path the specified data source folder name.
    * @return the children data source of the specified data source folder.
    */
   String[] getSubDataSourceNames(String path) throws RemoteException;

   /**
    * Get the named data source.
    */
   XDataSource getDataSource(String dsname) throws RemoteException;

   /**
    * Get the named data source folder.
    */
   DataSourceFolder getDataSourceFolder(String dsname)
      throws RemoteException;

   /**
    * Get the named data source.
    * @param clone true to clone data source, false otherwise.
    */
   XDataSource getDataSource(String dsname, boolean clone);

   /**
    * Get the named data source folder.
    * @param clone true to clone data source folder, false otherwise.
    */
   DataSourceFolder getDataSourceFolder(String dsname, boolean clone);

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * Otherwise it should be null.
    */
   void updateDataSource(XDataSource dx, String oname)
      throws Exception;

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * Otherwise it should be null.
    */
   void updateDataSource(XDataSource dx, String oname, boolean checkDelete)
      throws Exception;

   /**
    * Add or replace a data source in the repository.
    * @param dx new data source.
    * @param oname old name of the data source, if the name has been changed.
    * @param actionRecord control whether write down audit record when remote
    * in designer.
    * Otherwise it should be null.
    */
   void updateDataSource(XDataSource dx, String oname,
                         Boolean actionRecord, boolean checkDelete) throws Exception;

    /**
    * Add or replace a data source folder in the repository.
    * @param dx new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    */
    void updateDataSourceFolder(DataSourceFolder dx, String oname)
      throws Exception;

    /**
    * Add or replace a data source folder in the repository.
    * @param folder new data source folder.
    * @param oname old name of the data source folder, if the name has been
    * changed. Otherwise it should be null.
    * @param forcerename control whether copy folder.
    */

    void updateDataSourceFolder(DataSourceFolder folder, String oname,
                                Boolean forcerename) throws Exception;

   /**
    * Remove a data source from the repository.
    */
   void removeDataSource(String dxname) throws Exception;

   /**
    * Remove a data source from the repository.
    */
   boolean removeDataSource(String dxname, boolean removeAnyWay) throws Exception;

   /**
    * Remove a data source folder from the repository.
    */
   boolean removeDataSourceFolder(String name) throws Exception;

   /**
    * Get the data model for the specified data source.
    *
    * @param dxname the name of the data source.
    */
   XDataModel getDataModel(String dxname) throws RemoteException;

   /**
    * Get the domain for the specified data source.
    *
    * @param dxname the name of the data source.
    */
   XDomain getDomain(String dxname) throws RemoteException;

   /**
    * Add or replace a domain in the repository.
    *
    * @param domain the new domain.
    */
   void updateDomain(XDomain domain) throws Exception;

   /**
    * Add or replace a domain in the repository.
    * @param domain the new domain.
    * @param recordAction record action.
    */
   void updateDomain(XDomain domain, boolean recordAction) throws Exception;

   /**
    * Remove a domain from the repository.
    *
    * @param dxname the name of the data source to which the domain is
    * associated.
    */
   void removeDomain(String dxname) throws Exception;

   /**
    * Add or replace a data model in the repository.
    * @param dx new data model.
    */
   void updateDataModel(XDataModel dx)
      throws Exception;

   /**
    * Add or replace a query in the repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * Otherwise it should be null.
    */
   @Override
   void updateQuery(XQuery dx, String oname)
      throws Exception;

   /**
    * Add or replace a query in the repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * @param queryChange when update dependencies or import, return not change.
    */
   @Override
   void updateQuery(XQuery dx, String oname, boolean queryChange)
      throws Exception;

   /**
    * Add or replace a query in the query repository.
    * @param dx new query.
    * @param oname old name of the query, if the name has been changed.
    * @param actionRecord control whether write audit record when remote in
    * designer.
    * Otherwise it should be null.
    * @param queryChange when update dependencies or import, return not change.
    */
   void updateQuery(XQuery dx, String oname, Boolean actionRecord, Boolean queryChange) throws Exception;

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
   XNode getMetaData(Object session, XDataSource dx, XNode mtype)
      throws Exception;

   /**
    * Build the meta data of this data source as a XNode tree. This
    * method will rebuild the meta data tree everytime it's called.
    * The meta data should be cached by the caller.
    * @param session session object.
    * @param dx data source.
    * @param mtype meta data type, defined in each data source.
    * @param clone true to clone the cached node.
    * @return the root node of the meta data tree.
    * @deprecated
    */
   @Deprecated
   XNode getMetaData(Object session, XDataSource dx, XNode mtype,
                     boolean clone) throws Exception;

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
   XNode getMetaData(Object session, XDataSource dx, XNode mtype, boolean clone,
                     MetaDataListener listener) throws Exception;

   /**
    * Asynchronously builds the meta data of this data source as a XNode tree. This method will
    * rebuild the meta data tree everytime it's called. The meta data should be cached by the
    * caller.
    *
    * @param session session object.
    * @param dx      data source.
    * @param mtype   meta data type, defined in each data source.
    * @param clone   true to clone the cached node.
    *
    * @return a Future that will contain the root node of the meta data tree.
    */
   Future<XNode> getMetaDataAsync(Object session, XDataSource dx, XNode mtype, boolean clone,
                                  MetaDataListener listener);

   /**
    * Clear cached data.
    */
   void clearCache();

   /**
    * Refresh matadata of a datasource. It will remove the related file caches
    * and memory caches.
    *
    * @param dxName the specified datasource name.
    */
   void refreshMetaData(String dxName);

   /**
    * Rename transform assets dependent on the source.
    * @param info the rename dependency info.
    */
   void renameTransform(RenameDependencyInfo info);

   /**
    * Rename transform assets dependent on the source.
    * @param info the rename dependency info.
    */
   void renameTransform(RenameInfo info);

   /**
    * Clear the cached meta data.
    */
   void refreshMetaData();

   /**
    * Rename a query folder in the repository.
    * @param nname new name of query folder.
    * @param oname old name of the query folder, if the name has been
    * changed. Otherwise it should be null.
    */
   void renameQueryFolder(String nname, String oname) throws Exception;

   void renameSourceFolder(XDataSource source, String oname);

   interface MetaDataListener {
      void start(XDataSource dx, String msg);
      void end(XDataSource dx);
   }
}

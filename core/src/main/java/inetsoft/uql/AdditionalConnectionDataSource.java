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
package inetsoft.uql;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.service.XEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;

public abstract class AdditionalConnectionDataSource<SELF extends AdditionalConnectionDataSource<SELF>>
   extends XDataSource
{
   protected AdditionalConnectionDataSource(String type, Class<SELF> selfClass) {
      super(type);
      this.selfClass = selfClass;
   }

   /**
    * Check if additional connection exists.
    */
   public boolean containDatasource(String name) {
      String path = getFullName() + "/" + name;
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, path, null);
      return getRegistry().containObject(entry);
   }

   /**
    * Add additional connection.
    */
   public void addDatasource(AdditionalConnectionDataSource<?> datasource) {
      SELF ads = selfClass.cast(datasource);
      String path = getFullName() + "/" + ads.getFullName();
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, path, null);
      ads.setBaseDatasource(self());
      getRegistry().setObject(entry, new XDataSourceWrapper(ads));
   }

   /**
    * Get names of additional connections.
    */
   public String[] getDataSourceNames() {
      String[] names = null;

      try {
         String prefix = getFullName() + "/";
         AssetEntry[] entries = getRegistry().getEntries(prefix,
                                                         AssetEntry.Type.DATA_SOURCE);
         names = new String[entries.length];

         for(int i = 0; i < entries.length; i++) {
            names[i] = entries[i].getPath().substring(prefix.length());
         }
      }
      catch(Exception e) {
         LOG.error("Failed to get data source names", e);
      }

      return names;
   }

   /**
    * Get additional datasource by special name.
    */
   public SELF getDataSource(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, getFullName() + "/" + name, null);
      XDataSourceWrapper wrap = (XDataSourceWrapper) getRegistry().getObject(entry, true);
      SELF ds = wrap != null ? selfClass.cast(wrap.getSource()) : null;

      if(ds != null) {
         ds.setBaseDatasource(self());
      }
      else if(wrap == null) {
         LOG.debug("Additional datasource {}/{} was not found in the datasource registry.", getFullName(), name);
      }
      else {
         LOG.debug("Datasource wrapper for {}/{} is missing a datasource.", getFullName(), name);
      }

      return ds;
   }

   /**
    * Rename additional datasource.
    */
   public void renameDatasource(String oname, String nname) {
      SELF ds = getDataSource(oname);
      ds.setName(nname);
      String oldPath = getFullName() + "/" + oname;
      String newPath = getFullName() + "/" + nname;
      getRegistry().updateObject(oldPath, newPath, AssetEntry.Type.DATA_SOURCE,
                                 new XDataSourceWrapper(ds));

      renameDatasourceChildren(oname, nname);
      removeMetaData(oname);
   }

   protected void renameDatasourceChildren(String oname, String nname) {
   }

   /**
    * Remove additional connection.
    */
   public void removeDatasource(String name) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, getFullName() + "/" + name, null);
      getRegistry().removeObject(entry);
      removeMetaData(name);
   }

   private void removeMetaData(String name) {
      XRepository repository;

      try {
         repository = XFactory.getRepository();

         if(repository instanceof XEngine) {
            ((XEngine) repository).removeMetaDataFiles(getFullName() + "__" + name);
         }
      }
      catch(RemoteException ex) {
         LOG.error("Failed to remove meta-data for " +
                      getFullName() + "::" + name, ex);
      }
   }

   /**
    * If this is an 'additional' data source, the base data source is the
    * main (containing) data source.
    */
   public void setBaseDatasource(SELF base) {
      this.base = base;
   }

   public SELF getBaseDatasource() {
      return base;
   }

   protected DataSourceRegistry getRegistry() {
      return DataSourceRegistry.getRegistry();
   }

   private SELF self() {
      return selfClass.cast(this);
   }

   private SELF base;
   private final Class<SELF> selfClass;

   private static final Logger LOG = LoggerFactory.getLogger(AdditionalConnectionDataSource.class);
}

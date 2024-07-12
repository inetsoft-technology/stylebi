/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.dep;

import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * XDataSourceAsset represents a data source type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XDataSourceAsset extends AbstractXAsset implements FolderChangeableAsset {
   /**
    * Data source type XAsset.
    */
   public static final String XDATASOURCE = "XDATASOURCE";

   /**
    * Get data source assets for data source and it's additional data sources.
    */
   public static XDataSourceAsset[] getAssets(String ds) {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      XDataSource xds = registry.getDataSource(ds);
      List<XDataSourceAsset> list = new ArrayList<>();

      if(xds != null) {
         list.add(new XDataSourceAsset(ds));

         if(xds instanceof AdditionalConnectionDataSource) {
            String[] names = ((AdditionalConnectionDataSource<?>) xds).getDataSourceNames();

            for(String name :names) {
               list.add(0, new XDataSourceAsset(ds + "^" + name));
            }
         }
      }

      XDataSourceAsset[] res = new XDataSourceAsset[list.size()];
      return list.toArray(res);
   }

   /**
    * Constructor.
    */
   public XDataSourceAsset() {
      super();
   }

   /**
    * Constructor.
    * @param dataSource the data source name.
    */
   public XDataSourceAsset(String dataSource) {
      this();
      this.dataSource = dataSource;
   }

   public XDataSource getXDataSource() {
      return getRegistry().getDataSource(this.dataSource);
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      String additionalDS = getAdditionalDatasource();
      String ds = getDatasource();
      List<XAssetDependency> list = new ArrayList<>();

      if(additionalDS != null) {
         AdditionalConnectionDataSource<?> jds =
            (AdditionalConnectionDataSource<?>) getRegistry().getDataSource(ds);

         if(jds.containDatasource(additionalDS)) {
            String desc = generateDescription(
               catalog.getString("common.xasset.dataSource", additionalDS),
               catalog.getString("common.xasset.dataSource", ds));
            XAssetDependency dependency = new XAssetDependency(new XDataSourceAsset(ds),
               this, XAssetDependency.XDATASOURCE_XDATASOURCE, desc);
            list.add(dependency);
         }
         else {
            this.dataSource = ds;
         }
      }

      XDataModel model = DataSourceRegistry.getRegistry().getDataModel(ds);

      if(model != null) {
         String[] vpmNames = model.getVirtualPrivateModelNames();

         for(int i = 0; i < vpmNames.length; i++) {
            VirtualPrivateModelAsset vpmAsset = new VirtualPrivateModelAsset(
               ds + "^" + vpmNames[i], true);
            String desc = generateDescription(
               catalog.getString("common.xasset.vpm", vpmNames[i]),
               catalog.getString("common.xasset.dataSource", ds));
            // use vpm depends on datasource to follow the topology
            XAssetDependency dependency = new XAssetDependency(this,
               vpmAsset, XAssetDependency.VPM_XDATASOURCE, desc);
            list.add(dependency);
         }
      }

      return list.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return dataSource;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return XDATASOURCE;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return null;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf('^');
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      this.dataSource = identifier.substring(idx + 1);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.dataSource = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return toIdentifier0(dataSource);
   }

   private String toIdentifier0(String dataSource) {
      return getClass().getName() + "^" + dataSource;
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder) {
      return getChangeFolderIdentifier(oldFolder, newFolder, null);
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser) {
      if(oldFolder == null || newFolder == null) {
         return toIdentifier();
      }

      String newDatasource = FolderChangeableAsset.changeFolder(getPath(), oldFolder, newFolder);

      return toIdentifier0(newDatasource);
   }

   /**
    * Check if this asset is visible to client users.
    * The additional connections are invisible, but it also need be exported.
    */
   @Override
   public boolean isVisible() {
      return getAdditionalDatasource() == null;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      Document doc = Tool.parseXML(input);

      if(doc == null) {
         return;
      }

      final TransformerManager transf = TransformerManager.getManager(TransformerManager.SOURCE);
      doc = (Document) transf.transform(doc);

      Element elem = doc.getDocumentElement();
      boolean overwriting = config != null && config.isOverwriting();
      String ds = getDatasource();
      String ads = getAdditionalDatasource();

      if(ads == null) {
         String datasource = getDataSourceName(ds);

         if(getRegistry().containDatasource(datasource)) {
            if(!overwriting) {
               return;
            }

            // @by yanie: bug1418014734688
            // We should keep the folders in existed datasource,
            // Otherwise, after import, the old folders will be lost and
            // the querys in those folders will be lost in StyleStudio's
            // Data Source tree node
            String[] folders;

            if(getRegistry().containDatasource(ds)) {
               XDataSource existingDatasource = getRegistry().getDataSource(ds);

               if(existingDatasource != null) {
                  folders = existingDatasource.getFolders();
                  getRegistry().setExistQueryFolders(folders);
               }
            }

            getRegistry().updateDataSource(datasource, elem, isImport);
         }
         else {
            getRegistry().setExistQueryFolders(new String[0]);
            getRegistry().parseDomain(elem);
            getRegistry().parseXDataSource(elem, isImport);
         }

         getRegistry().setExistQueryFolders(new String[0]);
      }
      else {
         XDataSource dx = getRegistry().getDataSource(ds);

         if(dx instanceof AdditionalConnectionDataSource) {
            AdditionalConnectionDataSource<?> jdx = (AdditionalConnectionDataSource<?>) dx;
            elem = Tool.getChildNodeByTagName(elem, "datasource");
            XDataSource dx1 = getRegistry().parseXDataSource2(elem, false);

            if(dx1 instanceof AdditionalConnectionDataSource) {
               if(jdx.containDatasource(ads) && !overwriting) {
                  return;
               }

               jdx.addDatasource((AdditionalConnectionDataSource<?>) dx1);
            }
         }
      }
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" + replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      writeXML(writer);
      writer.flush();
      return true;
   }

   @Override
   public boolean exists() {
      String ds = getDatasource();
      String datasource = getDataSourceName(ds);
      return getRegistry().containDatasource(datasource);
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      XDataSource xDataSource = getXDataSource();
      return xDataSource == null ? 0 : xDataSource.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.DATA_SOURCE, dataSource);
   }

   /**
    * Write xml.
    * param writer the speciefied print writer.
    */
   private synchronized void writeXML(PrintWriter writer) {
      writer.println("<registry>");
      writer.println("<Version>" + FileVersions.DATASOURCE + "</Version>");

      String ds = getDatasource();
      String ads = getAdditionalDatasource();
      XDataSource dx = getRegistry().getDataSource(ds);

      if(ads != null && dx instanceof AdditionalConnectionDataSource) {
         dx = ((AdditionalConnectionDataSource<?>) dx).getDataSource(ads);
      }

      if(dx != null) {
         writer.println("<datasource name=\"" + Tool.escape(dx.getFullName()) +
            "\" type=\"" + dx.getType() + "\">");
         dx.writeXML(writer);
         writer.println("</datasource>");

         if(dx instanceof AdditionalConnectionDataSource) {
            String[] names = ((AdditionalConnectionDataSource<?>) dx).getDataSourceNames();

            for(String name : names) {
               AdditionalConnectionDataSource<?> jds =
                  ((AdditionalConnectionDataSource<?>) dx).getDataSource(name);
               writer.println("<additional name=\"" +
                  Tool.escape(jds.getFullName()) + "\" type=\"" + jds.getType() +
                  "\" parent=\"" + Tool.escape(dx.getFullName()) + "\">");
               jds.writeXML(writer);
               writer.println("</additional>");
            }
         }
      }

      XDomain domain = getRegistry().getDomain(dataSource);

      if(domain != null) {
         domain.writeXML(writer);
      }

      writer.println("</registry>");
   }

   /**
    * Get data source registry.
    */
   private DataSourceRegistry getRegistry() {
      if(registry == null) {
         registry = DataSourceRegistry.getRegistry();
      }

      return registry;
   }

   public String getDatasource() {
      String[] strs = dataSource.split("\\^");
      return strs[0];
   }

   private String getAdditionalDatasource() {
      String[] strs = dataSource.split("\\^");
      return strs.length > 1 ? strs[1] : null;
   }

   /**
    * Get the datasource type.
    */
   public String getDataSourceType() {
      XDataSource xds = getRegistry().getDataSource(getDatasource());
      return xds != null ? xds.getType() : XDataSource.JDBC;
   }

   public String getDataSourceName(String name) {
      String result = name;
      String[] sources = getRegistry().getDataSourceFullNames();
      String src = DataSourceFolder.getDisplayName(name);
      String tar;

      for(String source : sources) {
         tar = DataSourceFolder.getDisplayName(source);

         if(Tool.equals(src, tar)) {
            result = source;
            break;
         }
      }

      return result;
   }

   private String dataSource;
   private transient DataSourceRegistry registry;
}

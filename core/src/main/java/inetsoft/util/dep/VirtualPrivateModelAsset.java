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
import inetsoft.uql.erm.*;
import inetsoft.uql.erm.vpm.VirtualPrivateModel;
import inetsoft.uql.erm.vpm.VpmCondition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * VirtualPrivateModelAsset represents a VPM asset.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class VirtualPrivateModelAsset extends AbstractXAsset implements FolderChangeableAsset {
   /**
    * VPM type XAsset.
    */
   public static final String VPM = "VPM";

   /**
    * Constructor.
    */
   public VirtualPrivateModelAsset() {
      super();
   }

   /**
    * Constructor.
    * @param path the full path of VPM.
    */
   public VirtualPrivateModelAsset(String path) {
      this(path, false);
   }

   /**
    * Constructor.
    * @param path the full path of VPM.
    */
   public VirtualPrivateModelAsset(String path, boolean ignoreDatasourceDependecy) {
      this();

      this.path = path;
      this.ignoreDatasourceDependecy = ignoreDatasourceDependecy;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      if(getDataSource() == null || getVPM() == null) {
         return new XAssetDependency[0];
      }

      List<XAssetDependency> dependencies = new ArrayList<>();
      String desc = null;

      if(!ignoreDatasourceDependecy) {
         desc = generateDescription(
            catalog.getString("common.xasset.vpm", getVPM()),
            catalog.getString("common.xasset.dataSource", getDataSource()));
         dependencies.add(new XAssetDependency(
            new XDataSourceAsset(getDataSource()), this,
            XAssetDependency.VPM_XDATASOURCE, desc));
      }

      XDataModel model =
         DataSourceRegistry.getRegistry().getDataModel(getDataSource());
      VirtualPrivateModel vpm = model.getVirtualPrivateModel(getVPM());

      if(vpm != null) {
         Enumeration<VpmCondition> conditions = vpm.getConditions();

         while(conditions.hasMoreElements()) {
            VpmCondition condition = conditions.nextElement();
            String table = condition.getTable();

            if(condition.getType() == VpmCondition.PHYSICMODEL && table != null) {
               desc = generateDescription(
                  catalog.getString("common.xasset.vpm", getVPM()),
                  catalog.getString("common.xasset.xpartition", table));
               dependencies.add(new XAssetDependency(
                  new XPartitionAsset(getDataSource() + "^" + table, ignoreDatasourceDependecy), this,
                     XAssetDependency.VPM_XPARTITION, desc));
            }
         }
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return path;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return VPM;
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
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      path = identifier.substring(idx + 1);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.path = path;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return toIdentifier0(path);
   }

   private String toIdentifier0(String path) {
      return getClass().getName() + "^" + path;
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder) {
      return getChangeFolderIdentifier(oldFolder, newFolder, null);
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser) {
      String newPath = FolderChangeableAsset.changeFolder(path, oldFolder, newFolder);

      return toIdentifier0(newPath);
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      Element elem = Tool.parseXML(input).getDocumentElement();
      boolean overwriting = config != null && config.isOverwriting();

      if(getModel() == null) {
         LOG.warn(
            "Cannot parse virtual private model '{}': parent data source '{}' " +
               "is not defined", getVPM(), getDataSource());
         return;
      }

      if(getModel().getVirtualPrivateModel(getVPM()) != null && !overwriting) {
         return;
      }

      VirtualPrivateModel vpm = new VirtualPrivateModel();
      vpm.parseXML(elem);

      if(getModel().containsVirtualPrivateModel(vpm)) {
         getModel().removeVirtualPrivateModel(vpm);
      }

      getModel().addVirtualPrivateModel(vpm, !isImport);
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      VirtualPrivateModel vpm = getModel().getVirtualPrivateModel(getVPM());

      if(vpm == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
         replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer =
         new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      vpm.writeXML(writer);
      writer.flush();
      return true;
   }

   public VirtualPrivateModel getVirtualPrivateModel() {
      return getModel() != null ? getModel().getVirtualPrivateModel(getVPM()) : null;
   }

   @Override
   public boolean exists() {
      return getVirtualPrivateModel() != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      VirtualPrivateModel virtualPrivateModel = getVirtualPrivateModel();
      return virtualPrivateModel == null ? 0 : virtualPrivateModel.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      //use database permission
      return new Resource(ResourceType.DATA_SOURCE, getDataSource());
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

   /**
    * Get data model.
    */
   private XDataModel getModel() {
      if(model == null) {
         model = getRegistry().getDataModel(getDataSource());
      }

      return model;
   }

   /**
    * Get data source name.
    */
   public String getDataSource() {
      if(path == null) {
         return null;
      }

      String[] strs = Tool.split(path, '^');
      return strs[0];
   }

   /**
    * Get VPM name.
    */
   public String getVPM() {
      if(path == null) {
         return null;
      }

      String[] strs = Tool.split(path, '^');

      if(strs.length > 1) {
         return strs[1];
      }

      return null;
   }

   private String path;
   // if vpm was dependency of datasource, should not add datasource as dependecy.
   private transient boolean ignoreDatasourceDependecy;
   private transient XDataModel model;
   private transient DataSourceRegistry registry;
   private static final Logger LOG =
      LoggerFactory.getLogger(VirtualPrivateModelAsset.class);
}

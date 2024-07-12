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
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * XPartitionAsset represents a physical view asset.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class XPartitionAsset extends AbstractXAsset implements FolderChangeableAsset {
   /**
    * Physical view type XAsset.
    */
   public static final String XPARTITION = "XPARTITION";

   /**
    * Constructor.
    */
   public XPartitionAsset() {
      super();
   }

   /**
    * Constructor.
    * @param path the full path of physical view.
    */
   public XPartitionAsset(String path) {
      this(path, false);
   }

   /**
    * Constructor.
    * @param path the full path of physical view.
    */
   public XPartitionAsset(String path, boolean ignoreDatasourceDependecy) {
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
      if(getDataSource() == null || getPartition() == null) {
         return new XAssetDependency[0];
      }

      XPartition base = null;
      XPartition partition = null;

      if(getModel() == null) {
         return new XAssetDependency[0];
      }

      base = getModel().getPartition(getPartition());

      if(base == null) {
         return new XAssetDependency[0];
      }

      String extended = getExtendedPartition();

      if(extended != null) {
         partition = base.getPartition(extended);
      }

      List<XAssetDependency> dependencies = new ArrayList<>();

      if(partition != null) {
         getDependencies(dependencies, partition);
      }
      else {
         getDependencies(dependencies, base);
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   private void getDependencies(List<XAssetDependency> dependencies,
                                XPartition partition) {
      if(partition == null) {
         return;
      }

      String ds = getDataSource();

      XPartition base = partition.getBasePartition();

      if(base != null) {
         String desc = generateDescription(
            catalog.getString("common.xasset.xpartition", partition.getName()),
            catalog.getString("common.xasset.xpartition", base.getName()));
         String path =
            XUtil.getDataModelDisplayPath(ds, base.getFolder(), null, base.getName());
         dependencies.add(new XAssetDependency(new XPartitionAsset(path), this,
            XAssetDependency.XPARTITION_XPARTITION, desc));
      }

      if(!ignoreDatasourceDependecy) {
         String conn = partition.getConnection();
         String ds1 = conn == null ? ds : ds + "^" + conn;

         String desc = generateDescription(
            catalog.getString("common.xasset.xpartition", partition.getName()),
            catalog.getString("common.xasset.dataSource", ds1));
         dependencies.add(new XAssetDependency(
            new XDataSourceAsset(ds1), this,
            XAssetDependency.XPARTITION_XDATASOURCE, desc));
      }
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
      return XPARTITION;
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
   public String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser) {
      String newPath = FolderChangeableAsset.changeFolder(path, oldFolder, newFolder);

      return toIdentifier0(newPath);
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder) {
      return getChangeFolderIdentifier(oldFolder, newFolder, null);
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      Element elem = Tool.parseXML(input).getDocumentElement();
      XPartition partition = new XPartition();
      partition.parseXML(elem);
      boolean overwriting = config != null && config.isOverwriting();
      XDataModel model = getModel();

      if(model == null) {
         LOG.warn(
            "Cannot parse partition '{}': parent data model of '{}' is not defined",
            getPartition(), getDataSource());
         return;
      }

      XPartition base = model.getPartition(getPartition());
      String extended = getExtendedPartition();

      if(extended == null) {
         if(base != null && !overwriting) {
            return;
         }

         getModel().addPartition(partition, isImport);
      }

      if(extended != null && base != null) {
         if(base.containPartition(extended) && !overwriting) {
            return;
         }

         base.addPartition(partition, isImport);
      }
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      XPartition partition = getModel().getPartition(getPartition());

      if(partition == null) {
         return false;
      }

      if(getExtendedPartition() != null) {
         partition = partition.getPartition(getExtendedPartition());
      }

      if(partition == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
         replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer =
         new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      partition.writeXML(writer);
      writer.flush();
      return true;
   }

   @Override
   public boolean exists() {
      return getXPartition() != null;
   }

   public XPartition getXPartition() {
      final XDataModel dataModel = getModel();

      if(dataModel == null) {
         return null;
      }

      return dataModel.getPartition(getPartition());
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      XPartition xPartition = getXPartition();
      return xPartition == null ? 0 : xPartition.getLastModified();
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
    *
    * if parition has folder, the path is: "ds^_^folder^base^name"
    *             has no folder, the path is: "ds^base^name"
    * Get physical view name.
    */
   public String getPartition() {
      if(path == null) {
         return null;
      }

      boolean hasFolder = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER) != -1;
      String[] strs = Tool.split(path, '^');

      if(hasFolder && strs.length > 3) {
         return strs[3];
      }

      if(strs.length > 1) {
         return strs[1];
      }

      return null;
   }

   public String getModelFolder() {
      if(path == null) {
         return null;
      }

      int idx = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

      if(idx == -1) {
         return null;
      }

      String substring = path.substring(idx + XUtil.DATAMODEL_FOLDER_SPLITER.length());
      idx = substring.indexOf("^");

      if(idx == -1) {
         return null;
      }

      return substring.substring(0, idx);
   }

   /**
    *Get parent partition.
    */
   public String getParentPartition() {
      if(path == null) {
         return null;
      }

      boolean hasFolder = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER) != -1;
      String[] strs = Tool.split(path, '^');

      if(hasFolder && strs.length > 4) {
         return strs[3];
      }

      if(!hasFolder && strs.length > 2) {
         return strs[1];
      }

      return null;
   }

   /**
    * Get physical view name.
    */
   private String getExtendedPartition() {
      if(path == null) {
         return null;
      }

      boolean hasFolder = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER) != -1;
      String[] strs = Tool.split(path, '^');

      if(hasFolder && strs.length > 4) {
         return strs[4];
      }

      if(!hasFolder && strs.length > 2) {
         return strs[2];
      }

      return null;
   }

   private String path;
   // if current partition was dependency of datasource, should not add datasource as dependecy.
   private transient boolean ignoreDatasourceDependecy;
   private transient XDataModel model;
   private transient DataSourceRegistry registry;
   private static final Logger LOG = LoggerFactory.getLogger(XPartitionAsset.class);
}

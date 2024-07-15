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
package inetsoft.util.dep;

import inetsoft.report.Hyperlink;
import inetsoft.sree.security.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * XLogicalModelAsset represents a logical model asset.
 *
 * @version 9.5
 * @author InetSoft Technology Corp
 */
public class XLogicalModelAsset extends AbstractXAsset implements
   FolderChangeableAsset, AutoDrillAsset
{
   /**
    * Logical model type XAsset.
    */
   public static final String XLOGICALMODEL = "XLOGICALMODEL";

   public static XLogicalModelAsset[] getAssets(String path) {
      XLogicalModelAsset[] res = new XLogicalModelAsset[0];
      XLogicalModelAsset asset = new XLogicalModelAsset(path);

      if(asset.getDataSource() == null || asset.getModelName() == null ||
         asset.getModel() == null)
      {
         return new XLogicalModelAsset[] {asset};
      }

      XLogicalModel model =
         asset.getModel().getLogicalModel(asset.getModelName());
      List<XLogicalModelAsset> list = new ArrayList<>();

      if(model != null) {
         String folder = model.getFolder();
         String[] names = model.getLogicalModelNames();

         for(String name : names) {
            String key = XUtil.getDataModelDisplayPath(
               asset.getDataSource(), folder, model.getName(), name);
            list.add(new XLogicalModelAsset(key));
         }
      }

      list.add(asset);
      res = new XLogicalModelAsset[list.size()];
      return list.toArray(res);
   }

   /**
    * Constructor.
    */
   public XLogicalModelAsset() {
      super();
   }

   /**
    * Constructor.
    * @param path the full path of logical model.
    */
   public XLogicalModelAsset(String path) {
      this();
      this.path = path;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      if(getDataSource() == null || getModelName() == null ||
         getModel() == null)
      {
         return new XAssetDependency[0];
      }

      XLogicalModel base;
      XLogicalModel lmodel = null;
      base = getModel().getLogicalModel(getModelName());

      if(base == null) {
         return new XAssetDependency[0];
      }

      String extended = getExtendedModelName();

      if(extended != null) {
         lmodel = base.getLogicalModel(extended);
      }

      List<XAssetDependency> dependencies = new ArrayList<>();

      if(lmodel != null) {
         getDependencies(dependencies, lmodel);
      }
      else {
         getDependencies(dependencies, base);
      }

      return dependencies.toArray(new XAssetDependency[0]);
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   private void getDependencies(List<XAssetDependency> dependencies, XLogicalModel lmodel) {
      if(lmodel == null) {
         return;
      }

      XLogicalModel base = lmodel.getBaseModel();
      String ds = lmodel.getDataSource();

      if(base != null) {
         String desc = generateDescription(
            catalog.getString("common.xasset.model", lmodel.getName(), ds),
            catalog.getString("common.xasset.model", base.getName(), ds));
         String path =
            XUtil.getDataModelDisplayPath(ds, base.getFolder(), null, base.getName());
         dependencies.add(new XAssetDependency(new XLogicalModelAsset(path),
            this, XAssetDependency.XLOGICALMODEL_XLOGICALMODEL, desc));
      }

      Enumeration<XEntity> entities = lmodel.getEntities();
      String fromDesc = catalog.getString("common.xasset.model2", getModelName(), getDataSource());

      while(entities.hasMoreElements()) {
         XEntity entity = entities.nextElement();
         Enumeration<XAttribute> xattrs = entity.getAttributes();

         while(xattrs.hasMoreElements()) {
            XAttribute xattr = xattrs.nextElement();

            // check if uses a browse data query
            String query = xattr.getBrowseDataQuery();

            if(query != null) {
               String desc = generateDescription(
                  catalog.getString("common.xasset.model1",
                  getModelName(), getDataSource()),
                  catalog.getString("common.xasset.query1", query));
               dependencies.add(new XAssetDependency(
                  new XQueryAsset(query), this,
                  XAssetDependency.XLOGICALMODEL_BROWSE_XQUERY, desc));
            }

            getAutoDrillDependency(xattr.getXMetaInfo(), dependencies, fromDesc);
         }
      }

      if(lmodel.getPartition() != null) {
         XPartition partition = getModel().getPartition(lmodel.getPartition());
         XPartition extended = null;

         if(partition != null && base != null) {
            extended =
               partition.getPartitionByConnection(lmodel.getConnection());
         }

         String key = partition == null ? null : (partition.getName() +
            (extended == null ? "" : "^" + extended.getName()));

         if(key != null) {
            String desc = generateDescription(
               catalog.getString("common.xasset.model", lmodel.getName(), ds),
               catalog.getString("common.xasset.xpartition", key));
            String path = XUtil.getDataModelDisplayPath(
               getDataSource(), partition.getFolder(), null, key);
            dependencies.add(new XAssetDependency(new XPartitionAsset(path), this,
               XAssetDependency.XLOGICALMODEL_XPARTITION, desc));
         }
      }

      if(getExtendedModelName() != null) {
         String key = getDataSource() + "^" + getExtendedModelName();
         String desc = generateDescription(
            catalog.getString("common.xasset.model", lmodel.getName(), ds),
            catalog.getString("common.xasset.dataSource", key));
         dependencies.add(new XAssetDependency(
            new XDataSourceAsset(key), this,
            XAssetDependency.XPARTITION_XDATASOURCE, desc));
      }
   }

   public String getName() {
      String name = getExtendedModelName();
      name = getModelName() + (name == null ? "" : "^" + name);

      return name;
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
      return XLOGICALMODEL;
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
      if(getModel() == null) {
         return;
      }

      Element elem = Tool.parseXML(input).getDocumentElement();
      XLogicalModel model = new XLogicalModel("");
      model.parseXML(elem);
      boolean overwriting = config != null && config.isOverwriting();
      XLogicalModel base = getModel().getLogicalModel(getModelName());
      String extended = getExtendedModelName();

      if(!StringUtils.isEmpty(model.getFolder())) {
         getModel().addFolder(model.getFolder());
         XFactory.getRepository().updateDataModel(getModel());
      }

      if(extended == null) {
         if(base != null && !overwriting) {
            return;
         }

         getModel().addLogicalModel(model, !isImport);
      }

      if(extended != null && base != null) {
         if(base.containLogicalModel(extended) && !overwriting) {
            return;
         }

         base.addLogicalModel(model, !isImport);
      }
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      if(getModel() == null) {
         return false;
      }

      XLogicalModel lmodel = getModel().getLogicalModel(getModelName());

      if(lmodel == null) {
         return false;
      }

      if(getExtendedModelName() != null) {
         lmodel = lmodel.getLogicalModel(getExtendedModelName());
      }

      if(lmodel == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
         replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer =
         new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      lmodel.writeXML(writer);
      writer.flush();
      return true;
   }

   public XLogicalModel getXLogicalModel() {
      XDataModel dataModel = getModel();

      if(dataModel == null) {
         return null;
      }

      return getModel().getLogicalModel(getModelName());
   }

   @Override
   public boolean exists() {
      return getModel() != null && getModel().getLogicalModel(getModelName()) != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      XLogicalModel xLogicalModel = getXLogicalModel();
      return xLogicalModel == null ? 0 : xLogicalModel.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.QUERY, getModelName() + "::" + getDataSource());
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
    * Get logical model name.
    */
   public String getModelName() {
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

   /**
    * Get parent logical model name.
    */
   public String getModelFolder() {
      if(path == null) {
         return null;
      }

      int idx = path.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);

      if(idx == -1) {
         return null;
      }

      String subStr = path.substring(idx + XUtil.DATAMODEL_FOLDER_SPLITER.length());
      idx = subStr.indexOf("^");

      if(idx == -1) {
         return null;
      }

      return subStr.substring(0, idx);
   }

   /**
    * Get parent logical model name.
    */
   public String getParentModelName() {
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
    * Get extended logical model name.
    */
   private String getExtendedModelName() {
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

   /**
    * Get auto drill links.
    */
   public List<Hyperlink> getDrillLinks() {
      //by justin.rokisky bug1375086650871: getModel() can return null. If null
      //line 445 will cause a NPE
      if(getModel() == null) {
          return new ArrayList<>();
      }

      List<Hyperlink> drillLinks = new ArrayList<>();
      XLogicalModel base = getModel().getLogicalModel(getModelName());
      XLogicalModel lmodel = null;

      if(base == null) {
         return new ArrayList<>();
      }

      String extended = getExtendedModelName();

      if(extended != null) {
         lmodel = base.getLogicalModel(extended);
      }

      lmodel = lmodel == null ? base : lmodel;
      Enumeration entities = lmodel.getEntities();

      while(entities.hasMoreElements()) {
         XEntity entity = (XEntity) entities.nextElement();
         Enumeration xattrs = entity.getAttributes();

         while(xattrs.hasMoreElements()) {
            XAttribute xattr = (XAttribute) xattrs.nextElement();
            XMetaInfo meta = xattr.getXMetaInfo();

            if(meta == null) {
               continue;
            }

            meta.processDrillLinks(drillLinks);
         }
      }

      return drillLinks;
   }

   private String path;
   private transient XDataModel model;
   private transient DataSourceRegistry registry;
}

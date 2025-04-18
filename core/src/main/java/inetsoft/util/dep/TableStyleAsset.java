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

import inetsoft.report.LibManager;
import inetsoft.report.internal.StyleTreeModel;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Table style asset represents a table style type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class TableStyleAsset extends AbstractXAsset {
   /**
    * Table style type XAsset.
    */
   public static final String TABLESTYLE = "TABLESTYLE";

   /**
    * Constructor.
    */
   public TableStyleAsset() {
      super();
   }

   /**
    * Constructor.
    * @param style the specified table style name.
    */
   public TableStyleAsset(String style) {
      this();
      this.style = style;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      return new XAssetDependency[0];
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return style;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return TABLESTYLE;
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

      identifier = identifier.substring(idx + 1);
      style = identifier;
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      LibManager manager = LibManager.getManager();
      XTableStyle tableStyle = manager.getTableStyle(path);

      if(tableStyle == null) {
         throw new ResourceNotFoundException("Cannot find table style with path: " + path);
      }

      style = tableStyle.getID();
   }

   public String getLabel() {
      LibManager manager = LibManager.getManager();
      XTableStyle tableStyle = manager.getTableStyle(style);
      return tableStyle.getName();
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + style;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      parseAsset(input, (comment, in) -> parseContent(in, config, comment));
      input.close();
   }

   private void parseContent(InputStream in, XAssetConfig config, String comment) throws Exception {
      XTableStyle xstyle = XTableStyle.getXTableStyle(null, in);

      if(xstyle == null) {
         return;
      }

      LibManager manager = LibManager.getManager();
      String id = xstyle.getID();
      String name = xstyle.getName();

      if(id == null) {
         id = StyleTreeModel.getTableStyleID(name);
      }

      if(id == null) {
         id = manager.getNextStyleID(name);
      }

      xstyle.setID(id);

      boolean overwriting = config != null && config.isOverwriting();

      if(manager.getTableStyle(id) != null && !overwriting) {
         return;
      }

      if(!manager.containsFolder(StyleTreeModel.getParentPath(name)) ||
         manager.isAuditStyleFolder(StyleTreeModel.getParentPath(name)))
      {
         String[] folders = Tool.split(name, LibManager.SEPARATOR, false);
         String folder = null;

         for(int i = 0; i < folders.length - 1; i++) {
            folder = folder == null ? folders[i] :
               (folder + LibManager.SEPARATOR + folders[i]);

            if(!manager.containsFolder(folder) || manager.isAuditStyleFolder(folder)) {
               StyleTreeModel.addFolder(folder);
            }
         }
      }

      manager.setTableStyle(id, xstyle);
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      String style0 = Tool.replaceAll(style, "/", "~");
      style0 = StyleTreeModel.getTableStyleID(style0);
      LibManager manager = LibManager.getManager();
      XTableStyle xstyle = manager.getTableStyle(style0);

      if(xstyle == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
                                          replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      xstyle.export(out);
      out.flush();
      return true;
   }

   public XTableStyle getXTableStyle() {
      String id = Tool.replaceAll(style, "/", "~");
      id = StyleTreeModel.getTableStyleID(id);
      return LibManager.getManager().getTableStyle(id);
   }

   @Override
   public boolean exists() {
      return getXTableStyle() != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      XTableStyle xTableStyle = getXTableStyle();
      return xTableStyle == null ? 0 : xTableStyle.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.TABLE_STYLE, style);
   }

   private String style;
   private String label;
}

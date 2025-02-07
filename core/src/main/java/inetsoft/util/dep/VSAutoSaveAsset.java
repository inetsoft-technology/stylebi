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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import inetsoft.web.AutoSaveUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.Principal;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * AutoSaveAsset represents a auto save file type asset.
 *
 * @author InetSoft Technology Corp
 * @version 13.4
 */
public class VSAutoSaveAsset extends ViewsheetAsset {
   /**
    * AutoSave type XAsset.
    */
   public static final String AUTOSAVEVS = "AUTOSAVEVS";

   /**
    * Constructor.
    */
   public VSAutoSaveAsset() {
      super();
   }

   /**
    * Constructor.
    *
    * @param viewsheet the autosave asset entry.
    */
   public VSAutoSaveAsset(AssetEntry viewsheet) {
      this();
   }

   @Override
   public String getType() {
      return AUTOSAVEVS;
   }

   @Override
   public String getPath() {
      return autoFile;
   }

   @Override
   public IdentityID getUser() {
      return new IdentityID("__NULL__", OrganizationManager.getInstance().getCurrentOrgID());
   }

   public int hashCode() {
      int hash = 0;

      if(autoFile != null) {
         return autoFile.hashCode();
      }

      return hash;
   }

   /**
    * Parse content of the specified asset from input stream.
    */
   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport) throws Exception {
      boolean overwriting = config != null && config.isOverwriting();
      String file = AutoSaveUtils.RECYCLE_PREFIX + SUtil.addAutoSaveOrganization(autoFile);
      Principal principal = ThreadContext.getContextPrincipal();

      if(!overwriting && AutoSaveUtils.exists(file, principal)) {
         return;
      }

      AutoSaveUtils.writeAutoSaveFile(input, file, principal);
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      JarOutputStream out = getJarOutputStream(output);
      writeAutoSaveFile(out);
      out.flush();

      return true;
   }

   private void writeAutoSaveFile(JarOutputStream out) throws Exception {
      String file = AutoSaveUtils.RECYCLE_PREFIX + SUtil.addAutoSaveOrganization(autoFile);
      Principal principal = ThreadContext.getContextPrincipal();

      if(!AutoSaveUtils.exists(file, principal)) {
         return;
      }

      InputStream in = AutoSaveUtils.getInputStream(file, principal);
      String assetName = getType() + "_" + getClass().getName() + "^" + replaceFilePath(autoFile);
      ZipEntry zipEntry = new ZipEntry(assetName);
      out.putNextEntry(zipEntry);

      byte[] buf = new byte[1024];
      int len;

      while((len = in.read(buf)) >= 0) {
         out.write(buf, 0, len);
      }

      out.closeEntry();
      in.close();
   }

    /**
    * Parse an identifier to a real asset.
    *
    * @param identifier the specified identifier, usually with the format of
    *                   ClassName^identifier.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf('^');
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      parseIdentifier(identifier, null);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      autoFile = path;
      entry = AutoSaveUtils.createAssetEntry(autoFile);
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getChangeFolderIdentifier(String oldFolder, String newFolder, IdentityID newUser) {
      throw new UnsupportedOperationException();
   }

   private String autoFile = null;
   private static final Logger LOG = LoggerFactory.getLogger(VSAutoSaveAsset.class);
}

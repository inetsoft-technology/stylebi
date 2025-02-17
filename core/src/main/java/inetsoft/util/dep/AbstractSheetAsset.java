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
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Resource;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.FunctionIterator;
import inetsoft.uql.asset.internal.ScriptIterator.ScriptListener;
import inetsoft.uql.asset.internal.ScriptIterator.Token;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * AbstractSheetAsset describes worksheet, viewsheet and snapshot.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractSheetAsset extends AbstractXAsset {
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

      TransformerManager transf = TransformerManager.getManager(getTransformerType());
      // @by ChrisS bug1402502061808 2014-6-18
      // Set the "sourceName" parameter property in TransformerManager.
      Properties propsOut = new Properties();
      propsOut.setProperty("sourceName", entry.getName());
      transf.setProperties(propsOut);
      transf.transform(doc);

      Element root = doc.getDocumentElement();
      boolean overwriting = config != null && config.isOverwriting();
      AbstractSheet sheet0 = getSheet();
      AssetEntry entry = getAssetEntry();
      parseSheet(sheet0, root, config, entry.getOrgID());
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AssetEntry pentry = entry.getParent();

      if(engine.containsEntry(entry)) {
         if(!overwriting) {
            return;
         }
      }
      else if(SUtil.isDuplicatedEntry(engine, entry)) {
         String msg = catalog.getString("em.partialDeployment.importSheetError.duplicated",
            entry.getDescription());

         throw new Exception(msg);
      }
      else if(checkRepositoryPath() && SUtil.isDuplicatedRepositoryPath(
         entry.getPath(), entry.getUser(), RepositoryEntry.VIEWSHEET))
      {
         String msg = catalog.getString(
            "em.partialDeployment.importSheetError.duplicated",
            entry.getDescription());

         throw new Exception(msg);
      }

      // add folder when worksheet entry's parent is not existing
      if(pentry != null && (entry.isWorksheet() || entry.isViewsheet() || entry.isVSSnapshot())) {
         ArrayList folders = new ArrayList();
         AssetEntry folder = pentry;
         boolean isExisting;

         while(true) {
            try {
               isExisting = engine.containsEntry(folder);
            }
            catch(Exception e) {
               isExisting = false;
            }

            if(isExisting || folder == null) {
               break;
            }

            folders.add(0, folder);
            folder = folder.getParent();
         }

         for(int i = 0; i < folders.size(); i++) {
            engine.addFolder((AssetEntry) folders.get(i), null);
         }
      }

      if(pentry.isRepositoryFolder()) {
         String ppath = entry.getParentPath();
         RepletRegistry registry = RepletRegistry.getRegistry(getUser());

         if(getUser() != null && ppath != null && !Tool.equals(ppath, "/") &&
            !ppath.startsWith(Tool.MY_DASHBOARD))
         {
            ppath = Tool.MY_DASHBOARD + "/" + ppath;
         }

         if(ppath != null && !"/".equals(ppath) && !registry.isFolder(ppath)) {
            registry.addFolder(ppath);
            registry.save();
         }
      }

      if(sheet0 instanceof Viewsheet) {
         ((Viewsheet) sheet0).clearLayoutState();
      }

      if(engine instanceof AbstractAssetEngine) {
         ((AbstractAssetEngine) engine).setSheet(
            entry, sheet0, null, overwriting, true, true, false);
      }
      else {
         engine.setSheet(entry, sheet0, null, overwriting);
      }

      parseContent0(root);
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AbstractSheet sheet0 = getCurrentSheet(engine);

      if(sheet0 == null) {
         return false;
      }

      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
         replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer =
         new PrintWriter(new OutputStreamWriter(out, "UTF8"));
      writeContent0(sheet0, writer);
      writer.flush();

      sheet0.writeData(out);
      out.flush();

      return true;
   }

   /**
    * Get representing asset entry.
    */
   public AssetEntry getAssetEntry() {
      return entry;
   }

   /**
    * Set representing asset entry.
    */
   public void setAssetEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return entry.getPath();
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return entry.getUser();
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      AbstractSheet currentSheet = getCurrentSheet(engine);
      return currentSheet == null ? lastModifiedTime : currentSheet.getLastModified();
   }

   @Override
   public boolean exists() {
      try {
         return AssetUtil.getAssetRepository(false).containsEntry(getAssetEntry());
      }
      catch(Exception e) {
         LOG.warn("Failed to check if asset exists", e);
      }

      return false;
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(entry.isWorksheet() ? ResourceType.ASSET :
                             ResourceType.REPORT, entry.getPath());
   }

   /**
    * Get sheet to be exported.
    */
   public AbstractSheet getCurrentSheet(AssetRepository engine) {
      AbstractSheet sheet0 = getSheet(false);

      if(sheet0 != null) {
         return sheet0;
      }

      try {
         sheet0 = engine.getSheet(entry, null, false, AssetContent.ALL);
      }
      catch(Exception e) {
         LOG.error("Failed to get current sheet", e);
      }

      return sheet0;
   }

   /**
    * Get corresponding sheet object.
    */
   public AbstractSheet getSheet(boolean created) {
      return !created || sheet != null ? sheet : getSheet0();
   }

   /**
    * Get corresponding sheet object.
    */
   public AbstractSheet getSheet() {
      return getSheet(true);
   }

   /**
    * Set the sheet.
    */
   public void setSheet(AbstractSheet sheet) {
      this.sheet = sheet;
   }

   /**
    * Process scripts. Export reference be part of the dependencies.
    */
   protected void processScript(String script, List<XAssetDependency> deps, String description,
                                AbstractSheet sheet)
   {
      if(script == null || script.length() == 0) {
         return;
      }

      final Vector functions = new Vector();
      manager = getLibManager();

      FunctionIterator iterator = new FunctionIterator(script);
      ScriptListener listener = new ScriptListener() {
         @Override
         public void nextElement(Token token, Token pref, Token cref) {
            if(token.isRef() && !functions.contains(token.val) &&
               manager.findScriptName(token.val) != null)
            {
               functions.add(token.val);
            }
         }
      };

      iterator.addScriptListener(listener);
      iterator.iterate();
      iterator.removeScriptListener(listener);

      for(int i = 0; i < functions.size(); i++) {
         String function = (String) functions.get(i);
         String desc = generateDescription(description,
            catalog.getString("common.xasset.script", function));
         int depType = getScriptDependencyType();

         if(depType > 0) {
            deps.add(new XAssetDependency(new ScriptAsset(function),
               this, depType, desc));
         }
      }
   }

   /**
    * Parse content from an xml element.
    */
   protected synchronized void parseContent0(Element elem) throws Exception {
   }

   /**
    * Write content of the specified asset to an output stream.
    */
   protected synchronized void writeContent0(AbstractSheet sheet0,
      PrintWriter writer) throws Exception
   {
      Assembly[] assemblies = sheet0.getAssemblies();

      if(assemblies != null) {
         Arrays.stream(assemblies)
            .filter(assembly -> assembly instanceof SnapshotEmbeddedTableAssembly)
            .forEach(assembly -> ((SnapshotEmbeddedTableAssembly) assembly).setShouldDeleteOldFiles(false));
      }

      sheet0.writeXML(writer);
   }

   /**
    * Check if should validate repository path.
    */
   protected boolean checkRepositoryPath() {
      return false;
   }

   /**
    * Get corresponding sheet object.
    */
   protected abstract AbstractSheet getSheet0();

   /**
    * Get transformer type.
    */
   protected abstract String getTransformerType();

   /**
    * Get script dependency type.
    */
   protected int getScriptDependencyType() {
      return -1;
   }

   /**
    * Parse sheet.
    */
   protected void parseSheet(AbstractSheet sheet, Element elem,
                             XAssetConfig config, String orgId)
      throws Exception
   {
      sheet.parseXML(elem);
   }

   /**
    * Get library manager.
    */
   private LibManager getLibManager() {
      if(manager == null) {
         manager = LibManager.getManager();
      }

      return manager;
   }

   protected AbstractSheet sheet;
   protected AssetEntry entry;
   private LibManager manager;
   private static final Logger LOG =
      LoggerFactory.getLogger(AbstractSheetAsset.class);
}

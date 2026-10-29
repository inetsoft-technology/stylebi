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
package inetsoft.uql.asset.sync;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;

/**
 * Rewrites the text of the dependents of a renamed library script function: viewsheet and
 * worksheet scripts and expressions, and other library scripts that call it.
 */
public class AssetScriptDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding worksheet.
    */
   public AssetScriptDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      // a library script is not an asset document, its text lives in the library
      if(asset.isScript()) {
         renameLibraryScript(infos);
         return null;
      }

      return super.process(infos);
   }

   @Override
   protected void renameScript(Element doc, RenameInfo info) {
      renameAssemblyScript(doc, info);
      renameViewsheetScript(doc, info);
      renameExpressions(doc, info);
   }

   /**
    * Rename the function in the scripts of the viewsheet itself: its own script, onInit and
    * onLoad. The paths are relative to the root, an embedded viewsheet's scripts are not run.
    */
   private void renameViewsheetScript(Element doc, RenameInfo info) {
      renameScript(getChildNodes(doc, "./assemblyInfo/script"), info);
      renameScript(getChildNodes(doc, "./viewsheetInfo/viewsheetInfo/initScript"), info);
      renameScript(getChildNodes(doc, "./viewsheetInfo/viewsheetInfo/loadScript"), info);
   }

   private void renameAssemblyScript(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc,
         "//assemblies/oneAssembly/assembly/assemblyInfo/script");
      renameScript(list, info);
   }

   protected void renameScript(NodeList scriptList, RenameInfo info) {
      if(!info.isScriptFunction()) {
         return;
      }

      for(int i = 0; i < scriptList.getLength(); i++) {
         renameScriptText((Element) scriptList.item(i), info);
      }
   }

   /**
    * Rename the function in script expressions: viewsheet calc fields and worksheet expression
    * columns. A SQL expression is not script, so it is left alone.
    */
   private void renameExpressions(Element doc, RenameInfo info) {
      NodeList list = getChildNodes(doc, ".//dataRef[@class='inetsoft.uql.erm.ExpressionRef']");

      for(int i = 0; i < list.getLength(); i++) {
         Element expr = (Element) list.item(i);

         if(expr != null && !isSqlColumn(expr)) {
            renameScriptText(expr, info);
         }
      }
   }

   /**
    * Whether the expression is a SQL expression. ColumnRef writes the sql attribute only when it
    * is false and reads a missing one as true, so a worksheet column is script only when it says
    * sql="false". A viewsheet calc field is always script.
    */
   private boolean isSqlColumn(Element expr) {
      return expr.getParentNode() instanceof Element parent &&
         !CalculateRef.class.getName().equals(parent.getAttribute("class")) &&
         !"false".equals(parent.getAttribute("sql"));
   }

   private void renameScriptText(Element scriptEle, RenameInfo info) {
      if(scriptEle == null) {
         return;
      }

      String script = Tool.getValue(scriptEle);
      String nscript = LibManager.renameFunctionReferences(
         info.getOldName(), info.getNewName(), script);

      if(nscript != null && !nscript.equals(script)) {
         replaceCDATANode(scriptEle, nscript);
      }
   }

   /**
    * Rewrite the calls in the text of a library script that calls the renamed function. The
    * dependency record is moved by the rename task itself.
    */
   private void renameLibraryScript(List<RenameInfo> infos) {
      if(getAssetFile() != null || infos == null) {
         return;
      }

      for(RenameInfo info : infos) {
         if(!info.isScriptFunction()) {
            continue;
         }

         // the task runs on a thread without the user's organization, use the renamed one
         LibManager lib = LibManagerProvider.getInstance().getManager(info.getOrganizationId());
         String name = asset.getName();

         synchronized(lib) {
            String script = lib.getScript(name);
            String nscript = LibManager.renameFunctionReferences(
               info.getOldName(), info.getNewName(), script);

            if(nscript != null && !nscript.equals(script)) {
               lib.setScript(name, nscript);

               try {
                  lib.save();
               }
               catch(Exception e) {
                  LOG.warn("Failed to save library script {} after renaming function {}",
                           name, info.getOldName(), e);
               }
            }
         }
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetScriptDependencyTransformer.class);
}

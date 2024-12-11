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

import inetsoft.report.internal.Util;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AssetScriptDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding worksheet.
    */
   public AssetScriptDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   @Override
   protected void renameScript(Element doc, RenameInfo info) {
      renameAssemblyScript(doc, info);
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

      String oname = info.getOldName();
      String nname = info.getNewName();

      for(int i = 0; i < scriptList.getLength(); i++) {
         Element scriptEle = (Element) scriptList.item(i);

         if(scriptEle != null) {
            String nscript = Util.renameScriptDepended(oname, nname, Tool.getValue(scriptEle));

            if(nscript != null) {
               replaceCDATANode(scriptEle, nscript);
            }
         }
      }
   }
}

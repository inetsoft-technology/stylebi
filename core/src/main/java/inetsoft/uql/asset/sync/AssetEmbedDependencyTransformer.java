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

import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AssetEmbedDependencyTransformer is a class to rename dependenies for the vs used embed vs
 * rename assets
 *
 * @version 13.4
 * @author InetSoft Technology Corp
 */
public class AssetEmbedDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) linked the renamed asset.
    */
   public AssetEmbedDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameEmbedVS(Element relem, RenameInfo rinfo) {
      String oname = rinfo.getOldName();
      String nname = rinfo.getNewName();
      AssetEntry oentry = AssetEntry.createAssetEntry(oname);
      AssetEntry nentry = AssetEntry.createAssetEntry(nname);
      String opath = oentry.getPath();
      String npath = nentry.getPath();

      NodeList list = getChildNodes(relem, ".//viewsheetEntry/assetEntry");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         if(elem == null) {
            continue;
         }

         String elePath = Tool.getChildValueByTagName(elem, "path");
         String eleUser = Tool.getChildValueByTagName(elem, "user");
         String eleScope = elem.getAttribute("scope");


         if(!Tool.equals(opath, elePath) || !isUserMatch(oentry, eleUser) ||
            !Tool.equals(eleScope, oentry.getScope() + ""))
         {
            continue;
         }

         replaceChildValue(elem, "path", opath, npath, true);
         replaceChildValue(elem, "description", oentry.getDescription(), nentry.getDescription(),
            false);
         replacePropertyNode0(elem, "_description_", oentry.getDescription(),
            nentry.getDescription(), false);
         replaceChildValue(elem, "user",
                           oentry.getUser() == null ? null : oentry.getUser().name,
                           nentry.getUser() == null ? null : nentry.getUser().name,
                           true, true);
         elem.setAttribute("scope", nentry.getScope() + "");
      }
   }

   private boolean isUserMatch(AssetEntry oentry, String eleUser) {
      if(oentry.getUser() == null) {
         return eleUser == null;
      }
      else {
         return Tool.equals(oentry.getUser().convertToKey(), eleUser);
      }
   }
}
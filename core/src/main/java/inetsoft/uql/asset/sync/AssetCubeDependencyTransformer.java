/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.sync;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AssetSqlTableDependencyTransformer is a class to rename dependenies for ws/vs binding sql table
 *
 * @version 13.2
 * @author InetSoft Technology Corp
 */
public class AssetCubeDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) binding model.
    */
   public AssetCubeDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   @Override
   protected void renameVSSource(Element doc, RenameInfo info) {
      renameSourceInfos(doc, info);
   }

   @Override
   protected void renameSourceInfos(Element doc, RenameInfo info) {
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(info.isCube()) {
         NodeList list = getChildNodes(doc, getVSSourcePath());
         NodeList list2 = getChildNodes(doc, "./allcalc/tname");

         for(int i = 0; i < list.getLength(); i++) {
            Element assembly = (Element) list.item(i);
            NodeList slist = getChildNodes(assembly, getVSSourcePath());

            for(int j = 0; j < slist.getLength(); j++) {
               Element sinfo = (Element) slist.item(i);
               String source = Tool.getValue(sinfo);

               if(source != null && (source.startsWith(oname + "/") || source.equals(oname))) {
                  replaceCDATANode(sinfo, source.replace(oname, nname));
               }
            }

            for(int j = 0; j < list2.getLength(); j++) {
               Element sinfo = (Element) list2.item(i);
               String source = Tool.getValue(sinfo);

               if(source != null && (source.startsWith(oname + "/") || source.equals(oname))) {
                  replaceCDATANode(sinfo, source.replace(oname, nname));
               }
            }
         }

         for(int i = 0; i < list.getLength(); i++) {
            Element sinfo = (Element) list.item(i);
            String source = Tool.getValue(sinfo);

            if(source != null && (source.startsWith(oname + "/") || source.equals(oname))) {
               replaceCDATANode(sinfo, source.replace(oname, nname));
            }
         }
      }
   }

   @Override
   protected void renameWSSource(Element elem, RenameInfo info) {
      if(elem == null || info == null) {
         return;
      }

      NodeList list = Tool.getChildNodesByTagName(elem, "prefix");
      String oname = info.getOldName();
      String nname = info.getNewName();

      if(oname.startsWith(Assembly.CUBE_VS)) {
         oname = oname.substring(Assembly.CUBE_VS.length());
      }

      if(nname.startsWith(Assembly.CUBE_VS)) {
         nname = nname.substring(Assembly.CUBE_VS.length());
      }

      for(int i = 0; i < list.getLength(); i++) {
         Element prefix = (Element) list.item(i);

         if(Tool.equals(Tool.getValue(prefix), oname)) {
            replaceCDATANode(prefix, nname);
         }
      }
   }
}

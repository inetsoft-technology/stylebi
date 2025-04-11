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

import inetsoft.report.Hyperlink;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * AssetHyperlinkDependencyTransformer is a class to rename dependenies for the vs link used
 * rename assets
 *
 * @version 13.3
 * @author InetSoft Technology Corp
 */
public class AssetHyperlinkDependencyTransformer extends AssetDependencyTransformer {
   /**
    * Create a transformer to rename dependenies for the asset(ws/vs) linked the renamed asset.
    */
   public AssetHyperlinkDependencyTransformer(AssetEntry asset) {
      super(asset);
   }

   protected void renameLink(Element relem, RenameInfo rinfo) {
      String oname = rinfo.getOldName();
      String nname = rinfo.getNewName();

      NodeList list = getChildNodes(relem, ".//Hyperlink");

      for(int i = 0; i < list.getLength(); i++) {
         replaceAttribute((Element) list.item(i), "Link", oname, nname, true);
      }
   }

   protected RenameDependencyInfo renameAutoDrills(Element relem, List<RenameInfo> rinfos) {
      boolean autoDrillChanged = false;

      for(RenameInfo rinfo : rinfos) {
         autoDrillChanged |= renameAutoDrill(relem, rinfo);
      }

      return autoDrillChanged ? createRenameDependency(asset.toIdentifier(), new ArrayList<>()) : null;
   }

   protected boolean renameAutoDrill(Element relem, RenameInfo rinfo) {
      NodeList list = getChildNodes(relem, ".//XDrillInfo/drillPath");
      boolean autoDrillChanged = false;

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);

         String oname = rinfo.getOldName();
         String nname = rinfo.getNewName();

         if((Hyperlink.VIEWSHEET_LINK + "").equals(Tool.getAttribute(elem, "linkType"))) {
            autoDrillChanged |= replaceAttribute(elem, "link", oname, nname, true);
         }
      }

      return autoDrillChanged;
   }
}
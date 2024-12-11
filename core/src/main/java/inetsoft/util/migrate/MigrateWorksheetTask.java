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

package inetsoft.util.migrate;

import inetsoft.report.Hyperlink;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.util.AbstractIdentity;
import inetsoft.util.Tool;
import org.w3c.dom.*;

public class MigrateWorksheetTask extends MigrateDocumentTask {
   public MigrateWorksheetTask(AssetEntry entry, AbstractIdentity oOrg, AbstractIdentity nOrg) {
      super(entry, oOrg, nOrg);
   }

   public MigrateWorksheetTask(AssetEntry entry, String oname, String nname) {
      super(entry, oname, nname);
   }

   @Override
   protected void processAssemblies(Element root) {
      NodeList list = getChildNodes(root,
         "//assemblies/oneAssembly/assembly/assemblyInfo/mirrorAssembly/mirrorAssembly");

      for(int i = 0; i < list.getLength(); i++) {
         Element mirror = (Element) list.item(i);
         String source = Tool.getAttribute(mirror, "source");

         if(source == null && !(getNewOrganization() instanceof Organization)) {
            continue;
         }

         String newSource = source == null ? null : AssetEntry.createAssetEntry(source)
            .cloneAssetEntry((Organization) getNewOrganization()).toIdentifier(true);

         mirror.setAttribute("source", newSource);
         updateAssetDependency(mirror);
      }

      if(this.getEntry().isWorksheet()) {
         String ouser = root.getAttribute("modifiedBy");

         if(Tool.equals(ouser, getOldName())) {
            root.setAttribute("modifiedBy", getNewName());
         }

         ouser = root.getAttribute("createdBy");

         if(Tool.equals(ouser, getOldName())) {
            root.setAttribute("createdBy", getNewName());
         }
      }

      list = getChildNodes(root, "//dependencies");

      for(int i = 0; i < list.getLength(); i++) {
         Element dependency = (Element) list.item(i);

         if(dependency == null) {
            continue;
         }

         updateAssetEntry(dependency);
      }

      updateDrillPaths(root);
   }

   private void updateAssetDependency(Element mirror) {
      NodeList list = getChildNodes(mirror, ".//assetDependency");

      for(int i = 0; i < list.getLength(); i++) {
         Element dependency = (Element) list.item(i);
         String value = Tool.getValue(dependency);

         if(getNewOrganization() instanceof Organization) {
            replaceElementCDATANode(dependency, AssetEntry.createAssetEntry(value)
               .cloneAssetEntry((Organization) getNewOrganization()).toIdentifier(true));
         }
      }
   }
}

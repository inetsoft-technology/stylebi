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

import inetsoft.sree.store.port.TransformerUtil;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

/**
 *  process sync the portal dashboard asset information when the dependency assets is change.
 *
 */
public class DashboardAssetDependencyTransformer extends DependencyTransformer {
   public DashboardAssetDependencyTransformer(AssetEntry dashboard) {
      this.dashboard = dashboard;
   }

   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      try {
         if(dashboard == null || !dashboard.isDashboard()) {
            return null;
         }

         Document doc;

         //just implement tansform the specific xml file.
         if(getAssetFile() != null) {
            doc = getAssetFileDoc();

            if(doc == null) {
               return null;
            }

            renameDashboard(doc.getDocumentElement(), infos);
            TransformerUtil.save(getAssetFile().getAbsolutePath(), doc);
         }
         else {
            DashboardRegistry registry = dashboard.getUser() == null ?
               DashboardRegistry.getRegistry(dashboard.getOrgID()) :
               DashboardRegistry.getRegistry(dashboard.getUser());
            VSDashboard dash = (VSDashboard) registry.getDashboard(dashboard.getName());

            if(dash == null || dash.getViewsheet() == null) {
               return null;
            }

            String id = dash.getViewsheet().getIdentifier();

            for(int i = 0; i < infos.size(); i++) {
               RenameInfo info = infos.get(i);

               if(Tool.equals(info.getOldName(), id)) {
                  AssetEntry vs = AssetEntry.createAssetEntry(info.getNewName());
                  dash.getViewsheet().setIdentifier(info.getNewName());
                  dash.getViewsheet().setPath(vs.getPath());
               }
            }

            registry.save();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   private void renameDashboard(Element ele, List<RenameInfo> infos) {
      for(RenameInfo info : infos) {
         if(info == null || !info.isViewsheet()) {
            continue;
         }

         Element entryNode = getChildNode(ele, "//dashboard/entry");

         if(entryNode == null) {
            return;
         }

         String identifier = entryNode.getAttribute("identifier");

         if(Tool.isEmptyString(identifier)) {
            return;
         }

         identifier = Tool.byteDecode(identifier);

         if(!Tool.equals(identifier, info.getOldName())) {
            return;
         }

         entryNode.setAttribute("identifier", Tool.byteEncode(info.getNewName()));

         try {
            AssetEntry assetEntry = AssetEntry.createAssetEntry(info.getNewName());

            if(assetEntry != null && assetEntry.isViewsheet()) {
               Element path = getChildNode(entryNode, "path");

               if(path != null) {
                  replaceCDATANode(path, assetEntry.getPath());
               }

               Element owner = getChildNode(entryNode, "owner");

               if(owner != null) {
                  replaceCDATANode(owner, assetEntry.getUser().convertToKey());
               }
            }
         }
         catch(Exception ignore){
         }
      }
   }

   private AssetEntry dashboard;
   private static final Logger LOG = LoggerFactory.getLogger(DashboardAssetDependencyTransformer.class);
}

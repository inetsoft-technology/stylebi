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

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.store.port.TransformerUtil;
import inetsoft.sree.web.dashboard.*;
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
               DashboardRegistryManager.getInstance().getRegistry(dashboard.getOrgID()) :
               DashboardRegistryManager.getInstance().getRegistry(dashboard.getUser());
            // changed in the registry file, not in the cached registry (Bug #77272)
            registry.updateDashboard(dashboard.getName(), d -> {
               VSDashboard dash = (VSDashboard) d;

               if(dash.getViewsheet() == null) {
                  return false;
               }

               String id = dash.getViewsheet().getIdentifier();
               boolean changed = false;

               for(int i = 0; i < infos.size(); i++) {
                  RenameInfo info = infos.get(i);

                  if(Tool.equals(info.getOldName(), id)) {
                     AssetEntry vs = AssetEntry.createAssetEntry(info.getNewName());
                     dash.getViewsheet().setIdentifier(info.getNewName());
                     dash.getViewsheet().setPath(vs.getPath());
                     changed = true;
                  }
               }

               return changed;
            });
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

         if(!needTransform(identifier, info)) {
            continue;
         }

         // byteEncode leaves control chars as is, which XML can't hold. Encode them the way
         // ViewsheetEntry.writeXML does, without its Tool.escape: the DOM serializer escapes
         // the attribute (Bug #77808).
         entryNode.setAttribute("identifier", RepositoryEntry.encodeControlChars(
            Tool.byteEncode(info.getNewName()), false));

         try {
            AssetEntry assetEntry = AssetEntry.createAssetEntry(info.getNewName());

            if(assetEntry != null && assetEntry.isViewsheet()) {
               Element path = getChildNode(entryNode, "path");

               if(path != null) {
                  // byte-encoded like RepositoryEntry.writeContents, as parseContents
                  // byte-decodes it (Bug #77808)
                  replaceCDATANode(path, RepositoryEntry.encodeControlChars(
                     Tool.byteEncode(assetEntry.getPath()), true));
               }

               Element owner = getChildNode(entryNode, "owner");

               if(owner != null) {
                  replaceCDATANode(owner, assetEntry.getUser() == null ? null : assetEntry.getUser().convertToKey());
               }
            }
         }
         catch(Exception ignore){
         }
      }
   }

   private boolean needTransform(String identifier, RenameInfo info) {
      boolean match = Tool.equals(identifier, info.getOldName());

      if(!match && getAssetFile() != null && dashboard != null) {
         AssetEntry entry = AssetEntry.createAssetEntry(identifier);

         if(!Tool.equals(entry.getOrgID(), dashboard.getOrgID()) ||
            entry.getUser() != null && !Tool.equals(entry.getUser().orgID, dashboard.getOrgID()))
         {
            entry.setOrgID(dashboard.getOrgID());

            if(entry.getUser() != null) {
               entry.getUser().setOrgID(dashboard.getOrgID());
            }

            identifier = entry.toIdentifier(true);
            match = Tool.equals(identifier, info.getOldName());
         }
      }

      return match;
   }

   private AssetEntry dashboard;
   private static final Logger LOG = LoggerFactory.getLogger(DashboardAssetDependencyTransformer.class);
}

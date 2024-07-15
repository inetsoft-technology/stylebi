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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

public class LogicalModelPartitionDependencyTransformer extends DependencyTransformer {
   public LogicalModelPartitionDependencyTransformer(AssetEntry logicalModel) {
      this.logicalModel = logicalModel;
   }

   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      try {
         if(logicalModel == null) {
            return null;
         }

         Document doc;

         //just implement tansform the specific xml file.
         if(getAssetFile() != null) {
            doc = getAssetFileDoc();

            if(doc == null) {
               return null;
            }

            renameLogicalModel(doc.getDocumentElement(), infos);
            TransformerUtil.save(getAssetFile().getAbsolutePath(), doc);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   private void renameLogicalModel(Element element, List<RenameInfo> infos) {
      for(RenameInfo info : infos) {
         if(info == null || !info.isPartition()) {
            continue;
         }

         AssetEntry oldPartitionEntry = AssetEntry.createAssetEntry(info.getOldName());

         if(oldPartitionEntry == null || !oldPartitionEntry.isPartition()) {
            return;
         }

         AssetEntry newPartitionEntry = AssetEntry.createAssetEntry(info.getNewName());

         if(newPartitionEntry == null || !newPartitionEntry.isPartition()) {
            return;
         }

         String partition = element.getAttribute("partition");

         if(!Tool.equals(partition, oldPartitionEntry.getPath())) {
            return;
         }

         element.setAttribute("partition", newPartitionEntry.getPath());
      }
   }

   private AssetEntry logicalModel;

   private static final Logger LOG =
      LoggerFactory.getLogger(LogicalModelPartitionDependencyTransformer.class);
}

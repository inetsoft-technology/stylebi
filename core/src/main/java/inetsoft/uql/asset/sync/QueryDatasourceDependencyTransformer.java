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
import org.w3c.dom.*;

import java.util.List;

public class QueryDatasourceDependencyTransformer extends DependencyTransformer {
   public QueryDatasourceDependencyTransformer(AssetEntry query) {
      this.query = query;
   }

   @Override
   public RenameDependencyInfo process(List<RenameInfo> infos) {
      try {
         if(query == null) {
            return null;
         }

         Document doc;

         //just implement tansform the specific xml file.
         if(getAssetFile() != null) {
            doc = getAssetFileDoc();

            if(doc == null) {
               return null;
            }

            renameQuery(doc.getDocumentElement(), infos);
            TransformerUtil.save(getAssetFile().getAbsolutePath(), doc);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to rename dependency assets: ", e);
      }

      return null;
   }

   private void renameQuery(Element ele, List<RenameInfo> infos) {
      for(RenameInfo info : infos) {
         if(info == null || !info.isDataSource()) {
            continue;
         }

         String datasource = ele.getAttribute("datasource");

         if(Tool.isEmptyString(datasource)) {
            return;
         }

         AssetEntry oldDatasourceEntry = AssetEntry.createAssetEntry(info.getOldName());

         if(oldDatasourceEntry == null || !oldDatasourceEntry.isDataSource()) {
            return;
         }

         AssetEntry newDatasourceEntry = AssetEntry.createAssetEntry(info.getNewName());

         if(newDatasourceEntry == null || !newDatasourceEntry.isDataSource()) {
            return;
         }

         if(!Tool.equals(datasource, oldDatasourceEntry.getPath())) {
            return;
         }

         ele.setAttribute("datasource", newDatasourceEntry.getPath());
         renameQuery0(ele, oldDatasourceEntry, newDatasourceEntry);
      }
   }

   private static void renameQuery0(Element query, AssetEntry oldDatasourceEntry,
                                    AssetEntry newDatasourceEntry)
   {
      NodeList querys = query.getChildNodes();

      if(querys == null) {
         return;
      }

      for(int j = 0; j < querys.getLength(); j++) {
         Node queryItem = querys.item(j);

         if(queryItem == null) {
            continue;
         }

         if(queryItem.getNodeName().startsWith("query_")) {
            Element name = Tool.getChildNodeByTagName(queryItem, "datasource");
            String value = Tool.getValue(name);

            if(Tool.equals(oldDatasourceEntry.getPath(), value)) {
               DependencyTransformer.replaceElementCDATANode(name, newDatasourceEntry.getPath());
            }
         }
      }
   }

   private AssetEntry query;

   private static final Logger LOG =
      LoggerFactory.getLogger(QueryDatasourceDependencyTransformer.class);
}

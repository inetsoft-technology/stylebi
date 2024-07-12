/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { RepositoryEntryType } from "./repository-entry-type.enum";
import { RepositoryTreeNode } from "../../em/src/app/settings/content/repository/repository-tree-node";

export interface RepositoryFolderMeta {
   metaItems: RepositoryFolderMetaItem[];
}

export interface RepositoryFolderMetaItem {
   assetTypeLabel: string;
   assetCount: number;
}

export class RepositoryEntryHelper {
   public static getNodeTypeMetaLabel(node: RepositoryTreeNode, parentNode?: RepositoryTreeNode): string {
      let parentNodeType = parentNode?.type;

      if((parentNodeType & RepositoryEntryType.PARTITION) == RepositoryEntryType.PARTITION) {
         parentNodeType = RepositoryEntryType.PARTITION;
      }
      else if((parentNodeType & RepositoryEntryType.LOGIC_MODEL) == RepositoryEntryType.LOGIC_MODEL) {
         parentNodeType = RepositoryEntryType.LOGIC_MODEL;
      }

      let itemType = node.type;

      if((itemType & RepositoryEntryType.PARTITION) == RepositoryEntryType.PARTITION) {
         itemType = RepositoryEntryType.PARTITION;
      }
      else if((itemType & RepositoryEntryType.LOGIC_MODEL) == RepositoryEntryType.LOGIC_MODEL) {
         itemType = RepositoryEntryType.LOGIC_MODEL;
      }
      else if((itemType & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE) {
         itemType = node.label === "_#(js:Data Model)" ? RepositoryEntryType.DATA_MODEL :
            RepositoryEntryType.DATA_SOURCE;
      }
      else if(this.isFolder(node.type)) {
         itemType = RepositoryEntryType.FOLDER;
      }
      else if((itemType & RepositoryEntryType.AUTO_SAVE_FILE) == RepositoryEntryType.AUTO_SAVE_FILE) {
         itemType = itemType & ~RepositoryEntryType.AUTO_SAVE_FILE
      }

      return this.getTypeMetaLabel(itemType, parentNodeType);

   }
   public static getTypeMetaLabel(type: RepositoryEntryType, parentType?: RepositoryEntryType): string {
      if(this.isFolder(type)) {
         return "_#(js:Folders)";
      }

      switch(type) {
      case RepositoryEntryType.VIEWSHEET:
         return "_#(js:Viewsheets)";
      case RepositoryEntryType.WORKSHEET:
         return "_#(js:Worksheets)";
      case RepositoryEntryType.DATA_SOURCE:
         if((parentType & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE) {
            return "_#(js:Addional Sources)"
         }

         return "_#(js:Data Sources)";
      case RepositoryEntryType.DATA_MODEL:
         return null;
      case RepositoryEntryType.SCRIPT:
         return "_#(js:Scripts)";
      case RepositoryEntryType.TABLE_STYLE:
         return "_#(js:Table Styles)";
      case RepositoryEntryType.QUERY:
         return "_#(js:Queries)";
      case RepositoryEntryType.LOGIC_MODEL:
         if(parentType == RepositoryEntryType.LOGIC_MODEL) {
            return "_#(js:Extended Models)"
         }

         return "_#(js:Logical Models)";
      case RepositoryEntryType.PARTITION:
         if(parentType == RepositoryEntryType.PARTITION) {
            return "_#(js:Extended Views)"
         }

         return "_#(js:Physical Views)";
      case RepositoryEntryType.VPM:
         return "_#(js:Virtual Private Models)";
      case RepositoryEntryType.DASHBOARD:
         return "_#(js:Dashboards)";
      case RepositoryEntryType.SCHEDULE_TASK:
         return "_#(js:Schedule Tasks)";
      default:
         return "_#(js:Others)";
      }
   }

   private static isFolder(type: RepositoryEntryType) {
      return (type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER;
   }
}

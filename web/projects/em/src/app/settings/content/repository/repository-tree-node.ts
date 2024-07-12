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
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { FlatTreeNode, TreeDataNode } from "../../../common/util/tree/flat-tree-model";
import { RepositoryEntryHelper } from "../../../../../../shared/data/repository-entry-helper";
import { equalsIdentity, IdentityId } from "../../security/users/identity-id";

export interface RepositoryTreeNode extends TreeDataNode<RepositoryTreeNode> {
   readonly label: string;
   readonly path: string;
   readonly fullPath?: string;
   readonly owner: IdentityId;
   readonly type: RepositoryEntryType;
   readonly readOnly: boolean;
   readonly builtIn: boolean;
   readonly description: string;
   readonly icon: string;
   readonly properties?: any;
   visible: boolean;
   lastModifiedTime?: string;
}

export class RepositoryFlatNode extends FlatTreeNode<RepositoryTreeNode> {
   parent?: RepositoryFlatNode;

   /**
    * Checks whether a pair of nodes should be treated equally for tracking expansion state
    */
   public equals(node: this): boolean {
      return node != null &&
         this.data.type === node.data.type &&
         this.data.path === node.data.path &&
         equalsIdentity(this.data.owner, node.data.owner) &&
         this.level === node.level;
   }

   public getNodeMetaMap(): Map<string, number> {
      let metaMap = new Map<string, number>();

      if(!this.data.children) {
         return metaMap;
      }

      this.data.children.forEach(item => {
         let typeLabel = RepositoryEntryHelper.getNodeTypeMetaLabel(item, this.data);

         if(!typeLabel) {
            return;
         }

         let sameTypeItemsCount = metaMap.get(typeLabel);

         if(!sameTypeItemsCount) {
            sameTypeItemsCount = 1;
         }
         else {
            sameTypeItemsCount += 1;
         }

         metaMap.set(typeLabel, sameTypeItemsCount);
      });

      return metaMap;
   }

   private isFolder(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER;
   }
}

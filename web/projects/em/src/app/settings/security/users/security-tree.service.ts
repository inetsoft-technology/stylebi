/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { Injectable } from "@angular/core";
import { SecurityTreeNode } from "../security-tree-view/security-tree-node";
import { SecurityTreeNodeModel } from "./users-settings-view/security-tree-node-model";

@Injectable()
export class SecurityTreeService {
   createSecurityTreeNode(node: SecurityTreeNodeModel): SecurityTreeNode {
      let children = null;

      if(node.children != null && node.children.length > 0) {
         children = node.children.map((child: SecurityTreeNodeModel) => this.createSecurityTreeNode(child));
      }

      return new SecurityTreeNode(node.identityID, node.type, children, node.readOnly, node.root,
         node.organization);
   }
}

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
import { BehaviorSubject, Observable, of } from "rxjs";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { SearchComparator } from "../../../../../../portal/src/app/widget/tree/search-comparator";
import { IdentityId } from "../users/identity-id";

export interface TreeNodeData {
   identityID: IdentityId;
   type: number;
}

export interface SecurityTreeNodeFunction {
   hasChildren(): boolean;
   getChildren(): Observable<SecurityTreeNode[]>;
}

export class SecurityTreeNode implements TreeNodeData, SecurityTreeNodeFunction {
   public expanded = new BehaviorSubject<boolean>(false);

   constructor(public identityID: IdentityId,
               public type: number,
               public children?: SecurityTreeNode[],
               public readOnly?: boolean,
               public root?: boolean,
               public organization?: string)
   {
   }

   public filter(filterString: string): SecurityTreeNode {
      // non-strict for number/string comparison
      if(this.children != null) {
         const children = this.children
            .map((child) => child.filter(filterString))
            .filter((child) => child != null)
            .sort((a, b) => new SearchComparator(filterString).searchSort(a, b));

         if(children.length > 0) {
            const node = new SecurityTreeNode(this.identityID, this.type, children, this.readOnly);
            node.toggle(true);
            return node;
         }
      }

      if(this.type >= 0 && (this.identityID.name == filterString ||
         filterString && this.identityID.name.toLowerCase().includes(filterString.toLowerCase())))
      {
         return new SecurityTreeNode(this.identityID, this.type, [], this.readOnly);
      }

      return null;
   }

   public hasChildren(): boolean {
      return !!this.children && this.children.length > 0;
   }

   public getChildren(): Observable<SecurityTreeNode[]> {
      return of(this.children);
   }

   public toggle(expand: boolean) {
      this.expanded.next(expand);
   }

   public equals(node: SecurityTreeNode): boolean {
      return node && node.identityID === this.identityID && node.type === this.type;
   }

   public getIcon(expanded: boolean): string {
      let icon: string;

      switch(this.type) {
      case IdentityType.USER:
         icon = "account-icon";
         break;
      case IdentityType.GROUP:
         icon = "user-group-icon";
         break;
      case IdentityType.ROLE:
         icon = "user-roles-icon";
         break;
      case IdentityType.ORGANIZATION:
         icon = "user-organizations-icon";
         break;
      default:
         icon = "folder-icon";
      }

      return icon;
   }
}

/** Flat node with expandable and level information */
export class FlatSecurityTreeNode {
   constructor(public expandable: boolean,
               private data: SecurityTreeNode,
               public level: number)
   {
   }

   public get label() {
      return this.data.identityID.name;
   }

   public get identityID() {
      return this.data.identityID;
   }

   public get type() {
      return this.data.type;
   }

   public getData(): SecurityTreeNode {
      return this.data;
   }

   public toggle(expand: boolean): void {
      this.data.toggle(expand);
   }

   public equals(node: FlatSecurityTreeNode): boolean {
      return node && node.label === this.label && node.type === this.type;
   }

   public getIcon(expanded: boolean): string {
      return this.data.getIcon(expanded);
   }
}


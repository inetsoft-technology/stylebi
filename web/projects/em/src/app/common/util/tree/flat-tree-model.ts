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
export interface TreeDataModel<T extends TreeDataNode<T>> {
   nodes: T[];
}

export interface TreeDataNode<T> {
   children: T[];
}

export interface FlatTreeNodeMenu {
   items: FlatTreeNodeMenuItem[];
}

export interface FlatTreeNodeMenuItem {
   name: string;
   label: string;
   disabled: () => boolean;
}

export class FlatTreeNode<T extends TreeDataNode<T> = TreeDataNode<any>> {
   constructor(public readonly label: string,
               public readonly level: number,
               public readonly expandable: boolean,
               public readonly data?: T,
               public loading: boolean = false,
               public visible: boolean = true,
               private getContextMenu: () => FlatTreeNodeMenu = () => null,
               public getIcon: (expanded: boolean) => string = () => null)
   {
   }

   get contextMenu(): FlatTreeNodeMenu | null {
      if(this.getContextMenu) {
         return this.getContextMenu();
      }

      return null;
   }
}

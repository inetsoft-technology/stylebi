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
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";

/**
 * Interface for classes that hold the context-specific menu actions for a component.
 */
export class ContextMenuActions {
   private menuActions: AssemblyActionGroup[];

   /**
    * Actions that should be displayed in the context menu. Null entries represent a menu
    * separator.
    */
   public get actions(): AssemblyActionGroup[] {
      if(!this.menuActions) {
         this.menuActions = this.createMenuActions([], []);
      }

      return this.menuActions;
   }

   /**
    * Creates the menuActions for this type of assembly.
    */
   protected createMenuActions(actions: AssemblyAction[],
                               groups: AssemblyActionGroup[]): AssemblyActionGroup[]
   {
      if(actions.length > 0) {
         groups.push(new AssemblyActionGroup(actions));
      }

      return groups;
   }
}
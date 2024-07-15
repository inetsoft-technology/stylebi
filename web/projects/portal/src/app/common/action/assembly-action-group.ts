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
import { AssemblyAction } from "./assembly-action";

export class AssemblyActionGroup {

   /**
    * A boolean flag indicating if this group is visible.
    */
   public get visible(): boolean {
      return this.actions && this.actions.some(v => v && v.visible());
   }

   public getVisible(checkVisible?: Function): boolean {
      if(checkVisible) {
         return this.actions && this.actions.some(v => v && v.visible() && checkVisible(v));
      }

      return this.actions && this.actions.some(v => v && v.visible());
   }

   /**
    * Creates a new instance of AssemblyActionGroup.
    *
    * @param actions the actions in the group.
    */
   constructor(public actions: AssemblyAction[] = [],
               public label: () => string = () => "options...",
               public icon: () => string = () => "menu-vertical-icon")
   {
   }
}

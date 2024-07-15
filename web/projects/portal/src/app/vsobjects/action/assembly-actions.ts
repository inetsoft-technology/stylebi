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
import { EventEmitter } from "@angular/core";
import { AssemblyActionEvent } from "../../common/action/assembly-action-event";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssemblyAction } from "../../common/action/assembly-action";

/**
 * Interface for classes that hold the context-specific actions for a selected assembly.
 */
export abstract class AssemblyActions<T> {
   /**
    * The source of the assembly action events.
    */
   onAssemblyActionEvent = new EventEmitter<AssemblyActionEvent<T>>();

   /**
    * The actions that should be displayed on the mini toolbar. Null entries represent a
    * menu separator.
    */
   abstract toolbarActions: AssemblyActionGroup[];

   /**
    * Actions that should be displayed in the context menu. Null entries represent a menu
    * separator.
    */
   abstract menuActions: AssemblyActionGroup[];

   public addActionHandlers(group: AssemblyActionGroup, model: T): void {
      if(group && group.actions) {
         group.actions.forEach((action) => this.addActionHandler(action, model));
      }
   }

   public addActionHandler(action: AssemblyAction, model: T): void {
      if(action.action) {
         return;
      }

      action.action = (event: MouseEvent) => this.fireEvent(action, model, event);

      if(action.childAction) {
        action.childAction().forEach((groupc) => this.addActionHandlers(groupc, model));
      }
   }

   public fireEvent(action: AssemblyAction, model: T, event?: MouseEvent) {
      this.onAssemblyActionEvent.emit(new AssemblyActionEvent<T>(action.id(), model, event));
   }
}

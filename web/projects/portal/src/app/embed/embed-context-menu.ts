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
import { AssemblyActionGroup } from "../common/action/assembly-action-group";
import { AbstractVSActions } from "../vsobjects/action/abstract-vs-actions";
import { MiniToolbarService } from "../vsobjects/objects/mini-toolbar/mini-toolbar.service";
import {
   ActionsContextmenuComponent
} from "../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../widget/fixed-dropdown/dropdown-options";
import { DropdownRef } from "../widget/fixed-dropdown/fixed-dropdown-ref";
import { FixedDropdownService } from "../widget/fixed-dropdown/fixed-dropdown.service";

/**
 * The context menu of an embedded assembly. Each embed component binds (contextmenu) to its own
 * onOpenContextMenu(), and all six of them want exactly this behaviour, so it lives here once
 * instead of being copied a seventh time. Not a service: the components already inject what it
 * needs, and an @Injectable would have to be added to six providers lists to say nothing new.
 */
export namespace EmbedContextMenu {
   /**
    * Opens the assembly's menu at the pointer, if the assembly has anything to put in it.
    *
    * @param actions            the assembly's actions, or null before the component is wired up.
    * @param event              the contextmenu - or click - event that asked for the menu.
    * @param dropdownService    the service the menu is opened through.
    * @param miniToolbarService frozen while the menu is up so the toolbar cannot hide behind it.
    * @param assemblyName       the absolute name the mini toolbar is keyed by.
    */
   export function open(actions: AbstractVSActions<any>, event: MouseEvent,
                        dropdownService: FixedDropdownService,
                        miniToolbarService: MiniToolbarService, assemblyName: string): void
   {
      if(!actions) {
         return;
      }

      const groups: AssemblyActionGroup[] = event.type === "click" ?
         [new AssemblyActionGroup([actions.clickAction])] : actions.menuActions;

      // Bug #75951: an embed assembly can have nothing to offer. The table, crosstab, text,
      // gauge and image embeds put every command on the toolbar and so have no menu actions at
      // all, and every chart menu action is tied to a selection, so a right click on plain plot
      // area leaves all of them hidden. Opening the dropdown anyway gives an empty popup with
      // the mini toolbar frozen behind it. The browser's own menu still stays suppressed, as it
      // would have been below: having nothing of our own to show is not a reason to offer the
      // embedding page's users Reload/Save As.
      if(!AssemblyActionGroup.anyVisible(groups)) {
         event.preventDefault();
         return;
      }

      const dropdown: DropdownRef = show(groups, event, dropdownService);
      miniToolbarService.hiddenFreeze(assemblyName);

      const sub = dropdown.closeEvent.subscribe(() => {
         sub.unsubscribe();
         miniToolbarService.hiddenUnfreeze(assemblyName);
      });
   }

   /**
    * Interface with the dropdown service.
    *
    * @param groups          the action groups to list.
    * @param event           the event that asked for the menu, positioning it.
    * @param dropdownService the service the menu is opened through.
    *
    * @returns a handle on the open menu, for the caller to watch it close.
    */
   function show(groups: AssemblyActionGroup[], event: MouseEvent,
                 dropdownService: FixedDropdownService): DropdownRef
   {
      const options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      const dropdownRef = dropdownService.open(ActionsContextmenuComponent, options);
      const contextmenu: ActionsContextmenuComponent = dropdownRef.componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = groups;
      event.preventDefault();
      return dropdownRef;
   }
}

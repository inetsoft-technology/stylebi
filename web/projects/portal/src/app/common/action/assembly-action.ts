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
/**
 * Interface for classes that provide the implementation of a toolbar or menu action.
 */
import { AssemblyActionGroup } from "./assembly-action-group";

export interface AssemblyAction {
   /**
    * The id for the menu item or toolbar button.
    */
   id: () => string;

   /**
    * The label for the menu item or tooltip text for the toolbar button.
    */
   label: () => string;

   /**
    * The CSS class used for the toolbar button icon.
    */
   icon: () => string;

   /**
    * Determines if the action is currently displayed.
    */
   visible: () => boolean;

   /**
    * Determines if the action is currently enabled.
    */
   enabled: () => boolean;

   /**
    * Action to be performed when the action is invoked.
    */
   action?: (sourceEvent: MouseEvent) => any;

   /**
    * URL to be opened when the action is clicked.
    */
   link?: () => string;

   /**
    * The target window in which the link URL should be opened.
    */
   target?: () => string;

   /**
    * Space-separated classes to apply to the context menu action.
    */
   classes?: () => string;

   /**
    * child action.
    */
   childAction?: () => AssemblyActionGroup[];
}

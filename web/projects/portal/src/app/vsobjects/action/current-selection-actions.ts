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
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { VSSelectionContainerModel } from "../model/vs-selection-container-model";
import { SelectionContainerActions } from "./selection-container-actions";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { FeatureFlagsService } from "../../../../../shared/feature-flags/feature-flags.service";

export class CurrentSelectionActions extends SelectionContainerActions {
   constructor(model: VSSelectionContainerModel, contextProvider, securityEnabled: boolean = false,
               stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               popService: PopComponentService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "current-selection unselect",
            label: () => "_#(js:Unselect)",
            icon: () => "eraser-icon",
            enabled: () => true,
            visible: () => true
         }
      ]));
      return groups;
   }
}

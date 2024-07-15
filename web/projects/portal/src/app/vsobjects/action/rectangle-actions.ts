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
import { ContextProvider } from "../context-provider.service";
import { VSRectangleModel } from "../model/vs-rectangle-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class RectangleActions extends AbstractVSActions<VSRectangleModel> {
   constructor(model: VSRectangleModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               popService: PopComponentService = null,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "rectangle properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer
         },
         {
            id: () => "rectangle show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer
         },
         {
            id: () => "rectangle lock",
            label: () => "_#(js:Lock)",
            icon: () => "fa fa-lock",
            enabled: () => true,
            visible: () => this.composer && !this.model.locked &&
               this.model.containerType != "VSGroupContainer"
         },
         {
            id: () => "rectangle unlock",
            label: () => "_#(js:Unlock)",
            icon: () => "fa fa-unlock",
            enabled: () => true,
            visible: () => this.composer && this.model.locked &&
               this.model.containerType != "VSGroupContainer"
         }
      ]));
      groups.push(new AssemblyActionGroup([{
         id: () => "viewer annotation",
         label: () => "_#(js:Add Annotation)",
         icon: () => "annotation-icon",
         enabled: () => true,
         visible: () => this.securityEnabled && !this.model.inEmbeddedViewsheet,
      }]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());
      groups.push(this.createDefaultAnnotationMenuActions());
      return super.createMenuActions(groups);
   }

   protected getEditScriptActionId(): string {
      return "rectangle edit-script";
   }
}

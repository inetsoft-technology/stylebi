/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { ContextProvider } from "../context-provider.service";
import { VSAnnotationModel } from "../model/annotation/vs-annotation-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AnnotatableActions } from "./annotatable-actions";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class AnnotationActions extends AbstractVSActions<VSAnnotationModel> implements AnnotatableActions {
   constructor(model: VSAnnotationModel,
               contextProvider: ContextProvider,
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
            id: () => "annotation edit" + this.model.absoluteName,
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice
         },
         {
            id: () => "annotation format" + this.model.absoluteName,
            label: () => "_#(js:Format)",
            icon: () => "paint-brush-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice
         },
         {
            id: () => "annotation remove" + this.model.absoluteName,
            label: () => "_#(js:Remove)",
            icon: () => "trash-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice
         }
      ]));
      return super.createMenuActions(groups);
   }
}

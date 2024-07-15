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
import { ContextProvider } from "../context-provider.service";
import { VSTextModel } from "../model/output/vs-text-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AnnotatableActions } from "./annotatable-actions";
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class TextActions extends AbstractVSActions<VSTextModel> implements AnnotatableActions {
   constructor(model: VSTextModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               protected stateProvider: ActionStateProvider = null,
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
            id: () => "text properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected
         },
         {
            id: () => "text show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected
         },
         {
            id: () => "text conditions",
            label: () => "_#(js:Conditions)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected &&
               (!this.model.cubeType || this.model.worksheetCube)
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "text hyperlink",
            label: () => "_#(js:Hyperlink)...",
            icon: () => "fa fa-link",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected
         },
         {
            id: () => "text highlight",
            label: () => "_#(js:Highlight)...",
            icon: () => "fa fa-filter",
            enabled: () => true,
            visible: () => this.composer && !this.annotationsSelected
         }
      ]));
      groups.push(new AssemblyActionGroup([
         {
            id: () => "text annotate",
            label: () => "_#(js:Annotate Component)",
            icon: () => "edit-icon",
            enabled: () => this.securityEnabled,
            visible: () => !this.annotationsSelected && (this.viewer || this.preview) &&
               this.securityEnabled && this.model.enabled && !this.mobileDevice &&
               this.isActionVisibleInViewer("Annotate Component") &&
               (!this.stateProvider || this.stateProvider.isActionVisible("Annotation", this.model))
         }
      ]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());
      groups.push(this.createDefaultAnnotationMenuActions());

      if(this.mobileDevice && (this.viewer || this.preview)) {
         groups.push(new AssemblyActionGroup([this.createClickAction()]));
      }

      return super.createMenuActions(groups);
   }

   protected createClickAction(): AssemblyAction {
      if(!this.viewer && !this.preview) {
         return null;
      }

      return {
         id: () => "text show-hyperlink",
         label: () => "_#(js:Show Hyperlinks)",
         icon: () => "fa fa-link",
         enabled: () => true,
         visible: () => !!this.model.hyperlinks && this.model.hyperlinks.length > 0 &&
            this.isActionVisibleInViewer("Show Hyperlinks")
      };
   }

   protected getEditScriptActionId(): string {
      return "text edit-script";
   }
}

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
import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute, NavigationExtras, Params, Router } from "@angular/router";
import { Subscription } from "rxjs";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ViewData } from "../view-data";
import { HideNavService } from "../../portal/services/hide-nav.service";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel, WizardOriginalInfo } from "../../vs-wizard/model/vs-wizard-model";
import { CloseWizardModel } from "../../vs-wizard/model/close-wizard-model";
import { VSWizardConstants } from "../../vs-wizard/model/vs-wizard-constants";

export enum EditorMode {
   DEFAULT,
   CHART_WIZARD,
   BINDING_EDITOR
}

@Component({
   selector: "v-viewer-edit",
   templateUrl: "viewer-edit.component.html",
   styleUrls: ["viewer-edit.component.scss"],
   providers: [ViewsheetClientService]
})
export class ViewerEditComponent implements OnInit, OnDestroy {
   viewData: ViewData;
   isMetadata: boolean = false;
   isCube: boolean = false;
   bindingPaneParams: any = {};
   editorMode: EditorMode = EditorMode.DEFAULT;
   editedByWizard: boolean = false;
   bindingPaneModel = {
      runtimeId: null,
      objectType: null,
      absoluteName: null,
      wizardOriginalInfo: null
   };
   private _wizardModel: VsWizardModel;
   private routeSubscription: Subscription;

   constructor(private route: ActivatedRoute, private router: Router,
               private viewsheetClient: ViewsheetClientService,
               private hideNavService: HideNavService)
   {
   }

   ngOnInit(): void {
      this.routeSubscription = this.route.data.subscribe((data: { viewData: ViewData }) => {
         this.viewData = data.viewData;
         this.viewsheetClient.runtimeId = this.viewData.runtimeId;
         this.isMetadata = this.viewData.isMetadata;

         this.bindingPaneModel = {
            runtimeId: this.viewData.runtimeId,
            objectType: this.objectType,
            absoluteName: this.assemblyName,
            wizardOriginalInfo: null
         };

         if(data.viewData.tableModel) {
            this.editedByWizard = data.viewData.tableModel.editedByWizard;
            this.bindingPaneParams = {
               runtimeId: data.viewData.runtimeId,
               assemblyName: data.viewData.tableModel.absoluteName,
               objectType: data.viewData.tableModel.objectType,
               variableValues: data.viewData.variableValues,
               linkUri: data.viewData.linkUri
            };

            this.isCube = data.viewData.tableModel.cubeType != null &&
               data.viewData.tableModel.cubeType.trim().length > 0;
         }
         else if(data.viewData.chartModel) {
            this.editedByWizard = data.viewData.chartModel.editedByWizard;
            this.bindingPaneParams = {
               runtimeId: data.viewData.runtimeId,
               assemblyName: data.viewData.chartModel.absoluteName,
               objectType: data.viewData.chartModel.objectType,
               variableValues: data.viewData.variableValues,
               linkUri: data.viewData.linkUri
            };

            this.isCube = data.viewData.chartModel.cubeType != null &&
               data.viewData.chartModel.cubeType.trim().length > 0;
         }
      });

      this.viewsheetClient.connect();
   }

   ngOnDestroy(): void {
      if(this.routeSubscription) {
         this.routeSubscription.unsubscribe();
      }
   }

   get assemblyName(): string {
      return this.viewData.tableModel ?
         this.viewData.tableModel.absoluteName : this.viewData.chartModel.absoluteName;
   }

   get objectType(): string {
      return this.viewData.tableModel ?
         this.viewData.tableModel.objectType : this.viewData.chartModel.objectType;
   }

   get isEmbedded(): boolean {
      return !!this.assemblyName && this.assemblyName.indexOf(".") != -1;
   }

   get wizardChart(): boolean {
      return !this.isMetadata && !this.isEmbedded && this.editedByWizard;
   }

   get displayChartWizard(): boolean {
      if(this.editorMode == EditorMode.DEFAULT) {
         return this.wizardChart;
      }

      return this.editorMode == EditorMode.CHART_WIZARD;
   }

   get wizardModel(): VsWizardModel {
      if(!this._wizardModel) {
         this._wizardModel = {
            runtimeId: this.viewData.runtimeId,
            linkUri: this.viewData.linkUri,
            objectModel: this.viewData.chartModel || this.viewData.tableModel,
            viewer: true,
            oinfo: {
               runtimeId: this.viewData.runtimeId,
               editMode: VsWizardEditModes.VIEWSHEET_PANE,
               objectType: this.objectType,
               absoluteName: this.assemblyName
            }
         };
      }

      return this._wizardModel;
   }

   set wizardModel(model: VsWizardModel) {
      this._wizardModel = model;
   }

   openWizardPane(evt: VsWizardModel): void {
      if(!!evt) {
         this.wizardModel = evt;
         this.editorMode = EditorMode.CHART_WIZARD;
      }
   }

   closeWizardPane(evt?: CloseWizardModel): void {
      const fromWizard = this.bindingPaneModel &&
         this.bindingPaneModel.absoluteName.startsWith(VSWizardConstants.TEMP_ASSEMBLY);

      // wizard cancel will destroy the temp assembly. so if this is editing a temp assembly,
      // don't try to open the full editor.
      if(fromWizard && !evt.save) {
         this.closeEditor();
      }
      // full editor
      else if(evt && evt.model && evt.model.editMode == VsWizardEditModes.FULL_EDITOR) {
         this.goToFullEditor(evt);
      }
      // cancel
      else if(!evt || !evt.save) {
         if(evt.model.oinfo.editMode == VsWizardEditModes.VIEWSHEET_PANE) {
            this.closeEditor();
         }
         else {
            let oinfo = evt.model.oinfo;
            this.openBindingPane(evt.model.runtimeId, oinfo.objectType,
               oinfo.absoluteName, oinfo);
         }
      }
      else {
         this.closeEditor();
      }
   }

   goToFullEditor(evt?: CloseWizardModel): void {
      this.openBindingPane(evt.model.runtimeId, evt.model.objectModel.objectType,
         evt.model.objectModel.absoluteName, evt.model.oinfo);
   }

   openBindingPane(runtimeId: string, objectType: string,
                   assemblyName: string, oinfo: WizardOriginalInfo): void {

      this.bindingPaneModel = {
         runtimeId: runtimeId,
         objectType: objectType,
         absoluteName: assemblyName,
         wizardOriginalInfo: oinfo
      };

      this.editorMode = EditorMode.BINDING_EDITOR;
   }

   closeEditor(): void {
      let commands: any[];
      let queryParams: Params = {
         fullScreen: this.viewData.fullScreen,
         fullscreenId: this.viewData.runtimeId,
         runtimeId: this.viewData.runtimeId
      };
      queryParams = this.hideNavService.appendParameter(queryParams);

      const extras: NavigationExtras = {
         skipLocationChange: true,
         queryParams: queryParams
      };

      if(this.viewData.portal) {
         const tab: string = this.viewData.dashboard ? "dashboard" : "report";
         commands = [`/portal/tab/${tab}/vs/view/${this.viewData.assetId}`];
      }
      else {
         commands = ["/viewer/view/", {assetId: this.viewData.assetId}];
      }

      this.router.navigate(commands, extras)
         .catch((error) => {
            console.error("Failed to close editor: ", error);
         });
   }
}

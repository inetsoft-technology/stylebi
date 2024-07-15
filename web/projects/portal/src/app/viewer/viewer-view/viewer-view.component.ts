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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AfterViewChecked,
   ChangeDetectorRef,
   Component,
   ElementRef,
   OnDestroy,
   OnInit,
   Optional,
   QueryList,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { ActivatedRoute, NavigationExtras, Params, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { from, Observable, of, Subscription } from "rxjs";
import { CanComponentDeactivate } from "../../../../../shared/util/guard/can-component-deactivate";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { HideNavService } from "../../portal/services/hide-nav.service";
import { SetPrincipalCommand } from "../../vsobjects/command/set-principal-command";
import { ComposerToken, ContextProvider, ViewerContextProviderFactory } from "../../vsobjects/context-provider.service";
import { BaseTableModel } from "../../vsobjects/model/base-table-model";
import { VSChartModel } from "../../vsobjects/model/vs-chart-model";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { ViewerAppComponent } from "../../vsobjects/viewer-app.component";
import { PageTabService, TabInfoModel } from "../services/page-tab.service";
import { ViewDataService } from "../services/view-data.service";
import { ViewData } from "../view-data";
import { Tool } from "../../../../../shared/util/tool";
import { map, mergeMap } from "rxjs/operators";
import { ModelService } from "../../widget/services/model.service";

@Component({
   selector: "v-viewer-view",
   templateUrl: "viewer-view.component.html",
   styleUrls: ["viewer-view.component.scss"],
   providers: [{
      provide: ContextProvider,
      useFactory: ViewerContextProviderFactory,
      deps: [[new Optional(), ComposerToken]]
   }]
})
export class ViewerViewComponent implements OnInit, OnDestroy, CanComponentDeactivate, AfterViewChecked {
   @ViewChildren("viewerApp") viewerApps: QueryList<ViewerAppComponent>;
   @ViewChild("pageTabBar") pageTabBar: ElementRef;
   runtimeId: string;
   principal: string;
   securityEnabled: boolean;
   toolbarPermissions: string[];
   visible = true;
   fullScreen = false;
   dashboardName: string = null;
   inPortal = false;
   inDashboard = false;
   fullScreenId: string;
   tabBarHeight: number = 0;
   public modified: boolean = false;
   private subscriptions: Subscription = new Subscription();

   constructor(private route: ActivatedRoute,
               private router: Router,
               private viewDataService: ViewDataService,
               private modelService: ModelService,
               private modalService: NgbModal,
               private hideNavService: HideNavService,
               private http: HttpClient,
               private pageTabService: PageTabService,
               private changeRef: ChangeDetectorRef)
   {
   }

   public ngOnInit(): void {
      this.subscriptions.add(this.route.data.subscribe((data: {
         viewData: ViewData
         principalCommand: SetPrincipalCommand
      }) => {
         this.pageTabService.clearTabs();
         const tab: TabInfoModel = {
            id: data.viewData.assetId,
            label: this.pageTabService.getVSTabLabel(data.viewData.assetId),
            tooltip: this.pageTabService.getVSTabLabel(data.viewData.assetId),
            isFocused: true,
            runtimeId: data.viewData.runtimeId
         };

         this.pageTabService.addTab(tab);

         this.queryParameters = this.updateQueryParams(data.viewData.queryParameters);
         // getQueryParameters in resolver gets the previous url information
         // call it here to get the current page parameters
         this.runtimeId = data.viewData.runtimeId;
         this.principal = data.principalCommand.principal;
         this.securityEnabled = data.principalCommand.securityEnabled;
         this.toolbarPermissions = data.viewData.toolbarPermissions;
         this.inPortal = data.viewData.portal;
         this.inDashboard = data.viewData.dashboard;
         this.fullScreen = data.viewData.fullScreen;
         this.dashboardName = data.viewData.dashboardName;
         this.fullScreenId = data.viewData.fullScreenId;
         this.modified = false;
      }));

      this.subscriptions.add(this.pageTabService.onRefreshPage.subscribe((tab: TabInfoModel) => {
         this.runtimeId = tab.runtimeId;
      }));
   }

   /**
    * Maintain the previous parameters and overwrite with new instead of clearing
    * so when we follow a hyperlink and go back we keep our variable values
    */
   private updateQueryParams(data: Map<string, string[]>) {
      const queryParams = new Map<string, string[]>();

      if(data != null) {
         // hyperlink parameter
         if(data.has("previousURL")) {
            data.delete("runtimeId");
            this.queryParameters != null &&
               this.queryParameters.forEach((v, k) => queryParams.set(k, v));
         }

         data.forEach((v, k) => queryParams.set(k, v));
      }

      GuiTool.getQueryParameters().forEach((v, k) => queryParams.set(k, v));
      return queryParams;
   }

   public ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
      }
   }

   ngAfterViewChecked() {
      // get subpixel height to avoid a 1px gap between the tab bar and the view
      const h = this.tabs.length && this.pageTabBar
         ? this.pageTabBar.nativeElement.getBoundingClientRect().height : 0;

      if(h != this.tabBarHeight) {
         this.tabBarHeight = h;
         this.changeRef.detectChanges();
      }
   }

   get tabs(): TabInfoModel[] {
      return this.pageTabService.tabs;
   }

   public get currentTab(): TabInfoModel {
      return this.pageTabService.currentTab;
   }

   private get assetId(): string {
      return this.currentTab != null ? this.currentTab.id : null;
   }

   public closeCurrentTab(): void {
      this.pageTabService.closeTab(this.currentTab);
   }

   get queryParameters(): Map<string, string[]> {
      return this.currentTab.queryParameters;
   }

   set queryParameters(queryParameters: Map<string, string[]>) {
      this.currentTab.queryParameters = queryParameters;
   }

   public onEditTable(evt: any): void {
      if(!evt) {
         return;
      }

      let model: BaseTableModel = evt.model;
      const viewerApp = this.getActiveViewerApp();
      this.viewDataService.data.assetId = this.assetId;
      this.viewDataService.data.tableModel = model;
      this.viewDataService.data.chartModel = null;
      this.viewDataService.data.variableValues =
         VSUtil.getVariableList(viewerApp.vsObjects, model.absoluteName);
      this.viewDataService.data.linkUri = viewerApp.vsInfo.linkUri;
      this.viewDataService.data.runtimeId = viewerApp.runtimeId;
      this.viewDataService.data.toolbarPermissions = viewerApp.toolbarPermissions;

      this.openEditor("Failed to navigate to table editor: ", evt.isMetadata);
   }

   public onEditChart(evt: any): void {
      if(!evt) {
         return;
      }

      let model: VSChartModel = evt.model;

      const viewerApp = this.getActiveViewerApp();
      this.viewDataService.data.assetId = this.assetId;
      this.viewDataService.data.chartModel = model;
      this.viewDataService.data.tableModel = null;
      this.viewDataService.data.variableValues =
         VSUtil.getVariableList(viewerApp.vsObjects, model.absoluteName, true);
      this.viewDataService.data.linkUri = viewerApp.vsInfo.linkUri;
      this.viewDataService.data.runtimeId = viewerApp.runtimeId;
      this.viewDataService.data.toolbarPermissions = viewerApp.toolbarPermissions;

      this.openEditor("Failed to navigate to chart editor: ", evt.isMetadata);
   }

   public onViewsheetClosed(): void {
      if(this.inPortal) {
         this.tabs.splice(0, this.tabs.length);
      }
   }

   public canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      const viewerApp = this.getActiveViewerApp();

      if(!!!viewerApp) {
         return true;
      }

      const params = new HttpParams().set("runtimeId", Tool.byteEncode(viewerApp.runtimeId));

      return this.modelService.getModel("../api/vs/checkFormTables", params)
         .pipe(
            mergeMap((showConfirm) => {
               if(showConfirm) {
                  const msg: string = "_#(js:common.warnUnsavedChanges)";
                  return from(
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg)
                  ).pipe(map((result) => result === "ok"));
               }
               else if(this.modified) {
                  return from(ComponentTool.showAnnotationChangedDialog(this.modalService));
               }
               else {
                  return of(true);
               }
            })
         );
   }

   private openEditor(errorMsg: string, isMetadata: boolean): void {
      let queryParams: Params = {
         fullScreen: this.fullScreen
      };
      queryParams = this.hideNavService.appendParameter(queryParams);

      const navigationExtras: NavigationExtras = {
         queryParams: queryParams,
         skipLocationChange: true
      };

      const params = this.dashboardName ?
         {assetId: this.assetId, dashboardName: this.dashboardName} :
         {assetId: this.assetId, isMetadata: isMetadata};

      this.router.navigate(["/viewer/edit", params], navigationExtras)
         .catch((error: any) => {
            console.error(errorMsg, error);
         });
   }

   openViewsheet(runtimeId: string): void {
      const hideNavParam: Params = this.hideNavService.appendParameter({});
      const target: string = this.inPortal ? "/portal/tab/report/vs" : "/viewer";
      this.router.navigate([target + "/view/" + runtimeId], hideNavParam);
   }

   isActiveTab(tab: TabInfoModel): boolean {
      return this.visible && tab != null && tab == this.currentTab;
   }

   private getActiveViewerApp(): ViewerAppComponent | undefined {
      return this.viewerApps.find(app => app.active);
   }

   isIframe(): boolean {
      return GuiTool.isIFrame();
   }
}

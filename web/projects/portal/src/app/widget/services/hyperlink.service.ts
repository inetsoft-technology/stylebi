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
import { AssetEntry, createAssetEntry } from "../../../../../shared/data/asset-entry";
import { AppInfoService } from "../../../../../shared/util/app-info.service";
import { CommonKVModel } from "../../common/data/common-kv-model";
import { DropdownOptions } from "../fixed-dropdown/dropdown-options";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { ActionsContextmenuComponent } from "../fixed-dropdown/actions-contextmenu.component";
import { Injectable, NgZone, OnDestroy } from "@angular/core";
import { HyperlinkModel, ParameterValueModel, LinkType } from "../../common/data/hyperlink-model";
import { AssemblyAction } from "../../common/action/assembly-action";
import { SortInfo } from "../../vsobjects/objects/table/sort-info";
import { Observable, Subscription, Subject } from "rxjs";
import { AutoDrilllEvent } from "../../vsobjects/event/auto-drill-event";
import { ModelService } from "./model.service";
import { ComponentTool } from "../../common/util/component-tool";
import { VSAutoDrillDialogModel } from "../../vsobjects/model/vs-auto-drill-dialog-model";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AutoDrillDialog } from "../dialog/auto-drill-dialog/auto-drill-dialog.component";
import { NavigationEnd, Router } from "@angular/router";
import { filter, map } from "rxjs/operators";
import { PreviousSnapshot, PreviousSnapshotType } from "../hyperlink/previous-snapshot";
import { ViewDataService } from "../../viewer/services/view-data.service";
import { ViewConstants } from "../../viewer/view-constants";

const SHOW_DRILL = "../api/get-drill-model";

@Injectable()
export abstract class HyperlinkService implements OnDestroy {
   protected _drillModel: VSAutoDrillDialogModel;
   protected subscriptions = new Subscription();
   protected inPortal = false;
   protected inDashboard = false;
   protected inComposer = false;
   protected inViewer = false;
   public inEmbed = false;
   public singleClick = false;
   openLinkSubject: Subject<any> = new Subject<any>();
   backToPreviousLinkSubject: Subject<any> = new Subject<any>();
   private orgInfo: CommonKVModel<string, string> = null;

   constructor(protected zone: NgZone, protected modelService: ModelService,
                protected modalService: NgbModal, protected router: Router,
                protected viewDataService: ViewDataService,
               protected appInfoService: AppInfoService)
   {
      this.inPortal = !!router.url && router.url.startsWith("/portal/");
      this.inComposer = !!router.url && router.url.split("?")[0].endsWith("/composer");
      this.inDashboard = !!router.url && router.url.startsWith("/portal/tab/dashboard/");
      this.inViewer = !!router.url && router.url.startsWith("/viewer/");
      this.subscriptions.add(router.events.pipe(
         filter((event) => (event instanceof NavigationEnd)),
         map((event: NavigationEnd) => event.urlAfterRedirects)
      ).subscribe(
         (url) => {
            this.inPortal = !!url && url.startsWith("/portal/");
            this.inDashboard = !!url && url.startsWith("/portal/tab/dashboard/");
         }
      ));

      this.subscriptions.add(this.appInfoService.getCurrentOrgInfo().subscribe((orgInfo) => {
         this.orgInfo = orgInfo;
      }));
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
      this.openLinkSubject.unsubscribe();
      this.openLinkSubject = null;
   }

   public createActionsContextmenu(dropdownService: FixedDropdownService,
                                   actions: AssemblyActionGroup[], event: any,
                                   xPos: number, yPos: number,
                                   isForceTab: boolean = false): void
   {
      if(this.singleClick && actions.length == 1 && actions[0].actions.length == 1) {
         actions[0].actions[0].action(event);
         return;
      }

      let options: DropdownOptions = {
         position: {x: xPos, y: yPos},
         contextmenu: true
      };

      this.zone.run(() => {
         let dropdownRef = dropdownService.open(ActionsContextmenuComponent, options);
         let contextmenu: ActionsContextmenuComponent = dropdownRef.componentInstance;
         contextmenu.sourceEvent = event;
         contextmenu.forceTab = isForceTab;
         contextmenu.actions = actions;
      });
   }

   public createHyperlinkActions(hyperlinks: HyperlinkModel[], linkUri?: string,
                                 runtimeId?: string, params?: ParameterValueModel[]):
                                 AssemblyActionGroup
   {
      const linkActions: AssemblyAction[] = hyperlinks.map((link) => {
         return {
            id: () => "hyperlink-cell",
            label: () => link.label,
            icon: () => "fa-link",
            visible: () => true,
            enabled: () => true,
            action: () => this.clickLink(link, runtimeId, linkUri, params)
         };
      });

      return new AssemblyActionGroup(linkActions);
   }

   getTargetName(link: HyperlinkModel) {
      if(!link.targetFrame || link.targetFrame === "SELF" || link.targetFrame === "_self") {
         return "_self";
      }

      return link.targetFrame;
   }

   getURL(url: string) {
      if(!!url && url.toLowerCase().search(/http:/) < 0 &&
         url.toLowerCase().search(/https:/) < 0 && url.search(/\./) > 0 &&
         url.search(/\//) != 0 && url.toLowerCase().search(/mailto:/) < 0)
      {
         url = "http://" + url;
      }

      return url;
   }

   public clickLink(link: HyperlinkModel, runtimeId?: string,
                    linkUri?: string, params?: ParameterValueModel[]): void
   {
      if(link.linkType == LinkType.VIEWSHEET_LINK) {
         let entry: AssetEntry = createAssetEntry(link.link);

         if(this.orgInfo.key != entry.organization && entry.user != null) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:deny.access.private.resources.of.default.org)");
            return;
         }
      }

      if(link.query != null || link.wsIdentifier != null) {
         this.loadShowDrillModel(link, runtimeId, linkUri);
      }
      else {
         this.showURL(link, linkUri, runtimeId, params);
      }
   }

   loadShowDrillModel(link: HyperlinkModel, runtimeId?: string, linkUri?: string): void {
      for(let i = 0; i < link.parameterValues.length; i++) {
         link.parameterValues[i].name = decodeURIComponent(link.parameterValues[i].name);
      }

      this.getDrillDialogModel(link).subscribe((data: any) => {
         this.zone.run(() => {
            if(data && data.body) {
               this.drillModel = data.body;
               this.showDrill(link, linkUri, runtimeId);
            }
            else {
               this.showURL(link, linkUri, runtimeId);
            }
         });
      });
   }

   showDrill(link: HyperlinkModel, linkUri?: string, runtimeId?: string): void {
      if(this.drills == null) {
         return;
      }

      for(let i = 0; i < this.drills.length; i++) {
         if(this.drills[i] == null || this.drills[i].length == 0) {
            this.showURL(link, linkUri, runtimeId);
            return;
         }
      }

      if(this.drills.length <= 2) {
         let fillParam: ParameterValueModel[] = this.fillParameters(link,
            this.drillModel, this.drills[1]);
         this.showDrillURL(link, fillParam, linkUri, runtimeId);
         return;
      }

      const dialog = ComponentTool.showDialog(this.modalService, AutoDrillDialog,
         (result: any) => {
            const fillParam: ParameterValueModel[] =
               this.fillParameters(link, this.drillModel, result);
            this.showDrillURL(link, fillParam, linkUri, runtimeId);
         });

      dialog.model = this.drillModel;
      dialog.onSort.subscribe((result: any) => {
         this.getDrillDialogModel(link, result).subscribe((data: any) => {
            if(data && data.body) {
               this.drillModel = data.body;
               dialog.model = this.drillModel;
            }
         });
      });
   }

   fillParameters(link: HyperlinkModel, model: VSAutoDrillDialogModel, row: string[]):
      ParameterValueModel[]
   {
      let fixedParams: ParameterValueModel[] = [];
      let linkType: number = link.linkType;

      for(let i = 0; i < link.parameterValues.length; i++) {
         let pname: string = link.parameterValues[i].name;
         let pvalue: string = link.parameterValues[i].value;
         let idx = pname.indexOf("Param_");

         if(linkType == 1 && idx < 0) {
            fixedParams.push(new ParameterValueModel(pname, typeof pvalue, pvalue));
            continue;
         }

         pname = (idx == 0) ? pname.substring("Param_".length) : pname;
         let cidx: number = this.getColumnIndex(model, pvalue);

         if(cidx < 0) {
            fixedParams.push(new ParameterValueModel(pname, typeof pvalue, pvalue));
            continue;
         }

         let rowValue = row != null ? row[cidx] : null;
         fixedParams.push(new ParameterValueModel(pname, typeof rowValue, rowValue));
      }

      return fixedParams;
   }

   showDrillURL(link: HyperlinkModel, params: ParameterValueModel[],
                linkUri: string, runtimeId: string): void
   {
      let paramValues: ParameterValueModel[] = [];

      for(let i = 0; params != null && i < params.length; i++) {
         let pname = params[i].name;
         let pvalue = params[i].value;

         if(pvalue != null && pvalue.indexOf("^DATE^") == 0) {
            pvalue = pvalue.substring(6);
         }

         if(pname.indexOf("sub_query_param_") >= 0 || pname == "req") {
            continue;
         }

         paramValues.push(params[i]);
      }

      this.showURL(link, linkUri, runtimeId, paramValues);
   }

   getColumnIndex(model: VSAutoDrillDialogModel, value: string): number {
      for(let i = 0; i < model.drills.length; i++) {
         let colName = model.drills[0][i];

         if(colName == value) {
            return i;
         }
      }

      return -1;
   }

   showHyperlinks(event: MouseEvent, hyperlinks: HyperlinkModel[],
                  dropdownService: FixedDropdownService, runtimeId?: string,
                  linkUri?: string, isForceTab: boolean = false,
                  params?: ParameterValueModel[]): void
   {
      // alt used to select shape instead of opening hyperlink
      if(!hyperlinks || hyperlinks.length == 0 || event && event.ctrlKey) {
         return;
      }

      let actions: AssemblyActionGroup =
         this.createHyperlinkActions(hyperlinks, linkUri, runtimeId, params);

      // if there is only one hyperlink, jump to the link directly without showing a menu first.
      // don't go directly to hyperlink for chart to allow for brushing/show-details/...
      if(actions.actions.length == 1) {
         actions.actions[0].action(event);
         return;
      }

      let xPos = event.clientX;
      let yPos = event.clientY;
      this.createActionsContextmenu(dropdownService, [actions], event, xPos, yPos, isForceTab);
      event.preventDefault();
   }

   protected getDrillDialogModel(link: HyperlinkModel, sortInfo?: SortInfo): Observable<any> {
      return this.modelService.sendModel(SHOW_DRILL, new AutoDrilllEvent(link, sortInfo));
   }

   protected createViewsheetURL(path: string): string {
      // The content of queryParams should not be carried when creating the previous url.
      // This url part will be used as the assetID on back.
      if(!!this.viewDataService && !!this.viewDataService.data &&
         !!this.viewDataService.data.assetId)
      {
         const vsPrefix = "/view/";
         const vsIndex = path.indexOf(vsPrefix) + vsPrefix.length;
         return path.substring(0, vsIndex) + this.viewDataService.data.assetId;
      }

      return path;
   }

   protected createPreviousURL(path: string): string {
      if(!path) {
         return path;
      }

      if(path.indexOf("/vs/view") >= 0 || path.indexOf("/viewer/view") >= 0) {
         return this.createViewsheetURL(path);
      }

      if(path.indexOf("?") >= 0) {
         path = path.substring(0, path.indexOf("?"));
      }

      if(path.indexOf("/portal/tab/dashboard/vs/dashboard") >= 0) {
         return path;
      }

      return "";
   }

   public backToPreviousSheet(router: Router, snapshotStack: string[], runtimeid: string,
                              drillFrom: string): void
   {
      if(!router || !snapshotStack || snapshotStack.length < 1) {
         throw new Error("Unsupported operation...");
      }

      const extras = {
         queryParams: {
         },
         skipLocationChange: true
      };
      const snapshot: PreviousSnapshot = this.getSnapshotStack(snapshotStack);

      if(snapshot.type == PreviousSnapshotType.VS) {
         if(snapshot.id) {
            extras.queryParams["runtimeId"] = snapshot.id;
         }
      }
      else if(drillFrom) {
         extras.queryParams["runtimeId"] = drillFrom;
      }

      if(snapshotStack.length > 0) {
         extras.queryParams[ViewConstants.PRE_SNAPSHOT_PARAM_NAME] = snapshotStack;
      }

      // mark the target link is a base link of the current link.
      extras.queryParams["baseLink"] = true;

      if(!!runtimeid) {
         extras.queryParams["backfrom"] = runtimeid;
      }

      this.backToPreviousLinkSubject.next(runtimeid);
      router.navigate([snapshot.url], extras);
   }

   getSnapshotStack(snapshotStack: string[]): PreviousSnapshot {
      if(!snapshotStack || snapshotStack.length == 0) {
         return null;
      }

      let lastStack = <PreviousSnapshot> JSON.parse(snapshotStack.pop());

      if(lastStack.type == PreviousSnapshotType.VS && snapshotStack.length > 0 &&
         JSON.parse(snapshotStack[snapshotStack.length - 1]).type == PreviousSnapshotType.VS)
      {
         lastStack = this.getSnapshotStack(snapshotStack);
      }

      return lastStack;
   }

   set drillModel(model: VSAutoDrillDialogModel) {
      this._drillModel = model;
   }

   get drillModel(): VSAutoDrillDialogModel {
      return this._drillModel;
   }

   get drills(): string[][] {
      return this.drillModel != null ? this.drillModel.drills : null;
   }

   public abstract showURL(link: HyperlinkModel, linkUri?: string, runtimeId?: string,
                           paramValues?: ParameterValueModel[]): void;
}

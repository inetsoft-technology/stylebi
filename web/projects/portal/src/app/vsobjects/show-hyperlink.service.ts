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
import { Injectable, NgZone, OnDestroy, Optional } from "@angular/core";
import { convertToParamMap, NavigationExtras, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../shared/feature-flags/feature-flags.service";
import { AppInfoService } from "../../../../shared/util/app-info.service";
import {
   HyperlinkModel,
   HyperlinkViewModel,
   LinkType,
   ParameterValueModel
} from "../common/data/hyperlink-model";
import { GuiTool } from "../common/util/gui-tool";
import { PageTabService, TabInfoModel } from "../viewer/services/page-tab.service";
import { ViewDataService } from "../viewer/services/view-data.service";
import { PreviousSnapshot, PreviousSnapshotType } from "../widget/hyperlink/previous-snapshot";
import { HyperlinkService } from "../widget/services/hyperlink.service";
import { ModelService } from "../widget/services/model.service";
import { ViewConstants } from "../viewer/view-constants";

export interface LinkTabModel {
   id: string;
   queryParameters: Map<string, string[]>;
}

const BROWSER_URL_MAX_LENGTH_LIMIT: number = 8000;

@Injectable()
export class ShowHyperlinkService extends HyperlinkService implements OnDestroy {
   drillDownSubject = new Subject<any>();
   showLinkSheetSubject = new Subject<LinkTabModel>();
   portalRepositoryPermission: boolean = true;
   private drillId: number = 0;

   constructor(zone: NgZone, modelService: ModelService, modalService: NgbModal,
               @Optional() private pageTabService: PageTabService,
               viewDataService: ViewDataService, router: Router,
               private featureFlagsService: FeatureFlagsService,
               appInfoService: AppInfoService)
   {
      super(zone, modelService, modalService, router, viewDataService, appInfoService);
   }

   showURL(link: HyperlinkModel, linkUri: string, runtimeId: string,
           paramValues?: ParameterValueModel[]): void
   {
      const _model = HyperlinkViewModel.fromHyperlinkModel(
         link, linkUri, paramValues, runtimeId, this.inPortal && this.portalRepositoryPermission);

      if(_model == null) {
         return;
      }

      const extras: NavigationExtras = {
         queryParams: {
            bookmarkName: link.bookmarkName,
            bookmarkUser: link.bookmarkUser
         },
         skipLocationChange: true
      };

      if(link.sendSelectionParameters) {
         extras.queryParams.hyperlinkSourceId = runtimeId;
      }

      const params = new Map<string, string[]>();

      for(let param in _model.parameters) {
         if(!!param && Array.isArray(_model.parameters[param])) {
            params.set(param, _model.parameters[param]);
         }
         else {
            params.set(param, [_model.parameters[param]]);
         }
      }

      if(paramValues) {
         paramValues.forEach((model) => {
            extras.queryParams[model.name] = model.value;

            if(!params.has(model.name)) {
               params.set(model.name, [model.value]);
            }
         });
      }
      else if(link.parameterValues) {
         link.parameterValues.forEach((model) => {
            if(extras.queryParams[model.name] == null) {
               extras.queryParams[model.name] = [model.value];
            }
            else if(Array.isArray(extras.queryParams[model.name])) {
               extras.queryParams[model.name].push(model.value);
            }
            else {
               extras.queryParams[model.name] = model.value;
            }

            if(!params.has(model.name)) {
               params.set(model.name, [model.value]);
            }

            extras.queryParams[model.name + ".__type__"] = model.type;
            params.set(model.name + ".__type__", [model.type]);
         });
      }

      if(!this.inComposer && !this.inEmbed) {
         const snapshot: PreviousSnapshot = {
            type: PreviousSnapshotType.VS,
            id: runtimeId,
            url: this.createPreviousURL(this.router.url)
         };

         if(!!this.viewDataService.data) {
            if(!this.viewDataService.data.previousSnapshots) {
               this.viewDataService.data.previousSnapshots = [];
            }

            this.viewDataService.data.previousSnapshots =
               this.viewDataService.data.previousSnapshots.concat(JSON.stringify(snapshot));
         }

         extras.queryParams[ViewConstants.PRE_SNAPSHOT_PARAM_NAME] =
            _model.parameters[ViewConstants.PRE_SNAPSHOT_PARAM_NAME] =
               !!this.viewDataService.data ?
                  this.viewDataService.data.previousSnapshots : [JSON.stringify(snapshot)];
      }

      if(link.linkType === LinkType.MESSAGE_LINK) {
         if(!!_model.url) {
            window.parent.postMessage(JSON.parse(_model.url), "*");
         }
      }
      else {
         let str: string = this.getURL(_model.url);
         let target: string = this.getTargetName(link);

         if(this.inComposer && target === "_self" && link.linkType === LinkType.VIEWSHEET_LINK) {
            this.showLinkSheetSubject.next({
               id: link.link,
               queryParameters: params
            });

            return;
         }

         // never replace composer window with drilldown
         if(this.inComposer || this.inEmbed) {
            target = "_blank";
         }

         if(runtimeId) {
            params.set("drillfrom", [runtimeId]);
         }

         if(target == "_self" &&
            // if in portal and no repository permissing, open in a separate tab
            // without portal otherwise there will be an error
            (!this.inPortal || this.portalRepositoryPermission))
         {
            if(link.linkType === LinkType.WEB_LINK) {
               window.open(str, "_self");
            }
            else {
               if(!!runtimeId) {
                  this.pageTabService.currentTab.runtimeId = runtimeId;
               }

               const tab: TabInfoModel = {
                  id: link.link,
                  label: link.label,
                  tooltip: this.pageTabService.getVSTabLabel(link.link),
                  parentTab: this.pageTabService.currentTab,
                  queryParameters: params
               };

               this.pageTabService.addTab(tab);
               this.pageTabService.refreshVSPage(tab);
            }
         }
         else {
            // if drill into vs in a dashboard, hide tree so it looks more like a dashboard
            if(this.inPortal && this.inDashboard) {
               if(str.indexOf("?") < 0) {
                  str += "?";
               }

               if(!str.endsWith("?")) {
                  str += "&";
               }

               str += "collapseTree=true";
            }

            if(link.linkType === LinkType.WEB_LINK) {
               window.open(str, target);
            }
            else {
               this.openWindow(str, target, _model.parameters);
            }
         }
      }
   }

   private openWindow(str: string, target: string, params: any) {
      const q = str.indexOf("?");
      str = q > 0 ? str.substring(0, q) : str;

      // direct export, don't open report/vs viewer
      if(params["outtype"]) {
         if(str.indexOf("app/portal/tab/report/vs/view") > 0) {
            str = str.replace("app/portal/tab/report/vs/view", "export/viewsheet");
         }
         else if(str.indexOf("app/portal/tab/report/vs/view") > 0) {
            str = str.replace("app/viewer/view", "export/viewsheet");
         }

         str += "&outtype=" + params["outtype"].toLowerCase();
         this.openWindowWithPost(str, target, params);
         return;
      }

      const drillId = "drill" + ++this.drillId;
      str = str + "?drillId=" + drillId;
      let parameterStr = "";

      for(let i in params) {
         if(i == ViewConstants.PRE_SNAPSHOT_PARAM_NAME) {
            continue;
         }

         if(params.hasOwnProperty(i)) {
            if(Array.isArray(params[i])) {
               for(let value of params[i]) {
                  parameterStr = parameterStr + "&" + i + "=" + encodeURIComponent(value);
               }
            }
            else {
               parameterStr = parameterStr + "&" + i + "=" + encodeURIComponent(params[i]);
            }
         }
      }

      if(str.length + parameterStr.length < BROWSER_URL_MAX_LENGTH_LIMIT) {
         str += parameterStr;
      }

      const storage = window.sessionStorage;
      const drillParams = storage.setItem("__drillParameters__" + drillId, JSON.stringify(params));
      window.open(str, target);
   }

   private openWindowWithPost(url: string, target: string, params: any) {
      // force a unique name so the target in format would work
      if(target == "_blank") {
         target = "newwin" + (new Date()).getTime();
      }

      const form = document.createElement("form");
      form.setAttribute("method", "post");
      form.setAttribute("action", url);
      form.setAttribute("target", target);

      for(let i in params) {
         if(params.hasOwnProperty(i)) {
            const input = document.createElement("input");
            input.type = "hidden";
            input.name = i;

            if(Array.isArray(params[i])) {
               // encode array according to Tool.decodeParameters
               input.value = "^[" + (<any[]> params[i]).join(",") + "]^";
            }
            else {
               input.value = params[i];
            }

            form.appendChild(input);
         }
      }

      GuiTool.preventAutoScroll();
      document.body.appendChild(form);
      window.open("", target);
      form.submit();
      document.body.removeChild(form);
   }

   // fetch router query parameter from queryParams or windows param map
   public static getQueryParams(params: ParamMap): ParamMap {
      const drillId = params.get("drillId");
      const winparams = (<any> window)["_query_params_"];

      if(drillId && winparams) {
         const params2 = Object.assign({}, winparams[drillId]);

         if(params2) {
            for(let k of params.keys) {
               params2[k] = params.get(k);
            }

            return convertToParamMap(params2);
         }
      }

      return params;
   }
}

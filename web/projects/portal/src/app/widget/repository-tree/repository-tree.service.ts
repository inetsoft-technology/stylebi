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
import { Injectable, OnDestroy } from "@angular/core";
import { convertToParamMap, ParamMap, Params, UrlSegment } from "@angular/router";
import { Observable, Subject } from "rxjs";
import { createAssetEntry } from "../../../../../shared/data/asset-entry";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { ResourceAction } from "../../../../../shared/util/security/resource-permission/resource-action.enum";
import { Tool } from "../../../../../shared/util/tool";
import { TreeNodeModel } from "../tree/tree-node-model";

const GET_PORTAL_TREE_FOLDER = "../api/portal/tree";

export interface NotificationData {
   type: string;
   content: string;
}

@Injectable({
   providedIn: "root"
})
export class RepositoryTreeService implements OnDestroy {
   onNotification = new Subject<NotificationData>();

   constructor(public http: HttpClient) {
   }

   ngOnDestroy(): void {
      if(!!this.onNotification) {
         this.onNotification.unsubscribe();
         this.onNotification = null;
      }
   }

   public notify: (data: NotificationData) => void
      = (data: NotificationData) => this.onNotification.next(data);

   getRootFolder(permission?: ResourceAction, selector?: number,
                 detailType?: string, isFavoritesTree?: boolean,
                 checkDetailType: boolean = false,
                 isReport: boolean = true,
                 isGlobal: boolean = false,
                 showBurstReport: boolean = true): Observable<TreeNodeModel>
   {
      return this.getFolder("/", permission, selector, detailType, isFavoritesTree, null,
         checkDetailType, isReport, isGlobal, false, false, showBurstReport);
   }

   getFolder(path: string, permission?: ResourceAction, selector?: number,
             detailType?: string, isFavoritesTree?: boolean, isArchive?: boolean,
             checkDetailType: boolean = false,
             isReport: boolean = true, isGlobal: boolean = false,
             isPortalData = false, showVS: boolean = false, showBurstReport: boolean = true): Observable<TreeNodeModel>
   {
      let params = new HttpParams()
         .set("path", path)
         .set("checkDetailType", checkDetailType + "")
         .set("isReport", isReport + "")
         .set("showBurstReport", showBurstReport + "");

      if(permission) {
         params = params.set("permission", permission.toString());
      }

      if(selector) {
         params = params.set("selector", String(selector));
      }

      if(detailType) {
         params = params.set("detailType", detailType);
      }

      if(isFavoritesTree) {
         params = params.set("isFavoritesTree", isFavoritesTree + "" );
      }

      if(isGlobal) {
         params = params.set("isGlobal", isGlobal + "" );
      }

      if(isPortalData) {
         params = params.set("isPortalData", isPortalData + "" );
      }

      if(isArchive) {
         params = params.set("isArchive", isArchive + "");
      }

      if(showVS) {
         params = params.set("showVS", showVS + "");
      }

      return this.http.get<TreeNodeModel>(GET_PORTAL_TREE_FOLDER, { params });
   }

   /**
    * given the path in the directory to a entry, get the path where every node
    * along the way is represented by its alias
    * @param {string} path
    * @param {TreeNodeModel} root
    * @returns {string}
    */
   getAliasedPath(path: string, root: TreeNodeModel): string {
      if(!path) {
         return "";
      }

      let segments = path.split("/");
      let aliasedSegments = [];
      let curr = root;

      if(segments.length > 0) {
         for(let sIdx = 0; sIdx < segments.length; sIdx++) {
            let segment = segments[sIdx];

            if(curr.children.length == 0) {

            }
            curr = curr.children.find((child) => {
               return child.data.name === segment;
            });

            if(!curr){
               //implies no match
               return path;
            }

            aliasedSegments.push(!curr.label ? curr.data.path : curr.label);
         }
      }

      return aliasedSegments.join("/");
   }

   getContentSource(entry: RepositoryEntry, params?: ParamMap): string {
      if(entry.type === RepositoryEntryType.VIEWSHEET) {
         return "viewer/view/" +
            Tool.encodeURIComponentExceptSlash(entry.entry.identifier);
      }

      return null;
   }

   getRouteUrlEntry(routeUrl: string): RepositoryEntry {
      if(routeUrl.indexOf("/") == 0) {
         routeUrl = routeUrl.substring(1);
      }

      const qIndex = routeUrl.indexOf("?");
      const params: Params = {};
      let url: string[];

      if(qIndex < 0) {
         url = routeUrl.split("/");
      }
      else {
         url = routeUrl.substring(0, qIndex).split("/");
         const query = routeUrl.substring(qIndex + 1);
         query.split("&").forEach((pair) => {
            const index = pair.indexOf("=");

            if(index < 0) {
               params[pair] = "";
            }
            else {
               const name = pair.substring(index);
               params[name] = pair.substring(index + 1);
            }
         });
      }

      return this.getRouteParamsEntry(
         url.map((s) => decodeURIComponent(s)), convertToParamMap(params));
   }

   public getRouteParamsEntry(url: string[], params: ParamMap): RepositoryEntry {
      let entry: RepositoryEntry = null;

      if(url.length > 1) {
         const type = url[0];
         const name = url[url.length - 1];
         const path = url.slice(1).map(decodeURIComponent).join("/");
         const mode = parseInt(params.get("mode"), 10);

         if(type == "vs" && url.length > 1) {
            const assetId = Tool.getAssetIdFromUrl(url.slice(1));

            if(assetId) {
               const assetEntry = createAssetEntry(assetId);

               if(assetEntry) {
                  const nameOverride = name === assetId ? assetEntry.path : name;

                  entry = {
                     name: nameOverride,
                     path: assetEntry.path,
                     type: RepositoryEntryType.VIEWSHEET,
                     label: name,
                     owner: assetEntry.user,
                     entry: assetEntry,
                     classType: null,
                     htmlType: 0
                  };
               }
            }
         }
      }

      return entry;
   }

   getRouteParamsContentSource(url: UrlSegment[], params: ParamMap): string {
      if(params.has("op")) {
         return Tool.getServletRepositoryPath() + "?" + params.keys
            .map((key) => encodeURIComponent(key) + "=" + encodeURIComponent(params.get(key)))
            .join("&");
      }
      else {
         return this.getContentSource(
            this.getRouteParamsEntry(url.map((s) => s.path), params), params);
      }
   }

   public getCSSIcon(entry: RepositoryEntry, expanded: boolean): string {
      let css: string;

      switch(entry.type) {
      case RepositoryEntryType.VIEWSHEET:
         if(entry.snapshot) {
            css = "snapshot-icon";
         }
         else if(entry.materialized) {
            css = "materialized-viewsheet-icon";
         }
         else {
            css = "viewsheet-icon";
         }

         break;
      case RepositoryEntryType.WORKSHEET:
         if(entry.materialized) {
            css = "materialized-worksheet-icon";
         }
         else {
            css = "worksheet-icon";
         }

         break;
      case RepositoryEntryType.FOLDER:
         if(expanded) {
            css = "folder-open-icon";
         }
         else {
            css = "folder-icon";
         }

         break;
      }

      return css;
   }
}
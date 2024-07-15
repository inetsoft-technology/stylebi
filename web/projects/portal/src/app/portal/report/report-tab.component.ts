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
import { HttpClient, HttpParams } from "@angular/common/http";
import { ChangeDetectorRef, Component, NgZone, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { DomSanitizer } from "@angular/platform-browser";
import { ActivatedRoute, NavigationEnd, NavigationExtras, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { withLatestFrom } from "rxjs/operators";
import { convertToKey } from "../../../../../em/src/app/settings/security/users/identity-id";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { AssetLoadingService } from "../../common/services/asset-loading.service";
import { OpenComposerService } from "../../common/services/open-composer.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { CommandProcessor, ViewsheetClientService } from "../../common/viewsheet-client";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { EditViewsheetEvent } from "../../composer/gui/vs/event/edit-viewsheet-event";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { CurrentUser } from "../current-user";
import { CurrentRouteService } from "../services/current-route.service";
import { HideNavService } from "../services/hide-nav.service";
import { HistoryBarService } from "../services/history-bar.service";
import { CollapseRepositoryTreeService } from "./desktop/collapse-repository-tree.service.component";
import { ReportTabModel } from "./report-tab-model";

const CURRENT_USER_URI: string = "../api/portal/get-current-user";
const MAX_RECENTLY_VIEWED: number = 5;

@Component({
   templateUrl: "report-tab.component.html",
   styleUrls: ["../portal-tab.component.scss", "report-tab.component.scss"],
   providers: [ViewsheetClientService]
})
export class ReportTabComponent extends CommandProcessor implements OnInit, OnDestroy {
   rootNode: TreeNodeModel;
   model: ReportTabModel;
   mobile: boolean;
   recentlyViewed: RepositoryEntry[];
   currentUser: CurrentUser;
   selectedEntry: RepositoryEntry;
   wizardShown = false;
   currentRouteService: CurrentRouteService;
   treePaneCollapsed: boolean = false;
   openedEntrys: RepositoryEntry[] = [];
   @ViewChild("mobileView") mobileView: any;
   @ViewChild("notifications") notifications: NotificationsComponent;

   get childRouteShown(): boolean {
      return !!this.selectedEntry;
   }

   get viewType(): "list" | "desktop" | "mobile" {
      if(this.mobile && !this.model.showRepositoryAsList) {
         return "mobile";
      }
      else if(this.model.showRepositoryAsList) {
         return "list";
      }
      else {
         return "desktop";
      }
   }

   private subscriptions = new Subscription();

   constructor(private repositoryTreeService: RepositoryTreeService,
               private sanitationService: DomSanitizer,
               private route: ActivatedRoute,
               private router: Router,
               private http: HttpClient,
               private modal: NgbModal,
               private hideNavService: HideNavService,
               private changeRef: ChangeDetectorRef,
               private historyBarService: HistoryBarService,
               currentRouteService: CurrentRouteService,
               private viewsheetClient: ViewsheetClientService,
               zone: NgZone, private composerService: OpenComposerService,
               private collapseTreeService: CollapseRepositoryTreeService,
               private assetLoadingService: AssetLoadingService)
   {
      super(viewsheetClient, zone, true);
      this.subscriptions.add(currentRouteService.repositoryUrl.pipe(
         withLatestFrom(currentRouteService.repositoryEntry)
      ).subscribe(
         ([url, entry]) => {
            this.wizardShown = !!url && url.startsWith("/wizard");
            this.selectedEntry = entry;

            if(this.mobileView) {
               this.mobileView.activePane = entry ? "Viewer" : "Repository";
            }

            // clicking on 'Repository' tab generates a null url. at this point the report/vs
            // is no longer displayed and should be closed
            if(url == null || url == "/close") {
               this.openedEntrys = [];
            }
         }
      ));

      // ngOnInit may not be called if component is reused
      this.subscriptions.add(router.events.subscribe(event => {
         if(event instanceof NavigationEnd) {
            this.init();
         }
      }));

      this.subscriptions.add(this.repositoryTreeService.onNotification.subscribe(data => {
         const type = data.type;
         const content = data.content;

         switch(type) {
            case "success":
               this.notifications.success(content);
               break;
            case "info":
               this.notifications.info(content);
               break;
            case "warning":
               this.notifications.warning(content);
               break;
            case "danger":
               this.notifications.danger(content);
               break;
            default:
               this.notifications.warning(content);
         }
      }));

      this.currentRouteService = currentRouteService;
      this.viewsheetClient.connect();
   }

   ngOnInit() {
      this.init();
   }

   private init() {
      this.mobile = GuiTool.isMobileDevice();

      this.subscriptions.add(this.currentRouteService.repositoryUrl.pipe(
         withLatestFrom(this.currentRouteService.repositoryEntry),
         withLatestFrom(this.currentRouteService.currentUrl)
      ).subscribe(
         ([[url, entry], currentUrl]) => {
            //if no entry is being opening, refresh the tree
            if(!url && !entry && (!currentUrl || currentUrl.indexOf("hideWelcomePage=true") < 0)) {
               this.repositoryTreeService.getRootFolder().subscribe(
                  (rootNode) => {
                     this.rootNode = rootNode;
                     this.rootNode.expanded = true;
                  });
            }

            if(currentUrl && currentUrl.indexOf("collapseTree=true") >= 0) {
               this.treePaneCollapsed = true;
            }
         }
      ));

      this.subscriptions.add(this.route.data.subscribe(
         (data) => {
            this.model = data.reportTabModel;
            this.changeRef.detectChanges();

            if(this.model.collapseTree) {
               this.treePaneCollapsed = true;
            }

            if(!this.rootNode) {
               this.repositoryTreeService.getRootFolder().subscribe(
                  (rootNode) => {
                     this.rootNode = rootNode;
                     this.rootNode.expanded = true;
                  });
            }
         }
      ));

      this.http.get<CurrentUser>(CURRENT_USER_URI).subscribe(
         (currentUser) => {
            this.currentUser = currentUser;
            this.recentlyViewed =
               JSON.parse(LocalStorage.getItem("portal_recently_viewed_" +
                  convertToKey(this.currentUser.name))) || [];
         }, (error) => {
            ComponentTool.showMessageDialog(this.modal, "_#(js:Error)",
                                   "_#(js:Connection failed)");
         });
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   getAssemblyName(): string {
      return null;
   }

   private processMessageCommand(command: MessageCommand): void {
      if(command.message && command.type == "INFO") {
         this.notifications.info(command.message);
      }
      else {
         this.processMessageCommand0(command, this.modal, this.viewsheetClient);
      }
   }

   get historyBarEnabled(): boolean {
      return this.historyBarService.isHistoryBarEnabled;
   }

   deletedEntry(entry: RepositoryEntry) {
      if(this.isEntryOpened(entry)) {
         this.router.navigate(["/portal/tab/report"]);
      }
   }

   private isEntryOpened(entry: RepositoryEntry): boolean {
      if(this.openedEntrys.length > 0) {
         for(let oEntry of this.openedEntrys) {
            if(oEntry.path === entry.path) {
               return true;
            }
         }
      }

      return false;
   }

   showEntry(entry: RepositoryEntry) {
      if(!entry) {
         return;
      }

      if(entry.type !== RepositoryEntryType.FOLDER) {
         this.addRecentlyViewed(entry);
         this.openedEntrys = [entry];
         let navigationExtras: NavigationExtras = {};
         let url = "/portal/tab/report/";
         let reloading = false;

         if(entry.type === RepositoryEntryType.VIEWSHEET) {
            url += `vs/view/${entry.entry.identifier}`;
            reloading = !!this.selectedEntry &&
               this.selectedEntry.type === RepositoryEntryType.VIEWSHEET &&
               this.selectedEntry.entry.identifier === entry.entry.identifier;
         }

         navigationExtras.queryParams = this.hideNavService.appendParameter(navigationExtras.queryParams);

         if(reloading) {
            if((entry.type === RepositoryEntryType.VIEWSHEET &&
               this.assetLoadingService.isLoading(entry.entry?.identifier)))
            {
               ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)",
                  "_#(js:repository.tree.confirmReloadEntry)")
                  .then((buttonClicked) => {
                     if(buttonClicked === "ok") {
                        this.reloadUrl(url, navigationExtras);
                     }
                  });
            }
            else {
               this.reloadUrl(url, navigationExtras);
            }
         }
         else {
            this.router.navigate([url], navigationExtras);
         }
      }
   }

   private reloadUrl(url: string, navigationExtras: NavigationExtras) {
      const tempExtras: NavigationExtras = {
         queryParams: {
            hideWelcomePage: "true"
         },
         skipLocationChange: true
      };
      this.router.navigate(["/portal/tab/report"], tempExtras)
         .then((navigated) => {
            // only navigate if passed guard
            if(navigated) {
               this.router.navigate([url], navigationExtras);
            }
         });
   }

   addRecentlyViewed(entry: RepositoryEntry): void {
      for(let i = 0; i < this.recentlyViewed.length; i++) {
         if(entry.path === this.recentlyViewed[i].path) {
            this.recentlyViewed.splice(i, 1);
            break;
         }
      }

      this.recentlyViewed.unshift(entry);

      if(this.recentlyViewed.length > MAX_RECENTLY_VIEWED) {
         this.recentlyViewed.splice(MAX_RECENTLY_VIEWED,
            this.recentlyViewed.length - MAX_RECENTLY_VIEWED);
      }

      LocalStorage.setItem("portal_recently_viewed_" + convertToKey(this.currentUser.name),
         JSON.stringify(this.recentlyViewed));
   }

   editViewsheet(entry: RepositoryEntry): void {
      this.composerService.composerOpen.subscribe(open => {
         if(open) {
            let event = new EditViewsheetEvent(entry.entry.identifier);
            this.viewsheetClient.sendEvent("/events/composer/editViewsheet", event);
         }
         else {
            const params = new HttpParams().set("vsId", entry.entry.identifier);
            GuiTool.openBrowserTab("composer", params);
         }
      });
   }

   collapseTree(collapsed: boolean) {
      this.treePaneCollapsed = collapsed;
      this.collapseTreeService.collapseTree(this.treePaneCollapsed);
   }
}

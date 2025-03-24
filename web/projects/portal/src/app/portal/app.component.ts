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
import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { AfterViewInit, Component, Inject, OnDestroy, OnInit } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { NgbDatepickerConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { LogoutService } from "../../../../shared/util/logout.service";
import { AssetEntry, createAssetEntry } from "../../../../shared/data/asset-entry";
import { LicenseInfo } from "../common/data/license-info";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { LicenseInfoService } from "../common/services/license-info.service";
import { OpenComposerService } from "../common/services/open-composer.service";
import { ComponentTool } from "../common/util/component-tool";
import { GuiTool } from "../common/util/gui-tool";
import { PortalCreationPermissions } from "./custom/portal-creation-permissions";
import { PreferencesDialog } from "./dialog/preferences-dialog.component";
import { PortalModel } from "./portal-model";
import { PortalTab, PortalTabs } from "./portal-tab";
import { CurrentRouteService } from "./services/current-route.service";
import { HideNavService } from "./services/hide-nav.service";
import { HistoryBarService } from "./services/history-bar.service";
import { PortalModelService } from "./services/portal-model.service";
import { PortalTabsService } from "./services/portal-tabs.service";
import { GettingStartedService } from "../widget/dialog/getting-started-dialog/service/getting-started.service";

const PORTAL_MODEL_URI: string = "../api/portal/get-portal-model";
const REFRESH_CREATION_PERMISSION_URI = "../api/portal/refresh-creation-permissions";
const COMPOSER_WIZARD_STATUS_URI: string = "../api/composer/wizard/status";
const PORTAL_PROFILING_URI: string = "../api/portal/set-profiling/";
const PORTAL_CHECK_SHOW_GETTING_STARTED_URI: string = "../api/portal/getting-started";
declare const window: any;

@Component({
   selector: "portal-app",
   templateUrl: "app.component.html",
   styleUrls: ["app.component.scss"]
})
export class PortalAppComponent implements OnInit, OnDestroy, AfterViewInit {
   PortalTabs = PortalTabs;
   model: PortalModel;
   portalTabs: PortalTab[];
   customPortalTabs: PortalTab[];
   reportTabFirst: boolean = true;
   dataTabFirst: boolean = true;
   hideNav: boolean;
   logoSrc: string = "../portal/logo";
   mobile: boolean;
   currentUrl: string;
   tabBodyHeight: number;
   private routeSubscription: Subscription;
   private licenseInfo: LicenseInfo;
   private readonly ACCESSIBILITY_CLASS: string = "accessible";
   private destroy$ = new Subject<void>();

   get openComposerEnabled(): boolean {
      return this.model.composerEnabled;
   }

   constructor(private modalService: NgbModal, private http: HttpClient,
      private portalTabsService: PortalTabsService,
      private ngbDatepickerConfig: NgbDatepickerConfig,
      private route: ActivatedRoute,
      private router: Router,
      private hideNavService: HideNavService,
      private portalModelService: PortalModelService,
      private historyBarService: HistoryBarService,
      private licenseInfoService: LicenseInfoService,
      private openComposerService: OpenComposerService,
      currentRouteService: CurrentRouteService,
      @Inject(DOCUMENT) private document: Document,
      private firstDayOfWeekService: FirstDayOfWeekService,
      private bodyTitle: Title,
      private logoutService: LogoutService,
      private gettingStartedService: GettingStartedService) {
      ngbDatepickerConfig.minDate = { year: 1900, month: 1, day: 1 };
      ngbDatepickerConfig.maxDate = { year: 2099, month: 12, day: 31 };

      this.routeSubscription = currentRouteService.currentUrl.subscribe(
         (url) => this.currentUrl = url
      );
   }

   ngOnInit(): void {
      this.mobile = GuiTool.isMobileDevice();

      if(document.body.className.indexOf("app-loaded") == -1) {
         document.body.className += " app-loaded";
      }

      this.route
         .queryParams
         .subscribe(params => {
            let hideNav = params["hideNav"] === "true";

            if(!this.hideNavService.hideNav) {
               this.hideNavService.hideNav = hideNav;
               this.hideNav = hideNav;
            }
            else {
               this.hideNav = this.hideNavService.hideNav;
            }

            if(params["openDialog"] && params["openDialog"] === "preferences") {
               this.showPreferences();
            }
         });


      this.http.get<PortalModel>(PORTAL_MODEL_URI)
         .subscribe((model) => {
            this.model = model;
            this.portalModelService.model = model;
            this.updateAccessibility();
            this.checkDefaultTab();

            let title = model.title;
            let idx;

            if((idx = this.currentUrl.indexOf("vs/view/")) > 0) {
               title = decodeURIComponent(this.currentUrl.substring(idx + 8));
            }

            const entry: AssetEntry = createAssetEntry(title);
            this.bodyTitle.setTitle(entry ? entry.path : title);
            this.logoutService.setLogoutUrl(model.logoutUrl);
         });

      this.portalTabsService.getPortalTabs().subscribe((portalTabs) => {
         this.portalTabs = portalTabs;
         this.setTabOrder();
         this.checkDefaultTab();
      });

      this.portalTabsService.getCustomTabs().subscribe((tabs) => {
         this.customPortalTabs = tabs.filter(tab => tab.visible);
      });

      this.licenseInfoService.getLicenseInfo().subscribe(info => this.licenseInfo = info);
      window.addEventListener("message", (evt) => this.handleMessageEvent(evt), false);

      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.ngbDatepickerConfig.firstDayOfWeek = model.isoFirstDay;
      });

      this.logoutService.inGracePeriod
         .pipe(takeUntil(this.destroy$))
         .subscribe(gracePeriod => {
            if(!!this.model) {
               this.model.elasticLicenseExhausted = gracePeriod;
            }
         });
   }

   ngAfterViewInit(): void {
      this.http.get(PORTAL_CHECK_SHOW_GETTING_STARTED_URI).subscribe((result) => {
         if("false" != result) {
            this.gettingStartedService.start(result == "force");
         }
      });
   }

   ngOnDestroy(): void {
      if(this.routeSubscription) {
         this.routeSubscription.unsubscribe();
         this.routeSubscription = null;
      }

      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   refreshCreationPermissions() {
      this.http.get<PortalCreationPermissions>(REFRESH_CREATION_PERMISSION_URI)
         .subscribe((creationPermissions) => {
            if(!!creationPermissions) {
               this.model.composerEnabled = creationPermissions.composerEnabled;
               this.model.dashboardEnabled = creationPermissions.dashboardEnabled;
               this.model.newDatasourceEnabled = creationPermissions.newDatasourceEnabled;
               this.model.newWorksheetEnabled = creationPermissions.newWorksheetEnabled;
               this.model.newViewsheetEnabled = creationPermissions.newViewsheetEnabled;
            }
         });
   }

   updateAccessibility(): void {
      const accessible: boolean = this.model.accessible;

      if(accessible) {
         const body: HTMLElement = this.document.body;
         body.classList.add(this.ACCESSIBILITY_CLASS);
      }
   }

   setTabOrder(): void {
      if(!!this.portalTabs) {
         const dIndex = this.portalTabs.findIndex(t => t.name == PortalTabs.DASHBOARD);
         const rIndex = this.portalTabs.findIndex(t => t.name == PortalTabs.REPORT);
         const dataIndex = this.portalTabs.findIndex(t => t.name == PortalTabs.DATA);
         const scheduleIndex = this.portalTabs.findIndex(t => t.name == PortalTabs.SCHEDULE);
         this.reportTabFirst = rIndex <= dIndex;
         this.dataTabFirst = scheduleIndex <= dataIndex;
      }
      else {
         this.reportTabFirst = true;
         this.dataTabFirst = true;
      }
   }

   checkDefaultTab(): void {
      if(this.currentUrl == "/portal" && this.model && !this.model.homeVisible && this.portalTabs) {
         const dashboard = this.portalTabs.find(t => t.name == PortalTabs.DASHBOARD);
         const report = this.portalTabs.find(t => t.name == PortalTabs.REPORT);

         if(this.model.hasDashboards && dashboard) {
            this.router.navigate(["/portal/" + dashboard.uri]);
         }
         else if(report) {
            this.router.navigate(["/portal/" + report.uri]);
         }
      }
   }

   logOut(): void {
      this.logoutService.logout();
   }

   profiling(): void {
      this.http.get<void>(PORTAL_PROFILING_URI + !this.model.profiling)
         .subscribe(() => {
            this.model.profiling = !this.model.profiling;
         });
   }

   showPreferences(): void {
      ComponentTool.showDialog(this.modalService, PreferencesDialog,
         () => {
            this.historyBarService.refreshStatus();
         },
         {
            size: "lg",
            backdrop: "static"
         }
      );
   }

   showDocument(): void {
      if(this.model.helpURL) {
         window.open(this.model.helpURL + "#cshid=PortalFunctions");
      }
   }

   getTab(name: string): PortalTab {
      if(this.portalTabs) {
         for(let tab of this.portalTabs) {
            if(tab.name === name) {
               return tab;
            }
         }
      }

      return null;
   }

   getDashboardTabTooltip(): string {
      if(this.getTab(PortalTabs.DASHBOARD)) {
         return "_#(js:Dashboard)";
      }
      else {
         return "";
      }
   }

   getDataTabTooltip(): string {
      if(this.getTab(PortalTabs.DATA)) {
         return "_#(js:Data)";
      }
      else {
         return "";
      }
   }

   getScheduleTabTooltip(): string {
      if(this.getTab(PortalTabs.SCHEDULE)) {
         return "_#(js:Schedule)";
      }
      else {
         return "";
      }
   }

   isTabSelected(name: string): boolean {
      if(!this.portalTabs || !this.currentUrl) {
         return false;
      }

      if(name === PortalTabs.CUSTOM) {
         return this.currentUrl.indexOf(PortalTabs.CUSTOM) >= 0;
      }
      else {
         for(let tab of this.portalTabs) {
            if(tab.name === name && this.currentUrl.indexOf(tab.uri) >= 0) {
               return true;
            }
         }
      }

      return false;
   }

   openComposer(vs: boolean): void {
      this.openComposerService.composerOpen.subscribe(open => {
         if(!open) {
            this.http.get<WizardDialogStatusModel>(COMPOSER_WIZARD_STATUS_URI)
               .subscribe((model: WizardDialogStatusModel) => {
                  if(vs) {
                     let vsWizard: boolean = model.viewsheetWizardStatus == null ?
                        true : model.viewsheetWizardStatus == "show";
                     window.open("composer?vsWizard=" + vsWizard, "_blank");
                  }
                  else {
                     let wsWizard: boolean = model.worksheetWizardStatus == null ?
                        true : model.worksheetWizardStatus == "show";
                     window.open("composer?wsWizard=" + wsWizard, "_blank");
                  }
               });
         }
         else {
            // try to open wizard in composer
            const bc = new BroadcastChannel("composer");
            if(vs) {
               bc.postMessage("vsWizard");
            }
            else {
               bc.postMessage("wsWizard");
            }

            ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:composer.tabAlreadyOpened)");
         }
      });
   }

   showListings(): void {
      let queryParams = {
         path: "/",
         scope: "0"
      };

      let listingPath = "";

      this.router.navigate(["portal/tab/data/datasources/listing", listingPath],
         { queryParams: queryParams });
   }

   handleMessageEvent(event: any): void {
      if(!event.data) {
         return;
      }

      if(event.data.event === "openDialog") {
         if(event.data.dialogName === "preferences") {
            this.showPreferences();
         }
      }
   }
}

export class WizardDialogStatusModel {
   constructor(public viewsheetWizardStatus: string,
      public worksheetWizardStatus: string) {
   }
}

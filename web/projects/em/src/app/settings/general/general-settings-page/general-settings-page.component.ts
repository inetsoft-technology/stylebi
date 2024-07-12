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
import { HttpClient } from "@angular/common/http";
import { Component, ElementRef, OnDestroy, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { ActivatedRoute } from "@angular/router";
import { Subject, Subscription } from "rxjs";
import { filter, takeUntil } from "rxjs/operators";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { Tool } from "../../../../../../shared/util/tool";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Secured } from "../../../secured";
import { GeneralSettingsPageModel } from "./general-settings-page-model";
import { GeneralSettingsType } from "./general-settings-type.enum";

const GENERAL_SETTINGS_PAGE_MODEL_URI = "../api/em/general/settings/model";

export interface GeneralSettingsChanges {
   valid: boolean;
   modelType: GeneralSettingsType;
   model: any;
}

export class GeneralSettingsNavLink {
   constructor(public id: string, public label: string, public modelId?: string) {
   }
}

@Secured({
   route: "/settings/general",
   label: "General"
})
@ContextHelp({
   route: "/settings/general",
   link: "EMSettingsGeneral"
})
@Component({
   selector: "em-general-settings-page",
   templateUrl: "./general-settings-page.component.html",
   styleUrls: ["./general-settings-page.component.scss"]
})
export class GeneralSettingsPageComponent implements OnInit, OnDestroy {
   model: GeneralSettingsPageModel;
   modelClone: GeneralSettingsPageModel;
   saveModel = <GeneralSettingsPageModel> {};

   validModels = new Map();
   changed: boolean = false;
   loading: boolean = false;
   currentView = "data-source";

   private destroy$ = new Subject();
   private subscriptions = new Subscription();
   private licenseKeyChanged: boolean;
   securityEnabled?: boolean;
   isEnterprise: boolean;

   readonly _navLinks = [
      new GeneralSettingsNavLink("data-space", "_#(js:Data Space)", "dataSpaceSettingsModel"),
      new GeneralSettingsNavLink("server", "_#(js:Cluster)", "clusterSettingsModel"),
      new GeneralSettingsNavLink("license", "_#(js:License)", "licenseKeySettingsModel"),
      new GeneralSettingsNavLink("localization", "_#(js:Localization)", "localizationSettingsModel"),
      new GeneralSettingsNavLink("mv", "_#(js:Materialized Views)", "mvSettingsModel"),
      new GeneralSettingsNavLink("cache", "_#(js:Caching)", "cacheSettingsModel"),
      new GeneralSettingsNavLink("email", "_#(js:Email)", "emailSettingsModel"),
      new GeneralSettingsNavLink("performance", "_#(js:Performance)", "performanceSettingsModel")
   ];

   readonly editorStyle = {
      "display": "flex",
      "flex-direction": "row",
      "align-items": "stretch",
      "overflow": "hidden",
   };

   get navLinks(): GeneralSettingsNavLink[] {
      let visibleLinks = this._navLinks
         .filter(link => (!link.modelId || !!this.model[link.modelId])
            && (link.id != "license" || this.isEnterprise)
            && (link.id != "server" || this.isEnterprise));

      return visibleLinks.length <= 1 ? [] : visibleLinks;
   }

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private element: ElementRef, private route: ActivatedRoute,
               private dialog: MatDialog, private snackBar: MatSnackBar,
               private appInfoService: AppInfoService)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:General Settings)";
      this.loadModel();

      this.route.fragment
         .pipe(
            filter(fragment => fragment != null),
            takeUntil(this.destroy$)
         )
         .subscribe(fragment => {
            this.currentView = fragment;
            this.scrollToItem(this.currentView);
         });

      this.subscriptions.add(this.appInfoService.isEnterprise().subscribe((isEnterprise) => {
         this.isEnterprise = isEnterprise;
      }));
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
      this.subscriptions.unsubscribe();
   }

   onModelChanged(changes: GeneralSettingsChanges) {
      this.saveModel[changes.modelType] = changes.model;
      this.validModels.set(changes.modelType, changes.valid);
      this.changed = true;
   }

   loadModel() {
      this.http.get(GENERAL_SETTINGS_PAGE_MODEL_URI).subscribe(model => {
         this.model = model;
         this.modelClone = Tool.clone(model);
         setTimeout(() => this.scrollToItem(this.currentView));
      });
   }

   apply() {
      this.loading = true;
      const licenseKeyChanged = this.licenseKeyChanged;

      (async () => {
         const errors = [];

         if(this.saveModel.dataSpaceSettingsModel) {
            errors.push("_#(js:config.streaming.effectNote)");
         }

         while(errors.length > 0) {
            const error = errors.pop();
            await this.snackBar.open(error, undefined, {duration: Tool.SNACKBAR_DURATION})
               .afterDismissed()
               .toPromise();
         }
      })();

      this.http.post(GENERAL_SETTINGS_PAGE_MODEL_URI, this.saveModel).subscribe(model => {
         this.model = model;
         this.modelClone = Tool.clone(model);
         this.saveModel = {};
         this.changed = false;
         this.loading = false;

         if(licenseKeyChanged) {
            this.licenseKeyChanged = false;
            this.snackBar.open(
               "_#(js:em.license.restartRequired)", "_#(js:Close)",
               {duration: Tool.SNACKBAR_DURATION});
         }
      },
      (res) => {
         this.loading = false;
         let config = new MatSnackBarConfig();
         config.duration = Tool.SNACKBAR_DURATION;
         config.panelClass = ["max-width"];
         this.snackBar.open(res.error.message, "_#(js:Close)", config);
         this.loadModel();
         this.changed = false;
         this.saveModel = {};
      });
   }

   reset() {
      this.model = Tool.clone(this.modelClone);
      this.saveModel = {};
      this.changed = false;
   }

   onNavigationScrolled(event: string[]) {
      if(event && event.length > 0) {
         this.currentView = event[0];
         this.scrollToItem(`${this.currentView}-navlink`, false);
      }
   }

   scrollToItem(item: string, updateCurrentView: boolean = true) {
      if(this.element.nativeElement) {
         const target = this.element.nativeElement.querySelector(`#${item}`);

         if(target) {
            target.scrollIntoView();
         }
      }

      if(updateCurrentView) {
         this.currentView = item;
      }
   }

   filterNavLinks(linkID): string {
      let result = "";


      if(!!this.model) {
         if(linkID == "mv" && this.model.mvSettingsModel == null) {
            result = "none";
         }
      }

      return result;
   }

   get valid(): boolean {
      let final: boolean = true;
      this.validModels.forEach((val) => final = final && val);
      return final;
   }

   onLicenseKeyModelChanged(changes: GeneralSettingsChanges): void {
      this.onModelChanged(changes);
      this.licenseKeyChanged = true;
   }
}

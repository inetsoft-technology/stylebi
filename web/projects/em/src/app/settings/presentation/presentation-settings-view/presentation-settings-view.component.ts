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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Component, Input, ElementRef, OnDestroy, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { ActivatedRoute } from "@angular/router";
import { Observable, Subject, throwError } from "rxjs";
import { catchError, filter, map, takeUntil } from "rxjs/operators";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Secured } from "../../../secured";
import { PresentationSettingsModel } from "./presentation-settings-model";
import { PresentationSettingsType } from "./presentation-settings-type.enum";

export interface PresentationSettingsChanges {
   valid: boolean;
   modelType: PresentationSettingsType;
   model: any;
}

export class PresentationSettingsNavLink {
   constructor(public id: string, public label: string) {}
}

@Secured({
   route: "/settings/presentation/settings",
   label: "Presentation Settings"
})
@ContextHelp({
   route: "/settings/presentation/settings",
   link: "EMSettingsPresentation"
})
@Component({
   selector: "em-presentation-settings-view",
   templateUrl: "./presentation-settings-view.component.html",
   styleUrls: ["./presentation-settings-view.component.scss"]
})
export class PresentationSettingsViewComponent implements OnInit, OnDestroy {
   @Input() orgSettings: boolean = false;
   model: PresentationSettingsModel;
   modelClone: PresentationSettingsModel;
   saveModel = <PresentationSettingsModel> {};

   validModels = new Map();
   changed: boolean = false;
   currentView = "general-format";
   routingToView: boolean = false;
   isSysAdmin: boolean = false;

   private destroy$ = new Subject();

   navLinks = [
      new PresentationSettingsNavLink("general-format", "_#(js:General Format)"),
      new PresentationSettingsNavLink("look-and-feel", "_#(js:Look and Feel)"),
      new PresentationSettingsNavLink("welcome-page", "_#(js:Welcome Page)"),
      new PresentationSettingsNavLink("login-banner", "_#(js:Login Banner)"),
      new PresentationSettingsNavLink("composer-message", "_#(js:Composer Messages)"),
      new PresentationSettingsNavLink("portal-integration", "_#(js:Portal Integration)"),
      new PresentationSettingsNavLink("time-settings", "_#(js:Time Settings)"),
      new PresentationSettingsNavLink("pdf", "_#(js:PDF Settings)"),
      new PresentationSettingsNavLink("font-mapping", "_#(js:Font Mapping)"),
      new PresentationSettingsNavLink("data-source-visibility", "_#(js:Data Source Visibility)"),
      new PresentationSettingsNavLink("webmap", "_#(js:Web Map)")
   ];

   constructor(private pageTitle: PageHeaderService, private http: HttpClient,
               public snackBar: MatSnackBar,
               private element: ElementRef, private route: ActivatedRoute,
               public dialog: MatDialog)
   {
   }

   ngOnInit() {
      if(this.orgSettings) {
         this.pageTitle.title = "_#(js:Organization Presentation Settings)";
      }
      else {
         this.pageTitle.title = "_#(js:Presentation Settings)";
      }

      this.http.get("../api/em/navbar/isSiteAdmin").subscribe((isSysAdmin: boolean) =>
      {
         this.isSysAdmin = isSysAdmin && !this.orgSettings;
         const params = new HttpParams().set("orgSettings", !this.isSysAdmin);

         this.http.get<PresentationSettingsModel>("../api/em/settings/presentation/model", {params})
            .subscribe((model: PresentationSettingsModel) => {
               let offset = 0;
               this.isSysAdmin = !model.orgSettings;

               if(!this.isSysAdmin) {
                  this.navLinks = [
                     new PresentationSettingsNavLink("general-format", "_#(js:General Format)"),
                     new PresentationSettingsNavLink("look-and-feel", "_#(js:Look and Feel)"),
                     new PresentationSettingsNavLink("composer-message", "_#(js:Composer Messages)"),
                     new PresentationSettingsNavLink("portal-integration", "_#(js:Portal Integration)"),
                     new PresentationSettingsNavLink("time-settings", "_#(js:Time Settings)"),
                     new PresentationSettingsNavLink("pdf", "_#(js:PDF Settings)"),
                     new PresentationSettingsNavLink("data-source-visibility", "_#(js:Data Source Visibility)"),
                     new PresentationSettingsNavLink("webmap", "_#(js:Web Map)")
                  ];
               }

               if(model.exportMenuSettingsModel) {
                  this.navLinks.splice(
                     1 + offset, 0,
                     new PresentationSettingsNavLink("export-menu", "_#(js:Export Menu)"));
                  ++offset;
               }

               if(model.dashboardSettingsModel) {
                  this.navLinks.splice(
                     5 + offset, 0,
                     new PresentationSettingsNavLink("dashboard-settings", "_#(js:Dashboard Settings)"));
                  ++offset;
               }

               if(model.viewsheetToolbarOptionsModel) {
                  this.navLinks.splice(
                     5 + offset, 0,
                     new PresentationSettingsNavLink("viewsheet-toolbar", "_#(js:Viewsheet Toolbar)"));
                  ++offset;
               }

               if(model.shareSettingsModel) {
                  this.navLinks.push(
                     new PresentationSettingsNavLink("sharing", "_#(js:Social Sharing)"));
               }

               this.model = model;
               this.modelClone = Tool.clone(model);
               setTimeout(() => this.scrollToItem(this.currentView));
         });
      });

      this.route.fragment
         .pipe(
            filter(fragment => fragment != null),
            takeUntil(this.destroy$)
         )
         .subscribe(fragment => {
            this.routingToView = true;
            this.currentView = fragment;
            this.scrollToItem(fragment, false);
         });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   onModelChanged(changes: PresentationSettingsChanges) {
      this.saveModel[changes.modelType] = changes.model;
      this.validModels.set(changes.modelType, changes.valid);
      this.changed = true;
   }

   private handleError(res: HttpErrorResponse): Observable<Object> {
      let error  = res.error;
      let errMsg = (!!error && !!error.message) ? error.message :
               res.status ? `${res.status} - ${res.statusText}`
                  : "_#(js:server.error.connectionLost)";

      if(!!errMsg) {
         let config = new MatSnackBarConfig();
         config.duration = Tool.SNACKBAR_DURATION;
         config.panelClass = ["max-width"];

         this.snackBar.open(errMsg, "_#(js:Close)", config);
      }

      return throwError(errMsg);
   }

   apply() {
      this.saveModel.orgSettings = !this.isSysAdmin;
      this.http.post("../api/em/settings/presentation/model", this.saveModel)
         .pipe(catchError((error) => this.handleError(error)))
         .subscribe(newModel => {
            this.model = newModel;
            this.modelClone = Tool.clone(newModel);
            this.saveModel = <PresentationSettingsModel> {};
            this.changed = false;
         });
   }

   reset() {
      let dialogContent = this.isSysAdmin ?
         "_#(js:em.settings.presentation.confirmGlobalReset)" :
         "_#(js:em.settings.presentation.confirmOrgReset)";

      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:em.settings.presentationReset)",
            content: dialogContent,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(value => {
         if(value) {
            this.saveModel.orgSettings = !this.isSysAdmin;
            this.http.post("../api/em/settings/presentation/model/reset", this.saveModel)
               .pipe(catchError((error) => this.handleError(error)))
               .subscribe(newModel => {
                  this.model = newModel;
                  this.modelClone = Tool.clone(newModel);
                  this.saveModel = <PresentationSettingsModel> {};
                  this.changed = false;
               });
         }
      });
   }

   onNavigationScrolled(event: string[]) {
      if(this.routingToView == false) {
         if(event && event.length > 0) {
            this.currentView = event[0];
         }
         else {
            this.currentView = "format-settings";
         }
      }

      this.scrollToItem(`${this.currentView}-navlink`, false);
      this.routingToView = false;
   }

   scrollToItem(item: string, updateCurrentView: boolean = true) {
      item = item || "format-settings";
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

   get valid(): boolean {
      let final: boolean = true;
      this.validModels.forEach((val) => final = final && val);
      return final;
   }
}

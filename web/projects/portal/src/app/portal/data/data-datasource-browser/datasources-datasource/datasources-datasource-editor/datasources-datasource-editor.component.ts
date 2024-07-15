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
import { Component, EventEmitter, Input, NgZone, OnDestroy, OnInit, Output } from "@angular/core";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject, combineLatest, Observable, Subject } from "rxjs";
import { flatMap, map, takeUntil, tap } from "rxjs/operators";
import { FormValidators } from "../../../../../../../../shared/util/form-validators";
import { DataSourceDefinitionModel } from "../../../../../../../../shared/util/model/data-source-definition-model";
import { TabularButton } from "../../../../../common/data/tabular/tabular-button";
import { TabularView } from "../../../../../common/data/tabular/tabular-view";
import {
   OAuthAuthorizationService,
   OAuthParameters
} from "../../../../../common/services/oauth-authorization.service";
import { DebounceService } from "../../../../../widget/services/debounce.service";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";

const DATASOURCES_URI: string = "../api/portal/data/datasources";

@Component({
   selector: "datasources-datasource-editor",
   templateUrl: "./datasources-datasource-editor.component.html",
   styleUrls: ["./datasources-datasource-editor.component.scss"]
})
export class DatasourcesDatasourceEditorComponent implements OnInit, OnDestroy {
   @Input()
   get datasource(): DataSourceDefinitionModel {
      return this._datasource;
   }

   set datasource(value: DataSourceDefinitionModel) {
      this._datasource = value;
      this.initView();
   }

   @Input()
   get usedNames(): string[] {
      return this._usedNames;
   }

   set usedNames(value: string[]) {
      if(!!value) {
         this._usedNames.splice(0, this._usedNames.length, ...value);
      }
      else {
         this._usedNames.splice(0);
      }
   }

   @Output() datasourceChanged = new EventEmitter<DataSourceDefinitionModel>();
   @Output() datasourceValid = new EventEmitter<boolean>();
   @Output() onWarning = new EventEmitter<string>();
   nameGroup: FormGroup;
   private dependsOn: Set<string>;
   private refreshButtonExists: boolean;
   cancelButtonExists: boolean;
   private sequenceNumber = 0;
   private _datasource: DataSourceDefinitionModel;
   private _usedNames: string[] = [];
   private datasourceValid$ = new BehaviorSubject<boolean>(true);
   private nameValid$ = new BehaviorSubject<boolean>(true);
   private destroy$ = new Subject<void>();
   // return browser view model for files browser in tabular file editor
   browseFunction: (path: string, property: string, all: boolean) => Observable<TreeNodeModel> =
      (path: string, property, all: boolean) => {
         let params = new HttpParams()
            .set("property", property)
            .set("path", path);

         const uri = DATASOURCES_URI + "/browser";

         return this.httpClient.post<TreeNodeModel>(uri, this.datasource, {
            params: params
         });
      };

   private get nameValid(): boolean {
      return !!this.nameGroup?.get("name") ? this.nameGroup.get("name").valid : true;
   }

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               private debounceService: DebounceService,
               private oauthService: OAuthAuthorizationService,
               private zone: NgZone,
               formBuilder: FormBuilder)
   {
      combineLatest([this.datasourceValid$, this.nameValid$])
         .pipe(map(([v1, v2]) => v1 && v2))
         .subscribe(v => this.datasourceValid.emit(v));
      this.nameGroup = formBuilder.group({
         "name": ["", [Validators.required, Validators.pattern(FormValidators.DATASOURCE_NAME_REGEXP), FormValidators.exists(this.usedNames)]]
      });
      this.nameGroup.get("name").valueChanges.pipe(
         takeUntil(this.destroy$),
         tap(value => this.datasource.name = value),
         map(() => this.nameValid)
      )
         .subscribe(v => this.nameValid$.next(v));
   }

   ngOnInit(): void {
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
      this.datasourceValid$.complete();
      this.nameValid$.complete();
   }

   authorize(button: TabularButton) {
      button.clicked = false;
      const paramsRequest = {
         user: button.oauthUser,
         password: button.oauthPassword,
         clientId: button.oauthClientId,
         clientSecret: button.oauthClientSecret,
         scope: button.oauthScope,
         authorizationUri: button.oauthAuthorizationUri,
         tokenUri: button.oauthTokenUri,
         flags: button.oauthFlags,
         dataSource: this.datasource
      };
      const authParams = {
         serviceName: button.oauthServiceName,
         method: button.method
      };
      const tokensRequest = {
         dataSource: this.datasource
      };
      this.httpClient.post<OAuthParameters>(DATASOURCES_URI + "/oauth-params", paramsRequest)
         .pipe(
            map(params => Object.assign(authParams, params)),
            flatMap(params => this.oauthService.authorize(params)),
            map(tokens => Object.assign(tokensRequest, tokens)),
            flatMap(request => this.httpClient.post<DataSourceDefinitionModel>(DATASOURCES_URI + "/oauth-tokens", request))
         )
         .subscribe(
            (datasource) => {
               this.datasource = datasource;
               this.datasourceChanged.emit(this.datasource);
               this.initView();
            },
            (error) => {
               console.error("Authorization error: ", error);
               this.onWarning.emit("_#(js:data.datasources.authorizationError)");
            });
   }

   /**
    * Send request to refresh the data source definition and tabular view.
    * @param cancelButton  the cancel button that prompted this refresh or null if no cancel button
    */
   private refreshView(cancelButton: TabularButton = null): void {
      this.debounceService.debounce("tabular-view-changed", () => {
         this.datasource.sequenceNumber = ++this.sequenceNumber;
         this.httpClient.post<DataSourceDefinitionModel>(DATASOURCES_URI + "/refreshView", this.datasource)
            .subscribe(
               data => {
                  if(!cancelButton && data.sequenceNumber == this.sequenceNumber) {
                     // Run change detection to set the datasource
                     this.zone.run(() => {
                        this.datasource = data;
                        this.datasourceChanged.emit(this.datasource);
                        this.initView();
                     });
                  }
               },
               () => {
                  this.onWarning.emit("_#(js:data.datasources.refreshViewError)");
               },
               () => {
                  this.clearButtonLoading();
               }
            );

         this.clearButtonClicks(
            this.datasource.tabularView != null ? this.datasource.tabularView.views : []);
      }, 1000, []);
   }

   /**
    * Initialize the depends on properties and refresh button exists boolean.
    */
   initView(): void {
      this.nameGroup.get("name").setValue(this.datasource.name);

      if(this.datasource.tabularView != null) {
         this.dependsOn = this.getDependsOn(this.datasource.tabularView.views);
         this.refreshButtonExists = this.hasRefreshButton(this.datasource.tabularView.views);
         this.cancelButtonExists = this.hasCancelButton(this.datasource.tabularView.views);
      }
   }

   /**
    * Called when the tabular view has changed. Check if should refresh view then refresh.
    * @param view
    */
   onViewChanged(view: TabularView[]): void {
      if(!this.refreshButtonExists && this.dependsOn.has(view[0].value)) {
         this.refreshView();
      }
   }

   onValidChanged(valid: boolean): void {
      this.datasourceValid$.next(valid);
   }

   /**
    * Initialize the depends on set from the current tabular views.
    * @param views      the tabular vies
    * @param dependsOn  the depends on set
    * @returns {Set<string>}  a set containing the properties that the view depends on
    */
   getDependsOn(views: TabularView[], dependsOn: Set<string> = new Set<string>()): Set<string> {
      views.forEach((view: TabularView) => {
         if(view.editor != null && view.editor.dependsOn != null) {
            view.editor.dependsOn.forEach((value) => {
               dependsOn.add(value);
            });
         }

         if(view.button != null && view.button.dependsOn != null) {
            view.button.dependsOn.forEach((value) => {
               dependsOn.add(value);
            });
         }

         dependsOn = this.getDependsOn(view.views, dependsOn);
      });

      return dependsOn;
   }

   /**
    * Check if the tabular view contains a refresh button.
    * @param views   the tabular views
    * @returns {boolean}   true if the view contains a refresh button
    */
   hasRefreshButton(views: TabularView[]): boolean {
      for(let view of views) {
         if(view.type === "BUTTON" && view.button.type === "REFRESH") {
            return true;
         }
         else if(this.hasRefreshButton(view.views)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the tabular view contains a cancel button.
    * @param views   the tabular views
    * @returns {boolean}   true if the view contains a cancel button
    */
   hasCancelButton(views: TabularView[]): boolean {
      for(let view of views) {
         if(view.type === "BUTTON" && view.button.type === "CANCEL") {
            return true;
         }
         else if(this.hasCancelButton(view.views)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Called when a button is clicked. Refresh the tabular view.
    * @param button  the button that was clicked
    */
   buttonClicked(button: TabularButton): void {
      if(button.type === "OAUTH") {
         this.authorize(button);
      }
      else {
         this.refreshView(button.type === "CANCEL" ? button : null);
      }
   }

   clearButtonClicks(views: TabularView[]): void {
      for(let view of views) {
         if(view.type === "BUTTON") {
            view.button.clicked = false;
         }

         this.clearButtonClicks(view.views);
      }
   }

   clearButtonLoading(views: TabularView[] = this.datasource.tabularView.views): void {
      for(let view of views) {
         if(view.type === "BUTTON") {
            view.button.loading = false;
         }

         this.clearButtonLoading(view.views);
      }
   }
}

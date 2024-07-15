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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output, ChangeDetectorRef } from "@angular/core";
import { Observable } from "rxjs";
import { flatMap, map } from "rxjs/operators";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";
import { TabularButton } from "../../../common/data/tabular/tabular-button";
import { TabularView } from "../../../common/data/tabular/tabular-view";
import {
   OAuthAuthorizationService,
   OAuthParameters
} from "../../../common/services/oauth-authorization.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";
import { TabularQueryDialogModel } from "../../data/ws/tabular-query-dialog-model";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { FeatureFlagValue } from "../../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "tabular-query-dialog",
   templateUrl: "tabular-query-dialog.component.html",
   styleUrls: ["tabular-query-dialog.component.scss"]
})
export class TabularQueryDialog implements OnInit {
   @Input() runtimeId: string;
   @Input() tableName: string;
   @Input() initTableName: string;
   @Input() tables: AbstractTableAssembly[];
   @Input() dataSourceType: TabularDataSourceTypeModel;
   @Input() applyVisible: boolean = true;
   @Output() onApply = new EventEmitter<any>();
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();
   private readonly CONTROLLER_MODEL: string = "../api/composer/ws/tabular-query-dialog-model";
   private readonly CONTROLLER_SOCKET = "/events/ws/dialog/tabular-query-dialog-model";
   private readonly CONTROLLER_REFRESH_VIEW: string = "../api/composer/ws/tabular-query-dialog/refreshView";
   private readonly CONTROLLER_BROWSE: string = "../api/composer/ws/tabular-query-dialog/browse";
   private readonly CONTROLLER_OAUTH_PARAMS = "../api/composer/tabular-query-dialog/oauth-params";
   private readonly CONTROLLER_OAUTH_TOKENS = "../api/composer/tabular-query-dialog/oauth-tokens";
   readonly FeatureFlagValue = FeatureFlagValue;
   model: TabularQueryDialogModel;
   headers: HttpHeaders;
   valid: boolean;
   dependsOn: Set<string>;
   refreshButtonExists: boolean;
   cancelButtonExists: boolean;
   showLoading: boolean;
   isLoading: boolean;
   formValid = () => this.valid && !!this.model && !!this.model.dataSource &&
      (this.form?.valid || !this.tables);
   tableNameExists = false;
   form: UntypedFormGroup;

   constructor(private modelService: ModelService, private http: HttpClient,
               private oauthService: OAuthAuthorizationService,
               private changeRef: ChangeDetectorRef)
   {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   get displayTitle(): string {
      let value = this.dataSourceType.label;

      if(value != null && value.length > 30) {
         value = value.substring(0, 27) + "...";
      }

      return value;
   }

   get helpDisplayTitle(): string {
      let dataSources: {dataSourceTypeName, value}[] = [
         {dataSourceTypeName: "Rest.FortyTwoMatters", value: "42Matters"},
         {dataSourceTypeName: "Rest.ActiveCampaign", value: "ActiveCampaign"},
         {dataSourceTypeName: "Rest.AdobeAnalytics", value: "AdobeAnalytics"},
         {dataSourceTypeName: "Rest.Airtable", value: "Airtable"},
         {dataSourceTypeName: "Rest.Appfigures", value: "Appfigures"},
         {dataSourceTypeName: "Rest.Asana", value: "Asana"},
         {dataSourceTypeName: "Rest.AzureBlob", value: "AzureBlob"},
         {dataSourceTypeName: "Rest.AzureSearch", value: "AzureSearch"},
         {dataSourceTypeName: "Rest.Box", value: "Box"},
         {dataSourceTypeName: "Rest.CampaignMonitor", value: "CampaignMonitor"},
         {dataSourceTypeName: "Rest.Chargebee", value: "Chargebee"},
         {dataSourceTypeName: "Rest.Chargify", value: "Chargify"},
         {dataSourceTypeName: "Rest.ChartMogul", value: "ChartMogul"},
         {dataSourceTypeName: "Rest.Clockify", value: "Clockify"},
         {dataSourceTypeName: "Rest.ConstantContact", value: "ConstantContact"},
         {dataSourceTypeName: "Rest.Copper", value: "Copper"},
         {dataSourceTypeName: "Datagov", value: "DataGovJSON"},
         {dataSourceTypeName: "Rest.DataDotWorld", value: "DataWorld"},
         {dataSourceTypeName: "FacebookAdInsights", value: "FacebookAdInsights"},
         {dataSourceTypeName: "FacebookPageInsights", value: "FacebookPageInsights"},
         {dataSourceTypeName: "Rest.Firebase", value: "Firebase"},
         {dataSourceTypeName: "Rest.Freshdesk", value: "Freshdesk"},
         {dataSourceTypeName: "Rest.Freshsales", value: "Freshsales"},
         {dataSourceTypeName: "Rest.Freshservice", value: "Freshservice"},
         {dataSourceTypeName: "Rest.Fusebill", value: "Fusebill"},
         {dataSourceTypeName: "Rest.GitHub", value: "GitHub"},
         {dataSourceTypeName: "Rest.GitLab", value: "GitLab"},
         {dataSourceTypeName: "GOOGLE_ANALYTICS", value: "GoogleAnalytics"},
         {dataSourceTypeName: "Rest.GoogleCalendar", value: "GoogleCalendar"},
         {dataSourceTypeName: "Rest.GoogleSearchConsole", value: "GoogleSearchConsole"},
         {dataSourceTypeName: "GoogleDocs", value: "GoogleSpreadsheets"},
         {dataSourceTypeName: "GoSquared", value: "GoSquared"},
         {dataSourceTypeName: "GraphQL", value: "GraphQL"},
         {dataSourceTypeName: "Rest.Harvest", value: "Harvest"},
         {dataSourceTypeName: "Rest.HelpScoutDocs", value: "HelpScoutDocs"},
         {dataSourceTypeName: "Hive", value: "Hive"},
         {dataSourceTypeName: "Rest.HubSpot", value: "HubSpot"},
         {dataSourceTypeName: "Rest.InfluxDB", value: "InfluxDB"},
         {dataSourceTypeName: "Rest.Insightly", value: "Insightly"},
         {dataSourceTypeName: "Rest.Intervals", value: "Intervals"},
         {dataSourceTypeName: "Rest.Jira", value: "Jira"},
         {dataSourceTypeName: "Rest.Jive", value: "Jive"},
         {dataSourceTypeName: "Rest.Lighthouse", value: "Lighthouse"},
         {dataSourceTypeName: "Rest.Linkedin", value: "Linkedin"},
         {dataSourceTypeName: "Rest.LiveAgent", value: "LiveAgent"},
         {dataSourceTypeName: "Rest.Mailchimp", value: "Mailchimp"},
         {dataSourceTypeName: "Rest.Mixpanel", value: "Mixpanel"},
         {dataSourceTypeName: "monday.com", value: "MondayCom"},
         {dataSourceTypeName: "Mongo", value: "MongoDBREST"},
         {dataSourceTypeName: "Rest.Nicereply", value: "Nicereply"},
         {dataSourceTypeName: "OData", value: "OData"},
         {dataSourceTypeName: "Rest.Pipedrive", value: "Pipedrive"},
         {dataSourceTypeName: "Rest.PipelineDeals", value: "PipelineCRM"},
         {dataSourceTypeName: "Rest.Prometheus", value: "Prometheus"},
         {dataSourceTypeName: "QUICKBOOKS_DATA_CONNECTOR", value: "QuickBooks"},
         {dataSourceTypeName: "Rest.QuickbooksReports", value: "QuickBooksReports"},
         {dataSourceTypeName: "R", value: "R"},
         {dataSourceTypeName: "Rest.Remedyforce", value: "Remedyforce"},
         {dataSourceTypeName: "Rest", value: "RESTJSON"},
         {dataSourceTypeName: "Rest.XML", value: "RESTXML"},
         {dataSourceTypeName: "SForce", value: "Salesforce"},
         {dataSourceTypeName: "Rest.SalesforceReports", value: "SalesforceReports"},
         {dataSourceTypeName: "SAPABAP", value: "SAP"},
         {dataSourceTypeName: "SAPBAPI", value: "SAP"},
         {dataSourceTypeName: "SAPTable", value: "SAP"},
         {dataSourceTypeName: "Rest.SEOmonitor", value: "SEOMonitor"},
         {dataSourceTypeName: "Rest.ServiceNow", value: "ServiceNow"},
         {dataSourceTypeName: "SharepointOnline", value: "SharepointOnline"},
         {dataSourceTypeName: "shopify", value: "Shopify"},
         {dataSourceTypeName: "Rest.Smartsheet", value: "Smartsheet"},
         {dataSourceTypeName: "SOLR_DATA_CONNECTOR", value: "Solr"},
         {dataSourceTypeName: "Rest.Square", value: "Square"},
         {dataSourceTypeName: "Rest.Stripe", value: "Stripe"},
         {dataSourceTypeName: "Rest.SurveyMonkey", value: "SurveyMonkey"},
         {dataSourceTypeName: "Rest.TeamDesk", value: "TeamDesk"},
         {dataSourceTypeName: "SERVER_FILE", value: "TextExcel"},
         {dataSourceTypeName: "Rest.Toggl", value: "Toggl"},
         {dataSourceTypeName: "Rest.Twilio", value: "Twilio"},
         {dataSourceTypeName: "Rest.SendGrid", value: "TwilioSendGrid"},
         {dataSourceTypeName: "Rest.Twitter", value: "Twitter"},
         {dataSourceTypeName: "Rest.WordPress", value: "WordPress"},
         {dataSourceTypeName: "Rest.Xero", value: "Xero"},
         {dataSourceTypeName: "Rest.YouTubeAnalytics", value: "YouTubeAnalytics"},
         {dataSourceTypeName: "Rest.Zendesk", value: "Zendesk"},
         {dataSourceTypeName: "Rest.ZendeskSell", value: "ZendeskSell"},
         {dataSourceTypeName: "Rest.ZohoCRM", value: "ZohoCRM"}
      ];

      return dataSources.find(data => data.dataSourceTypeName === this.dataSourceType.name)?.value;
   }

   ngOnInit(): void {
      this.isLoading = true;
      this.form = this.createForm();

      this.form.get("name").valueChanges.subscribe(value => {
         this.initTableName = value;
         this.updateNameValidation();
      });

      this.valid = false;
      let params = new HttpParams()
         .set("runtimeId", this.runtimeId)
         .set("dataSource", this.dataSourceType.dataSource);

      if(this.tableName != null) {
         params = params.set("tableName", this.tableName);
      }

      this.modelService.getModel(this.CONTROLLER_MODEL, params).subscribe(
         (data) => {
            this.model = <TabularQueryDialogModel> data;
            this.model.dataSource = this.dataSourceType.dataSource;
            this.initView();

            if(!this.model.tabularView) {
               this.refreshView();
            }

            this.isLoading = false;
         },
          () => {
             this.isLoading = false;
          });
   }

   createForm(): UntypedFormGroup {
      return new UntypedFormGroup({
         name: new UntypedFormControl(this.initTableName, [
            Validators.required,
            FormValidators.nameSpecialCharacters,
            FormValidators.exists(this.tables?.map((table) => table.name.toUpperCase()),
               {
                  trimSurroundingWhitespace: true,
                  ignoreCase: true,
                  originalValue: this.initTableName
               })
         ])
      });
   }

   authorize(button: TabularButton) {
      button.clicked = false;
      const timeout = setTimeout(() => this.showLoading = true, 1000);
      const params = new HttpParams().set("dataSource", this.model.dataSource);
      const options = { headers: this.headers, params: params };
      const paramsRequest = {
         clientId: button.oauthClientId,
         clientSecret: button.oauthClientSecret,
         scope: button.oauthScope,
         authorizationUri: button.oauthAuthorizationUri,
         tokenUri: button.oauthTokenUri,
         flags: button.oauthFlags,
         view: this.model.tabularView
      };
      const authParams = {
         serviceName: button.oauthServiceName,
         method: button.method
      };
      const tokensRequest = {
         view: this.model.tabularView
      };

      this.http.post<OAuthParameters>(this.CONTROLLER_OAUTH_PARAMS, paramsRequest, options)
         .pipe(
            map(oauthParams => Object.assign(authParams, oauthParams)),
            flatMap(oauthParams => this.oauthService.authorize(oauthParams)),
            map(tokens => Object.assign(tokensRequest, tokens)),
            flatMap(request => this.http.post<TabularView>(this.CONTROLLER_OAUTH_TOKENS, request, options))
         )
         .subscribe(
            (view) => {
               clearTimeout(timeout);
               this.showLoading = false;
               this.model.tabularView = view;
               this.initView();
            },
            () => {
               clearTimeout(timeout);
               this.showLoading = false;
            });
   }

   refreshView(cancelClicked: boolean = false): void {
      const timeout = setTimeout(() => {
         // show loading overlay only if cancel button doesn't exist
         // otherwise you should be able to cancel the loading
         this.showLoading = !this.cancelButtonExists;
      }, 1000);
      let params = new HttpParams().set("dataSource", this.model.dataSource);

      if(this.tableName != null) {
         params = params.set("tableName", this.tableName);
         params = params.set("runtimeId", this.runtimeId);
      }

      const options = { headers: this.headers, params: params };

      this.http.post<TabularView>(this.CONTROLLER_REFRESH_VIEW, this.model.tabularView, options)
         .subscribe((data) => {
            clearTimeout(timeout);
            this.showLoading = false;

            // if cancel button is clicked then don't refresh the view
            if(!cancelClicked) {
               this.model.tabularView = data;
               this.initView();
            }
         }, () => {
            clearTimeout(timeout);
            this.showLoading = false;
         }, () => {
            this.clearButtonLoading();
         });

      this.clearButtonClicks(this.model.tabularView != null ? this.model.tabularView.views : []);
   }

   validChanged(event: boolean) {
      this.valid = event;
      this.changeRef.detectChanges();
   }

   initView(): void {
      if(this.model.tabularView != null) {
         this.dependsOn = this.getDependsOn(this.model.tabularView.views);
         this.refreshButtonExists = this.hasRefreshButton(this.model.tabularView.views);
         this.cancelButtonExists = this.hasCancelButton(this.model.tabularView.views);
      }
   }

   viewChanged(view: TabularView[]): void {
      if(!this.refreshButtonExists && this.dependsOn.has(view[0].value)) {
         this.nestedViewChanged(this.model.tabularView, view[0], view[1]);
         this.refreshView();
      }
   }

   nestedViewChanged(currView: TabularView, changedView: TabularView, parent: TabularView) {
      for(let i = 0; i < currView.views.length; i++) {
         const cell = currView.views[i];

         if(currView == parent) {
            if(cell.row == changedView.row && cell.col == changedView.col) {
               currView.views[i] = changedView;
               break;
            }
         }

         this.nestedViewChanged(cell, changedView, parent);
      }
   }

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

   buttonClicked(button: TabularButton): void {
      if(button.type === "OAUTH") {
         this.authorize(button);
      }
      else {
         this.refreshView(button.type === "CANCEL");
      }
   }

   browseFunction: (path: string, property: string, all: boolean) => Observable<TreeNodeModel> =
      (path: string, property: string, all: boolean) =>
   {
      const params = new HttpParams()
         .set("dataSource", this.model.dataSource)
         .set("property", property)
         .set("path", path)
         .set("all", all + "");
      const options = { headers: this.headers, params };

      return this.http.post<TreeNodeModel>(this.CONTROLLER_BROWSE, this.model.tabularView, options);
   };

   clearButtonClicks(views: TabularView[]): void {
      for(let view of views) {
         if(view.type === "BUTTON") {
            view.button.clicked = false;
         }

         this.clearButtonClicks(view.views);
      }
   }

   clearButtonLoading(views: TabularView[] = this.model.tabularView.views): void {
      for(let view of views) {
         if(view.type === "BUTTON") {
            view.button.loading = false;
         }

         this.clearButtonLoading(view.views);
      }
   }

   updateNameValidation() {
      this.tableNameExists = !!this.tables &&
         this.tables.map((table) => table.name.toUpperCase())
            .indexOf(this.initTableName.toUpperCase()) != -1;
   }

   ok(): void {
      if(this.tables != null && this.initTableName != "") {
         this.model.tableName = this.initTableName;
      }

      this.onCommit.emit({model: this.model, controller: this.CONTROLLER_SOCKET});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: { model: this.model,
                                                    controller: this.CONTROLLER_SOCKET}});
   }
}

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
import { Component, EventEmitter, Input, OnInit, Output, AfterViewInit } from "@angular/core";
import {
   AbstractControl,
   UntypedFormBuilder, UntypedFormControl,
   UntypedFormGroup,
   FormGroupDirective,
   NgForm,
   Validators
} from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationShareSettingsModel } from "./presentation-share-settings-model";
import { Tool } from "../../../../../../shared/util/tool";
import { ContextHelp } from "../../../context-help";

@Searchable({
   route: "/settings/presentation/settings#sharing",
   title: "Social Sharing",
   keywords: [
      "em.settings", "em.settings.share", "em.settings.general", "em.settings.share.facebook",
      "em.settings.share.googleChat", "em.settings.share.linkedin", "em.settings.share.slack",
      "em.settings.share.twitter", "em.settings.share.openGraph"
   ]
})
@ContextHelp({
   route: "/settings/presentation/settings#sharing",
   link: "EMPresentationSocialSharing"
})
@Component({
   selector: "em-presentation-share-settings-view",
   templateUrl: "./presentation-share-settings-view.component.html",
   styleUrls: ["./presentation-share-settings-view.component.scss"]
})
export class PresentationShareSettingsViewComponent implements OnInit, AfterViewInit {
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   @Input()
   get model(): PresentationShareSettingsModel {
      return this._model;
   }

   set model(value: PresentationShareSettingsModel) {
      this._model = value;

      if(this.model) {
         this.form.get("emailEnabled").setValue(this.model.emailEnabled, {emitEvent: false});
         this.form.get("facebookEnabled").setValue(this.model.facebookEnabled, {emitEvent: false});
         this.form.get("googleChat").get("googleChatEnabled")
            .setValue(this.model.googleChatEnabled, {emitEvent: false});
         this.form.get("googleChat").get("googleChatUrl")
            .setValue(this.model.googleChatUrl, {emitEvent: false});
         this.form.get("linkedinEnabled").setValue(this.model.linkedinEnabled, {emitEvent: false});
         this.form.get("slack").get("slackEnabled")
            .setValue(this.model.slackEnabled, {emitEvent: false});
         this.form.get("slack").get("slackUrl")
            .setValue(this.model.slackUrl, {emitEvent: false});
         this.form.get("twitterEnabled").setValue(this.model.twitterEnabled, {emitEvent: false});
         this.form.get("linkEnabled").setValue(this.model.linkEnabled, {emitEvent: false});
         this.form.get("openGraphSiteName")
            .setValue(this.model.openGraphSiteName, {emitEvent: false});
         this.form.get("openGraphTitle").setValue(this.model.openGraphTitle, {emitEvent: false});
         this.form.get("openGraphDescription")
            .setValue(this.model.openGraphDescription, {emitEvent: false});
         this.form.get("openGraphImageUrl")
            .setValue(this.model.openGraphImageUrl, {emitEvent: false});

         if(this.model.googleChatEnabled) {
            this.form.get("googleChat").get("googleChatUrl").enable({emitEvent: false});
         }
         else {
            this.form.get("googleChat").get("googleChatUrl").disable({emitEvent: false});
         }

         if(this.model.slackEnabled) {
            this.form.get("slack").get("slackUrl").enable({emitEvent: false});
         }
         else {
            this.form.get("slack").get("slackUrl").disable({emitEvent: false});
         }
      }
   }

   form: UntypedFormGroup;
   googleChatErrorStateMatcher: ErrorStateMatcher;
   slackErrorStateMatcher: ErrorStateMatcher;

   private _model: PresentationShareSettingsModel;
   private subscribed = false;
   private validateHangoutsUrl = (control: AbstractControl) => {
      if(control && control.get("googleChatEnabled") && control.get("googleChatUrl") &&
         control.get("googleChatEnabled").value)
      {
         if(Validators.required(control.get("googleChatUrl"))) {
            return { "urlRequired": true };
         }
         else if(!(this.model.googleChatUrl.startsWith("https://") ||
            this.model.googleChatUrl.startsWith("http://")))
         {
            return { "invalidPrefix": true };
         }
      }

      return null;
   };
   private validateSlackUrl = (control: AbstractControl) => {
      if(control && control.get("slackEnabled") && control.get("slackUrl") &&
         control.get("slackEnabled").value)
      {
         if(Validators.required(control.get("slackUrl"))) {
            return { "urlRequired": true };
         }
         else if(!(this.model.slackUrl.startsWith("https://") ||
            this.model.slackUrl.startsWith("http://")))
         {
            return { "invalidPrefix": true };
         }
      }

      return null;
   };

   constructor(fb: UntypedFormBuilder, defaultErrorMatcher: ErrorStateMatcher) {
      this.form = fb.group({
         emailEnabled: [false],
         facebookEnabled: [false],
         googleChat: fb.group(
            {
               googleChatEnabled: [false],
               googleChatUrl: [""],
            },
            {validators: this.validateHangoutsUrl}
         ),
         linkedinEnabled: [false],
         slack: fb.group(
            {
               slackEnabled: [false],
               slackUrl: [""]
            },
            {validators: this.validateSlackUrl}
         ),
         twitterEnabled: [false],
         linkEnabled: [false],
         openGraphSiteName: ["", Validators.required],
         openGraphTitle: ["", Validators.required],
         openGraphDescription: ["", Validators.required],
         openGraphImageUrl: ["", Validators.required]
      });

      this.googleChatErrorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!form.form.controls.googleChat && !!form.form.controls.googleChat.errors &&
            (form.form.controls.googleChat.errors.urlRequired ||
            form.form.controls.googleChat.errors.invalidPrefix) ||
            defaultErrorMatcher.isErrorState(control, form)
      };

      this.slackErrorStateMatcher = {
         isErrorState: (control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null) =>
            !!form.form.controls.slack && !!form.form.controls.slack.errors &&
            (form.form.controls.slack.errors.urlRequired ||
            form.form.controls.slack.errors.invalidPrefix) ||
            defaultErrorMatcher.isErrorState(control, form)
      };
   }

   ngOnInit() {
   }

   ngAfterViewInit() {
      if(!this.subscribed) {
         this.subscribed = true;
         // IE may trigger a change event immediately on populating the form
         setTimeout(() => {
            this.form.valueChanges.subscribe(() => this.onModelChanged());
         }, 200);
      }
   }

   onModelChanged(): void {
      const omodel = {...this._model};
      this._model = {
         emailEnabled: this.form.get("emailEnabled").value,
         facebookEnabled: this.form.get("facebookEnabled").value,
         googleChatEnabled: this.form.get("googleChat").get("googleChatEnabled").value,
         googleChatUrl: this.form.get("googleChat").get("googleChatUrl").value,
         linkedinEnabled: this.form.get("linkedinEnabled").value,
         slackEnabled: this.form.get("slack").get("slackEnabled").value,
         slackUrl: this.form.get("slack").get("slackUrl").value,
         twitterEnabled: this.form.get("twitterEnabled").value,
         linkEnabled: this.form.get("linkEnabled").value,
         openGraphSiteName: this.form.get("openGraphSiteName").value,
         openGraphTitle: this.form.get("openGraphTitle").value,
         openGraphDescription: this.form.get("openGraphDescription").value,
         openGraphImageUrl: this.form.get("openGraphImageUrl").value
      };

      if(Tool.isEquals(omodel, this._model)) {
         return;
      }

      if(this._model.googleChatEnabled) {
         this.form.get("googleChat").get("googleChatUrl").enable({emitEvent: false});
      }
      else {
         this.form.get("googleChat").get("googleChatUrl").disable({emitEvent: false});
      }

      if(this._model.slackEnabled) {
         this.form.get("slack").get("slackUrl").enable({emitEvent: false});
      }
      else {
         this.form.get("slack").get("slackUrl").disable({emitEvent: false});
      }

      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.SHARE_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }

}

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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { Tool } from "../../../../../../shared/util/tool";
import { ContextHelp } from "../../../context-help";
import { Searchable } from "../../../searchable";
import { PresentationSettingsType } from "../presentation-settings-view/presentation-settings-type.enum";
import { PresentationSettingsChanges } from "../presentation-settings-view/presentation-settings-view.component";
import { PresentationPdfGenerationSettingsModel } from "./presentation-pdf-generation-settings-model";

@Searchable({
   route: "/settings/presentation/settings#pdf",
   title: "PDF Generation",
   keywords: ["em.settings", "em.settings.presentation", "em.settings.pdf"]
})
@ContextHelp({
   route: "/settings/presentation/settings#pdf",
   link: "EMPresentationPDFSettings"
})
@Component({
   selector: "em-presentation-pdf-generation-settings-view",
   templateUrl: "./presentation-pdf-generation-settings-view.component.html",
   styleUrls: ["./presentation-pdf-generation-settings-view.component.scss"]
})
export class PresentationPdfGenerationSettingsViewComponent {
   @Output() modelChanged = new EventEmitter<PresentationSettingsChanges>();

   private _model: PresentationPdfGenerationSettingsModel;
   private omodel: PresentationPdfGenerationSettingsModel;
   form: UntypedFormGroup;

   @Input() set model(model: PresentationPdfGenerationSettingsModel) {
      this._model = model;

      if(this.model) {
         this.omodel = {...model};
         this.initForm();
      }
   }

   get model(): PresentationPdfGenerationSettingsModel {
      return this._model;
   }

   constructor(private formBuilder: UntypedFormBuilder) {
   }

   private initForm() {
      this.form = this.formBuilder.group({
         compressText: [this.model.compressText],
         compressImage: [this.model.compressImage],
         asciiOnly: [this.model.asciiOnly],
         mapSymbols: [this.model.mapSymbols],
         pdfEmbedCmap: [this.model.pdfEmbedCmap],
         pdfEmbedFont: [this.model.pdfEmbedFont],
         cidFontPath: [this.model.cidFontPath],
         afmFontPath: [this.model.afmFontPath],
         openFirst: [this.model.openFirst],
         browserEmbedPdf: [this.model.browserEmbedPdf],
         pdfHyperlinks: [this.model.pdfHyperlinks],
      });

      // IE may trigger a change event immediately on populating the form
      setTimeout(() => {
         this.form.controls["compressText"].valueChanges.subscribe((value) => {
            this.model.compressText = value;
            this.onModelChanged();
         });

         this.form.controls["compressImage"].valueChanges.subscribe((value) => {
            this.model.compressImage = value;
            this.onModelChanged();
         });

         this.form.controls["asciiOnly"].valueChanges.subscribe((value) => {
            this.model.asciiOnly = value;
            this.onModelChanged();
         });

         this.form.controls["mapSymbols"].valueChanges.subscribe((value) => {
            this.model.mapSymbols = value;
            this.onModelChanged();
         });

         this.form.controls["pdfEmbedCmap"].valueChanges.subscribe((value) => {
            this.model.pdfEmbedCmap = value;
            this.onModelChanged();
         });

         this.form.controls["pdfEmbedFont"].valueChanges.subscribe((value) => {
            this.model.pdfEmbedFont = value;
            this.onModelChanged();
         });

         this.form.controls["cidFontPath"].valueChanges.subscribe((value) => {
            this.model.cidFontPath = value;
            this.onModelChanged();
         });

         this.form.controls["afmFontPath"].valueChanges.subscribe((value) => {
            this.model.afmFontPath = value;
            this.onModelChanged();
         });

         this.form.controls["openFirst"].valueChanges.subscribe((value) => {
            this.model.openFirst = value;
            this.onModelChanged();
         });

         this.form.controls["browserEmbedPdf"].valueChanges.subscribe((value) => {
            this.model.browserEmbedPdf = value;
            this.onModelChanged();
         });

         this.form.controls["pdfHyperlinks"].valueChanges.subscribe((value) => {
            this.model.pdfHyperlinks = value;
            this.onModelChanged();
         });
      }, 200);
   }

   onModelChanged() {
      if(Tool.isEquals(this.omodel, this.model)) {
         return;
      }

      this.form.updateValueAndValidity();
      this.modelChanged.emit({
         model: this.model,
         modelType: PresentationSettingsType.PDF_GENERATION_SETTINGS_MODEL,
         valid: this.form.valid
      });
   }
}

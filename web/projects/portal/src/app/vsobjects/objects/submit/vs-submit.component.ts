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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { TinyColor } from "@ctrl/tinycolor";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { VSSubmitModel } from "../../model/output/vs-submit-model";
import { DebounceService } from "../../../widget/services/debounce.service";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { VSSubmitEvent } from "../../event/vs-submit-event";
import { FormInputService } from "../../util/form-input.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { GlobalSubmitService } from "../../util/global-submit.service";

@Component({
   selector: "vs-submit",
   templateUrl: "vs-submit.component.html",
   styleUrls: ["vs-submit.component.scss"]
})
export class VSSubmit extends NavigationComponent<VSSubmitModel> implements OnChanges {
   @Input() selected: boolean = false;
   @Output() submitClicked = new EventEmitter();
   @ViewChild("submitButton") button: ElementRef;

   vAlign: string;
   private padding: number = 8;
   focusColor: string = "rgba(218,218,218,0.5)";
   focus: boolean = false;

   get hasHyperlinks(): boolean {
      const _model = this.model as any;
      return !!_model.hyperlinks && !!_model.hyperlinks.length;
   }

   constructor(private socket: ViewsheetClientService,
               private debounceService: DebounceService,
               private formInputService: FormInputService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private globalSubmitService: GlobalSubmitService)
   {
      super(null, zone, context, dataTipService);
   }

   onClick(event: MouseEvent) {
      if(!this.viewer) {
         event.preventDefault();
      }

      if(this.model.refresh && this.model.enabled) {
         this.submitClicked.emit(this.model.absoluteName);
      }

      if(this.viewer) {
         this.globalSubmitService.submitGlobal(this.model.absoluteName);
         let target = "/events/onclick/" + this._model.absoluteName + "/" + event.offsetX +
            "/" + event.offsetY;
         let pendingValues = this.formInputService.getPendingValues().filter((v) => v.value != null);
         const submitEvent = new VSSubmitEvent(pendingValues);
         this.formInputService.clear();
         this.debounceService.debounce(
            `vs-submit.click.${this.model.absoluteName}`,
            (evt, socket) => socket.sendEvent(target, submitEvent),
            600, [event, this.socket]);
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      // @by changhongyang, 2017-10-25, button tags do not respect vertical-align so we must
      // calculate padding to position the text
      let fontSize = "";

      const attributes = this.model.objectFormat.font.split(" ");

      attributes.forEach((attribute) => {
         if(attribute.indexOf("px") != -1) {
            fontSize = attribute;
         }
      });

      fontSize = fontSize.substr(0, fontSize.indexOf("px"));

      if(this.model.objectFormat.vAlign === "top" || this.model.objectFormat.vAlign === "bottom") {
         this.vAlign = this.model.objectFormat.height - Number(fontSize) - this.padding + "px";
      }

      if(changes["model"]) {
         this.initFocusColor();
      }
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      if(!!this.button) {
         this.button.nativeElement.focus();

         if(key == NavigationKeys.SPACE) {
            this.button.nativeElement.click();
         }
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      // Do nothing
   }

   private initFocusColor() {
      if(!this.model) {
         return;
      }

      if(this.model.objectFormat.border) {
         const borders = [this.model.objectFormat.border.top, this.model.objectFormat.border.right,
            this.model.objectFormat.border.bottom, this.model.objectFormat.border.left];

         for(let border of borders) {
            if(border) {
               let color = border.split(" ")[2];

               if(color) {
                  this.focusColor = new TinyColor(color).setAlpha(0.5).toRgbString();
                  return;
               }
            }
         }
      }

      if(this.model.objectFormat.background) {
         this.focusColor = new TinyColor(this.model.objectFormat.background)
            .setAlpha(0.5).toRgbString();
      }
   }
}

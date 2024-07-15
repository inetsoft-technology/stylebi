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
import { ChangeDetectionStrategy, Component, ContentChild, Input } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ToggleAnnotationStatusEvent } from "../../event/annotation/toggle-annotation-status-event";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";

@Component({
   selector: "vs-hidden-annotation",
   templateUrl: "vs-hidden-annotation.component.html",
   styleUrls: ["vs-hidden-annotation.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSHiddenAnnotation {
   @Input()
   set annotations(annotations: VSAnnotationModel[]) {
      this.content = annotations &&
         annotations.filter((annotation) => annotation != null)
                    .filter((annotation) => annotation.hidden)
                    .map((annotation) => annotation.contentModel.content)
                    .join("\n");
   }

   @ContentChild("customIcon") transcluded: any;

   public content: string;

   constructor(protected viewsheetClient: ViewsheetClientService) {
   }

   get tooltipClass(): string[] {
      return ["hidden__annotation-tooltip", "compact-p", "bg-white-inet"];
   }

   /**
    * When the hidden annotation icon is clicked we should show all annotations
    */
   public toggleAnnotationStatus(): void {
      const event = new ToggleAnnotationStatusEvent(true);
      this.viewsheetClient.sendEvent("/events/annotation/toggle-status", event);
   }
}

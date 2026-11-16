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
import { NgClass } from "@angular/common";
import { ChangeDetectionStrategy, Component, ContentChild, Input } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ToggleAnnotationStatusEvent } from "../../event/annotation/toggle-annotation-status-event";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";
import { annotationTooltipFill } from "./annotation-tooltip-style";
import { TooltipDirective } from "../../../widget/tooltip/tooltip.directive";


@Component({
    selector: "vs-hidden-annotation",
    templateUrl: "vs-hidden-annotation.component.html",
    styleUrls: ["vs-hidden-annotation.component.scss"],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TooltipDirective, NgClass]
})
export class VSHiddenAnnotation {
   @Input()
   set annotations(annotations: VSAnnotationModel[]) {
      const hidden = (annotations || [])
         .filter((annotation) => annotation != null)
         .filter((annotation) => annotation.hidden);

      // the icon is shown only when there is content to show
      this.content = hidden.map((annotation) => annotation.contentModel.content).join("\n");

      // one card per annotation: trusted content keeps the author's inline colors, drawn on the
      // annotation's own fill (a joined string has no single fill)
      this.cards = hidden.map((annotation) => {
         const fill = annotationTooltipFill(annotation);
         return {
            html: this.domSanitizer.bypassSecurityTrustHtml(annotation.contentModel.content),
            fill: fill
         };
      });
   }

   @ContentChild("customIcon") transcluded: any;

   public content: string;
   public cards: { html: SafeHtml, fill: string }[] = [];

   constructor(protected viewsheetClient: ViewsheetClientService,
               private domSanitizer: DomSanitizer)
   {
   }

   get tooltipClass(): string[] {
      return ["hidden__annotation-tooltip", "compact-p"];
   }

   /**
    * When the hidden annotation icon is clicked we should show all annotations
    */
   public toggleAnnotationStatus(): void {
      const event = new ToggleAnnotationStatusEvent(true);
      this.viewsheetClient.sendEvent("/events/annotation/toggle-status", event);
   }
}

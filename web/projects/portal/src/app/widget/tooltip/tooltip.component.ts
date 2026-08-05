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
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   Input,
   TemplateRef
} from "@angular/core";
import { NgTemplateOutlet, NgClass } from "@angular/common";
import {
   buildChromePaths,
   ChromeGeometry,
   TAIL_LENGTH,
   TailSide,
   TOOLTIP_INSET
} from "./tooltip-tail-placement";

/**
 * Component used to render tooltips.
 *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * !!!! NOT INTENDED for direct use in the template. !!!!!
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 */
@Component({
    selector: "w-tooltip",
    templateUrl: "tooltip.component.html",
    styleUrls: ["tooltip.component.scss"],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgTemplateOutlet, NgClass]
})
export class TooltipComponent {
   @Input() content: string | TemplateRef<any>;
   @Input() tooltipCSS: string | string[] | Set<string>;
   @Input() tailSide: TailSide | null = null;
   @Input() tailOffset = 0;
   @Input() boxSize: { width: number, height: number } | null = null;
   /** chrome overhang: tail length minus box inset */
   readonly chromeInset = TOOLTIP_INSET - TAIL_LENGTH;

   constructor(private changeRef: ChangeDetectorRef) {
   }

   updateView() {
      this.changeRef.detectChanges();
   }

   contentIsTemplate(): boolean {
      return this.content instanceof TemplateRef;
   }

   get chrome(): ChromeGeometry | null {
      return !!this.tailSide && !!this.boxSize
         ? buildChromePaths(this.boxSize.width, this.boxSize.height, this.tailSide, this.tailOffset)
         : null;
   }
}

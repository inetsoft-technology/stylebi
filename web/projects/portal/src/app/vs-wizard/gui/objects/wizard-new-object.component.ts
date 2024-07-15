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
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   NgZone,
   Output,
   ViewChild,
   ChangeDetectorRef,
   OnChanges,
   SimpleChanges
} from "@angular/core";
import { Point } from "../../../common/data/point";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { AssemblyType } from "../../../composer/gui/vs/assembly-type";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { WizardNewObjectModel } from "./wizard-new-object-model";
import { Rectangle } from "../../../common/data/rectangle";
import { VSWizardConstants } from "../../model/vs-wizard-constants";

interface InsertObject {
   type: AssemblyType;
   point: Point;
}

@Component({
   selector: "wizard-new-object",
   templateUrl: "wizard-new-object.component.html",
   styleUrls: ["wizard-new-object.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WizardNewObject implements OnChanges {
   @Input() model: WizardNewObjectModel;
   @Input() componentWizardEnable: boolean;
   @Output() toComponentWizard = new EventEmitter<Point>();
   @Output() doInsertObject = new EventEmitter<InsertObject>();
   @ViewChild(FixedDropdownDirective) dropdownRef: FixedDropdownDirective;
   zIndex: number = 1;
   private boundingRect: ClientRect = null;

   constructor(public viewsheetClient: ViewsheetClientService,
               private debounceService: DebounceService,
               private changeRef: ChangeDetectorRef,
               private zone: NgZone)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["model"]) {
         this.boundingRect = null;
      }
   }

   onClickCell(): void {
      this.toComponentWizard.emit(new Point(this.model.bounds.x, this.model.bounds.y));
   }

   onMouseEnter(): void {
      this.debounceService.cancel("wizard new object");
   }

   onMouseLeave(): void {
      this.debounceService.debounce("wizard new object", () => {
         this.zone.run(() => {
            this.model.visible = false;
         });
      }, 200, []);
   }

   leaveMenu(): void {
      if(this.dropdownRef != null) {
         this.dropdownRef.close();
      }
   }

   insertObject(type: AssemblyType): void {
      this.doInsertObject.emit({
         type, point: new Point(this.model.bounds.x, this.model.bounds.y)
      });
   }

   insertImage(): void {
      this.insertObject(AssemblyType.IMAGE_ASSET);
   }

   insertText(): void {
      this.insertObject(AssemblyType.TEXT_ASSET);
   }

   onMousemove(event: MouseEvent) {
      if(this.boundingRect == null) {
         this.boundingRect = (<any> event.target).getBoundingClientRect();
      }

      // if inside inner area, raise the new-object above wizard objects so it won't disappear
      // when mouse is over a wizard-object
      if(event.pageX > this.boundingRect.left + 6 && event.pageX < this.boundingRect.right - 6 &&
         event.pageY > this.boundingRect.top + 6 && event.pageY < this.boundingRect.bottom - 6)
      {
         if(this.zIndex < 9999) {
            this.zIndex = 9999;
            this.changeRef.detectChanges();
         }
      }
      else {
         if(this.zIndex === 9999) {
            this.zIndex = 999;
            this.changeRef.detectChanges();
         }
      }
   }
}

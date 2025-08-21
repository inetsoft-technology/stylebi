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
import { BreakpointObserver } from "@angular/cdk/layout";
import {
   AfterContentChecked,
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   Output,
   Renderer2,
   SimpleChanges,
   ViewEncapsulation
} from "@angular/core";
import { debounceTime } from "rxjs/operators";
import { Subject } from "rxjs";
import { TopScrollSupport } from "../../../top-scroll/top-scroll-support";

const SMALL_WIDTH_BREAKPOINT = 720;

@Component({
   selector: "em-editor-panel",
   templateUrl: "./editor-panel.component.html",
   styleUrls: ["./editor-panel.component.scss"],
   encapsulation: ViewEncapsulation.None,
   providers: [ TopScrollSupport ],
   host: { "class": "editor-panel" } // eslint-disable-line @angular-eslint/no-host-metadata-property
})
export class EditorPanelComponent implements OnChanges, AfterViewInit, AfterContentChecked {
   @Input() applyVisible = true;
   @Input() applyLabel = "_#(js:Apply)";
   @Input() resetLabel = "_#(js:Reset)";
   @Input() cancelLabel = "_#(js:Cancel)";
   @Input() applyDisabled = false;
   @Input() resetDisabled = false;
   @Input() contentClass = "";
   @Input() contentStyle: { [key: string]: string; } = {};
   @Input() actionsClass = "";
   @Input() actionsStyle: { [key: string]: string; } = {};
   @Input() resetVisible = true;
   @Output() applyClicked = new EventEmitter<any>();
   @Output() resetClicked = new EventEmitter<any>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   private applyClickSubject = new Subject<Event>();

   constructor(private hostElement: ElementRef, private renderer: Renderer2,
               private breakpointObserver: BreakpointObserver,
               private scrollSupport: TopScrollSupport)
   {
      this.applyClickSubject.pipe(
         debounceTime(200)
      ).subscribe(event => {
         this.applyClicked.emit(event);
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.applyDisabled) {
         this.unsavedChanges.emit(!this.applyDisabled);
      }
   }

   ngAfterViewInit(): void {
      const host = this.hostElement.nativeElement;

      if(host) {
         const parent = host.parentElement;

         if(parent) {
            this.renderer.addClass(parent, "editor-panel-parent");
         }
      }
   }

   ngAfterContentChecked(): void {
      if(!this.isScreenSmall() || !this.hostElement || !this.hostElement.nativeElement) {
         return;
      }

      const host = this.hostElement.nativeElement;
      let scrollable;

      if(this.contentClass && this.contentClass.indexOf("tabbed-editor-panel-content") >= 0) {
         scrollable = host.querySelector(
            ".editor-panel-content > .mat-tab-group > .mat-tab-body-wrapper > " +
            ".mat-tab-body-active > .mat-tab-body-content");
      }
      else {
         scrollable = host.querySelector(".editor-panel-content");
      }

      if(!!scrollable) {
         this.scrollSupport.attach(scrollable);
      }
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   handleClick(event: MouseEvent) {
      this.applyDisabled = true;
      this.applyClickSubject.next(event);
   }
}

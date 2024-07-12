/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewEncapsulation
} from "@angular/core";
import Split from "split.js";

@Component({
   selector: "split-pane",
   templateUrl: "split-pane.component.html",
   styleUrls: ["split-pane.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class SplitPane implements OnInit, OnChanges {
   @Input() sizes: number[];
   @Input() dragEnable: boolean = true;
   @Input() minSize: number = 100;
   @Input() gutterSize: number = 6;
   @Input() snapOffset: number = 30;
   @Input() direction: "horizontal" | "vertical" = "horizontal";
   @Input() fullWidth: boolean = false;
   @Input() displayed: boolean = true;
   @Output() onDrag = new EventEmitter<void>();
   @Output() onDragStart = new EventEmitter<void>();
   @Output() onDragEnd = new EventEmitter<void>();
   splitInstance: Split.Instance;

   constructor(private element: ElementRef) {
   }

   ngOnInit(): void {
      this.initializeSplitInstance();
   }

   ngOnChanges(changes: SimpleChanges): void {
      const displayedChanges = changes["displayed"];

      if(displayedChanges != null && displayedChanges.previousValue === false
         && displayedChanges.currentValue === true
         || changes["gutterSize"] && !!this.splitInstance)
      {
         this.resetSplitInstance();
      }

      if(changes["sizes"] && this.splitInstance) {
         this.setSizes(this.sizes);
      }
   }

   private initializeSplitInstance(): void {
      let children: HTMLElement[] = [];
      let parent: HTMLElement = this.element.nativeElement;

      if(parent.querySelector(".split-pane-container")) {
         parent = parent.querySelector(".split-pane-container");

         if(this.direction === "vertical") {
            parent.classList.add("vertical");
         }
         else {
            parent.classList.add("horizontal");
         }

         if(this.fullWidth) {
            parent.classList.add("full-width");
         }
      }

      for(let i = 0; i < parent.childNodes.length; i++) {
         let childNode = parent.childNodes.item(i);

         if(childNode.nodeType === 1) { // element
            let childElement = <HTMLElement> childNode;
            childElement.classList.add("split");
            children.push(childElement);
         }
      }

      if(!this.sizes) {
         let size: number = Math.floor(100 / children.length);
         this.sizes = [];

         for(let i = 0; i < children.length; i++) {
            this.sizes.push(size);
         }
      }

      const config: Split.Options = {
         sizes: this.sizes,
         minSize: this.minSize,
         gutterSize: this.gutterSize,
         snapOffset: this.snapOffset,
         direction: this.direction,
         onDrag: () => {
            if(!this.dragEnable) {
               this.setSizes(this.sizes);
            }

            this.onDrag.emit();
         },
         onDragStart: () => {
            this.onDragStart.emit();
         },
         onDragEnd: () => {
            this.onDragEnd.emit();
         }
      };

      this.splitInstance = Split(children, config);
   }

   private resetSplitInstance(): void {
      let sizes: number[] = null;

      if(this.splitInstance != null) {
         sizes = this.getSizes();
         this.splitInstance.destroy();
         this.splitInstance = null;
      }

      this.initializeSplitInstance();

      // Need to do an isFinite check because if the split pane is initialized with display: none,
      // the sizes will be Infinity.
      if(sizes != null && sizes.every(s => Number.isFinite(s))) {
         this.setSizes(sizes);
      }
   }

   public setSizes(newSizes: number[]): void {
      this.splitInstance.setSizes(newSizes);
   }

   public getSizes(): number[] {
      return this.splitInstance.getSizes();
   }

   public collapse(index: number): void {
      return this.splitInstance.collapse(index);
   }
}

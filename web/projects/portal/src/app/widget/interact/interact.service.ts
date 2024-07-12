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
import { EventEmitter, Injectable, NgZone, OnDestroy } from "@angular/core";
import { Point } from "../../common/data/point";
import { GuiTool } from "../../common/util/gui-tool";
import { DomService } from "../dom-service/dom.service";
import { DebounceService } from "../services/debounce.service";
import { InteractContainerDirective } from "./interact-container.directive";
import { InteractableDirective } from "./interactable.directive";

@Injectable()
export class InteractService implements OnDestroy {
   private container: InteractContainerDirective;
   private children: InteractableDirective[] = [];

   private interactables: any[];
   private pendingUpdate: boolean = false;

   onSnap = new EventEmitter<{x: number, y: number}>();
   private currentSnap: {x: number, y: number} = null;
   private _id = new Date().getTime();

   constructor(private zone: NgZone,
               private debounceService: DebounceService,
               private domService: DomService) {
   }

   get interact(): any {
      let obj: any;
      this.zone.runOutsideAngular(() => obj = require("interactjs"));
      return obj;
   }

   ngOnDestroy(): void {
      this.container = null;
      this.children = [];

      const _interactables = this.interactables;
      this.interactables = null;

      this.zone.runOutsideAngular(() => {
         if(_interactables) {
            _interactables.forEach((interactable) => interactable.unset());
         }
      });
   }

   public setContainer(container: InteractContainerDirective): void {
      this.container = container;
   }

   public addInteractable(interactable: InteractableDirective): void {
      this.children.push(interactable);
   }

   public removeInteractable(interactable: InteractableDirective): void {
      this.children = this.children.filter((i) => i !== interactable);
   }

   notify(): void {
      if(!this.pendingUpdate) {
         this.pendingUpdate = true;
         this.debounceService.debounce("interact-createInteractables" + this._id, () => {
            Promise.resolve(null).then(() => this.createInteractables());
         }, 200, []);
      }
   }

   private createInteractables(): void {
      this.zone.runOutsideAngular(() => {
         if(this.interactables) {
            this.container.element.nativeElement.querySelectorAll("[data-interact-draggable]")
               .forEach((elem) => elem.removeAttribute("data-interact-draggable"));
            this.interactables.forEach((interactable) => interactable.unset());
            this.interactables = null;
         }

         this.interactables = [];
         const draggables = new Map<string, InteractableDirective[]>();

         this.children.forEach((child) => {
            if(child.element.nativeElement.offsetParent === null) {
               // element is hidden, skip it
               return;
            }

            if(child.interactableDraggable) {
               const group = child.interactableDraggableGroup;
               let draggableArray = draggables.get(group);

               if(!draggableArray) {
                  draggableArray = [];
                  draggables.set(group, draggableArray);
               }

               draggableArray.push(child);
            }
            else if(child.interactableDropzone || child.interactableResizable) {
               const interactable = this.interact(
                  child.element.nativeElement,
                  this.removeUndefinedProperties({
                     ignoreFrom: child.interactableIgnoreFrom,
                     allowFrom: child.interactableAllowFrom,
                     context: this.container.element.nativeElement
                  }));

               if(child.interactableDropzone) {
                  this.makeDropzone(child, interactable);
               }

               if(child.interactableResizable) {
                  this.makeResizable(child, interactable);
               }

               this.interactables.push(interactable);
            }
         });

         if(this.container?.composited) {
            draggables.forEach((value, key) => {
               const compositedDraggables = value.reduce((acc, draggable) => {
                  if(draggable.composited) {
                     acc.push(draggable);
                  }
                  else {
                     this.createDraggable([draggable]);
                  }

                  return acc;
               }, []);

               if(compositedDraggables.length > 1) {
                  this.createCompositedDraggable(compositedDraggables, key);
               }
               else {
                  this.createDraggable(compositedDraggables);
               }
            });
         }
         else {
            draggables.forEach((value) => this.createDraggable(value));
         }
      });

      this.pendingUpdate = false;
   }

   private makeResizable(element: InteractableDirective, interactable: any): void {
      interactable.resizable(this.removeUndefinedProperties({
         autoScroll: false,
         margin: element.resizableMargin,
         preserveAspectRatio: element.resizablePreserveAspectRatio,
         edges: {
            top: element.resizableTopEdge,
            left: element.resizableLeftEdge,
            bottom: element.resizableBottomEdge,
            right: element.resizableRightEdge
         },
         snap: this.createSnapConfig([element]),
         // currently broken: https://github.com/taye/interact.js/issues/484
         // restrict: {
         //    restriction: child.resizableRestriction,
         //    elementRect: child.resizableElementRect
         // },
         invert: "negate",
         onstart: (event: any) => this.zone.run(() => element.handleResizeStart(event)),
         onmove: (event: any) => this.zone.run(() => element.handleResizeMove(event)),
         onend: (event: any) => {
            this.zone.run(() => element.handleResizeEnd(event));

            if(this.currentSnap) {
               this.currentSnap = null;
               this.onSnap.emit(this.currentSnap);
            }

            // after resize, the selectionBounds changed so need to recreate snap config
            this.notify();
         }
      }));
   }

   private makeDropzone(element: InteractableDirective, interactable: any): void {
      interactable.dropzone(this.removeUndefinedProperties({
         accept: element.dropzoneAccept,
         overlap: element.dropzoneOverlap,
         ondropactivate: (event: any) => this.zone.run(() => element.handleDropzoneActivate(event)),
         ondropdeactivate: (event: any) => this.zone.run(() => element.handleDropzoneDeactivate(event)),
         ondragenter: (event: any) => this.zone.run(() => element.handleDropzoneEnter(event)),
         ondragleave: (event: any) => this.zone.run(() => element.handleDropzoneLeave(event)),
         ondragmove: (event: any) => this.zone.run(() => element.handleDropzoneMove(event)),
         ondrop: (event: any) => this.zone.run(() => element.handleDropzoneDrop(event))
      }));
   }

   /**
    * Make each InteractableDirective draggable and have each event handler call their
    * sibling's event handlers for tethering them together
    */
   private createCompositedDraggable(draggables: InteractableDirective[], group: string): void {
      const ignoreFrom: Map<string, boolean> = new Map<string, boolean>();
      const allowFrom: Map<string, boolean> = new Map<string, boolean>();
      let resizeable: InteractableDirective;

      draggables.forEach((draggable) => {
         draggable.element.nativeElement.setAttribute("data-interact-draggable", group);

         if(draggable.interactableIgnoreFrom) {
            draggable.interactableIgnoreFrom.split(",").forEach(v => ignoreFrom.set(v, true));
         }

         if(draggable.interactableAllowFrom) {
            draggable.interactableAllowFrom.split(",").forEach(v => allowFrom.set(v, true));
         }

         if(draggable.compositeResizeable) {
            resizeable = draggable;
         }
      });

      const ignoreFromStr: string = Array.from(ignoreFrom.keys()).join(",");
      const allowFromStr: string = Array.from(allowFrom.keys()).join(",");

      const interactable = this.interact(`[data-interact-draggable='${group}']`,
                                    this.removeUndefinedProperties({
                                       ignoreFrom: ignoreFromStr ? ignoreFromStr : null,
                                       allowFrom: allowFromStr ? allowFromStr : null,
                                       context: this.container.element.nativeElement
                                    }));

      interactable.draggable({
         autoSize: false,
         snap: this.createSnapConfig(draggables),
         restrict: {
            restriction: this.container.draggableRestriction,
            elementRect: this.container.draggableElementRect
         },
         onstart: (event: any) => this.zone.run(() => {
            draggables.forEach((draggable) => draggable.handleDragStart(event));
         }),
         onmove: (event: any) => this.zone.run(() => {
            draggables.forEach((draggable) => draggable.handleDragMove(event));
         }),
         onend: (event: any) => {
            this.disableNextClick(event);
            this.zone.run(() => draggables.forEach((draggable) => draggable.handleDragEnd(event)));

            if(this.currentSnap) {
               this.currentSnap = null;
               this.onSnap.emit(this.currentSnap);
            }
         }
      }).actionChecker((pointer, event, action) => {
         if(event.button != 0) {
            return null;
         }

         return action;
      });

      if(resizeable) {
         this.makeResizable(resizeable, interactable);
      }

      this.interactables.push(interactable);
   }

   /**
    * Make each InteractableDirective draggable independent of each other
    */
   private createDraggable(draggables: InteractableDirective[]): void {
      draggables.forEach((draggable) => {
         const dragElement = draggable.element.nativeElement;
         dragElement.setAttribute("data-interact-draggable", "true");

         const interactable = this.interact(dragElement, this.removeUndefinedProperties({
            ignoreFrom: draggable.interactableIgnoreFrom,
            allowFrom: draggable.interactableAllowFrom,
            context: this.container.element.nativeElement
         }));

         interactable.draggable({
            autoSize: false,
            restrict: {
               restriction: draggable.draggableRestriction || this.container.draggableRestriction
                  || this.container.element.nativeElement,
               elementRect: this.container.draggableElementRect
            },
            snap: this.createSnapConfig(draggables),
            onstart: (event: any) => this.zone.run(() => {
               draggable.handleDragStart(event);
            }),
            onmove: (event: any) => this.zone.run(() => {
               draggable.handleDragMove(event);
            }),
            onend: (event: any) => {
               this.disableNextClick(event);
               this.zone.run(() => draggable.handleDragEnd(event));

               if(this.currentSnap) {
                  this.currentSnap = null;
                  this.onSnap.emit(this.currentSnap);
               }
            }
         }).actionChecker((pointer, event, action) => {
            if(event.button != 0) {
               return null;
            }

            return action;
         });

         if(draggable.interactableDropzone) {
            this.makeDropzone(draggable, interactable);
         }

         if(draggable.interactableResizable) {
            this.makeResizable(draggable, interactable);
         }

         this.interactables.push(interactable);
      });
   }

   private createSnapConfig(draggables?: InteractableDirective[],
                            relativeToContainer: boolean = false): any
   {
      if(!this.container.snapToGuides && !this.container.snapToGrid) {
         return null;
      }

      const snap: any = {
         relativePoints: [
            { x: 0, y: 0 }
         ]
      };

      const dragging = !!draggables && draggables.length > 0;
      let draggableMap: Map<any, {x: number, y: number}> = null;
      let selectionBounds = null;

      if(dragging) {
         draggableMap = new Map<any, {x: number, y: number}>();

         selectionBounds = draggables.reduce((selection, draggable) => {
            const element = draggable.element.nativeElement;
            const bounds = GuiTool.getElementRect(element);

            if(bounds) {
               selection.top = Math.min(bounds.top, selection.top);
               selection.left = Math.min(bounds.left, selection.left);
               selection.bottom = Math.max(bounds.bottom, selection.bottom);
               selection.right = Math.max(bounds.right, selection.right);
            }

            return selection;
         }, { top: Infinity, left: Infinity, bottom: -Infinity, right: -Infinity });

         draggables.forEach((draggable) => {
            const element = draggable.element.nativeElement;
            const bounds = GuiTool.getElementRect(element);
            draggableMap.set(element, {
               x: bounds.left - selectionBounds.left,
               y: bounds.top - selectionBounds.top
            });
         });
      }

      snap.targets = [(x: number, y: number, interaction: any) => {
         let result: any;
         let currentX = x;
         let currentY = y;
         let selectionOffsetX = 0;
         let selectionOffsetY = 0;
         const resizingLeft = interaction && interaction.prepared && interaction.prepared.edges &&
            !!interaction.prepared.edges.left;
         const resizingRight = interaction && interaction.prepared && interaction.prepared.edges &&
            !!interaction.prepared.edges.right;
         const resizingTop = interaction && interaction.prepared && interaction.prepared.edges &&
            !!interaction.prepared.edges.top;
         const resizingBottom = interaction && interaction.prepared && interaction.prepared.edges &&
            !!interaction.prepared.edges.bottom;
         const moving = !resizingLeft && !resizingRight && !resizingTop && !resizingBottom;

         // this ensures that we are snapping to the bounding rectangle of the entire
         // selection
         if(dragging && interaction.element) {
            const offset = draggableMap.get(interaction.element);

            if(offset) {
               currentX -= offset.x;
               currentY -= offset.y;
               selectionOffsetX = offset.x;
               selectionOffsetY = offset.y;
            }
         }

         const containerRect = GuiTool.getElementRect(this.container.element.nativeElement);
         const containerX = relativeToContainer ? 0 : containerRect.left;
         const containerY = relativeToContainer ? 0 : containerRect.top;
         // resize border adds 2 pixels around the obj, so we subtract it.
         // otherwise the positions will be 2, 22, 42, ...
         const borderAdj = draggables ? draggables[0].resizeAdjustment : 2;

         if(this.container.snapToGuides) {
            const selectionWidth = selectionBounds
               ? selectionBounds.right - selectionBounds.left : 0;
            const selectionHeight = selectionBounds
               ? selectionBounds.bottom - selectionBounds.top : 0;

            let closestHGuide: number = null;
            let hGuideDistance: number = Number.MAX_VALUE;
            let closestVGuide: number = null;
            let vGuideDistance: number = Number.MAX_VALUE;
            const snappedTo: {x: number, y: number} = {x: undefined, y: undefined};
            const scrollTop = this.container.element.nativeElement.scrollTop;
            const scrollLeft = this.container.element.nativeElement.scrollLeft;

            if(this.container.snapHorizontalGuides) {
               this.container.snapHorizontalGuides.forEach((hGuide: number) => {
                  if(hGuide >= 0) {
                     // check top of selection
                     if(moving || resizingTop) {
                        let distance = Math.abs(hGuide - (currentY + scrollTop - containerY));

                        if(distance <= this.container.snapGuideRange && distance < hGuideDistance) {
                           closestHGuide = hGuide;
                           hGuideDistance = distance;
                           snappedTo.y = hGuide;
                        }
                     }

                     // check bottom of selection
                     if(moving || resizingBottom) {
                        let distance = Math.abs(hGuide - (currentY + selectionHeight + scrollTop
                                                          - containerY));

                        if(distance <= this.container.snapGuideRange && distance < hGuideDistance) {
                           const offsetGuide = hGuide - selectionHeight;
                           closestHGuide = offsetGuide;
                           hGuideDistance = distance;
                           snappedTo.y = hGuide;
                        }
                     }
                  }
                  // check middle of selection
                  else if(moving) {
                     hGuide = -hGuide;

                     let distance = Math.abs(hGuide - (currentY + selectionHeight / 2 +
                                                       scrollTop - containerY));

                     if(distance <= this.container.snapGuideRange && distance < hGuideDistance) {
                        const offsetGuide = hGuide - selectionHeight / 2;
                        closestHGuide = offsetGuide;
                        hGuideDistance = distance;
                        snappedTo.y = hGuide;
                     }
                  }
               });
            }

            if(this.container.snapVerticalGuides) {
               this.container.snapVerticalGuides.forEach((vGuide: number) => {
                  if(vGuide >= 0) {
                     // check left of selection
                     if(moving || resizingLeft) {
                        let distance = Math.abs(vGuide - (currentX + scrollLeft - containerX));

                        if(distance <= this.container.snapGuideRange && distance < vGuideDistance) {
                           closestVGuide = vGuide;
                           vGuideDistance = distance;
                           snappedTo.x = vGuide;
                        }
                     }

                     // check right of selection
                     if(moving || resizingRight) {
                        let distance = Math.abs(vGuide - (currentX + selectionWidth + scrollLeft
                                                          - containerX));

                        if(distance <= this.container.snapGuideRange && distance < vGuideDistance) {
                           const offsetGuide = vGuide - selectionWidth;
                           closestVGuide = offsetGuide;
                           vGuideDistance = distance;
                           snappedTo.x = vGuide;
                        }
                     }
                  }
                  // check center of selection
                  else if(moving) {
                     vGuide = -vGuide;
                     let distance = Math.abs(vGuide - (currentX + selectionWidth / 2 +
                                                       scrollLeft - containerX));

                     if(distance <= this.container.snapGuideRange && distance < vGuideDistance) {
                        const offsetGuide = vGuide - selectionWidth / 2;
                        closestVGuide = offsetGuide;
                        vGuideDistance = distance;
                        snappedTo.x = vGuide;
                     }
                  }
               });
            }

            if(closestHGuide || closestHGuide === 0 || closestVGuide || closestVGuide === 0) {
               result = {};

               if(closestVGuide !== null && closestVGuide !== undefined) {
                  result.x = closestVGuide + containerX + selectionOffsetX -
                     this.container.snapGuideOffset - scrollLeft - borderAdj;
               }

               if(closestHGuide !== null && closestHGuide !== undefined) {
                  result.y = closestHGuide + containerY + selectionOffsetY -
                     this.container.snapGuideOffset - scrollTop - borderAdj;
               }

               this.currentSnap = snappedTo;
               this.onSnap.emit(this.currentSnap);
            }
            else if(this.currentSnap) {
               this.currentSnap = null;
               this.onSnap.emit(this.currentSnap);
            }
         }

         if((!result || result.x === undefined || result.y === undefined) &&
            this.container.snapToGrid)
         {
            result = result || {};
            let rightOffset = 0;
            let bottomOffset = 0;

            if(selectionBounds) {
               if(resizingRight) {
                  // Interact passes in the top-left coordinate plus the drag delta. It applies the
                  // snap by adding the width and height to the returned snap values. In order for
                  // the right side to snap to the grid correctly, we need to calculate the snap for
                  // the right side and then subtract the width.
                  const selectionWidth = selectionBounds.right - selectionBounds.left;
                  currentX += selectionWidth;
                  rightOffset = -selectionWidth;
               }

               if(resizingBottom) {
                  // see comment for right-side resizing for an explanation of this
                  const selectionHeight = selectionBounds.bottom - selectionBounds.top;
                  currentY += selectionHeight;
                  bottomOffset = -selectionHeight;
               }
            }

            const gridX = Math.round((currentX - containerX) / this.container.snapGridSize);
            const gridY = Math.round((currentY - containerY) / this.container.snapGridSize);

            if(result.x === undefined) {
               result.x = (gridX * this.container.snapGridSize) +
                  containerX + selectionOffsetX + rightOffset - borderAdj;
            }

            if(result.y === undefined) {
               result.y = (gridY * this.container.snapGridSize) +
                  containerY + selectionOffsetY + bottomOffset - borderAdj;
            }
         }

         if(result && result.x !== undefined && result.y === undefined) {
            result.y = y;
         }

         if(result && result.x === undefined && result.y !== undefined) {
            result.x = x;
         }

         return result;
      }];

      return snap;
   }

   private removeUndefinedProperties(value: any): any {
      const output = {};

      Object.getOwnPropertyNames(value).forEach((prop: string) => {
         if(value[prop] !== null && value[prop] !== undefined) {
            output[prop] = value[prop];
         }
      });

      return output;
   }

   /*
    * Hacky work around so that a click event is not fired after a drag and drop. See
    * https://github.com/taye/interact.js/issues/217#issuecomment-114174915
    */
   private disableNextClick(event: any): void {
      const element = event.target;
      const listener = function(_event) {
         _event.stopPropagation();
         element.removeEventListener("click", listener, true);
      };
      element.addEventListener("click", listener, true);
   }

   // snap to grid/objects
   snap(point: Point): Point {
      const config = this.createSnapConfig(null, true);
      const p2: Point = config.targets[0](point.x, point.y);

      if(p2) {
         if(p2.x == null) {
            p2.x = point.x;
         }

         if(p2.y == null) {
            p2.y = point.y;
         }

         return p2;
      }

      return point;
   }
}

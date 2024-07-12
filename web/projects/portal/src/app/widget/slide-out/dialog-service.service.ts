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
import { Injectable, Injector, OnDestroy } from "@angular/core";
import { NgbModal, NgbModalRef } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { UIContextService } from "../../common/services/ui-context.service";
import { SlideOutOptions } from "./slide-out-options";
import { SlideOutRef } from "./slide-out-ref";
import { SlideOutService } from "./slide-out.service";
import { GuiTool } from "../../common/util/gui-tool";

/**
 * The service that delegates whether to open content in a modal or slide out panel.
 */
@Injectable({
   providedIn: "root"
})
export class DialogService implements OnDestroy {
   constructor(private modalService: NgbModal,
               private slideOutService: SlideOutService,
               private injector: Injector,
               private uiContext: UIContextService)
   {
      this.injector = Injector.create(
         {providers: [{provide: DialogService, useValue: this}], parent: injector});
      this.uiSub = uiContext.getObjectChange().subscribe(msg => {
         if(msg.action == "property") {
            this.panes.forEach(p => {
               if(p.ref.objectId == msg.objectId && p.ref.sheetId == msg.sheetId &&
                  (!msg.title || msg.title == p.ref.title))
               {
                  p.ref.changedByOthers = true;
               }
            });
         }
      });
   }

   ngOnDestroy() {
      if(this.uiSub) {
         this.uiSub.unsubscribe();
         this.uiSub = null;
      }
   }

   public container: string | HTMLElement = null;
   private sheetId: string;
   private objectDeleted = new Subject<string>();
   private panes: {ref: SlideOutRef, options: SlideOutOptions, content: any}[] = [];
   private currentSlideouts: SlideOutRef[] = [];
   private uiSub: Subscription;
   private popupCount: number = 0;

   getCurrentSlideOuts(): SlideOutRef[] {
      return this.currentSlideouts;
   }

   hasSlideout(objectId: string): boolean {
      return this.currentSlideouts.some(s => s.objectId == objectId);
   }

   public setSheetId(id: string) {
      this.sheetId = id;
   }

   // called when object is deleted
   objectDelete(objectId: string) {
      this.objectDeleted.next(objectId);
   }

   // called when object name is changed
   objectRename(oname: string, nname: string) {
      this.getCurrentSlideOuts()
         .filter(s => s.objectId == oname)
         .forEach(s => {
            s.objectId = nname;

            if(s.componentInstance && s.componentInstance.assemblyName == oname) {
               s.componentInstance.assemblyName = nname;
            }
         });
      const panes = this.panes.filter(s => s.options.objectId == oname);
      panes.forEach(s => s.options.objectId = nname);
      panes.forEach(s => s.options.title = s.ref.title);
   }

   // called when object properties changed (commit/apply)
   objectChange(objectId: string, title: string = null) {
      this.uiContext.objectPropertyChanged(objectId, title);
   }

   // find SlideOutRef with same content and options
   private findExisting(content0: any, options0: SlideOutOptions): SlideOutRef {
      const entry = this.panes.find(
         p => this.compareSlideoutOptions(p.options, options0) && content0 == p.content);
      return entry ? entry.ref : null;
   }

   public dismissAllSlideouts() {
      this.currentSlideouts.forEach(s => s.dismiss());
      this.currentSlideouts = [];
   }

   public showSlideout(idx: number) {
      for(let i = 0; i < this.currentSlideouts.length; i++) {
         this.currentSlideouts[i].setOnTop(i == idx);
         this.currentSlideouts[i].setExpanded(i == idx);
      }
   }

   public showSlideoutFor(objectId: string) {
      this.currentSlideouts.forEach(s => {
         s.setOnTop(s.objectId == objectId);
         s.setExpanded(s.objectId == objectId);
      });
   }

   // check if slideout is on top of all others. a slide out is on top if it's z-index
   // is set on top (1049), or if it's the last slideout of all visible slideouts.
   isSlideoutOnTop(idx: number): boolean {
      if(this.currentSlideouts[idx].isOnTop()) {
         return true;
      }

      if(this.currentSlideouts.find(s => s.isOnTop())) {
         return false;
      }

      return idx == this.currentSlideouts.length - 1;
   }

   get openPopups(): number {
      return this.popupCount;
   }

   /*
    * Called when an NgbModal is closed
    */
   onModalClose(callback: () => {}) {
      // Decrement the pop up counter after keyboard events go through
      setTimeout(() => this.popupCount -= 1);

      // restore scroll position on close
      if(callback) {
         callback();
      }
   }

   /**
    * Delegates open method to appropriate service based on boolean.
    */
   public open(content: any, options?: SlideOutOptions): any {
      const restore = GuiTool.preventAutoScroll(true);

      if(this.container) {
         if(!options) {
            options = <SlideOutOptions> {};
         }

         if(!options.container) {
            options.container = this.container;
         }
      }

      if(!options || !options.popup) {
         let ref: SlideOutRef = this.findExisting(content, options);

         if(ref) {
            // dismiss the old slideout ref, we only want to handle onCommit of the most
            // recent ref
            ref.dismiss();
         }

         ref = this.slideOutService.open(content, options, this.injector);
         this.panes.push({ref: ref, options: options, content: content});
         let sheetSubscription: Subscription;
         let assemblySubscription: Subscription;
         this.currentSlideouts.push(ref);
         this.showSlideout(this.currentSlideouts.length - 1);

         ref.result.then(() => {
            this.currentSlideouts = this.currentSlideouts.filter(s => s != ref);
         }, () => {
            this.currentSlideouts = this.currentSlideouts.filter(s => s != ref);
         });

         if(this.sheetId) {
            sheetSubscription = this.uiContext.getSheetChange().subscribe(msg => {
               if(msg.sheetId == this.sheetId) {
                  switch(msg.action) {
                  case "show":
                     this.currentSlideouts.push(ref);
                     ref.setVisible(true);
                     break;
                  case "hide":
                     this.currentSlideouts = this.currentSlideouts.filter(s => s != ref);
                     ref.setVisible(false);
                     break;
                  case "close":
                     this.currentSlideouts = this.currentSlideouts.filter(s => s != ref);
                     ref.close();
                     break;
                  }
               }
            });

            assemblySubscription = this.objectDeleted.subscribe(msg => {
               if(msg == ref.objectId && ref.objectId) {
                  this.currentSlideouts = this.currentSlideouts.filter(s => s != ref);
                  ref.dismiss();
               }
            });
         }

         const cleanup = () => {
            if(sheetSubscription) {
               sheetSubscription.unsubscribe();
               assemblySubscription.unsubscribe();
            }

            this.panes = this.panes.filter(p => p.ref != ref);
         };

         ref.result.then(cleanup, cleanup);

         return ref;
      }
      else {
         let ref: NgbModalRef = this.modalService.open(content, options);

         if(ref) {
            this.popupCount += 1;
            ref.result.then(() => this.onModalClose(restore), () => this.onModalClose(restore));
         }

         return ref;
      }
   }

   private compareSlideoutOptions(opt1: SlideOutOptions, opt2: SlideOutOptions): boolean {
      const noInj1 = Tool.clone(opt1);
      const noInj2 = Tool.clone(opt2);
      noInj1.injector = null;
      noInj2.injector = null;
      return Tool.isEquals(noInj1, noInj2);
   }
}

export function ViewerDialogServiceFactory(modalService: NgbModal,
                                           slideOutService: SlideOutService,
                                           injector: Injector,
                                           uiContext: UIContextService): DialogService
{
   const service = new DialogService(modalService, slideOutService, injector, uiContext);
   service.container = ".viewer-container";
   return service;
}

export function ComposerDialogServiceFactory(modalService: NgbModal,
                                             slideOutService: SlideOutService,
                                             injector: Injector,
                                             uiContext: UIContextService): DialogService
{
   const service = new DialogService(modalService, slideOutService, injector, uiContext);
   service.container = ".composer-body";
   return service;
}

export function VsWizardDialogServiceFactory(modalService: NgbModal,
                                             slideOutService: SlideOutService,
                                             injector: Injector,
                                             uiContext: UIContextService): DialogService
{
   const service = new DialogService(modalService, slideOutService, injector, uiContext);
   service.container = ".object-wizard-pane";
   return service;
}

export function BindingDialogServiceFactory(modalService: NgbModal,
                                            slideOutService: SlideOutService,
                                            injector: Injector,
                                            uiContext: UIContextService): DialogService
{
   const service = new DialogService(modalService, slideOutService, injector, uiContext);
   service.container = "binding-editor .split-content";
   return service;
}

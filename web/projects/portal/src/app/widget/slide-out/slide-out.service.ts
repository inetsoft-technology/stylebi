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
   ApplicationRef,
   ComponentFactory,
   ComponentFactoryResolver,
   ComponentRef,
   EmbeddedViewRef,
   Injectable,
   Injector,
   TemplateRef,
   ViewRef
} from "@angular/core";
import { NgbActiveModal } from "@ng-bootstrap/ng-bootstrap";
import { UIContextService } from "../../common/services/ui-context.service";
import { GuiTool } from "../../common/util/gui-tool";
import { BaseResizeableDialogComponent } from "../../vsobjects/dialog/base-resizeable-dialog.component";
import { SlideOutBackdropComponent } from "./slide-out-backdrop.component";
import { SlideOutOptions } from "./slide-out-options";
import { SlideOutRef } from "./slide-out-ref";
import { SlideOutComponent } from "./slide-out.component";
import { InSlideOutSignService } from "./in-slide-out-sign.service";

const isString = require("lodash/isString");

/*
 * Copied from ng-bootstrap, no longer exported.
 */
class ContentRef {
   constructor(public nodes: any[], public viewRef?: ViewRef, public componentRef?: ComponentRef<any>) {}
}

/**
 * The NgbModal service equivalent for the slide out component.
 */
@Injectable({
   providedIn: "root"
})
export class SlideOutService {
   private slideContainer: ComponentFactory<SlideOutComponent>;

   constructor(private factoryResolver: ComponentFactoryResolver,
               private applicationRef: ApplicationRef,
               private uiContext: UIContextService)
   {
      this.slideContainer = factoryResolver.resolveComponentFactory(SlideOutComponent);
   }

   /**
    * Method equivalent to the modal open method.
    * Note: Even though it takes the same options as modals, not all options make sense
    * for slide outs and will therefore be ignored.
    */
   public open(content: any, options?: SlideOutOptions, injector?: Injector): SlideOutRef {
      let containerEl: Element = null;

      if(!options?.container || typeof options?.container === "string") {
         const containerSelector: string = options.container as string || "body";
         const containerCandidates = document.querySelectorAll(containerSelector);

         // Don't check the layout box if only one element is found.
         if(containerCandidates.length === 1) {
            containerEl = containerCandidates.item(0);
         }
         else {
            for(let i = 0; i < containerCandidates.length; i++) {
               const containerCandidate = containerCandidates.item(i);

               if(GuiTool.hasLayoutBox(containerCandidate)) {
                  containerEl = containerCandidate;
                  break;
               }
            }
         }

         if(containerEl == null) {
            throw new Error(`No valid container found for selector ${containerSelector}`);
         }
      }
      else {
         containerEl = options?.container;
      }

      let applyInjector = options.injector || injector;
      // provide the InSlideOutSignService service to sign the content is in slide out.
      applyInjector = Injector.create(
         { providers: [{ provide: InSlideOutSignService }], parent: applyInjector });
      const activeModal: NgbActiveModal = new NgbActiveModal();
      const contentRef: ContentRef = this.getContentRef(applyInjector,
         content, activeModal);

      if(contentRef.componentRef &&
         contentRef.componentRef.instance instanceof BaseResizeableDialogComponent)
      {
         contentRef.componentRef.instance.inSlide = true;
      }

      const slideCon: ComponentRef<SlideOutComponent> =
         this.slideContainer.create(injector, contentRef.nodes);
      this.applicationRef.attachView(slideCon.hostView);

      const backdropFactory = this.factoryResolver
         .resolveComponentFactory(SlideOutBackdropComponent);
      const backdropCmptRef: ComponentRef<any> =
         options.backdrop == "static" ? backdropFactory.create(injector) : null;

      if(!!backdropCmptRef) {
         this.applicationRef.attachView(backdropCmptRef.hostView);
         containerEl.appendChild(backdropCmptRef.location.nativeElement);
      }

      containerEl.appendChild(slideCon.location.nativeElement);

      setTimeout(() => {
         let title: string = options.title;

         if(!title) {
            contentRef.nodes.forEach(nodes => nodes.forEach(node => {
               if(!title && node && node.querySelector) {
                  const titleNode: Element = node.querySelector(".modal-title");

                  if(titleNode) {
                     title = titleNode.innerHTML;

                     if(options.objectId) {
                        title = options.objectId.substring(options.objectId.lastIndexOf("/")) +
                           " " + title;
                     }
                  }
               }
            }));
         }

         if(title) {
            slideCon.instance.title = title;
         }
      }, 200);

      const slideRef: SlideOutRef = new SlideOutRef(slideCon, contentRef, backdropCmptRef,
                                                    options.objectId,
                                                    this.uiContext.getCurrentSheetId());

      activeModal.close = (result: any) => { slideRef.close(result); };
      activeModal.dismiss = (reason: any) => { slideRef.dismiss(reason); };

      this.applyOptions(slideCon.instance, options);

      return slideRef;
   }

   private applyOptions(tabs: SlideOutComponent, options: Object): void {
      ["keyboard", "size", "windowClass", "side", "limitResize"].forEach((optionName: string) => {
         if(options[optionName] || options[optionName] === false) {
            tabs[optionName] = options[optionName];
         }
      });
   }

   private getContentRef(contentInjector: Injector, content: any, context: NgbActiveModal): ContentRef
   {
      if(!content) {
         return new ContentRef([]);
      }
      else if(content instanceof TemplateRef) {
         const viewRef: EmbeddedViewRef<any> = content.createEmbeddedView(context);
         this.applicationRef.attachView(viewRef);
         return new ContentRef([viewRef.rootNodes], viewRef);
      }
      else if(isString(content)) {
         return new ContentRef([[document.createTextNode(`${content}`)]]);
      }
      else {
         const contentCmptFactory: ComponentFactory<any> =
            this.factoryResolver.resolveComponentFactory(content);
         const modalContentInjector = Injector.create(
            {providers: [{provide: NgbActiveModal, useValue: context}], parent: contentInjector});
         const componentRef: ComponentRef<any> =
            contentCmptFactory.create(modalContentInjector);
         this.applicationRef.attachView(componentRef.hostView);
         return new ContentRef([[componentRef.location.nativeElement]],
            componentRef.hostView, componentRef);
      }
   }
}

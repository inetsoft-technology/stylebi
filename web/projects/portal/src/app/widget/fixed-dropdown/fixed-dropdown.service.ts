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
import { DOCUMENT } from "@angular/common";
import {
   ApplicationRef,
   ComponentFactory,
   ComponentFactoryResolver,
   ComponentRef,
   Inject,
   Injectable,
   Injector,
   TemplateRef,
   Type,
   ViewRef
} from "@angular/core";
import { Rectangle } from "../../common/data/rectangle";
import { GuiTool } from "../../common/util/gui-tool";
import { DropdownOptions } from "./dropdown-options";
import { DropdownStackService } from "./dropdown-stack.service";
import { FixedDropdownContextmenuComponent } from "./fixed-dropdown-contextmenu.component";
import { DropdownRef } from "./fixed-dropdown-ref";
import { FixedDropdownComponent } from "./fixed-dropdown.component";

export class ContentRef {
   constructor(public nodes: any[],
               public viewRef?: ViewRef,
               public componentRef?: ComponentRef<any>)
   {
   }
}

/**
 * Class which handles the instantiation of dropdowns.
 * Inspired by https://github.com/ng-bootstrap/ng-bootstrap/blob/master/src/modal/modal-stack.ts
 */
@Injectable({
   providedIn: "root"
})
export class FixedDropdownService {
   private dropdownFactory: ComponentFactory<FixedDropdownComponent>;
   private dropdownContextmenuFactory: ComponentFactory<FixedDropdownContextmenuComponent>;
   private _container: Element;
   private _allowPositionOutsideContainer: boolean;

   constructor(private applicationRef: ApplicationRef,
               private injector: Injector,
               private componentFactoryResolver: ComponentFactoryResolver,
               @Inject(DOCUMENT) private document: Document,
               private stackService: DropdownStackService)
   {
      this.dropdownFactory = componentFactoryResolver
         .resolveComponentFactory(FixedDropdownComponent);
      this.dropdownContextmenuFactory = componentFactoryResolver
         .resolveComponentFactory(FixedDropdownContextmenuComponent);
   }

   open(content: any, options: DropdownOptions): DropdownRef {
      return this._open(this.componentFactoryResolver, this.injector, content, options);
   }

   private _open(moduleCFR: ComponentFactoryResolver, contentInjector: Injector, content: any,
                 options: DropdownOptions): DropdownRef
   {
      let containerEl = options.container || this.container || this.document.body;

      if(!containerEl) {
         throw new Error(
            `The specified dropdown container "${containerEl}" was not found in the DOM.`);
      }

      const contentRef = this.getContentRef(moduleCFR, contentInjector, content);
      const dropdownCmptRef = this.getDropdownRef(options, contentRef);
      this.applicationRef.attachView(dropdownCmptRef.hostView);
      GuiTool.preventAutoScroll();
      containerEl.appendChild(dropdownCmptRef.location.nativeElement);
      dropdownCmptRef.instance.container = containerEl;
      dropdownCmptRef.instance.allowPositionOutsideContainer = this.allowPositionOutsideContainer;
      this.applyOptions(dropdownCmptRef.instance, options);

      return new DropdownRef(dropdownCmptRef, this.stackService, contentRef);
   }

   private getDropdownRef(options: DropdownOptions,
                          contentRef: ContentRef): ComponentRef<FixedDropdownComponent>
   {
      return options.contextmenu ?
         this.dropdownContextmenuFactory.create(this.injector, contentRef.nodes) :
         this.dropdownFactory.create(this.injector, contentRef.nodes);
   }

   private getContentRef(moduleCFR: ComponentFactoryResolver, contentInjector: Injector,
                         content: TemplateRef<any> | Type<any>): ContentRef
   {
      if(content instanceof TemplateRef) {
         const viewRef = content.createEmbeddedView(null);
         this.applicationRef.attachView(viewRef);
         return new ContentRef([viewRef.rootNodes], viewRef);
      }
      else {
         const contentCmptFactory = moduleCFR.resolveComponentFactory(content);
         const componentRef = contentCmptFactory.create(contentInjector);
         this.applicationRef.attachView(componentRef.hostView);
         return new ContentRef([[componentRef.location.nativeElement]], componentRef.hostView,
                               componentRef);
      }
   }

   private applyOptions(dropdown: FixedDropdownComponent, options: DropdownOptions) {
      dropdown.dropdownBounds = new Rectangle(options.position.x, options.position.y, 0, 0);

      if(options.autoClose != null) {
         dropdown.autoClose = options.autoClose;
      }

      if(options.closeOnOutsideClick != null) {
         dropdown.closeOnOutsideClick = options.closeOnOutsideClick;
      }

      if(options.closeOnWindowResize != null) {
         dropdown.closeOnWindowResize = options.closeOnWindowResize;
      }

      if(options.zIndex != null) {
         dropdown.zIndex = options.zIndex;
      }
   }

   get container(): Element {
      return this._container;
   }

   set container(value: Element) {
      this._container = value;
   }

   get allowPositionOutsideContainer(): boolean {
      return this._allowPositionOutsideContainer;
   }

   set allowPositionOutsideContainer(value: boolean) {
      this._allowPositionOutsideContainer = value;
   }
}

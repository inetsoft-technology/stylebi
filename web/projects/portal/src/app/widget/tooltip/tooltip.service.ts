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
   Injector
} from "@angular/core";
import { TooltipComponent } from "./tooltip.component";

@Injectable({
   providedIn: "root"
})
export class TooltipService {
   private tooltipFactory: ComponentFactory<TooltipComponent>;
   private currentTooltip: ComponentRef<TooltipComponent>;
   private _container: Element;

   constructor(private applicationRef: ApplicationRef,
               private injector: Injector,
               private componentFactoryResolver: ComponentFactoryResolver,
               @Inject(DOCUMENT) private document: any)
   {
      this.tooltipFactory = componentFactoryResolver.resolveComponentFactory(TooltipComponent);
   }

   createTooltip(): ComponentRef<TooltipComponent> {
      if(this.currentTooltip) {
         this.currentTooltip.destroy();
         this.currentTooltip = null;
      }

      const tooltipRef = this.tooltipFactory.create(this.injector);
      this.applicationRef.attachView(tooltipRef.hostView);

      if(this.container) {
         this.container.appendChild(tooltipRef.location.nativeElement);
      }
      else {
         this.document.body.appendChild(tooltipRef.location.nativeElement);
      }

      return this.currentTooltip = tooltipRef;
   }

   removeTooltip(tooltipRef: ComponentRef<TooltipComponent>) {
      const tooltipElement = tooltipRef.location.nativeElement as HTMLElement;

      if(!!tooltipElement.parentNode) {
         tooltipElement.parentNode.removeChild(tooltipElement);
      }

      tooltipRef.destroy();
   }

   get container(): Element {
      return this._container;
   }

   set container(value: Element) {
      this._container = value;
   }
}
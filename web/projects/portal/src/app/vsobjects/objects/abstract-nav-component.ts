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
import { EventEmitter, Input, OnDestroy, Output, Directive } from "@angular/core";
import { VSObjectModel } from "../model/vs-object-model";
import { NavigationKeys } from "./navigation-keys";
import { Observable ,  Subscription } from "rxjs";
import { AbstractVSObject } from "./abstract-vsobject.component";
import { FocusObjectEventModel } from "../model/focus-object-event-model";
import { DataTipService } from "./data-tip/data-tip.service";

/**
 * Class used to keep keyboard navigation related methods.
 */
@Directive()
export abstract class NavigationComponent<T extends VSObjectModel>
   extends AbstractVSObject<T> implements OnDestroy
{
   @Input() set keyNavigation(observable: Observable<FocusObjectEventModel>) {
      this.keyboardNavObservable = observable;
      this.subscribeToKeyNav();
   }
   @Output() showHideMiniToolbar: EventEmitter<boolean> = new EventEmitter<boolean>();
   keyboardNavObservable: Observable<FocusObjectEventModel>;
   private keyboardNavSubscription: Subscription;
   protected miniToolbarFocus: boolean = false;

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.keyboardNavSubscription) {
         this.keyboardNavSubscription.unsubscribe();
      }
   }

   /**
    * Subscribe to the observable that will provide keyboard navigation,
    * and if this component is in focus, take the next "step" in the component.
    */
   private subscribeToKeyNav(): void {
      if(this.keyboardNavObservable) {
         this.keyboardNavSubscription =
            this.keyboardNavObservable
               .subscribe((data: FocusObjectEventModel) => {
                  if(!!data.focused &&
                     this.model.absoluteName == data.focused.absoluteName &&
                     data.key != NavigationKeys.TAB &&
                     data.key != NavigationKeys.SHIFT_TAB)
                  {
                     if(this.miniToolbarFocus && (data.key == NavigationKeys.LEFT ||
                        data.key == NavigationKeys.RIGHT || data.key == NavigationKeys.SPACE))
                     {
                        // Do nothing
                     }
                     else {
                        this.navigate(data.key);
                     }
                  }
                  else {
                     this.clearNavSelection();
                  }
               });
      }
   }

   /**
    * Update whether to force minitoolbar to show (force show when navigated to).
    * @param {boolean} focus
    */
   protected updateMiniToolbarFocus(focus: boolean): void {
      this.miniToolbarFocus = focus;
      this.showHideMiniToolbar.emit(focus);
   }

   /**
    * Given the navigation key clicked, do the appropriate action.
    * Up = move within component, or up to toolbar
    * Down, Left, Right = move withing component
    * Space = select
    * @param {NavigationKeys} key
    */
   protected abstract navigate(key: NavigationKeys): void;

   /**
    * Clear any selections made with key navigation.
    */
   protected abstract clearNavSelection(): void;
}

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
import { Injectable } from "@angular/core";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { Observable, Subject } from "rxjs";

@Injectable()
export class ComposerVsSearchService {
  private _searchString: string;
  private _searchMode: boolean = false;
  private _focusIndex: number = 0;
  private _focusObj: string;
  private focusChangeSubject: Subject<string> = new Subject<string>();

  constructor() {
  }

  set searchString(searchString: string) {
    if(this.isSearchMode()) {
      this._searchString = searchString;
      this._focusIndex = 0;
      this._focusObj = null;
    }
  }

  get searchString(): string {
    return !this._searchString ? this._searchString : this._searchString.trim();
  }

  get focusIndex(): number {
    return this._focusIndex;
  }

  focusChange(): Observable<string> {
    return this.focusChangeSubject.asObservable();
  }

  focusAssembly(focusObj: string): void {
    this._focusObj = focusObj;
    this.focusChangeSubject.next(this._focusObj);
  }

  isFocusAssembly(focusObj: string): boolean {
    return this._focusObj == focusObj;
  }

  matchName(str: string): boolean {
    if(!this.isSearchMode()) {
      return true;
    }

    if(!this.searchString) {
      return true;
    }

    if(!str) {
      return false;
    }

    return str.trim().toLowerCase().indexOf(this.searchString.toLowerCase()) >= 0;
  }

  assemblyVisible(obj: VSObjectModel): boolean {
    if(!this.isSearchMode()) {
      return true;
    }

    if(!this.searchString) {
      return true;
    }

    if(!obj) {
      return false;
    }

    if(this.matchName(obj.absoluteName)) {
      return true;
    }

    if(obj.objectType == "VSTab" || obj.objectType == "VSSelectionContainer") {
      return (<any> obj).childrenNames?.some(value => this.matchName(value));
    }

    return false;
  }

  isSearchMode(): boolean {
    return this._searchMode;
  }

  changeSearchMode(): void {
    this._searchMode = !this._searchMode;
    this._focusIndex = 0;
    this._focusObj = null;

    if(!this._searchMode) {
      this._searchString = null;
    }
  }

  nextFocus(): void {
    this._focusIndex++;
  }

  previousFocus(): void {
    this._focusIndex--;
  }
}

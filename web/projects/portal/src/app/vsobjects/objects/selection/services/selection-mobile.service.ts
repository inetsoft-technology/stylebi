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
import { Injectable } from "@angular/core";

import { VSObjectModel } from "../../../model/vs-object-model";
import { Observable, Subject } from "rxjs";
import { ViewsheetInfo } from "../../../data/viewsheet-info";
import { GuiTool } from "../../../../common/util/gui-tool";
import { FeatureFlagsService, FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";

@Injectable()
export class SelectionMobileService {
  private selectionToggleMaxMode: Subject<{obj: VSObjectModel, max: boolean}> =
     new Subject<{obj: VSObjectModel, max: boolean}>();
  private _latestToggleMax: VSObjectModel;

  constructor(private featureFlagsService: FeatureFlagsService) {
  }

  hasAutoMaxMode(vsInfo: ViewsheetInfo): boolean {
    let maxObj = vsInfo.vsObjects.find(v => v["maxMode"]);

    return this._latestToggleMax && maxObj && this._latestToggleMax.objectType == maxObj.objectType &&
       this._latestToggleMax.absoluteName == maxObj.absoluteName;
  }

  resetSelectionMaxMode(): void {
    if(this._latestToggleMax) {
      this.toggleSelectionMaxMode(this._latestToggleMax, false);
      this._latestToggleMax = null;
    }
  }

  toggleSelectionMaxMode(vsObject: VSObjectModel, max: boolean = true) {
    let selectType = vsObject.objectType;

    if(GuiTool.isMobileDevice() && (selectType == "VSSelectionList" || selectType == "VSSelectionTree" ||
       selectType == "VSSelectionContainer"))
    {
      this._latestToggleMax = vsObject;
      this.selectionToggleMaxMode.next({obj: vsObject, max: max});
    }
  }

  maxSelectionChanged(): Observable<{obj: VSObjectModel, max: boolean}> {
    return this.selectionToggleMaxMode.asObservable();
  }
}

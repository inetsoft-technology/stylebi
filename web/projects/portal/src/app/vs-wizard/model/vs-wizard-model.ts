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
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { VsWizardEditModes } from "../model/vs-wizard-edit-modes";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { Viewsheet } from "../../composer/data/vs/viewsheet";
import { Sheet } from "../../composer/data/sheet";

export interface VsWizardModel {
   entry?: AssetEntry; // source entry
   assetId?: string;
   linkUri?: string;
   runtimeId?: string;
   bindingOption?: string; // close option from full editor, cancel or finish or null.
   viewer?: boolean;
   temporarySheet?: boolean;
   hiddenNewBlock?: boolean;
   componentWizardEnable?: boolean;
   oinfo: WizardOriginalInfo;
   objectModel?: VSObjectModel;
   editMode?: VsWizardEditModes;
   oldAbsoluteName?: string;
   gettingStarted?: boolean;
}

/**
 * The original information when opened wizard pane.
 *
 * To support cancel can go back to original pane with original information after
 * switching between object wizard pane and binding pane many time times.
 */
export interface WizardOriginalInfo {
   runtimeId: string;   // should come from viewsheet pane/viewer, used when switch to use meta
   editMode: VsWizardEditModes;
   absoluteName?: string;
   objectType?: string;
   originalFocused?: Sheet;
}
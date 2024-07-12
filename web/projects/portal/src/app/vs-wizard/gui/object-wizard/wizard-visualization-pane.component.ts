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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ChangeVisualizationTypeEvent } from "../../model/event/change-visualizaton-type-event";
import { VSObjectRecommendation } from "../../model/recommender/vs-object-recommendation";
import { VSRecommendType } from "../../model/recommender/vs-recommend-type";
import { VSRecommendationModel } from "../../model/recommender/vs-recommendation-model";
import { VSSubType } from "../../model/recommender/vs-sub-type";

const CHANGE_SELECTED_TYPE: string = "/events/vswizard/visualization/change-selectedType";

@Component({
   selector: "wizard-visualization-pane",
   templateUrl: "wizard-visualization-pane.component.html",
   styleUrls: ["wizard-visualization-pane.component.scss"]
})
export class WizardVisualizationPane {
   @Output() onChangeSubtype = new EventEmitter<VSSubType>();
   selectedType: VSRecommendType;
   _model: VSRecommendationModel;

   @Input() set model(model: VSRecommendationModel) {
      this._model = model;
      this.changeSubType();
   }

   get model(): VSRecommendationModel {
      return this._model;
   }

   constructor(protected viewsheetClient: ViewsheetClientService) {
   }

   onSelectedType(type: VSRecommendType) {
      this.model.selectedType = type;
      let selectSubTypeIdx: number = type == VSRecommendType.ORIGINAL_TYPE ? 0 :
         this.getSelectedRecommendation().selectedIndex;
      this.sendSelectTypeEvent(selectSubTypeIdx);
   }

   selectSubTypeIdx(index: number) {
      this.sendSelectTypeEvent(index);
   }

   sendSelectTypeEvent(subTypeIndex: number) {
      const event = new ChangeVisualizationTypeEvent(this.model.selectedType, subTypeIndex);
      this.viewsheetClient.sendEvent(CHANGE_SELECTED_TYPE, event);
      this.changeSubType(subTypeIndex);
   }

   changeSubType(subTypeIndex?: number): void {
      let selectRecommend: VSObjectRecommendation = this.getSelectedRecommendation();
      let subType: VSSubType;

      if(!!selectRecommend && selectRecommend.subTypes.length > 0) {
         let subIndex = !!subTypeIndex ? subTypeIndex : selectRecommend.selectedIndex;
         subType = selectRecommend.subTypes[subIndex];
      }

      this.onChangeSubtype.emit(subType);
   }

   getSelectedRecommendation() {
      if(!this.model || !this.model.recommendationList) {
         return null;
      }

      return this.model.recommendationList.find((recommend: VSObjectRecommendation) => {
         return this.model.selectedType == recommend.type;
      });
   }
}

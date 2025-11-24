import { ViewsheetPropertyDialogModel } from "../../composer/data/vs/viewsheet-property-dialog-model";
import { ModelService } from "../../widget/services/model.service";
import { BehaviorSubject } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { Injectable } from "@angular/core";

const VIEWSHEET_PROPERTY_URI = "composer/vs/viewsheet-property-dialog-model";

@Injectable({ providedIn: 'root' })
export class VSPropertyDialogService {
   private hideNotificationsSubject = new BehaviorSubject<boolean>(false);
   hideNotifications$ = this.hideNotificationsSubject.asObservable();

   constructor(private modelService: ModelService) {
   }

   updateHideNotifications(runtimeId: string): void {
      const modelUri: string = "../api/" + VIEWSHEET_PROPERTY_URI + "/" +
                        Tool.byteEncode(runtimeId);
      this.modelService
         .getModel<ViewsheetPropertyDialogModel>(modelUri)
         .subscribe((data) => {
            console.log(data);
            const value = !!data.vsOptionsPane.hideNotifications;
            this.hideNotificationsSubject.next(value);
         });
   }

   updateVSPropertyDialogModel(model: ViewsheetPropertyDialogModel) {
      this.hideNotificationsSubject.next(!!model.vsOptionsPane.hideNotifications);
   }

}
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
   AfterViewInit,
   Component,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnInit,
   Output
} from "@angular/core";
import { CommandProcessor, ViewsheetClientService } from "../../../common/viewsheet-client";
import { AssemblyActionFactory } from "../../../vsobjects/action/assembly-action-factory.service";
import { ViewsheetInfo } from "../../../vsobjects/data/viewsheet-info";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { DialogService } from "../../../widget/slide-out/dialog-service.service";
import { VsWizardEditModes } from "../../model/vs-wizard-edit-modes";
import { ChangeVSObjectTextEvent } from "../../../vsobjects/event/change-vs-object-text-event";
import { GuiTool } from "../../../common/util/gui-tool";
import { SetPreviewPaneSizeEvent } from "../../model/event/set-preview-pane-size-event";
import { Dimension } from "../../../common/data/dimension";
import { RefreshDescriptionCommand } from "../../model/command/refresh-description-command";
import { VSChartModel } from "../../../vsobjects/model/vs-chart-model";
import { ConsoleMessage } from "../../../widget/console-dialog/console-message";

@Component({
   selector: "wizard-preview-pane",
   templateUrl: "./wizard-preview-pane.component.html",
   styleUrls: ["./wizard-preview-pane.component.scss"],
   providers: [
      AssemblyActionFactory
   ]
})
export class VSWizardPreviewPane extends CommandProcessor
   implements OnInit, AfterViewInit
{
   @Input() vsObject: VSObjectModel;
   @Input() runtimeId: string;
   @Input() linkuri: string;
   @Input() formatMap: Map<string, VSObjectFormatInfoModel>;
   @Input() editMode: VsWizardEditModes = VsWizardEditModes.WIZARD_DASHBOARD;
   @Input() showLegend: boolean;
   @Input() consoleMessages: ConsoleMessage[] = [];
   @Output() onUpdateFormat: EventEmitter<string> = new EventEmitter<string>();
   @Output() showLegendChange = new EventEmitter<boolean>();
   @Output() onMessageChange = new EventEmitter<ConsoleMessage[]>();

   description: string;
   vsInfo: ViewsheetInfo = new ViewsheetInfo([], null, null);

   constructor(protected zone: NgZone,
               public viewsheetClient: ViewsheetClientService)
   {
      super(viewsheetClient, zone, true);
   }

   ngOnInit() {
      this.vsInfo = new ViewsheetInfo([], this.linkuri, false, this.runtimeId);
   }

   ngAfterViewInit(): void {
      this.setPreviewPaneSize();
   }

   get assemblyName(): string {
      return !!this.vsObject ? this.vsObject.absoluteName : null;
   }

   getAssemblyName() {
      return null;
   }

   changeDescription(desc: string): void {
      this.description = desc;
      let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
         null, this.description);

      this.viewsheetClient.sendEvent("/events/vswizard/preview/changeDescription", event);
   }

   @HostListener("window:resize")
   setPreviewPaneSize(): void {
      let size: Dimension = GuiTool.getChartMaxModeSize();
      //subtract the height of the status bar
      size.height -= 98;
      this.viewsheetClient.sendEvent("/events/vswizard/preview/setPaneSize",
         new SetPreviewPaneSizeEvent(size));
   }

   private processRefreshDescriptionCommand(command: RefreshDescriptionCommand) {
      this.description = command.description;
   }

   get hasLegend(): boolean {
      const chart: VSChartModel = <VSChartModel> this.vsObject;
      return chart.legends && chart.legends.length > 0 || chart.legendHidden;
   }
}

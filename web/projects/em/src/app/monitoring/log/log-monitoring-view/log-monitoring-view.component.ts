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
   ChangeDetectionStrategy,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   Output,
   ViewChild
} from "@angular/core";
import { LogFileModel, LogMonitoringModel } from "../log-monitoring-model";

@Component({
   selector: "em-log-monitoring-view",
   templateUrl: "./log-monitoring-view.component.html",
   styleUrls: ["./log-monitoring-view.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class LogMonitoringViewComponent {
   @Input() model: LogMonitoringModel;
   @Output() downloadLog = new EventEmitter();
   @Output() refreshLog = new EventEmitter<boolean>();
   @Output() rotateLogs = new EventEmitter();
   @ViewChild("scrollViewport", { static: true }) scrollViewport: ElementRef<any>;

   get rotateDisabled(): boolean {
      return !this.model.selectedLog?.rotateSupported;
   }

   @Input()
   get logContents(): string[] {
      return this._logContents;
   }

   set logContents(value: string[]) {
      const end = !!this._logContents ? this._logContents.length : 0;
      this._logContents = value || [];
      this.allLines = this._logContents.join("\n");

      if(this.scrollViewport && this.scrollViewport.nativeElement) {
         if(this._logContents.length < end) {
            this.scrollViewport.nativeElement.scrollTop = this._logContents.length * 14;
         }
      }
   }

   private _logContents: string[];
   allLines: string;

   compareLogFileModel(model1: LogFileModel, model2: LogFileModel): boolean {
      return model1 && model2 && model1.clusterNode === model2.clusterNode &&
         model1.logFile === model2.logFile && model1.rotateSupported === model2.rotateSupported;
   }

   onLinesChange(evt) {
      this.model.lines = evt.target.value;
      this.refreshLog.emit();
   }

   onAllLinesChange(checked: boolean) {
      this.model.allLines = checked;
      this.refreshLog.emit();
   }

   onAutoRefreshChange(evt) {
      this.model.autoRefresh = evt.checked;
      this.refreshLog.emit(true);
   }

   handleKeypress(keyEvt) {
      if(keyEvt.keyCode === 13) {
         keyEvt.preventDefault();
         this.model.lines = keyEvt.target.value;
         this.refreshLog.emit();
      }
   }
}

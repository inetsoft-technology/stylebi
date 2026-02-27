/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
   Component,
} from "@angular/core";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { Secured } from "../../../secured";


@Secured({
   route: "/monitoring/summary/debug",
   label: "Debug"
})
@Component({
   selector: "em-debug-monitoring-page",
   templateUrl: "./debug-monitoring-page.component.html",
   styleUrls: ["./debug-monitoring-page.component.scss"]
})
export class DebugMonitoringPageComponent {

   constructor(private downloadService: DownloadService) {
   }

   getKVDump() {
      let url = "../em/monitoring/server/debug/key-value-storage-dump";

      this.downloadService.download(url);
   }
   getBlobPathDump() {
      let url = "../em/monitoring/server/debug/blob-path-dump";

      this.downloadService.download(url);
   }
}

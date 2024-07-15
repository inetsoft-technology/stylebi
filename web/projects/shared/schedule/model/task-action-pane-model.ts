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
import { ScheduleActionModel } from "./schedule-action-model";
import { ExportFormatModel } from "./export-format-model";
import { ServerLocation } from "./server-location";

export interface TaskActionPaneModel {
   securityEnabled: boolean;
   emailButtonVisible: boolean;
   endUser: string;
   administrator: boolean;
   defaultFromEmail: string;
   fromEmailEnabled: boolean;
   viewsheetEnabled: boolean;
   notificationEmailEnabled: boolean;
   saveToDiskEnabled: boolean;
   emailDeliveryEnabled: boolean;
   cvsEnabled: boolean;
   actions: ScheduleActionModel[];
   userDefinedClasses: string[];
   userDefinedClassLabels: string[];
   dashboardMap: {[id: string]: string};
   printers: string[];
   folderPaths: string[];
   folderLabels: string[];
   mailFormats: ExportFormatModel[];
   vsMailFormats: ExportFormatModel[];
   saveFileFormats: ExportFormatModel[];
   vsSaveFileFormats: ExportFormatModel[];
   expandEnabled: boolean;
   mailHistoryEnabled?: boolean;
   serverLocations?: ServerLocation[];
   fipsMode?: boolean;
}
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
import { ServerPathInfoModel } from "../../../portal/src/app/vsobjects/model/server-path-info-model";
import { ScheduleActionModel } from "./schedule-action-model";
import { VSBookmarkInfoModel } from "../../../portal/src/app/vsobjects/model/vs-bookmark-info-model";
import { CSVConfigModel } from "./csv-config-model";

export interface GeneralActionModel extends ScheduleActionModel {
   notificationEnabled: boolean;
   deliverEmailsEnabled: boolean;
   printOnServerEnabled: boolean;
   saveToServerEnabled: boolean;
   sheet?: string;
   sheetAlias?: string;
   bookmarks?: VSBookmarkInfoModel[];
   notifications?: string;
   notifyIfFailed?: boolean;
   fromEmail?: string;
   to?: string;
   subject?: string;
   format?: string;
   csvExportModel?: CSVConfigModel;
   csvSaveModel?: CSVConfigModel;
   bundledAsZip?: boolean;
   password?: string;
   attachmentName?: string;
   htmlMessage?: boolean;
   message?: string;
   highlightsSelected?: boolean;
   filePaths?: string[];
   saveFormats?: string[];
   serverFilePaths?: ServerPathInfoModel[];
   emailMatchLayout?: boolean;
   emailExpandSelections?: boolean;
   emailOnlyDataComponents?: boolean;
   exportAllTabbedTables?: boolean;
   saveMatchLayout?: boolean;
   saveExpandSelections?: boolean;
   saveOnlyDataComponents?: boolean;
   saveExportAllTabbedTables?: boolean;
   forceToRegenerateReport?: boolean;
   highlightAssemblies?: string[];
   highlightNames?: string[];
   folderPermission?: boolean;
   link?: boolean;
   deliverLink?: boolean;
   ccAddress: string;
   bccAddress: string;
}
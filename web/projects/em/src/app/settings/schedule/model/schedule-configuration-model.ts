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
import { ServerLocation } from "../../../../../../shared/schedule/model/server-location";
import { TimeRange } from "../../../../../../shared/schedule/model/time-condition-model";

export interface ScheduleConfigurationModel {
   concurrency: number;
   logFile: string;
   rmiPort: number;
   classpath: string;
   pathSeparator: string;
   notificationEmail: boolean;
   saveToDisk: boolean;
   emailDelivery: boolean;
   enableEmailBrowser: boolean;
   maxMemory: number;
   minMemory: number;
   emailAddress: string;
   emailSubject: string;
   emailMessage: string;
   notifyIfDown: boolean;
   notifyIfTaskFailed: boolean;
   shareTaskInSameGroup: boolean;
   deleteTaskOnlyByOwner: boolean;
   timeRanges: TimeRange[];
   serverLocations: ServerLocation[];
   saveAutoSuffix: string;
   securityEnable: boolean;
   cloudSecrets: boolean;
}

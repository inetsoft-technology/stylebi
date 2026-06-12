/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * MSW handlers for Viewer endpoints.
 *
 * Covers the ../api/vs/**, ../api/viewsheet/** API paths
 * used by components in the viewer. All defaults are "happy path" responses;
 * simple endpoints return the minimal shape, model endpoints return the complete
 * default model so that dialogs opened downstream do not hit missing-field errors.
 *
 * Per-test overrides:
 *   import { server } from '<path>/mocks/server';
 *   import { http, HttpResponse } from 'msw';
 *   server.use(http.get('*\/api/vs/someEndpoint', () => HttpResponse.json(myData)));
 */
import { http, HttpResponse } from "msw";

export const viewerHandlers = [

   // EmailDialog.ok() — validate email addresses before sending
   http.get("*/api/vs/check-email-valid", () => {
      return HttpResponse.json({
         messageCommand: { type: "OK", message: "" },
         addressHistory: [],
      });
   }),

   // ExportDialog.ok() — check if export is allowed for the current viewsheet
   http.get("*/export/check/*", () => {
      return HttpResponse.json({ type: "OK", message: "" });
   }),

   // ScheduleDialog.ok() — check if schedule bookmark is valid
   http.get("*/api/vs/check-schedule-dialog/*", () => {
      return HttpResponse.json({ type: "OK", message: "" });
   }),

   // ScheduleDialog.getSimpleScheduleDialog() — fetch simple schedule model
   http.get("*/api/vs/simple-schedule-dialog-model/*", () => {
      return HttpResponse.json({
         userDialogEnabled: false,
         timeProp: "",
         twelveHourSystem: false,
         taskName: "",
         isSecurity: false,
         formatTypes: [],
         expandEnabled: false,
         emailButtonVisible: false,
         emailDeliveryEnabled: false,
         timeConditionModel: null,
         actionModel: null,
         emailAddrDialogModel: null,
         timeRanges: [],
         startTimeEnabled: false,
         timeRangeEnabled: false,
      });
   }),

   // ProfilingDialog.ngOnInit() — load group-by field list
   http.get("*/api/portal/profile/group-by*", () => {
      return HttpResponse.json({
         fields: [
            { label: "Cycle Name", value: "cycle" },
         ],
      });
   }),

   // ProfilingDialog.reloadTable() — reload profiling table data (PUT)
   http.put("*/api/portal/profile/table*", () => {
      return HttpResponse.json({ body: [] });
   }),

];

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

   // VsWizardComponent.createNewRuntimeViewsheet() — GET to create a new runtime viewsheet
   // from an existing one; returns the new runtimeId string.
   http.get("*/api/vswizard/dialog/open", () => {
      return HttpResponse.json("rt-new-wizard");
   }),

   // VsWizardPane.fileChanged() — POST to upload an image into a wizard object.
   // Default returns a minimal VSImageModel so tests that don't care about the
   // response content don't hit "Unhandled request" warnings.
   http.post("*/api/composer/vswizard/update-image/**", () => {
      return HttpResponse.json({
         absoluteName: "PlaceholderImage",
         objectType: "VSImage",
         noImageFlag: false,
         objectFormat: { top: 10, left: 10, width: 200, height: 150 },
      });
   }),

   // AppInfoService constructor — load current org info on startup
   http.get("*/api/org/info", () => {
      return HttpResponse.json({ key: "host-org", value: "Default" });
   }),

   // AppInfoService.isEnterprise() — default: community edition
   http.get("*/api/enterprise", () => {
      return HttpResponse.json(false);
   }),

   // ShareEmailDialogComponent.ngOnInit() — load share-email model
   http.get("*/api/share/email", () => {
      return HttpResponse.json({
         emailModel: {
            toAddress: "test@example.com",
            ccAddress: "",
            bccAddress: "",
            fromAddress: "",
            fromAddressEnabled: false,
            subject: "Share Dashboard",
            message: "",
            userDialogEnabled: false,
            emailAddrDialogModel: { rootTree: {} },
         },
         historyEnabled: false,
         securityEnabled: false,
      });
   }),

   // ShareEmailDialogComponent.ok() — send email
   http.post("*/api/share/email", () => {
      return HttpResponse.json({});
   }),

   // ShareGoogleChatDialog.ok() — post to Google Chat
   http.post("*/api/share/google-chat", () => {
      return HttpResponse.json({});
   }),

   // ShareSlackDialog.ok() — post to Slack
   http.post("*/api/share/slack", () => {
      return HttpResponse.json({});
   }),
   
   // showBookmarks() — check whether the current asset belongs to the default org
   http.get("*/api/vs/bookmark/isDefaultOrgAsset/*", () => {
      return HttpResponse.json(false);
   }),

   // showBookmarks() — fetch the list of bookmarks for the current viewsheet
   http.get("*/api/vs/bookmark/get-bookmarks/*", () => {
      return HttpResponse.json([]);
   }),

   // closeViewsheet() — check whether the viewsheet has unsaved form-table data
   http.get("*/api/vs/checkFormTables*", () => {
      return HttpResponse.json(false);
   }),

   // ViewerViewComponent.ngOnInit() — PageTabService.getDrillTabsTop() preference lookup
   http.get("*/api/portal/drill-tabs-top", () => {
      return HttpResponse.json(false);
   }),

   // ObjectWizardToolBarComponent.openFullEditor() — open binding in full editor mode
   http.get("*/api/vswizard/object/toolbar/full-editor", () => {
      return HttpResponse.json({});
   }),

];

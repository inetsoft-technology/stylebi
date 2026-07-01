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
 * DriverWizardComponent - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - addDriverFiles / removeDriverFiles file-list mutation and dedup
 *   Group 2 [Risk 3] - isNextDisabled and next step routing
 *   Group 3 [Risk 3] - uploadDrivers upload-mode success and error handling
 *   Group 4 [Risk 3] - uploadDrivers maven-mode error mapping
 *   Group 5 [Risk 2] - createDriver success and failure
 *   Group 6 [Risk 1] - cancel output contract
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { EMPTY } from "rxjs";
import * as rxjs from "rxjs";

import { server } from "@test-mocks/server";
import {
   asDriverWizardPrivateApi,
   attachDriverWizardNotifications,
   createDriverWizardComponent,
   makeDriverWizardFile,
   makeDriverWizardFileEvent,
   makeDriverWizardNotificationsStub,
} from "./driver-wizard.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("DriverWizardComponent - risk", () => {
   describe("Group 1 - file list mutation", () => {
      it("addDriverFiles should append only new file names and mark the control dirty", () => {
         const comp = createDriverWizardComponent();
         comp.uploadForm.get("uploadFiles").setValue([makeDriverWizardFile("a.jar")]);
         const event = makeDriverWizardFileEvent([
            makeDriverWizardFile("a.jar"),
            makeDriverWizardFile("b.jar"),
         ]);

         comp.addDriverFiles(event);

         expect(comp.uploadForm.get("uploadFiles").value).toEqual([
            makeDriverWizardFile("a.jar"),
            makeDriverWizardFile("b.jar"),
         ]);
         expect(comp.uploadForm.get("uploadFiles").dirty).toBe(true);
      });

      it("removeDriverFiles should remove selected indexes and clear the selection list", () => {
         const comp = createDriverWizardComponent();
         comp.uploadForm.get("uploadFiles").setValue([
            makeDriverWizardFile("a.jar"),
            makeDriverWizardFile("b.jar"),
            makeDriverWizardFile("c.jar"),
         ]);
         comp.selectedDriverFiles = [0, 2];

         comp.removeDriverFiles();

         expect(comp.uploadForm.get("uploadFiles").value).toEqual([makeDriverWizardFile("b.jar")]);
         expect(comp.selectedDriverFiles).toEqual([]);
         expect(comp.uploadForm.get("uploadFiles").dirty).toBe(true);
      });
   });

   describe("Group 2 - disabled state and next routing", () => {
      it("isNextDisabled should reflect the upload step form state", () => {
         const comp = createDriverWizardComponent();

         expect(comp.isNextDisabled()).toBe(true);

         comp.uploadForm.get("uploadType").setValue("maven");
         comp.uploadForm.get("mavenCoord").setValue("g:a:1.0.0");
         comp.uploadForm.markAsDirty();

         expect(comp.isNextDisabled()).toBe(false);
      });

      it("isNextDisabled should reflect the drivers and plugin step form state", () => {
         const comp = createDriverWizardComponent();
         comp.step = "drivers";
         expect(comp.isNextDisabled()).toBe(true);

         comp.driverForm.get("drivers").setValue(["driver.a"]);
         comp.driverForm.markAsDirty();
         expect(comp.isNextDisabled()).toBe(false);

         comp.step = "plugin";
         expect(comp.isNextDisabled()).toBe(true);

         comp.pluginForm.get("pluginId").setValue("plugin.id");
         comp.pluginForm.get("pluginName").setValue("Plugin");
         comp.pluginForm.get("pluginVersion").setValue("1.0.0");
         comp.pluginForm.markAsDirty();
         expect(comp.isNextDisabled()).toBe(false);
      });

      it("next should route upload -> uploadDrivers, drivers -> plugin, plugin -> createDriver", () => {
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const uploadSpy = vi.spyOn(privateApi, "uploadDrivers").mockImplementation(() => {});
         const createSpy = vi.spyOn(privateApi, "createDriver").mockImplementation(() => {});

         comp.step = "upload";
         comp.next();
         expect(uploadSpy).toHaveBeenCalled();

         comp.step = "drivers";
         comp.next();
         expect(comp.step).toBe("plugin");

         comp.step = "plugin";
         comp.next();
         expect(createSpy).toHaveBeenCalled();
      });
   });

   describe("Group 3 - uploadDrivers upload mode", () => {
      it("uploadDrivers should upload files, scan drivers, dedupe results, and move to the drivers step", async () => {
         let uploadTypeParam = "";
         server.use(
            http.post("*/api/em/upload", ({ request }) => {
               uploadTypeParam = new URL(request.url).searchParams.get("uploadType") || "";
               return HttpResponse.json({ identifier: "upload-1", files: ["a.jar"] });
            }),
            http.get("*/api/em/settings/content/plugins/drivers/scan/upload-1", () =>
               HttpResponse.json({ drivers: ["existing.driver", "new.driver"] }),
            ),
         );
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         comp.drivers = ["existing.driver"];
         comp.uploadForm.get("uploadFiles").setValue([makeDriverWizardFile("a.jar")]);

         privateApi.uploadDrivers();

         await waitFor(() => expect(comp.step).toBe("drivers"));
         expect(uploadTypeParam).toBe("driver");
         expect(comp.drivers).toEqual(["existing.driver", "new.driver"]);
         expect(comp.loading).toBe(false);
      });

      it("uploadDrivers should surface the backend message for upload-mode 400 errors", async () => {
         server.use(
            http.post("*/api/em/upload", () =>
               HttpResponse.json({ message: "Upload failed" }, { status: 400 }),
            ),
         );
         vi.spyOn(rxjs, "throwError").mockReturnValue(EMPTY);
         const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const dataNotifications = makeDriverWizardNotificationsStub();
         attachDriverWizardNotifications(comp, dataNotifications);
         comp.uploadForm.get("uploadFiles").setValue([makeDriverWizardFile("a.jar")]);

         privateApi.uploadDrivers();

         await waitFor(() =>
            expect(dataNotifications.notifications.danger).toHaveBeenCalledWith("Upload failed"),
         );
         expect(consoleSpy).toHaveBeenCalled();
         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 4 - uploadDrivers maven mode", () => {
      it("maps unresolved dependency errors to the mavenCoordMissing notification", async () => {
         server.use(
            http.post("*/api/em/upload/maven", () =>
               HttpResponse.json({ message: "unresolved dependency foo" }, { status: 500 }),
            ),
         );
         vi.spyOn(rxjs, "throwError").mockReturnValue(EMPTY);
         vi.spyOn(console, "error").mockImplementation(() => {});
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const dataNotifications = makeDriverWizardNotificationsStub();
         attachDriverWizardNotifications(comp, dataNotifications);
         comp.uploadForm.get("uploadType").setValue("maven");
         comp.uploadForm.get("mavenCoord").setValue("g:a:1.0.0");

         privateApi.uploadDrivers();

         await waitFor(() =>
            expect(dataNotifications.notifications.danger)
               .toHaveBeenCalledWith("_#(js:em.data.databases.driver.mavenCoordMissing)"),
         );
         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 5 - createDriver", () => {
      it("createDriver should post the request and emit ok on success", async () => {
         let requestBody: unknown;
         server.use(
            http.post("*/api/em/settings/content/plugins/drivers", async ({ request }) => {
               requestBody = await request.json();
               return HttpResponse.json({});
            }),
         );
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const commitSpy = vi.spyOn(comp.onCommit, "emit");
         privateApi.uploadId = "upload-1";
         comp.driverForm.get("drivers").setValue(["driver.a"]);
         comp.pluginForm.get("pluginId").setValue("plugin.id");
         comp.pluginForm.get("pluginName").setValue("Plugin");
         comp.pluginForm.get("pluginVersion").setValue("1.0.0");

         privateApi.createDriver();

         await waitFor(() => expect(commitSpy).toHaveBeenCalledWith("ok"));
         expect(requestBody).toEqual({
            uploadId: "upload-1",
            pluginId: "plugin.id",
            pluginName: "Plugin",
            pluginVersion: "1.0.0",
            drivers: ["driver.a"],
         });
         expect(comp.loading).toBe(false);
      });

      it("createDriver should notify on failure and reset loading", async () => {
         server.use(
            http.post("*/api/em/settings/content/plugins/drivers", () =>
               HttpResponse.json({ message: "create failed" }, { status: 500 }),
            ),
         );
         vi.spyOn(rxjs, "throwError").mockReturnValue(EMPTY);
         vi.spyOn(console, "error").mockImplementation(() => {});
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const dataNotifications = makeDriverWizardNotificationsStub();
         attachDriverWizardNotifications(comp, dataNotifications);
         privateApi.uploadId = "upload-1";
         comp.driverForm.get("drivers").setValue(["driver.a"]);
         comp.pluginForm.get("pluginId").setValue("plugin.id");
         comp.pluginForm.get("pluginName").setValue("Plugin");
         comp.pluginForm.get("pluginVersion").setValue("1.0.0");

         privateApi.createDriver();

         await waitFor(() =>
            expect(dataNotifications.notifications.danger)
               .toHaveBeenCalledWith("_#(js:em.data.databases.driverCreateError)"),
         );
         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 6 - cancel", () => {
      it("cancel should emit the cancel token", () => {
         const comp = createDriverWizardComponent();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.cancel();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });
   });
});

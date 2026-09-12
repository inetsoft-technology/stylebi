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
 * EditDashboardDialog - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit bootstrap for new and existing dashboards
 *   Group 2 [Risk 2] - dashboard-name validation and form validity
 *   Group 3 [Risk 2] - nodeSelected selection mapping
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - repository tree ViewChild -> minimal helper stub
 */

import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { GuiTool } from "../../common/util/gui-tool";
import {
   attachEditDashboardTree,
   createEditDashboardDialog,
   makeDashboardModel,
   makeEditDashboardDialogNode,
} from "./edit-dashboard-dialog.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("EditDashboardDialog - interaction", () => {
   describe("Group 1 - ngOnInit", () => {
      it("should initialize a new dashboard, mobile state, and validators", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const { comp, repositoryTreeService } = createEditDashboardDialog();

         comp.ngOnInit();

         expect(comp.isNew).toBe(true);
         expect(comp.mobile).toBe(true);
         expect(comp.dashboard).toEqual({});
         expect(comp.nameControl.value).toBeFalsy();
         expect(repositoryTreeService.getRootFolder).toHaveBeenCalledWith(
            null,
            RepositoryEntryType.FOLDER | RepositoryEntryType.VIEWSHEET,
            null,
            false,
            true,
            false,
            undefined,
            true,
            false,
            false,
            true,
         );
      });

      it("should strip the global suffix and expand the selected path for an existing dashboard", async () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
         const { comp, changeDetector } = createEditDashboardDialog();
         const tree = attachEditDashboardTree(comp);
         comp.dashboard = makeDashboardModel();

         comp.ngOnInit();
         await Promise.resolve();

         expect(comp.isNew).toBe(false);
         expect(comp.oldName).toBe("Construction__GLOBAL");
         expect(comp.name).toBe("Construction");
         expect(comp.global).toBe(true);
         expect(comp.rootNode).toEqual(expect.objectContaining({ label: "Repository" }));
         expect(changeDetector.detectChanges).toHaveBeenCalled();
         expect(tree.selectAndExpandToPath).toHaveBeenCalledWith("Examples/Construction Dashboard");
      });
   });

   describe("Group 2 - name validation", () => {
      it("should allow dashboard names such as A&B and keep the form valid when a path is selected", () => {
         const { comp } = createEditDashboardDialog();
         attachEditDashboardTree(comp);
         comp.dashboard = makeDashboardModel();

         comp.ngOnInit();
         comp.nameControl.setValue("A&B");

         expect(comp.nameControl.errors).toBeNull();
         expect(comp.isValid()).toBe(true);
      });

      it("should reject dashboard names containing invalid characters such as A%~", () => {
         const { comp } = createEditDashboardDialog();
         attachEditDashboardTree(comp);
         comp.dashboard = makeDashboardModel();

         comp.ngOnInit();
         comp.nameControl.setValue("A%~");

         expect(comp.nameControl.errors).toEqual({ containsSpecialCharsForName: true });
         expect(comp.isValid()).toBe(false);
      });
   });

   describe("Group 3 - nodeSelected", () => {
      it("should map a viewsheet selection into dashboard path and identifier", () => {
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel({ path: null, identifier: null });

         comp.nodeSelected(makeEditDashboardDialogNode({
            type: RepositoryEntryType.VIEWSHEET,
         }) as never);

         expect(comp.dashboard.path).toBe("Examples/Construction Dashboard");
         expect(comp.dashboard.identifier).toBe("1^128^__NULL__^Examples/Construction Dashboard");
      });

      it("should clear the target when a folder is selected", () => {
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel();

         comp.nodeSelected(makeEditDashboardDialogNode({
            type: RepositoryEntryType.FOLDER,
            entry: undefined,
         }) as never);

         expect(comp.dashboard.path).toBeNull();
         expect(comp.dashboard.identifier).toBeNull();
      });
   });
});

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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * bug #76588 (viewsheet taskToken audit-pinning): {@link AdminViewsheetController} must map a
 * {@code TaskTokenMismatchException} to a structured 409, mirroring
 * {@code handlePlanHashMismatch} and {@code AdminAiController}'s own equivalent handler exactly --
 * otherwise the exception falls through to a default 500 instead.
 *
 * <p>No prior controller test file exercised {@code preview}/{@code apply}/exception mapping for
 * this controller ({@link AdminViewsheetControllerGetFolderTest} only covers {@code getFolder}) --
 * scoped narrowly to the new mapping this fix adds, not a general controller test suite.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminViewsheetControllerTest {
   @Mock private ViewsheetService viewsheetApiService;
   @Mock private ViewsheetFolderService folderService;
   @Mock private ViewsheetChangePlanService planService;
   @Mock private ViewsheetChangesetApplyService applyService;
   private AdminViewsheetController controller;

   @BeforeEach void setUp() {
      controller = new AdminViewsheetController(
         viewsheetApiService, folderService, planService, applyService);
   }

   @Test void handleTaskTokenMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("rename census", List.of(), true, true,
                                              "hash456", "token456");
      AdminChangesetApplyService.TaskTokenMismatchException ex =
         new AdminChangesetApplyService.TaskTokenMismatchException(current,
            "taskToken: does not match the current plan; re-review before applying");

      Map<String, Object> actual = controller.handleTaskTokenMismatch(ex);

      assertEquals("conflict", actual.get("status"));
      assertEquals(ex.getMessage(), actual.get("error"));
      ResolvedPlan returnedPlan = (ResolvedPlan) actual.get("plan");
      assertEquals(current.task(), returnedPlan.task());
      assertEquals(current.planHash(), returnedPlan.planHash());
      assertNull(returnedPlan.taskToken());
   }

   @Test void handleTaskTokenMismatchIsAnnotatedConflict() throws NoSuchMethodException {
      ResponseStatus annotation = AdminViewsheetController.class
         .getMethod("handleTaskTokenMismatch",
                    AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleTaskTokenMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}

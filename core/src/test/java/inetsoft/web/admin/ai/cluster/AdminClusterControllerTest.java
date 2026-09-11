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
package inetsoft.web.admin.ai.cluster;

import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.cluster.ServerClusterClient;
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
 * bug #76588 (cluster taskToken audit-pinning): {@link AdminClusterController} must map a
 * {@code TaskTokenMismatchException} to a structured 409, mirroring
 * {@code AdminAiController#handleTaskTokenMismatch} exactly -- otherwise the exception falls
 * through to the global {@code AdminExceptionHandler} catch-all and comes back as a 500.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminClusterControllerTest {
   @Mock private ClusterChangePlanService planService;
   @Mock private ClusterChangesetApplyService applyService;
   @Mock private ServerClusterClient client;
   private AdminClusterController controller;

   @BeforeEach void setUp() {
      controller = new AdminClusterController(planService, applyService, client);
   }

   @Test void handleTaskTokenMismatchReturnsConflictStatusWithCurrentPlan() {
      ResolvedPlan current = new ResolvedPlan("pause node1", List.of(), false, true,
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
      ResponseStatus annotation = AdminClusterController.class
         .getMethod("handleTaskTokenMismatch",
                    AdminChangesetApplyService.TaskTokenMismatchException.class)
         .getAnnotation(ResponseStatus.class);

      assertNotNull(annotation, "handleTaskTokenMismatch must be annotated @ResponseStatus");
      assertEquals(HttpStatus.CONFLICT, annotation.value());
   }
}

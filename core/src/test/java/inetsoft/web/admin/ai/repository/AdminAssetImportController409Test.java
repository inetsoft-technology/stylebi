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
package inetsoft.web.admin.ai.repository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #80093: the three apply 409 bodies carry a {@code reason} the client can key on. */
@Tag("core")
class AdminAssetImportController409Test {
   private final AdminAssetImportController controller =
      new AdminAssetImportController(null, null, null);

   private static RepositoryImportPlan plan() {
      return new RepositoryImportPlan("tok", null, List.of(), List.of(), List.of(), false, true,
                                      true, "h", "t");
   }

   @Test
   void applyInProgressBody() {
      Map<String, String> body = controller.handleApplyInProgress(
         new AdminAssetImportApplyService.ApplyInProgressException());
      assertEquals("conflict", body.get("status"));
      assertEquals("apply-in-progress", body.get("reason"));
      assertTrue(body.get("error").startsWith("another repository import apply is in progress"));
   }

   @Test
   void planDriftBody() {
      Map<String, Object> body = controller.handlePlanHashMismatch(
         new AdminAssetImportApplyService.PlanHashMismatchException(plan()));
      assertEquals("conflict", body.get("status"));
      assertEquals("plan-drift", body.get("reason"));
      assertNotNull(body.get("plan"));
      assertNotNull(body.get("error"));
   }

   @Test
   void taskTokenMismatchBody() {
      Map<String, Object> body = controller.handleTaskTokenMismatch(
         new AdminAssetImportApplyService.TaskTokenMismatchException(plan(), "bad token"));
      assertEquals("conflict", body.get("status"));
      assertEquals("task-token-mismatch", body.get("reason"));
      assertEquals("bad token", body.get("error"));
      assertNotNull(body.get("plan"));
   }
}

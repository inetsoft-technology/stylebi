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
package inetsoft.web.wiz.controller;

import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.LogicalModelService;
import inetsoft.web.portal.controller.database.PhysicalModelManagerService;
import inetsoft.web.viewsheet.Audited;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every write route of the kind-agnostic controllers reaches an audited service method, so the
 * Portal's standard audit records are written. The services' {@code @Audited} annotations are
 * pinned by reflection (a unit test cannot run the aspect); that the controllers reach those
 * methods is shown by the effect tests in {@code WizDataModelBridgeTest}, which fail if a route
 * stops calling its service. Move and folder rename write their own {@code ActionRecord} inside
 * {@code DatabaseModelBrowserService}, which the bridge calls unchanged.
 */
@Tag("core")
class WizDataModelAuditReachabilityTest {
   @Test
   void theServiceMethodsTheBridgeCallsAreAudited() {
      assertAudited(LogicalModelService.class, "removeModel",
                    ActionRecord.ACTION_NAME_DELETE, ActionRecord.OBJECT_TYPE_LOGICAL_MODEL);
      assertAudited(LogicalModelService.class, "renameModel",
                    ActionRecord.ACTION_NAME_RENAME, ActionRecord.OBJECT_TYPE_LOGICAL_MODEL);
      assertAudited(PhysicalModelManagerService.class, "removeModel",
                    ActionRecord.ACTION_NAME_DELETE, ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW);
      assertAudited(PhysicalModelManagerService.class, "renameModel",
                    ActionRecord.ACTION_NAME_RENAME, ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW);
      assertAudited(PhysicalModelManagerService.class, "createAndSaveModel",
                    ActionRecord.ACTION_NAME_CREATE, ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW);
      assertAudited(PhysicalModelManagerService.class, "updateAndSaveModel",
                    ActionRecord.ACTION_NAME_EDIT, ActionRecord.OBJECT_TYPE_PHYSICAL_VIEW);
      assertAudited(DataModelFolderManagerService.class, "setDataModelFolder",
                    ActionRecord.ACTION_NAME_CREATE, ActionRecord.OBJECT_TYPE_FOLDER);
      assertAudited(DataModelFolderManagerService.class, "deleteDataModelFolder",
                    ActionRecord.ACTION_NAME_DELETE, ActionRecord.OBJECT_TYPE_FOLDER);
   }

   private static void assertAudited(Class<?> type, String method, String action,
                                     String objectType)
   {
      Method found = Arrays.stream(type.getMethods())
         .filter(m -> m.getName().equals(method) && m.isAnnotationPresent(Audited.class))
         .findFirst()
         .orElseThrow(() -> new AssertionError(
            type.getSimpleName() + "." + method + " is no longer @Audited, so the bridge write " +
               "that calls it would write no Portal audit record"));
      Audited audited = found.getAnnotation(Audited.class);
      assertEquals(action, audited.actionName(), type.getSimpleName() + "." + method);
      assertEquals(objectType, audited.objectType(), type.getSimpleName() + "." + method);
   }
}

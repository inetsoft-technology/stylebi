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
package inetsoft.test;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.extension.ExtensionContext;

public class RuntimeViewsheetExtension extends MockMessageExtension {
   public RuntimeViewsheetExtension(OpenViewsheetEvent openViewsheetEvent,
                                    ControllersExtension controllersResource)
   {
      this.openViewsheetEvent = openViewsheetEvent;
      this.controllersResource = controllersResource;
   }

   public String getRuntimeId() {
      return runtimeId;
   }

   public RuntimeViewsheet getRuntimeViewsheet() {
      try {
         return runtimeId == null ?
            null : controllersResource.getViewsheetService().getViewsheet(runtimeId, null);
      }
      catch(RuntimeException e ) {
         throw e;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get runtime viewsheet", e);
      }
   }

   @Override
   public void beforeEach(ExtensionContext context) {
      runtimeId = mockMessage(openViewsheetEvent, this::openViewsheet);
   }

   @Override
   public void afterEach(ExtensionContext context) {
      mockMessage(runtimeId, this::closeViewsheet);
      runtimeId = null;
   }

   private String openViewsheet(OpenViewsheetEvent openViewsheetEvent) {
      try {
         controllersResource.getOpenViewsheetController().openViewsheet(
            openViewsheetEvent, getHeaderAccessor().getUser(), getCommandDispatcher(),
            "http://localhost:8080/sree");
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to open viewsheet", e);
      }

      return controllersResource.getRuntimeId();
   }

   private void closeViewsheet(String runtimeId) {
      if(runtimeId != null) {
         try {
            controllersResource.getViewsheetService().closeViewsheet(runtimeId, null);
         }
         catch(Exception e) {
            e.printStackTrace();
         }
      }
   }

   private final OpenViewsheetEvent openViewsheetEvent;
   private final ControllersExtension controllersResource;
   private String runtimeId;
}

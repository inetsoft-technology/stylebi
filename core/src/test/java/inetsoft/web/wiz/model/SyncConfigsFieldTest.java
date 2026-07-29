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
package inetsoft.web.wiz.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("core")
class SyncConfigsFieldTest {
   @Test
   void autoBindingRequestSyncConfigsRoundTrips() {
      AutoBindingRequest req = new AutoBindingRequest();
      assertFalse(req.isSyncConfigs());
      req.setSyncConfigs(true);
      assertTrue(req.isSyncConfigs());
   }

   @Test
   void createVisualizationModelSyncConfigsRoundTrips() {
      CreateVisualizationModel model = new CreateVisualizationModel();
      assertFalse(model.isSyncConfigs());
      model.setSyncConfigs(true);
      assertTrue(model.isSyncConfigs());
   }
}

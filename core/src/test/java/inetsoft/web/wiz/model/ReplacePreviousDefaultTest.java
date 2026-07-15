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

/**
 * The {@code replacePrevious} flag decides whether a chart-type change replaces the current chart in
 * place (front-end click) or keeps it and adds a new one (agent/MCP). The safe default is
 * {@code false} (keep the old, add a new) — the agent path relies on omitting it, so a flipped
 * default would silently start deleting charts on every agent-triggered type change.
 */
@Tag("core")
class ReplacePreviousDefaultTest {

   @Test
   void changeTypeRequest_defaultsToFalse_andRoundTrips() {
      ChangeTypeRequest req = new ChangeTypeRequest();
      assertFalse(req.isReplacePrevious(), "ChangeTypeRequest.replacePrevious must default to false (add-new)");
      req.setReplacePrevious(true);
      assertTrue(req.isReplacePrevious());
   }

   @Test
   void createVisualizationModel_defaultsToFalse_andRoundTrips() {
      CreateVisualizationModel model = new CreateVisualizationModel();
      assertFalse(model.isReplacePrevious(), "CreateVisualizationModel.replacePrevious must default to false (add-new)");
      model.setReplacePrevious(true);
      assertTrue(model.isReplacePrevious());
   }

   @Test
   void autoBindingRequest_defaultsToFalse_andRoundTrips() {
      AutoBindingRequest req = new AutoBindingRequest();
      assertFalse(req.isReplacePrevious(), "AutoBindingRequest.replacePrevious must default to false (add-new)");
      req.setReplacePrevious(true);
      assertTrue(req.isReplacePrevious());
   }
}

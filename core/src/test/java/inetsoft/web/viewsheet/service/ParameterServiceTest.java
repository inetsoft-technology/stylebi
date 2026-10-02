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
package inetsoft.web.viewsheet.service;

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ParameterServiceTest {
   // Bug #77329, a client must not be able to set the identity variables that VPM filters on
   @Test
   void readParametersDropsContextVariables() throws Exception {
      Map<String, String[]> parameters = new HashMap<>();
      parameters.put("_USER_", new String[] { "Hardware" });
      parameters.put("_ROLES_", new String[] { "Hardware" });
      parameters.put("_GROUPS_", new String[] { "Hardware" });
      parameters.put("__principal__", new String[] { "Hardware" });
      parameters.put("region", new String[] { "West" });

      VariableTable vars = new ParameterService(null).readParameters(parameters);

      assertFalse(vars.contains("_USER_"));
      assertFalse(vars.contains("_ROLES_"));
      assertFalse(vars.contains("_GROUPS_"));
      assertFalse(vars.contains("__principal__"));
      assertEquals("West", vars.get("region"));
   }
}

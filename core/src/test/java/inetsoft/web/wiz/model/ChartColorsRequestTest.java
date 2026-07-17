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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * copy+apply wire contract: {@code copy} defaults to false (existing callers untouched) and
 * deserializes when the caller explicitly asks to duplicate the assembly before applying colors.
 */
@Tag("core")
class ChartColorsRequestTest {
   @Test
   void copyDefaultsToFalseWhenOmitted() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ChartColorsRequest req = mapper.readValue("{ \"wizRuntimeId\": \"rt-1\" }", ChartColorsRequest.class);
      assertFalse(req.isCopy());
   }

   @Test
   void copyDeserializesWhenTrue() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ChartColorsRequest req = mapper.readValue("{ \"copy\": true }", ChartColorsRequest.class);
      assertTrue(req.isCopy());
   }
}

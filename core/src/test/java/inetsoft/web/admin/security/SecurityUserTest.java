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
package inetsoft.web.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Bug #77326: SecurityUser.active is a Boolean so that an update can tell an omitted status
 * apart from true. The JSON keeps a single "active" property, an unset status is not sent (an
 * older server reads a null as false for its primitive field and would disable the user), and
 * the deprecated isActive()/setActive(boolean) still work for existing scripts and clients.
 */
@Tag("core")
class SecurityUserTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void activeUnset_isNotSerialized_andDeserializesAsNull() throws Exception {
      JsonNode json = mapper.valueToTree(new SecurityUser());

      assertFalse(json.has("active"), json.toString());
      assertNull(mapper.readValue("{}", SecurityUser.class).getActive());
   }

   @Test
   void activeTrueAndFalse_roundTripAsSingleActiveProperty() throws Exception {
      for(boolean value : new boolean[] { true, false }) {
         SecurityUser user = new SecurityUser();
         user.setActive(Boolean.valueOf(value));

         JsonNode json = mapper.valueToTree(user);
         assertTrue(json.get("active").isBoolean(), json.toString());
         assertEquals(value, json.get("active").booleanValue());
         assertEquals(1, json.findValues("active").size(), json.toString());

         SecurityUser read = mapper.readValue(json.toString(), SecurityUser.class);
         assertEquals(Boolean.valueOf(value), read.getActive());
      }
   }

   @Test
   void explicitNullActive_deserializesAsNull() throws Exception {
      assertNull(mapper.readValue("{\"active\":null}", SecurityUser.class).getActive());
   }

   @Test
   @SuppressWarnings("deprecation")
   void deprecatedAccessors_keepTheOldBehavior() {
      SecurityUser user = new SecurityUser();
      // the old primitive field defaulted to true
      assertTrue(user.isActive());

      user.setActive(false);
      assertFalse(user.isActive());
      assertEquals(Boolean.FALSE, user.getActive());

      user.setActive(true);
      assertTrue(user.isActive());
      assertEquals(Boolean.TRUE, user.getActive());
   }
}

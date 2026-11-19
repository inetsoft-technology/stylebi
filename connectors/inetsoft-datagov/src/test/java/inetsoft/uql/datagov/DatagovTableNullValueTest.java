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
package inetsoft.uql.datagov;

import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.credential.*;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies JSON null values are read as real nulls (bug #81806).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, DatagovTableNullValueTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class DatagovTableNullValueTest {
   @AfterAll
   static void resetContext() {
      ConfigurationContext.getContext().setApplicationContext(null);
   }

   private static JsonObject json(String[] types, Object[]... rows) {
      JsonArrayBuilder cols = Json.createArrayBuilder();

      for(int i = 0; i < types.length; i++) {
         cols.add(Json.createObjectBuilder().add("name", "c" + i)
                     .add("dataTypeName", types[i]));
      }

      JsonArrayBuilder data = Json.createArrayBuilder();

      for(Object[] row : rows) {
         JsonArrayBuilder r = Json.createArrayBuilder();

         for(Object v : row) {
            if(v == null) {
               r.addNull();
            }
            else if(v instanceof Boolean) {
               r.add((Boolean) v);
            }
            else if(v instanceof Number) {
               r.add(((Number) v).doubleValue());
            }
            else {
               r.add(v.toString());
            }
         }

         data.add(r);
      }

      JsonObjectBuilder view = Json.createObjectBuilder().add("columns", cols);
      return Json.createObjectBuilder()
         .add("meta", Json.createObjectBuilder().add("view", view))
         .add("data", data).build();
   }

   @Test
   void allNullRowYieldsRealNulls() {
      String[] types = { "text", "checkbox", "calendar_date", "number", "url",
                         "floating_timestamp" };
      DatagovTable t = new DatagovTable(
         json(types, new Object[] { null, null, null, null, null, null }), 0,
         new DatagovQuery());
      t.next();

      for(int i = 0; i < types.length; i++) {
         assertNull(t.getObject(i), types[i]);
      }
   }

   @Test
   void checkboxBooleanThenNullDoesNotBreakBooleanColumn() {
      DatagovTable t = new DatagovTable(
         json(new String[] { "checkbox" }, new Object[] { true },
              new Object[] { null }, new Object[] { false }), 0, new DatagovQuery());
      assertEquals(Boolean.class, t.getType(0));

      // same cast XBooleanColumn.addObject performs on each value
      while(t.next()) {
         Object o = t.getObject(0);
         assertDoesNotThrow(() -> {
            if(o != null) {
               ((Boolean) o).booleanValue();
            }
         });
      }

      t.rewind();
      t.next();
      t.next();
      assertNull(t.getObject(0));
   }

   @Test
   void nullFirstColumnKeepsMetadataType() {
      DatagovTable t = new DatagovTable(
         json(new String[] { "checkbox", "calendar_date" },
              new Object[] { null, null }, new Object[] { true, "2024-01-02T03:04:05" }),
         0, new DatagovQuery());
      assertEquals(Boolean.class, t.getType(0));
      assertEquals(Date.class, t.getType(1));
   }

   @Test
   void literalNullStringStaysText() {
      DatagovTable t = new DatagovTable(
         json(new String[] { "text" }, new Object[] { "null" }), 0,
         new DatagovQuery());
      t.next();
      assertEquals("null", t.getObject(0));
   }

   @Configuration
   static class TestConfig {
      @Bean
      public CredentialService credentialService() {
         CredentialService credentialService = mock(CredentialService.class);
         when(credentialService.createCredential(CredentialType.PASSWORD)).thenReturn(mock(LocalPasswordCredential.class));
         when(credentialService.createCredential(CredentialType.PASSWORD, false)).thenReturn(mock(LocalPasswordCredential.class));
         return credentialService;
      }
   }
}

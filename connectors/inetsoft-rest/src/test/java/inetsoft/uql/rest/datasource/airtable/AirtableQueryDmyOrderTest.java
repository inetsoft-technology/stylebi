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
package inetsoft.uql.rest.datasource.airtable;

import inetsoft.test.*;
import inetsoft.uql.tabular.RestParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bug #81424: the date order of Airtable's text dates follows the User Locale parameter.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class AirtableQueryDmyOrderTest {
   private static AirtableQuery query(String userLocale) {
      AirtableQuery query = new AirtableQuery();
      query.setEndpoint("Records");
      RestParameters parameters = query.getParameters();
      parameters.findParameter("User Locale").setValue(userLocale);
      query.setParameters(parameters);
      return query;
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource({
      "en,false",
      "en-us,false",
      "en-gb,true",
      "en_GB,true",
      "fr,true",
      "es,true",
      "de,true"
   })
   void dayFirstFollowsUserLocale(String userLocale, boolean expected) {
      assertEquals(expected, query(userLocale).getDmyOrder());
   }

   @Test
   void noUserLocaleDeclaresNoOrder() {
      assertNull(query(null).getDmyOrder());
      assertNull(query("").getDmyOrder());
      assertNull(query("  ").getDmyOrder());
   }
}

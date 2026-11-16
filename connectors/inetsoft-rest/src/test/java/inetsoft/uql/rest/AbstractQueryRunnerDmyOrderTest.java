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
package inetsoft.uql.rest;

import inetsoft.test.*;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.datasource.airtable.AirtableQuery;
import inetsoft.uql.tabular.RestParameters;
import inetsoft.uql.util.BaseJsonTable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81424: the runner hands the order the query declares to the table it builds, so an
 * Airtable table reads its text dates in the order of the User Locale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
class AbstractQueryRunnerDmyOrderTest {
   @ParameterizedTest(name = "{0}")
   @CsvSource({
      "en,10/5/2026",
      "en-gb,5/10/2026",
      "fr,5/10/2026"
   })
   void airtableTableReadsTextDatesInTheUserLocaleOrder(String userLocale, String text) {
      AirtableQuery query = new AirtableQuery();
      query.setEndpoint("Records");
      RestParameters parameters = query.getParameters();
      parameters.findParameter("User Locale").setValue(userLocale);
      query.setParameters(parameters);

      AbstractQueryRunner<AirtableQuery> runner = new AbstractQueryRunner<>(query) {
         @Override
         public XTableNode runStream() {
            return null;
         }

         @Override
         public boolean isCancelled() {
            return false;
         }

         @Override
         public boolean isLiveMode() {
            return false;
         }

         @Override
         public long getTouchTimestamp() {
            return 0;
         }
      };

      BaseJsonTable table = runner.getTable();
      table.load(List.of(Map.of("Due Date", text)));
      table.rewind();

      assertEquals(true, table.next());
      assertEquals(Date.valueOf(LocalDate.of(2026, 10, 5)), table.getObject(0));
   }
}

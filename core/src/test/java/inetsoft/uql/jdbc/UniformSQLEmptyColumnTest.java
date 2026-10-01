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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL generation for "{...}" escapes that are not valid date, time or timestamp literals,
 * and for an empty quoted identifier. The parser used to drop such an escape, which left an
 * empty column in the select list, and quoting the columns of a condition expression looped
 * forever on an empty column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLEmptyColumnTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "select {ts '2020-01-0 10:00:00'} al0 from t where name like 'x'",
      "select {fn now()} from t where name like 'x'",
      "select \"\" from t where name like 'x'"
   })
   void generationFinishesWithEmptyColumn(String input) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(input, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      String generated = assertTimeoutPreemptively(Duration.ofSeconds(10), sql::getSQLString);

      assertTrue(generated.matches("(?is).*\\bwhere\\s+name\\s+like\\s+'x'.*"), generated);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
      "select {fn now()} al0 from t | {fn now()}",
      "select {ts '2020-01-0 10:00:00'} from t | {ts '2020-01-0 10:00:00'}",
      "select a from t where a = {ts '2020-01-0 10:00:00'} | {ts '2020-01-0 10:00:00'}",
      "select a from t where a = {d '2020-01-01'} | {d '2020-01-01'}"
   })
   void escapeIsKept(String input, String escape) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(input, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      String generated = assertTimeoutPreemptively(Duration.ofSeconds(10), sql::getSQLString);

      assertTrue(generated.contains(escape), generated);
   }
}

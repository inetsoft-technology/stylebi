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
package com.inetsoft.connectors;

import inetsoft.uql.tabular.Property;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #82215: the script is mandatory, so the query contract (which is built from the
 * property annotations) refuses a query without one before any call to the R server.
 */
class RQueryContractTest {
   @Test
   void scriptIsARequiredProperty() throws Exception {
      Property property = RQuery.class.getMethod("getScript").getAnnotation(Property.class);

      assertTrue(property.required(), "the R script must be a required property");
   }
}

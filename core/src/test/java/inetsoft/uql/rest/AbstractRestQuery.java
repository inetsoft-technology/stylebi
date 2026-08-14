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
package inetsoft.uql.rest;

import inetsoft.uql.tabular.TabularQuery;

/**
 * Test-only stand-in for the REST base class, which really lives in the inetsoft-rest connector.
 *
 * <p>Core does not depend on that module, which is exactly why the classifier matches it by name.
 * A class with the same fully-qualified name here lets that name-matching be exercised; if the real
 * class is ever moved or renamed, this file is the reminder that the constant must move with it.</p>
 */
public abstract class AbstractRestQuery extends TabularQuery {
   protected AbstractRestQuery(String type) {
      super(type);
   }
}

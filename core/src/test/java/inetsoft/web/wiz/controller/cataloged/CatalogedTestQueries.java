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
package inetsoft.web.wiz.controller.cataloged;

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.tabular.TabularQuery;

/**
 * Query classes that ship an endpoints.json, which is what makes a connector a catalogue.
 *
 * <p>They live in their own package because the classifier resolves that file RELATIVE TO THE
 * QUERY CLASS. Keeping them beside the other test queries would put the catalogue on every one of
 * them and classify the lot as catalogues -- which is exactly what happened before this package
 * existed.</p>
 */
public final class CatalogedTestQueries {
   private CatalogedTestQueries() {
   }

   public static class CatalogedQuery extends TabularQuery {
      public CatalogedQuery() {
         super("TEST");
      }
   }

   /** Both a catalogue and a REST query, which is the ordering case that matters. */
   public static class CatalogedRestQuery extends AbstractRestQuery {
      public CatalogedRestQuery() {
         super("TEST");
      }
   }
}

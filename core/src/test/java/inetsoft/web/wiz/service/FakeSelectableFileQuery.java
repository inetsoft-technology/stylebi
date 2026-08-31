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
package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.uql.tabular.SelectableTabularQuery;

/**
 * Minimal, REAL (non-mock) stand-in for a {@link SelectableTabularQuery} such as
 * {@code ServerFileQuery} -- exists only so {@code WorksheetTableService.sandboxSampleLimit}'s
 * {@code SelectableTabularQuery.class.isAssignableFrom(query.getClass())} test has something to
 * answer {@code true} for, without depending on the {@code inetsoft-serverfile} connector
 * module (core does not depend on it).
 */
public class FakeSelectableFileQuery extends SelectableTabularQuery {
   public FakeSelectableFileQuery() {
      super("FakeSelectableFile");
   }

   @Override
   protected ColumnDefinition[] loadColumns() {
      return new ColumnDefinition[0];
   }
}

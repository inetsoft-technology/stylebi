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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * A connector whose {@code delimiter} default is derived from its data source, the way a real
 * connector's getter may read the data source's own settings: {@code ";"} when the data source is
 * named {@code "semi"}, {@code ","} otherwise, and a failure when there is no data source at all.
 */
@View(vertical = true, value = {
   @View1("delimiter"),
   @View1("header"),
})
public class FakeDataSourceDefaultQuery extends FakeDelimitedFileQuery {
   @Override
   @Property(label = "Delimiter")
   public String getDelimiter() {
      if(getDataSource() == null) {
         throw new IllegalStateException("the delimiter default needs the data source");
      }

      String value = super.getDelimiter();
      return value == null || value.equals(",") && "semi".equals(getDataSource().getName())
         ? ";" : value;
   }
}

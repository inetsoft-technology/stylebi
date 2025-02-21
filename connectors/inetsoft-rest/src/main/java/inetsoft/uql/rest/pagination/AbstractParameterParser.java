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
package inetsoft.uql.rest.pagination;

import java.io.IOException;
import java.net.URL;

/**
 * Skeletal ParameterParser implementation where each operation throws
 * {@link UnsupportedOperationException}.
 */
public class AbstractParameterParser implements ParameterParser {
   @Override
   public int parseInt(String value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean parseBoolean(String value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String parseString(String value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object parseObject(String value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public URL parseURL(PaginationParameter name) throws IOException {
      throw new UnsupportedOperationException();
   }
}

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
package inetsoft.uql.jdbc;

import java.util.Set;

public class DremioHelper extends SQLHelper {
   @Override
   public String getSQLHelperType() {
      return "dremio";
   }

   @Override
   protected String fixTableAlias(Object name, String alias, int maxlen, int index,
                                  Set<String> aliases)
   {
      String nalias = alias;

      if(alias.equals(name)) {
         String table = (String) name;
         int idx = table.lastIndexOf('.');

         if(idx >= 0) {
            String prefix = nalias.substring(idx + 1);
            nalias = prefix;

            for(int i = 1; aliases.contains(nalias); i++) {
               nalias = prefix + i;
            }
         }
      }

      return super.fixTableAlias(name, nalias, maxlen, index, aliases);
   }
}

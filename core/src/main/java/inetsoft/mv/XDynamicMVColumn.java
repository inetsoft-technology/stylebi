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
package inetsoft.mv;

/**
 * XDynamicMVColumn, represents one dynamic column created for mv.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XDynamicMVColumn {
   /**
    * Get the name of this dynamic mv column.
    */
   public String getName();

   /**
    * Convert raw value to range value.
    */
   public Object convert(Object obj);

   /**
    * Get the base mv column.
    */
   public MVColumn getBase();
}

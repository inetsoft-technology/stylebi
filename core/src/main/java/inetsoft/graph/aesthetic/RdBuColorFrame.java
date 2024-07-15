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
package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;

/**
 * This class defines a sequential diverging color frame for numeric values
 * using ColorBrewer scale.
 *
 * @version 12.3
 * @author InetSoft Technology
 */
public class RdBuColorFrame extends AbstractSplineColorFrame {
   @Override
   protected String[] getColorRamps() {
      return new String[] {
         "ef8a62f7f7f767a9cf",
         "ca0020f4a58292c5de0571b0",
         "ca0020f4a582f7f7f792c5de0571b0",
         "b2182bef8a62fddbc7d1e5f067a9cf2166ac",
         "b2182bef8a62fddbc7f7f7f7d1e5f067a9cf2166ac",
         "b2182bd6604df4a582fddbc7d1e5f092c5de4393c32166ac",
         "b2182bd6604df4a582fddbc7f7f7f7d1e5f092c5de4393c32166ac",
         "67001fb2182bd6604df4a582fddbc7d1e5f092c5de4393c32166ac053061",
         "67001fb2182bd6604df4a582fddbc7f7f7f7d1e5f092c5de4393c32166ac053061"};
   }

   private static final long serialVersionUID = 1L;
}

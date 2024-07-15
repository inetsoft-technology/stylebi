/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.analytic.composition.command;


/**
 * Set stack order command.
 *
 * @version 9.6
 * @author InetSoft Technology Corp
 */
public class SetLayerIndexCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public SetLayerIndexCommand() {
      super();
   }

   /**
    * Constructor.
    * @param name the assembly name.
    * @param index the layer index.
    */
   public SetLayerIndexCommand(String name, int index) {
      this(name, index + "");
   }

   /**
    * Constructor, set multiple objects index.
    * @param names the all assemblies' name, delimiter is "::".
    * @param indexes the index for each assembly, delimiter is "::".
    */
   public SetLayerIndexCommand(String names, String indexes) {
      this();
      put("names", names);
      put("indexes", indexes);
   }
}

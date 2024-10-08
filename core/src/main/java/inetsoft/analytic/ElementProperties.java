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
package inetsoft.analytic;

import inetsoft.report.internal.info.ElementInfo;
import inetsoft.util.XMLSerializable;

/**
 * Base class for holding and transmitting element properties.
 * @version Nov 26, 2003
 * @author haiqiangy@inetsoft.com
 */
public abstract class ElementProperties implements XMLSerializable {
   /**
    * Get the ElementInfo used by this properties object for storing
    * properties information.
    */
   public abstract ElementInfo getElementInfo();

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   public abstract String getTagName();
}

/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.AssetObject;

/**
 * MirrroAssemblyInfo stores basic mirror assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface MirrorAssemblyInfo extends AssetObject {
   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   public MirrorAssemblyImpl getImpl();

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   public void setImpl(MirrorAssemblyImpl impl);
}
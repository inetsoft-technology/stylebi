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
package inetsoft.analytic.composition;

import inetsoft.sree.internal.HTMLUtil;
import inetsoft.util.XPortalHelper;

import java.io.IOException;
import java.io.InputStream;

/**
 * The XPortalHelper for viewsheet.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSPortalHelper implements XPortalHelper {
   /**
    * Create an instance.
    */
   public VSPortalHelper() {
      super();
   }

   /**
    * Get the portal resource.
    */
   @Override
   public InputStream getPortalResource(String name, boolean applyStyle,
                                        boolean applyTheme)
      throws IOException
   {
      return HTMLUtil.getPortalResource(name, applyStyle, applyTheme);
   }
}
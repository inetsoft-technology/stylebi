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
package inetsoft.report.internal;

import inetsoft.report.lens.DefaultTextLens;

import java.util.Locale;

/**
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class HeaderTextLens extends DefaultTextLens {
   /**
    * Get the text content after formatting.
    * @return text string.
    */
   public abstract String getDisplayText(Locale locale);

   /**
    * Get the text content after formatting.
    * @param formatExpre format the expression or not, such as {P} to {1}.
    * @return text string.
    */
   public abstract String getDisplayText(Locale locale, boolean formatExpre);
}


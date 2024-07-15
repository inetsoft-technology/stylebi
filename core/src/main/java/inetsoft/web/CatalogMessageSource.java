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
package inetsoft.web;

import inetsoft.util.Catalog;
import inetsoft.util.ThreadContext;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * <tt>MessageSource</tt> implementation that delegates to {@link Catalog}
 */
public class CatalogMessageSource extends AbstractMessageSource {
   @Override
   protected String resolveCodeWithoutArguments(String code, Locale locale) {
      return getCatalog(locale).getString(code);
   }

   @Override
   protected MessageFormat resolveCode(String code, Locale locale) {
      MessageFormat format = null;
      String value = getCatalog(locale).getString(code);

      if(value != null) {
         format = new MessageFormat(value);
      }

      return format;
   }

   /**
    * Gets the catalog for the specified locale.
    *
    * @param locale the locale.
    *
    * @return the catalog.
    */
   private Catalog getCatalog(Locale locale) {
      ThreadContext.setLocale(locale);

      try {
         return Catalog.getCatalog();
      }
      finally {
         ThreadContext.setLocale(null);
      }
   }
}

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
      return resolve(code, locale);
   }

   @Override
   protected MessageFormat resolveCode(String code, Locale locale) {
      MessageFormat format = null;
      String value = resolve(code, locale);

      if(value != null) {
         format = new MessageFormat(value);
      }

      return format;
   }

   /**
    * Resolves a message code against the catalog. An unknown code normally resolves to the
    * code itself, but an unknown {@code problemDetail.*} code resolves to {@code null}. Spring's
    * {@link org.springframework.web.ErrorResponse#updateAndGetBody} asks for those codes with no
    * default message and keeps the exception's own type, title and detail only when the lookup
    * returns {@code null}; echoing the code would replace the detail with the raw code.
    *
    * @param code   the message code.
    * @param locale the locale.
    *
    * @return the message, or {@code null} for an unknown {@code problemDetail.*} code.
    */
   private String resolve(String code, Locale locale) {
      Catalog catalog = getCatalog(locale);

      if(code != null && code.startsWith(PROBLEM_DETAIL_PREFIX) &&
         catalog.getIDString(code) == null)
      {
         return null;
      }

      return catalog.getString(code);
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

   private static final String PROBLEM_DETAIL_PREFIX = "problemDetail.";
}

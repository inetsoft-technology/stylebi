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
package inetsoft.report.pdf;

import inetsoft.util.Catalog;
import inetsoft.util.ThreadContext;

import java.io.OutputStream;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Specialization of {@link inetsoft.report.PDFPrinter} that generates files in
 * version 1.7 of the PDF specification.
 *
 * @author InetSoft Technology
 * @since  11.4
 */
public class PDF8Printer extends PDF4Printer {
   /**
    * Creates a new instance of <tt>PDF8Printer</tt>.
    */
   public PDF8Printer() {
      super();
   }

   /**
    * Creates a new instance of <tt>PDF8Printer</tt>.
    *
    * @param out the output stream to which the PDF will be written.
    */
   public PDF8Printer(OutputStream out) {
      super(out);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public String getPDFVersion() {
      return "1.7";
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected String getNaturalLanguage() {
      String lang;

      try {
         Locale locale = getReportLocale();

         if(locale == null || Locale.getDefault().equals(locale)) {
            locale = Catalog.getCatalog(ThreadContext.getContextPrincipal()).getLocale();
         }

         if(locale == null) {
            locale = Locale.getDefault();
         }

         ResourceBundle bundle = ResourceBundle.getBundle("SreeBundle", locale);
         // The bundle exists, use the its locale. This locale may be
         // different from that requested if there is no bundle for that
         // locale (uses the default).
         locale = bundle.getLocale();
         lang = locale.getLanguage() + "-" + locale.getCountry();
      }
      catch(Exception useDefault) {
         // The "SreeBundle" resource does not exist, so the report will not
         // be localized. We'll assume that the natural language of the
         // report is the same as the default locale for this machine.
         Locale locale = Locale.getDefault();
         lang = locale.getLanguage() + "-" + locale.getCountry();
      }

      return "/Lang (" + lang + ")";
   }
}

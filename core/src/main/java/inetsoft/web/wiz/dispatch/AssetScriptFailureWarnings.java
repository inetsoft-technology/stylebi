/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.dispatch;

import inetsoft.report.composition.WorksheetService;
import inetsoft.util.script.ScriptException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Bug #79623: a viewsheet reset runs every assembly script and swallows a failure into the
 * thread-local {@link WorksheetService#ASSET_EXCEPTIONS}; only the Composer's
 * {@code @HandleAssetExceptions} aspect turns that list into commands, and the wiz refresh path
 * does not go through it. So a Text, chart, selection or input script that failed during a
 * refresh was invisible to the caller. This scopes a fresh list around the refresh and reports
 * its script failures as warnings.
 */
public final class AssetScriptFailureWarnings {
   private AssetScriptFailureWarnings() {
   }

   /**
    * Runs {@code refresh} with its own {@code ASSET_EXCEPTIONS} list, restores the previous one,
    * and returns the refresh's own warnings plus one warning per assembly whose script failed
    * and is not already reported there. Only a {@link ScriptException}, or an exception caused
    * by one, is reported; data and query errors are not.
    */
   public static List<String> collect(Callable<List<String>> refresh) throws Exception {
      List<Exception> previous = WorksheetService.ASSET_EXCEPTIONS.get();
      WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
      List<Exception> exceptions = null;
      List<String> warnings;

      try {
         warnings = refresh.call();
         exceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      }
      finally {
         if(previous == null) {
            WorksheetService.ASSET_EXCEPTIONS.remove();
         }
         else {
            WorksheetService.ASSET_EXCEPTIONS.set(previous);
         }
      }

      List<String> result = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);

      if(exceptions != null) {
         for(Exception ex : exceptions) {
            if(ex instanceof ScriptException || ex != null && ex.getCause() instanceof ScriptException) {
               String message = ex.getMessage();

               if((message == null || message.isBlank()) && ex.getCause() != null) {
                  message = ex.getCause().getMessage();
               }

               message = CommandErrorException.messageOf(message);

               if(!reported(result, message)) {
                  result.add(message);
               }
            }
         }
      }

      return result;
   }

   /** A table failure already arrives as an INFO warning carrying the same first line. */
   private static boolean reported(Collection<String> warnings, String message) {
      String head = firstLine(message);

      for(String warning : warnings) {
         if(firstLine(warning).equals(head)) {
            return true;
         }
      }

      return false;
   }

   private static String firstLine(String text) {
      int index = text.indexOf('\n');
      return (index < 0 ? text : text.substring(0, index)).trim();
   }
}

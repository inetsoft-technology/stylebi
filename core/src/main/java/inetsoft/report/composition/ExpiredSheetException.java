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
package inetsoft.report.composition;

import java.security.Principal;

/**
 * Exception that is thrown when an expired runtime worksheet or viewsheet is accessed.
 */
public class ExpiredSheetException extends RuntimeException {
   /**
    * Creates a new instance of {@code ExpiredSheetException}.
    *
    * @param id   the runtime identifier of the sheet.
    * @param user a principal that identifies the user that owns the sheet.
    */
   public ExpiredSheetException(String id, Principal user) {
      super(String.format("Sheet [%s] has expired", id != null && id.indexOf("-") > -1 ?
         id.substring(0, id.lastIndexOf("-")) : id));

      this.id = id;
      this.user = user;
   }

   /**
    * Gets the runtime identifier of the sheet.
    *
    * @return the identifier.
    */
   public String getId() {
      return id;
   }

   /**
    * Gets a principal that identifies the user that owns the sheet.
    *
    * @return the user principal.
    */
   public Principal getUser() {
      return user;
   }

   private final String id;
   private final Principal user;
}

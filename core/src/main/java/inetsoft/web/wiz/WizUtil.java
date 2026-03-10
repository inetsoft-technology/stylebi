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

package inetsoft.web.wiz;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WizUtil {
   public static String decodeId(String id) {
      String decodedId;

      if(id == null || id.isEmpty()) {
         decodedId = null;
      }
      else {
         try {
            decodedId = new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8);
         }
         catch(IllegalArgumentException e) {
            decodedId = null;
         }
      }

      return decodedId;
   }

   public static final String ANNOTATION_RAW_DATA_MAX_ROW = "annotation.rawdata.maxrow";
}

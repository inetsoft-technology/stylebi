/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.admin.general.model.model;

public enum SMTPAuthType {
   NONE("none"),
   SMTP_AUTH("smtpAuth"),
   SASL_XOAUTH2("saslXOauth2"),
   GOOGLE_AUTH("googleAuth");

   private final String value;

   SMTPAuthType(String value) {
      this.value = value;
   }

   public String value() {
      return value;
   }

   public static SMTPAuthType forValue(String value) {
      SMTPAuthType result = null;

      for(SMTPAuthType type : values()) {
         if(type.value.equals(value)) {
            result = type;
            break;
         }
      }

      if(result == null && value.equalsIgnoreCase("true")) {
         result = SMTP_AUTH;
      }

      if(result == null) {
         result = NONE;
      }

      return result;
   }
}

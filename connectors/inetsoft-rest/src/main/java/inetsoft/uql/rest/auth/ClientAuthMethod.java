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
package inetsoft.uql.rest.auth;

/**
 * How the client ID and secret are sent to the token endpoint in the OAuth 2.0 client
 * credentials grant (RFC 6749 section 2.3.1).
 */
public enum ClientAuthMethod {
   /**
    * Send the credentials in an HTTP Basic authorization header (client_secret_basic).
    */
   BASIC,
   /**
    * Send the credentials as client_id and client_secret form parameters in the request body
    * (client_secret_post).
    */
   POST
}

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


package inetsoft.web.wiz.model;

/**
 * The outcome of creating or updating an XMLA data source. Same shape and codes as
 * {@link WizTabularSaveResult}: {@code ok}, a stable {@code reason} (null when ok; otherwise
 * {@code DUPLICATE_NAME}, {@code INVALID_FOLDER}, {@code DATASOURCE_LOST} or {@code UNKNOWN}), the
 * saved {@code path} (the new path after a rename) and the server's own {@code message} when it
 * refused with one.
 */
public record WizXmlaSaveResult(boolean ok, String reason, String path, String message) {
   public static WizXmlaSaveResult ok(String path) {
      return new WizXmlaSaveResult(true, null, path, null);
   }

   public static WizXmlaSaveResult failed(String reason) {
      return new WizXmlaSaveResult(false, reason, null, null);
   }

   public static WizXmlaSaveResult failed(String reason, String message) {
      return new WizXmlaSaveResult(false, reason, null, message);
   }

   public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
   public static final String INVALID_FOLDER = "INVALID_FOLDER";
   public static final String DATASOURCE_LOST = "DATASOURCE_LOST";
   public static final String UNKNOWN = "UNKNOWN";
}

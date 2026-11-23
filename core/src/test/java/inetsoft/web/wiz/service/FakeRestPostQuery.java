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

package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * Stand-in for the generic REST query: no {@code endpoint} property, and a plain, directly
 * settable {@code requestBody} next to the fixed-list {@code requestType} and
 * {@code contentType}.
 *
 * <p>{@code @View} is REQUIRED -- see {@link FakeNamedConnectorQuery}'s own doc for why.</p>
 */
@View(vertical = true, value = {
   @View1("suffix"),
   @View1("requestType"),
   @View1("contentType"),
   @View1("requestBody"),
})
public class FakeRestPostQuery extends TabularQuery {
   public FakeRestPostQuery() {
      super("FakeRestPost");
   }

   @Property(label = "Suffix")
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Property(label = "Request Type")
   @PropertyEditor(tags = {"GET", "POST"})
   public String getRequestType() {
      return requestType;
   }

   public void setRequestType(String requestType) {
      this.requestType = requestType;
   }

   @Property(label = "Content Type")
   @PropertyEditor(tags = {
      "application/json", "application/xml", "application/x-www-form-urlencoded", "text/plain",
      "text/xml"})
   public String getContentType() {
      return contentType;
   }

   public void setContentType(String contentType) {
      this.contentType = contentType;
   }

   @Property(label = "Request Body")
   @PropertyEditor(dependsOn = "requestType")
   public String getRequestBody() {
      return requestBody;
   }

   public void setRequestBody(String requestBody) {
      this.requestBody = requestBody;
   }

   private String suffix;
   private String requestType = "GET";
   private String contentType = "application/json";
   private String requestBody;
}

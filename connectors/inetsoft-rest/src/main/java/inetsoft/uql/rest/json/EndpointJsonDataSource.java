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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.HttpResponse;
import org.w3c.dom.Element;

import java.io.PrintWriter;

public abstract class EndpointJsonDataSource<SELF extends EndpointJsonDataSource<SELF>>
   extends AbstractRestDataSource<SELF>
{
   public EndpointJsonDataSource(String type, Class<SELF> selfClass) {
      super(type, selfClass);
   }

   protected abstract String getTestSuffix();

   protected boolean isIgnoreBaseUrlForTest() {
      return false;
   }

   protected void validateTestResponse(HttpResponse response) throws Exception {
      if(response.getResponseStatusCode() >= 400) {
         throw new Exception(
            "Test request returned " + response.getResponseStatusCode() + ": " +
               response.getResponseStatusText());
      }
   }

   public double getRequestsPerSecond() {
      return requestsPerSecond;
   }

   public void setRequestsPerSecond(double requestsPerSecond) {
      this.requestsPerSecond = requestsPerSecond;
   }

   public int getMaxConnections() {
      return maxConnections;
   }

   public void setMaxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(requestsPerSecond > 0D || maxConnections > 0) {
         writer.print("<quota");

         if(requestsPerSecond >= 0D) {
            writer.format(" requestsPerSecond=\"%.3f\"", requestsPerSecond);
         }

         if(maxConnections > 0) {
            writer.format(" maxConnections=\"%d\"", maxConnections);
         }

         writer.println("/>");
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
   }

   private double requestsPerSecond = -1D;
   private int maxConnections = -1;
}

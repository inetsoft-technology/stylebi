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
package inetsoft.web.viewsheet.service;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

public class ExportResponse {
   public ExportResponse(OutputStream output) {
      this.output = output;
      this.response = null;
   }

   public ExportResponse(HttpServletResponse response) {
      this.response = response;
      this.output = null;
   }

   public boolean isHttp() {
      return response != null;
   }

   public void setHeader(String name, String value) {
      if(response == null) {
         throw new IllegalStateException("Header cannot be set on direct export response");
      }

      response.setHeader(name, value);
   }

   public void setContentType(String type) {
      if(response == null) {
         throw new IllegalStateException("Header cannot be set on direct export response");
      }

      response.setContentType(type);
   }

   public void setContentLength(int length) {
      if(response == null) {
         throw new IllegalStateException("Header cannot be set on direct export response");
      }

      response.setContentLength(length);
   }

   public OutputStream getOutputStream() throws IOException {
      if(response != null) {
         return response.getOutputStream();
      }
      else {
         return output;
      }
   }

   private final OutputStream output;
   private final HttpServletResponse response;
}

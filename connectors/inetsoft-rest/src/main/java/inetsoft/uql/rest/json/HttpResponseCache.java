/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.json;

import inetsoft.mv.data.TwoLevelCache;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.SerializableHttpResponse;

import java.io.*;

public class HttpResponseCache extends TwoLevelCache<String, HttpResponse> {
   public HttpResponseCache() {
      super(10, 2000);
      setId("RestResponseCache");
   }

   public HttpResponse get(String key, boolean livemode) {
      return get(key, livemode ? -1L : System.currentTimeMillis() - getL2Timeout());
   }

   @Override
   public void put(String key, HttpResponse response) {
      // Response has to be turned into a serializable response, so this is not allowed.
      throw new UnsupportedOperationException();
   }

   /**
    * Converts the response into a Serializable response and adds it to this cache.
    *
    * @param key      a key derived from the rest request.
    * @param response the response to cache.
    */
   public HttpResponse putResponse(String key, HttpResponse response) throws IOException {
      final SerializableHttpResponse serializableResponse = new SerializableHttpResponse(response);
      super.put(key, serializableResponse);
      return serializableResponse;
   }

   protected long getL2Timeout() {
      final Long timeout = SreeEnv.getLong("rest.cache.timeout.millis");
      return timeout != null ? timeout : 0;
   }

   @Override
   protected boolean isDemoted(File file) {
      // support restartability
      return file.exists();
   }

   @Override
   protected void writeObject(OutputStream output, HttpResponse response) throws IOException {
      ObjectOutputStream oout = new ObjectOutputStream(output);
      oout.writeObject(response);
      oout.flush();
   }

   @Override
   protected HttpResponse readObject(InputStream input) {
      try {
         ObjectInputStream oin = new ObjectInputStream(input);
         return (HttpResponse) oin.readObject();
      }
      catch(Exception e) {
         e.printStackTrace();
         return null;
      }
   }
}

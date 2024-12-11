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

package inetsoft.util.credential;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

@JsonSerialize(using = CloudClientTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudClientTokenCredential.Deserializer.class)
public class CloudClientTokenCredential extends AbstractCloudCredential
   implements CloudCredential, ClientTokenCredential
{
   @Override
   public String getClientId() {
      return clientId;
   }

   @Override
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Override
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken) && StringUtils.isEmpty(clientId);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
      clientId = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudClientTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudClientTokenCredential) obj).accessToken, accessToken) &&
         Tool.equals(((CloudClientTokenCredential) obj).clientId, clientId);
   }

   private String accessToken = "";
   private String clientId = "";
}

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

package inetsoft.util.config;

import java.io.Serializable;

@InetsoftConfigBean
public class IpFinderConfig implements Serializable {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public AwsElbIpFinderConfig getAwsElb() {
      return awsElb;
   }

   public void setAwsElb(AwsElbIpFinderConfig awsElb) {
      this.awsElb = awsElb;
   }

   public GoogleGCSIpFinderConfig getGoogleGcs() {
      return googleGcs;
   }

   public void setGoogleGcs(GoogleGCSIpFinderConfig googleGcs) {
      this.googleGcs = googleGcs;
   }

   public AzureIpFinderConfig getAzureBlob() {
      return azureBlob;
   }

   public void setAzureBlob(AzureIpFinderConfig azureBlob) {
      this.azureBlob = azureBlob;
   }

   private String type;
   private AwsElbIpFinderConfig awsElb;
   private GoogleGCSIpFinderConfig googleGcs;
   private AzureIpFinderConfig azureBlob;
}

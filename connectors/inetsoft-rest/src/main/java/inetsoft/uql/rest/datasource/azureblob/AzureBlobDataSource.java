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
package inetsoft.uql.rest.datasource.azureblob;

import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "accountName", visibleMethod = "useCredential"),
   @View1(value = "URL", visibleMethod = "useCredential"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "storageAccountKey", align = ViewAlign.FILL, visibleMethod = "useCredential"),
      @View2(type = ViewType.BUTTON, text = "Generate Signature",
         button = @Button(type = ButtonType.METHOD, method = "generateSignature",
                          dependsOn = {"storageAccountKey", "accountName", "credentialId"},
                          enabledMethod = "generateSignatureEnabled"))
   }),
   @View1(type = ViewType.LABEL, text = "storageAccountKey.description", colspan = 2, wrap = true),
   @View1(value = "signatureServicesVersion"),
   @View1(value = "signatureServices"),
   @View1(value = "signatureResourceTypes"),
   @View1(value = "signatureStartTime"),
   @View1(value = "signatureExpiryTime"),
   @View1(value = "signaturePermissions"),
   @View1(value = "signatureIpRange"),
   @View1(value = "signatureProtocol"),
   @View1(value = "signature")
})
public class AzureBlobDataSource extends AbstractRestDataSource<AzureBlobDataSource> {
   static final String TYPE = "Rest.AzureBlob";

   public AzureBlobDataSource() {
      super(TYPE, AzureBlobDataSource.class);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.SIGNATURE;
   }

   @Property(label = "Account Name", required = true)
   public String getAccountName() {
      return this.accountName;
   }

   public void setAccountName(String accountName) {
      this.accountName = accountName;
   }

   @Property(label = "Storage Account Key", password = true)
   public String getStorageAccountKey() {
      if(((SignatureCredential) getCredential()).getAccountKey() != null) {
         return ((SignatureCredential) getCredential()).getAccountKey();
      }

      return this.storageAccountKey;
   }

   public void setStorageAccountKey(String storageAccountKey) {
      if(getCredential() instanceof LocalSignatureCredential) {
         ((SignatureCredential) getCredential()).setAccountKey(storageAccountKey);
      }
      // if cloud secret only contains account key, we need to local persistent for signature.
      else if(getCredential() instanceof CloudCredential &&
         ((SignatureCredential) getCredential()).getSignature() == null)
      {
         this.storageAccountKey = storageAccountKey;
      }
   }

   @Property(label = "Signature Services Version", required = true)
   public String getSignatureServicesVersion() {
      return signatureServicesVersion;
   }

   public void setSignatureServicesVersion(String signatureServicesVersion) {
      this.signatureServicesVersion = signatureServicesVersion;
   }

   @Property(label = "Signature Services", required = true)
   public String getSignatureServices() {
      return signatureServices;
   }

   public void setSignatureServices(String signatureServices) {
      this.signatureServices = signatureServices;
   }

   @Property(label = "Signature Resource Types", required = true)
   public String getSignatureResourceTypes() {
      return signatureResourceTypes;
   }

   public void setSignatureResourceTypes(String signatureResourceTypes) {
      this.signatureResourceTypes = signatureResourceTypes;
   }

   @Property(label = "Signature Start Time", required = true)
   public String getSignatureStartTime() {
      return signatureStartTime;
   }

   public void setSignatureStartTime(String signatureStartTime) {
      this.signatureStartTime = signatureStartTime;
   }

   @Property(label = "Signature Expiry Time", required = true)
   public String getSignatureExpiryTime() {
      return signatureExpiryTime;
   }

   public void setSignatureExpiryTime(String signatureExpiryTime) {
      this.signatureExpiryTime = signatureExpiryTime;
   }

   @Property(label = "Signature Permissions", required = true)
   public String getSignaturePermissions() {
      return signaturePermissions;
   }

   public void setSignaturePermissions(String signaturePermissions) {
      this.signaturePermissions = signaturePermissions;
   }

   @Property(label = "Signature IP Range")
   public String getSignatureIpRange() {
      return signatureIpRange;
   }

   public void setSignatureIpRange(String signatureIpRange) {
      this.signatureIpRange = signatureIpRange;
   }

   @Property(label = "Signature Protocol", required = true)
   public String getSignatureProtocol() {
      return signatureProtocol;
   }

   public void setSignatureProtocol(String signatureProtocol) {
      this.signatureProtocol = signatureProtocol;
   }

   @Property(label = "Account SAS Signature", required = true, password = true)
   public String getSignature() {
      if(isCloudSignature() || getCredential() instanceof AbstractLocalCredential) {
         return ((SignatureCredential) getCredential()).getSignature();
      }

      // in case cloud secret only contains account key, generate Signature here.
      if(getStorageAccountKey() != null && getCredential() instanceof CloudCredential) {
         try {
            generateSignature(null);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      }

      return this.signature;
   }

   @Property(label = "URL")
   @PropertyEditor(enabled = false, dependsOn = "accountName")
   @Override
   public String getURL() {
      StringBuilder url = new StringBuilder("https://");

      if(getAccountName() == null) {
         url.append("[Account Name]");
      }
      else {
         url.append(getAccountName());
      }

      return url.append(".blob.core.windows.net").toString();
   }

   @Override
   public void setURL(String url) {
      // no-op
   }

   @Override
   public HttpParameter[] getQueryHttpParameters() {
      String now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
         .format(DateTimeFormatter.ISO_INSTANT);
      List<HttpParameter> params = new ArrayList<>();
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.HEADER)
                    .name("x-ms-date")
                    .value(now)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("sv")
                    .value(signatureServicesVersion)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("ss")
                    .value(signatureServices)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("srt")
                    .value(signatureResourceTypes)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("st")
                    .value(signatureStartTime)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("se")
                    .value(signatureExpiryTime)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("sp")
                    .value(signaturePermissions)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("spr")
                    .value(signatureProtocol)
                    .build());
      params.add(HttpParameter.builder()
                    .type(HttpParameter.ParameterType.QUERY)
                    .name("sig")
                    .value(getSignature())
                    .build());

      if(signatureIpRange != null && !signatureIpRange.isEmpty()) {
         params.add(HttpParameter.builder()
                       .type(HttpParameter.ParameterType.QUERY)
                       .name("sip")
                       .value(signatureIpRange)
                       .build());
      }

      return params.toArray(new HttpParameter[0]);
   }

   @Override
   public void setQueryHttpParameters(HttpParameter[] parameters) {
      // no-op
   }

   public void setSignature(String signature) {
      if(getCredential() instanceof LocalSignatureCredential) {
         ((SignatureCredential) getCredential()).setSignature(signature);
      }
      // if cloud secret only contains account key, we need to local persistent for signature.
      else if(!isCloudSignature()){
         this.signature = signature;
      }
   }

   private boolean isCloudSignature() {
      return getCredential() instanceof CloudCredential &&
         ((SignatureCredential) getCredential()).getSignature() != null;
   }

   public boolean generateSignatureEnabled() {
      return !Tool.isEmptyString(getStorageAccountKey()) && !Tool.isEmptyString(getAccountName()) ||
         isUseCredentialId() && !Tool.isEmptyString(getCredentialId()) ;
   }

   public void generateSignature(String sessionId) throws Exception {
      if(!generateSignatureEnabled()) {
         return;
      }

      OffsetDateTime now =
         OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
      signatureStartTime = now.minus(Duration.ofMinutes(15)).format(DateTimeFormatter.ISO_INSTANT);
      signatureExpiryTime = now.plus(Duration.ofDays(365)).format(DateTimeFormatter.ISO_INSTANT);

      if(signatureServicesVersion == null || signatureServicesVersion.isEmpty()) {
         signatureServicesVersion = "2019-07-07";
      }

      if(signatureServices == null || signatureServices.isEmpty()) {
         signatureServices = "b";
      }

      if(signatureResourceTypes == null || signatureResourceTypes.isEmpty()) {
         signatureResourceTypes = "sco";
      }

      if(signaturePermissions == null || signaturePermissions.isEmpty()) {
         signaturePermissions = "lr";
      }

      if(signatureIpRange == null) {
         signatureIpRange = "";
      }

      if(signatureProtocol == null || signatureProtocol.isEmpty()) {
         signatureProtocol = "https";
      }

      String stringToSign = getAccountName() + "\n" +
         signaturePermissions + "\n" +
         signatureServices + "\n" +
         signatureResourceTypes + "\n" +
         signatureStartTime + "\n" +
         signatureExpiryTime + "\n" +
         signatureIpRange + "\n" +
         signatureProtocol + "\n" +
         signatureServicesVersion + "\n";

      byte[] secret = Base64.getDecoder().decode(getStorageAccountKey());
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
      mac.init(key);

      String signature = Base64.getEncoder().encodeToString(
         mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
      setSignature(signature);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(accountName != null) {
         writer.format("<accountName><![CDATA[%s]]></accountName>%n", accountName);
      }

      if(signatureServicesVersion != null) {
         writer.format(
            "<signatureServicesVersion><![CDATA[%s]]></signatureServicesVersion>%n",
            signatureServicesVersion);
      }

      if(signatureServices != null) {
         writer.format(
            "<signatureServices><![CDATA[%s]]></signatureServices>%n", signatureServices);
      }

      if(signatureResourceTypes != null) {
         writer.format(
            "<signatureResourceTypes><![CDATA[%s]]></signatureResourceTypes>%n",
            signatureResourceTypes);
      }

      if(signatureStartTime != null) {
         writer.format(
            "<signatureStartTime><![CDATA[%s]]></signatureStartTime>%n", signatureStartTime);
      }

      if(signatureExpiryTime != null) {
         writer.format(
            "<signatureExpiryTime><![CDATA[%s]]></signatureExpiryTime>%n", signatureExpiryTime);
      }

      if(signaturePermissions != null) {
         writer.format(
            "<signaturePermissions><![CDATA[%s]]></signaturePermissions>%n", signaturePermissions);
      }

      if(signatureIpRange != null) {
         writer.format("<signatureIpRange><![CDATA[%s]]></signatureIpRange>%n", signatureIpRange);
      }

      if(signatureProtocol != null) {
         writer.format("<signatureProtocol><![CDATA[%s]]></signatureProtocol>%n", signatureProtocol);
      }

      if(signature != null) {
         writer.format("<signature><![CDATA[%s]]></signature>%n", Tool.encryptPassword(signature));
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      accountName = Tool.getChildValueByTagName(root, "accountName");
      signatureServicesVersion = Tool.getChildValueByTagName(root, "signatureServicesVersion");
      signatureServices = Tool.getChildValueByTagName(root, "signatureServices");
      signatureResourceTypes = Tool.getChildValueByTagName(root, "signatureResourceTypes");
      signatureStartTime = Tool.getChildValueByTagName(root, "signatureStartTime");
      signatureExpiryTime = Tool.getChildValueByTagName(root, "signatureExpiryTime");
      signaturePermissions = Tool.getChildValueByTagName(root, "signaturePermissions");
      signatureIpRange = Tool.getChildValueByTagName(root, "signatureIpRange");
      signatureProtocol = Tool.getChildValueByTagName(root, "signatureProtocol");

      String val = Tool.getChildValueByTagName(root, "signature");

      if(val != null) {
         signature = Tool.decryptPassword(val);
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof AzureBlobDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      AzureBlobDataSource that = (AzureBlobDataSource) o;
      return Objects.equals(signatureServicesVersion, that.signatureServicesVersion) &&
         Objects.equals(signatureServices, that.signatureServices) &&
         Objects.equals(signatureResourceTypes, that.signatureResourceTypes) &&
         Objects.equals(signatureStartTime, that.signatureStartTime) &&
         Objects.equals(signatureExpiryTime, that.signatureExpiryTime) &&
         Objects.equals(signaturePermissions, that.signaturePermissions) &&
         Objects.equals(signatureIpRange, that.signatureIpRange) &&
         Objects.equals(signatureProtocol, that.signatureProtocol) &&
         Objects.equals(getSignature(), that.getSignature());
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         super.hashCode(), getAccountName(), signatureServicesVersion, signatureServices,
         signatureResourceTypes, signatureStartTime, signatureExpiryTime, signaturePermissions,
         signatureIpRange, signatureProtocol, getSignature());
   }

   private String accountName;
   private String storageAccountKey;
   private String signatureServicesVersion;
   private String signatureServices;
   private String signatureResourceTypes;
   private String signatureStartTime;
   private String signatureExpiryTime;
   private String signaturePermissions;
   private String signatureIpRange;
   private String signatureProtocol;
   private String signature; // add signature attribute, in case user doesn't provide the signature in secret.
}

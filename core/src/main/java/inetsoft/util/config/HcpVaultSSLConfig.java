package inetsoft.util.config;

@InetsoftConfigBean
public class HcpVaultSSLConfig {
   public String getSslType() {
      return sslType;
   }

   public void setSslType(String sslType) {
      this.sslType = sslType;
   }

   public String getTrustStoreFile() {
      return trustStoreFile;
   }

   public void setTrustStoreFile(String trustStoreFile) {
      this.trustStoreFile = trustStoreFile;
   }

   public String getKeyStoreFile() {
      return keyStoreFile;
   }

   public void setKeyStoreFile(String keyStoreFile) {
      this.keyStoreFile = keyStoreFile;
   }

   public String getKeystorePassword() {
      return keystorePassword;
   }

   public void setKeystorePassword(String keystorePassword) {
      this.keystorePassword = keystorePassword;
   }

   public String getPemFile() {
      return pemFile;
   }

   public void setPemFile(String pemFile) {
      this.pemFile = pemFile;
   }

   public String getClientPemFile() {
      return clientPemFile;
   }

   public void setClientPemFile(String clientPemFile) {
      this.clientPemFile = clientPemFile;
   }

   public String getClientKeyPemFile() {
      return clientKeyPemFile;
   }

   public void setClientKeyPemFile(String clientKeyPemFile) {
      this.clientKeyPemFile = clientKeyPemFile;
   }

   public String getPemUTF8() {
      return pemUTF8;
   }

   public void setPemUTF8(String pemUTF8) {
      this.pemUTF8 = pemUTF8;
   }

   public String getClientPemUTF8() {
      return clientPemUTF8;
   }

   public void setClientPemUTF8(String clientPemUTF8) {
      this.clientPemUTF8 = clientPemUTF8;
   }

   public String getClientKeyPemUTF8() {
      return clientKeyPemUTF8;
   }

   public void setClientKeyPemUTF8(String clientKeyPemUTF8) {
      this.clientKeyPemUTF8 = clientKeyPemUTF8;
   }

   @Override
   public Object clone() {
      HcpVaultSSLConfig clone = new HcpVaultSSLConfig();
      clone.setSslType(sslType);
      clone.setTrustStoreFile(trustStoreFile);
      clone.setKeyStoreFile(keyStoreFile);
      clone.setKeystorePassword(keystorePassword);
      clone.setPemFile(pemFile);
      clone.setClientPemFile(clientPemFile);
      clone.setClientKeyPemFile(clientKeyPemFile);
      clone.setPemUTF8(pemUTF8);
      clone.setClientPemUTF8(clientPemUTF8);
      clone.setClientKeyPemUTF8(clientKeyPemUTF8);

      return clone;
   }

   // SSL type: either JKS or PEM
   private String sslType = SSLType.JKS.getValue();
   // JKS-related configuration
   private String trustStoreFile;        // Path to the JKS truststore file
   private String keyStoreFile;          // Path to the JKS keystore file
   private String keystorePassword;      // Base64 Encoded password for the JKS keystore
   // PEM-related configuration
   private String pemFile;               // Path to the PEM file for the server certificate
   private String clientPemFile;         // Path to the PEM file for the client certificate
   private String clientKeyPemFile;      // Path to the PEM file for the client private key
   private String pemUTF8;               // Base64 Encoded UTF-8 Content of the PEM File for the Server Certificate
   private String clientPemUTF8;         // Base64 Encoded UTF-8 content of the PEM file for the client certificate
   private String clientKeyPemUTF8;      // Base64 Encoded UTF-8 content of the PEM file for the client private key

   public enum SSLType {
      JKS("jks"),
      PEM("pem");

      private final String value;

      SSLType(String value) {
         this.value = value;
      }

      public String getValue() {
         return value;
      }
   }
}

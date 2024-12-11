package inetsoft.util.config;

@InetsoftConfigBean
public class HcpAuthMethodConfig {
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getAppRoleId() {
      return appRoleId;
   }

   public void setAppRoleId(String appRoleId) {
      this.appRoleId = appRoleId;
   }

   public String getAppRoleSecretId() {
      return appRoleSecretId;
   }

   public void setAppRoleSecretId(String appRoleSecretId) {
      this.appRoleSecretId = appRoleSecretId;
   }

   @Override
   public Object clone() {
      HcpAuthMethodConfig clone = new HcpAuthMethodConfig();
      clone.setType(type);
      clone.setToken(token);
      clone.setUsername(username);
      clone.setPassword(password);
      clone.setAppRoleId(appRoleId);
      clone.setAppRoleSecretId(appRoleSecretId);

      return clone;
   }

   private String type;
   // used for token auth
   private String token;
   // used for userpass auth
   private String username;
   private String password;
   // used for approle auth
   private String appRoleId;
   private String appRoleSecretId;
}

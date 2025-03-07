package inetsoft.uql.rest.datasource.mailgun;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import inetsoft.util.credential.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Objects;

@View(vertical = true, value = {
   @View1(value = "useCredentialId", visibleMethod = "supportToggleCredential"),
   @View1(value = "credentialId", visibleMethod = "isUseCredentialId"),
   @View1(value = "URL", visibleMethod = "useCredential"),
   @View1(value = "apiKey", visibleMethod = "useCredential")
})
public class MailgunDataSource extends EndpointJsonDataSource<MailgunDataSource> {
   public static final String TYPE = "Rest.Mailgun";

   public MailgunDataSource() {
      super(TYPE, MailgunDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected CredentialType getCredentialType() {
      return CredentialType.API_KEY;
   }

   @Property(label = "API Key", required = true, password = true)
   @PropertyEditor(dependsOn = "useCredentialId")
   public String getApiKey() {
      return ((ApiKeyCredential) getCredential()).getApiKey();
   }

   public void setApiKey(String apiKey) {
      ((ApiKeyCredential) getCredential()).setApiKey(apiKey);
   }

   @Override
   public String getUser() {
      return "api";
   }

   @Override
   public void setUser(String user) {
      // no-op
   }

   @Override
   public String getPassword() {
      return getApiKey();
   }

   @Override
   public void setPassword(String password) {
      // no-op
   }

   @Property(label = "URL", required = true)
   @PropertyEditor(tags={"https://api.mailgun.net/", "https://api.eu.mailgun.net/"},
      dependsOn = "useCredentialId")
   @Override
   public String getURL() {
      return this.URL;
   }

   @Override
   public void setURL(String URL) {
      this.URL = URL;
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(URL != null) {
         writer.format("<URL><![CDATA[%s]]></URL>%n", URL);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      URL = Tool.getChildValueByTagName(root, "URL");
      URL = URL == null ? "https://api.mailgun.net/" : URL;
   }

   @Override
   protected String getTestSuffix() {
      return "/v4/domains";
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof MailgunDataSource)) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      MailgunDataSource that = (MailgunDataSource) o;
      return (Objects.equals(URL, that.URL) && Objects.equals(this.getApiKey(), that.getApiKey()));
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), URL, getApiKey());
   }

   private String URL;
}
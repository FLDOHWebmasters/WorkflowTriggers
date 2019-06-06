/**
 * This code was taken from the Tri-C Github page and added here.
 * Modifications made to handle our own specific system. 
 */
package gov.floridahealth.cascade.email;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.function.EmailProvider;
import com.hannonhill.cascade.api.adapters.APIAdapterFactory;
import com.hannonhill.cascade.api.asset.home.File;
import com.hannonhill.cascade.email.Email;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.Group;
import com.hannonhill.cascade.model.dom.Site;
import com.hannonhill.cascade.model.dom.User;
import com.hannonhill.cascade.model.dom.Workflow;
import com.hannonhill.cascade.model.dom.WorkflowHistory;
import com.hannonhill.cascade.model.dom.WorkflowStep;
import com.hannonhill.cascade.model.dom.WorkflowStepOwnerType;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.EmailService;
import com.hannonhill.cascade.model.service.GroupService;
import com.hannonhill.cascade.model.service.UserService;
import com.hannonhill.cascade.model.sysinfo.ContextPathBean;
import com.hannonhill.cascade.model.util.CascadeHashCodeBuilder;
import com.hannonhill.cascade.model.util.URLUtil;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;
import com.hannonhill.commons.util.ClassUtil;
import com.hannonhill.commons.util.StringUtil;

import gov.floridahealth.cascade.properties.CascadeCustomProperties;

public class DOHEmailProvider extends EmailProvider {

	private static final Logger LOG = Logger.getLogger(DOHEmailProvider.class);
    private static final String MODE_PARAM = "mode";
    private static final String MODE_BRANDING = "branding";
    private static final String TEMPLATE_LOCATION = "gov.floridahealth.cascade.email.templates";
    private static final String TEMPLATE_EXTENSION = "vm";
    private static final String VELOCITY_PROPERTIES_FILE_NAME = "velocity.properties";
    private static final String TEMPLATE_VARIABLE_WORKFLOW_NAME = "_workflowName";
	private static final String TEMPLATE_VARIABLE_ENTITY_TYPE = "_entityType";
    private static final String TEMPLATE_VARIABLE_ENTITY_URL = "_entityUrl";
    private static final String TEMPLATE_VARIABLE_WORKFLOW_URL = "_workflowUrl";
    private static final String TEMPLATE_VARIABLE_DASHBOARD_URL = "_dashboardUrl";    
    private static final String TEMPLATE_VARIABLE_ACTION = "_action";
    private static final String TEMPLATE_VARIABLE_SUBJECT = "_subject";
    private static final String TEMPLATE_VARIABLE_USE_EMAIL = "_useEmailList";
    private static final String TEMPLATE_VARIABLE_USE_GROUPS = "_useGroupsList";
    private static final String TEMPLATE_VARIABLE_USE_USERS = "_useUsersList";
    private static final String TEMPLATE_VARIABLE_USE_DESTINATION = "_useDestinationOwner";
    private static final String TEMPLATE_VARIABLE_APPROVER = "_approver";
    private static final String TEMPLATE_VARIABLE_COMMENTS = "_comments";
    private static final String TEMPLATE_VARIABLE_STEP_OWNER = "_stepOwner";
    private static final String TEMPLATE_VARIABLE_STYLE = "_style";
    private static final String TEMPLATE_VARIABLE_OWNER = "_owner";
    private static final String TEMPLATE_VARIABLE_START_DATE ="_startDate";
    private static final String TEMPLATE_VARIABLE_CURRENT_STEP = "_currentStep";
    private static final String TEMPLATE_VARIABLE_HISTORY_COMMENTS = "_historyComments";
   

    //PROPERTY File Items
    private static final String SITE_PREFIX_PROP = "site.prefix";                 // added Version 1.6
    private static final String TEMPLATE_SITE_PROP = "email.template.site";
    private static final String TEMPLATE_PATH_PROP = "email.template.path";
    private static final String TEMPLATE_CSS_PATH_PROP = "email.template.css.path";
    private static final String TEMPLATE_CSS_FILE_PROP = "email.template.css.file";
    
    
/**
 * Main Process Function - Requirement of all Triggers for Cascade
 * @Override    
 */
    public boolean process() throws TriggerProviderException  {
        // Mode contains the name of the Velocity file to use for the email    	
    	String mode = parameters.get(MODE_PARAM);
    	//Branding... I have
    	String branding = parameters.get(MODE_BRANDING);
    	
    	//Properties file defined by Cascade for Velocity
    	Properties props;
        try {
            props = getProperties();
        } catch (IOException e) {
            String err = "Could not load velocity properties";
            LOG.fatal(err, e);
            throw new FatalTriggerProviderException(err, e);
        }
        VelocityEngine ve = new VelocityEngine(props);
        
        //Properties for the class
        Properties cascadeProps;
        try {
        	cascadeProps = CascadeCustomProperties.getProperties();
        } catch (IOException e) {
            String err = "Could not load velocity properties";
            LOG.fatal(err, e);
            throw new FatalTriggerProviderException(err, e);
        }         

        // Information for the template variables is pulled from the Workflow
        Workflow commonWorkflow;
        try {
            commonWorkflow = ((PublicWorkflowAdapter)this.workflow).getWorkflow();
        } catch (Throwable t) {
            String err = String.format("Could not get commonWorkflow for workflow %s", this.workflow.getName());
            LOG.fatal(err, t);
            throw new FatalTriggerProviderException(err, t);
        }
        String systemURL = serviceProvider.getPreferencesService().getSystemURL();
        
        //Get the list of comments entered by the Web Manager and the Web Team from the Workflow 
        ArrayList<ArrayList<String>> historyComments = new ArrayList<ArrayList<String>>();
        WorkflowHistory cwHistory = commonWorkflow.getHistory();
        int counter = 0; 
        while(cwHistory != null && counter < 100) {
        	if (cwHistory.getComments() != null && cwHistory.getComments() != "") {
        		ArrayList<String> historyInfo = new ArrayList<String>();
        		historyInfo.add(cwHistory.getWho());
        		historyInfo.add(cwHistory.getComments());
        		historyComments.add(historyInfo);
        	}
        	cwHistory = cwHistory.getNextHistory();
        	counter++;
        }
        
        String relatedEntityId = commonWorkflow.getRelatedEntityId();
        String relatedEntityType = commonWorkflow.getRelatedEntityType();
        String hostPrefix;
        try {
        	hostPrefix = URLUtil.getHostURL(systemURL);
        } catch (MalformedURLException e) {
        	hostPrefix = null;
        } catch (Exception e) {
        	hostPrefix = null;
        }
        if (hostPrefix == null) {
            hostPrefix = "";
        }
        if (systemURL == null) {
            systemURL = "";
        }
        String urlPrefix = String.format("%s%s/entity/open.act?id=", hostPrefix, ContextPathBean.CONTEXT_PATH);        
        String templateName = String.format("%s/%s.%s", TEMPLATE_LOCATION, mode.toLowerCase(), TEMPLATE_EXTENSION);
        String chdRepSiteId = GetSiteId(cascadeProps.getProperty(TEMPLATE_SITE_PROP));
        String prefix = cascadeProps.getProperty(SITE_PREFIX_PROP);
        String defaultFolder = cascadeProps.getProperty(TEMPLATE_PATH_PROP);
        String template = GetTemplateFromFile(mode, chdRepSiteId, prefix, defaultFolder);
        if (branding != null && branding != "") {
        	//Load the CSS file specified
        	//String emailStyle = TryGetFile(chdRepSiteId, String.format("%s%s", DEFAULT_TEMPLATE_FILE_FOLDER, DEFAULT_CSS_FILE)).getText();
        }
        //Load the default css
        String emailStyle = TryGetFile(chdRepSiteId, String.format("%s%s", 
        		cascadeProps.getProperty(TEMPLATE_CSS_PATH_PROP), 
        		cascadeProps.getProperty(TEMPLATE_CSS_FILE_PROP))).getText();
                
        if (template == null) {
            if (IsSimpleName(mode, prefix)) {
                if (!ve.resourceExists(templateName)) {
                    String err = String.format("Default template not found: %s", mode);
                    LOG.fatal(err);
                    throw new FatalTriggerProviderException(err);
                }
            } else {
                String err = String.format("Template file not found: %s", mode);
                LOG.fatal(err);
                throw new FatalTriggerProviderException(err);
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
        LOG.info("Velocity Context");
        VelocityContext vc = new VelocityContext();
        vc.put(TEMPLATE_VARIABLE_ENTITY_TYPE, relatedEntityType);
        vc.put(TEMPLATE_VARIABLE_STYLE, emailStyle);
        vc.put(TEMPLATE_VARIABLE_WORKFLOW_NAME, commonWorkflow.getName());
        vc.put(TEMPLATE_VARIABLE_DASHBOARD_URL, systemURL);
        vc.put(TEMPLATE_VARIABLE_ENTITY_URL, String.format("%s%s&type=%s", urlPrefix, relatedEntityId, relatedEntityType));
        vc.put(TEMPLATE_VARIABLE_WORKFLOW_URL, String.format("%s%s&type=workflow", urlPrefix, commonWorkflow.getId()));
        vc.put(TEMPLATE_VARIABLE_ACTION, mode);
        vc.put(TEMPLATE_VARIABLE_SUBJECT, String.format("%s: %s", commonWorkflow.getName(), mode));
        vc.put(TEMPLATE_VARIABLE_USE_EMAIL, true);
        vc.put(TEMPLATE_VARIABLE_USE_GROUPS, true);
        vc.put(TEMPLATE_VARIABLE_USE_USERS, true);
        vc.put(TEMPLATE_VARIABLE_USE_DESTINATION, true);
        vc.put(TEMPLATE_VARIABLE_APPROVER, getApprover(commonWorkflow));
        vc.put(TEMPLATE_VARIABLE_COMMENTS, this.comments);
        vc.put(TEMPLATE_VARIABLE_STEP_OWNER, getCurrentStepOwner(commonWorkflow));
        vc.put(TEMPLATE_VARIABLE_OWNER, getWorkflowOwner(commonWorkflow));
        vc.put(TEMPLATE_VARIABLE_START_DATE, sdf.format(new Date(commonWorkflow.getStartDate())));
        vc.put(TEMPLATE_VARIABLE_CURRENT_STEP, commonWorkflow.getCurrentStep().getDisplayName());
        vc.put(TEMPLATE_VARIABLE_HISTORY_COMMENTS, historyComments);
        
        String body;
        LOG.info("Write Email");
        try (StringWriter stream = new StringWriter()) { // added try with resource in version 1.7
            if (template == null) {
                if (!ve.mergeTemplate(templateName, "UTF-8", vc, stream)) {
                    String err = String.format("Velocity error (check the Velocity log) on mergeTemplate: %s", templateName);
                    LOG.fatal(err);
                    throw new FatalTriggerProviderException(err);
                }
            } else {
                if (!ve.evaluate(vc, stream, mode, template)) {
                    String err = String.format("Velocity error (check the Velocity log) on evaluate: %s", mode);
                    LOG.fatal(err);
                    throw new FatalTriggerProviderException(err);
                }
            }
            body = stream.toString();
        } catch (IOException ex) {
            String err = String.format("Stream error reading template %s: %s", template == null ? templateName : mode,
                    ex.getMessage());
            LOG.error(err);
            throw new FatalTriggerProviderException(err, ex);
        }
        String subject = vc.get(TEMPLATE_VARIABLE_SUBJECT).toString();
        Set<RecipientInfo> recipients = Recipients(vc);
        if (recipients.size() == 0) {
            LOG.error(String.format("No recipients to send email for %s for %s id: %s",
                    commonWorkflow.getName(), relatedEntityType, relatedEntityId));
        } else {
            EmailService emailService = serviceProvider.getEmailService();
            for (RecipientInfo recipient : recipients) {
                if (recipient.getEmailAddress() != null && 
                	recipient.getEmailAddress().length() > 0 && 
                	recipient.getEmailAddress() != "") {
	                try {
	                    Email email = new Email(recipient.getEmailAddress(), subject, body, null, true);
	                    emailService.sendEmail(email);
	                } catch (Exception e) {
	                    String err = String.format("Workflow %s could not send email to %s for %s id: %s",
	                            commonWorkflow.getName(), recipient.getEmailAddress(),
	                            relatedEntityType, relatedEntityId);
	                    LOG.fatal(err);
	                    throw new FatalTriggerProviderException(err);
	                }
                }
            }
        }
        return true;
    }

    private String getCurrentStepOwner(Workflow commonWorkflow) {
        return getStepOwner(commonWorkflow.getCurrentStep());
    }

    private String getApprover(Workflow commonWorkflow) {
        return getStepOwner(commonWorkflow.getCurrentStep().getPreviousStep());
    }

    private String getStepOwner(WorkflowStep step) {
        if (step == null) {
            LOG.debug("getStepOwner: step is null");
            return "";
        }
        UserService userService = this.serviceProvider.getUserService();
        String owner = "";
        try {
            owner = step.getOwner();
            if (owner == null) return "";
            if (step.getOwnerType() == WorkflowStepOwnerType.USER) {
                User user = userService.get(owner);
                return user.getFullName();
            } else {
                return owner;
            }
        } catch (Exception e) {
            LOG.warn(String.format("Error finding user %s", owner ), e);
            return "";
        }
    }

    private String getWorkflowOwner(Workflow commonWorkflow){
        UserService userService = this.serviceProvider.getUserService();
        String ownerId = commonWorkflow.getOwner();
        if (ownerId == null) {
            LOG.error(String.format("getOwner is null for workflow %s", this.workflow.getName()));
            return "";
        }
        try {
            User workflowOwner = userService.get(ownerId);
            if (workflowOwner == null) {
                LOG.error(String.format("Could not find user %s", ownerId));
                return "";
            }
            return workflowOwner.getFullName();
        } catch (Throwable t) {
            LOG.warn(String.format("Could not find workflow owner %s", ownerId), t);
            return "";
        }
    }


    // added in version 1.6

    private String GetTemplateFromFile(String path, String defaultSiteId, String prefix, String defaultFolder) {
        // path can take one of three forms
        // 1) site://sitename/folder/folder/file NOTE: folders are optional
        // 2) /folder/file NOTE: the site of the document associated with the workflow will be used
        // 3) filename NOTE: the site of the document associated with the workflow will be used and
        //      the path will be the value of DEFAULT_TEMPLATE_FILE_FOLDER
        // returns null if anything goes wrong
        // if the file is not found and it has no extension then the default extension will be added
        // and retried
        File templateFile;
        if (PathHasSite(path,prefix)) {
            String siteId;
            SiteNamePath siteNamePath = ParsePath(path,prefix);
            if (siteNamePath == null) {
                return null;
            }
            siteId = GetSiteId(siteNamePath.getSiteName());
            if (siteId == null) {
                return null;
            }
            templateFile = TryGetFile(siteId, siteNamePath.getPath());
        } else if (path.indexOf('/') > -1) {
            // path has has folders or start with a /
            if (defaultSiteId == null || defaultSiteId.length() == 0) {
                LOG.warn("Default SiteId is null or blank");
                return null;
            }
            String absolutePath = path;
            if (path.startsWith("/")) {
                absolutePath = absolutePath.substring(1);
            }
            templateFile = TryGetFile(defaultSiteId, absolutePath);
        } else {
            if (defaultSiteId == null || defaultSiteId.length() == 0) {
                LOG.warn("Default SiteId is null or blank");
                return null;
            }
            templateFile = TryGetFile(defaultSiteId, String.format("%s%s", defaultFolder, path));
        }
        if (templateFile == null) {
            LOG.warn(String.format("File not found %s", path));
            return null;
        }
        return templateFile.getText();
    }

    // extract the site name and path from site://sitename/folder/folder/file NOTE: folders are optional
    private SiteNamePath ParsePath(String input, String prefix) {
        if (!PathHasSite(input,prefix)) {
            LOG.warn(String.format("Path not valid: %s", input));
            return null;
        }
        String sitePath = input.substring(prefix.length());
        int pos = sitePath.indexOf('/');
        if (pos < 1) {
            LOG.warn(String.format("Site Name missing from: %s", sitePath));
            return null;
        }
        String siteName = sitePath.substring(0, pos);
        int pathStart = pos+1;
        if (pathStart == siteName.length()) {
            LOG.warn(String.format("path missing in %s", sitePath));
            return null;
        }
        return new SiteNamePath(sitePath.substring(0, pos), sitePath.substring(pathStart));
    }

    private class SiteNamePath {
        private String SiteName;
        private String Path;

        SiteNamePath(String siteName, String path) {
            SiteName = siteName;
            Path = path;
        }

        public int hashCode() {
            return new CascadeHashCodeBuilder().append(this.SiteName).append(this.Path).toHashCode();
        }

        String getSiteName() {
            return SiteName;
        }

        String getPath() {
            return Path;
        }
    }

    private String GetSiteId(String siteName) {
        if (siteName == null || siteName.length() == 0) {
            LOG.error("siteName is null or empty");
            return null;
        }
        Site site = this.serviceProvider.getSiteService().getByName(siteName);
        if (site == null) {
            LOG.error(String.format("Site not found %s", siteName));
            return null;
        }
        return site.getId();
    }

    private File TryGetFile(String siteId, String path) {
        File templateFile = GetFile(siteId, path);
        if (templateFile == null) {
            if (path.indexOf('.') == -1) {
                templateFile = GetFile(siteId, String.format("%s.%s", path, TEMPLATE_EXTENSION));
            }
        }
        return templateFile;
    }
    
    private File GetFile(String siteId, String path) {
        FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(
                path, EntityTypes.TYPE_FILE, siteId);
        if (fce == null) {
            return null;
        }
        return (File) APIAdapterFactory.createAPIAdapter(fce, true, false, this.serviceProvider);
    }

    /*
     * Commenting out to remove the warning for the short term
    private String GetSiteIdFromAsset(String assetId) {
        FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(assetId);
        if (fce == null) {
            LOG.error(String.format("Asset could not be found, ID: %s", assetId));
            return null;
        }
        return SiteUtil.getSiteId(fce);
    }
	*/
    
    private Boolean PathHasSite(String path, String prefix) {
        return path.toUpperCase().startsWith(prefix);
    }

    private Boolean IsSimpleName(String path, String prefix) {
        return !PathHasSite(path,prefix) && path.indexOf('/') == -1;
    }
    // end added in Version 1.6


    public static Properties getProperties()
            throws IOException
    {
        Properties props = new Properties();
        try (InputStream stream = ClassUtil.relativeInputStream(DOHEmailProvider.class, VELOCITY_PROPERTIES_FILE_NAME)){
            props.load(stream);
        }
        return props;
    }

    private Set<RecipientInfo> Recipients(VelocityContext context) throws TriggerProviderException {
        Set<RecipientInfo> recipients = new HashSet<>();
        GroupService groupService = this.serviceProvider.getGroupService();
        UserService userService = this.serviceProvider.getUserService();
        if (makeBool(context.get(TEMPLATE_VARIABLE_USE_EMAIL))){
            String recipientEmails = getParameter("recipients-email");
            if (StringUtil.isNotEmpty(recipientEmails)) {
                String[] list = recipientEmails.split(",");
                for (String email : list) {
                    recipients.add(new RecipientInfo(null, email.trim()));
                }
            }
        }
        if (makeBool(context.get(TEMPLATE_VARIABLE_USE_GROUPS))) {
            String recipientGroups = getParameter("recipient-groups");
            if (StringUtil.isNotEmptyTrimmed(recipientGroups)) {
                String[] groupList = recipientGroups.split(",");
                for (String groupName : groupList) {
                    groupName = groupName.trim();
                    Group group = groupService.get(groupName);
                    if (group == null) {
                        LOG.error(String.format("While sending workflow email, unable to find group with name : %s", groupName));
                    } else {
                        List<User> users = userService.getUsersOfGroup(groupName);
                        for (User user : users) {
                            String userEmail = user.getEmail();
                            if (StringUtil.isNotEmptyTrimmed(userEmail)) {
                                recipients.add(new RecipientInfo(user.getName(), userEmail.trim()));
                            }
                        }
                    }
                }
            }
        }
        if (makeBool(context.get(TEMPLATE_VARIABLE_USE_USERS))) {
            String recipientUsers = getParameter("recipient-users");
            if (StringUtil.isNotEmptyTrimmed(recipientUsers)) {
                String[] userList = recipientUsers.split(",");
                for (String username : userList) {
                    username = username.trim();
                    try {
                        User user = userService.get(username);
                        String userEmail = user.getEmail();
                        if (StringUtil.isNotEmptyTrimmed(userEmail)) {
                            recipients.add(new RecipientInfo(user.getName(), userEmail.trim()));
                        }
                    } catch (Exception e) {
                        LOG.error(String.format("While sending workflow email, unable to find user with name: %s", username));
                    }
                }
            }
        }
        if (makeBool(context.get(TEMPLATE_VARIABLE_USE_DESTINATION))) {
            if (this.workflow != null && this.destStep != null) {
                String destStepType = this.destStep.getStepType();
                if (("transition".equals(destStepType)) || ("edit".equals(destStepType))) {
                    String owner = this.destStep.getOwner();
                    if (this.destStep.getOwnerType() == 1) {
                        Group grp = groupService.get(owner);
                        if (grp == null) {
                            String err = String.format("Workflow step owner group '%s' could not be found.", owner);
                            LOG.fatal(err);
                            throw new TriggerProviderException(err);
                        }
                        List<User> users = userService.getUsersOfGroup(owner);
                        if (users != null) {
                            for (User user : users) {
                                String email = user.getEmail();
                                recipients.add(new RecipientInfo(user.getName(), email));
                            }
                        }
                    } else {
                        User user = userService.get(owner);
                        if (user == null) {
                            String err = String.format("Workflow step owner user '%s' could not be found.", owner);
                            LOG.fatal(err);
                            throw new TriggerProviderException(err);
                        }
                        recipients.add(new RecipientInfo(user.getName(), user.getEmail()));
                    }
                }
                else if (("auto".equals(destStepType)) || ("system".equals(destStepType)))
                {
                    User workflowOwner = userService.get(this.workflow.getOwner());
                    if (workflowOwner == null) {
                        String err = String.format("The workflow owner '%s' could not be found.", this.workflow.getOwner());
                        LOG.fatal(err);
                        throw new TriggerProviderException(err);
                    }
                    recipients.add(new RecipientInfo(workflowOwner.getName(), workflowOwner.getEmail()));
                }
            }
        }
        return recipients;
    }

    public class RecipientInfo
    {
        private String username;
        private String emailAddress;

        public RecipientInfo(String username, String emailAddress)
        {
            setUsername(username);
            setEmailAddress(emailAddress);
        }

        public int hashCode()
        {
            return new CascadeHashCodeBuilder().append(this.username).append(this.emailAddress).toHashCode();
        }

        public void setUsername(String username)
        {
            this.username = username;
        }

        public String getUsername()
        {
            return this.username;
        }

        public void setEmailAddress(String emailAddress)
        {
            this.emailAddress = emailAddress;
        }

        public String getEmailAddress()
        {
            return this.emailAddress;
        }
    }

    private boolean makeBool(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return Boolean.parseBoolean(o.toString());
    }
}

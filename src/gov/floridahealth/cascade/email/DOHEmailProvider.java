/**
 * Constructs the outgoing email utilizing Velocity formats to convert the original variables into HTML and then 
 * uses the Email Service to send to the intended recipients 
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
	/* Incoming Parameters */
	private static final String MODE_PARAM = "mode";
	private static final String MODE_BRANDING = "branding";

	/*
	 * Original Location of the Velocity Formats; Removed from JAR due to desire to
	 * update Email Templates without having to deploy the JAR file again.
	 */
	private static final String TEMPLATE_LOCATION = "gov.floridahealth.cascade.email.templates";
	private static final String TEMPLATE_EXTENSION = "vm";

	/* Cascade Velocity Properties File - Not Editable */
	private static final String VELOCITY_PROPERTIES_FILE_NAME = "velocity.properties";

	/* Values within the Workflow that need to be part of the various emails sent */
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
	private static final String TEMPLATE_VARIABLE_START_DATE = "_startDate";
	private static final String TEMPLATE_VARIABLE_CURRENT_STEP = "_currentStep";
	private static final String TEMPLATE_VARIABLE_HISTORY_COMMENTS = "_historyComments";

	/* Property File Items */
	private static final String SITE_PREFIX_PROP = "site.prefix";
	private static final String TEMPLATE_SITE_PROP = "email.template.site";
	private static final String TEMPLATE_PATH_PROP = "email.template.path";
	private static final String TEMPLATE_CSS_PATH_PROP = "email.template.css.path";
	private static final String TEMPLATE_CSS_FILE_PROP = "email.template.css.file";

	/**
	 * Main Process Function - Requirement of all Triggers for Cascade
	 */
	@Override
	public boolean process() throws TriggerProviderException {
		/* Mode contains the name of the Velocity file to use for the email */
		String mode = parameters.get(MODE_PARAM);
		/*
		 * Branding - Originally, there was a requirement that the workflow could
		 * utilize a different CSS file depending upon the template. This was nixed, but
		 * parameter and commented out code remains.
		 */
		String branding = parameters.get(MODE_BRANDING);

		// Properties file defined by Cascade for Velocity
		Properties props;
		try {
			props = getProperties();
		} catch (IOException e) {
			String err = "Could not load velocity properties";
			LOG.fatal(err, e);
			throw new FatalTriggerProviderException(err, e);
		}
		VelocityEngine ve = new VelocityEngine(props);

		// Information for the template variables is pulled from the Workflow
		Workflow commonWorkflow;
		try {
			commonWorkflow = ((PublicWorkflowAdapter) this.workflow).getWorkflow();
		} catch (Throwable t) {
			String err = String.format("Could not get commonWorkflow for workflow %s", this.workflow.getName());
			LOG.fatal(err, t);
			throw new FatalTriggerProviderException(err, t);
		}
		String systemURL = serviceProvider.getPreferencesService().getSystemURL();

		// Get the list of comments entered by the Web Manager and the Web Team from the
		// Workflow
		ArrayList<ArrayList<String>> historyComments = new ArrayList<ArrayList<String>>();
		WorkflowHistory cwHistory = commonWorkflow.getHistory();
		int counter = 0;
		while (cwHistory != null && counter < 100) {
			if (cwHistory.getComments() != null && cwHistory.getComments() != "") {
				ArrayList<String> historyInfo = new ArrayList<String>();
				historyInfo.add(cwHistory.getWho());
				historyInfo.add(cwHistory.getComments());
				historyComments.add(historyInfo);
			}
			cwHistory = cwHistory.getNextHistory();
			counter++;
		}

		// The ID and Type of the Asset that the workflow is being used to CRUD/Move
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
		String chdRepSiteId = GetSiteId(CascadeCustomProperties.getProperty(TEMPLATE_SITE_PROP));
		String prefix = CascadeCustomProperties.getProperty(SITE_PREFIX_PROP);
		String defaultFolder = CascadeCustomProperties.getProperty(TEMPLATE_PATH_PROP);
		String template = GetTemplateFromFile(mode, chdRepSiteId, prefix, defaultFolder);
		if (branding != null && branding != "") {
			// Load the CSS file specified
			// String emailStyle = TryGetFile(chdRepSiteId, String.format("%s%s",
			// DEFAULT_TEMPLATE_FILE_FOLDER, DEFAULT_CSS_FILE)).getText();
		}
		// Load the default css
		String emailStyle = TryGetFile(chdRepSiteId,
				String.format("%s%s", CascadeCustomProperties.getProperty(TEMPLATE_CSS_PATH_PROP),
						CascadeCustomProperties.getProperty(TEMPLATE_CSS_FILE_PROP))).getText();

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
		vc.put(TEMPLATE_VARIABLE_ENTITY_URL,
				String.format("%s%s&type=%s", urlPrefix, relatedEntityId, relatedEntityType));
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
		try (StringWriter stream = new StringWriter()) { // added try with resource in version 1.7
			if (template == null) {
				if (!ve.mergeTemplate(templateName, "UTF-8", vc, stream)) {
					String err = String.format("Velocity error (check the Velocity log) on mergeTemplate: %s",
							templateName);
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
			LOG.error(String.format("No recipients to send email for %s for %s id: %s", commonWorkflow.getName(),
					relatedEntityType, relatedEntityId));
		} else {
			EmailService emailService = serviceProvider.getEmailService();
			for (RecipientInfo recipient : recipients) {
				if (recipient.getEmailAddress() != null && recipient.getEmailAddress().length() > 0
						&& recipient.getEmailAddress() != "") {
					try {
						Email email = new Email(recipient.getEmailAddress(), subject, body, null, true);
						emailService.sendEmail(email);
					} catch (Exception e) {
						String err = String.format("Workflow %s could not send email to %s for %s id: %s",
								commonWorkflow.getName(), recipient.getEmailAddress(), relatedEntityType,
								relatedEntityId);
						LOG.fatal(err);
						throw new FatalTriggerProviderException(err);
					}
				}
			}
		}
		return true;
	}

	/**
	 * Retrieves the entity that is currently in possession of the workflow
	 * @param commonWorkflow Workflow Object controlling the asset
	 * @return User ID or Group ID
	 */
	private String getCurrentStepOwner(Workflow commonWorkflow) {
		return getStepOwner(commonWorkflow.getCurrentStep());
	}

	/**
	 * Retrieves the entity that will receive the workflow via the next step
	 * @param commonWorkflow Workflow Object controlling the asset
	 * @return User ID or Group ID
	 */
	private String getApprover(Workflow commonWorkflow) {
		return getStepOwner(commonWorkflow.getCurrentStep().getPreviousStep());
	}

	/**
	 * Gets the owner of a past step (used in conjunction with Workflow History)
	 * @param step Step of the Workflow in question
	 * @return User ID or Group ID
	 */
	private String getStepOwner(WorkflowStep step) {
		if (step == null) {
			LOG.debug("getStepOwner: step is null");
			return "";
		}
		UserService userService = this.serviceProvider.getUserService();
		String owner = "";
		try {
			owner = step.getOwner();
			if (owner == null)
				return "";
			if (step.getOwnerType() == WorkflowStepOwnerType.USER) {
				User user = userService.get(owner);
				return user.getFullName();
			} else {
				return owner;
			}
		} catch (Exception e) {
			LOG.warn(String.format("Error finding user %s", owner), e);
			return "";
		}
	}

	/**
	 * Returns the User who initiated the workflow
	 * @param commonWorkflow Workflow Object controlling the asset
	 * @return User ID
	 */
	private String getWorkflowOwner(Workflow commonWorkflow) {
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

	/**
	 * Obtains the Template in one of 3 ways: 1) site://sitename/folder/folder/file
	 * NOTE: folders are optional 2) /folder/file NOTE: the site of the document
	 * associated with the workflow will be used 3) filename NOTE: the site of the
	 * document associated with the workflow will be used and the path will be the
	 * value of DEFAULT_TEMPLATE_FILE_FOLDER Nore: If the file is not found and it
	 * has no extension then the default extension will be added and retried.
	 * 
	 * @param path          - Path to the file
	 * @param defaultSiteId - Site ID of the Workflow
	 * @param prefix        - Beginning of the path in the case that the value is
	 *                      referring across sites
	 * @param defaultFolder - Default Folder to check if Path does not contain any
	 *                      folder structure
	 * @return Text of the Template File or null upon failure
	 */
	private String GetTemplateFromFile(String path, String defaultSiteId, String prefix, String defaultFolder) {
		//
		File templateFile;
		if (PathHasSite(path, prefix)) {
			String siteId;
			SiteNamePath siteNamePath = ParsePath(path, prefix);
			if (siteNamePath == null) {
				return null;
			}
			siteId = GetSiteId(siteNamePath.siteName);
			if (siteId == null) {
				return null;
			}
			templateFile = TryGetFile(siteId, siteNamePath.path);
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

	/**
	 * Extracts the site name and path from site://sitename/folder/folder/file NOTE:
	 * folders are optional
	 * @param input  - Path to be parsed
	 * @param prefix - Site Prefix
	 * @return Object holding the Site name and path
	 */
	private SiteNamePath ParsePath(String input, String prefix) {
		if (!PathHasSite(input, prefix)) {
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
		int pathStart = pos + 1;
		if (pathStart == siteName.length()) {
			LOG.warn(String.format("path missing in %s", sitePath));
			return null;
		}
		return new SiteNamePath(sitePath.substring(0, pos), sitePath.substring(pathStart));
	}

	/**
	 * Basic object for Site Name and Path
	 */
	static class SiteNamePath {
		final String siteName;
		final String path;

		SiteNamePath(String siteName, String path) {
			this.siteName = siteName;
			this.path = path;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (obj == null || obj.getClass() != this.getClass()) {
				return false;
			}
			SiteNamePath that = (SiteNamePath) obj;
			return this.siteName == that.siteName && this.path == that.path;
		}

		@Override
		public int hashCode() {
			return new CascadeHashCodeBuilder().append(this.siteName).append(this.path).toHashCode();
		}
	}

	/**
	 * Retrieves the ID of the site based upon the name
	 * @param siteName Name of the Site used to get the ID
	 * @return Site ID
	 */
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

	/**
	 * Retrieves the file based upon the parameters
	 * @param siteId Site ID
	 * @param path   Path
	 * @return File Object if exists, null if not
	 */
	private File GetFile(String siteId, String path) {
		FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(path,
				EntityTypes.TYPE_FILE, siteId);
		if (fce == null) {
			return null;
		}
		return (File) APIAdapterFactory.createAPIAdapter(fce, true, false, this.serviceProvider);
	}

	/**
	 * Checks to see if the string has any reference to the site by way of the path
	 * @param path   - Path of the object
	 * @param prefix - Site Prefix to check
	 * @return true if Site Prefix exists, false if not
	 */
	private Boolean PathHasSite(String path, String prefix) {
		return path.toUpperCase().startsWith(prefix);
	}

	/**
	 * Determines if this only the file or has additional information
	 * 
	 * @param path   - Path of the
	 * @param prefix - prefix to compare if the path starts with the site style
	 * @return True if just a file, False if any path information whatsoever.
	 */
	private Boolean IsSimpleName(String path, String prefix) {
		return !PathHasSite(path, prefix) && path.indexOf('/') == -1;
	}

	/**
	 * Returns the properties object
	 * @return Properties of Velocity as dictated by Cascade/Hannon Hill
	 * @throws IOException Error if the file can not be found.
	 */
	public static Properties getProperties() throws IOException {
		Properties props = new Properties();
		try (InputStream stream = ClassUtil.relativeInputStream(DOHEmailProvider.class,
				VELOCITY_PROPERTIES_FILE_NAME)) {
			props.load(stream);
		}
		return props;
	}

	/**
	 * Determines who should receive the email in question, based upon the most
	 * recent step completed.
	 * @param context Information from the workflow necessary for delivering the
	 *                email
	 * @return Collection of Recipients to get the email
	 * @throws TriggerProviderException Error that shuts down the trigger within the
	 *                                  workflow
	 */
	private Set<RecipientInfo> Recipients(VelocityContext context) throws TriggerProviderException {
		Set<RecipientInfo> recipients = new HashSet<>();
		GroupService groupService = this.serviceProvider.getGroupService();
		UserService userService = this.serviceProvider.getUserService();
		if (makeBool(context.get(TEMPLATE_VARIABLE_USE_EMAIL))) {
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
						LOG.error(String.format("While sending workflow email, unable to find group with name : %s",
								groupName));
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
						LOG.error(String.format("While sending workflow email, unable to find user with name: %s",
								username));
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
				} else if (("auto".equals(destStepType)) || ("system".equals(destStepType))) {
					User workflowOwner = userService.get(this.workflow.getOwner());
					if (workflowOwner == null) {
						String err = String.format("The workflow owner '%s' could not be found.",
								this.workflow.getOwner());
						LOG.fatal(err);
						throw new TriggerProviderException(err);
					}
					recipients.add(new RecipientInfo(workflowOwner.getName(), workflowOwner.getEmail()));
				}
			}
		}
		return recipients;
	}

	/**
	 * Basic class for storing information of the recipient(s)
	 */
	public class RecipientInfo {
		private String username;
		private String emailAddress;

		public RecipientInfo(String username, String emailAddress) {
			setUsername(username);
			setEmailAddress(emailAddress);
		}

		public int hashCode() {
			return new CascadeHashCodeBuilder().append(this.username).append(this.emailAddress).toHashCode();
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getUsername() {
			return this.username;
		}

		public void setEmailAddress(String emailAddress) {
			this.emailAddress = emailAddress;
		}

		public String getEmailAddress() {
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

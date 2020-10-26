/**
 * Publishes a Folder of a site without need of a Publish Set
 * This was created in order to remove the need to create 67 Publish Sets and 268 Workflow Definitions in order to publish
 * the JSON files that handle the Left Hand Navigation 
 */
package gov.floridahealth.cascade.publisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.WorkflowTriggerProcessingResult;
import com.hannonhill.cascade.model.dom.*;
import com.hannonhill.cascade.model.dom.identifier.EntityType;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.LocatorService;
import com.hannonhill.cascade.model.util.SiteUtil;

import gov.floridahealth.util.CascadeCustomProperties;

public class FolderPublisher extends BaseFolderPublisher {
	private static final String DEV_ENV_PROP_PREFIX = "dev";
	private static final String TEST_ENV_PROP_PREFIX = "test";
	private static final String PROD_ENV_PROP_PREFIX = "prod";
	private static final String NODE_HOST_PROP_SUFFIX = ".nodejs.host";
	private static final String CASCADE_HOST_PROP_SUFFIX = ".cascade.host";
	private static final String JSON_LOCATION_PROP = "json.defaultFolder";
	private static final Logger LOG = Logger.getLogger(FolderPublisher.class);

	@Override
	protected Logger getLog() {
		return LOG;
	}

	/**
	 * Main function of the Workflow Trigger <ol>
	 * <li>Checks to see if a folder is specified. If so, it uses it. If not,
	 * it defaults to the JSON folder unless the asset being published is a county
	 * location page, in which case the locations index page is published as well.</li>
	 * <li>Checks to see if a site is specified for the folder. If it does not
	 * exist, defaults to the Site for the Asset being modified by the Workflow.</li>
	 * <li>Constructs a Publish Request based upon the options and initiates it.</li>
	 * </ol>
	 * @return boolean whether the parent resource was published successfully
	 */
	@Override
	public WorkflowTriggerProcessingResult process() throws TriggerProviderException {
		LOG.info("Starting custom workflow trigger");
		final String folderName = getParameter("folder");
		final String siteName = getParameter("site");
		final String relatedEntityId = getRelatedEntityId();
		final boolean folderSpecified = folderName != null && !folderName.isEmpty();
		final boolean siteSpecified = siteName != null && !siteName.isEmpty();
		final LocatorService service = this.serviceProvider.getLocatorService();
		final FolderContainedEntity asset;
		if (folderSpecified && siteSpecified) {
			asset = null;
		} else {
			asset = service.locateFolderContainedEntity(relatedEntityId);
			if (asset == null) {
				return fail("Asset could not be found, ID: " + relatedEntityId);
			}
		}
		final String siteId;
		if (siteSpecified) {
			Site site = this.serviceProvider.getSiteService().getByName(siteName);
			if (site == null) {
				return fail("Site not found: " + siteName);
			}
			siteId = site.getId();
		} else {
			siteId = SiteUtil.getSiteId(asset);
		}
		if (siteId == null) {
			return fail("siteId was null");
		}
		final boolean isLocation = !folderSpecified && isLocation(asset);
		final String jsonLocation = CascadeCustomProperties.getProperty(JSON_LOCATION_PROP);
		final EntityType entityType = isLocation ? EntityTypes.TYPE_PAGE : EntityTypes.TYPE_FOLDER;
		final String path = isLocation ? "locations/index" : folderSpecified ? folderName : jsonLocation;
		if (path == null) {
			return fail(JSON_LOCATION_PROP + " property has no value");
		}
		final FolderContainedEntity entity;
		try {
			entity = service.locateFolderContainedEntity(path, entityType, siteId);
		} catch (Exception e) {
			return fail("Could not get the designated entity: " + path + " in site ID " + siteId);
		}
		if (EntityTypes.TYPE_FOLDER.equals(entityType)) {
			queuePublishRequest((Folder)entity);
		} else {
			queuePublishRequest((Page)entity);
		}
		if (isLocation) {
			try {
				generateLocationJson();
			} catch (Exception e) {
				LOG.error(e);
				return fail("Could not generate location JSON. " + e.getMessage());
			}
		}
		return WorkflowTriggerProcessingResult.CONTINUE;
	}

	public void generateLocationJson() throws TriggerProviderException {
		String serverName;
		Map<String, String> system = System.getenv();
	    if (system.containsKey("COMPUTERNAME")) {
	    	serverName = system.get("COMPUTERNAME"); // "HOSTNAME" on Unix/Linux
	    } else try {
	    	serverName = InetAddress.getLocalHost().getHostName();
	    } catch (UnknownHostException e) {
	    	throw new TriggerProviderException(e.getMessage(), e);
	    }
		String cascadeTest = CascadeCustomProperties.getProperty(TEST_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		String cascadeProd = CascadeCustomProperties.getProperty(PROD_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		String environment = DEV_ENV_PROP_PREFIX;
		if (serverName.startsWith(cascadeTest)) {
			environment = TEST_ENV_PROP_PREFIX;
		} else if (serverName.startsWith(cascadeProd)) {
			environment = PROD_ENV_PROP_PREFIX;
		}
		final String nodeHostProp = environment + NODE_HOST_PROP_SUFFIX;
		final String nodeHost = CascadeCustomProperties.getProperty(nodeHostProp);

		LOG.info("Starting location JSON generation/publish");
		final String uri = "https://" + nodeHost + ":8443/locations/load";
		String outputString = "";
		try {
			final URL url = new URL(uri);
			HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
			InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
			BufferedReader in = new BufferedReader(isr);
			String responseString;
			while ((responseString = in.readLine()) != null) {
				outputString = outputString + responseString;
			}
		} catch (IOException e) {
			throw new TriggerProviderException(uri + ": " + e.getMessage(), e);
		}
		System.out.println(outputString);
	}
}

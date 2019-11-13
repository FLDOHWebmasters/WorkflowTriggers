/**
 * Publishes a Folder of a site without need of a Publish Set
 * This was created in order to remove the need to create 67 Publish Sets and 268 Workflow Definitions in order to publish
 * the JSON files that handle the Left Hand Navigation 
 */
package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.Site;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.LocatorService;
import com.hannonhill.cascade.model.util.SiteUtil;

public class FolderPublisher extends BaseFolderPublisher {
	private static final Logger LOG = Logger.getLogger(FolderPublisher.class);

	protected Logger getLog() {
		return LOG;
	}

	/**
	 * Main function of the Workflow Trigger 1.) Checks to see if a folder is
	 * specified. If so, it uses it. If not, it defaults to the JSON folder. 2.)
	 * Checks to see if a site is specified for the folder. If it does not exist,
	 * defaults to the Site for the Asset being modified by the Workflow. 3.)
	 * Constructs a Publish Request based upon the options and initiates it.
	 * 
	 * @return boolean Successful Completion of Folder publish
	 */
	public boolean process() throws TriggerProviderException {
		final Config config = new Config();
		final String folder = getParameter("folder");
		final String siteName = getParameter("site");
		final String relatedEntityId = getRelatedEntityId();
		final LocatorService service = this.serviceProvider.getLocatorService();
		final String siteId;
		if (siteName != null && siteName != "") {
			Site site = this.serviceProvider.getSiteService().getByName(siteName);
			if (site == null) {
				return fail("Site not found: " + siteName);
			}
			siteId = site.getId();
		} else {
			FolderContainedEntity fce = service.locateFolderContainedEntity(relatedEntityId);
			if (fce == null) {
				return fail("Asset could not be found, ID: " + relatedEntityId);
			}
			siteId = SiteUtil.getSiteId(fce);
		}
		if (siteId == null) {
			return fail("siteId was null");
		}
		final String path = folder != null && folder != "" ? folder : config.jsonLocation;
		if (path == null) {
			return fail(Config.JSON_LOCATION_PROP + " property has no value");
		}
		final FolderContainedEntity fce;
		try {
			fce = service.locateFolderContainedEntity(path, EntityTypes.TYPE_FOLDER, siteId);
		} catch (Exception e) {
			return fail("Could not get the designated folder: " + path + " in site ID " + siteId);
		}
		queuePublishRequest(fce);
		return true;
	}
}

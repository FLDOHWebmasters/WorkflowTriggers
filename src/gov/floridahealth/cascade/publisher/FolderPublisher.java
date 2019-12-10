/**
 * Publishes a Folder of a site without need of a Publish Set
 * This was created in order to remove the need to create 67 Publish Sets and 268 Workflow Definitions in order to publish
 * the JSON files that handle the Left Hand Navigation 
 */
package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;
import com.hannonhill.cascade.model.dom.*;
import com.hannonhill.cascade.model.dom.identifier.EntityType;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.LocatorService;
import com.hannonhill.cascade.model.util.SiteUtil;

import gov.floridahealth.util.CascadeCustomProperties;

public class FolderPublisher extends BaseFolderPublisher {
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
	public boolean process() throws TriggerProviderException {
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
		final String indexPage = folderSpecified ? null : getIndexPath(asset);
		final String jsonLocation = CascadeCustomProperties.getProperty(JSON_LOCATION_PROP);
		final String path = indexPage != null ? indexPage : folderSpecified ? folderName : jsonLocation;
		final EntityType entityType = indexPage != null ? EntityTypes.TYPE_PAGE : EntityTypes.TYPE_FOLDER;
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
		return true;
	}

	// if the asset is a page in a top-level locations folder and is not the index page, return the index page
	private String getIndexPath(FolderContainedEntity asset) {
		final String path = asset.getPath();
		final int slashIndex = path.indexOf("/");
		final boolean isTopLevel = slashIndex > 0 && path.indexOf("/", slashIndex + 1) < 0;
		final boolean isLocation = isTopLevel && "locations".equals(path.substring(0, slashIndex));
		final boolean isPage = EntityTypes.TYPE_PAGE.equals(asset.getType());
		final boolean isLocPage = isLocation && isPage && !"index".equals(path.substring(slashIndex + 1));
		if (isLocPage) {
			return "locations/index";
		}
		return null;
	}
}

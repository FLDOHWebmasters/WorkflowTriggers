package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.WorkflowTriggerProcessingResult;
import com.hannonhill.cascade.model.dom.Folder;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.LocatorService;
import com.hannonhill.cascade.model.util.SiteUtil;

import gov.floridahealth.util.CascadeCustomProperties;

/**
 * Publishes an ancestor folder of the asset involved in a workflow. Values to
 * use: PARENT - 1 Folder Up GRANDPARENT - 2 Folders Up GREATGRANDPARENT - 3
 * Folders Up (not implemented yet)
 * 
 * @author VerschageJX
 */
public class ParentFolderPublisher extends BaseFolderPublisher {
	private static final String PARENT_PARAM_PROP = "parent.param.name";
	private static final String DEFAULT_VALUE_PROP = "parent.parent.default.value";
	private static final Logger LOG = Logger.getLogger(ParentFolderPublisher.class);

	@Override
	protected Logger getLog() {
		return LOG;
	}

	@Override
	public WorkflowTriggerProcessingResult process() throws TriggerProviderException {
		LOG.info("Starting custom workflow trigger");
		final String relatedEntityId = getRelatedEntityId();
		LocatorService service = this.serviceProvider.getLocatorService();
		FolderContainedEntity fce = service.locateFolderContainedEntity(relatedEntityId);
		if (fce == null) {
			return fail("Asset could not be found by ID: " + relatedEntityId);
		}
		String itemPath = fce.getPath();
		String siteId = SiteUtil.getSiteId(fce);
		if (siteId == null) {
			return fail("Site ID not found for asset: " + itemPath);
		}
		String parentType = getParameter(CascadeCustomProperties.getProperty(PARENT_PARAM_PROP));
		if (parentType == null || parentType.isEmpty()) {
			parentType = CascadeCustomProperties.getProperty(DEFAULT_VALUE_PROP);
		}
		String parentFolderLocation = itemPath.substring(0, itemPath.lastIndexOf('/'));
		if (parentType.equals("GRANDPARENT")) {
			if (parentFolderLocation.lastIndexOf('/') < 0) {
				return fail("Cannot publish Grandparent of file in single folder: " + itemPath);
			}
			parentFolderLocation = parentFolderLocation.substring(0, parentFolderLocation.lastIndexOf('/'));
		} else if (!parentType.equals("PARENT")) {
			return fail("Parent type invalid: " + parentType);
		}
		if (parentFolderLocation.isEmpty()) {
			return fail("Missing " + parentType + " folder for asset: " + itemPath);
		}
		try {
			fce = service.locateFolderContainedEntity(parentFolderLocation, EntityTypes.TYPE_FOLDER, siteId);
		} catch (Exception e) {
			return fail("Could not get the designated folder: " + parentFolderLocation + " in site ID " + siteId);
		}
		queuePublishRequest((Folder)fce);
		return WorkflowTriggerProcessingResult.CONTINUE;
	}
}

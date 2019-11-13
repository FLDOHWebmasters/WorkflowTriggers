package gov.floridahealth.cascade.publisher;

import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.function.Publisher;
import com.hannonhill.cascade.model.dom.Folder;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.PublishRequest;
import com.hannonhill.cascade.model.dom.Workflow;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.LocatorService;
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.util.SiteUtil;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

import gov.floridahealth.cascade.properties.CascadeCustomProperties;

/**
 * Publishes an ancestor folder of the asset involved in a workflow. Values to
 * use: PARENT - 1 Folder Up GRANDPARENT - 2 Folders Up GREATGRANDPARENT - 3
 * Folders Up (not implemented yet)
 * 
 * @author VerschageJX
 */
public class ParentFolderPublisher extends Publisher {
	private static final Logger LOG = Logger.getLogger(ParentFolderPublisher.class);
	private static final String PARENT_PARAM_PROP = "parent.param.name";
	private static final String DEFAULT_PARAM_PROP = "parent.parent.default.value";

	public boolean process() throws TriggerProviderException {
		final String parentParam, defaultValue;
		try {
			Properties cascadeProperties = CascadeCustomProperties.getProperties();
			parentParam = cascadeProperties.getProperty(PARENT_PARAM_PROP);
			defaultValue = cascadeProperties.getProperty(DEFAULT_PARAM_PROP);
		} catch (IOException ioe) {
			throw new TriggerProviderException(ioe.getMessage(), ioe);
		}
		final String relatedEntityId;
		try {
			Workflow commonWorkflow = ((PublicWorkflowAdapter) this.workflow).getWorkflow();
			relatedEntityId = commonWorkflow.getRelatedEntityId();
		} catch (Throwable t) {
			String workflowName = this.workflow == null ? "null" : this.workflow.getName();
			String err = "Could not get commonWorkflow for workflow " + workflowName;
			LOG.fatal(err, t);
			throw new FatalTriggerProviderException(err, t);
		}
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
		String parentType = getParameter(parentParam);
		if (parentType == null || parentType == "") {
			parentType = defaultValue;
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
		if (parentFolderLocation == "") {
			return fail("Missing " + parentType + " folder for asset: " + itemPath);
		}
		try {
			fce = service.locateFolderContainedEntity(parentFolderLocation, EntityTypes.TYPE_FOLDER, siteId);
		} catch (Exception e) {
			return fail("Could not get the designated folder: " + parentFolderLocation + " in site ID " + siteId);
		}
		PublishRequest pubReq = new PublishRequest();
		pubReq.setId(fce.getId());
		pubReq.setFolder((Folder) fce);
		pubReq.setPublishAllDestinations(true);
		pubReq.setGenerateReport(false);
		pubReq.setUsername("_publisher");
		PublishService pubServ = this.serviceProvider.getPublishService();
		try {
			pubServ.queue(pubReq);
		} catch (Exception e) {
			throw new TriggerProviderException(e.getLocalizedMessage(), e);
		}
		return true;
	}

	private boolean fail(String message) {
		LOG.error(message);
		return false;
	}

	public boolean triggerShouldFetchEntity() {
		return true;
	}
}

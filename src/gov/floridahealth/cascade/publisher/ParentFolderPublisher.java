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

	static class Config {
		private static final String PARENT_PARAM_PROP = "parent.param.name";
		private static final String DEFAULT_VALUE_PROP = "parent.parent.default.value";
		final String parentParam;
		final String defaultValue;
		public Config() throws TriggerProviderException {
			String parentParam = null, defaultValue = null;
			try {
				Properties cascadeProperties = CascadeCustomProperties.getProperties();
				parentParam = cascadeProperties.getProperty(PARENT_PARAM_PROP);
				defaultValue = cascadeProperties.getProperty(DEFAULT_VALUE_PROP);
			} catch (IOException ioe) {
				LOG.error("Could not get properties", ioe);
				throw new TriggerProviderException(ioe.getMessage(), ioe);
			} finally {
				this.parentParam = parentParam;
				this.defaultValue = defaultValue;
			}
		}
	}

	public boolean process() throws TriggerProviderException {
		final Config config = new Config();
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
		String parentType = getParameter(config.parentParam);
		if (parentType == null || parentType == "") {
			parentType = config.defaultValue;
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
		queuePublishRequest(fce);
		return true;
	}

	private boolean fail(String message) {
		LOG.error(message);
		return false;
	}

	private String getRelatedEntityId() throws FatalTriggerProviderException {
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
		return relatedEntityId;
	}

	private void queuePublishRequest(FolderContainedEntity fce) throws TriggerProviderException {
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
	}

	public boolean triggerShouldFetchEntity() {
		return true;
	}
}

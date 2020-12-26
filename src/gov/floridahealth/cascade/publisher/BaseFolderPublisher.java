package gov.floridahealth.cascade.publisher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.WorkflowTriggerProcessingResult;
import com.cms.workflow.function.Publisher;
import com.hannonhill.cascade.model.dom.*;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

import gov.floridahealth.util.CascadeCustomProperties;

public abstract class BaseFolderPublisher extends Publisher {
	private static final String DEV_ENV_PROP_PREFIX = "dev";
	private static final String TEST_ENV_PROP_PREFIX = "test";
	private static final String PROD_ENV_PROP_PREFIX = "prod";
	private static final String NODE_HOST_PROP_SUFFIX = ".nodejs.host";
	private static final String CASCADE_HOST_PROP_SUFFIX = ".cascade.host";

	protected WorkflowTriggerProcessingResult fail(String message) {
		getLog().error(message);
		return WorkflowTriggerProcessingResult.CONTINUE;
	}

	protected abstract Logger getLog();

	protected String getCascadeHost() throws TriggerProviderException {
		return getServerName();
	}

	protected String getServerName() throws TriggerProviderException {
		final String serverName;
		final Map<String, String> system = System.getenv();
	    if (system.containsKey("COMPUTERNAME")) {
	    	serverName = system.get("COMPUTERNAME").toLowerCase(); // "HOSTNAME" on Unix/Linux
	    } else try {
	    	serverName = InetAddress.getLocalHost().getHostName().toLowerCase();
	    } catch (UnknownHostException e) {
	    	throw new TriggerProviderException(e.getMessage(), e);
	    }
	    return serverName;
	}

	protected String getNodeJsHost() throws TriggerProviderException {
		final String serverName = getServerName();
		return getNodeJsHost(serverName);
	}

	protected String getNodeJsHost(String serverName) throws TriggerProviderException {
		final String cascadeTest = CascadeCustomProperties.getProperty(TEST_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		final String cascadeProd = CascadeCustomProperties.getProperty(PROD_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		final String environment;
		if (serverName.startsWith(cascadeProd)) {
			environment = PROD_ENV_PROP_PREFIX;
		} else if (serverName.startsWith(cascadeTest)) {
			environment = TEST_ENV_PROP_PREFIX;
		} else {
			environment = DEV_ENV_PROP_PREFIX;
		}
		final String nodeHostProp = environment + NODE_HOST_PROP_SUFFIX;
		final String nodeHost = CascadeCustomProperties.getProperty(nodeHostProp);
		return "https://" + nodeHost + ":8443";
	}

	protected String getRelatedEntityId() throws FatalTriggerProviderException {
		try {
			Workflow commonWorkflow = ((PublicWorkflowAdapter) this.workflow).getWorkflow();
			return commonWorkflow.getRelatedEntityId();
		} catch (Throwable t) {
			String workflowName = this.workflow == null ? "null" : this.workflow.getName();
			String err = "Could not get common workflow for workflow " + workflowName;
			getLog().fatal(err, t);
			throw new FatalTriggerProviderException(err, t);
		}
	}

	// return whether the asset is a page in a top-level locations folder and is not the index page
	protected boolean isLocation(FolderContainedEntity asset) {
		final String path = asset.getPath();
		final int slashIndex = path.indexOf("/");
		final boolean isTopLevel = slashIndex > 0 && path.indexOf("/", slashIndex + 1) < 0;
		final boolean isLocation = isTopLevel && path.startsWith("locations");
		final boolean isPage = EntityTypes.TYPE_PAGE.equals(asset.getType());
		return isLocation && isPage && !path.endsWith("index");
	}

	// Hierarchy: File < PublishableEntity < ExpiringEntity < DublinMetadataAwareEntity < FolderContainedEntity
	protected void queuePublishRequest(File file) throws TriggerProviderException {
		PublishRequest pubReq = new PublishRequest();
		// Without both the ID of the file and the File object, publishing fails.
		pubReq.setId(file.getId());
		pubReq.setFile(file);
		queuePublishRequest(pubReq);
	}

	// Hierarchy: Folder < PublishableEntity < ExpiringEntity < DublinMetadataAwareEntity < FolderContainedEntity
	protected void queuePublishRequest(Folder folder) throws TriggerProviderException {
		PublishRequest pubReq = new PublishRequest();
		// Without both the ID of the folder and the Folder object, publishing fails.
		pubReq.setId(folder.getId());
		pubReq.setFolder(folder);
		queuePublishRequest(pubReq);
	}

	// Hierarchy: Page < PublishableEntity < ExpiringEntity < DublinMetadataAwareEntity < FolderContainedEntity
	protected void queuePublishRequest(Page page) throws TriggerProviderException {
		PublishRequest pubReq = new PublishRequest();
		// Without both the ID of the page and the Page object, publishing fails.
		pubReq.setId(page.getId());
		pubReq.setPage(page);
		queuePublishRequest(pubReq);
	}

	private void queuePublishRequest(PublishRequest pubReq) throws TriggerProviderException {
		pubReq.setPublishAllDestinations(true);
		pubReq.setGenerateReport(false);
		pubReq.setUsername("_publisher");
		PublishService pubServ = this.serviceProvider.getPublishService();
		try {
			pubServ.queue(pubReq);
		} catch (Exception e) {
			getLog().error("Publish queue failed", e);
			throw new TriggerProviderException(e.getLocalizedMessage(), e);
		}
	}

    @Override
	public boolean triggerShouldFetchEntity() {
		return true;
	}
}

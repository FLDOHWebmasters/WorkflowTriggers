package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.function.Publisher;
import com.hannonhill.cascade.model.dom.*;
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

public abstract class BaseFolderPublisher extends Publisher {
	protected boolean fail(String message) {
		getLog().error(message);
		return false;
	}

	protected abstract Logger getLog();

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

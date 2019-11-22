package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.function.Publisher;
import com.hannonhill.cascade.model.dom.Folder;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.PublishRequest;
import com.hannonhill.cascade.model.dom.Workflow;
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

	protected void queuePublishRequest(FolderContainedEntity fce) throws TriggerProviderException {
		PublishRequest pubReq = new PublishRequest();
		// Without both the ID of the folder and the Folder object, publishing fails.
		pubReq.setId(fce.getId());
		pubReq.setFolder((Folder)fce);
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

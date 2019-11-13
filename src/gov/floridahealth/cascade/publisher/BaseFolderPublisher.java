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
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

import gov.floridahealth.cascade.properties.CascadeCustomProperties;

public abstract class BaseFolderPublisher extends Publisher {
	protected static class Config {
		static final String PARENT_PARAM_PROP = "parent.param.name";
		static final String DEFAULT_VALUE_PROP = "parent.parent.default.value";
		static final String JSON_LOCATION_PROP = "json.defaultFolder";
		final String parentParam;
		final String defaultValue;
		final String jsonLocation;
		public Config() throws TriggerProviderException {
			String parentParam = null, defaultValue = null, jsonLocation = null;
			try {
				Properties cascadeProperties = CascadeCustomProperties.getProperties();
				parentParam = cascadeProperties.getProperty(PARENT_PARAM_PROP);
				defaultValue = cascadeProperties.getProperty(DEFAULT_VALUE_PROP);
				jsonLocation = cascadeProperties.getProperty(JSON_LOCATION_PROP);
			} catch (IOException ioe) {
				Logger.getLogger(BaseFolderPublisher.class).error("Could not get properties", ioe);
				throw new TriggerProviderException(ioe.getMessage(), ioe);
			} finally {
				this.parentParam = parentParam;
				this.defaultValue = defaultValue;
				this.jsonLocation = jsonLocation;
			}
		}
	}

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
			String err = "Could not get commonWorkflow for workflow " + workflowName;
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
			throw new TriggerProviderException(e.getLocalizedMessage(), e);
		}
	}

	public boolean triggerShouldFetchEntity() {
		return true;
	}
}

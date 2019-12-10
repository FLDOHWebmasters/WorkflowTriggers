package gov.floridahealth.cascade.trigger;

import java.util.*;
import org.apache.log4j.Logger;
import com.cms.publish.*;

/**
 * This is custom a publish trigger, configurable in Cascade under Administration >
 * Manage Triggers & Plugins > Publish Triggers. The idea was to hardcode it to publish
 * dependent resources when certain resources are published, for instance to publish
 * the locations index page when any other page in the locations folder is published.
 * However, Cascade doesn't provide a simple means to call back into its API from
 * this kind of trigger, so this class now merely serves as a documented example
 * to help a would-be future developer implement a publish trigger.
 * @author KnickerbockerGM
 */
public class ExamplePublishTrigger implements PublishTrigger {
	private static final Logger LOG = Logger.getLogger(ExamplePublishTrigger.class);

	private Map<String, String> parameters = new HashMap<String, String>();
	private PublishTriggerInformation information;

	@Override
	public void setParameter(String name, String value) {
		parameters.put(name, value);
	}

	/**
	 * PublishTriggerInformation contains the following properties:<ul>
	 * <li>destinationId - the Cascade unique ID of the target Destination</li>
	 * <li>destination - (duplicative) the Cascade unique ID of the target Destination</li>
	 * <li>destinationName - the name of the target Destination as listed under Manage Site > Destinations</li>
	 * <li>entityId - the Cascade unique ID of the file or page entity being published</li>
	 * <li>entityPath - the relative path of the file or page within its site</li>
	 * <li>entityType - 1 for a file, 2 for a page</li>
	 * <li>pageConfigurationId - null for files; for entityType 2 (page) it's the Cascade unique ID of the Configuration</li>
	 * <li>transportId - the Cascade unique ID of the target Transport underlying the aforementioned destination</li>
	 * <li>unpublish - true if the publish is an unpublish, otherwise false</li>
	 * </ul>
	 */
	@Override
	public void setPublishInformation(PublishTriggerInformation info) {
		information = info;
	}

	@Override
	public void invoke() throws PublishTriggerException {
		LOG.info("EntityPath " + information.getEntityPath());
		if (information.getEntityType() != PublishTriggerEntityTypes.TYPE_PAGE) {
			return; // ignore files, we care only about pages
		}
		// do stuff
	}
}

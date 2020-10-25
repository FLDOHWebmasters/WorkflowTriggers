package gov.floridahealth.cascade.workflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.WorkflowTriggerProcessingResult;
import com.cms.workflow.function.Publisher;

import gov.floridahealth.util.CascadeCustomProperties;

public class LocationJsonWorkflowTrigger extends Publisher {
	private static final Logger LOG = Logger.getLogger(LocationJsonWorkflowTrigger.class);
	private static final String DEV_ENV_PROP_PREFIX = "dev";
	private static final String TEST_ENV_PROP_PREFIX = "test";
	private static final String PROD_ENV_PROP_PREFIX = "prod";
	private static final String NODE_HOST_PROP_SUFFIX = ".nodejs.host";
	private static final String CASCADE_HOST_PROP_SUFFIX = ".cascade.host";

	/**
	 * Main function of the Workflow Trigger <ol>
	 * <li>.</li>
	 * </ol>
	 * @return WorkflowTriggerProcessingResult whether the parent resource was published successfully
	 */
	@Override
	public WorkflowTriggerProcessingResult process() throws TriggerProviderException {
		String serverName;
		Map<String, String> system = System.getenv();
	    if (system.containsKey("COMPUTERNAME")) {
	    	serverName = system.get("COMPUTERNAME"); // "HOSTNAME" on Unix/Linux
	    } else try {
	    	serverName = InetAddress.getLocalHost().getHostName();
	    } catch (UnknownHostException e) {
	    	throw new TriggerProviderException(e.getMessage(), e);
	    }
		String cascadeTest = CascadeCustomProperties.getProperty(TEST_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		String cascadeProd = CascadeCustomProperties.getProperty(PROD_ENV_PROP_PREFIX + CASCADE_HOST_PROP_SUFFIX);
		String environment = DEV_ENV_PROP_PREFIX;
		if (serverName.startsWith(cascadeTest)) {
			environment = TEST_ENV_PROP_PREFIX;
		} else if (serverName.startsWith(cascadeProd)) {
			environment = PROD_ENV_PROP_PREFIX;
		}
		final String nodeHostProp = environment + NODE_HOST_PROP_SUFFIX;
		final String nodeHost = CascadeCustomProperties.getProperty(nodeHostProp);

		LOG.info("Starting location JSON publish trigger");
		final URL url;
		try {
			url = new URL("https://" + nodeHost + ":8443/locations/load");
		} catch (MalformedURLException e) {
			throw new TriggerProviderException(e.getMessage(), e);
		}
		String outputString = "";
		try {
			HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
			InputStreamReader isr = new InputStreamReader(httpConn.getInputStream());
			BufferedReader in = new BufferedReader(isr);
			String responseString;
			while ((responseString = in.readLine()) != null) {
				outputString = outputString + responseString;
			}
		} catch (IOException e) {
			throw new TriggerProviderException(e.getMessage(), e);
		}
		System.out.println(outputString);
		return WorkflowTriggerProcessingResult.CONTINUE;
	}

    @Override
	public boolean triggerShouldFetchEntity() {
		return false;
	}
}

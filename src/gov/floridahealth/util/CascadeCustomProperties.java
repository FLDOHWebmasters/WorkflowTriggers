package gov.floridahealth.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.cms.workflow.TriggerProviderException;

/**
 * Simple Properties Object for the Cascade Custom Workflow Triggers
 * @author VerschageJX
 */
public class CascadeCustomProperties {
	private static final String PROP_FILE = "/resources/doh-custom.properties";
	private static Properties props;

	/**
	 * Gets a property value for use by a workflow trigger.
	 * @param propertyName the name of the property
	 * @return the value of the property
	 * @throws TriggerProviderException the properties file cannot be loaded
	 */
	public static String getProperty(String propertyName) throws TriggerProviderException {
		if (props == null) {
			try {
				loadProperties();
				Logger logger = Logger.getLogger(CascadeCustomProperties.class);
				logger.info("Loaded " + props.size() + " properties from " + PROP_FILE);
				for (Object key: props.keySet()) {
					logger.info(key + "=" + props.getProperty(String.valueOf(key)));
				}
			} catch (IOException e) {
				Logger.getLogger(CascadeCustomProperties.class).error("Could not get properties", e);
				throw new TriggerProviderException("Failed to get property " + propertyName, e);
			}
		}
		return props.getProperty(propertyName);
	}

	/**
	 * Caches the properties found in /resources/config.properties
	 * @return properties relevant to workflow triggers
	 * @throws IOException if the file is not found or loaded
	 */
	private static void loadProperties() throws IOException {
		InputStream inputStream = CascadeCustomProperties.class.getClassLoader().getResourceAsStream(PROP_FILE);
		if (inputStream == null) {
			throw new FileNotFoundException("Property File " + PROP_FILE + " not found.");
		}
		try {
			props = new Properties();
			props.load(inputStream);
		} finally {
			inputStream.close();
		}
	}
}
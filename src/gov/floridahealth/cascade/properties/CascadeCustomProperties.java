package gov.floridahealth.cascade.properties;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Simple Properties Object for the Cascade Custom Workflow Triggers
 * @author VerschageJX
 */
public class CascadeCustomProperties extends Properties {
    
	private static final long serialVersionUID = 1L;
	private static Properties props;
	
	/**
	 * Property Values Singleton for the DOH Cascade Custom Jar
	 * @throws IOException returned from getProperties
	 */
	private CascadeCustomProperties() throws IOException {
		loadPropValues();
	}

	/**
	 * Retrieves the Properties object from the static object
	 * @return Cascade specific properties object
	 * @throws IOException returned from loadPropValues
	 */
	public static Properties getProperties() throws IOException{
		if (props == null) {
			props = new CascadeCustomProperties();
		}
		return props;
	}
        
	/**
	 * Loads the properties found in /resources/config.properties
	 * @throws IOException Unable to find the file
	 */
    private void loadPropValues() throws IOException {
        String propFileName = "/resources/config.properties";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(propFileName);
        if (inputStream != null) {
            props.load(inputStream);            ;
        }
        throw new FileNotFoundException("Property File " + propFileName + " not found.");
    }
}
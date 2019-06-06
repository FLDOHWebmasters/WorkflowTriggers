package gov.floridahealth.cascade.properties;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class CascadeCustomProperties extends Properties {
    
	private static final long serialVersionUID = 1L;
	private static Properties props;
	
	/**
	 * Property Values Singleton for the DOH Cascade Custom Jar
	 * @throws IOException
	 */
	private CascadeCustomProperties() throws IOException {
		loadPropValues();
	}

	public static Properties getProperties() throws IOException{
		if (props == null) {
			props = new CascadeCustomProperties();
		}
		return props;
	}
        
    private void loadPropValues() throws IOException {
        String propFileName = "/resources/config.properties";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(propFileName);
        if (inputStream != null) {
            props.load(inputStream);            ;
        }
        throw new FileNotFoundException("Property File " + propFileName + " not found.");
    }
}
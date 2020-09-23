package gov.floridahealth.cascade.assetfactory;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cms.assetfactory.AssetFactoryPlugin;
import com.cms.assetfactory.PluginException;
import com.cms.assetfactory.StructuredDataPlugin;
import com.hannonhill.cascade.api.asset.admin.AssetFactory;
import com.hannonhill.cascade.api.asset.common.StructuredDataNode;
import com.hannonhill.cascade.api.asset.common.TextNodeOptions;
import com.hannonhill.cascade.api.asset.home.FolderContainedAsset;
import com.hannonhill.cascade.api.asset.home.StructuredDataCapableAsset;
import com.hannonhill.commons.util.StringUtil;

/**
 * This plugin runs after the user submits the new page and 
 * takes the value from the Data Definition fields
 * specified, concatenates them together using the specified concatenation token,
 * replaces spaces with specified space token, converts to lower case,
 * makes the result "safe" and uses that as the system name.
 * 
 * Code is mostly copied from HH's com.cms.assetfactory.StructuredDataFieldsToSystemNamePlugin,
 * except dates are formatted differently and existing system name is overwritten.
 * 
 * @author  Glen Knickerbocker
 * @version $Id$
 */
public class StructuredDataFieldsToSystemNameReplacePlugin extends StructuredDataPlugin implements AssetFactoryPlugin {
    private static final Logger LOG = getLogger(StructuredDataFieldsToSystemNameReplacePlugin.class);

    private static final String DESCRIPTION_KEY = "plugin.asset.factory.description.sdfieldstosystemnamex";
    private static final String NAME_KEY = "plugin.asset.factory.name.sdfieldstosystemnamex";

    private static final String PARAM_FIELD_IDS_NAME_KEY = "assetfactory.plugin.sdfieldstosystemname.param.name.fieldids";
    private static final String PARAM_FIELD_IDS_DESCRIPTION_KEY = "assetfactory.plugin.sdfieldstosystemname.param.description.fieldids";

    private static final String PARAM_CONCAT_TOKEN_NAME_KEY = "assetfactory.plugin.sdfieldstosystemname.param.name.concattoken";
    private static final String PARAM_CONCAT_TOKEN_DESCRIPTION_KEY = "assetfactory.plugin.sdfieldstosystemname.param.description.concattoken";

    private static final String PARAM_SPACE_TOKEN_NAME_KEY = "assetfactory.plugin.sdfieldstosystemname.param.name.spacetoken";
    private static final String PARAM_SPACE_TOKEN_DESCRIPTION_KEY = "assetfactory.plugin.sdfieldstosystemname.param.description.spacetoken";

    /* (non-Javadoc)
     * @see com.cms.assetfactory.BaseAssetFactoryPlugin#doPluginActionPre(com.hannonhill.cascade.api.asset.admin.AssetFactory, com.hannonhill.cascade.api.asset.home.FolderContainedAsset)
     */
    @Override
    public void doPluginActionPre(AssetFactory factory, FolderContainedAsset asset) throws PluginException
    {
        LOG.debug("Plugin Action Pre started");
        if (!isValidType(asset)) {
            return;
        }
        asset.setHideSystemName(true);
        if (StringUtil.isEmpty(asset.getName())) {
            // Setting name to avoid form validation error: System Name required when there is no base asset in asset factory
            asset.setName("hidden");
        }
        LOG.debug("Plugin Action Pre finished");
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.BaseAssetFactoryPlugin#doPluginActionPost(com.hannonhill.cascade.api.asset.admin.AssetFactory, com.hannonhill.cascade.api.asset.home.FolderContainedAsset)
     */
    @Override
    public void doPluginActionPost(AssetFactory factory, FolderContainedAsset asset) throws PluginException
    {
        LOG.debug("Plugin Action Post started");
        if (!isValidType(asset))
        {
            // Although the plugin is disabled for other asset type, allow the asset to be created
            this.setAllowCreation(true, "");
            return;
        }

        StructuredDataCapableAsset sdCapable = (StructuredDataCapableAsset) asset;
        if (sdCapable.getStructuredData() == null)
        {
            this.setAllowCreation(true, "");
            LOG.debug("The asset has no Data Definition. Plugin quits.");
            return;
        }

        String sdIdentifiers = getParameter(PARAM_FIELD_IDS_NAME_KEY);
        if (StringUtil.isEmpty(sdIdentifiers))
        {
            this.setAllowCreation(true, "");
            LOG.debug("The field identifiers parameter is required. Plugin quits.");
            return;
        }

        StringBuilder newName = new StringBuilder();
        String concatToken = getParameter(PARAM_CONCAT_TOKEN_NAME_KEY);

        // Make concatToken be an empty string in case if it is null or has spaces
        if (StringUtil.isEmptyTrimmed(concatToken)) {
            concatToken = "";
        }
        String[] identifiers = sdIdentifiers.split(",");
        for (String identifier : identifiers)
        {
            identifier = identifier.trim();
			String result = getStructuredData(sdCapable, identifier);
            if (StringUtil.isNotEmptyTrimmed(result))
            {
                newName.append(result);
                newName.append(concatToken);
                LOG.debug(StringUtil.concat("Found a value for ", identifier, " field: ", result, ". The current new name is: ", newName));
            }
        }

        // Delete the last concatToken
        if (newName.length() > concatToken.length()) {
            newName.delete(newName.length() - concatToken.length(), newName.length());
        }
        LOG.debug(StringUtil.concat("The new name after concatenations is: ", newName));

        String newNameStr = newName.toString();
        if (StringUtil.isEmptyTrimmed(newNameStr))
        {
            setAllowCreation(false, StringUtil.concat("Content must be entered for at least one of the following fields: ", sdIdentifiers));
            LOG.debug(StringUtil.concat("None of the fields ", sdIdentifiers, " are populated. Asset creation is not allowed. Plugin quits."));
            return;
        }

        newNameStr = newNameStr.toLowerCase();
        LOG.debug(StringUtil.concat("The new name in lower case is: ", newNameStr));

        String spaceToken = getParameter(PARAM_SPACE_TOKEN_NAME_KEY);
        if (spaceToken != null)
        {
            newNameStr = newNameStr.replaceAll("\\s+", spaceToken);
            LOG.debug(StringUtil.concat("The new name after adding space tokens is: ", newNameStr));
        }

        newNameStr = utilityProvider.getFilenameNormalizer().normalize(newNameStr, new ArrayList<Character>());
        LOG.debug(StringUtil.concat("The normalized new name field is: ", newNameStr));

        sdCapable.setName(newNameStr);
        this.setAllowCreation(true, "");
        LOG.debug("Plugin finished execution");
    }

    /**
     * Returns true if the type of asset is valid for this plugin. If not valid logs the information and returns false;
     * 
     * @param asset
     * @return
     */
    private boolean isValidType(FolderContainedAsset asset)
    {
        if (asset instanceof StructuredDataCapableAsset) {
            return true;
        }
        LOG.debug("The asset is not a StructuredDataCapableAsset. Plugin quits.");
        return false;
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getAvailableParameterDescriptions()
     */
    public Map<String, String> getAvailableParameterDescriptions()
    {
        Map<String, String> toRet = new HashMap<String, String>();
        toRet.put(PARAM_FIELD_IDS_NAME_KEY, PARAM_FIELD_IDS_DESCRIPTION_KEY);
        toRet.put(PARAM_CONCAT_TOKEN_NAME_KEY, PARAM_CONCAT_TOKEN_DESCRIPTION_KEY);
        toRet.put(PARAM_SPACE_TOKEN_NAME_KEY, PARAM_SPACE_TOKEN_DESCRIPTION_KEY);
        return toRet;
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getAvailableParameterNames()
     */
    public String[] getAvailableParameterNames()
    {
        return new String[]
        {
            PARAM_FIELD_IDS_NAME_KEY, PARAM_CONCAT_TOKEN_NAME_KEY, PARAM_SPACE_TOKEN_NAME_KEY
        };
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getDescription()
     */
    public String getDescription()
    {
        return DESCRIPTION_KEY;
    }

    /* (non-Javadoc)
     * @see com.cms.assetfactory.AssetFactoryPlugin#getName()
     */
    public String getName()
    {
        return NAME_KEY;
    }

    /**
     * Attempts to retrieve the value of a text node from the given asset's Structured Data.
     * @param asset the {@link StructuredDataCapableAsset} to search within
     * @param sdIdentifier the identifier of the {@link StructuredDataNode} to look for.
     * @return the value of the node, or null if the node is not found, not plain text or empty
     */
    private String getStructuredData(StructuredDataCapableAsset asset, String sdIdentifier)
    {
        sdIdentifier = StringUtil.removeLeadingSlashes(sdIdentifier);
        StructuredDataNode node = asset.getStructuredDataNode(sdIdentifier);
        //StructuredDataNode[] structuredData = asset.getStructuredData();

        if (node != null && node.isText())
        {
            TextNodeOptions options = node.getTextNodeOptions();
            if (options.isPlainText() || options.isDatetime()) {
	            try
	            {
	                String[] nodeValues = node.getTextValues();
	                if (nodeValues.length > 0 && StringUtil.isNotEmptyTrimmed(nodeValues[0])) {
	                	if (options.isDatetime()) {
	                		try {
	                			long unixTime = Long.parseLong(nodeValues[0]);
		                		Date date = new Date(unixTime);
		                		DateFormat format = new SimpleDateFormat("yyMMdd");
		                		return format.format(date);
	                		} catch (Exception e) {
	        	            	String message = "Error occurred when retrieving node date value: ";
	        	                LOG.debug(message + e.getMessage(), e);
	                		}
	                	}
	                    return nodeValues[0];
	                }
	            }
	            catch (Exception e)
	            {
	                // Invalid XML. Skipping.
	            	String message = "Error occurred when retrieving node text values possibly due to invalid XML during link rewriting: ";
	                LOG.debug(message + e.getMessage(), e);
	            }
            }
        }
        return null;
    }
}

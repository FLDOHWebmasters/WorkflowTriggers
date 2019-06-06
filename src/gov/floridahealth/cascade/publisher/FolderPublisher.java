/**
 * Publishes a Folder of a site without need of a Publish Set
 * This was created in order to remove the need to create 67 Publish Sets and 268 Workflow Definitons in order to publish
 * the JSON files that handle the Left Hand Navigation 
 */
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
import com.hannonhill.cascade.model.dom.Site;
import com.hannonhill.cascade.model.dom.Workflow;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.util.SiteUtil;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

import gov.floridahealth.cascade.properties.CascadeCustomProperties;

public class FolderPublisher extends Publisher
{
  private static final Logger LOG = Logger.getLogger(FolderPublisher.class);
  private static final String JSON_LOCATION_PROP = "json.defaultFolder";
  
  /**
   * Main function of the Workflow Trigger
   * 1.) Checks to see if a folder is specified.  If so, it uses it.  If not, it defaults to the JSON folder.
   * 2.) Checks to see if a site is specified for the folder.  If it does not exist, defaults to the Site for the Asset
   *     being modified by the Workflow.
   * 3.) Constructs a Publish Request based upon the options and initiates it.  
   * @return boolean Successful Completion of Folder publish 
   */
  public boolean process()
    throws TriggerProviderException
  {
	 Properties cascadeProperties = null;
	 String jsonLocation = null;
	  try {
    	cascadeProperties = CascadeCustomProperties.getProperties();
    } catch (IOException ioe) {
    	throw new TriggerProviderException(ioe.getMessage());
    }
    if (cascadeProperties != null ) {
    	jsonLocation = cascadeProperties.getProperty(JSON_LOCATION_PROP);
    }
	  
	String folderLocation = getParameter("folder");
    String siteName = getParameter("site");
    Workflow commonWorkflow;
    try
    {
    	commonWorkflow = ((PublicWorkflowAdapter)this.workflow).getWorkflow();
    }
    catch (Throwable t)
    {
      String err = String.format("Could not get commonWorkflow for workflow %s", new Object[] { this.workflow.getName() });
      LOG.fatal(err, t);
      throw new FatalTriggerProviderException(err, t);
    }
    
    String relatedEntityId = commonWorkflow.getRelatedEntityId();
    String siteId;
    if ((siteName != null) && (siteName != "")) {
      siteId = GetSiteIdFromName(siteName);
    } else {
      siteId = GetSiteIdFromAsset(relatedEntityId);
    }
    if (siteId != null)
    {
      String path = "";
	  FolderContainedEntity fce;
      if ((folderLocation != null) && (folderLocation != ""))
      {
        path = folderLocation;
        fce = TryGetFolder(siteId, path);
      }
      else if (jsonLocation != null) 
      {
        path = jsonLocation;
        fce = TryGetFolder(siteId, path);
      } else {
    	  return false;
      }
      Folder folder = (Folder)fce;
      
      PublishRequest pubReq = new PublishRequest();
      /* 
       * No, I do not know why it needs the ID of the folder and the Folder object of what it's publishing.
       * What I do know is that without both, it throws up.  C'est la vie.
       */
      pubReq.setId(fce.getId());
      pubReq.setFolder(folder);      
      pubReq.setPublishAllDestinations(true);
      pubReq.setGenerateReport(false);
      pubReq.setUsername("_publisher");
      
      /* Kick off the publish */
      PublishService pubServ = this.serviceProvider.getPublishService();
      try
      {
        pubServ.queue(pubReq);
      }
      catch (Exception e)
      {
        throw new TriggerProviderException(e.getLocalizedMessage());
      }
      return true;
    }
    return false;
  }
  
  /**
   *  Attempts to retrieve the folder object (which is itself a FolderContainedEntity)
   * @param siteId Site ID of the Folder
   * @param path Path of the folder
   * @return FolderContainedEntity object to publish
   */
  private FolderContainedEntity TryGetFolder(String siteId, String path)
  {
	FolderContainedEntity folder;
	try
    {
      LOG.info("Getting folder id");
      folder = this.serviceProvider.getLocatorService().locateFolderContainedEntity(path, EntityTypes.TYPE_FOLDER, siteId);
    }
    catch (Exception e)
    {
      LOG.error("Could not get the designated Folder");
      return null;
    }
    
    return folder;
  }
  
  /**
   * Obtains the FolderContainedEntity object represenatation of the asset, then pulls the Site ID from that.
   * @param assetId ID of the Asset in the associated Workflow 
   * @return Site ID of the Asset
   */
  private String GetSiteIdFromAsset(String assetId)
  {
    FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(assetId);
    if (fce == null)
    {
      LOG.error(String.format("Asset could not be found, ID: %s", new Object[] { assetId }));
      return null;
    }
    return SiteUtil.getSiteId(fce);
  }
  
	/**
	 * Pulls the Site ID based upon the parameter name if passed 
	 * @param siteName
	 * @return Site ID
	 */
  private String GetSiteIdFromName(String siteName)
  {
    if ((siteName == null) || (siteName.length() == 0))
    {
      LOG.error("siteName is null or empty");
      return null;
    }
    Site site = this.serviceProvider.getSiteService().getByName(siteName);
    if (site == null)
    {
      LOG.error(String.format("Site not found %s", new Object[] { siteName }));
      return null;
    }
    return site.getId();
  }
  
  /**
   * Utility Function required by Cascade
   * @return always true
   */
  public boolean triggerShouldFetchEntity()
  {
    return true;
  }
}

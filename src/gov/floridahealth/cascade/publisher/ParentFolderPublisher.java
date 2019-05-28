
package gov.floridahealth.cascade.publisher;

import org.apache.log4j.Logger;

import com.cms.workflow.FatalTriggerProviderException;
import com.cms.workflow.TriggerProviderException;
import com.cms.workflow.function.Publisher;
import com.hannonhill.cascade.model.dom.Folder;
import com.hannonhill.cascade.model.dom.FolderContainedEntity;
import com.hannonhill.cascade.model.dom.PublishRequest;
import com.hannonhill.cascade.model.dom.Workflow;
import com.hannonhill.cascade.model.dom.identifier.EntityTypes;
import com.hannonhill.cascade.model.service.PublishService;
import com.hannonhill.cascade.model.util.SiteUtil;
import com.hannonhill.cascade.model.workflow.adapter.PublicWorkflowAdapter;

public class ParentFolderPublisher
extends Publisher {
    private static final Logger LOG = Logger.getLogger(ParentFolderPublisher.class);
    private static final String PARENT_PARAM = "parent_type";

    public boolean process() throws TriggerProviderException {
        Workflow commonWorkflow;
        String parentType = this.getParameter(PARENT_PARAM);
        String parentFolderLocation = "";
        LOG.info((Object)("Parent Type: " + parentType));
        try {
            commonWorkflow = ((PublicWorkflowAdapter)this.workflow).getWorkflow();
        }
        catch (Throwable t) {
            String err = String.format("Could not get commonWorkflow for workflow %s", this.workflow.getName());
            LOG.fatal((Object)err, t);
            throw new FatalTriggerProviderException(err, t);
        }
        String relatedEntityId = commonWorkflow.getRelatedEntityId();
        String siteId = this.GetSiteIdFromAsset(relatedEntityId);
        parentFolderLocation = parentType != null && parentType != "" ? this.GetParentFolderFromAsset(relatedEntityId, parentType) : this.GetParentFolderFromAsset(relatedEntityId, "PARENT");
        LOG.info((Object)("Folder Path: " + parentFolderLocation));
        if (siteId != null && parentFolderLocation != "") {
            String path = "";
            path = parentFolderLocation;
            FolderContainedEntity fce = this.TryGetFolder(siteId, path);
            Folder folder = (Folder)fce;
            PublishRequest pubReq = new PublishRequest();
            pubReq.setId(fce.getId());
            pubReq.setFolder(folder);
            pubReq.setPublishAllDestinations(true);
            pubReq.setGenerateReport(false);
            pubReq.setUsername("_publisher");
            PublishService pubServ = this.serviceProvider.getPublishService();
            try {
                pubServ.queue(pubReq);
            }
            catch (Exception e) {
                throw new TriggerProviderException(e.getLocalizedMessage());
            }
            return true;
        }
        return false;
    }

    private FolderContainedEntity TryGetFolder(String siteId, String path) {
        FolderContainedEntity folder;
        try {
            LOG.info((Object)"Getting folder id");
            folder = this.serviceProvider.getLocatorService().locateFolderContainedEntity(path, EntityTypes.TYPE_FOLDER, siteId);
        }
        catch (Exception e) {
            LOG.error((Object)"Could not get the designated Folder");
            return null;
        }
        return folder;
    }

    private String GetSiteIdFromAsset(String assetId) {
        FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(assetId);
        if (fce == null) {
            LOG.error((Object)String.format("Asset could not be found, ID: %s", assetId));
            return null;
        }
        return SiteUtil.getSiteId((FolderContainedEntity)fce);
    }

    private String GetParentFolderFromAsset(String assetId, String parentType) {
        FolderContainedEntity fce = this.serviceProvider.getLocatorService().locateFolderContainedEntity(assetId);
        if (fce == null) {
            LOG.error((Object)String.format("Asset could not be found, ID: %s", assetId));
            return null;
        }
        String itemPath = fce.getPath();
        if (parentType.equals("PARENT")) {
            return itemPath.substring(0, itemPath.lastIndexOf(47));
        }
        if (parentType.equals("GRANDPARENT")) {
            if (itemPath.lastIndexOf("/") > 1) {
                return itemPath.substring(0, itemPath.lastIndexOf(47, itemPath.lastIndexOf("/") - 1));
            }
            LOG.error((Object)"Can not publish Grandparent of file in single folder");
            return null;
        }
        LOG.error((Object)"Parent Value not set, returning null");
        return null;
    }

    public boolean triggerShouldFetchEntity() {
        return true;
    }
}


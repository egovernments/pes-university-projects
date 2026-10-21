package org.pdi.validator.dashboard;

import org.pdi.exception.CustomException;
import org.pdi.web.models.GapSearchRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import static org.pdi.util.PdiConstants.CAMPAIGN_ID_REQUIRED;
import static org.pdi.util.PdiConstants.CAMPAIGN_ID_REQUIRED_MSG;
import static org.pdi.util.PdiConstants.GAP_SEARCH_CAMPAIGN_ID_REQUIRED_MSG;

@Component
public class DashboardValidator {

    public void validateStatsRequest(String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            throw new CustomException(CAMPAIGN_ID_REQUIRED, CAMPAIGN_ID_REQUIRED_MSG, HttpStatus.BAD_REQUEST);
        }
    }

    public void validateGapSearchRequest(GapSearchRequest request) {
        if (request == null || request.getGapSearchCriteria() == null
                || request.getGapSearchCriteria().getCampaignId() == null) {
            throw new CustomException(CAMPAIGN_ID_REQUIRED, GAP_SEARCH_CAMPAIGN_ID_REQUIRED_MSG, HttpStatus.BAD_REQUEST);
        }
    }
}

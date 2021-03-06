/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ref.compliance.submission;

import java.io.*;
import java.sql.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.commons.lang.*;
import org.dspace.app.util.*;
import org.dspace.authorize.*;
import org.dspace.content.*;
import org.dspace.core.*;
import org.dspace.ref.compliance.result.*;
import org.dspace.ref.compliance.service.*;
import org.dspace.submit.*;
import org.dspace.utils.*;

/**
 * Created by Philip Vissenaekens (philip at atmire dot com)
 * Date: 23/03/16
 * Time: 11:02
 */
public class REFComplianceStep extends AbstractProcessingStep {

    public static final int STATUS_BLOCKED = 2;

    private static final String blockSubmissionConfig = "ref.submission.block.on.rule.violation.";
    private static final String defaultConfig = "default";

    private static ComplianceCheckService complianceCheckService = new DSpace().getServiceManager()
            .getServiceByName("refComplianceCheckService", ComplianceCheckService.class);

    @Override
    public int doProcessing(Context context, HttpServletRequest request, HttpServletResponse response, SubmissionInfo subInfo) throws ServletException, IOException, SQLException, AuthorizeException {
        String buttonPressed = Util.getSubmitButton(request, NEXT_BUTTON);

        if(NEXT_BUTTON.equals(buttonPressed)) {

            Item item = subInfo.getSubmissionItem().getItem();

            String blockSubmissionOnViolation = org.dspace.core.ConfigurationManager.getProperty("item-compliance", blockSubmissionConfig + subInfo.getCollectionHandle());

            if(StringUtils.isBlank(blockSubmissionOnViolation)) {
                blockSubmissionOnViolation = org.dspace.core.ConfigurationManager.getProperty("item-compliance", blockSubmissionConfig + defaultConfig);
            }

            if (StringUtils.isNotBlank(blockSubmissionOnViolation) && Boolean.parseBoolean(blockSubmissionOnViolation)) {
                ComplianceResult result = complianceCheckService.checkCompliance(context, item);

                if (!result.isCompliant()) {
                    return STATUS_BLOCKED;
                }
            }
        }

        return STATUS_COMPLETE;
    }

    @Override
    public int getNumberOfPages(HttpServletRequest request, SubmissionInfo subInfo) throws ServletException {
        return 1;
    }
}

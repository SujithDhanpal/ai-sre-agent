package com.sre.agent.plugin.api;

import com.sre.agent.plugin.api.model.*;

import java.time.Instant;
import java.util.List;

public interface ErrorTrackingPlugin extends SrePlugin {

    // Issue Discovery
    List<ErrorGroup> getRecentErrorGroups(String service, Instant since);

    ErrorGroupDetail getErrorGroupDetail(String errorGroupId);

    // Impact Analysis
    List<ErrorGroup> getNewErrors(String service, Instant since);

    List<ErrorGroup> getRegressions(String service, Instant since);

    // Release Correlation
    ReleaseHealth getReleaseHealth(String service, String version);

    List<ReleaseHealth> getRecentReleases(String service, int limit);
}

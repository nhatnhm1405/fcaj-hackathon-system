package com.seal.hackathon.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Outcome of applying a leftover-grouping plan during SETUP. */
@Getter
@Builder
public class GroupingCommitResponse {

    private int teamsCreated;
    private int teamsGrown;
    private int peoplePlaced;
    /** Situations left for the coordinator to resolve manually (unchanged by commit). */
    private int unresolvedWarnings;
    private List<GroupingPreviewResponse.WarningView> warnings;
}

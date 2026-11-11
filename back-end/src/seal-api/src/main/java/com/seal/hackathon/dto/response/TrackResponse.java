package com.seal.hackathon.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackResponse {
    private Integer trackId;
    private Integer eventId;
    private String name;
    private String description;
    private Integer capacity;   // total slots (max teams) for this track, computed on SETUP entry
    private Integer teamCount;  // approved teams currently assigned to this track (slots used)
}

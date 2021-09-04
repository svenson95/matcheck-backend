package com.example.footballdata.models;

import lombok.Data;

@Data
public class MatchDetails {
    private String matchId;
    private String matchDay;
    private String date;
    private String time;
    private String fulltimeResult;
    private String halftimeResult;
    private String homeTeam;
    private String awayTeam;
}

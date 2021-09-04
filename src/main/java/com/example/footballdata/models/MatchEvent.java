package com.example.footballdata.models;

import lombok.Data;

@Data
public class MatchEvent {
    EventType type;
    String player;          // Goal & Substitution IN
    String assistPlayer;    // Goal & Substitution Out

    // Goal
    String minute;
    String newResult;

    // Substitution
    String team;
    String reason;

    // Card
    String cardType;
}

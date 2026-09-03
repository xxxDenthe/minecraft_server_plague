package dev.denthe.gmtools.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectatorToggleTest {

    @Test
    void координатыВозвратаБезЗапятой() {
        List<String> cmds = SpectatorToggle.commandsToLeaveSpectator(
            "survival", 1.5, 64.0, -2.5, 90.0f, -10.0f);

        assertEquals(List.of(
            "gamemode survival",
            "tp @s 1.500 64.000 -2.500 90.0 -10.0"), cmds);
    }

    @Test
    void безСохранённойПозицииТолькоРежим() {
        assertEquals(List.of("gamemode creative"),
            SpectatorToggle.commandsToLeaveSpectator("creative", Double.NaN, 0, 0, 0f, 0f));
    }
}

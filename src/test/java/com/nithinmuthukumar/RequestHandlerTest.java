package com.nithinmuthukumar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestHandlerTest {
    @Test
    void getSummonerTest() {
        System.out.println(RequestHandler.getSummoner("popoplolj"));

    }

    @Test
    void getMatchesTest() {
        System.out.println(RequestHandler.getMatches(200,"0L7bXwRYRDvSTdhU3fmDvi4KihWjE6VZtDUstj5NGtblyitQy8D9qtA3vfvcTEpGyO3or53vID13Kg").block().get(0));
    }

    @Test
    void getMatchDataTest() {
        System.out.println(RequestHandler.getMatchData("NA1_4150175966"));
    }
}
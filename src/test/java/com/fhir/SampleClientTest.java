package com.fhir;

import org.junit.Test;
//import com.fhir.;

import java.util.Map;

import static org.junit.Assert.assertTrue;

public class SampleClientTest {
    @Test
    public void loopTwoShouldHaveTheShortestAverage() {
        Map<Integer, Long> timerMap = new SampleClient().executeSearch();
        assertTrue(timerMap.get(2) < timerMap.get(1));
        assertTrue(timerMap.get(3) > timerMap.get(2));
    }
}

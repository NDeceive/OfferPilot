package com.zhimian.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleScoreAlignmentTest {
    private final ModuleScoreService service=new ModuleScoreService(null,null,null,null,null,null,null,null,null,null,null);
    @Test void defaultMatchUsesExactlyTheFiveDisplayedModules(){
        var scores=new LinkedHashMap<String,BigDecimal>();var weights=new LinkedHashMap<String,Double>();var targets=new LinkedHashMap<String,Integer>();
        for(int i=0;i<10;i++){scores.put("m"+i,BigDecimal.valueOf(i<5?37.5:100));if(i<5){weights.put("m"+i,.2);targets.put("m"+i,75);}}
        var result=service.calculateMatch(scores,weights,targets,List.of());
        assertEquals(50,result.overallMatch);assertEquals(weights.keySet(),result.moduleMatches.keySet());
    }
    @Test void absentSelectedScoreMustNotBecomeAZero(){
        assertThrows(com.zhimian.common.BizException.class,()->service.calculateMatch(Map.of(),Map.of("required",1.0),Map.of("required",75),List.of()));
    }
    @Test void zeroIsARealScoreAndTargetsCanBeExceeded(){
        var zero=service.calculateMatch(Map.of("m",BigDecimal.ZERO),Map.of("m",1.0),Map.of("m",75),List.of());assertEquals(0,zero.overallMatch);
        var above=service.calculateMatch(Map.of("m",BigDecimal.valueOf(100)),Map.of("m",1.0),Map.of("m",65),List.of());assertEquals(120,above.overallMatch);
    }
}

package com.zhimian.service;

import com.zhimian.common.BizException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectivePracticeTest {
    Map<String,Object> detail=Map.of("options",List.of(Map.of("key","A","text","一"),Map.of("key","B","text","二"),Map.of("key","C","text","三")));
    @Test void validatesSelectionsAndCanonicalizesMultiSelectWithoutAcceptingDuplicates() {
        assertEquals("A,C",PracticeService.objectiveAnswer("MULTIPLE_CHOICE","C, A",detail));
        assertEquals("A",PracticeService.objectiveAnswer("MULTIPLE_CHOICE","A",detail)); // Valid submission, scored incorrect against all correct keys.
        assertThrows(BizException.class,()->PracticeService.objectiveAnswer("CHOICE","A,C",detail));
        assertThrows(BizException.class,()->PracticeService.objectiveAnswer("MULTIPLE_CHOICE","A,A",detail));
        assertThrows(BizException.class,()->PracticeService.objectiveAnswer("CHOICE","D",detail));
        assertThrows(BizException.class,()->PracticeService.objectiveAnswer("MULTIPLE_CHOICE","A,",detail));
        var judgment=Map.<String,Object>of("options",List.of(Map.of("key","true"),Map.of("key","false")));
        assertEquals("false",PracticeService.objectiveAnswer("TRUE_FALSE","false",judgment));
    }
}

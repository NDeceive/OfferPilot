package com.zhimian.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TeachingDraftSelectionTest {
    private TeachingService.TaskInput input(String mode,List<Long> students){
        return new TeachingService.TaskInput(1L,"任务","",2L,List.of("问题"),900,2,1,3,false,
                LocalDateTime.now(),LocalDateTime.now().plusDays(7),students,List.of(),mode);
    }
    @Test void explicitStudentSelectionNeverFallsBackToEntireClass(){
        assertFalse(input("SELECTED",List.of()).isRecipientSelectionValid());
        assertFalse(input("SELECTED",null).isRecipientSelectionValid());
        assertTrue(input("SELECTED",List.of(7L)).isRecipientSelectionValid());
        assertTrue(input("ALL",List.of()).isRecipientSelectionValid());
        assertTrue(input(null,List.of()).isRecipientSelectionValid(),"Older clients retain explicit legacy behavior");
        assertFalse(input("unknown",List.of(7L)).isRecipientSelectionValid());
    }
}

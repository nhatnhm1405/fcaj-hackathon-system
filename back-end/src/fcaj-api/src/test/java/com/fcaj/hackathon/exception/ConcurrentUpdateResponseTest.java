package com.fcaj.hackathon.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConcurrentUpdateResponseTest {
    @RestController static class Writer {
        @GetMapping("/race") public void write() {throw new org.springframework.orm.ObjectOptimisticLockingFailureException("User",1);}
        @GetMapping("/commit") public void commit() {throw new org.springframework.transaction.TransactionSystemException("commit failed",new jakarta.persistence.RollbackException(new jakarta.persistence.OptimisticLockException()));}
    }
    @Test void commitWrapperReturnsTheSameSafe409() throws Exception {
        MockMvcBuilders.standaloneSetup(new Writer()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/commit")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("CONCURRENT_UPDATE"));
    }
    @Test void springTransactionConflictReturnsSafe409() throws Exception {
        MockMvcBuilders.standaloneSetup(new Writer()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/race")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("CONCURRENT_UPDATE"));
    }
}

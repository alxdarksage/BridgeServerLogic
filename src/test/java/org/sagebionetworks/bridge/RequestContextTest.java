package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import org.sagebionetworks.bridge.models.Metrics;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

public class RequestContextTest {

    private static final Set<String> SUBSTUDIES = ImmutableSet.of("testA", "testB");
    private static final Set<Roles> ROLES = ImmutableSet.of(Roles.DEVELOPER, Roles.WORKER);

    @Test
    public void builderIsNullSafe() { 
        // Verify that minimal construction of a context works and returns a valid, if 
        // empty and useless, request context. It's expected this will be augmented by other
        // code that executes.
        RequestContext nullContext = new RequestContext.Builder().withRequestId(null).withCallerStudyId(null)
                .withCallerSubstudies(null).withCallerRoles(null).build();
        
        assertNotNull(nullContext.getId());
        assertTrue(nullContext.getCallerSubstudies().isEmpty());
        assertTrue(nullContext.getCallerRoles().isEmpty());
        assertNull(nullContext.getCallerStudyId());
        assertNull(nullContext.getCallerStudyIdentifier());
        assertNotNull(nullContext.getMetrics());
        
        ObjectNode node = nullContext.getMetrics().getJson();
        assertTrue(node.has("request_id"));
        assertTrue(node.has("version"));
        assertTrue(node.has("start"));
    }
    
    @Test
    public void nullInstanceContainsNoValues() {
        // This is truly devoid of all values and represents no value (in production it's used to 
        // clear the ThreadLocal variable in a way that prevents making logs of null-pointer checks
        // in the code).
        assertNull(RequestContext.NULL_INSTANCE.getId());
        assertTrue(RequestContext.NULL_INSTANCE.getCallerSubstudies().isEmpty());
        assertTrue(RequestContext.NULL_INSTANCE.getCallerRoles().isEmpty());
        assertNull(RequestContext.NULL_INSTANCE.getCallerStudyId());
        assertNull(RequestContext.NULL_INSTANCE.getCallerStudyIdentifier());
        assertNull(RequestContext.NULL_INSTANCE.getMetrics());
    }

    @Test
    public void test() {
        // An existing metrics object
        Metrics metrics = new Metrics("requestId");
        
        RequestContext context = new RequestContext.Builder().withRequestId("requestId")
                .withCallerStudyId(TestConstants.TEST_STUDY).withCallerSubstudies(SUBSTUDIES)
                .withMetrics(metrics).withCallerRoles(ROLES).build();

        assertEquals("requestId", context.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, context.getCallerStudyId());
        assertEquals(TestConstants.TEST_STUDY, context.getCallerStudyIdentifier());
        assertEquals(SUBSTUDIES, context.getCallerSubstudies());
        assertEquals(ROLES, context.getCallerRoles());
        assertEquals(metrics, context.getMetrics());
    }
}

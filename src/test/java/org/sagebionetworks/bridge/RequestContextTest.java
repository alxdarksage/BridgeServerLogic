package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class RequestContextTest {

    private static final Set<String> SUBSTUDIES = ImmutableSet.of("testA", "testB");
    private static final Set<Roles> ROLES = ImmutableSet.of(Roles.DEVELOPER, Roles.WORKER);

    @Test
    public void nullObjectReturnsNullAndEmpty() { 
        RequestContext nullContext = new RequestContext.Builder().withRequestId(null).withCallerStudyId(null)
                .withCallerSubstudies(null).withCallerRoles(null).build();
        
        assertNotNull(nullContext.getRequestId());
        assertTrue(nullContext.getCallerSubstudies().isEmpty());
        assertTrue(nullContext.getCallerRoles().isEmpty());
        assertNull(nullContext.getCallerStudyId());
        assertNull(nullContext.getCallerStudyIdentifier());
        
        assertNull(RequestContext.NULL_INSTANCE.getRequestId());
        assertTrue(RequestContext.NULL_INSTANCE.getCallerSubstudies().isEmpty());
        assertTrue(RequestContext.NULL_INSTANCE.getCallerRoles().isEmpty());
        assertNull(RequestContext.NULL_INSTANCE.getCallerStudyId());
        assertNull(RequestContext.NULL_INSTANCE.getCallerStudyIdentifier());
    }

    @Test
    public void test() { 
        RequestContext context = new RequestContext.Builder().withRequestId("requestId")
                .withCallerStudyId(TestConstants.TEST_STUDY).withCallerSubstudies(SUBSTUDIES)
                .withCallerRoles(ROLES).build();

        assertEquals("requestId", context.getRequestId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, context.getCallerStudyId());
        assertEquals(TestConstants.TEST_STUDY, context.getCallerStudyIdentifier());
        assertEquals(SUBSTUDIES, context.getCallerSubstudies());
        assertEquals(ROLES, context.getCallerRoles());
    }
}

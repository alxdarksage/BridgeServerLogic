package org.sagebionetworks.bridge.services;

import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ActivityEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ActivityEventServiceTest {

    private ActivityEventService activityEventService;

    private ActivityEventDao activityEventDao;
    
    @Before
    public void before() {
        activityEventDao = mock(ActivityEventDao.class);

        activityEventService = new ActivityEventService();
        activityEventService.setActivityEventDao(activityEventDao);
    }

    @Test
    public void canPublishCustomEvent() throws Exception {
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("eventKey1", "eventKey2"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now();
        activityEventService.publishCustomEvent(study, "healthCode", "eventKey1", timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals("custom:eventKey1", activityEvent.getEventId());
        assertEquals("healthCode", activityEvent.getHealthCode());
        assertEquals(timestamp.getMillis(), activityEvent.getTimestamp().longValue());
    }

    @Test
    public void canPublishCustomEventFromAutomaticEvents() {
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        study.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "myEvent:P3D"));

        ArgumentCaptor<ActivityEvent> activityEventArgumentCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        when(activityEventDao.publishEvent(activityEventArgumentCaptor.capture())).thenReturn(true);

        DateTime timestamp = DateTime.now().plusDays(3);
        activityEventService.publishCustomEvent(study, "healthCode", "3-days-after-enrollment",
                timestamp);

        ActivityEvent activityEvent = activityEventArgumentCaptor.getValue();

        assertEquals("custom:3-days-after-enrollment", activityEvent.getEventId());
        assertEquals("healthCode", activityEvent.getHealthCode());
        assertEquals(timestamp.getMillis(), activityEvent.getTimestamp().longValue());
    }

    @Test
    public void cannotPublishUnknownCustomEvent() throws Exception {
        Study study = Study.create();
        try {
            activityEventService.publishCustomEvent(study, "healthCode", "eventKey5",
                    DateTime.now());
            fail("expected exception");
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), endsWith("eventKey5"));
        }
    }
    
    @Test
    public void canPublishEvent() {
        ActivityEvent event = new Builder().withHealthCode("BBB")
            .withObjectType(ActivityEventObjectType.ENROLLMENT).withTimestamp(DateTime.now()).build();

        activityEventService.publishActivityEvent(event);
        
        verify(activityEventDao).publishEvent(eq(event));
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canGetActivityEventMap() {
        DateTime now = DateTime.now();
        
        Map<String,DateTime> map = Maps.newHashMap();
        map.put("enrollment", now);
        when(activityEventDao.getActivityEventMap("BBB")).thenReturn(map);

        Map<String, DateTime> results = activityEventService.getActivityEventMap("BBB");
        assertEquals(now, results.get("enrollment"));
        assertEquals(1, results.size());
        
        verify(activityEventDao).getActivityEventMap("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canDeleteActivityEvents() {
        activityEventService.deleteActivityEvents("BBB");
        
        verify(activityEventDao).deleteActivityEvents("BBB");
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void badPublicDoesntCallDao() {
        try {
            activityEventService.publishActivityEvent((ActivityEvent) null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badGetDoesntCallDao() {
        try {
            activityEventService.getActivityEventMap(null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void badDeleteDoesntCallDao() {
        try {
            activityEventService.deleteActivityEvents(null);
            fail("Exception should have been thrown");
        } catch(NullPointerException e) {}
        verifyNoMoreInteractions(activityEventDao);
    }

    @Test
    public void canPublishEnrollmentEvent() {
        DateTime now = DateTime.now();
        
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(now.minusDays(10).getMillis())
                .withSignedOn(now.getMillis()).build();

        activityEventService.publishEnrollmentEvent(Study.create(),"AAA-BBB-CCC", signature);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals("enrollment", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("AAA-BBB-CCC", argument.getValue().getHealthCode());
    }

    @Test
    public void canPublishEnrollmentEventWithAutomaticCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D") // defaults to enrollment
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());

        // Create consent signature
        DateTime enrollment = DateTime.parse("2018-04-04T16:00-0700");
        ConsentSignature signature = new ConsentSignature.Builder()
                .withBirthdate("1980-01-01")
                .withName("A Name")
                .withConsentCreatedOn(enrollment.minusDays(10).getMillis())
                .withSignedOn(enrollment.getMillis()).build();

        when(activityEventDao.publishEvent(any())).thenReturn(true);
        
        // Execute
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", signature);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals("enrollment", publishedEventList.get(0).getEventId());
        assertEquals(enrollment.getMillis(), publishedEventList.get(0).getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(0).getHealthCode());

        assertEquals("custom:3-days-after", publishedEventList.get(1).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"), publishedEventList.get(1)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(1).getHealthCode());

        assertEquals("custom:1-week-after", publishedEventList.get(2).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"), publishedEventList.get(2)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(2).getHealthCode());

        assertEquals("custom:13-weeks-after", publishedEventList.get(3).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-07-04T16:00-0700"), publishedEventList.get(3)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(3).getHealthCode());
    }
    
    @Test
    public void whenNoEnrollmentEventPublishNoCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that are implicitly and explicitly related to 
        // enrollment, and some that are not applicable that should be ignored.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", new ConsentSignature.Builder().build());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenActivitiesRetrievedEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishActivitiesRetrieved(study,"AAA-BBB-CCC", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void whenEnrollmentEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "enrollment:P3D")
                .put("1-week-after", "enrollment:P1W")
                .put("13-weeks-after", "enrollment:P13W")
                .put("5-years-after", "not_enrollment:P5Y")
                .put("10-years-after", "not_entrollment:P10Y").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishEnrollmentEvent(study,"AAA-BBB-CCC", new ConsentSignature.Builder().build());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }

    @Test
    public void whenCustomEventFailsPublishNoAutomaticEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        // Note that these automatic events include events that are triggered by enrollment, 
        // and some that are not, that should be ignored
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        
        when(activityEventDao.publishEvent(any())).thenReturn(false);
        
        activityEventService.publishCustomEvent(study,"AAA-BBB-CCC", "myEvent", DateTime.now());
        
        // Only happens once, none of the other custom events are published.
        verify(activityEventDao, times(1)).publishEvent(any());
    }
    
    @Test
    public void canPublishActivitiesRetrievedEventWithAutomaticCustomEvents() {
        // Configure study with automatic custom events
        Study study = Study.create();
        // Note that these events include events that should be triggered for enrollment, 
        // not activities retrieved. These are ignore.
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "activities_retrieved:P3D")
                .put("1-week-after", "activities_retrieved:P1W")
                .put("13-weeks-after", "activities_retrieved:P13W")
                .put("5-years-after", "enrollment:P5Y")
                .put("10-years-after", "enrollment:P10Y").build());

        // Create consent signature
        DateTime retrieved = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishActivitiesRetrieved(study, "AAA-BBB-CCC", retrieved);

        // Verify published events (4)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(4)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();

        assertEquals("activities_retrieved", publishedEventList.get(0).getEventId());
        assertEquals(retrieved.getMillis(), publishedEventList.get(0).getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(0).getHealthCode());

        assertEquals("custom:3-days-after", publishedEventList.get(1).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"), publishedEventList.get(1)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(1).getHealthCode());

        assertEquals("custom:1-week-after", publishedEventList.get(2).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"), publishedEventList.get(2)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(2).getHealthCode());

        assertEquals("custom:13-weeks-after", publishedEventList.get(3).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-07-04T16:00-0700"), publishedEventList.get(3)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(3).getHealthCode());
    }
    
    @Test
    public void canPublishCustomEventWithAutomaticCustomEvents() {
        // This also verifies the correct parsing of the custom event key, which contains a colon.
        Study study = Study.create();
        study.setActivityEventKeys(ImmutableSet.of("myEvent"));
        study.setAutomaticCustomEvents(ImmutableMap.<String, String>builder()
                .put("3-days-after", "myEvent:P3D")
                .put("1-week-after", "myEvent:P1W").build());
        DateTime timestamp = DateTime.parse("2018-04-04T16:00-0700");
        
        when(activityEventDao.publishEvent(any())).thenReturn(true);

        // Execute
        activityEventService.publishCustomEvent(study, "AAA-BBB-CCC", "myEvent", timestamp);

        // Verify published events (3)
        ArgumentCaptor<ActivityEvent> publishedEventCaptor = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao, times(3)).publishEvent(publishedEventCaptor.capture());

        List<ActivityEvent> publishedEventList = publishedEventCaptor.getAllValues();
        
        assertEquals("custom:myEvent", publishedEventList.get(0).getEventId());
        assertEquals(timestamp.getMillis(), publishedEventList.get(0).getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(0).getHealthCode());

        assertEquals("custom:3-days-after", publishedEventList.get(1).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-07T16:00-0700"), publishedEventList.get(1)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(1).getHealthCode());

        assertEquals("custom:1-week-after", publishedEventList.get(2).getEventId());
        assertEquals(DateUtils.convertToMillisFromEpoch("2018-04-11T16:00-0700"), publishedEventList.get(2)
                .getTimestamp().longValue());
        assertEquals("AAA-BBB-CCC", publishedEventList.get(2).getHealthCode());
    }

    @Test
    public void canPublishSurveyAnswer() {
        DateTime now = DateTime.now();
        
        SurveyAnswer answer = new SurveyAnswer();
        answer.setAnsweredOn(now.getMillis());
        answer.setQuestionGuid("BBB-CCC-DDD");
        answer.setAnswers(Lists.newArrayList("belgium"));

        activityEventService.publishQuestionAnsweredEvent("healthCode", answer);
        
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());
        
        assertEquals("question:BBB-CCC-DDD:answered", argument.getValue().getEventId());
        assertEquals(new Long(now.getMillis()), argument.getValue().getTimestamp());
        assertEquals("healthCode", argument.getValue().getHealthCode());
    }
    
    @Test
    public void doesNotPublishActivityFinishedEventForOldActivity() {
        ScheduledActivity activity = ScheduledActivity.create();
        activity.setGuid("AAA");

        activityEventService.publishActivityFinishedEvent(activity);
        verifyNoMoreInteractions(activityEventDao);
    }
    
    @Test
    public void canPublishActivityFinishedEvents() {
        long finishedOn = DateTime.now().getMillis();
        
        ScheduledActivity schActivity = ScheduledActivity.create();
        schActivity.setGuid("AAA:"+DateTime.now().toLocalDateTime());
        schActivity.setActivity(TestUtils.getActivity1());
        schActivity.setLocalExpiresOn(LocalDateTime.now().plusDays(1));
        schActivity.setStartedOn(DateTime.now().getMillis());
        schActivity.setFinishedOn(finishedOn);
        schActivity.setHealthCode("BBB");


        activityEventService.publishActivityFinishedEvent(schActivity);
        ArgumentCaptor<ActivityEvent> argument = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(activityEventDao).publishEvent(argument.capture());

        ActivityEvent event = argument.getValue();
        assertEquals("BBB", event.getHealthCode());
        assertEquals("activity:AAA:finished", event.getEventId());
        assertEquals(finishedOn, event.getTimestamp().longValue());
    }
}

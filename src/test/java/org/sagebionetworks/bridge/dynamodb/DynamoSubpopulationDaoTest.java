package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

public class DynamoSubpopulationDaoTest extends Mockito {

    private static final String GUID = "oneGuid";
    private static final String CRITERIA_KEY = "subpopulation:" + GUID;
    
    @Mock
    DynamoDBMapper mockMapper;
    
    @Mock
    CriteriaDao mockCriteriaDao;
    
    @Captor
    ArgumentCaptor<Subpopulation> subpopCaptor;
    
    @Captor
    ArgumentCaptor<Criteria> criteriaCaptor;
    
    @InjectMocks
    @Spy
    DynamoSubpopulationDao dao;
    
    @BeforeMethod
    public void beforeMethod() { 
        MockitoAnnotations.initMocks(this);
        when(dao.generateGuid()).thenReturn(GUID);
    }
    
    @Test
    public void createSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier(TEST_STUDY_IDENTIFIER);
        
        dao.createSubpopulation(subpop);
        
        verify(mockMapper).save(subpopCaptor.capture());
        Subpopulation savedSubpop = subpopCaptor.getValue();
        assertNotNull(savedSubpop.getGuidString());
        assertFalse(savedSubpop.isDeleted());
        assertNull(savedSubpop.getVersion());
        assertFalse(savedSubpop.isDefaultGroup());
        assertEquals(savedSubpop.getPublishedConsentCreatedOn(), 0L);
        
        verify(mockCriteriaDao).createOrUpdateCriteria(criteriaCaptor.capture());
        Criteria savedCriteria = criteriaCaptor.getValue();
        assertEquals(savedCriteria.getKey(), CRITERIA_KEY);
    }
    
    @Test
    public void createDefaultSubpopulation() {
        
    }
    
    @Test
    public void getSubpopulations() {
        
    }
    
    @Test
    public void getSubpopulation() {
        
    }
    
    @Test
    public void updateSubpopulation() {
        
    }

    @Test
    public void deleteSubpopulation() {
        
    }
    
    @Test
    public void deleteSubpopulationPermanently() {
        
    }
}

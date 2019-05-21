package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DynamoCriteriaDaoTest extends Mockito {

    @Mock
    DynamoDBMapper mockMapper;
    
    @InjectMocks
    DynamoCriteriaDao dao;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void createOrUpdateCriteria() {
    }
    
    @Test
    public void getCriteria() {
    }

    @Test
    public void deleteCriteria() {
    }    
    
}

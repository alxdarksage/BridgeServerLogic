package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.Headers.SERVER_SIDE_ENCRYPTION;
import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.net.URL;
import java.util.Date;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dao.UploadDedupeDao;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.sagebionetworks.bridge.models.upload.UploadSession;
import org.sagebionetworks.bridge.models.upload.UploadView;
import org.sagebionetworks.bridge.validators.UploadValidator;

public class UploadServiceTest extends Mockito {
    
    private static final String UPLOAD_BUCKET_NAME = "upload-bucket";

    final static DateTime TIMESTAMP = DateTime.now();
    
    final static String ORIGINAL_UPLOAD_ID = "anOriginalUploadId";
    
    final static String NEW_UPLOAD_ID = "aNewUploadId";
    
    final static String RECORD_ID = "aRecordId";
    
    final static StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE).build();
/*
    @Mock
    String uploadBucket;
    
    @Mock
    UploadValidationService uploadValidationService;
*/
    @Mock
    HealthDataService mockHealthDataService;
    
    @Mock
    AmazonS3 mockS3UploadClient;
    
    @Mock
    UploadSessionCredentialsService mockUploadCredentailsService;

    @Mock
    UploadDedupeDao mockUploadDedupeDao;
    
    @Mock
    UploadDao mockUploadDao;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Captor
    ArgumentCaptor<GeneratePresignedUrlRequest> requestCaptor;
    
    @InjectMocks
    UploadService service;
    
    @BeforeMethod
    public void beforeMethod() {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        MockitoAnnotations.initMocks(this);
        service.setValidator(new UploadValidator());
        service.setS3UploadClient(mockS3UploadClient);
        
        when(mockConfig.getProperty(UploadService.CONFIG_KEY_UPLOAD_BUCKET)).thenReturn(UPLOAD_BUCKET_NAME);
        when(mockConfig.getList(UploadService.CONFIG_KEY_UPLOAD_DUPE_STUDY_WHITELIST))
                .thenReturn(ImmutableList.of("whitelisted-study"));
        service.setConfig(mockConfig);
    }
    
    @AfterMethod
    public void afterMethod() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    UploadRequest constructUploadRequest() throws Exception {
        String json = TestUtils.createJson("{"+
                "'name':'oneUpload',"+
                "'contentLength':1048,"+ 
                "'contentMd5': 'md5-value',"+
                "'contentType':'application/binary'}");
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        return UploadRequest.fromJson(node);
    }

    @Test
    public void createUpload() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(NEW_UPLOAD_ID);
        
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockUploadDao.createUpload(uploadRequest, TEST_STUDY, HEALTH_CODE, null)).thenReturn(upload);
        when(mockUploadCredentailsService.getSessionCredentials())
                .thenReturn(new BasicSessionCredentials(null, null, null));
        when(mockS3UploadClient.generatePresignedUrl(any())).thenReturn(new URL("https://ws.com/some-link"));
        
        UploadSession session = service.createUpload(TEST_STUDY, PARTICIPANT, uploadRequest);
        assertEquals(session.getId(), NEW_UPLOAD_ID);
        assertEquals(session.getUrl(), "https://ws.com/some-link");
        assertEquals(session.getExpires(), TIMESTAMP.getMillis() + UploadService.EXPIRATION);
        
        verify(mockS3UploadClient).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET_NAME);
        assertEquals(request.getContentMd5(), "md5-value");
        assertEquals(request.getContentType(), "application/binary");
        assertEquals(request.getExpiration(), new Date(TIMESTAMP.getMillis() + UploadService.EXPIRATION));
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getRequestParameters().get(SERVER_SIDE_ENCRYPTION), AES_256_SERVER_SIDE_ENCRYPTION);
    }
    
    @Test
    public void createUploadDuplicate() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        
        when(mockUploadDedupeDao.getDuplicate(eq(HEALTH_CODE), eq("md5-value"), any())).thenReturn(ORIGINAL_UPLOAD_ID);
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockUploadDao.createUpload(uploadRequest, TEST_STUDY, HEALTH_CODE, ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockUploadCredentailsService.getSessionCredentials())
                .thenReturn(new BasicSessionCredentials(null, null, null));
        when(mockS3UploadClient.generatePresignedUrl(any())).thenReturn(new URL("https://ws.com/some-link"));
        
        UploadSession session = service.createUpload(TEST_STUDY, PARTICIPANT, uploadRequest);
        assertEquals(session.getId(), ORIGINAL_UPLOAD_ID);
        assertEquals(session.getUrl(), "https://ws.com/some-link");
        assertEquals(session.getExpires(), TIMESTAMP.getMillis() + UploadService.EXPIRATION);
        
        verify(mockS3UploadClient).generatePresignedUrl(requestCaptor.capture());
        GeneratePresignedUrlRequest request = requestCaptor.getValue();
        assertEquals(request.getBucketName(), UPLOAD_BUCKET_NAME);
        assertEquals(request.getContentMd5(), "md5-value");
        assertEquals(request.getContentType(), "application/binary");
        assertEquals(request.getExpiration(), new Date(TIMESTAMP.getMillis() + UploadService.EXPIRATION));
        assertEquals(request.getMethod(), HttpMethod.PUT);
        assertEquals(request.getRequestParameters().get(SERVER_SIDE_ENCRYPTION), AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test
    public void getUpload() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        
        Upload result = service.getUpload(ORIGINAL_UPLOAD_ID);
        assertSame(result, upload);
    }
    
    @Test
    public void getUploadView() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        DynamoUpload2 upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        upload.setRecordId(RECORD_ID);
        
        HealthDataRecord record = new DynamoHealthDataRecord();
        
        when(mockUploadDao.getUpload(ORIGINAL_UPLOAD_ID)).thenReturn(upload);
        when(mockHealthDataService.getRecordById(RECORD_ID)).thenReturn(record);
        
        UploadView result = service.getUploadView(ORIGINAL_UPLOAD_ID);
        assertSame(result.getUpload(), upload);
        assertSame(result.getHealthData(), record);
    }

    @Test
    public void getUploads() throws Exception {
        UploadRequest uploadRequest = constructUploadRequest();
        Upload upload = new DynamoUpload2(uploadRequest, HEALTH_CODE);
        upload.setUploadId(ORIGINAL_UPLOAD_ID);
        
        DateTime startTime = TIMESTAMP.minusDays(7);
        DateTime endTime = TIMESTAMP;
        ForwardCursorPagedResourceList<Upload> uploads = new ForwardCursorPagedResourceList<>(ImmutableList.of(upload),
                null);
        when(mockUploadDao.getUploads(HEALTH_CODE, startTime, endTime, 40, "offsetKey")).thenReturn(uploads);
            
        ForwardCursorPagedResourceList<UploadView> result = service.getUploads(HEALTH_CODE, startTime, endTime, 40,
                "offsetKey");
        assertEquals(result.getItems().get(0).getUpload(), upload);
    }
    
    @Test
    public void getStudyUploads() {
    }
    
    @Test
    public void getUploadValidationStatus() {
    }

    @Test
    public void pollUploadValidationStatusUntilComplete() {
    }

    @Test
    public void uploadComplete() {
    }
    
    @Test
    public void deleteUploadsForHealthCode() {
    }
}

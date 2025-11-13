/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.bulkimport.service;

import com.google.common.io.ByteSource;
import jakarta.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import org.apache.commons.io.IOUtils;
import org.apache.fineract.infrastructure.bulkimport.data.BulkImportEvent;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.infrastructure.bulkimport.data.ImportData;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocument;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocumentRepository;
import org.apache.fineract.infrastructure.bulkimport.importhandler.ImportHandlerUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepositoryFactory;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.data.FileData;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformService;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class BulkImportWorkbookServiceImpl implements BulkImportWorkbookService {

    private static final Logger LOG = LoggerFactory.getLogger(BulkImportWorkbookServiceImpl.class);
    private final ApplicationContext applicationContext;
    private final PlatformSecurityContext securityContext;
    private final DocumentWritePlatformService documentWritePlatformService;
    private final DocumentRepository documentRepository;
    private final ImportDocumentRepository importDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ContentRepositoryFactory contentRepositoryFactory;

    @Autowired
    public BulkImportWorkbookServiceImpl(final ApplicationContext applicationContext, final PlatformSecurityContext securityContext,
            final DocumentWritePlatformService documentWritePlatformService, final DocumentRepository documentRepository,
            final ImportDocumentRepository importDocumentRepository, final JdbcTemplate jdbcTemplate,
            final ContentRepositoryFactory contentRepositoryFactory) {
        this.applicationContext = applicationContext;
        this.securityContext = securityContext;
        this.documentWritePlatformService = documentWritePlatformService;
        this.documentRepository = documentRepository;
        this.importDocumentRepository = importDocumentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.contentRepositoryFactory = contentRepositoryFactory;
    }

    @Override
    public Long importWorkbook(String entity, InputStream inputStream, FormDataContentDisposition fileDetail, final String locale,
            final String dateFormat) {
        try {
            if (entity != null && inputStream != null && fileDetail != null && locale != null && dateFormat != null) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(inputStream, baos);
                final byte[] bytes = baos.toByteArray();
                InputStream clonedInputStream = new ByteArrayInputStream(bytes);
                final BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(bytes));
                final Tika tika = new Tika();
                final TikaInputStream tikaInputStream = TikaInputStream.get(bis);
                final String fileType = tika.detect(tikaInputStream);
                if (!fileType.contains("msoffice") && !fileType.contains("application/vnd.ms-excel")) {
                    // We had a problem where we tried to upload the downloaded
                    // file from the import options, it was somehow changed the
                    // extension we use this fix.
                    throw new GeneralPlatformDomainRuleException("error.msg.invalid.file.extension",
                            "Uploaded file extension is not recognized.");

                }
                Workbook workbook = new HSSFWorkbook(clonedInputStream);
                GlobalEntityType entityType = null;
                int primaryColumn = 0;
                if (entity.trim().equalsIgnoreCase(GlobalEntityType.CLIENTS_PERSON.toString())) {
                    entityType = GlobalEntityType.CLIENTS_PERSON;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.CLIENTS_ENTITY.toString())) {
                    entityType = GlobalEntityType.CLIENTS_ENTITY;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.CENTERS.toString())) {
                    entityType = GlobalEntityType.CENTERS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.GROUPS.toString())) {
                    entityType = GlobalEntityType.GROUPS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.LOANS.toString())) {
                    entityType = GlobalEntityType.LOANS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.LOAN_TRANSACTIONS.toString())) {
                    entityType = GlobalEntityType.LOAN_TRANSACTIONS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.GUARANTORS.toString())) {
                    entityType = GlobalEntityType.GUARANTORS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.OFFICES.toString())) {
                    entityType = GlobalEntityType.OFFICES;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.CHART_OF_ACCOUNTS.toString())) {
                    entityType = GlobalEntityType.CHART_OF_ACCOUNTS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.GL_JOURNAL_ENTRIES.toString())) {
                    entityType = GlobalEntityType.GL_JOURNAL_ENTRIES;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.STAFF.toString())) {
                    entityType = GlobalEntityType.STAFF;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.SHARE_ACCOUNTS.toString())) {
                    entityType = GlobalEntityType.SHARE_ACCOUNTS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.SAVINGS_ACCOUNT.toString())) {
                    entityType = GlobalEntityType.SAVINGS_ACCOUNT;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.SAVINGS_TRANSACTIONS.toString())) {
                    entityType = GlobalEntityType.SAVINGS_TRANSACTIONS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.RECURRING_DEPOSIT_ACCOUNTS.toString())) {
                    entityType = GlobalEntityType.RECURRING_DEPOSIT_ACCOUNTS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.RECURRING_DEPOSIT_ACCOUNTS_TRANSACTIONS.toString())) {
                    entityType = GlobalEntityType.RECURRING_DEPOSIT_ACCOUNTS_TRANSACTIONS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.FIXED_DEPOSIT_ACCOUNTS.toString())) {
                    entityType = GlobalEntityType.FIXED_DEPOSIT_ACCOUNTS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.FIXED_DEPOSIT_TRANSACTIONS.toString())) {
                    entityType = GlobalEntityType.FIXED_DEPOSIT_TRANSACTIONS;
                    primaryColumn = 0;
                } else if (entity.trim().equalsIgnoreCase(GlobalEntityType.USERS.toString())) {
                    entityType = GlobalEntityType.USERS;
                    primaryColumn = 0;
                } else {
                    workbook.close();
                    throw new GeneralPlatformDomainRuleException("error.msg.unable.to.find.resource", "Unable to find requested resource");

                }
                return publishEvent(primaryColumn, fileDetail, bis, entityType, workbook, locale, dateFormat);
            }
            throw new GeneralPlatformDomainRuleException("error.msg.null", "One or more of the given parameters not found");
        } catch (IOException e) {
            LOG.error("Problem occurred in importWorkbook function", e);
            throw new GeneralPlatformDomainRuleException("error.msg.io.exception",
                    "IO exception occured with " + fileDetail.getFileName() + " " + e.getMessage(), e);

        }
    }

    private Long publishEvent(final Integer primaryColumn, final FormDataContentDisposition fileDetail,
            final InputStream clonedInputStreamWorkbook, final GlobalEntityType entityType, final Workbook workbook, final String locale,
            final String dateFormat) {

        final String fileName = fileDetail.getFileName();

        final Long documentId = this.documentWritePlatformService.createInternalDocument(
                DocumentWritePlatformServiceJpaRepositoryImpl.DocumentManagementEntity.IMPORT.name(),
                this.securityContext.authenticatedUser().getId(), null, clonedInputStreamWorkbook,
                URLConnection.guessContentTypeFromName(fileName), fileName, null, fileName);
        final Document document = this.documentRepository.findById(documentId).orElse(null);

        final ImportDocument importDocument = ImportDocument.instance(document, DateUtils.getLocalDateTimeOfTenant(), entityType.getValue(),
                this.securityContext.authenticatedUser(), ImportHandlerUtils.getNumberOfRows(workbook.getSheetAt(0), primaryColumn));
        this.importDocumentRepository.saveAndFlush(importDocument);
        BulkImportEvent event = BulkImportEvent.instance(this, workbook, importDocument.getId(), locale, dateFormat,
                ThreadLocalContextUtil.getContext());
        applicationContext.publishEvent(event);
        return importDocument.getId();
    }

    @Override
    public Collection<ImportData> getImports(GlobalEntityType type) {
        this.securityContext.authenticatedUser();

        final ImportMapper rm = new ImportMapper();
        final String sql = "select " + rm.schema() + " order by i.id desc";

        return this.jdbcTemplate.query(sql, rm, new Object[] { type.getValue() }); // NOSONAR
    }

    private static final class ImportMapper implements RowMapper<ImportData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();
            sql.append("i.id as id, i.document_id as documentId, d.name as name, i.import_time as importTime, i.end_time as endTime, ")
                    .append("i.completed as completed, i.total_records as totalRecords, i.success_count as successCount, ")
                    .append("i.failure_count as failureCount, i.createdby_id as createdBy ")
                    .append("from m_import_document i inner join m_document d on i.document_id=d.id ").append("where i.entity_type= ? ");
            return sql.toString();
        }

        @Override
        public ImportData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

            final Long id = rs.getLong("id");
            final Long documentId = rs.getLong("documentId");
            final String name = rs.getString("name");
            final LocalDate importTime = JdbcSupport.getLocalDate(rs, "importTime");
            final LocalDate endTime = JdbcSupport.getLocalDate(rs, "endTime");
            final Boolean completed = rs.getBoolean("completed");
            final Integer totalRecords = JdbcSupport.getInteger(rs, "totalRecords");
            final Integer successCount = JdbcSupport.getInteger(rs, "successCount");
            final Integer failureCount = JdbcSupport.getInteger(rs, "failureCount");
            final Long createdBy = rs.getLong("createdBy");

            return ImportData.instance(id, documentId, importTime, endTime, completed, name, createdBy, totalRecords, successCount,
                    failureCount);
        }
    }

    @Override
    public DocumentData getOutputTemplateLocation(String importDocumentId) {
        this.securityContext.authenticatedUser();
        final ImportTemplateLocationMapper importTemplateLocationMapper = new ImportTemplateLocationMapper();
        final String sql = "select " + importTemplateLocationMapper.schema();

        return this.jdbcTemplate.queryForObject(sql, importTemplateLocationMapper, new Object[] { Long.parseLong(importDocumentId) }); // NOSONAR
    }

    @Override
    public Response getOutputTemplate(String importDocumentId) {
        this.securityContext.authenticatedUser();
        final ImportTemplateLocationMapper importTemplateLocationMapper = new ImportTemplateLocationMapper();
        final String sql = "select " + importTemplateLocationMapper.schema();
        DocumentData documentData;
        try {
            documentData = this.jdbcTemplate.queryForObject(sql, importTemplateLocationMapper,
                    new Object[] { Long.parseLong(importDocumentId) }); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            LOG.error("Import document not found for ID: {}", importDocumentId);
            throw new ResourceNotFoundException("error.msg.import.document.not.found", "Import document not found for ID: {0}",
                    new Object[] { importDocumentId, e });
        }
        return buildResponse(documentData);
    }

    private Response buildResponse(DocumentData documentData) {
        validateDocumentData(documentData);

        String fileName = prepareFileName(documentData);
        String fileLocation = validateAndGetFileLocation(documentData, fileName);

        LOG.info("Building response for bulk import template: fileName={}, fileLocation={}", fileName, fileLocation);

        StorageType storageType = determineStorageType(documentData);

        if (storageType == StorageType.FILE_SYSTEM) {
            return buildFileSystemResponse(fileName, fileLocation);
        }

        return buildStorageResponse(storageType, documentData, fileName, fileLocation);
    }

    private void validateDocumentData(DocumentData documentData) {
        if (documentData == null) {
            LOG.error("DocumentData is null, cannot build response");
            throw new ResourceNotFoundException("error.msg.document.data.missing", "Document data is missing", new Object[0]);
        }
    }

    private String prepareFileName(DocumentData documentData) {
        String fileName = documentData.getFileName();
        if (fileName == null || fileName.isBlank()) {
            LOG.error("Document file name is null or blank");
            throw new ResourceNotFoundException("error.msg.document.filename.missing", "Document file name is missing", new Object[0]);
        }
        return "Output" + fileName;
    }

    private String validateAndGetFileLocation(DocumentData documentData, String fileName) {
        String fileLocation = documentData.getLocation();
        if (fileLocation == null || fileLocation.isBlank()) {
            LOG.error("Document file location is null or blank for fileName: {}", fileName);
            throw new ResourceNotFoundException("error.msg.document.location.missing", "Document file location is missing",
                    new Object[] { fileName });
        }
        return fileLocation;
    }

    private StorageType determineStorageType(DocumentData documentData) {
        final Integer storageTypeValue = documentData.getStorageType();
        if (storageTypeValue != null) {
            StorageType storageType = documentData.storageType();
            LOG.debug("Using storage type from document: {}", storageType);
            return storageType;
        }
        // If storage type is null (legacy documents), get from default repository
        StorageType storageType = this.contentRepositoryFactory.getRepository().getStorageType();
        LOG.debug("Storage type not specified in document, using default repository type: {}", storageType);
        return storageType;
    }

    private Response buildFileSystemResponse(String fileName, String fileLocation) {
        LOG.info("Using filesystem storage path for template download: {}", fileLocation);
        File file = new File(fileLocation);
        if (!file.exists()) {
            LOG.error("Template file not found at filesystem location: {}", fileLocation);
            throw new ResourceNotFoundException("error.msg.document.file.not.found", "Document file not found at location: {0}",
                    new Object[] { fileLocation });
        }
        long fileSize = file.length();
        LOG.info("Successfully located template file: fileName={}, fileSize={} bytes, path={}", fileName, fileSize, fileLocation);
        final Response.ResponseBuilder response = Response.ok(file);
        response.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.header("Content-Type", "application/vnd.ms-excel");
        LOG.debug("Built response for filesystem template: fileName={}, fileSize={}", fileName, fileSize);
        return response.build();
    }

    private Response buildStorageResponse(StorageType storageType, DocumentData documentData, String fileName, String fileLocation) {
        LOG.info("Using {} storage abstraction layer for template download: {}", storageType, fileLocation);
        try {
            final ContentRepository contentRepository = this.contentRepositoryFactory.getRepository(storageType);
            final FileData fileData = contentRepository.fetchFile(documentData);
            LOG.debug("Fetched file data from {} repository: fileName={}", storageType, fileName);
            return buildResponseFromFileData(storageType, fileData, fileName, fileLocation);
        } catch (Exception e) {
            LOG.error("Failed to fetch document file from {} repository: fileLocation={}", storageType, fileLocation, e);
            throw new ResourceNotFoundException("error.msg.document.file.not.found",
                    "Document file not found at location: {0}, exception: {1}", new Object[] { fileLocation, e.getMessage(), e });
        }
    }

    private Response buildResponseFromFileData(StorageType storageType, FileData fileData, String fileName, String fileLocation) {
        try {
            ByteSource byteSource = fileData.getByteSource();
            InputStream is = byteSource.openBufferedStream();
            Response.ResponseBuilder response = Response.ok(is);
            response.header("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

            Long contentLength = setContentLengthIfKnown(byteSource, response);

            String contentType = fileData.contentType() != null ? fileData.contentType() : "application/vnd.ms-excel";
            response.header("Content-Type", contentType);
            LOG.info("Successfully built response for {} storage template: fileName={}, contentType={}, contentLength={}", storageType,
                    fileName, contentType, contentLength != null ? contentLength + " bytes" : "unknown");
            return response.build();
        } catch (IOException e) {
            LOG.error("Failed to open file stream for document from {} storage: fileLocation={}", storageType, fileLocation, e);
            throw new ResourceNotFoundException("error.msg.document.file.not.found",
                    "Document file not found at location: {0}, exception: {1}", new Object[] { fileLocation, e.getMessage(), e });
        }
    }

    private Long setContentLengthIfKnown(ByteSource byteSource, Response.ResponseBuilder response) {
        try {
            var sizeOptional = byteSource.sizeIfKnown();
            if (sizeOptional.isPresent()) {
                Long contentLength = sizeOptional.get();
                response.header("Content-Length", contentLength);
                LOG.debug("Set Content-Length header from sizeIfKnown(): {} bytes", contentLength);
                return contentLength;
            }
            LOG.debug("File size not known, using chunked transfer encoding");
            return null;
        } catch (Exception e) {
            // If sizeIfKnown() fails or is not available, skip Content-Length header
            // The client will handle chunked transfer encoding
            LOG.debug("Could not determine file size (expected for some storage types), using chunked transfer encoding: {}",
                    e.getMessage());
            return null;
        }
    }

    private static final class ImportTemplateLocationMapper implements RowMapper<DocumentData> {

        public String schema() {
            final StringBuilder sql = new StringBuilder();
            sql.append("d.location,d.file_name,d.storage_type_enum ")
                    .append("from m_import_document i inner join m_document d on i.document_id=d.id ").append("where i.id= ? ");
            return sql.toString();
        }

        @Override
        public DocumentData mapRow(ResultSet rs, @SuppressWarnings("unused") int rowNum) throws SQLException {
            final String location = rs.getString("location");
            final String fileName = rs.getString("file_name");
            final Integer storageType = JdbcSupport.getInteger(rs, "storage_type_enum");
            return new DocumentData(null, null, null, null, fileName, null, null, location, null, storageType);
        }
    }
}

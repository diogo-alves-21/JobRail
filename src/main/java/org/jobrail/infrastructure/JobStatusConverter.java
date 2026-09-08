package org.jobrail.infrastructure;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jobrail.core.JobStatus;
import org.jobrail.core.JobStatuses;

@Converter
public class JobStatusConverter implements AttributeConverter<JobStatus, String> {

    @Override
    public String convertToDatabaseColumn(JobStatus status) {
        return status == null ? null : status.getName();
    }

    @Override
    public JobStatus convertToEntityAttribute(String value) {

        return value == null ? null : JobStatuses.fromName(value);
    }
}

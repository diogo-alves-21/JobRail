package org.jobrail.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jobrail.core.JobStatus;
import org.jobrail.core.jobstatuses.Pending;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(value = "unit")
public class JobStatusConverterTest {

    private final JobStatusConverter jobStatusConverter = new JobStatusConverter();

    @Test
    void status_convertToDatabaseColumn_returnsStatusName(){
        JobStatus status = Pending.provider();
        String name = jobStatusConverter.convertToDatabaseColumn(status);
        assertEquals(status.getName(), name);
    }

    @Test
    void nullStatus_convertToDatabaseColumn_returnsNull() {
        String name = jobStatusConverter.convertToDatabaseColumn(null);
        assertNull(name);
    }

    @Test
    void value_convertToEntityAttribute_returnsJobStatusFromValue() {
        String value = "PENDING";
        JobStatus status = jobStatusConverter.convertToEntityAttribute(value);
        assertEquals(value, status.getName());
    }

    @Test
    void nullValue_convertToEntityAttribute_returnsNull() {
        JobStatus status = jobStatusConverter.convertToEntityAttribute(null);
        assertNull(status);
    }
}

package com.fcaj.hackathon.aijob;

import com.fcaj.hackathon.storage.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JobSnapshotLimitsTest {
    private final ObjectStorage storage=mock(ObjectStorage.class);
    private final JobSnapshotWriter writer=new JobSnapshotWriter(storage,"mock-v1","mock-v1","mock-v1");
    private JobSnapshotReader.Input input(com.fasterxml.jackson.databind.node.ObjectNode rubric, String description) {
        return new JobSnapshotReader.Input(1,1,1,1,"https://github.com/example/test",JobJson.object().put("description",description),rubric,"fingerprint");
    }
    private GitHubSourceReader.Source source() { return new GitHubSourceReader.Source("a".repeat(40),JobJson.object().put("text","hello")); }
    @Test void oversizedRubricRejectsBeforeAnyUploadIncludingFiveLongDescriptions() {
        var rubric=JobJson.object();var list=rubric.putArray("criteria");
        for(int i=0;i<5;i++) list.addObject().put("description","a".repeat(60_000));
        assertThatThrownBy(()->writer.prepare(UUID.randomUUID(),input(rubric,""),source())).hasMessage("RUBRIC_TOO_LARGE");
        verifyNoInteractions(storage);
    }
    @Test void utf8BoundaryUsesSerializedBytesAndPutsThoseExactBytes() {
        when(storage.put(anyString(),any(),anyString())).thenAnswer(c->{byte[] bytes=c.getArgument(1);return new ObjectStorage.Ref(c.getArgument(0),ObjectKeys.sha256(bytes),bytes.length,"application/json",null);});
        for(int delta:new int[]{-1,0,1}) {
            var rubric=JobJson.object().put("rubricVersion","v1").put("text","Việt Nam 😃");
            int padding=JobLimits.RUBRIC+delta-JobJson.bytes(rubric).length;
            rubric.put("text",rubric.path("text").asText()+"a".repeat(padding));
            assertThat(JobJson.bytes(rubric)).hasSize(JobLimits.RUBRIC+delta);
            if(delta<=0) writer.prepare(UUID.randomUUID(),input(rubric,""),source());
            else assertThatThrownBy(()->writer.prepare(UUID.randomUUID(),input(rubric,""),source())).hasMessage("RUBRIC_TOO_LARGE");
        }
        verify(storage,times(6)).put(anyString(),any(),anyString());
    }
    @Test void manifestFailureKeepsPreparedInputsForReferenceAwareReconciliation() {
        when(storage.put(anyString(),any(),anyString())).thenAnswer(c->{byte[] b=c.getArgument(1);return new ObjectStorage.Ref(c.getArgument(0),ObjectKeys.sha256(b),b.length,"application/json","v".repeat(140_000));});
        assertThatThrownBy(()->writer.prepare(UUID.randomUUID(),input(JobJson.object(),""),source())).hasMessage("MANIFEST_TOO_LARGE");
        verify(storage,times(2)).put(anyString(),any(),anyString());
        verify(storage,never()).delete(anyString(),any());
    }
}

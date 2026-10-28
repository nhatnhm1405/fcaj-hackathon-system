package com.fcaj.hackathon.storage;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ObjectStorageTest {
    @TempDir Path directory;
    private final byte[] png={(byte)0x89,'P','N','G',13,10,26,10,0};
    @Test void immutableLocalObjectCanBeReadAfterReopeningStorage() {
        var storage=new LocalObjectStorage(directory.toString());byte[] bytes="hello".getBytes(StandardCharsets.UTF_8);
        var ref=storage.put("inputs/test/source.json",bytes,"application/json");
        assertThat(ref.sha256()).isEqualTo(ObjectKeys.sha256(bytes));
        assertThat(new LocalObjectStorage(directory.toString()).read(ref.key(),null,5)).containsExactly(bytes);
        assertThatThrownBy(() -> storage.put(ref.key(),bytes,"application/json")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> storage.read(ref.key(),null,4)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void privateStorageRejectsTraversalAndArbitraryUrls() {
        var storage=new LocalObjectStorage(directory.toString());
        for(String key:java.util.List.of("../secret","/absolute","a/../../secret","https://example.test/key","a\\secret"))
            assertThatThrownBy(() -> storage.read(key,null,100)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void s3WritesAreBoundToConfiguredBucketPrefixAndCannotOverwrite() {
        var client=mock(S3Client.class);when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenReturn(PutObjectResponse.builder().versionId("version-1").build());
        var storage=new S3ObjectStorage(client,"test-bucket","isolated/be1");
        var ref=storage.put("inputs/test/source.json",png,"image/png");
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");assertThat(request.getValue().key()).isEqualTo("isolated/be1/inputs/test/source.json");
        assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");assertThat(ref.versionId()).isEqualTo("version-1");
        assertThat(ref.key()).isEqualTo("inputs/test/source.json");
    }
    @Test void s3ReadsUsePinnedVersionAndRejectOversizedResponse() {
        var client=mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(call -> new software.amazon.awssdk.core.ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long)png.length).build(),
                software.amazon.awssdk.http.AbortableInputStream.create(new java.io.ByteArrayInputStream(png))));
        var storage=new S3ObjectStorage(client,"test-bucket","isolated/be1");
        assertThat(storage.read("results/test/output.json","version-1",100)).containsExactly(png);
        var request=org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);verify(client).getObject(request.capture());
        assertThat(request.getValue().versionId()).isEqualTo("version-1");assertThat(request.getValue().key()).isEqualTo("isolated/be1/results/test/output.json");
        assertThatThrownBy(() -> storage.read("results/test/output.json","version-1",2)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void avatarRollbackRetainsOldObjectAndDeletesOnlyNewObject() {
        var storage=new LocalObjectStorage(directory.toString());var avatars=new AvatarStorage(storage,directory.toString(),"local");
        String previous=avatars.upload(new MockMultipartFile("file","old.png","image/png",png));
        TransactionSynchronizationManager.initSynchronization();
        try {
            String created=avatars.upload(new MockMultipartFile("file","new.png","image/png",png));avatars.replaced(previous,created);
            for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(avatars.read(previous.substring("/uploads/avatars/".length())).resource().getByteArray()).containsExactly(png);
            assertThatThrownBy(() -> avatars.read(created.substring("/uploads/avatars/".length()))).isInstanceOf(RuntimeException.class);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void avatarCommitRemovesOldObjectAndPreservesExternalProviderImage() {
        var storage=new LocalObjectStorage(directory.toString());var avatars=new AvatarStorage(storage,directory.toString(),"local");
        String previous=avatars.upload(new MockMultipartFile("file","old.png","image/png",png));
        TransactionSynchronizationManager.initSynchronization();
        try {
            String created=avatars.upload(new MockMultipartFile("file","new.png","image/png",png));avatars.replaced(previous,created);
            avatars.replaced("https://provider.example.test/avatar",null);
            for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            assertThatThrownBy(() -> avatars.read(previous.substring("/uploads/avatars/".length()))).isInstanceOf(RuntimeException.class);
            assertThat(avatars.read(created.substring("/uploads/avatars/".length())).resource().getByteArray()).containsExactly(png);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void svgOrHtmlDisguisedAsImageIsRejected() {
        var avatars=new AvatarStorage(new LocalObjectStorage(directory.toString()),directory.toString(),"local");
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file","image.png","image/png","<html>unsafe</html>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(com.fcaj.hackathon.exception.BadRequestException.class);
    }
}

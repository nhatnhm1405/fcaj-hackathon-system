package com.fcaj.hackathon.storage;

import com.fcaj.hackathon.exception.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarStorage {
    private static final long MAX=5L*1024*1024;
    private static final String PREFIX="/uploads/avatars/";
    private final ObjectStorage storage;
    private final Path legacy;
    private final boolean local;
    public record Image(ByteArrayResource resource,String contentType) {}
    public AvatarStorage(ObjectStorage storage,@Value("${app.upload.dir:uploads}") String directory,
                         @Value("${app.storage.provider:local}") String provider) {
        this.storage=storage;this.legacy=Path.of(directory,"avatars").toAbsolutePath().normalize();this.local="local".equals(provider);
    }
    public String upload(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>MAX) throw new BadRequestException("Image must be between 1 byte and 5MB.");
        try(var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes((int)MAX+1);
            if(bytes.length>MAX) throw new BadRequestException("Image must be 5MB or smaller.");
            String type=type(bytes),ext=switch(type) {case "image/png" -> ".png";case "image/gif" -> ".gif";case "image/webp" -> ".webp";default -> ".jpg";};
            String name=UUID.randomUUID()+ext;storage.put("avatars/"+name,bytes,type);return PREFIX+name;
        } catch(IOException failed) { throw new BadRequestException("Image upload failed."); }
    }
    public void replaced(String previous,String created) {
        if(!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("Avatar mutations require a transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                String obsolete=status==STATUS_COMMITTED?previous:status==STATUS_ROLLED_BACK?created:null;
                if(obsolete==null || !obsolete.startsWith(PREFIX)) return;
                String name=obsolete.substring(PREFIX.length());
                try {
                    if(modern(name)) storage.delete("avatars/"+name,null);
                    else if(local && name.matches("[a-zA-Z0-9_.-]{1,150}") && !name.contains("..")) Files.deleteIfExists(legacy.resolve(name));
                } catch(IOException | RuntimeException ignored) { /* Retry orphan cleanup separately; retain the live reference. */ }
            }
        });
    }
    public Image read(String name) {
        try {
            byte[] bytes;
            if(modern(name)) bytes=storage.read("avatars/"+name,null,MAX);
            else if(local && name.matches("[a-zA-Z0-9_.-]{1,150}") && !name.contains("..")) {
                try(var input=Files.newInputStream(legacy.resolve(name))) { bytes=input.readNBytes((int)MAX+1); }
                if(bytes.length>MAX) throw new ResourceNotFoundException("Avatar not found.");
            } else throw new ResourceNotFoundException("Avatar not found.");
            return new Image(new ByteArrayResource(bytes),type(bytes));
        } catch(IOException | IllegalStateException failed) { throw new ResourceNotFoundException("Avatar not found."); }
    }
    private static boolean modern(String name) { return name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg|gif|webp)"); }
    static String type(byte[] b) {
        if(b.length>=8 && b[0]==(byte)0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "image/png";
        if(b.length>=3 && b[0]==(byte)0xff && b[1]==(byte)0xd8 && b[2]==(byte)0xff) return "image/jpeg";
        if(b.length>=6 && new String(b,0,6,java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a")) return "image/gif";
        if(b.length>=12 && new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw new BadRequestException("Only PNG, JPEG, GIF and WebP images are supported.");
    }
}

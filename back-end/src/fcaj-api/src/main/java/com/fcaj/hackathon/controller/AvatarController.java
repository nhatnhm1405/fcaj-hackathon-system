package com.fcaj.hackathon.controller;

import com.fcaj.hackathon.storage.AvatarStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AvatarController {
    private final AvatarStorage avatars;
    @GetMapping("/uploads/avatars/{name}")
    public ResponseEntity<Resource> avatar(@PathVariable String name) {
        var image=avatars.read(name);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noCache()).body(image.resource());
    }
}

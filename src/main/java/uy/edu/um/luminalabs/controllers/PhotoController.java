package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.luminalabs.repositories.StoredImageRepository;

import java.time.Duration;

// Sirve las fotos guardadas en la base (actividades y localidades). Es publico: se ven en el catalogo.
// Una foto nunca cambia (una nueva es otro id), asi que el navegador la puede guardar en cache un año.
@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final StoredImageRepository imageRepository;

    @GetMapping("/photos/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return imageRepository.findById(id)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(image.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}

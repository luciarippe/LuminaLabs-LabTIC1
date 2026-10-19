package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.StoredImage;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {
}

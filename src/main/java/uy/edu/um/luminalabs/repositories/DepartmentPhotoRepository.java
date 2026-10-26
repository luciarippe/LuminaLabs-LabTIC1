package uy.edu.um.luminalabs.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.luminalabs.entities.Department;
import uy.edu.um.luminalabs.entities.DepartmentPhoto;

public interface DepartmentPhotoRepository extends JpaRepository<DepartmentPhoto, Department> {
}

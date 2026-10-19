package fr.petitesannonces.categorie;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    boolean existsByLibelleIgnoreCase(String libelle);

    List<Categorie> findAll(Sort sort);
}

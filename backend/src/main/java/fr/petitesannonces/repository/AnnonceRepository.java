package fr.petitesannonces.repository;

import fr.petitesannonces.entity.StatutAnnonce;
import fr.petitesannonces.entity.Annonce;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AnnonceRepository extends JpaRepository<Annonce, UUID> {

    /** Lecture minimale pour la règle de propriété (pas besoin de charger toute l'annonce). */
    @Query("select a.auteurId from Annonce a where a.id = :id")
    Optional<String> findAuteurIdById(UUID id);

    // @EntityGraph : la catégorie est chargée dans la même requête (évite le N+1).
    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByStatut(StatutAnnonce statut, Pageable pageable);

    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByStatutAndCategorieId(StatutAnnonce statut, Long categorieId, Pageable pageable);

    @EntityGraph(attributePaths = "categorie")
    Page<Annonce> findByAuteurId(String auteurId, Pageable pageable);

    boolean existsByCategorieId(Long categorieId);
}
